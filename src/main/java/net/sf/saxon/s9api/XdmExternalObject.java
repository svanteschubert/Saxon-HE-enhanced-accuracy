////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.value.ExternalObject;
import net.sf.saxon.value.ObjectValue;

/**
 * The class XdmExternalObject represents an XDM item that wraps an external (Java or .NET) object.
 * As such, it is outside the scope of the XDM specification (but permitted as an extension).
 * <p>In releases prior to 9.5, external objects in Saxon were represented as atomic values. From
 * 9.5 they are represented as a fourth kind of item, alongside nodes, atomic values, and functions.</p>
 */
public class XdmExternalObject extends XdmItem {

    private XdmExternalObject(){}

    /**
     * Create an XdmExternalObject that wraps a supplied Java object
     * @param value the supplied Java object. Must not be null.
     * @throws NullPointerException if the supplied value is null.
     */

    public XdmExternalObject(Object value) {
        super(value instanceof ObjectValue ? (ObjectValue)value : new ObjectValue<>(value));
    }

    /**
     * Get the wrapped Java object
     *
     * @return the wrapped object
     */

    public Object getExternalObject() {
        return ((ExternalObject) getUnderlyingValue()).getObject();
    }

    /**
     * Get the result of converting the external value to a string.
     * @return the result of applying toString() to the wrapped external object.
     */

    public String toString() {
        return getExternalObject().toString();
    }

    /**
     * Compare two external objects for equality. Two instances of {@code XdmExternalObject} are equal
     * if the Java objects that they wrap are equal.
     * @param other the object to be compared
     * @return true if the other object is an {@code XdmExternalObject} and the two wrapped objects
     * are equal under the {@code equals()} method.
     */

    public boolean equals(Object other) {
        return other instanceof XdmExternalObject
                && getUnderlyingValue().equals(((XdmExternalObject)other).getUnderlyingValue());
    }

    /**
     * Return a hash code for the object. This respects the semantics of {@link #equals(Object)};
     * @return a suitable hash code
     */

    public int hashCode() {
        return getUnderlyingValue().hashCode();
    }


}

