////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntToIntHashMap;
import net.sf.saxon.z.IntToIntMap;

/**
 * Implement the XPath translate() function
 */

public class Translate extends SystemFunction implements Callable, StatefulSystemFunction {

    private IntToIntMap staticMap = null;

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param arguments the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     */
    @Override
    public Expression fixArguments(Expression... arguments) {
        if (arguments[1] instanceof StringLiteral && arguments[2] instanceof StringLiteral) {
            staticMap = buildMap(((StringLiteral) arguments[1]).getValue(), ((StringLiteral) arguments[2]).getValue());
        }
        return null;
    }

    /**
     * Get the translation map built at compile time if there is one
     *
     * @return the map built at compile time, or null if not available
     */

    public IntToIntMap getStaticMap() {
        return staticMap;
    }

    /**
     * Perform the translate function
     *
     * @param sv0 the string to be translated
     * @param sv1 the characters to be substituted
     * @param sv2 the replacement characters
     * @return the converted string
     */

    public static CharSequence translate(StringValue sv0, StringValue sv1, StringValue sv2) {

        // if any string contains surrogate pairs, expand everything to 32-bit characters
        if (sv0.containsSurrogatePairs() || sv1.containsSurrogatePairs() || sv2.containsSurrogatePairs()) {
            return translateUsingMap(sv0, buildMap(sv1, sv2));
        }

        // if the size of the strings is above some threshold, use a hash map to avoid O(n*m) performance
        if (sv0.getStringLength() * sv1.getStringLength() > 1000) {
            // Cut-off point for building the map based on some simple measurements
            return translateUsingMap(sv0, buildMap(sv1, sv2));
        }

        CharSequence cs0 = sv0.getStringValueCS();
        CharSequence cs1 = sv1.getStringValueCS();
        CharSequence cs2 = sv2.getStringValueCS();

        String st1 = cs1.toString();
        FastStringBuffer sb = new FastStringBuffer(cs0.length());
        int s2len = cs2.length();
        int s0len = cs0.length();
        for (int i = 0; i < s0len; i++) {
            char c = cs0.charAt(i);
            int j = st1.indexOf(c);
            if (j < s2len) {
                sb.cat(j < 0 ? c : cs2.charAt(j));
            }
        }
        return sb;
    }

    /**
     * Build an index
     *
     * @param arg1 a string containing characters to be replaced
     * @param arg2 a string containing the corresponding replacement characters
     * @return a map that maps characters to their replacements (or to -1 if the character is to be removed)
     */

    private static IntToIntMap buildMap(StringValue arg1, StringValue arg2) {
        UnicodeString a1 = arg1.getUnicodeString();
        UnicodeString a2 = arg2.getUnicodeString();
        IntToIntMap map = new IntToIntHashMap(a1.uLength(), 0.5);
        // allow plenty of free space, it's better for lookups (though worse for iteration)
        for (int i = 0; i < a1.uLength(); i++) {
            if (!map.find(a1.uCharAt(i))) {
                map.put(a1.uCharAt(i), i > a2.uLength() - 1 ? -1 : a2.uCharAt(i));
            }
            // else no action: duplicate
        }
        return map;
    }

    /**
     * Implement the translate() function using an index built at compile time
     *
     * @param in  the string to be translated
     * @param map index built at compile time, mapping input characters to output characters. The map returns
     *            -1 for a character that is to be deleted from the input string, Integer.MAX_VALUE for a character that is
     *            to remain intact
     * @return the translated character string
     */

    public static CharSequence translateUsingMap(StringValue in, IntToIntMap map) {
        UnicodeString us = in.getUnicodeString();
        int len = us.uLength();
        FastStringBuffer sb = new FastStringBuffer(len);
        for (int i = 0; i < len; i++) {
            int c = us.uCharAt(i);
            int newchar = map.get(c);
            if (newchar == Integer.MAX_VALUE) {
                // character not in map, so is not to be translated
                newchar = c;
            }
            if (newchar != -1) {
                sb.appendWideChar(newchar);
            }
            // else no action, delete the character
        }
        return sb;
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue sv0 = (StringValue) arguments[0].head();
        if (sv0 == null) {
            return StringValue.EMPTY_STRING;
        }
        if (staticMap != null) {
            return new StringValue(translateUsingMap(sv0, staticMap));
        } else {
            StringValue sv1 = (StringValue) arguments[1].head();
            StringValue sv2 = (StringValue) arguments[2].head();
            return new StringValue(translate(sv0, sv1, sv2));
        }
    }

    @Override
    public String getCompilerName() {
        return "TranslateCompiler";
    }

    /**
     * Make a copy of this SystemFunction. This is required only for system functions such as regex
     * functions that maintain state on behalf of a particular caller.
     *
     * @return a copy of the system function able to contain its own copy of the state on behalf of
     * the caller.
     */
    @Override
    public Translate copy() {
        Translate copy = (Translate) SystemFunction.makeFunction(getFunctionName().getLocalPart(), getRetainedStaticContext(), getArity());
        copy.staticMap = staticMap;
        return copy;
    }

}

