////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.ma.json.JsonReceiver;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.serialize.charcode.CharacterSet;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.serialize.jcs.NumberToJSON;
import net.sf.saxon.str.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntPredicateLambda;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.text.Normalizer;
import java.util.Properties;
import java.util.Stack;
import java.util.function.Function;

/**
 * This class implements the back-end text generation of the JSON serialization method. It takes
 * as input a sequence of event-based calls such as startArray, endArray, startMap, endMap,
 * and generates the lexical JSON output.
 *
 */

public class JSONEmitter {

    //private final ExpandedStreamResult result;

    private final Configuration config;
    private final UnicodeWriter writer;
    private boolean normalize;
    private Normalizer.Form normalizationForm;
    private CharacterMap characterMap;
    private Properties outputProperties;
    private CharacterSet characterSet;
    private boolean isIndenting;
    private boolean isJsonLines;
    private int indentSpaces = 2;
    private int maxLineLength;
    private boolean first = true;
    private boolean afterKey = false;
    private int level;
    private final Stack<Boolean> oneLinerStack = new Stack<>();
    private boolean mustClose = true;
    private boolean escapeSolidus = true;
    private boolean canonical = false;
    private boolean unfailing = false;
    private boolean is40 = false;
    private JsonReceiver.EscapeOptions escapeOptions;
    private JsonReceiver.EscapeOptions canonicalEscapeOptions;

    public JSONEmitter(PipelineConfiguration pipe, UnicodeWriter writer, Properties outputProperties)  {
        config = pipe.getConfiguration();
        setOutputProperties(outputProperties);
        this.writer = writer;
        String specVn = outputProperties.getProperty(SaxonOutputKeys.SPEC_VERSION);
        if ("40".equals(specVn) || "4.0".equals(specVn)) {
            is40 = true;
        }
    }

    /**
     * Set output properties
     *
     * @param details the output serialization properties
     */

    public void setOutputProperties(Properties details) {
        this.outputProperties = details;
        if ("yes".equals(details.getProperty(OutputKeys.INDENT))) {
            isIndenting = true;
        }
        if ("yes".equals(details.getProperty(SaxonOutputKeys.JSON_LINES))) {
            isJsonLines = true;
        }
        if ("yes".equals(details.getProperty(SaxonOutputKeys.CANONICAL))) {
            canonical = true;
        }
        if ("yes".equals(details.getProperty(SaxonOutputKeys.UNFAILING)) && !canonical) {
            unfailing = true;
        }
        if ("no".equals(details.getProperty(SaxonOutputKeys.ESCAPE_SOLIDUS)) || canonical) {
            escapeSolidus = false;
        }
        escapeOptions = new JsonReceiver.EscapeOptions(
                false, !escapeSolidus, false, IntPredicateLambda.of(
                                                  c -> c < 31 || (c >= 127 && c <= 159) || !characterSet.inCharset(c)));
        canonicalEscapeOptions = new JsonReceiver.EscapeOptions(
                false, true, false, IntPredicateLambda.of(c -> c < 31));

        String max = details.getProperty(SaxonOutputKeys.LINE_LENGTH);
        if (max != null) {
            try {
                maxLineLength = Integer.parseInt(max);
            } catch (NumberFormatException err) {
                // ignore the error.
            }
        }
        String spaces = details.getProperty(SaxonOutputKeys.INDENT_SPACES);
        if (spaces != null) {
            try {
                indentSpaces = Integer.parseInt(spaces);
            } catch (NumberFormatException err) {
                // ignore the error.
            }
        }
        String encoding = details.getProperty(OutputKeys.ENCODING);
        try {
            characterSet = config.getCharacterSetFactory().getCharacterSet(encoding);
        } catch (XPathException e) {
            characterSet = UTF8CharacterSet.getInstance();
        }

    }

    /**
     * Say whether the output must be closed on completion
     *
     * @param mustClose true if the output must be closed
     */
    public void setMustClose(boolean mustClose) {
        this.mustClose = mustClose;
    }

    /**
     * Get the output properties
     *
     * @return the properties that were set using setOutputProperties
     */

    public Properties getOutputProperties() {
        return outputProperties;
    }

