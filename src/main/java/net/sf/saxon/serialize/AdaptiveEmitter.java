////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverWithOutputProperties;
import net.sf.saxon.event.SequenceWriter;
import net.sf.saxon.functions.FormatNumber;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.str.BMPString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.str.UnicodeWriter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.QualifiedNameValue;

import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.text.Normalizer;
import java.util.Properties;

/**
 * This class implements the Adaptive serialization method defined in XSLT+XQuery Serialization 3.1.
 */

public class AdaptiveEmitter extends SequenceWriter implements ReceiverWithOutputProperties {

    private final UnicodeWriter writer;
    private CharacterMap characterMap;
    private Properties outputProperties;
    private String itemSeparator = "\n";
    private boolean started = false;
    private boolean mustClose = true;

    public AdaptiveEmitter(PipelineConfiguration pipe, UnicodeWriter writer)  {
        super(pipe);
        this.writer = writer;
    }

    public void setOutputProperties(Properties props) {
        outputProperties = props;
        String sep = props.getProperty(SaxonOutputKeys.ITEM_SEPARATOR);
        if (sep != null && !"#absent".equals(sep)) {
            itemSeparator = sep;
        }
    }

    /**
     * Set the Unicode normalizer to be used for normalizing strings.
     *
     * @param normalizationForm the normalizationForm to be used
     */

    public void setNormalizationForm(Normalizer.Form normalizationForm) {
        // TODO: should this method do something?
    }

    /**
     * Say whether the output must be closed on completion
     * @param mustClose true if the output must be closed
     */
    public void setMustClose(boolean mustClose) {
        this.mustClose = mustClose;
    }

    /**
     * Set the CharacterMap to be used, if any
     *
     * @param map the character map
     */

    public void setCharacterMap(CharacterMap map) {
        this.characterMap = map;
    }


    @Override
    public Properties getOutputProperties() {
        return outputProperties;
    }

