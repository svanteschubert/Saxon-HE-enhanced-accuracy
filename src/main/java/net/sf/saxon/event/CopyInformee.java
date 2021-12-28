////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.NodeInfo;

/**
 * A CopyInformee is an agent that receives extra information while a tree is being copied. Specifically,
 * each time an element node is copied to the receiver, before calling the startElement() method, the copying
 * code will first call notifyElementNode(), giving the informee extra information about the element currently
 * being copied.
 */
public interface CopyInformee<T extends Object> {

    /**
     * Provide information about the node being copied. This method is called immediately before
     * the startElement call for the element node in question.
     *
     * @param element the node being copied, which must be an element node
     * @return information about this node
     */

    T notifyElementNode(NodeInfo element);
}

