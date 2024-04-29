////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.AdjacentTextNodeMerger;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.Type;

import java.io.Closeable;

/**
 * AdjacentTextNodeMergingIterator is an iterator that eliminates zero-length text nodes
 * and merges adjacent text nodes from the underlying iterator
 */

public class AdjacentTextNodeMergingIterator implements LookaheadIterator, Closeable {

    private final SequenceIterator base;
    private Item _next;

    public AdjacentTextNodeMergingIterator(SequenceIterator base) throws XPathException {
        try {
            this.base = base;
            _next = base.next();
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }

    @Override
    public boolean hasNext() {
        return _next != null;
    }

    /*@Nullable*/
    @Override
    public Item next() {
        Item current = _next;
        if (current == null) {
            return null;
        }
        _next = base.next();

        if (AdjacentTextNodeMerger.isTextNode(current)) {
            UnicodeBuilder ub = new UnicodeBuilder();
            ub.accept(current.getUnicodeStringValue());
            while (AdjacentTextNodeMerger.isTextNode(_next)) {
                ub.accept(_next.getUnicodeStringValue() /*.toString() */);
                // NOTE: toString() shouldn't be necessary - added 2011-05-05 for bug workaround; removed again 2011-07-14
                _next = base.next();
            }
            if (ub.isEmpty()) {
                return next();
            } else {
                Orphan o = new Orphan(((NodeInfo) current).getConfiguration());
                o.setNodeKind(Type.TEXT);
                o.setStringValue(ub.toUnicodeString());
                current = o;
                return current;
            }
        } else {
            return current;
        }
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    @Override
    public void close() {
        base.close();
    }

}