    /**
     * Set the Unicode normalizer to be used for normalizing strings.
     *
     * @param form the normalization form to be used (default is no normalization)
     */

    public void setNormalizationForm(Normalizer.Form form) {
        this.normalize = true;
        this.normalizationForm = form;
    }

    /**
     * Set the CharacterMap to be used, if any
     *
     * @param map the character map
     */

    public void setCharacterMap(CharacterMap map) {
        this.characterMap = map;
    }

    /**
     * Output the key for an entry in a map. The corresponding value must be supplied
     * in the following call.
     * @param key the value of the key, without any escaping of special characters
     * @throws XPathException if any error occurs
     */

    public void writeKey(UnicodeString key) throws XPathException {
        boolean oneLiner = oneLinerStack.peek();
        conditionalComma(false);
        emit('"');
        emit(escape(key));
        emit("\":");
        if (isIndenting && !oneLiner) {
            emit(" ");
        }
        afterKey = true;
    }

    /**
     * Append a singleton value (number, string, or boolean) to the output
     *
     * @param item the atomic value to be appended, or null to append "null"
     * @throws XPathException if the operation fails
     */

    public void writeAtomicValue(AtomicValue item) throws XPathException {
        conditionalComma(false);
        if (item == null) {
            emit("null");
        } else if (item instanceof NumericValue num) {
            if (item instanceof DecimalValue && !canonical) {
                // Avoid exponential notation
                emit(num.getUnicodeStringValue());
            } else if (num.isNaN()) {
                if (unfailing && !canonical) {
                    emit("NaN");
                } else if (is40 && !canonical) {
                    emit("null");
                } else {
                    throw new XPathException("JSON has no way of representing NaN", "SERE0020");
                }
            } else if (Double.isInfinite(num.getDoubleValue())) {
                if ((unfailing || is40) & !canonical) {
                    emit(num.getDoubleValue() < 0 ? "-1e9999" : "1e9999");
                } else {
                    throw new XPathException("JSON has no way of representing Infinity", "SERE0020");
                }
            } else if (num.isNegativeZero()) {
                emit(canonical ? "0" : "-0");
            } else {

                double val = num.getDoubleValue();
                double abs = Math.abs(val);
                // Avoid exponential notation except in extremis
                if (abs == 0.0) {
                    emit("0");
                } else if (!SerializerFactory.canonicalJsonIsSupported()) {
                    // This path currently used on SaxonCS
                    String s = FloatingPointConverter.convertDouble(val, val != 0 && (val >= 1000000 || val < 0.000001)).toString();
                    s = s.replaceFirst("E", "e").replaceFirst("e(?!-)", "e+");
                    emit(s);
                } else {
                    try {
                        String s = NumberToJSON.serializeNumber(val);
                        emit(s);
                    } catch (IOException e) {
                        throw new XPathException(e.getMessage());
                    }
//                } else {
//                    emit(new BigDecimalValue(val).toString());
//                    //emit(FloatingPointConverter.convertDouble(val, false));
                }
            }
        } else if (item instanceof BooleanValue) {
            emit(item.getUnicodeStringValue());
        } else if (item instanceof QNameValue && item.equals(JSON_NULL)) {
            emit(StringConstants.NULL);
        } else {
            emit('"');
            emit(escape(item.getUnicodeStringValue()));
            emit('"');
        }
    }

    public final static QNameValue JSON_NULL = new QNameValue(new StructuredQName("", NamespaceUri.FN, "null"), BuiltInAtomicType.QNAME);

    /**
     * Append a singleton string value to the output
     *
     * @param str the string value to be appended
     * @throws XPathException if the operation fails
     */

    public void writeStringValue(UnicodeString str) throws XPathException {
        conditionalComma(false);
        emit('"');
        emit(escape(str));
        emit('"');
    }


    /**
     * Output the start of an array. This call must be followed by the members of the
     * array, followed by a call on {@link #endArray()}.
     * @param oneLiner True if the caller thinks the value should be output without extra newlines
     *                 after the open bracket or before the close bracket,
     *                 even when indenting is on.
     * @throws XPathException if any failure occurs
     */

    public void startArray(boolean oneLiner) throws XPathException {
        emitOpen('[', oneLiner);
        level++;
    }

