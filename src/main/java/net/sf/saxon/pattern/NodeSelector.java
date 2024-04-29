////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.UType;

import java.util.function.Predicate;

/**
 * A NodeTest that wraps a general Predicate applied to nodes.
 *
 * <p>This is needed for C#, where it is not possible for a method such as {@link NodeInfo#iterateAxis(int, NodePredicate)}
 * to accept either a NodeTest object or a Predicate supplied as a lambda expression. To supply a predicate, it is
 * therefore necessary to wrap it in a {@code NodeSelector}.</p>
 */

public class NodeSelector extends NodeTest {

    private final Predicate<? super NodeInfo> predicate;

    private NodeSelector(Predicate<? super NodeInfo> predicate) {
        this.predicate = predicate;
    }

    /**
     * Create a NodeTest based on a supplied predicate. The NodeTest selects a node if the
     * predicate returns true.
     * @param predicate the supplied predicate (a boolean function of a node)
     * @return a NodeTest matching the selected nodes
     */
    public static NodeSelector of(Predicate<? super NodeInfo> predicate) {
        return new NodeSelector(predicate);
    }

    @Override
    public boolean test(NodeInfo node) {
        return predicate.test(node);
    }

    @Override
    public double getDefaultPriority() {
        return 0;
    }

    @Override
    public boolean matches(int nodeKind, NodeName name, SchemaType annotation) {
        throw new UnsupportedOperationException("NodePredicate doesn't support this method");
    }

    @Override
    public UType getUType() {
        return UType.ANY;
    }
}

