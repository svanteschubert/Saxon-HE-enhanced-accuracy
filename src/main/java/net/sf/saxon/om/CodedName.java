////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

/**
 * An implementation of NodeName that encapsulates an integer fingerprint, a string prefix, and a reference to the NamePool from which
 * the fingerprint was allocated.
 */
public class CodedName implements NodeName {

    private int fingerprint;
    private String prefix;
    private NamePool pool;

    public CodedName(int fingerprint, String prefix, NamePool pool) {
//        if (fingerprint >> 20 != 0) {
//            throw new IllegalArgumentException();
//        }
        this.fingerprint = fingerprint;
        this.prefix = prefix;
        this.pool = pool;
    }

    /**
     * Get the prefix of the QName.
     *
     * @return the prefix. Returns the empty string if the name is unprefixed.
     */
    @Override
    public String getPrefix() {
        return prefix;
    }

    /**
     * Get the namespace URI of the QName.
     *
     * @return the URI. Returns the empty string to represent the no-namespace
     */
    @Override
    public String getURI() {
        return pool.getURI(fingerprint);
    }

    /**
     * Get the local part of the QName
     *
     * @return the local part of the QName
     */
    @Override
    public String getLocalPart() {
        return pool.getLocalName(fingerprint);
    }

    /**
     * Get the display name, that is the lexical QName in the form [prefix:]local-part
     *
     * @return the lexical QName
     */
    @Override
    public String getDisplayName() {
        return prefix.isEmpty() ? getLocalPart() : prefix + ":" + getLocalPart();
    }

    /**
     * Get the name in the form of a StructuredQName
     *
     * @return the name in the form of a StructuredQName
     */
    @Override
    public StructuredQName getStructuredQName() {
        StructuredQName qn = pool.getUnprefixedQName(fingerprint);
        if (prefix.isEmpty()) {
            return qn;
        } else {
            return new StructuredQName(prefix, qn.getURI(), qn.getLocalPart());
        }
    }

    /**
     * Test whether this name is in a given namespace
     *
     * @param ns the namespace to be tested against
     * @return true if the name is in the specified namespace
     */
    @Override
    public boolean hasURI(String ns) {
        return getURI().equals(ns);
    }

    /**
     * Get a {@link net.sf.saxon.om.NamespaceBinding} whose (prefix, uri) pair are the prefix and URI of this
     * node name
     *
     * @return the corresponding NamespaceBinding
     */

    @Override
    public NamespaceBinding getNamespaceBinding() {
        return new NamespaceBinding(prefix, pool.getURI(fingerprint));
    }


    /**
     * Ask whether this node name representation has a known fingerprint
     *
     * @return true if the method getFingerprint() will
     *         return a result other than -1
     */
    @Override
    public boolean hasFingerprint() {
        return true;
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
     * Get the nameCode of this name, allocating a new code from the namepool if necessary
     *
     * @param namePool the NamePool used to allocate the name
     * @return a nameCode for this name, newly allocated if necessary
     */
    @Override
    public int obtainFingerprint(NamePool namePool) {
        return fingerprint;
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        return StructuredQName.computeHashCode(getURI(), getLocalPart());
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NodeName) {
            NodeName n = (NodeName) obj;
            if (n.hasFingerprint()) {
                return getFingerprint() == n.getFingerprint();
            } else {
                return n.getLocalPart().equals(getLocalPart()) && n.hasURI(getURI());
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean isIdentical(IdentityComparable other) {
        return other instanceof NodeName &&
                this.equals(other) && this.getPrefix().equals(((NodeName) other).getPrefix());
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

    /**
     * The toString() method returns the value of getDisplayName(), that is, the lexical QName
     * @return the value as a lexical QName
     */

    @Override
    public String toString() {
        return getDisplayName();
    }
}

