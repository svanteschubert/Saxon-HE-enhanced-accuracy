////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.type.Type;

/**
 * Interface defining methods common to the TinyTree and the Domino tree model. These two models are
 * recognized by the {@link NodeTest} class, which is able to match nodes without actually instantiating
 * the NodeInfo object
 */
public interface NodeVectorTree {

    /**
     * Ask whether the tree contains non-trivial type information (from schema validation)
     * @return true if type information is present
     */
    default boolean isTyped() {
        return false;
    }

    /**
     * Construct a NodeInfo representing the node at a given position in the tree
     * @param nodeNr the node number in the tree
     * @return the constructed NodeInfo
     */

    NodeInfo getNode(int nodeNr);

    /**
     * Get the kind of node at a given position in the tree
     * @param nodeNr the node number
     * @return the kind of node, for example {@link Type#ELEMENT}
     */

    int getNodeKind(int nodeNr);

    /**
     * Get the integer fingerprint of the node at a given position in the tree
     *
     * @param nodeNr the node number
     * @return the fingerprint of the node, as registered in the NamePool. -1 for an unnamed node.
     */

    int getFingerprint(int nodeNr);



    byte[] getNodeKindArray();

    int[] getNameCodeArray();


}

// Copyright (c) 2018-2020 Saxonica Limited

