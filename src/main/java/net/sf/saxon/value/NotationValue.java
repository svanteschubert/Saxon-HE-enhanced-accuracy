////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.XPathComparable;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.NoDynamicContextException;
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
     * @param check     Used for request checking names against XML 1.0 or XML 1.1 syntax rules
     * @throws XPathException if an error is detected
     */

    public NotationValue(String prefix, NamespaceUri uri, String localName, boolean check) throws XPathException {
        super(new StructuredQName(prefix, uri, localName), BuiltInAtomicType.NOTATION);
        if (check && !NameChecker.isValidNCName(localName)) {
            throw new XPathException("Malformed local name in NOTATION: '" + localName + '\'', "FORG0001");
        }
        prefix = prefix == null ? "" : prefix;
        if (check && uri.isEmpty() && prefix.length() != 0) {
            throw new XPathException("NOTATION has null namespace but non-empty prefix", "FOCA0002");
        }
    }

    public NotationValue(String prefix, String uri, String localName, boolean check) throws XPathException {
        this(prefix, NamespaceUri.of(uri), localName, check);
    }

    /**
     * Constructor for a value that is known to be valid
     *
     * @param prefix    The prefix part of the QName (not used in comparisons). Use null or "" to represent the
     *                  default prefix.
     * @param uri       The namespace part of the QName. Use null or "" to represent the null namespace.
     * @param localName The local part of the QName
     */

    public NotationValue(String prefix, NamespaceUri uri, String localName) {
        super(new StructuredQName(prefix, uri, localName), BuiltInAtomicType.NOTATION);
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

    public NotationValue(String prefix, NamespaceUri uri, String localName, AtomicType typeLabel) {
        super(new StructuredQName(prefix, uri, localName), typeLabel);
    }

    /**
     * Constructor
     *
     * @param qName     the name as a StructuredQName
     * @param typeLabel idenfies a subtype of xs:QName
     */

    public NotationValue(/*@Nullable*/ StructuredQName qName, /*@Nullable*/ AtomicType typeLabel) {
        super(qName, typeLabel);
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
        return new NotationValue(getStructuredQName(), typeLabel);
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

    @Override
    public int hashCode() {
        return qName.hashCode();
    }

    @Override
    public XPathComparable getXPathComparable(StringCollator collator, int implicitTimezone) throws NoDynamicContextException {
        return null;
    }

    /**
     * The show() method returns the name in the form <code>NOTATION({uri}local)</code>
     *
     * @return the name in Clark notation: {uri}local
     */

    @Override
    public String show() {
        return "NOTATION(" + getClarkName() + ')';
    }

}

