////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.NoDynamicContextException;
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
     * Constructor
     *
     * @param qName     the name as a StructuredQName
     * @param typeLabel idenfies a subtype of xs:QName
     */

    public QNameValue(/*@Nullable*/ StructuredQName qName, /*@Nullable*/ AtomicType typeLabel) {
        super(qName, typeLabel);
    }


    /**
     * Constructor for a QName that is known to be valid. No validation takes place.
     *
     * @param prefix    The prefix part of the QName (not used in comparisons). Use "" to represent the
     *                  default prefix.
     * @param uri       The namespace part of the QName. Use "" to represent the non-namespace.
     * @param localName The local part of the QName
     */

    public QNameValue(String prefix, NamespaceUri uri, String localName) {
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

    public QNameValue(String prefix, NamespaceUri uri, String localName, AtomicType type) {
        super(new StructuredQName(prefix, uri, localName), type);
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

    public QNameValue(String prefix, NamespaceUri uri, String localName, AtomicType type, boolean check) throws XPathException {
        this(buildStructuredQName(prefix, uri, localName, check), type);
    }

    private static StructuredQName buildStructuredQName(String prefix, NamespaceUri uri, String localName, boolean check) throws XPathException {
        if (check && !NameChecker.isValidNCName(localName)) {
            throw new XPathException("Malformed local name in QName: '" + localName + '\'', "FORG0001");
        }
        prefix = prefix == null ? "" : prefix;
        if (check && uri.isEmpty() && prefix.length() != 0) {
            throw new XPathException("QName has null namespace but non-empty prefix", "FOCA0002");
        }
        return new StructuredQName(prefix, uri, localName);
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
                return new AnyURIValue((getNamespaceURI().toUnicodeString()));
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

    @Override
    public int hashCode() {
        return qName.hashCode();
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone) throws NoDynamicContextException {
        return null;
    }
}