    /**
     * Output the end of an array
     * @throws XPathException  if any failure occurs
     */

    public void endArray() throws XPathException {
        emitClose(']', level--);
    }

    /**
     * Output the start of an map. This call must be followed by the entries in the
     * map (each starting with a call on {@link #writeKey(UnicodeString)}, followed by a call on
     * {@link #endMap()}.
     *
     * @param oneLiner True if the caller thinks the value should be output without extra newlines
     *                 after the open bracket or before the close bracket,
     *                 even when indenting is on.
     * @throws XPathException if any failure occurs
     */

    public void startMap(boolean oneLiner) throws XPathException {
        emitOpen('{', oneLiner);
        level++;
    }

    public void endMap() throws XPathException {
        emitClose('}', level--);
    }

    private void emitOpen(char bracket, boolean oneLiner) throws XPathException {
        conditionalComma(true);
        oneLinerStack.push(oneLiner);
        emit(bracket);
        first = true;
        if (isIndenting && oneLiner) {
            emit(' ');
        }
    }

    private void emitClose(char bracket, int level) throws XPathException {
        boolean oneLiner = oneLinerStack.pop();
        if (isIndenting) {
            if (oneLiner || isJsonLines) {
                emit(' ');
            } else {
                indent(level - 1);
            }
        }
        emit(bracket);
        first = false;

    }

    private void conditionalComma(boolean opening) throws XPathException {
        boolean wasFirst = first;
        boolean oneLiner = !oneLinerStack.isEmpty() && oneLinerStack.peek();
        boolean actuallyIndenting = isIndenting && level != 0 && !oneLiner;
        if (first) {
            first = false;
        } else if (level == 0 && isJsonLines) {
            return;
        } else if (!afterKey) {
            emit(',');
            if (oneLiner && isIndenting) {
                emit(' ');
            }
        }
        if ((wasFirst && afterKey)) {
            emit(' ');
        } else if (actuallyIndenting && !afterKey) {
            emit('\n');
            for (int i = 0; i < indentSpaces * level; i++) {
                emit(' ');
            }
        }
        afterKey = false;
    }

    private void indent(int level) throws XPathException {
        emit('\n');
        for (int i = 0; i < indentSpaces * level; i++) {
            emit(' ');
        }
    }

    public void newLine() throws XPathException {
        emit('\n');
    }

    private UnicodeString escape(UnicodeString cs) throws XPathException {
        Function<UnicodeString, UnicodeString> escaper =
                canonical ? this::canonicalEscape : this::simpleEscape;
        if (characterMap != null) {
            UnicodeBuilder out = new UnicodeBuilder();
            UnicodeString s = characterMap.map(cs, true);
            long prev = 0;
            while (true) {
                long start = s.indexOf((char)0, prev);
                if (start >= 0) {
                    out.append(escaper.apply(s.substring(prev, start)));
                    long end = s.indexOf((char)0, start + 1);
                    out.append(s.substring(start + 1, end));
                    prev = end + 1;
                } else {
                    out.append(escaper.apply(s.substring(prev)));
                    return out.toUnicodeString();
                }
            }
        } else {
            return escaper.apply(cs);
        }
    }

    private UnicodeString simpleEscape(UnicodeString cs) {
        if (normalize) {
            cs = StringView.of(Normalizer.normalize(cs.toString(), normalizationForm));
        }
        return JsonReceiver.escape(cs, escapeOptions);
    }

    private UnicodeString canonicalEscape(UnicodeString cs)  {
        if (normalize) {
            cs = StringView.of(Normalizer.normalize(cs.toString(), normalizationForm));
        }
        return JsonReceiver.escape(cs, canonicalEscapeOptions);
    }

    private void emit(String s) throws XPathException {
        assert writer != null;
        try {
            writer.write(s);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    private void emit(UnicodeString s) throws XPathException {
        assert writer != null;
        try {
            writer.write(s);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    private void emit(char c) throws XPathException {
        assert writer != null;
        try {
            writer.writeCodePoint(c);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }


    /**
     * End of the document.
     *
     * @throws XPathException if any error occurs
     */

    public void close() throws XPathException {
        if (first && !isJsonLines) {
            emit("null");
        }
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

