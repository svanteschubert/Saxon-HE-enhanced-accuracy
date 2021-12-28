////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

/**
 * A QName triple (prefix, URI, local) with the additional ability to hold an integer fingerprint.
 * The integer fingerprint provides a fast way of checking equality. A FingerprintedQName makes sense
 * only in the context of a known NamePool, and instances must be compared only if they relate to the
 * same NamePool. The fingerprint is optional, and is used only if present.
 */
public class FingerprintedQName implements NodeName {

    private StructuredQName qName;
    private int fingerprint = -1;

    public FingerprintedQName(String prefix, String uri, String localName) {
        qName = new StructuredQName(prefix, uri, localName);
    }

    public FingerprintedQName(String prefix, String uri, String localName, int fingerprint) {
        qName = new StructuredQName(prefix, uri, localName);
        this.fingerprint = fingerprint;
    }

    public FingerprintedQName(String prefix, String uri, String localName, NamePool pool) {
        qName = new StructuredQName(prefix, uri, localName);
        this.fingerprint = pool.allocateFingerprint(uri, localName);
    }

    public FingerprintedQName(StructuredQName qName, int fingerprint) {
        this.qName = qName;
        this.fingerprint = fingerprint;
    }

    public FingerprintedQName(StructuredQName qName, NamePool pool) {
        this.qName = qName;
        this.fingerprint = pool.allocateFingerprint(qName.getURI(), qName.getLocalPart());
    }

    /**
     * Make a FingerprintedQName from a Clark name
     *
     * @param expandedName the name in Clark notation "{uri}local" if in a namespace, or "local" otherwise.
     *                     The format "{}local" is also accepted for a name in no namespace.
     * @return the constructed FingerprintedQName
     * @throws IllegalArgumentException if the Clark name is malformed
     */

    public static FingerprintedQName fromClarkName(String expandedName) {
        String namespace;
        String localName;
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
        return new FingerprintedQName("", namespace, localName);
    }

    /**
     * Make a FingerprintedQName from a Clark name
     *
     * @param expandedName the name in EQName notation "Q{uri}local" if in a namespace, or "local" otherwise.
     *                     The format "Q{}local" is also accepted for a name in no namespace.
     * @return the constructed FingerprintedQName
     * @throws IllegalArgumentException if the EQName name is malformed
     */

    public static FingerprintedQName fromEQName(String expandedName) {
        String namespace;
        String localName;
        if (expandedName.startsWith("Q{")) {
            int closeBrace = expandedName.indexOf('}', 2);
            if (closeBrace < 0) {
                throw new IllegalArgumentException("No closing '}' in EQName");
            }
            namespace = expandedName.substring(2, closeBrace);
            if (closeBrace == expandedName.length()) {
                throw new IllegalArgumentException("Missing local part in EQName");
            }
            localName = expandedName.substring(closeBrace + 1);
        } else {
            namespace = "";
            localName = expandedName;
        }
        return new FingerprintedQName("", namespace, localName);
    }


    /**
     * Ask whether this node name representation has a known namecode and fingerprint
     *
     * @return true if the methods getFingerprint() and getNameCode() will
     * return a result other than -1
     */
    @Override
    public boolean hasFingerprint() {
        return fingerprint != -1;
    }

    /**
     * Get the fingerprint of this name if known. This method should not to any work to allocate
     * a fingerprint if none is already available
     *
     * @return the fingerprint if known; otherwise -1
     */
    @Override
    public int getFingerprint() {
        return fingerprint;
    }

    /**
     * Get the fingerprint of this name, allocating a new code from the namepool if necessary
     *
     * @param pool the NamePool used to allocate the name
     * @return a fingerprint for this name, newly allocated if necessary
     */

    @Override
    public int obtainFingerprint(NamePool pool) {
        if (fingerprint == -1) {
            fingerprint = pool.allocateFingerprint(getURI(), getLocalPart());
        }
        return fingerprint;
    }

    /**
     * Get the display name, that is the lexical QName in the form [prefix:]local-part
     *
     * @return the lexical QName
     */
    @Override
    public String getDisplayName() {
        return qName.getDisplayName();
    }

    /**
     * Get the prefix of the QName.
     *
     * @return the prefix. Returns the empty string if the name is unprefixed.
     */
    @Override
    public String getPrefix() {
        return qName.getPrefix();
    }

    /**
     * Get the namespace URI of the QName.
     *
     * @return the URI. Returns the empty string to represent the no-namespace
     */
    @Override
    public String getURI() {
        return qName.getURI();
    }

    /**
     * Get the local part of the QName
     *
     * @return the local part of the QName
     */
    @Override
    public String getLocalPart() {
        return qName.getLocalPart();
    }

    /**
     * Get the name in the form of a StructuredQName
     *
     * @return the name in the form of a StructuredQName
     */
    @Override
    public StructuredQName getStructuredQName() {
        return qName;
    }

    /**
     * Test whether this name is in a given namespace
     *
     * @param ns the namespace to be tested against
     * @return true if the name is in the specified namespace
     */
    @Override
    public boolean hasURI(String ns) {
        return qName.hasURI(ns);
    }

    /**
     * Get a {@link NamespaceBinding} whose (prefix, uri) pair are the prefix and URI of this
     * node name
     *
     * @return the corresponding NamespaceBinding
     */
    @Override
    public NamespaceBinding getNamespaceBinding() {
        return qName.getNamespaceBinding();
    }

    /**
     * Get a hashCode that offers the guarantee that if A.isIdentical(B), then A.identityHashCode() == B.identityHashCode()
     *
     * @return a hashCode suitable for use when testing for identity.
     */
    @Override
    public int identityHashCode() {
        return 0;
    }

    /*
     * Compare two names for equality
     */

    public boolean equals(/*@NotNull*/ Object other) {
        if (other instanceof NodeName) {
            if (fingerprint != -1 && ((NodeName) other).hasFingerprint()) {
                return getFingerprint() == ((NodeName) other).getFingerprint();
            } else {
                return getLocalPart().equals(((NodeName) other).getLocalPart()) &&
                        hasURI(((NodeName) other).getURI());
            }
        } else {
            return false;
        }
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        return qName.hashCode();
    }

    /**
     * Determine whether two IdentityComparable objects are identical. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone.
     *
     * @param other the other value
     * @return true if the two values are indentical, false otherwise
     */
    @Override
    public boolean isIdentical(IdentityComparable other) {
        return other instanceof NodeName &&
                this.equals(other) &&
                this.getPrefix().equals(((NodeName) other).getPrefix());
    }

    @Override
    public String toString() {
        return qName.getDisplayName();
    }
}
