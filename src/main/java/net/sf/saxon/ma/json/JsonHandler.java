////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.z.IntPredicateProxy;

import java.util.Map;

/**
 * Default handler class for accepting the result from parsing JSON strings
 */
public class JsonHandler {

    public boolean escape;
    protected IntPredicateProxy charChecker;
    private XPathContext context;

    private FunctionItem fallbackFunction = null;

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
     * @param key the key for the entry (null implies no key) in unescaped form (backslashes,
     *            if present, do not signal an escape sequence)
     * @return true if the key is already present in the map, false if it is not
     */
    public boolean setKey(UnicodeString key) {
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
     * @param asString the raw string representation of the value
     * @param parsedValue the parsed representation of the value, typically an xs:double, but under user control
     * @throws XPathException if any error occurs
     */
    public void writeNumeric(String asString, Item parsedValue) throws XPathException {}

    /**
     * Write a string value
     *
     * @param val The string to be written (which may or may not contain JSON escape sequences, according to the
     * options that were set)
     * @throws XPathException if any error occurs
     */
    public void writeString(UnicodeString val) throws XPathException {}

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

    protected void markAsEscaped(UnicodeString escaped, boolean isKey) throws XPathException {
        // do nothing in this class
    }

    public void setFallbackFunction(Map<String, GroundedValue> options, XPathContext context) throws XPathException {
        GroundedValue val = options.get("fallback");
        if (val != null) {
            Item fn = val.head();
            if (fn instanceof FunctionItem) {
                fallbackFunction = (FunctionItem) fn;
                if (fallbackFunction.getArity() != 1) {
                    throw new XPathException("Fallback function must have arity=1", "FOJS0005");
                }
                SpecificFunctionType required = new SpecificFunctionType(
                        SequenceType.SINGLE_STRING, SequenceType.ANY_SEQUENCE);
                if (!required.matches(fallbackFunction)) {
                    throw new XPathException("Fallback function does not match the required type", "XPTY0004");
                }
            } else {
                throw new XPathException("Value of option 'fallback' is not a function", "XPTY0004");
            }
        }
    }

    public FunctionItem getFallbackFunction() {
        return fallbackFunction;
    }
}

// Copyright (c) 2018-2026 Saxonica Limited
