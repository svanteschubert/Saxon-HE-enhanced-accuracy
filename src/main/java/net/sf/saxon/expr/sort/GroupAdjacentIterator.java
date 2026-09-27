////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

//import com.saxonica.ee.stream.ManualGroupIterator;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.functions.Count;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceExtent;

import java.util.ArrayList;
import java.util.List;

/**
 * A GroupAdjacentIterator iterates over a sequence of groups defined by
 * xsl:for-each-group group-adjacent="x". The groups are returned in
 * order of first appearance.
 * <p>Each step of this iterator advances to the first item of the next group,
 * leaving the members of that group in a saved list.</p>
 */

public class GroupAdjacentIterator implements GroupIterator, LastPositionFinder, LookaheadIterator {

    private final PullEvaluator select;
    private final FocusIterator population;
    private final Expression keyExpression;
    private final StringCollator collator;
    private final XPathContext baseContext;
    private final XPathContext runningContext;
    private CompositeAtomicKey currentComparisonKey;
    private AtomicSequence currentKey;
    private List<Item> currentMembers;
    private CompositeAtomicKey nextComparisonKey;
    private List<AtomicValue> nextKey = null;
    private Item nextItem;
    private Item current = null;
    private int position = 0;
    private boolean composite = false;

    public GroupAdjacentIterator(PullEvaluator select, Expression keyExpression,
                                 XPathContext baseContext, StringCollator collator, boolean composite)
            throws XPathException {
        this.select = select;
        this.keyExpression = keyExpression;
        this.baseContext = baseContext;
        this.runningContext = baseContext.newMinorContext();
        this.population = runningContext.trackFocus(select.iterate(baseContext));
        this.collator = collator;
        this.composite = composite;
        nextItem = population.next();
        if (nextItem != null) {
            nextKey = getKey(runningContext);
            nextComparisonKey = getComparisonKey(nextKey, baseContext);
        }
    }

    @Override
    public boolean supportsGetLength() {
        return true;
    }

    @Override
    public int getLength() {
        try {
            GroupAdjacentIterator another = new GroupAdjacentIterator(select, keyExpression, baseContext, collator, composite);
            return Count.steppingCount(another);
        } catch (XPathException e) {
            throw new UncheckedXPathException(e);
        }
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

    private CompositeAtomicKey getComparisonKey(List<AtomicValue> key, XPathContext keyContext) throws XPathException {
        List<AtomicMatchKey> ckey = new ArrayList<>(key.size());
        for (AtomicValue aKey : key) {
            AtomicMatchKey comparisonKey;
            if (aKey.isNaN()) {
                comparisonKey = AtomicSortComparer.COLLATION_KEY_NaN;
            } else {
                comparisonKey = aKey.getXPathMatchKey(collator, keyContext.getImplicitTimezone(), 31);
            }
            ckey.add(comparisonKey);
        }
        return new CompositeAtomicKey(ckey);
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
            CompositeAtomicKey newComparisonKey = getComparisonKey(newKey, baseContext);

            try {
                if (newComparisonKey.equals(currentComparisonKey)) {
                    currentMembers.add(nextCandidate);
                } else {
                    nextItem = nextCandidate;
                    nextComparisonKey = newComparisonKey;
                    nextKey = newKey;
                    return;
                }
            } catch (ClassCastException e) {
                throw new XPathException("Grouping key values are of non-comparable types")
                        .asTypeError()
                        .withXPathContext(runningContext);
            }
        }
        nextItem = null;
        nextKey = null;
    }

    @Override
    public AtomicSequence getCurrentGroupingKey() {
        return currentKey;
    }

    @Override
    public GroundedValue currentGroup() throws XPathException {
        return SequenceExtent.makeSequenceExtent(currentMembers);
    }

    @Override
    public boolean supportsHasNext() {
        return true;
    }

    @Override
    public boolean hasNext() {
        return nextItem != null;
    }

    @Override
    public Item next() {
        try {
            if (nextItem == null) {
                current = null;
                position = -1;
                return null;
            }
            current = nextItem;
            if (nextKey.size() == 1) {
                currentKey = nextKey.get(0);
            } else {
                currentKey = new AtomicArray(nextKey);
            }
            currentComparisonKey = nextComparisonKey;
            position++;
            advance();
            return current;
        } catch (XPathException e) {
            throw new UncheckedXPathException(e);
        }
    }

    @Override
    public void close() {
        population.close();
    }



}

