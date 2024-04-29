////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ValidationFailure;

import javax.xml.namespace.QName;
import java.util.Objects;


/**
 * A qualified name: this is an abstract superclass for QNameValue and NotationValue, representing the
 * XPath primitive types xs:QName and xs:NOTATION respectively
 */

public abstract class QualifiedNameValue extends AtomicValue implements AtomicMatchKey {

    /*@NotNull*/ protected final StructuredQName qName;

    public QualifiedNameValue(StructuredQName qName, AtomicType typeLabel) {
        super(typeLabel);
        Objects.requireNonNull(qName);
        this.qName = qName;
    }

    /**
     * Factory method to construct either a QName or a NOTATION value, or a subtype of either of these.
     * Note that it is the caller's responsibility to resolve the QName prefix into a URI
     *
     * @param prefix      the prefix part of the value. Use "" or null for the empty prefix.
     * @param uri         the namespace URI part of the value. Use "" or null for the non-namespace
     * @param local       the local part of the value
     * @param targetType  the target type, which must be xs:QName or a subtype of xs:NOTATION or xs:QName
     * @param lexicalForm the original lexical form of the value. This is needed in case there are facets
     *                    such as pattern that check the lexical form
     * @param rules       the conversion rules to be applied
     * @return the converted value
     * @throws XPathException if the value cannot be converted.
     */

    /*@Nullable*/
    public static AtomicValue makeQName(String prefix, NamespaceUri uri, String local,
                                        /*@NotNull*/ AtomicType targetType, UnicodeString lexicalForm, ConversionRules rules)
            throws XPathException {

        if (targetType.getFingerprint() == StandardNames.XS_QNAME) {
            return new QNameValue(prefix, uri, local, BuiltInAtomicType.QNAME, true);
        } else {
            QualifiedNameValue qnv;

            if (targetType.getPrimitiveType() == StandardNames.XS_QNAME) {
                qnv = new QNameValue(prefix, uri, local, targetType, true);
            } else {
                qnv = new NotationValue(prefix, uri, local, targetType);
            }
            ValidationFailure vf = targetType.validate(qnv, lexicalForm, rules);
            if (vf != null) {
                throw vf.makeException();
            }
            return qnv;
        }
    }


    /**
     * Get the string value as a String. Returns the QName as a lexical QName, retaining the original
     * prefix if available.
     * @return the value converted to a string
     */

    @Override
    public final UnicodeString getPrimitiveStringValue() {
        return StringView.of(qName.getDisplayName()).tidy();
    }

    /**
     * Get the QName in Clark notation, that is "{uri}local" if in a namespace, or "local" otherwise
     *
     * @return the name in Clark notation
     */

    public final String getClarkName() {
        return qName.getClarkName();
    }

    /**
     * Get the QName in EQName notation, that is "Q{uri}local" if in a namespace, or "Q{}local" otherwise
     *
     * @return the name in EQName notation
     */

    public final String getEQName() {
        return qName.getEQName();
    }

    /**
     * Get the local part
     *
     * @return the local part of the name (the part after the colon)
     */

    /*@NotNull*/
    public final String getLocalName() {
        return qName.getLocalPart();
    }

    /**
     * Get the namespace part. Returns the empty string for a name in no namespace.
     *
     * @return the namespace URI component of the name, or "" for a no-namespace name
     */

    /*@NotNull*/
    public final NamespaceUri getNamespaceURI() {
        return qName.getNamespaceUri();
    }

    /**
     * Get the prefix. Returns the empty string if the name is unprefixed.
     *
     * @return the prefix, or "" to indicate no prefix
     */

    /*@NotNull*/
    public final String getPrefix() {
        return qName.getPrefix();
    }


    /**
     * Get an object value that implements the XPath equality and ordering comparison semantics for this value.
     * If the ordered parameter is set to true, the result will be a Comparable and will support a compareTo()
     * method with the semantics of the XPath lt/gt operator, provided that the other operand is also obtained
     * using the getXPathComparable() method. In all cases the result will support equals() and hashCode() methods
     * that support the semantics of the XPath eq operator, again provided that the other operand is also obtained
     * using the getXPathComparable() method. A context argument is supplied for use in cases where the comparison
     * semantics are context-sensitive, for example where they depend on the implicit timezone or the default
     * collation.
     *  @param collator the collation to be used for the comparison
     * @param implicitTimezone  the XPath dynamic evaluation context, used in cases where the comparison is context
     */

    /*@Nullable*/
    @Override
    public AtomicMatchKey getXPathMatchKey(StringCollator collator, int implicitTimezone) {
        return this;
    }

    public int hashCode() {
        return qName.hashCode();
    }

    @Override
    public boolean isIdentical(/*@NotNull*/ AtomicValue v) {
        return super.isIdentical(v) && qName.getPrefix().equals(((QualifiedNameValue) v).getPrefix());
    }

    /**
     * Get a hashCode that offers the guarantee that if A.isIdentical(B), then A.identityHashCode() == B.identityHashCode()
     *
     * @return a hashCode suitable for use when testing for identity.
     */
    @Override
    public int identityHashCode() {
        return qName.identityHashCode();
    }

    /**
     * The show() method returns the name in the form QName("uri", "local")
     * @return the name in in the form QName("uri", "local")
     */

    @Override
    public String show() {
        return "QName(\"" + getNamespaceURI() + "\", \"" + getLocalName() + "\")";
    }

    /**
     * Construct a javax.xml.namespace.QName from this QualifiedNameValue
     *
     * @return an equivalent instance of the JAXP QName class
     */

    public QName toJaxpQName() {
        return qName.toJaxpQName();
    }
    /**
     * Get the equivalent StructuredQName
     *
     * @return the equivalent StructuredQName
     */

    /*@NotNull*/
    public StructuredQName getStructuredQName() {
        return qName;
    }
}
