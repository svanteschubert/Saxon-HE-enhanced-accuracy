////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.FocusIterator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.EnumSet;
import java.util.List;

/**
 * A GroupMatchingIterator contains code shared between GroupStartingIterator and GroupEndingIterator
 */

public abstract class GroupMatchingIterator implements LookaheadIterator, LastPositionFinder, GroupIterator {

    protected Expression select;
    protected FocusIterator population;
    protected Pattern pattern;
    protected XPathContext baseContext;
    protected XPathContext runningContext;
    protected List<Item> currentMembers;
    /*@Nullable*/ protected Item next;
    protected Item current = null;
    protected int position = 0;


    protected abstract void advance() throws XPathException;

    @Override
    public AtomicSequence getCurrentGroupingKey() {
        return null;
    }

    @Override
    public SequenceIterator iterateCurrentGroup() {
        return new ListIterator<>(currentMembers);
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public Item next() throws XPathException {
        if (next != null) {
            current = next;
            position++;
            advance();
            return current;
        } else {
            current = null;
            position = -1;
            return null;
        }
    }

    @Override
    public void close() {
        population.close();
    }

    @Override
    public EnumSet<Property> getProperties() {
        return EnumSet.of(Property.LOOKAHEAD, Property.LAST_POSITION_FINDER);
    }


}

