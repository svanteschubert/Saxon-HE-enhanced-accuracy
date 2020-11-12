////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api.streams;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;

import java.util.stream.Stream;

/**
 * An AxisStep is a {@link Step} that implements one of the 13 XPath Axes.
 */
class AxisStep extends Step<XdmNode> {

    private Axis axis;

    public AxisStep(Axis axis) {
        this.axis = axis;
    }

    /**
     * Apply this function to the given argument. In other words, return a stream of nodes
     * selected by this XPath axis, starting at the supplied origin
     *
     * @param node the origin node for the navigation
     * @return the stream of nodes selected by the axis. The nodes are returned in Axis order
     * (for a reverse axis like preceding-sibling or ancestor, this is the reverse of document
     * order). If the supplied argument is not a node, return an empty stream.
     */
    @Override
    public Stream<? extends XdmNode> apply(XdmItem node) {
        return node instanceof XdmNode ? ((XdmNode)node).axisIterator(axis).stream() : Stream.empty();
    }
}

