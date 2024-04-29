////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.transpile.CSharpDelegate;

import java.util.function.Predicate;

/**
 * Interface representing a predicate applied to a node.
 *
 * <p>Note: the reason for not simply using {@code Predicate<NodeInfo>} is for C#: interfaces and delegates
 * need to be provided separately.
 */
@FunctionalInterface
@CSharpDelegate(false)
public interface NodePredicate {
    boolean test(NodeInfo node);
}

