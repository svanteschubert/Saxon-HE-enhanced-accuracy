// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern.nodetest;

import net.sf.saxon.om.StructuredQName;

/**
 * Interface implemented by a node test that selects nodes of a specific node
 * kind with a specific node name with a known
 * fingerprint. Having the right name and node kind is a necessary
 * condition for satisfying the predicate, and if {@link #isFingerprintSufficient()} returns
 * true, it is also a sufficient condition.
 */

public interface NamedXNodePredicate extends NodePredicate {

    /**
     * Get the kind of nodes that this node predicate matches
     * @return the kind of nodes, for example {@link net.sf.saxon.type.Type#ELEMENT} or {@code net.sf.saxon.type.Type#ATTRIBUTE}
     */

    int getNodeKind();

    /**
     * Get a fingerprint that all nodes (of the specified node kind) must have
     * if they are to satisfy the predicate.
     *
     * @return the fingerprint of the name that a node must have if it is to satisfy the predicate
     */

    int getRequiredFingerprint();

    /**
     * Get the corresponding StructuredQName: the name that all nodes must have
     * if they are to satisfy the predicate
     *
     * @return the name that a node must have if it is to satisfy the predicate
     */

    StructuredQName getMatchingNodeName();

    /**
     * Ask whether having the required fingerprint and node kind is a sufficient
     * condition for a node to satisfy the predicate, or whether other conditions
     * (such as type annotation or nillability) must also be satisfied.
     * @return true if matching the fingerprint is a sufficient condition.
     */

    boolean isFingerprintSufficient();

}

