////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Genre;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.value.ObjectValue;

import java.util.concurrent.ConcurrentHashMap;

/**
 * This class represents the type of an external Java object returned by
 * an extension function, or supplied as an external variable/parameter.
 */

@CSharpInjectMembers(code={"private static readonly System.Collections.Concurrent.ConcurrentDictionary<System.Type, Saxon.Hej.type.JavaExternalObjectType> cache = new (16, 20);"})
public class JavaExternalObjectType extends ExternalObjectType {

    protected Class<?> javaClass;

    private static final ConcurrentHashMap<Class<?>, JavaExternalObjectType> cache = new ConcurrentHashMap<>(20);

    /**
     * Create an external object type. Private constructor; to create an instance,
     * use {@link JavaExternalObjectType#of}.
     *
     * @param javaClass the Java class to which this type corresponds
     */

    private JavaExternalObjectType(Class<?> javaClass) {
        this.javaClass = javaClass;
    }

    /**
     * Allocate an external object type. for a given Java class. Reuses a known
     * {@code JavaExternalObjectType} if found in a static cache
     * @param javaClass the Java class to which this type corresponds
     * @return the corresponding {@code JavaExternalObjectType}
     */

    @CSharpReplaceBody(code="return Saxon.Impl.Helpers.MapUtils.ComputeIfAbsent(cache, javaClass, jc => new Saxon.Hej.type.JavaExternalObjectType(jc));")
    public static JavaExternalObjectType of(Class<?> javaClass) {
        return cache.computeIfAbsent(javaClass, JavaExternalObjectType::new);
    }

    /**
     * Get the local name of this type.
     *
     * @return the fully qualified name of the Java class.
     */

    /*@Nullable*/
    @Override
    public String getName() {
        return classNameToQName(javaClass.getName()).getLocalPart();
    }

    /**
     * Get the target namespace of this type. The is always NamespaceConstant.JAVA_TYPE.
     *
     * @return the target namespace of this type definition.
     */

    /*@Nullable*/
    @Override
    public String getTargetNamespace() {
        return NamespaceConstant.JAVA_TYPE;
    }

    /**
     * Get the name of this type as a StructuredQName, unless the type is anonymous, in which case
     * return null
     *
     * @return the name of the atomic type, or null if the type is anonymous.
     */

    /*@Nullable*/
    @Override
    public StructuredQName getTypeName() {
        return classNameToQName(javaClass.getName());
    }

    /**
     * Get the primitive item type corresponding to this item type.
     *
     * @return EXTERNAL_OBJECT_TYPE, the ExternalObjectType that encapsulates
     *         the Java type Object.class.
     */

    /*@NotNull*/
    @Override
    public ItemType getPrimitiveItemType() {
        return new JavaExternalObjectType(Object.class);
    }

    /**
     * Get the relationship of this external object type to another external object type
     *
     * @param other the other external object type
     * @return the relationship of this external object type to another external object type,
     *         as one of the constants in class {@link Affinity}, for example {@link Affinity#SUBSUMES}
     */

    public Affinity getRelationship(/*@NotNull*/ JavaExternalObjectType other) {
        Class<?> j2 = other.javaClass;
        if (javaClass.equals(j2)) {
            return Affinity.SAME_TYPE;
        } else if (javaClass.isAssignableFrom(j2)) {
            return Affinity.SUBSUMES;
        } else if (j2.isAssignableFrom(javaClass)) {
            return Affinity.SUBSUMED_BY;
        } else if (javaClass.isInterface() || j2.isInterface()) {
            return Affinity.OVERLAPS; // there may be an overlap, we play safe
        } else {
            return Affinity.DISJOINT;
        }
    }

    /**
     * Get the Java class to which this external object type corresponds
     *
     * @return the corresponding Java class
     */

    public Class<?> getJavaClass() {
        return javaClass;
    }

    /**
     * Test whether a given item conforms to this type
     * @param item    The item to be tested
     * @param th      The type hierarchy cache
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(/*@NotNull*/ Item item, /*@NotNull*/TypeHierarchy th) {
        if (item.getGenre() == Genre.EXTERNAL) {
            Object obj = ((ObjectValue<?>) item).getObject();
            return javaClass.isAssignableFrom(obj.getClass());
        }
        return false;
    }

    /*@NotNull*/
    public String toString() {
        return classNameToQName(javaClass.getName()).getEQName();
    }

    /*@NotNull*/
    public String getDisplayName() {
        return "java-type:" + javaClass.getName();
    }

    /**
     * Returns a hash code value for the object.
     */

    public int hashCode() {
        return javaClass.hashCode();
    }

    /**
     * Test whether two ExternalObjectType objects represent the same type
     *
     * @param obj the other ExternalObjectType
     * @return true if the two objects represent the same type
     */

    public boolean equals(/*@NotNull*/ Object obj) {
        return obj instanceof JavaExternalObjectType && javaClass == ((JavaExternalObjectType) obj).javaClass;
    }

    /**
     * Static method to convert a Java class name to an XPath local name. This involves the
     * following substitutions: "$" is replaced by "-", and "[" is replaced by "_-".
     */

    public static String classNameToLocalName(String className) {
        return className.replace('$', '-').replace("[", "_-");
    }

    /**
     * Static method to convert an XPath local name to a Java class name. This involves the
     * following substitutions: "-" is replaced by "$", and leading "_-" pairs are replaced by "[".
     */

    public static String localNameToClassName(String className) {
        StringBuilder fsb = new StringBuilder(className.length());
        boolean atStart = true;
        for (int i=0; i<className.length(); i++) {
            char c = className.charAt(i);
            if (atStart) {
                if (c == '_' && i+1 < className.length()  && className.charAt(i+1) == '-') {
                    fsb.append('[');
                    i++;
                } else {
                    atStart = false;
                    fsb.append(c == '-' ? '$' : c);
                }
            } else {
                fsb.append(c == '-' ? '$' : c);
            }
        }
        return fsb.toString();
    }

    /**
     * Static method to get the QName corresponding to a Java class name
     */

    public static StructuredQName classNameToQName(String className) {
        return new StructuredQName("jt", NamespaceUri.JAVA_TYPE, classNameToLocalName(className));
    }

}
