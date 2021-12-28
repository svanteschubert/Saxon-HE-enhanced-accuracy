////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverWithOutputProperties;
import net.sf.saxon.event.SequenceWriter;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.serialize.charcode.CharacterSet;
import net.sf.saxon.serialize.codenorm.Normalizer;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.*;

/**
 * This class implements the JSON serialization method defined in XSLT+XQuery Serialization 3.1.
 *
 * @author Michael H. Kay
 */

public class JSONSerializer extends SequenceWriter implements ReceiverWithOutputProperties {

    private boolean allowDuplicateKeys = false;
    private String nodeOutputMethod = "xml";
    private int level = 0;
    private int topLevelCount = 0;
    private int maxLineLength = 80;

    private JSONEmitter emitter;
    private Properties outputProperties;
    private CharacterSet characterSet;
    private boolean isIndenting;
    private Comparator<AtomicValue> propertySorter;

    private boolean unfailing = false;

    public JSONSerializer(PipelineConfiguration pipe, JSONEmitter emitter, Properties outputProperties) throws XPathException {
        super(pipe);
        setOutputProperties(outputProperties);
        this.emitter = emitter;
    }

    /**
     * Set output properties
     *
     * @param details the output serialization properties
     */

    public void setOutputProperties(Properties details) {
        this.outputProperties = details;
        if ("yes".equals(details.getProperty(SaxonOutputKeys.ALLOW_DUPLICATE_NAMES))) {
            allowDuplicateKeys = true;
        }
        if ("yes".equals(details.getProperty(OutputKeys.INDENT))) {
            isIndenting = true;
        }
        if ("yes".equals(details.getProperty(SaxonOutputKeys.UNFAILING))) {
            unfailing = true;
            allowDuplicateKeys = true;
        }
        String jnom = details.getProperty(SaxonOutputKeys.JSON_NODE_OUTPUT_METHOD);
        if (jnom != null) {
            nodeOutputMethod = jnom;
        }
        String max = details.getProperty(SaxonOutputKeys.LINE_LENGTH);
        if (max != null) {
            try {
                maxLineLength = Integer.parseInt(max);
            } catch (NumberFormatException err) {
                // ignore the error.
            }
        }
    }

    public void setPropertySorter(Comparator<AtomicValue> sorter) {
        this.propertySorter = sorter;
    }

    /**
     * Get the output properties
     *
     * @return the properties that were set using setOutputProperties
     */

    @Override
    public Properties getOutputProperties() {
        return outputProperties;
    }

    /**
     * Set the Unicode normalizer to be used for normalizing strings.
     *
     * @param normalizer the normalizer to be used
     */

    public void setNormalizer(Normalizer normalizer) {
        emitter.setNormalizer(normalizer);
    }

    /**
     * Set the CharacterMap to be used, if any
     *
     * @param map the character map
     */

    public void setCharacterMap(CharacterMap map) {
        emitter.setCharacterMap(map);
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     *
     * @param item the item to be appended
     * @throws net.sf.saxon.trans.XPathException if the operation fails
     */
    @Override
    public void write(Item item) throws XPathException {
        if (level == 0 && ++topLevelCount >= 2) {
            throw new XPathException("JSON output method cannot handle sequences of two or more items", "SERE0023");
        }

        if (item instanceof AtomicValue) {
            emitter.writeAtomicValue((AtomicValue) item);
        } else if (item instanceof MapItem) {
            Set<String> keys = null;
            if (!allowDuplicateKeys) {
                keys = new HashSet<>();
            }
            boolean oneLiner = !isIndenting || isOneLinerMap((MapItem) item);
            emitter.startMap(oneLiner);
            boolean first = true;
            List<AtomicValue> keyList = new ArrayList<>();
            for (KeyValuePair pair : ((MapItem) item).keyValuePairs()) {
                keyList.add(pair.key);
            }
            if (propertySorter != null) {
                keyList.sort(propertySorter);
            }
            for (AtomicValue key : keyList) {
                String stringKey = key.getStringValue();
                emitter.writeKey(stringKey);
                if (!allowDuplicateKeys && !keys.add(stringKey)) {
                    throw new XPathException("Key value \"" + stringKey + "\" occurs more than once in JSON map", "SERE0022");
                }
                Sequence value = ((MapItem) item).get(key);
                writeSequence(value.materialize());
            }
            emitter.endMap();
        } else if (item instanceof ArrayItem) {
            boolean oneLiner = !isIndenting || isOneLinerArray((ArrayItem) item);
            emitter.startArray(oneLiner);
            boolean first = true;
            for (Sequence member : ((ArrayItem) item).members()) {
                writeSequence(member.materialize());
            }
            emitter.endArray();
        } else if (item instanceof NodeInfo) {
            String s = serializeNode((NodeInfo) item);
            emitter.writeAtomicValue(new StringValue(s));
        } else if (unfailing) {
            String s = item.getStringValue();
            emitter.writeAtomicValue(new StringValue(s));
        } else {
            throw new XPathException("JSON output method cannot handle an item of type " + item.getClass(), "SERE0021");
        }

    }

    private boolean isOneLinerArray(ArrayItem array) {
        int totalSize = 0;
        if (array.arrayLength() < 2) {
            return true;
        }
        for (Sequence member : array.members()) {
            if (!(member instanceof AtomicValue)) {
                return false;
            }
            totalSize += ((AtomicValue) member).getStringValueCS().length() + 1;
            if (totalSize > maxLineLength) {
                return false;
            }
        }
        return true;
    }

    private boolean isOneLinerMap(MapItem map) {
        int totalSize = 0;
        if (map.size() < 2) {
            return true;
        }
        for (KeyValuePair entry : map.keyValuePairs()) {
            if (!(entry.value instanceof AtomicValue)) {
                return false;
            }
            totalSize += entry.key.getStringValueCS().length() + ((AtomicValue) entry.value).getStringValueCS().length() + 4;
            if (totalSize > maxLineLength) {
                return false;
            }
        }
        return true;
    }


    private String serializeNode(NodeInfo node) throws XPathException {
        StringWriter sw = new StringWriter();
        Properties props = new Properties();
        props.setProperty("method", nodeOutputMethod);
        props.setProperty("indent", "no");
        props.setProperty("omit-xml-declaration", "yes");
        QueryResult.serialize(node, new StreamResult(sw), props);
        return sw.toString().trim();
    }

    private void writeSequence(GroundedValue seq) throws XPathException {
        int len = seq.getLength();
        if (len == 0) {
            emitter.writeAtomicValue(null);
        } else if (len == 1) {
            level++;
            write(seq.head());
            level--;
        } else {
            throw new XPathException("JSON serialization: cannot handle a sequence of length "
                                             + len + Err.depictSequence(seq), "SERE0023");
        }
    }

    /**
     * End of the document.
     */
    @Override
    public void close() throws XPathException {
        if (topLevelCount == 0) {
            emitter.writeAtomicValue(null);
        }
        emitter.close();
        super.close();
    }
}

