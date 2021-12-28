////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;

/**
 * The UniversalPattern matches everything
 *
 * @author Michael H. Kay
 */

public class UniversalPattern extends Pattern {

    /**
     * Create an UniversalPattern that matches all items
     */

    public UniversalPattern() {
        setPriority(-1);
    }

    /**
     * Determine whether this Pattern matches the given Node. This is the main external interface
     * for matching patterns: it sets current() to the node being tested
     *
     * @param item    The item to be tested against the Pattern
     * @param context The context in which the match is to take place.
     * @return true if the item matches the Pattern, false otherwise
     */

    @Override
    public boolean matches(Item item, XPathContext context) {
        return true;
    }

    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */
    @Override
    public UType getUType() {
        return UType.ANY;
    }

    /**
     * Get a NodeTest that all the nodes matching this pattern must satisfy
     */

    @Override
    public ItemType getItemType() {
        return AnyItemType.getInstance();
    }

    /**
     * Determine the name fingerprint of nodes to which this pattern applies. Used for
     * optimisation.
     *
     * @return A fingerprint that the nodes must match, or -1 if it can match multiple fingerprints
     */

    @Override
    public int getFingerprint() {
        return -1;
    }

    /**
     * Display the pattern for diagnostics
     */

    @Override
    public String reconstruct() {
        return ".";
    }

    @Override
    public void export(ExpressionPresenter presenter) {
        presenter.startElement("p.any");
        presenter.endElement();
    }

    /**
     * Determine whether this pattern is the same as another pattern
     *
     * @param other the other object
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return other instanceof UniversalPattern;
    }

    /**
     * Hashcode supporting equals()
     */

    @Override
    public int computeHashCode() {
        return 0x7aeccea8;
    }

    /**
     * Copy a UniversalPattern.
     * Since there is only one, return the same.
     *
     * @return the original nodeTest
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Pattern copy(RebindingMap rebindings) {
        return this;
    }
}

