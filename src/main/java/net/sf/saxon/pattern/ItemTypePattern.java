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
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AlphaCode;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;

/**
 * An ItemTypePattern is a pattern that consists simply of an ItemType; although
 * a node test is an item type, this class is used only for non-node item types,
 * such as atomic types, map types, array types etc.
 */

public class ItemTypePattern extends Pattern {

    private ItemType itemType;


    /**
     * Create an ItemTypePattern that matches all items of a given type
     *
     * @param test the type that the items must satisfy for the pattern to match
     */

    public ItemTypePattern(ItemType test) {
        itemType = test;
        setPriority(test.getDefaultPriority());
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
    public boolean matches(Item item, XPathContext context) throws XPathException {
        return itemType.matches(item, context.getConfiguration().getTypeHierarchy());
    }

    /**
     * Get an ItemType that all the items matching this pattern must satisfy
     *
     * @return an ItemType, as specific as possible, which all the matching items satisfy
     */

    @Override
    public ItemType getItemType() {
        return itemType;
    }

    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */
    @Override
    public UType getUType() {
        return itemType.getUType();
    }

    /**
     * Display the pattern for diagnostics
     */

    @Override
    public String reconstruct() {
        return itemType.toString();
    }

    /**
     * Determine whether this pattern is the same as another pattern
     *
     * @param other the other object
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return (other instanceof ItemTypePattern) &&
                ((ItemTypePattern) other).itemType.equals(itemType);
    }

    /**
     * Hashcode supporting equals()
     */

    @Override
    public int computeHashCode() {
        return 0x7a83d1a8 ^ itemType.hashCode();
    }

    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("p.nodeTest");
        presenter.emitAttribute("test", AlphaCode.fromItemType(itemType));
        presenter.endElement();
    }

    /**
     * Copy a pattern. This makes a deep copy.
     *
     * @return the copy of the original pattern
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Pattern copy(RebindingMap rebindings) {
        // (is this necessary? I think the class is immutable...)
        return new ItemTypePattern(itemType);
    }


}

