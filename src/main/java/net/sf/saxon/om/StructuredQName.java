////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.Whitespace;

import javax.xml.namespace.QName;

/**
 * This class provides an economical representation of a QName triple (prefix, URI, and localname).
 * The value is stored internally as a character array containing the concatenation of URI, localname,
 * and prefix (in that order) with two integers giving the start positions of the localname and prefix.
 * <p><i>Instances of this class are immutable.</i></p>
 */

public class StructuredQName implements IdentityComparable {

    private char[] content;
    private int localNameStart;
    private int prefixStart;
    private int cachedHashCode = -1;

    private StructuredQName(char[] content, int localNameStart, int prefixStart) {
        this.content = content;
        this.localNameStart = localNameStart;
        this.prefixStart = prefixStart;
    }

    /**
     * Construct a StructuredQName from a prefix, URI, and local name. This method performs no validation.
     *
     * @param prefix    The prefix. Use an empty string to represent the null prefix.
     * @param uri       The namespace URI. Use an empty string or null to represent the no-namespace
     * @param localName The local part of the name
     */

    public StructuredQName(String prefix, /*@Nullable*/ String uri, String localName) {
        if (uri == null) {
            uri = "";
        }
        int plen = prefix.length();
        int ulen = uri.length();
        int llen = localName.length();
        localNameStart = ulen;
        prefixStart = ulen + llen;
        content = new char[ulen + llen + plen];
        uri.getChars(0, ulen, content, 0);
        localName.getChars(0, llen, content, ulen);
        prefix.getChars(0, plen, content, ulen + llen);
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
        return new StructuredQName("", namespace, localName);
    }

    /**
     * Make a structured QName from a lexical QName, using a supplied NamespaceResolver to
     * resolve the prefix
     *
     *
     * @param lexicalName the QName as a lexical name (prefix:local), or (Q{uri}local) if
     *                    allowEQName is set to true. Leading and trailing whitespace is
     *                    ignored.
     * @param useDefault  set to true if an absent prefix implies use of the default namespace;
     *                    set to false if an absent prefix implies no namespace
     * @param allowEQName true if the EQName syntax Q{uri}local is acceptable
     * @param resolver    NamespaceResolver used to look up a URI for the prefix
     * @return the StructuredQName object corresponding to this lexical QName
     * @throws XPathException if the namespace prefix is not in scope or if the value is lexically
     *                        invalid. Error code FONS0004 is set if the namespace prefix has not been declared; error
     *                        code FOCA0002 is set if the name is lexically invalid. These may need to be
     *                        changed on return depending on the caller's requirements.
     */

