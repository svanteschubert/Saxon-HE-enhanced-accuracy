////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.ma.json.JsonReceiver;
import net.sf.saxon.serialize.charcode.CharacterSet;
import net.sf.saxon.serialize.codenorm.Normalizer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.NumericValue;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.Writer;
import java.util.Properties;
import java.util.Stack;

/**
 * This class implements the back-end text generation of the JSON serialization method. It takes
 * as input a sequence of event-based calls such as startArray, endArray, startMap, endMap,
 * and generates the lexical JSON output.
 *
 * @author Michael H. Kay
 */

public class JSONEmitter {

    private ExpandedStreamResult result;

    private Writer writer;
    private Normalizer normalizer;
    private CharacterMap characterMap;
    private Properties outputProperties;
    private CharacterSet characterSet;
    private boolean isIndenting;
    private int indentSpaces = 2;
    private int maxLineLength;
    private boolean first = true;
    private boolean afterKey = false;
    private int level;
    private Stack<Boolean> oneLinerStack = new Stack<>();

    private boolean unfailing = false;

    public JSONEmitter(PipelineConfiguration pipe, StreamResult result, Properties outputProperties) throws XPathException {
        setOutputProperties(outputProperties);
        this.result = new ExpandedStreamResult(pipe.getConfiguration(), result, outputProperties);
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
        if ("yes".equals(details.getProperty(SaxonOutputKeys.UNFAILING))) {
            unfailing = true;
        }
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
     * @param normalizer the normalizer to be used
     */

    public void setNormalizer(Normalizer normalizer) {
        this.normalizer = normalizer;
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

    public void writeKey(String key) throws XPathException {
        conditionalComma(false);
        emit('"');
        emit(escape(key));
        emit("\":");
        if (isIndenting) {
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
        } else if (item instanceof NumericValue) {
            NumericValue num = (NumericValue)item;
            if (num.isNaN()) {
                if (unfailing) {
                    emit("NaN");
                } else {
                    throw new XPathException("JSON has no way of representing NaN", "SERE0020");
                }
            } else if (Double.isInfinite(num.getDoubleValue())) {
                if (unfailing) {
                    emit(num.getDoubleValue() < 0 ? "-INF" : "INF");
                } else {
                    throw new XPathException("JSON has no way of representing Infinity", "SERE0020");
                }
            } else if (item instanceof IntegerValue) {
                // " Implementations MAY serialize the numeric value using any
                //   lexical representation of a JSON number defined in [RFC 7159]. "
                // This avoids exponential notation for integers such as 1123456.
                emit(num.longValue() + "");
            } else if (num.isWholeNumber() && !num.isNegativeZero() && num.abs().compareTo(1_000_000_000_000_000_000L) < 0) {
                emit(num.longValue() + "");
            } else {
                emit(num.getStringValue());
            }
        } else if (item instanceof BooleanValue) {
            emit(item.getStringValue());
        } else {
            emit('"');
            emit(escape(item.getStringValue()));
            emit('"');
        }
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
     * map (each starting with a call on {@link #writeKey(String)}, followed by a call on
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
//        if (isIndenting) {
//            emit(' ');
//        }
        emit(bracket);
        first = true;
        if (isIndenting && oneLiner) {
            emit(' ');
        }

    }

    private void emitClose(char bracket, int level) throws XPathException {
        boolean oneLiner = oneLinerStack.pop();
        if (isIndenting) {
            if (oneLiner) {
                emit(' ');
            } else {
                indent(level-1);
            }
        }
        emit(bracket);
        first = false;

    }


    private void conditionalComma(boolean opening) throws XPathException {
        boolean wasFirst = first;
        boolean actuallyIndenting = isIndenting && level != 0 && !oneLinerStack.peek();
        if (first) {
            first = false;
        } else if (!afterKey) {
            emit(',');
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

    private CharSequence escape(CharSequence cs) throws XPathException {
        if (characterMap != null) {
            FastStringBuffer out = new FastStringBuffer(cs.length());
            cs = characterMap.map(cs, true);
            String s = cs.toString();
            int prev = 0;
            while (true) {
                int start = s.indexOf(0, prev);
                if (start >= 0) {
                    out.cat(simpleEscape(s.substring(prev, start)));
                    int end = s.indexOf(0, start + 1);
                    out.append(s.substring(start + 1, end));
                    prev = end + 1;
                } else {
                    out.cat(simpleEscape(s.substring(prev)));
                    return out;
                }
            }
        } else {
            return simpleEscape(cs);
        }
    }

    private CharSequence simpleEscape(CharSequence cs) throws XPathException {
        if (normalizer != null) {
            cs = normalizer.normalize(cs);
        }
        return JsonReceiver.escape(cs, false,
                                   c -> c < 31 || (c >= 127 && c <= 159) || !characterSet.inCharset(c));
    }

    private void emit(CharSequence s) throws XPathException {
        if (writer == null) {
            writer = result.obtainWriter();
            characterSet = result.getCharacterSet();
        }
        try {
            writer.append(s);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    private void emit(char c) throws XPathException {
        emit(c + "");
    }


    /**
     * End of the document.
     */

    public void close() throws XPathException {
        if (first) {
            emit("null");
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // no action
            }
        }
    }
}

