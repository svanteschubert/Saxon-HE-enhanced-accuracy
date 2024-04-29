////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.*;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.z.IntIterator;
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
            staticMap = buildMap(((StringLiteral) arguments[1]).getGroundedValue(), ((StringLiteral) arguments[2]).getGroundedValue());
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

    public static StringValue translate(StringValue sv0, StringValue sv1, StringValue sv2) {

        // if the size of the strings is above some threshold, use a hash map to avoid O(n*m) performance
        if (sv0.length() * sv1.length() > 1000) {
            // Cut-off point for building the map based on some simple measurements
            return translateUsingMap(sv0, buildMap(sv1, sv2));
        }


        UnicodeBuilder sb = new UnicodeBuilder(sv0.length32());
        long s2len = sv2.length();
        IntIterator iter = sv0.codePoints();
        while (iter.hasNext()) {
            int c = iter.next();
            long j = sv1.getContent().indexOf(c, 0);
            if (j < s2len) {
                sb.append(j < 0 ? c : sv2.getContent().codePointAt(j));
            }
        }
        return new StringValue(sb.toUnicodeString());
    }

    /**
     * Build an index
     *
     * @param arg1 a string containing characters to be replaced
     * @param arg2 a string containing the corresponding replacement characters
     * @return a map that maps characters to their replacements (or to -1 if the character is to be removed)
     */

    private static IntToIntMap buildMap(StringValue arg1, StringValue arg2) {
        IntToIntMap map = new IntToIntHashMap(arg1.length32(), 0.5);
        // allow plenty of free space, it's better for lookups (though worse for iteration)
        IntIterator iter = arg1.codePoints();
        long arg2len = arg2.length();
        long i=0;
        while (iter.hasNext()) {
            int ch = iter.next();
            if (!map.contains(ch)) {
                map.put(ch, i >= arg2len ? -1 : arg2.getContent().codePointAt(i));
            }
            i++;
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

    public static StringValue translateUsingMap(StringValue in, IntToIntMap map) {
        UnicodeBuilder builder = new UnicodeBuilder(in.length32());
        IntIterator iter = in.codePoints();
        while (iter.hasNext()) {
            int c = iter.next();
            int newchar = map.get(c);
            if (newchar == Integer.MAX_VALUE) {
                // character not in map, so is not to be translated
                newchar = c;
            }
            if (newchar != -1) {
                builder.append(newchar);
            }
            // else no action, delete the character
        }
        return new StringValue(builder.toUnicodeString());
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
            return translateUsingMap(sv0, staticMap);
        } else {
            StringValue sv1 = (StringValue) arguments[1].head();
            StringValue sv2 = (StringValue) arguments[2].head();
            return translate(sv0, sv1, sv2);
        }
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

    /**
     * Make an elaborator for a system function call on this function
     *
     * @return a suitable elaborator; or null if no custom elaborator is available
     */
    @Override
    public Elaborator getElaborator() {
        return new TranslateFnElaborator();
    }

    public static class TranslateFnElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final Translate fn = (Translate) fnc.getTargetFunction();
            final ItemEvaluator arg0Eval = fnc.getArg(0).makeElaborator().elaborateForItem();
            final ItemEvaluator arg1Eval = fnc.getArg(1).makeElaborator().elaborateForItem();
            final ItemEvaluator arg2Eval = fnc.getArg(2).makeElaborator().elaborateForItem();
            final IntToIntMap staticMap = fn.getStaticMap();

            if (staticMap != null) {
                return context -> {
                    StringValue s0 = (StringValue)arg0Eval.eval(context);
                    if (s0 == null || s0.isEmpty()) {
                        return StringValue.EMPTY_STRING;
                    }
                    return translateUsingMap(s0, staticMap);
                };
            } else {
                return context -> {
                    StringValue s0 = (StringValue) arg0Eval.eval(context);
                    if (s0 == null || s0.isEmpty()) {
                        return StringValue.EMPTY_STRING;
                    }
                    StringValue s1 = (StringValue) arg1Eval.eval(context);
                    StringValue s2 = (StringValue) arg2Eval.eval(context);
                    return translate(s0, s1, s2);
                };
            }
        }

    }
}

