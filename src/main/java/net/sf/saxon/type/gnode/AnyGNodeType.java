////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.gnode;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.Item;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.type.ChoiceItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;
import net.sf.saxon.type.coercion.CoercionPlan;

import java.util.Optional;

/**
 * Class {@code AnyGNodeType} corresponds to the item type {@code gnode()}. It
 * matches any XNode or JNode.
 */

public final class AnyGNodeType extends GNodeType {

    private static final AnyGNodeType THE_INSTANCE = new AnyGNodeType();

    /**
     * Get an instance of AnyNodeTest
     * @return the singleton instance of this class
     */

    public static AnyGNodeType getInstance() {
        return THE_INSTANCE;
    }

    /**
     * Private constructor
     */

    private AnyGNodeType() {
    }

    /**
     * Get the corresponding {@link UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.GNODE;
    }

    @Override
    public NodeTest asXNodeTest(Configuration config) {
        return AnyXNodeType.getInstance();
    }

    /**
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     *
     * @param version the XPath language version (40 or 31)
     */
    @Override
    public CoercionPlan getCoercionPlan(int version) {
//        if (version < 4.0) {
//            return super.getCoercionPlan(version);
//        } else {
//            return GNodeCoercionPlan.getInstance();
//        }
        return null;
    }

    public ChoiceItemType asChoiceItemType() {
        return ChoiceItemType.CHOICE_OF_GNODE;
    }


    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */

    @Override
    public boolean matches(Item item) {
        return item instanceof GNode;
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
        return "gnode()";
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
        return "G";
    }

    /**
     * Get extra diagnostic information about why a supplied item does not conform to this
     * item type, if available. If extra information is returned, it should be in the form of a complete
     * sentence, minus the closing full stop. No information should be returned for obvious cases.
     *
     * @param item the item that doesn't match this type
     * @param th   the type hierarchy cache
     * @return optionally, a message explaining why the item does not match the type
     */
    @Override
    public Optional<String> explainMismatch(Item item, TypeHierarchy th) {
        return Optional.empty();
    }

    /**
     * Determine whether the content type (if present) is nillable
     *
     * @return true if the content test (when present) can match nodes that are nilled
     */
    @Override
    public boolean isNillable() {
        return true;
    }
}

