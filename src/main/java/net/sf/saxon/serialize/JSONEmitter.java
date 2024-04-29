////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.ma.json.JsonReceiver;
import net.sf.saxon.serialize.charcode.CharacterSet;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.str.UnicodeWriter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.text.Normalizer;
import java.util.Properties;
import java.util.Stack;

/**
 * This class implements the back-end text generation of the JSON serialization method. It takes
 * as input a sequence of event-based calls such as startArray, endArray, startMap, endMap,
 * and generates the lexical JSON output.
 *
 */

public class JSONEmitter {

    //private final ExpandedStreamResult result;

    private Configuration config;
    private UnicodeWriter writer;
    private boolean normalize;
    private Normalizer.Form normalizationForm;
    private CharacterMap characterMap;
    private Properties outputProperties;
    private CharacterSet characterSet;
    private boolean isIndenting;
    private int indentSpaces = 2;
    private int maxLineLength;
    private boolean first = true;
    private boolean afterKey = false;
    private int level;
    private final Stack<Boolean> oneLinerStack = new Stack<>();
    private boolean mustClose = true;
    private boolean escapeSolidus = true;

    private boolean unfailing = false;

    public JSONEmitter(PipelineConfiguration pipe, UnicodeWriter writer, Properties outputProperties)  {
        config = pipe.getConfiguration();
        setOutputProperties(outputProperties);
        this.writer = writer;
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
        if ("no".equals(details.getProperty(SaxonOutputKeys.ESCAPE_SOLIDUS))) {
            escapeSolidus = false;
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

    public void writeKey(String key) throws XPathException {
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
        } else if (item instanceof NumericValue) {
            NumericValue num = (NumericValue)item;
            if (item instanceof DecimalValue) {
                // Avoid exponential notation
                emit(num.getUnicodeStringValue());
            } else if (num.isNaN()) {
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
            } else if (num.isNegativeZero()) {
                emit("-0");
            } else {
                double val = num.getDoubleValue();
                double abs = Math.abs(val);
                // Avoid exponential notation except in extremis
                emit(FloatingPointConverter.convertDouble(val, abs >= 1e18 || abs < 1e-18));
//                if (num.isWholeNumber() && abs < 1e18) {
//                    emit(num.longValue() + "");
//                } else if (abs < 1e18 && abs > 1e-18) {
//                    // Avoid exponential notation except in extremis
//                    emit(Converter.DoubleToDecimal.INSTANCE.convert(num).asAtomic().getUnicodeStringValue());
//                } else {
//                    emit(num.getUnicodeStringValue());
//                }
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
     * Append a singleton string value to the output
     *
     * @param str the string value to be appended
     * @throws XPathException if the operation fails
     */

    public void writeStringValue(String str) throws XPathException {
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

    private String escape(String cs) throws XPathException {
        if (characterMap != null) {
            StringBuilder out = new StringBuilder(cs.length());
            String s = characterMap.map(StringView.of(cs).tidy(), true).toString();
            int prev = 0;
            while (true) {
                int start = s.indexOf((char)0, prev);
                if (start >= 0) {
                    out.append(simpleEscape(s.substring(prev, start)));
                    int end = s.indexOf((char)0, start + 1);
                    out.append(s, start + 1, end);
                    prev = end + 1;
                } else {
                    out.append(simpleEscape(s.substring(prev)));
                    return out.toString();
                }
            }
        } else {
            return simpleEscape(cs);
        }
    }

    private String simpleEscape(String cs) throws XPathException {
        if (normalize) {
            cs = Normalizer.normalize(cs, normalizationForm);
        }
        return JsonReceiver.escape(cs, false, !escapeSolidus,
                                   c -> c < 31 || (c >= 127 && c <= 159) || !characterSet.inCharset(c));
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
        if (first) {
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

