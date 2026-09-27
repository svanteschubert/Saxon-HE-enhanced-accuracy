////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import javax.xml.namespace.QName;

/**
 * This class provides an economical representation of a QName triple (prefix, URI, and localname).
 * The value is stored internally as a character array containing the concatenation of URI, localname,
 * and prefix (in that order) with two integers giving the start positions of the localname and prefix.
 * <p><i>Instances of this class are immutable.</i></p>
 */

public class StructuredQName implements IdentityComparable {

    private final String prefix;
    private final NamespaceUri uri;
    private final String local;
    private int cachedHashCode = -1;

    /**
     * Construct a StructuredQName from a prefix, URI, and local name. This method performs no validation.
     *
     * @param prefix    The prefix. Use an empty string to represent the null prefix.
     * @param uri       The namespace URI. Use {@link NamespaceUri#NULL} to represent the no-namespace
     * @param localName The local part of the name
     */

    public StructuredQName(String prefix, NamespaceUri uri, String localName) {
        this.prefix = prefix == null ? "" : prefix;
        this.uri = uri;
        this.local = localName;
    }

    /**
     * Construct a StructuredQName from a prefix, URI, and local name. This method performs no validation.
     *
     * @param prefix    The prefix. Use an empty string to represent the null prefix.
     * @param uri       The namespace URI. Use an empty string or null to represent the no-namespace
     * @param localName The local part of the name
     */

    public StructuredQName(String prefix, String uri, String localName) {
        this.prefix = prefix == null ? "" : prefix;
        this.uri = NamespaceUri.of(uri);
        this.local = localName;
    }

    /**
     * Make a structuredQName from a Clark name
     *
     * @param expandedName the name in Clark notation "{uri}local" if in a namespace, or "local" otherwise.
     *                     The format "{}local" is also accepted for a name in no namespace. The EQName syntax (Q{uri}local) is
     *                     also accepted.
     * @return the constructed StructuredQName
     * @throws IllegalArgumentException if the Clark name is malformed
     */

    public static StructuredQName fromClarkName(String expandedName) {
        String namespace;
        String localName;
        if (expandedName.startsWith("Q{")) {
            expandedName = expandedName.substring(1);
        }
        if (expandedName.charAt(0) == '{') {
            int closeBrace = expandedName.indexOf('}');
            if (closeBrace < 0) {
                throw new IllegalArgumentException("No closing '}' in Clark name");
            }
            namespace = expandedName.substring(1, closeBrace);
            if (closeBrace == expandedName.length()) {
                throw new IllegalArgumentException("Missing local part in Clark name");
            }
            localName = expandedName.substring(closeBrace + 1);
        } else {
            namespace = "";
            localName = expandedName;
        }
        return new StructuredQName("", NamespaceUri.of(namespace), localName);
    }

    /**
     * Make a structured QName from a lexical QName, using a supplied NamespaceResolver to
     * resolve the prefix
     *
     * @param lexicalName the QName as a lexical name (prefix:local), or (Q{uri}local) if
     *                    allowEQName is set to true. Leading and trailing whitespace is
     *                    ignored.
     * @param useDefault  set to true if an absent prefix implies use of the default namespace;
     *                    set to false if an absent prefix implies no namespace
     * @param format      +1 {@link #QUL} if the syntax Q{uri}local is acceptable;
     *                    +2 {@link #QUPL} if the syntax Q{uri}prefix:local is acceptable
     * @param resolver    NamespaceResolver used to look up a URI for the prefix
     * @return the StructuredQName object corresponding to this lexical QName
     * @throws XPathException if the namespace prefix is not in scope or if the value is lexically
     *                        invalid. Error code FONS0004 is set if the namespace prefix has not been declared; error
     *                        code FOCA0002 is set if the name is lexically invalid. These may need to be
     *                        changed on return depending on the caller's requirements.
     */