    public static StructuredQName fromLexicalQName(CharSequence lexicalName, boolean useDefault,
                                                   boolean allowEQName, NamespaceResolver resolver)
            throws XPathException {
        lexicalName = Whitespace.trimWhitespace(lexicalName);
        if (allowEQName && lexicalName.length() >= 4 && lexicalName.charAt(0) == 'Q' && lexicalName.charAt(1) == '{') {
            String name = lexicalName.toString();
            int endBrace = name.indexOf('}');
            if (endBrace < 0) {
                throw new XPathException("Invalid EQName: closing brace not found", "FOCA0002");
            } else if (endBrace == name.length() - 1) {
                throw new XPathException("Invalid EQName: local part is missing", "FOCA0002");
            }
            String uri = name.substring(2, endBrace);
            if (uri.contains("{")) {
                throw new XPathException("Namespace URI must not contain '{'", "FOCA0002");
            }
            String local = name.substring(endBrace + 1);
            if (!NameChecker.isValidNCName(local)) {
                throw new XPathException("Invalid EQName: local part is not a valid NCName", "FOCA0002");
            }
            return new StructuredQName("", uri, local);
        }
        try {
            String[] parts = NameChecker.getQNameParts(lexicalName);
            String uri = resolver.getURIForPrefix(parts[0], useDefault);
            if (uri == null) {
                if (NameChecker.isValidNCName(parts[0])) {
                    XPathException de = new XPathException("Namespace prefix '" + parts[0] + "' has not been declared");
                    de.setErrorCode("FONS0004");
                    throw de;
                } else {
                    XPathException de = new XPathException("Invalid namespace prefix '" + parts[0] + "'");
                    de.setErrorCode("FOCA0002");
                    throw de;
                }
            }
            return new StructuredQName(parts[0], uri, parts[1]);
        } catch (QNameException e) {
            throw new XPathException(e.getMessage(), "FOCA0002");
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

    public static StructuredQName fromEQName(CharSequence eqName) {
        eqName = Whitespace.trimWhitespace(eqName);
        if (eqName.length() >= 4 && eqName.charAt(0) == 'Q' && eqName.charAt(1) == '{') {
            String name = eqName.toString();
            int endBrace = name.indexOf('}');
            if (endBrace < 0) {
                throw new IllegalArgumentException("Invalid EQName: closing brace not found");
            } else if (endBrace == name.length() - 1) {
                throw new IllegalArgumentException("Invalid EQName: local part is missing");
            }
            String uri = name.substring(2, endBrace);
            String local = name.substring(endBrace + 1);
            return new StructuredQName("", uri, local);
        } else {
            return new StructuredQName("", "", eqName.toString());
        }
    }


    /**
     * Get the prefix of the QName.
     *
     * @return the prefix. Returns the empty string if the name is unprefixed.
     */

    public String getPrefix() {
        return new String(content, prefixStart, content.length - prefixStart);
    }

    /**
     * Get the namespace URI of the QName.
     *
     * @return the URI. Returns the empty string to represent the no-namespace
     */

    public String getURI() {
        if (localNameStart == 0) {
            return "";
        }
        return new String(content, 0, localNameStart);
    }

    /**
     * Test whether the URI is equal to some constant
     * @param uri the namespace URI to be tested
     * @return true if the namespace URI of this QName is equal to the supplied URI
     */

    public boolean hasURI(String uri) {
        if (localNameStart != uri.length()) {
            return false;
        }
        for (int i = localNameStart - 1; i >= 0; i--) {
            // compare from the end of the URI to maximize chance of finding a difference quickly
            if (content[i] != uri.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the local part of the QName
     *
     * @return the local part of the QName
     */

    public String getLocalPart() {
        return new String(content, localNameStart, prefixStart - localNameStart);
    }

    /**
     * Get the display name, that is the lexical QName in the form [prefix:]local-part
     *
     * @return the lexical QName
     */

    public String getDisplayName() {
        if (prefixStart == content.length) {
            return getLocalPart();
        } else {
            FastStringBuffer buff = new FastStringBuffer(content.length - localNameStart + 1);
            buff.append(content, prefixStart, content.length - prefixStart);
            buff.cat(':');
            buff.append(content, localNameStart, prefixStart - localNameStart);
            return buff.toString();
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
        FastStringBuffer buff = new FastStringBuffer(content.length - prefixStart + 2);
        if (localNameStart > 0) {
            buff.cat('{');
            buff.append(content, 0, localNameStart);
            buff.cat('}');
        }
        buff.append(content, localNameStart, prefixStart - localNameStart);
        return buff.toString();
    }

    /**
     * Get the expanded QName as an EQName, that is "Q{uri}local" for a name in a namespace,
     * or "Q{}local" otherwise
     *
     * @return the QName in EQName notation
     */

    public String getEQName() {
        FastStringBuffer buff = new FastStringBuffer(content.length - prefixStart + 2);
        buff.append("Q{");
        if (localNameStart > 0) {
            buff.append(content, 0, localNameStart);
        }
        buff.cat('}');
        buff.append(content, localNameStart, prefixStart - localNameStart);
        return buff.toString();
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
            int c = ((StructuredQName) other).cachedHashCode;
            if (c != -1 && c != hashCode()) {
                return false;
            }
            StructuredQName sq2 = (StructuredQName) other;
            if (localNameStart != sq2.localNameStart || prefixStart != sq2.prefixStart) {
                return false;
            }
            for (int i = prefixStart - 1; i >= 0; i--) {
                // compare from the end of the local name to maximize chance of finding a difference quickly
                if (content[i] != sq2.content[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get a hashcode to reflect the equals() method.
     *
     * <p>The hashcode is based on the URI and local part only, ignoring the prefix. In fact the URI plays little
     * part in computing the hashcode, because the URI is often long, and largely redundant: QNames with the same
     * local name will rarely have different URIs, and there are significant performance savings in the NamePool
     * if a cheaper hashcode is used. So the only contribution from the URI is that we take its length into account.</p>
     *
     * @return a hashcode used to distinguish distinct QNames
     */

    public int hashCode() {
        if (cachedHashCode == -1) {
            int h = 0x8004a00b;
            h ^= prefixStart;
            h ^= localNameStart;
            for (int i = localNameStart; i < prefixStart; i++) {
                h ^= content[i] << (i & 0x1f);
            }
            return cachedHashCode = h;
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

    public static int computeHashCode(CharSequence uri, CharSequence local) {
        int h = 0x8004a00b;
        int localLen = local.length();
        int uriLen = uri.length();
        int totalLen = localLen + uriLen;
        h ^= totalLen;
        h ^= uriLen;
        for (int i = 0, j = uriLen; i < localLen; i++, j++) {
            h ^= local.charAt(i) << (j & 0x1f);
        }
        return h;
    }

    /**
     * Convert the StructuredQName to a javax.xml.namespace.QName
     *
     * @return an object of class javax.xml.namespace.QName representing this qualified name
     */

    public QName toJaxpQName() {
        return new javax.xml.namespace.QName(getURI(), getLocalPart(), getPrefix());
    }

    /**
     * Get the NamespaceBinding (prefix/uri pair) corresponding to this name
     *
     * @return a NamespaceBinding containing the prefix and URI present in this QName
     */

    public NamespaceBinding getNamespaceBinding() {
        return NamespaceBinding.makeNamespaceBinding(getPrefix(), getURI());
    }

    /**
     * Determine whether two IdentityComparable objects are identical. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone. In the case of a StructuredQName, the identity test compares
     * prefixes as well as the namespace URI and local name.
     *
     * @param other the value to be compared with
     * @return true if the two values are indentical, false otherwise
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

