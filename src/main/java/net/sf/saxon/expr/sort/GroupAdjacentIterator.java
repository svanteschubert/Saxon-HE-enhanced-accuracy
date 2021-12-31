////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import com.saxonica.ee.stream.ManualGroupIterator;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.Count;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.AtomicValue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A GroupAdjacentIterator iterates over a sequence of groups defined by
 * xsl:for-each-group group-adjacent="x". The groups are returned in
 * order of first appearance.
 * <p>Each step of this iterator advances to the first item of the next group,
 * leaving the members of that group in a saved list.</p>
 */

public class GroupAdjacentIterator implements GroupIterator, LastPositionFinder, LookaheadIterator {

    private Expression select;
    private FocusIterator population;
    private Expression keyExpression;
    private StringCollator collator;
    private XPathContext baseContext;
    private XPathContext runningContext;
    private List<AtomicMatchKey> currentComparisonKey;
    private AtomicSequence currentKey;
    private List<Item> currentMembers;
    private List<AtomicMatchKey> nextComparisonKey;
    private List<AtomicValue> nextKey = null;
    private Item next;
    private Item current = null;
    private int position = 0;
    private boolean composite = false;

    public GroupAdjacentIterator(Expression select, Expression keyExpression,
                                 XPathContext baseContext, StringCollator collator, boolean composite)
            throws XPathException {
        this.select = select;
        this.keyExpression = keyExpression;
        this.baseContext = baseContext;
        this.runningContext = baseContext.newMinorContext();
        this.population = runningContext.trackFocus(select.iterate(baseContext));
        this.collator = collator;
        this.composite = composite;
        next = population.next();
        if (next != null) {
            nextKey = getKey(runningContext);
            nextComparisonKey = getComparisonKey(nextKey, baseContext);
        }
    }

    @Override
    public int getLength() throws XPathException {
        GroupAdjacentIterator another = new GroupAdjacentIterator(select, keyExpression, baseContext, collator, composite);
        return Count.steppingCount(another);
    }

    private List<AtomicValue> getKey(XPathContext context) throws XPathException {
        List<AtomicValue> key = new ArrayList<>();
        SequenceIterator iter = keyExpression.iterate(context);
        while (true) {
            AtomicValue val = (AtomicValue) iter.next();
            if (val == null) {
                break;
            }
            key.add(val);
        }
        return key;
    }

    private List<AtomicMatchKey> getComparisonKey(List<AtomicValue> key, XPathContext keyContext) throws XPathException {
        List<AtomicMatchKey> ckey = new ArrayList<>(key.size());
        for (AtomicValue aKey : key) {
            AtomicMatchKey comparisonKey;
            if (aKey.isNaN()) {
                comparisonKey = AtomicMatchKey.NaN_MATCH_KEY;
            } else {
                comparisonKey = aKey.getXPathComparable(false, collator, keyContext.getImplicitTimezone());
            }
            ckey.add(comparisonKey);
        }
        return ckey;
    }

    private void advance() throws XPathException {
        currentMembers = new ArrayList<>(20);
        currentMembers.add(current);
        while (true) {
            Item nextCandidate = population.next();
            if (nextCandidate == null) {
                break;
            }
            List<AtomicValue> newKey = getKey(runningContext);
            List<AtomicMatchKey> newComparisonKey = getComparisonKey(newKey, baseContext);

            try {
                if (currentComparisonKey.equals(newComparisonKey)) {
                    currentMembers.add(nextCandidate);
                } else {
                    next = nextCandidate;
                    nextComparisonKey = newComparisonKey;
                    nextKey = newKey;
                    return;
                }
            } catch (ClassCastException e) {
                String message = "Grouping key values are of non-comparable types";
                XPathException err = new XPathException(message);
                err.setIsTypeError(true);
                err.setXPathContext(runningContext);
                throw err;
            }
        }
        next = null;
        nextKey = null;
    }

    @Override
    public AtomicSequence getCurrentGroupingKey() {
        return currentKey;
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
        if (next == null) {
            current = null;
            position = -1;
            return null;
        }
        current = next;
        if (nextKey.size() == 1) {
            currentKey = nextKey.get(0);
        } else {
            currentKey = new AtomicArray(nextKey);
        }
        currentComparisonKey = nextComparisonKey;
        position++;
        advance();
        return current;
    }

    @Override
    public void close() {
        population.close();
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link net.sf.saxon.om.SequenceIterator.Property#GROUNDED}, {@link net.sf.saxon.om.SequenceIterator.Property#LAST_POSITION_FINDER},
     *         and {@link net.sf.saxon.om.SequenceIterator.Property#LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    @Override
    public EnumSet<Property> getProperties() {
        return EnumSet.of(Property.LOOKAHEAD, Property.LAST_POSITION_FINDER);
    }



}

