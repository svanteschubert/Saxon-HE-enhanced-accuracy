////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.NodeInfo;

/**
 * Interface to a function that wraps nodes from an external object model in a Saxon NodeInfo
 * representation
 *
 * @param <B> the type of the node in the external object model
 * @param <T> the type of the Saxon node
 */

public interface NodeWrappingFunction<B, T extends NodeInfo> {

    T wrap(B node);
}

