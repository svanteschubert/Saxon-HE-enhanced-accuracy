// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.jnode;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.Genre;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;
import net.sf.saxon.type.coercion.CoercionPlan;
import net.sf.saxon.type.coercion.JNodeCoercionPlan;
import net.sf.saxon.type.gnode.GNodeType;
import net.sf.saxon.value.SequenceType;

public abstract class JNodeType extends GNodeType {

    public SequenceType getValueType() {
        return SequenceType.ANY_SEQUENCE;
    }

    /**
     * Determine the Genre (top-level classification) of this type
     *
     * @return the Genre to which this type belongs, for example node or atomic value
     */
    @Override
    public Genre getGenre() {
        return Genre.JNODE;
    }

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue and union types it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that integer, xs:dayTimeDuration, and xs:yearMonthDuration
     * are considered to be primitive types.
     *
     * @return the corresponding primitive type
     */
    @Override
    public ItemType getPrimitiveItemType() {
        return AnyJNodeType.getInstance();
    }

    /**
     * Get the primitive type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is BuiltInAtomicType.ANY_ATOMIC. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     *
     * @return the integer fingerprint of the corresponding primitive type
     */
    @Override
    public int getPrimitiveType() {
        return Type.JNODE;
    }

    /**
     * Get the corresponding {@link UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.JNODE;
    }

    /**
     * Get the default priority when this ItemType is used as an XSLT pattern
     *
     * @return the default priority
     */
    @Override
    public double getDefaultPriority() {
        return 0;
    }

    /**
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     *
     * @param version the XPath language version (40 or 31)
     */
    @Override
    public CoercionPlan getCoercionPlan(int version) {
        return JNodeCoercionPlan.getInstance();
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
        return "J";
    }

    @Override
    public NodeTest asXNodeTest(Configuration config) {
        return ErrorType.getInstance();
    }
}

