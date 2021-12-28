////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.expr.sort.DocumentOrderIterator;
import net.sf.saxon.expr.sort.GlobalOrderComparer;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.List;

/**
 * An iterator that returns the same items as its base iterator, checking to see that they are either
 * all nodes, or all non-nodes; if they are all nodes, it delivers them in document order.
 */

public class HomogeneityCheckerIterator implements SequenceIterator {

    SequenceIterator base = null;
    Location loc;
    int state;
    // state = 0: initial state, will accept either nodes or atomic values
    // state = +1: have seen a node, all further items must be nodes
    // state = -1: have seen an atomic value or function item, all further items must be the same

    public HomogeneityCheckerIterator(SequenceIterator base, Location loc) {
        this.base = base;
        this.loc = loc;
        state = 0;
    }

    @Override
    public void close() {
        base.close();
    }

    /*@NotNull*/
    private XPathException reportMixedItems() {
        XPathException err = new XPathException("Cannot mix nodes and atomic values in the result of a path expression");
        err.setErrorCode("XPTY0018");
        err.setLocator(loc);
        return err;
    }


    /*@Nullable*/
    @Override
    public Item next() throws XPathException {
        Item item = base.next();
        if (item == null) {
            return null;
        }
        //first item in iterator
        if (state == 0) {
            if (item instanceof NodeInfo) {
                List<Item> nodes = new ArrayList<>(50);
                nodes.add(item);
                while ((item = base.next()) != null) {
                    if (!(item instanceof NodeInfo)) {
                        throw reportMixedItems();
                    } else {
                        nodes.add(item);
                    }
                }
                base = new DocumentOrderIterator(new ListIterator<>(nodes), GlobalOrderComparer.getInstance());
                state = 1; // first item is a node
                return base.next();
            } else {
                state = -1; // first item is an atomic value or function item
            }
        } else if (state == -1 && item instanceof NodeInfo) {
            throw reportMixedItems();
        }
        return item;
    }


}
