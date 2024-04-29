////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceExtent;

import java.util.ArrayList;
import java.util.List;


/**
 * This receiver is used when writing a sequence of atomic values and nodes, that
 * is, when xsl:variable is used with content and an "as" attribute. The receiver
 * builds the sequence and provides access to it. Note that the event sequence can include calls such as
 * startElement and endElement that require trees to be built. If nodes such as attributes
 * and text nodes are received while an element is being constructed, the nodes are added
 * to the tree. Otherwise, "orphan" nodes (nodes with no parent) are created and added
 * directly to the sequence.
 */

public final class SequenceCollector extends SequenceWriter {

    private List<Item> list;

    /**
     * Create a new SequenceOutputter
     *
     * @param pipe the pipeline configuration
     */

    public SequenceCollector(PipelineConfiguration pipe) {
        this(pipe, 20);
    }

    public SequenceCollector(PipelineConfiguration pipe, int estimatedSize) {
        super(pipe);
        this.list = new ArrayList<>(estimatedSize);
    }

    /**
     * Clear the contents of the SequenceCollector and make it available for reuse
     */

    public void reset() {
        list = new ArrayList<>(Math.min(list.size() + 10, 50));
    }

    /**
     * Method to be supplied by subclasses: output one item in the sequence.
     */

    @Override
    public void write(Item item) {
        list.add(item);
    }

    /**
     * Get the sequence that has been built
     *
     * @return the value (sequence of items) that have been written to this SequenceOutputter
     */

    public GroundedValue getSequence() {
        switch (list.size()) {
            case 0:
                return EmptySequence.getInstance();
            case 1:
                return list.get(0);
            default:
                return new SequenceExtent.Of<>(list);
        }
    }

    /**
     * Get an iterator over the sequence of items that has been constructed
     *
     * @return an iterator over the items that have been written to this SequenceOutputter
     */

    public SequenceIterator iterate() {
        if (list.isEmpty()) {
            return EmptyIterator.getInstance();
        } else {
            return new ListIterator.Of<>(list);
        }
    }

    /**
     * Get the list containing the sequence of items
     *
     * @return the list of items that have been written to this SequenceOutputter
     */

    public List<Item> getList() {
        return list;
    }

    /**
     * Get the first item in the sequence that has been built
     *
     * @return the first item in the list of items that have been written to this SequenceOutputter;
     *         or null if the list is empty.
     */

    public Item getFirstItem() {
        if (list.isEmpty()) {
            return null;
        } else {
            return list.get(0);
        }
    }

}

