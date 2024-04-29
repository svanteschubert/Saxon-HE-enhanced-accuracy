////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.trans.XPathException;

/**
 * A simple class for holding stateful details of a rule search operation
 * This is a dummy implementation for Saxon-HE which does not optimize searching a rule chain.
 * It is subclassed for Saxon-EE.
 */
public class RuleSearchState {

    @SuppressWarnings("InstantiationOfUtilityClass")
    private final static RuleSearchState THE_INSTANCE = new RuleSearchState();

    public static RuleSearchState getInstance() {
        return THE_INSTANCE;
    }

    protected RuleSearchState() {}

    /**
     * Use this search state to check whether the preconditions for a particular rule
     * are satisfied
     * @param rule the rule being tested
     * @return true if all preconditions are satisfied; in the HE implementation, there
     * are no preconditions, so this is always true.
     * @throws XPathException (in a subclass) if evaluation of preconditions fails.
     */
    public boolean checkPreconditions(Rule rule) throws XPathException {
        return true;
    }


}