    private void emit(String s) throws XPathException {
        try {
            writer.write(s);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    private void emit(UnicodeString s) throws XPathException {
        try {
            writer.write(s);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    /**
     * Abstract method to be supplied by subclasses: output one item in the sequence.
     *
     * @param item the item to be written to the sequence
     * @throws XPathException if any failure occurs while writing the item
     */
    @Override
    public void write(Item item) throws XPathException {
        if (started) {
            emit(itemSeparator);
        } else {
            started = true;
        }
        serializeItem(item);
    }

    private void serializeItem(Item item) throws XPathException {
        if (item instanceof AtomicValue) {
            emit(serializeAtomicValue((AtomicValue) item));
        } else if (item instanceof NodeInfo) {
            serializeNode((NodeInfo) item);
        } else if (item instanceof MapItem) {
            serializeMap((MapItem) item);
        } else if (item instanceof ArrayItem) {
            serializeArray((ArrayItem) item);
        } else if (item instanceof FunctionItem) {
            serializeFunction((FunctionItem) item);
        }
    }

    static ARegularExpression QUOTES = ARegularExpression.compile("\"", "");

    private String serializeAtomicValue(AtomicValue value) throws XPathException {
        switch(value.getPrimitiveType().getFingerprint()) {
            case StandardNames.XS_STRING:
            case StandardNames.XS_ANY_URI:
            case StandardNames.XS_UNTYPED_ATOMIC: {
                UnicodeString s = value.getUnicodeStringValue();
                s = QUOTES.replace(s, BMPString.of("\"\""));
                if (characterMap != null) {
                    s = characterMap.map(s, false);
                }
                return "\"" + s.toString() + "\"";
            }
            case StandardNames.XS_BOOLEAN:
                return value.effectiveBooleanValue() ? "true()" : "false()";

            case StandardNames.XS_DECIMAL:
            case StandardNames.XS_INTEGER:
                return value.getStringValue();

            case StandardNames.XS_DOUBLE:
                return FormatNumber.formatExponential((DoubleValue)value);

            case StandardNames.XS_FLOAT:
            case StandardNames.XS_DURATION:
            case StandardNames.XS_DATE_TIME:
            case StandardNames.XS_DATE:
            case StandardNames.XS_TIME:
            case StandardNames.XS_G_YEAR_MONTH:
            case StandardNames.XS_G_MONTH:
            case StandardNames.XS_G_MONTH_DAY:
            case StandardNames.XS_G_YEAR:
            case StandardNames.XS_G_DAY:
            case StandardNames.XS_HEX_BINARY:
            case StandardNames.XS_BASE64_BINARY:
                return value.getPrimitiveType().getDisplayName() + "(\"" + value.getUnicodeStringValue() + "\")";

            case StandardNames.XS_DAY_TIME_DURATION:
            case StandardNames.XS_YEAR_MONTH_DURATION:
                return "xs:duration(\"" + value.getUnicodeStringValue() + "\")";

            case StandardNames.XS_QNAME:
            case StandardNames.XS_NOTATION:
                return ((QualifiedNameValue)value).getStructuredQName().getEQName();
            default:
                return "***";
        }
    }

    private void serializeFunction(FunctionItem fn) throws XPathException {
        StructuredQName fname = fn.getFunctionName();
        if (fname == null || fname.hasURI(NamespaceUri.ANONYMOUS)) {
            emit("(anonymous-function)");
        } else if (fname.hasURI(NamespaceUri.FN)) {
            emit("fn:" + fname.getLocalPart());
        } else if (fname.hasURI(NamespaceUri.MATH)) {
            emit("math:" + fname.getLocalPart());
        } else if (fname.hasURI(NamespaceUri.MAP_FUNCTIONS)) {
            emit("map:" + fname.getLocalPart());
        } else if (fname.hasURI(NamespaceUri.ARRAY_FUNCTIONS)) {
            emit("array:" + fname.getLocalPart());
        } else if (fname.hasURI(NamespaceUri.SCHEMA)) {
            emit("xs:" + fname.getLocalPart());
        } else {
            emit(fname.getEQName());
        }
        emit("#" + fn.getArity());
    }

    private void serializeNode(NodeInfo node) throws XPathException {
        switch (node.getNodeKind()) {
            case Type.ATTRIBUTE:
                emit(node.getDisplayName());
                emit("=\"");
                emit(escapeAttributeValue(node.getStringValue()));
                emit("\"");
                break;
            case Type.NAMESPACE:
                emit(node.getLocalPart().isEmpty() ? "xmlns" : "xmlns:" + node.getLocalPart());
                emit("=\"");
                emit(escapeAttributeValue(node.getStringValue()));
                emit("\"");
                break;
            default:
                StringWriter sw = new StringWriter();
                Properties props = new Properties(outputProperties);
                props.setProperty("method", "xml");
                //props.setProperty("indent", "no");
                if (props.getProperty("omit-xml-declaration") == null) {
                    props.setProperty("omit-xml-declaration", "no");
                }
                props.setProperty(SaxonOutputKeys.UNFAILING, "yes");
                CharacterMapIndex cmi = null;
                if (characterMap != null) {
                    cmi = new CharacterMapIndex();
                    cmi.putCharacterMap(characterMap.getName(), characterMap);
                }
                SerializationProperties sProps = new SerializationProperties(props, cmi);
                QueryResult.serialize(node, new StreamResult(sw), sProps);
                emit(sw.toString());
                break;
        }
    }

    private String escapeAttributeValue(String value) {
        StringBuilder sb = new StringBuilder(value.length()*2);
        for (int i=0; i<value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\r':
                    sb.append("&#xD;");
                    break;
                case '\t':
                    sb.append("&#x9;");
                    break;
                case '\n':
                    sb.append("&#xA;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                default:
                    sb.append(c);
                    break;
            }

        }
        return sb.toString();
    }

    private void serializeArray(ArrayItem array) throws XPathException {
        emit("[");
        boolean first = true;
        for (Sequence seq: array.members()) {
            if (first) {
                first = false;
            } else {
                emit(",");
            }
            outputInternalSequence(seq);
        }
        emit("]");
    }

    private void serializeMap(MapItem map) throws XPathException {
        emit("map{");
        boolean first = true;
        for (KeyValuePair pair : map.keyValuePairs()) {
            if (first) {
                first = false;
            } else {
                emit(",");
            }
            serializeItem(pair.key);
            emit(":");
            Sequence value = pair.value;
            outputInternalSequence(value);
        }
        emit("}");
    }

    private void outputInternalSequence(Sequence value) throws XPathException {
        boolean first = true;
        Item it;
        SequenceIterator iter = value.iterate();
        boolean omitParens = value instanceof GroundedValue && ((GroundedValue)value).getLength() == 1;
        if (!omitParens) {
            emit("(");
        }
        while ((it = iter.next()) != null) {
            if (!first) {
                emit(",");
            }
            first = false;
            serializeItem(it);
        }
        if (!omitParens) {
            emit(")");
        }
    }

    @Override
    public void close() throws XPathException {
        super.close();
        if (writer != null) {
            try {
                if (mustClose) {
                    writer.close();
                } else {
                    writer.flush();
                }
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }
    }
}

