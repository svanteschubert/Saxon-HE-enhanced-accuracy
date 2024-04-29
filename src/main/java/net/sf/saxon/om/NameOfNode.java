////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.tree.tiny.TinyNodeImpl;
import net.sf.saxon.tree.wrapper.AbstractVirtualNode;

/**
 * An implementation of NodeName that gets the name of an existing NodeInfo object.
 * Useful when nodes are copied. However, it's not safe to use when the node is mutable.
 */
public class NameOfNode implements NodeName {

    private final NodeInfo node;

    /**
     * Constructor is private to protect against use with mutable nodes
     * @param node the node whose name is required
     */

    private NameOfNode(NodeInfo node) {
        this.node = node;
    }

    /**
     * Make a NodeName object based on the name of a supplied node.
     * @param node the supplied node
     * @return a NameOfNode object unless the node is mutable, in which case an immutable name is returned.
     */

    public static NodeName makeName(NodeInfo node) {
        if (node instanceof MutableNodeInfo) {
            return new FingerprintedQName(node.getPrefix(), node.getNamespaceUri(), node.getLocalPart());
        } else if (node instanceof AbstractVirtualNode) {
            return new NameOfNode(((AbstractVirtualNode)node).getUnderlyingNode());
        } else {
            return new NameOfNode(node);
        }
    }

    /**
     * Get the prefix of the QName.
     *
     * @return the prefix. Returns the empty string if the name is unprefixed.
     */
    @Override
    public String getPrefix() {
        return node.getPrefix();
    }

    /**
     * Get the namespace URI of the QName.
     *
     * @return the URI. Returns the empty string to represent the no-namespace
     */
    @Override
    public NamespaceUri getNamespaceUri() {
        return node.getNamespaceUri();
    }

    /**
     * Get the local part of the QName
     *
     * @return the local part of the QName
     */
    @Override
    public String getLocalPart() {
        return node.getLocalPart();
    }

    /**
     * Get the display name, that is the lexical QName in the form [prefix:]local-part
     *
     * @return the lexical QName
     */
    @Override
    public String getDisplayName() {
        return node.getDisplayName();
    }

    /**
     * Get the name in the form of a StructuredQName
     *
     * @return the name in the form of a StructuredQName
     */
    @Override
    public StructuredQName getStructuredQName() {
        return new StructuredQName(getPrefix(), getNamespaceUri(), getLocalPart());
    }

    /**
     * Test whether this name is in a given namespace
     *
     * @param ns the namespace to be tested against
     * @return true if the name is in the specified namespace
     */
    @Override
    public boolean hasURI(NamespaceUri ns) {
        if (node instanceof TinyNodeImpl) {
            // fast path (avoids object allocation)
            return ((TinyNodeImpl)node).hasURI(ns);
        }
        return node.getNamespaceUri().equals(ns);
    }

    /**
     * Get a {@link net.sf.saxon.om.NamespaceBinding} whose (prefix, uri) pair are the prefix and URI of this
     * node name
     *
     * @return the corresponding NamespaceBinding
     */

    @Override
    public NamespaceBinding getNamespaceBinding() {
        return new NamespaceBinding(getPrefix(), getNamespaceUri());
    }

    /**
     * Ask whether this node name representation has a known namecode and fingerprint
     *
     * @return true if the methods getFingerprint() and getNameCode() will
     *         return a result other than -1
     */
    @Override
    public boolean hasFingerprint() {
        return node.hasFingerprint();
    }

    /**
     * Get the fingerprint of this name if known. This method should not to any work to allocate
     * a fingerprint if none is already available
     *
     * @return the fingerprint if known; otherwise -1
     */
    @Override
    public int getFingerprint() {
        if (hasFingerprint()) {
            return node.getFingerprint();
        } else {
            return -1;
        }
    }

    /**
     * Get the nameCode of this name, allocating a new code from the namepool if necessary
     *
     * @param namePool the NamePool used to allocate the name
     * @return a nameCode for this name, newly allocated if necessary
     */
    @Override
    public int obtainFingerprint(NamePool namePool) {
        if (node.hasFingerprint()) {
            return node.getFingerprint();
        } else {
            return namePool.allocateFingerprint(node.getNamespaceUri(), node.getLocalPart());
        }
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        return StructuredQName.computeHashCode(getNamespaceUri(), getLocalPart());
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NodeName) {
            NodeName n = (NodeName) obj;
            if (node.hasFingerprint() && n.hasFingerprint()) {
                return node.getFingerprint() == n.getFingerprint();
            } else {
                return n.getLocalPart().equals(node.getLocalPart()) && n.hasURI(node.getNamespaceUri());
            }
        } else {
            return false;
        }
    }

    /**
     * Determine whether two IdentityComparable objects are identical. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone.
     *
     * @param other the value to be compared with
     * @return true if the two values are identical, false otherwise
     */
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
}