    public static StructuredQName fromLexicalQName(String lexicalName, boolean useDefault,
                                                   int format, NamespaceResolver resolver)
            throws XPathException {
        lexicalName = Whitespace.trim(lexicalName);
        int len = lexicalName.length();
        if (format != 0 && len >= 4 && lexicalName.charAt(0) == 'Q' && lexicalName.charAt(1) == '{') {
            int endBrace = lexicalName.indexOf('}', 2);
            if (endBrace < 0) {
                throw new XPathException("Invalid EQName '" + lexicalName + "': closing brace not found", "FOCA0002");
            } else if (endBrace == len - 1) {
                throw new XPathException("Invalid EQName '" + lexicalName + ": 'local part is missing", "FOCA0002");
            }
            String uri = lexicalName.substring(2, endBrace);
            if (uri.indexOf('{', 0) >= 0) {
                throw new XPathException("Namespace URI must not contain '{'", "FOCA0002");
            }
            String remainder = lexicalName.substring(endBrace + 1, len);
            if ((format & QUPL) != 0) {
                int colon = remainder.indexOf(':');
                if (colon < 0) {
                    if (!NameChecker.isValidNCName(remainder)) {
                        throw new XPathException("Invalid QName local part '" + remainder + "'", "FOCA0002");
                    }
                    return new StructuredQName("", NamespaceUri.of(uri), remainder);
                } else if (colon == 0) {
                    throw new XPathException("Invalid EQName: zero-length prefix", "FOCA0002");
                } else if (colon == remainder.length() - 1) {
                    throw new XPathException("Invalid EQName: zero-length local part", "FOCA0002");
                } else {
                    String prefix = remainder.substring(0, colon);
                    String local = remainder.substring(colon + 1);
                    if (!NameChecker.isValidNCName(prefix)) {
                        throw new XPathException("Invalid QName prefix '" + prefix + "'", "FOCA0002");
                    }
                    if (!NameChecker.isValidNCName(local)) {
                        throw new XPathException("Invalid QName local part '" + local + "'", "FOCA0002");
                    }
                    return new StructuredQName(prefix, NamespaceUri.of(uri), local);
                }
            }
            if (!NameChecker.isValidNCName(remainder)) {
                throw new XPathException("Invalid EQName: local part is not a valid NCName", "FOCA0002");
            }
            return new StructuredQName("", NamespaceUri.of(uri), remainder);
        }
        // Handle a lexical qname (prefix:local)
        try {
            String[] parts = NameChecker.getQNameParts(lexicalName);
            NamespaceUri uri = resolver.getURIForPrefix(parts[0], useDefault);
            if (uri == null) {
                if (NameChecker.isValidNCName(parts[0])) {
                    throw new XPathException("Namespace prefix '" + parts[0] + "' has not been declared", "FONS0004");
                } else {
                    throw new XPathException("Invalid namespace prefix '" + parts[0] + "'", "FOCA0002");
                }
            }
            return new StructuredQName(parts[0], uri, parts[1]);
        } catch (QNameException e) {
            throw new XPathException(e.getMessage(), "FOCA0002");
        }
    }

    /**
     * Format option for {@link #fromLexicalQName(String, boolean, int, NamespaceResolver)}: allow
     * the format <code>Q{uri}local</code>
     */
    public final static int QUL = 1;

    /**
     * Format option for {@link #fromLexicalQName(String, boolean, int, NamespaceResolver)}: allow
     * the format <code>Q{uri}prefix:local</code>
     */
    public final static int QUPL = 2;

