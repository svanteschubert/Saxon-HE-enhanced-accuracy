////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.om.Genre;
import net.sf.saxon.trans.Err;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

import java.util.Objects;


/**
 * An XPath value that encapsulates a Java object. Such a value can only be constructed from
 * Java code either in a Java extension function, or in the calling application.
 * <p>Until Saxon 9.4, ObjectValue was a subclass of AtomicValue, and external Java objects were
 * considered to be atomic. From 9.5, this has been changed so that an ObjectValue is another
 * kind of Item: a fourth kind, at the same level as nodes, atomic values, and function items.</p>
 * <p>In the same way as ObjectValue is a wrapper around a Java instance object, the type of the
 * value is a wrapper around the corresponding Java class. These types have subtype-supertype
 * relationships that correspond to the Java class hierarchy. The top type in this branch of
 * the class hierarchy, which wraps the Java class "Object", has item() as its superclass.
 * These types can be referred to in the SequenceType syntax using QNames of the form
 * jt:full.package.name.ClassName, where the conventional prefix jt maps to the namespace
 * URI http://saxon.sf.net/java-type.</p>
 * <p>An ObjectValue is no longer permitted to wrap a Java null. An empty sequence should be
 * used in this situation.</p>
 * <p>The effective boolean value of a wrapped Java object, like that of a node, is always true.</p>
 * <p>Atomizing a wrapped Java object, or applying the {@code fn:string()} function, returns
 * an {@code xs:string} whose value is the result of calling {@code toString()} on the wrapped
 * Java object.</p>
 */

public class ObjectValue<T> implements ExternalObject<T> {

    /*@NotNull*/ private T value;

    /**
     * Constructor
     *
     * @param object the object to be encapsulated
     */

    public ObjectValue(/*@NotNull*/ T object) {
        value = Objects.requireNonNull(object, "External object cannot wrap a Java null");
    }

    /**
     * Get the genre of this item
     *
     * @return the genre
     */
    @Override
    public Genre getGenre() {
        return Genre.EXTERNAL;
    }

    /**
     * Get the value of the item as a string. For nodes, this is the string value of the
     * node as defined in the XPath 2.0 data model, except that all nodes are treated as being
     * untyped: it is not an error to get the string value of a node with a complex type.
     * For atomic values, the method returns the result of casting the atomic value to a string.
     * <p>If the calling code can handle any CharSequence, the method {@link #getStringValueCS} should
     * be used. If the caller requires a string, this method is preferred.</p>
     *
     * @return the string value of the item
     * @throws UnsupportedOperationException if the item is a function item (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @see #getStringValueCS
     * @since 8.4
     */
    @Override
    public String getStringValue() {
        return value.toString();
    }

    /**
     * Get the string value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String. The method satisfies the rule that
     * <code>X.getStringValueCS().toString()</code> returns a string that is equal to
     * <code>X.getStringValue()</code>.
     * <p>Note that two CharSequence values of different types should not be compared using equals(), and
     * for the same reason they should not be used as a key in a hash table.</p>
     * <p>If the calling code can handle any CharSequence, this method should
     * be used. If the caller requires a string, the {@link #getStringValue} method is preferred.</p>
     *
     * @return the string value of the item
     * @throws UnsupportedOperationException if the item is a function item (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @see #getStringValue
     * @since 8.4
     */
    @Override
    public CharSequence getStringValueCS() {
        return value.toString();
    }

    /**
     * Atomize the item.
     *
     * @return the result of atomization. For an external object this is defined
     * to be the result of the toString() method, as an xs:string value.
     */
    @Override
    public StringValue atomize() {
        return new StringValue(getStringValue());
    }

    /**
     * Determine the data type of the items in the expression, if possible
     *
     * @param th The TypeHierarchy.
     * @return for the default implementation: AnyItemType (not known)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType(/*@Nullable*/ TypeHierarchy th) {
        return th.getConfiguration().getJavaExternalObjectType(value.getClass());
    }

    /**
     * Display the type name for use in error messages
     *
     * @return the type name. This will be in the form "java-type:" followed by the full class name of the
     * wrapped Java object.
     */

    /*@NotNull*/
    public static String displayTypeName(Object value) {
        return "java-type:" + value.getClass().getName();
    }

    /**
     * Get the effective boolean value of the value
     *
     * @return true (always)
     */
    @Override
    public boolean effectiveBooleanValue() {
        return true;
    }

    /**
     * Get the encapsulated object
     *
     * @return the Java object that this external object wraps
     */

    @Override
    public T getObject() {
        return value;
    }

    /**
     * Determine if two ObjectValues are equal
     *
     * @return true if the other object is also a wrapped Java object and if the two wrapped objects compare
     * equal using the Java equals() method.
     */

    public boolean equals(/*@NotNull*/ Object other) {
        if (other instanceof ObjectValue) {
            Object o = ((ObjectValue) other).value;
            return value.equals(o);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toShortString() {
        String v = value.toString();
        if (v.startsWith(value.getClass().getName())) {
            return v;
        } else {
            return "(" + value.getClass().getSimpleName() + ")" + Err.truncate30(value.toString());
        }
    }
}

