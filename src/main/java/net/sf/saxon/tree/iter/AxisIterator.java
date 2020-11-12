////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.om.NodeInfo;

import java.util.Iterator;
import java.util.function.Consumer;


/**
 * A SequenceIterator is used to iterate over a sequence of items. An AxisIterator
 * is a SequenceIterator that throws no exceptions, and that always returns
 * nodes. The nodes should all be in the same document (though there are
 * some cases, such as PrependIterator, where this is the responsibility of the
 * user of the class and is not enforced.)
 */

public interface AxisIterator extends UnfailingIterator {

    @Override
    NodeInfo next();

    /**
     * Get a Java {@link Iterator} over the same nodes as this {@code AxisIterator}.
     * This is normally called when the iterator is positioned at the start; in principle,
     * however, it can be called at any point in the iteration. The Java iterator picks
     * up where the original {@code AxisIterator} left off
     * @return a Java {@link Iterator} over the same nodes as this {@code AxisIterator}.
     */

    default Iterator<NodeInfo> asIterator() {
        return new Iterator<NodeInfo>() {
            NodeInfo next = AxisIterator.this.next();

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public NodeInfo next() {
                NodeInfo curr = next;
                next = AxisIterator.this.next();
                return curr;
            }
        };
    }

    default void forEachNode(Consumer<? super NodeInfo> consumer) {
        NodeInfo item;
        while ((item = next()) != null) {
            consumer.accept(item);
        }
    }
}

