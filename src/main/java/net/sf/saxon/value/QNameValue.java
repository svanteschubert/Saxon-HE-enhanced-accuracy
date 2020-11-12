////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;

/**
 * A QName value. This implements the so-called "triples proposal", in which the prefix is retained as
 * part of the value. The prefix is not used in any operation on a QName other than conversion of the
 * QName to a string.
 */

public class QNameValue extends QualifiedNameValue {


    /**
     * Constructor for a QName that is known to be valid. No validation takes place.
     *
     * @param prefix    The prefix part of the QName (not used in comparisons). Use "" to represent the
     *                  default prefix.
     * @param uri       The namespace part of the QName. Use "" to represent the non-namespace.
     * @param localName The local part of the QName
     */

    public QNameValue(String prefix, String uri, String localName) {
        this(prefix, uri, localName, BuiltInAtomicType.QNAME);
    }

    /**
     * Constructor for a QName that is known to be valid, allowing a user-defined subtype of QName
     * to be specified. No validation takes place.
     *
     * @param prefix    The prefix part of the QName (not used in comparisons). Use "" to represent the
     *                  default prefix (but null is also accepted)
     * @param uri       The namespace part of the QName. Use null to represent the non-namespace (but "" is also
     *                  accepted).
     * @param localName The local part of the QName
     * @param type      The type label, xs:QName or a subtype of xs:QName
     */

    public QNameValue(String prefix, String uri, String localName, /*@Nullable*/ AtomicType type) {
        qName = new StructuredQName(prefix, uri, localName);
        if (type == null) {
            type = BuiltInAtomicType.QNAME;
        }
        typeLabel = type;
    }

    /**
     * Constructor. This constructor validates that the local part is a valid NCName.
     *
     * @param prefix    The prefix part of the QName (not used in comparisons). Use "" to represent the
     *                  default prefix (but null is also accepted).
     *                  Note that the prefix is not checked for lexical correctness, because in most cases
     *                  it will already have been matched against in-scope namespaces. Where necessary the caller must
     *                  check the prefix.
     * @param uri       The namespace part of the QName. Use null to represent the non-namespace (but "" is also
     *                  accepted).
     * @param localName The local part of the QName
     * @param type      The atomic type, which must be either xs:QName, or a
     *                  user-defined type derived from xs:QName by restriction
     * @param check     Supply false if the name does not need to be checked (the caller asserts that it is known to be valid)
     * @throws XPathException if the local part of the name is malformed or if the name has a null
     *                        namespace with a non-empty prefix
     */

    public QNameValue(String prefix, String uri, String localName, AtomicType type, boolean check) throws XPathException {
        if (!NameChecker.isValidNCName(localName)) {
            XPathException err = new XPathException("Malformed local name in QName: '" + localName + '\'');
            err.setErrorCode("FORG0001");
            throw err;
        }
        prefix = prefix == null ? "" : prefix;
        uri = "".equals(uri) ? null : uri;
        if (check && uri == null && prefix.length() != 0) {
            XPathException err = new XPathException("QName has null namespace but non-empty prefix");
            err.setErrorCode("FOCA0002");
            throw err;
        }
        qName = new StructuredQName(prefix, uri, localName);
        typeLabel = type;
    }

    /**
     * Constructor
     *
     * @param qName     the name as a StructuredQName
     * @param typeLabel idenfies a subtype of xs:QName
     */

    public QNameValue(/*@Nullable*/ StructuredQName qName, /*@Nullable*/ AtomicType typeLabel) {
        if (qName == null) {
            throw new NullPointerException("qName");
        }
        if (typeLabel == null) {
            throw new NullPointerException("typeLabel");
        }
        this.qName = qName;
        this.typeLabel = typeLabel;
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    /*@NotNull*/
    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        return new QNameValue(qName, typeLabel);
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    /*@NotNull*/
    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.QNAME;
    }

    /**
     * Get a component. Returns a zero-length string if the namespace-uri component is
     * requested and is not present.
     *
     * @param part either Component.LOCALNAME or Component.NAMESPACE indicating which
     *             component of the value is required
     * @return either the local name or the namespace URI, in each case as a StringValue
     */

    /*@Nullable*/
    @Override
    public AtomicValue getComponent(AccessorFn.Component part) {
        switch (part) {
            case LOCALNAME:
                return new StringValue(getLocalName(), BuiltInAtomicType.NCNAME);
            case NAMESPACE:
                return new AnyURIValue(getNamespaceURI());
            case PREFIX:
                String prefix = getPrefix();
                if (prefix.isEmpty()) {
                    return null;
                } else {
                    return new StringValue(prefix, BuiltInAtomicType.NCNAME);
                }
            default:
                throw new UnsupportedOperationException("Component of QName must be URI, Local Name, or Prefix");
        }
    }

    /**
     * Determine if two QName values are equal. This comparison ignores the prefix part
     * of the value.
     *
     * @throws ClassCastException if they are not comparable
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return other instanceof QNameValue && qName.equals(((QNameValue) other).qName);
    }

    /*@NotNull*/
    @Override
    public Comparable getSchemaComparable() {
        return new QNameComparable();
    }

    private class QNameComparable implements Comparable {

        /*@NotNull*/
        public QNameValue getQNameValue() {
            return QNameValue.this;
        }

        @Override
        public int compareTo(/*@NotNull*/ Object o) {
            return equals(o) ? 0 : SequenceTool.INDETERMINATE_ORDERING;
        }

        public boolean equals(/*@NotNull*/ Object o) {
            return (o instanceof QNameComparable && qName.equals(((QNameComparable) o).getQNameValue().qName));
        }

        public int hashCode() {
            return qName.hashCode();
        }
    }

}

