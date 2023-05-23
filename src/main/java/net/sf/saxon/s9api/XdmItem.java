////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.streams.XdmStream;
import net.sf.saxon.value.AtomicValue;

import java.util.Map;
import java.util.stream.Stream;

/**
 * The class XdmItem represents an item in a sequence, as defined by the XDM data model.
 * An item may be an atomic value, a node, a function item (including maps and arrays), or an external object.
 * <p>An item is a member of a sequence, but it can also be considered as a sequence
 * (of length one) in its own right. <tt>XdmItem</tt> is a subtype of <tt>XdmValue</tt> because every
 * Item in the XDM data model is also a value.</p>
 * <p>It cannot be assumed that every sequence of length one will be represented by
 * an <tt>XdmItem</tt>. It is quite possible for an <tt>XdmValue</tt> that is not an <tt>XdmItem</tt> to hold
 * a singleton sequence.</p>
 * <p>Saxon provides a number of concrete subclasses of <code>XdmItem</code>, namely {@link XdmAtomicValue},
 * {@link XdmNode}, {@link XdmFunctionItem} and {@link XdmExternalObject}. Users must not attempt to create
 * additional subclasses.</p>
 */

public abstract class XdmItem extends XdmValue {

    // internal protected constructor

    protected XdmItem() {
    }

    /**
     * Construct an XdmItem as a wrapper around an existing Saxon Item object
     *
     * @param item the Item object to be wrapped. This can be retrieved using the
     *             {@link #getUnderlyingValue} method.
     * @throws NullPointerException if item is null
     * @since 9.5 (previously a protected constructor)
     */

    public XdmItem(Item item) {
        super();
        setValue(item);
    }

    // internal factory mathod to wrap an Item

    /*@Nullable*/
    protected static XdmItem wrapItem(Item item) {
        return item == null ? null : (XdmItem) XdmValue.wrap(item);
    }

    protected static XdmNode wrapItem(NodeInfo item) {
        return item == null ? null : (XdmNode) XdmValue.wrap(item);
    }

    protected static XdmAtomicValue wrapItem(AtomicValue item) {
        return item == null ? null : (XdmAtomicValue) XdmValue.wrap(item);
    }

    /**
     * Get the underlying implementation object representing the value. This method allows
     * access to lower-level Saxon functionality, including classes and methods that offer
     * no guarantee of stability across releases.
     *
     * @return the underlying implementation object representing the value
     * @since 9.8 (previously inherited from XdmValue which returns a Sequence)
     */
    @Override
    public Item getUnderlyingValue() {
        return (Item)super.getUnderlyingValue();
    }

    /**
     * Get the string value of the item. For a node, this gets the string value
     * of the node. For an atomic value, it has the same effect as casting the value
     * to a string. In all cases the result is the same as applying the XPath string()
     * function.
     * <p>For atomic values, the result is the same as the result of calling
     * <code>toString</code>. This is not the case for nodes, where <code>toString</code>
     * returns an XML serialization of the node.</p>
     *
     * @return the result of converting the item to a string.
     */

    public String getStringValue() {
        return getUnderlyingValue().getStringValue();
    }

    /**
     * Determine whether the item is a node or some other type of item
     *
     * @return true if the item is a node, false if it is an atomic value or a function (including maps and arrays)
     * @since 10.0
     */

    public boolean isNode() {
        return getUnderlyingValue() instanceof NodeInfo;
    }

    /**
     * Determine whether the item is an atomic value or some other type of item
     *
     * @return true if the item is an atomic value, false if it is a node or a function (including maps and arrays)
     */

    public boolean isAtomicValue() {
        return getUnderlyingValue() instanceof AtomicValue;
    }

    /**
     * Get the number of items in the sequence
     *
     * @return the number of items in the value. For an item (including a map or array) this is always 1 (one).
     */

    @Override
    public int size() {
        return 1;
    }

    /**
     * If this item is a map, return a corresponding Java Map.
     *
     * @return if this item is a map, return a mutable Map from atomic values to (sequence) values, containing the
     * same entries as this map. Otherwise return null.
     * @since 9.6.
     */

    public Map<XdmAtomicValue, XdmValue> asMap() {
        return null; // Overridden in XdmMap. The method is retained on this interface for compatibility reasons.
    }

    /**
     * Get a stream comprising the items in this value
     *
     * @return a Stream over the items in this value
     * @since 9.9
     */
    @Override
    public XdmStream<? extends XdmItem> stream() {
        return new XdmStream<>(Stream.of(this));
    }



    /**
     * Determine whether this item matches a given item type.
     *
     * @param type the item type to be tested against this item
     * @return true if the item matches this item type, false if it does not match.
     * @since 9.9
     */
    public boolean matches(ItemType type){
        return type.matches(this);
    }

}