    private void checkNCName(String name, String message) {
        if (!NameChecker.isValidNCName(name)) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * Make a structured QName from an EQName in {@code Q{uri}local} format.
     *
     * @param eqName the QName as an EQName ({@code Q{uri}local}), or an unqualified local name. The format
     * of the local name is not checked.
     * @return the StructuredQName object corresponding to this EQName
     * @throws IllegalArgumentException if the eqName syntax is invalid (but the format of the
     * URI and local name parts are not checked)
     */

    public static StructuredQName fromEQName(String eqName) {
        eqName = Whitespace.trim(eqName);
        if (eqName.startsWith("Q{")) {
            int endBrace = eqName.indexOf('}');
            if (endBrace < 0) {
                throw new IllegalArgumentException("Invalid EQName: closing brace not found");
            } else if (endBrace == eqName.length() - 1) {
                throw new IllegalArgumentException("Invalid EQName: local part is missing");
            }
            String uri = eqName.substring(2, endBrace);
            if (uri.indexOf('{') >= 0) {
                throw new IllegalArgumentException("Invalid EQName: open brace in URI part");
            }
            String local = eqName.substring(endBrace + 1);
            return new StructuredQName("", NamespaceUri.of(uri), local);
        } else {
            return new StructuredQName("", NamespaceUri.NULL, eqName);
        }
    }

    /**
     * Make a structured QName from an EQName in {@code Q{uri}[prefix:]local} format.
     *
     * @param eqName the QName as an EQName ({@code Q{uri}[prefix:]local}), or an unqualified local name. The format
     *               of the prefix and local name is not checked.
     * @return the StructuredQName object corresponding to this EQName
     * @throws IllegalArgumentException if the eqName syntax is invalid (but the format of the
     *                                  URI and local name parts are not checked)
     */

    public static StructuredQName fromEQName40(String eqName) {
        eqName = Whitespace.trim(eqName);
        if (eqName.startsWith("Q{")) {
            int endBrace = eqName.indexOf('}');
            if (endBrace < 0) {
                throw new IllegalArgumentException("Invalid EQName: closing brace not found");
            } else if (endBrace == eqName.length() - 1) {
                throw new IllegalArgumentException("Invalid EQName: local part is missing");
            }
            String uri = eqName.substring(2, endBrace);
            if (uri.indexOf('{') >= 0) {
                throw new IllegalArgumentException("Invalid EQName: open brace in URI part");
            }
            String remainder = eqName.substring(endBrace + 1);
            int colon = remainder.indexOf(':');
            if (colon < 0) {
                return new StructuredQName("", NamespaceUri.of(uri), remainder);
            } else if (colon == 0) {
                throw new IllegalArgumentException("Invalid EQName: zero-length prefix");
            } else if (colon == remainder.length() - 1) {
                throw new IllegalArgumentException("Invalid EQName: zero-length local part");
            } else {
                return new StructuredQName(remainder.substring(0, colon), NamespaceUri.of(uri), remainder.substring(colon + 1));
            }
        } else {
            return new StructuredQName("", NamespaceUri.NULL, eqName);
        }
    }


    /**
     * Get the prefix of the QName.
     *
     * @return the prefix. Returns the empty string if the name is unprefixed.
     */

    public String getPrefix() {
        return prefix;
    }

    /**
     * Get the namespace URI of the QName.
     *
     * @return the URI. Returns {@link NamespaceUri#NULL} to represent the no-namespace
     */

    public NamespaceUri getNamespaceUri() {
        return this.uri;
    }

    /**
     * Get the namespace URI of the QName as a string.
     *
     * <p>This method is retained for backwards compatibility, but {@link #getNamespaceUri()} should
     * be used in preference.</p>
     *
     * @return the URI. Returns the empty string to represent the no-namespace
     */

    public String getURI() {
        return this.uri.toString();
    }

    /**
     * Test whether the URI is equal to some constant
     * @param uri the namespace URI to be tested
     * @return true if the namespace URI of this QName is equal to the supplied URI
     */

    public boolean hasURI(NamespaceUri uri) {
        return this.uri == uri;
    }

    /**
     * Get the local part of the QName
     *
     * @return the local part of the QName
     */

    public String getLocalPart() {
        return local;
    }

    /**
     * Get the display name, that is the lexical QName in the form [prefix:]local-part
     *
     * @return the lexical QName
     */

    public String getDisplayName() {
        if (prefix.isEmpty()) {
            return local;
        } else {
            return prefix + ":" + local;
        }
    }

    /**
     * Get the name as a StructuredQName (which it already is; but this satisfies the NodeName interface)
     * @return the name as a StructuredQName
     */

    public StructuredQName getStructuredQName() {
        return this;
    }

    /**
     * Get the expanded QName in Clark format, that is "{uri}local" if it is in a namespace, or just "local"
     * otherwise.
     *
     * @return the QName in Clark notation
     */

    public String getClarkName() {
        if (uri == NamespaceUri.NULL) {
            return local;
        } else {
            return "{" + uri + "}" + local;
        }
    }

    /**
     * Get the expanded QName as an EQName, that is "Q{uri}local" for a name in a namespace,
     * or "Q{}local" otherwise
     *
     * @return the QName in EQName notation
     */

    public String getEQName() {
        if (uri == NamespaceUri.NULL) {
            return "Q{}" + local;
        } else {
            return "Q{" + uri + "}" + local;
        }
    }

    /**
     * The toString() method displays the QName as a lexical QName, that is prefix:local
     *
     * @return the lexical QName
     */

    public String toString() {
        return getDisplayName();
    }

    /**
     * Compare two StructuredQName values for equality. This compares the URI and local name parts,
     * excluding any prefix
     */

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof StructuredQName) {
            return local.equals(((StructuredQName)other).local) && uri == ((StructuredQName) other).uri;
        } else {
            return false;
        }
    }

    /**
     * Get a hashcode to reflect the equals() method.
     *
     * <p>The hashcode is based on the URI and local part only, ignoring the prefix. In fact the URI plays little
     * part in computing the hashcode: {@link NamespaceUri} objects are pooled, so the hashcode is simply the
     * object identifier.</p>
     *
     * @return a hashcode used to distinguish distinct QNames
     */

    public int hashCode() {
        if (cachedHashCode == -1) {
            return cachedHashCode = 0x5004a00b ^ local.hashCode() ^ uri.hashCode();
        } else {
            return cachedHashCode;
        }
    }

    /**
     * Expose the hashCode algorithm so that other implementations of QNames can construct a compatible hashcode
     *
     * @param uri   the namespace URI
     * @param local the local name
     * @return a hash code computed from the URI and local name
     */

    public static int computeHashCode(NamespaceUri uri, String local) {
        return 0x5004a00b ^ local.hashCode() ^ uri.hashCode();
    }

    /**
     * Convert the StructuredQName to a javax.xml.namespace.QName
     *
     * @return an object of class javax.xml.namespace.QName representing this qualified name
     */

    public QName toJaxpQName() {
        return new javax.xml.namespace.QName(getNamespaceUri().toString(), getLocalPart(), getPrefix());
    }
    
    /**
     * Get the NamespaceBinding (prefix/uri pair) corresponding to this name
     *
     * @return a NamespaceBinding containing the prefix and URI present in this QName
     */

    public NamespaceBinding getNamespaceBinding() {
        return new NamespaceBinding(getPrefix(), getNamespaceUri());
    }

    /**
     * Determine whether two IdentityComparable objects are identical. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone. In the case of a StructuredQName, the identity test compares
     * prefixes as well as the namespace URI and local name.
     *
     * @param other the value to be compared with
     * @return true if the two values are identical, false otherwise
     */
    @Override
    public boolean isIdentical(IdentityComparable other) {
        return equals(other) && ((StructuredQName)other).getPrefix().equals(getPrefix());
    }

    /**
     * Get a hashCode that offers the guarantee that if A.isIdentical(B), then A.identityHashCode() == B.identityHashCode()
     *
     * @return a hashCode suitable for use when testing for identity.
     */
    @Override
    public int identityHashCode() {
        return hashCode() ^ getPrefix().hashCode();
    }
}

