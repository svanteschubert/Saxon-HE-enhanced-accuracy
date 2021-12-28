////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.expr.sort.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ErrorType;

import java.util.Properties;

/**
 * Abstract superclass for functions that take an optional collation argument, in which the
 * collation is not present as an explicit argument, either because it was defaulted in the
 * original function call, or because it has been bound during static analysis.
 */


public abstract class CollatingFunctionFixed extends SystemFunction implements StatefulSystemFunction {

    // The absolute collation URI
    private String collationName;

    // The collation corresponding to this collation name
    private StringCollator stringCollator = null;

    // The AtomicComparer to be used (not used by all collating functions)
    private AtomicComparer atomicComparer = null;

    /**
     * Ask whether this function needs a collation that can handle substring matching
     *
     * @return true in the case of functions such as contains() and starts-with() where
     * substring matching is required. Returns false by default;
     */

    public boolean isSubstringMatchingFunction() {
        return false;
    }


    /**
     * Get the collation if known statically, as a StringCollator object
     *
     * @return a StringCollator. Return null if the collation is not known statically.
     */

    public StringCollator getStringCollator() {
        return stringCollator;
    }

    @Override
    public void setRetainedStaticContext(RetainedStaticContext retainedStaticContext) {
        super.setRetainedStaticContext(retainedStaticContext);
        if (collationName == null) {
            collationName = retainedStaticContext.getDefaultCollationName();
            try {
                allocateCollator();
            } catch (XPathException e) {
                // ignore the failure, it will be reported later
            }
        }
    }

    public void setCollationName(String collationName) throws XPathException {
        this.collationName = collationName;
        allocateCollator();
    }

    private void allocateCollator() throws XPathException {
        stringCollator = getRetainedStaticContext().getConfiguration().getCollation(collationName);
        if (stringCollator == null) {
            throw new XPathException("Unknown collation " + collationName, "FOCH0002");
        }
        if (isSubstringMatchingFunction()) {
            if (stringCollator instanceof SimpleCollation) {
                stringCollator = ((SimpleCollation) stringCollator).getSubstringMatcher();
            }
            if (!(stringCollator instanceof SubstringMatcher)) {
                throw new XPathException("The collation requested for " + getFunctionName().getDisplayName() +
                                                 " does not support substring matching", "FOCH0004");
            }
        }
    }

    /**
     * During static analysis, if types are known and the collation is known, pre-allocate a comparer
     * for comparing atomic values. Called by some collating functions during type-checking. The comparer
     * that is allocated treats NaN as not equal to NaN.
     * @param type0        the type of the first comparand
     * @param type1        the type of the second comparand
     * @param env          the static context
     */

    protected void preAllocateComparer(AtomicType type0, AtomicType type1, StaticContext env) {
        StringCollator collation = getStringCollator();
        if (type0 == ErrorType.getInstance() || type1 == ErrorType.getInstance()) {
            // there will be no instances to compare, so we can use any comparer
            atomicComparer = EqualityComparer.getInstance();
            return;
        }

        atomicComparer = GenericAtomicComparer.makeAtomicComparer(
                (BuiltInAtomicType) type0.getBuiltInBaseType(), (BuiltInAtomicType) type1.getBuiltInBaseType(),
                stringCollator, env.makeEarlyEvaluationContext());

    }


    /**
     * Get the pre-allocated atomic comparer, if available
     *
     * @return the preallocated atomic comparer, or null
     */

    public AtomicComparer getPreAllocatedAtomicComparer() {
        return atomicComparer;
    }

    /**
     * During evaluation, get the pre-allocated atomic comparer if available, or allocate a new one otherwise
     *
     * @param context the dynamic evaluation context
     * @return the pre-allocated comparer if one is available; otherwise, a newly allocated one, using the specified
     * StringCollator for comparing strings
     */

    public AtomicComparer getAtomicComparer(XPathContext context) {
        if (atomicComparer != null) {
            return atomicComparer.provideContext(context);
        } else {
            return new GenericAtomicComparer(getStringCollator(), context);
        }
    }

    @Override
    public void exportAttributes(ExpressionPresenter out) {
        if (!collationName.equals(NamespaceConstant.CODEPOINT_COLLATION_URI)) {
            out.emitAttribute("collation", collationName);
        }
    }

    @Override
    public void importAttributes(Properties attributes) throws XPathException {
        String collationName = attributes.getProperty("collation");
        if (collationName != null) {
            setCollationName(collationName);
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
    public CollatingFunctionFixed copy() {
        SystemFunction copy = SystemFunction.makeFunction(getFunctionName().getLocalPart(), getRetainedStaticContext(), getArity());
        if (copy instanceof CollatingFunctionFree) {
            try {
                copy = ((CollatingFunctionFree) copy).bindCollation(collationName);
            } catch (XPathException e) {
                throw new AssertionError(e);
            }
        }
        if (copy instanceof CollatingFunctionFixed) {
            ((CollatingFunctionFixed) copy).collationName = collationName;
            ((CollatingFunctionFixed) copy).atomicComparer = atomicComparer;
            ((CollatingFunctionFixed) copy).stringCollator = stringCollator;
            return (CollatingFunctionFixed) copy;
        }
        throw new IllegalStateException();
    }


}

