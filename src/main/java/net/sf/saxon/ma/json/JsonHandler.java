////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.function.IntPredicate;

import java.util.Map;

/**
 * Default handler class for accepting the result from parsing JSON strings
 */
public class JsonHandler {

    public boolean escape;
    protected IntPredicate charChecker;
    private XPathContext context;

    private Function fallbackFunction = null;
    private static final String REPLACEMENT = "\ufffd";

    public void setContext(XPathContext context) {
        this.context = context;
    }

    public XPathContext getContext() {
        return context;
    }

    public Sequence getResult() throws XPathException {
        return null;
    }

    /**
     * Set the key to be written for the next entry in an object/map
     *
     * @param unEscaped the key for the entry (null implies no key) in unescaped form (backslashes,
     *                  if present, do not signal an escape sequence)
     * @param reEscaped the key for the entry (null implies no key) in reescaped form. In this form
     *                  special characters are represented as backslash-escaped sequences if the escape
     *                  option is yes; if escape=no, the reEscaped form is the same as the unEscaped form.
     * @return true if the key is already present in the map, false if it is not
     */
    public boolean setKey(String unEscaped, String reEscaped) {
        return false;
    }

    /**
     * Open a new array
     *
     * @throws XPathException if any error occurs
     */
    public void startArray() throws XPathException {}

    /**
     * Close the current array
     *
     * @throws XPathException if any error occurs
     */
    public void endArray() throws XPathException {}

    /**
     * Start a new object/map
     *
     * @throws XPathException if any error occurs
     */
    public void startMap() throws XPathException {}

    /**
     * Close the current object/map
     *
     * @throws XPathException if any error occurs
     */
    public void endMap() throws XPathException {}

    /**
     * Write a numeric value
     *
     * @param asString the string representation of the value
     * @param asDouble the double representation of the value
     * @throws XPathException if any error occurs
     */
    public void writeNumeric(String asString, double asDouble) throws XPathException {}

    /**
     * Write a string value
     *
     * @param val The string to be written (which may or may not contain JSON escape sequences, according to the
     * options that were set)
     * @throws XPathException if any error occurs
     */
    public void writeString(String val) throws XPathException {}

    /**
     * Optionally apply escaping or unescaping to a value.
     * @param val the string to be escaped or unEscaped
     * @return the escaped or unescaped string
     * @throws XPathException
     */

    public String reEscape(String val) throws XPathException {
        CharSequence escaped;
        if (escape) {
            escaped = JsonReceiver.escape(val, true, new IntPredicate() {
                @Override
                public boolean test(int value) {
                    return (value >= 0 && value <= 0x1F) ||
                            (value >= 0x7F && value <= 0x9F) ||
                            !charChecker.test(value) ||
                            (value == 0x5C);
                }
            });
        } else {
            FastStringBuffer buffer = new FastStringBuffer(val);
            handleInvalidCharacters(buffer);
            escaped = buffer;
        }
        return escaped.toString();
    }

    /**
     * Write a boolean value
     * @param value the boolean value to be written
     * @throws XPathException if any error occurs
     */
    public void writeBoolean(boolean value) throws XPathException {}

    /**
     * Write a null value
     *
     * @throws XPathException if any error occurs
     */
    public void writeNull() throws XPathException {}

    /**
     * Deal with invalid characters in the JSON string
     * @param buffer the JSON string
     * @throws XPathException if any error occurs
     */
    protected void handleInvalidCharacters(FastStringBuffer buffer) throws XPathException {
        //if (checkSurrogates && !liberal) {
            IntPredicate charChecker = context.getConfiguration().getValidCharacterChecker();
            for (int i = 0; i < buffer.length(); i++) {
                char ch = buffer.charAt(i);
                if (UTF16CharacterSet.isHighSurrogate(ch)) {
                    if (i + 1 >= buffer.length() || !UTF16CharacterSet.isLowSurrogate(buffer.charAt(i + 1))) {
                        substitute(buffer, i, 1, context);
                    }
                } else if (UTF16CharacterSet.isLowSurrogate(ch)) {
                    if (i == 0 || !UTF16CharacterSet.isHighSurrogate(buffer.charAt(i - 1))) {
                        substitute(buffer, i, 1, context);
                    } else {
                        int pair = UTF16CharacterSet.combinePair(buffer.charAt(i - 1), ch);
                        if (!charChecker.test(pair)) {
                            substitute(buffer, i - 1, 2, context);
                        }
                    }
                } else {
                    if (!charChecker.test(ch)) {
                        substitute(buffer, i, 1, context);
                    }
                }
            }
        //}
    }

    protected void markAsEscaped(CharSequence escaped, boolean isKey) throws XPathException {
        // do nothing in this class
    }

    /**
     * Replace an character or two characters within a string buffer, either by executing the replacement function,
     * or using the default Unicode replacement character
     *
     * @param buffer the string buffer, which is modified by this call
     * @param offset the position of the characters to be replaced
     * @param count the number of characters to be replaced
     * @param context the XPath context
     * @throws XPathException if the callback function throws an exception
     */
    private void substitute(FastStringBuffer buffer, int offset, int count, XPathContext context) throws XPathException {
        FastStringBuffer escaped = new FastStringBuffer(count*6);
        for (int j=0; j<count; j++) {
            escaped.append("\\u");
            String hex = Integer.toHexString(buffer.charAt(offset + j));
            while (hex.length() < 4) {
                hex = "0" + hex;
            }
            hex = hex.toUpperCase(); // cheat to get through test json-to-xml-039
            escaped.append(hex);
        }
        String replacement = replace(escaped.toString(), context);
        if (replacement.length() == count) {
            for (int j = 0; j < count; j++) {
                buffer.setCharAt(offset + j, replacement.charAt(j));
            }
        } else {
            for (int j = 0; j < count; j++) {
                buffer.removeCharAt(offset + j);
            }
            for (int j=0; j < replacement.length(); j++) {
                buffer.insert(offset + j, replacement.charAt(j));
            }
        }
    }

    /**
     * Replace an illegal XML character, either by executing the replacement function,
     * or using the default Unicode replacement character
     *
     * @param s       the string representation of the illegal character
     * @param context the XPath context
     * @return the replacement string
     * @throws XPathException if the callback function throws an exception
     */
    private String replace(String s, XPathContext context) throws XPathException {
        if (fallbackFunction != null) {
            Sequence[] args = new Sequence[1];
            args[0] = new StringValue(s);
            Sequence result = SystemFunction.dynamicCall(fallbackFunction, context, args).head();
            Item first = result.head();
            return first == null ? "" : first.getStringValue();
        } else {
            return REPLACEMENT;
        }
    }

    public void setFallbackFunction(Map<String, Sequence> options, XPathContext context) throws XPathException {
        Sequence val = options.get("fallback");
        if (val != null) {
            Item fn = val.head();
            if (fn instanceof Function) {
                fallbackFunction = (Function) fn;
                if (fallbackFunction.getArity() != 1) {
                    throw new XPathException("Fallback function must have arity=1", "FOJS0005");
                }
                SpecificFunctionType required = new SpecificFunctionType(
                        new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.ANY_SEQUENCE);
                if (!required.matches(fallbackFunction, context.getConfiguration().getTypeHierarchy())) {
                    throw new XPathException("Fallback function does not match the required type", "FOJS0005");
                }
            } else {
                throw new XPathException("Value of option 'fallback' is not a function", "FOJS0005");
            }
        }
    }
}

// Copyright (c) 2018-2020 Saxonica Limited
