////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.QNameValue;

import java.util.HashMap;

/**
 * A user-defined function that is declared as a memo function, meaning that it remembers results
 * of previous calls.
 */

public class MemoFunction extends UserFunction {

    /**
     * Determine the preferred evaluation mode for this function
     */

    @Override
    public void computeEvaluationMode() {
        evaluator = ExpressionTool.eagerEvaluator(getBody());
    }

    /**
     * Ask whether this function is a memo function
     *
     * @return true if this function is marked as a memo function
     */

    @Override
    public boolean isMemoFunction() {
        return true;
    }

    /**
     * Call this function to return a value.
     *
     * @param actualArgs the arguments supplied to the function. These must have the correct
     *                   types required by the function signature (it is the caller's responsibility to check this).
     *                   It is acceptable to supply a {@link net.sf.saxon.value.Closure} to represent a value whose
     *                   evaluation will be delayed until it is needed. The array must be the correct size to match
     *                   the number of arguments: again, it is the caller's responsibility to check this.
     * @param context    This provides the run-time context for evaluating the function. It is the caller's
     *                   responsibility to allocate a "clean" context for the function to use; the context that is provided
     *                   will be overwritten by the function.
     * @return a Value representing the result of the function.
     */

    @Override
    public Sequence call(XPathContext context, Sequence[] actualArgs) throws XPathException {

        // See if the result is already known
        String key = getCombinedKey(actualArgs);
        Controller controller = context.getController();
        HashMap<String, Sequence> map = (HashMap<String, Sequence>) controller.getUserData(this, "memo-function-cache");
        Sequence value = map == null ? null : map.get(key);
        if (value != null) {
            return value;
        }

        value = super.call(context, actualArgs);

        // Save the result in the cache
        if (map == null) {
            map = new HashMap<>(32);
            controller.setUserData(this, "memo-function-cache", map);
        }
        map.put(key, value);

        return value;
    }

    /**
     * Get a key value representing the values of all the supplied arguments
     *
     * @param params the supplied values of the function arguments
     * @return a key value which can be used to index the cache of remembered function results
     * @throws XPathException if an error occurs evaluating the key, for example because one
     *                        of the supplied parameters uses lazy evaluation
     */

    private static String getCombinedKey(Sequence[] params) throws XPathException {
        FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C256);

        for (Sequence val : params) {
            SequenceIterator iter = val.iterate();
            Item item;
            while ((item = iter.next()) != null) {
                if (item instanceof NodeInfo) {
                    NodeInfo node = (NodeInfo) item;
                    node.generateId(sb);
                } else if (item instanceof QNameValue) {
                    sb.cat(Type.displayTypeName(item)).cat('/').cat(((QNameValue) item).getClarkName());
                } else if (item instanceof AtomicValue) {
                    sb.cat(Type.displayTypeName(item)).cat('/').cat(item.getStringValueCS());
                } else if (item instanceof Function) {
                    sb.cat(item.getClass().getName()).cat("@").cat(""+System.identityHashCode(item));
                }
                sb.cat('\u0001');
            }
            sb.cat('\u0002');
        }
        return sb.toString();
    }

}



