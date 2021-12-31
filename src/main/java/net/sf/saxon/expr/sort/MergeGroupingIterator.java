////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import com.saxonica.ee.stream.ManualGroupIterator;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.AtomicArray;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ExternalObject;
import net.sf.saxon.value.ObjectValue;

import java.util.*;

/**
 * An iterator that groups the result of merging several xsl:merge input streams, identifying
 * groups of adjacent items having the same merge key value
 */

public class MergeGroupingIterator implements GroupIterator, LookaheadIterator, LastPositionFinder {

    private SequenceIterator baseItr;
    private ObjectValue<ItemWithMergeKeys> currenti = null;
    private ObjectValue<ItemWithMergeKeys> next;
    private List<Item> currentMembers;
    private Map<String, List<Item>> currentSourceMembers;
    private ItemOrderComparer comparer;
    private int position = 0;
    List<AtomicValue> compositeMergeKey;
    private LastPositionFinder lastPositionFinder;


    public MergeGroupingIterator(
            SequenceIterator p1,
            ItemOrderComparer comp, LastPositionFinder lpf) throws XPathException {
        this.baseItr = p1;
        next = (ObjectValue<ItemWithMergeKeys>)p1.next();
        if (next != null) {
            compositeMergeKey = ((ItemWithMergeKeys) ((ObjectValue) next).getObject()).sortKeyValues;
        }
        this.comparer = comp;
        this.lastPositionFinder = lpf;
    }


    /**
     * The advance() method reads ahead a group of items having common merge key values. These items are
     * placed in the variable currentMembers. The variable next is left at the next item after this group,
     * or null if there are no more items
     * @throws XPathException if a failure occurs reading the input, or if merge keys are out of order, or
     * not comparable
     */
    private void advance() throws XPathException {
        currentMembers = new ArrayList<>(20);
        currentSourceMembers = new HashMap<>(20);
        Item currentItem = currenti.getObject().baseItem;
        String source = currenti.getObject().sourceName;
        currentMembers.add(currentItem);
        if (source != null) {
            List<Item> list = new ArrayList<>();
            list.add(currentItem);
            currentSourceMembers.put(source, list);
        }
        while (true) {
            ObjectValue<ItemWithMergeKeys> nextCandidate = (ObjectValue<ItemWithMergeKeys>)baseItr.next();
            if (nextCandidate == null) {
                next = null;
                return;
            }

            try {
                int c = comparer.compare(currenti, nextCandidate);
                if (c == 0) {
                    currentItem = nextCandidate.getObject().baseItem;
                    source = nextCandidate.getObject().sourceName;
                    currentMembers.add(currentItem);
                    if (source != null) {
                        List<Item> list = currentSourceMembers.computeIfAbsent(source, k -> new ArrayList<>());
                        list.add(currentItem);
                    }
                } else if (c > 0) {
                    List<AtomicValue> keys = nextCandidate.getObject().sortKeyValues;
                    throw new XPathException(
                            "Merge input for source " + source + " is not ordered according to merge key, detected at key value: " +
                                    Arrays.toString(keys.toArray()), "XTDE2220");
                } else {
                    next = nextCandidate;
                    return;
                }
            } catch (ClassCastException e) {
                XPathException err = new XPathException("Merge key values are of non-comparable types ("
                        + Type.displayTypeName(currentItem) + " and " + Type.displayTypeName(nextCandidate.getObject().baseItem) + ')', "XTTE2230");
                err.setIsTypeError(true);
                throw err;
            }

        }
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public Item next() throws XPathException {
        if (next == null) {
            currenti = null;
            position = -1;
            return null;
        }
        currenti = next;
        position++;
        compositeMergeKey = ((ItemWithMergeKeys) ((ExternalObject) next).getObject()).sortKeyValues;
        advance();
        return currenti.getObject().baseItem;
    }

    @Override
    public void close() {
        baseItr.close();
    }

    @Override
    public int getLength() throws XPathException {
        return lastPositionFinder.getLength();
    }

    @Override
    public EnumSet<Property> getProperties() {
        return EnumSet.of(Property.LOOKAHEAD, Property.LAST_POSITION_FINDER);
    }

    @Override
    public AtomicSequence getCurrentGroupingKey() {
        return new AtomicArray(compositeMergeKey);
    }

    @Override
    public SequenceIterator iterateCurrentGroup() {
        return new ListIterator<>(currentMembers);
    }

    public SequenceIterator iterateCurrentGroup(String source) {
        List<Item> sourceMembers = currentSourceMembers.get(source);
        if (sourceMembers == null) {
            return EmptyIterator.emptyIterator();
        } else {
            return new ListIterator<>(sourceMembers);
        }
    }


}


