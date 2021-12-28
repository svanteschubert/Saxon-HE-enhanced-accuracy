////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;


/**
 * An xs:NOTATION value.
 */

public final class NotationValue extends QualifiedNameValue {

    /**
     * Constructor
     *
     * @param prefix    The prefix part of the QName (not used in comparisons). Use null or "" to represent the
     *                  default prefix.
     * @param uri       The namespace part of the QName. Use null or "" to represent the null namespace.
     * @param localName The local part of the QName
     * @param check   Used for request checking names against XML 1.0 or XML 1.1 syntax rules
     */

    public NotationValue(String prefix, String uri, String localName, boolean check) throws XPathException {
        if (check && !NameChecker.isValidNCName(localName)) {
            XPathException err = new XPathException("Malformed local name in NOTATION: '" + localName + '\'');
            err.setErrorCode("FORG0001");
            throw err;
        }
        prefix = prefix == null ? "" : prefix;
        uri = uri == null ? "" : uri;
        if (check && uri.isEmpty() && prefix.length() != 0) {
            XPathException err = new XPathException("NOTATION has null namespace but non-empty prefix");
            err.setErrorCode("FOCA0002");
            throw err;
        }
        qName = new StructuredQName(prefix, uri, localName);
        typeLabel = BuiltInAtomicType.NOTATION;
    }

    /**
     * Constructor for a value that is known to be valid
     *
     * @param prefix    The prefix part of the QName (not used in comparisons). Use null or "" to represent the
     *                  default prefix.
     * @param uri       The namespace part of the QName. Use null or "" to represent the null namespace.
     * @param localName The local part of the QName
     */

    public NotationValue(String prefix, String uri, String localName) {
        qName = new StructuredQName(prefix, uri, localName);
        typeLabel = BuiltInAtomicType.NOTATION;
    }

    /**
     * Constructor for a value that is known to be valid
     *
     * @param prefix    The prefix part of the QName (not used in comparisons). Use null or "" to represent the
     *                  default prefix.
     * @param uri       The namespace part of the QName. Use null or "" to represent the null namespace.
     * @param localName The local part of the QName
     * @param typeLabel A type derived from xs:NOTATION to be used for the new value
     */

    public NotationValue(String prefix, String uri, String localName, AtomicType typeLabel) {
        qName = new StructuredQName(prefix, uri, localName);
        this.typeLabel = typeLabel;
    }

    /**
     * Constructor
     *
     * @param qName     the name as a StructuredQName
     * @param typeLabel idenfies a subtype of xs:QName
     */

    public NotationValue(/*@Nullable*/ StructuredQName qName, /*@Nullable*/ AtomicType typeLabel) {
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
        NotationValue v = new NotationValue(getPrefix(), getNamespaceURI(), getLocalName());
        v.typeLabel = typeLabel;
        return v;
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
        return BuiltInAtomicType.NOTATION;
    }

    /**
     * Determine if two Notation values are equal. This comparison ignores the prefix part
     * of the value.
     *
     * @throws ClassCastException    if they are not comparable
     * @throws IllegalStateException if the two QNames are in different name pools
     */

    public boolean equals(/*@NotNull*/ Object other) {
        return other instanceof NotationValue && qName.equals(((NotationValue) other).qName);
    }

    /*@NotNull*/
    @Override
    public Comparable getSchemaComparable() {
        return new NotationComparable();
    }

    private class NotationComparable implements Comparable {

        /*@NotNull*/
        public NotationValue getNotationValue() {
            return NotationValue.this;
        }

        @Override
        public int compareTo(/*@NotNull*/ Object o) {
            return equals(o) ? 0 : SequenceTool.INDETERMINATE_ORDERING;
        }

        public boolean equals(/*@NotNull*/ Object o) {
            return (o instanceof NotationComparable && qName.equals(((NotationComparable) o).getNotationValue().qName));
        }

        public int hashCode() {
            return qName.hashCode();
        }
    }

    /**
     * The toString() method returns the name in the form QName("uri", "local")
     *
     * @return the name in Clark notation: {uri}local
     */

    /*@NotNull*/
    public String toString() {
        return "NOTATION(" + getClarkName() + ')';
    }

}

