////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.Genre;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.ObjectValue;

/**
 * This class represents the type of an external object returned by
 * an extension function, or supplied as an external variable/parameter.
 */
public class AnyExternalObjectType implements ItemType {

    public static AnyExternalObjectType THE_INSTANCE = new AnyExternalObjectType();

    protected AnyExternalObjectType() {}

    @Override
    public boolean isAtomicType() {
        return false;
    }

    @Override
    public boolean matches(Item item, TypeHierarchy th) throws XPathException {
        return item instanceof ObjectValue;
    }

    @Override
    public boolean isPlainType() {
        return false;
    }

    @Override
    public int getPrimitiveType() {
        return -1;
    }

    /**
     * Get an alphabetic code representing the type, or at any rate, the nearest built-in type
     * from which this type is derived. The codes are designed so that for any two built-in types
     * A and B, alphaCode(A) is a prefix of alphaCode(B) if and only if A is a supertype of B.
     *
     * @return the alphacode for the nearest containing built-in type
     */
    @Override
    public String getBasicAlphaCode() {
        return "X";
    }

    @Override
    public ItemType getPrimitiveItemType() {
        return this;
    }

    @Override
    public UType getUType() {
        return UType.EXTENSION;
    }

    @Override
    public AtomicType getAtomizedItemType() {
        return BuiltInAtomicType.STRING;
    }

    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return true;
    }

    @Override
    public Genre getGenre() {
        return Genre.EXTERNAL;
    }

    /**
     * Get the default priority when this ItemType is used as an XSLT pattern
     *
     * @return the default priority
     */
    @Override
    public double getDefaultPriority() {
        return -1;
    }
}
