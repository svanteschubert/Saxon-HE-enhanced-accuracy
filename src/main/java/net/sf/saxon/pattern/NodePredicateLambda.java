////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.GNode;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.transpile.CSharpReplaceBody;

import java.util.function.Predicate;

/**
 * A {@link NodePredicate} that wraps a general predicate applied to nodes.
 *
 * <p>This is needed for C#, where it is not possible for a method
 * to accept either a NodeTest object or a Predicate supplied as a lambda expression. To supply a predicate, it is
 * therefore necessary to wrap it in a {@code NodePredicateLambda}.</p>
 *
 * <p>In SaxonJ the class is never instantiated, because the factory method {@link NodePredicateLambda#of(Predicate)}
 * returns a {@code NodePredicate}</p>
 */

public class NodePredicateLambda implements NodePredicate {

    private final Predicate<? super GNode> predicate;

    private NodePredicateLambda(Predicate<? super GNode> predicate) {
        this.predicate = predicate;
    }

    /**
     * Create a NodeTest based on a supplied predicate. The NodeTest selects a node if the
     * predicate returns true.
     * @param predicate the supplied predicate (a boolean function of a node)
     * @return a NodeTest matching the selected nodes
     */
    @CSharpReplaceBody(code="return new NodePredicateLambda(predicate);")
    public static NodePredicate of(Predicate<? super GNode> predicate) {
        return predicate::test;
    }

    @Override
    public boolean test(GNode node) {
        return predicate.test(node);
    }


}

