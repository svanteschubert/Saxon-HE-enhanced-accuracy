////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.gnode;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.qname.AnyQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.type.ChoiceItemType;
import net.sf.saxon.type.UType;

/**
 * An {@code AnyXNodeType} corresponds to the item type {@code node()}, which matches any XNode.
 */

public final class AnyXNodeType extends XNodeType {

    private static final AnyXNodeType THE_INSTANCE = new AnyXNodeType();

    /**
     * Get an instance of AnyNodeTest
     * @return the singleton instance of this class
     */

    public static AnyXNodeType getInstance() {
        return THE_INSTANCE;
    }

    /**
     * Private constructor
     */

    private AnyXNodeType() {
    }

    /**
     * Get the set of allowed node names that this type if capable
     * of matching
     *
     * @return the allowed node names
     */
    @Override
    public QNameTest getAllowedNodeNames() {
        return AnyQNameTest.getInstance();
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item) {
        return item instanceof NodeInfo;
    }

    /**
     * Get an alphabetic code representing the type, or at any rate, the nearest built-in type
     * from which this type is derived. The codes are designed so that for any two built-in types
     * A and B, alphaCode(A) is a prefix of alphaCode(B) if and only if A is a supertype of B.
     *
     * @return the alphacode for the nearest containing built-in type. For example: for xs:string
     * return "AS", for xs:boolean "AB", for node() "N", for element() "NE", for map(*) "FM", for
     * array(*) "FA".
     */
    @Override
    public String getBasicAlphaCode() {
        return "N";
    }

    /**
     * Get the corresponding {@link net.sf.saxon.type.UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.XNODE;
    }


    public ChoiceItemType asChoiceItemType() {
        return ChoiceItemType.CHOICE_OF_XNODE;
    }

    /**
     * Determine the default priority of this node test when used on its own as a Pattern
     */

    @Override
    public final double getDefaultPriority() {
        return -0.5;
    }


    /*@NotNull*/
    public String toString() {
        return "node()";
    }


}

