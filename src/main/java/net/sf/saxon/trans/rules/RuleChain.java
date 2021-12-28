////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

/**
 * A chain of rules: actually in this incarnation just a pointer to the first rule in the chain, the others
 * being linked together each to the next.
 */
public class RuleChain {

    private Rule head;
    public Object optimizationData; // give this a better type

    /**
     * Create an empty rule chain
     */

    public RuleChain() {
        head = null;
    }

    /**
     * Create an rule chain with a give rule as the head of the chain
     * @param head the head of a chain of rules
     */

    public RuleChain(Rule head) {
        this.head = head;
    }

    /**
     * Get the first rule in the chain, or null if the chain is empty
     * @return the first rule in the chain, or null if empty
     */

    public Rule head() {
        return head;
    }

    /**
     * Set the first rule in the chain, or null if the chain is empty
     * @param head the first rule in the chain, or null if empty
     */

    public void setHead(Rule head) {
        this.head = head;
    }

    /**
     * Get the length of the rule chain
     * @return the number of rules in the chain
     */

    public int getLength() {
        int i = 0;
        Rule r = head();
        while (r != null) {
            i++;
            r = r.getNext();
        }
        return i;
    }
}

