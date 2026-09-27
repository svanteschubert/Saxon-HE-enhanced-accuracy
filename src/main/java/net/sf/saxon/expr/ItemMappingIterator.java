////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.tree.iter.LookaheadIterator;

/**
 * ItemMappingIterator applies a mapping function to each item in a sequence.
 * The mapping function either returns a single item, or null (representing an
 * empty sequence).
 * <p>This is a specialization of the more general MappingIterator class, for use
 * in cases where a single input item never maps to a sequence of more than one
 * output item.</p>
 * <p>The Java implementation accepts a lambda expression as the mapping function
 * (because it is a functional interface, even though not declared as such). To achieve
 * the same effect on C# we add extra constructors that explicitly accept a lambda
 * expression, by virtue of the fact that the expected type is a functional interface
 * (which translates to a C# delegate)</p>
 */

@CSharpInjectMembers(code={
        "public ItemMappingIterator(Saxon.Hej.om.SequenceIterator first, Saxon.Hej.expr.ItemMapper.Lambda action) : this(first, Saxon.Hej.expr.ItemMapper.of(action)) {}",
        "public ItemMappingIterator(Saxon.Hej.om.SequenceIterator first, Saxon.Hej.expr.ItemMapper.Lambda action, bool oneToOne) : this(first, Saxon.Hej.expr.ItemMapper.of(action), oneToOne) {}"
})
                
public class ItemMappingIterator
        implements SequenceIterator, LookaheadIterator, LastPositionFinder {

    private final SequenceIterator base;
    private final ItemMappingFunction action;
    private boolean oneToOne = false;

    /**
     * Construct an ItemMappingIterator that will apply a specified ItemMappingFunction to
     * each Item returned by the base iterator.
     *
     * @param base   the base iterator
     * @param action the mapping function to be applied.
     */

    public ItemMappingIterator(SequenceIterator base, ItemMappingFunction action) {
        this.base = base;
        this.action = action;
    }

    /**
     * Factory method designed for use when the mapping function is a lambda expression
     * Example of usage: {@code ItemMappingIterator.map(base, item -> item.getParentNode())}
     * @param base iterator over the base sequence
     * @param mappingExpression function to be applied to items in the base sequence
     * @return an iterator over the items determined by applying the mapping expression
     * to every item in the base sequence (a flatMap operation).
     */

    public static ItemMappingIterator map(SequenceIterator base, ItemMapper.Lambda mappingExpression) {
        return new ItemMappingIterator(base, ItemMapper.of(mappingExpression));
    }

    /**
     * Factory method designed for use when the mapping function is designed to filter the input nodes
     * Example of usage: {@code ItemMappingIterator.filter(base, item -> item.hasChildNodes())}
     * @param base iterator over the base sequence
     * @param filterExpression predicate to be applied to items in the base sequence
     * @return an iterator over the items in the base sequence that satisfy the predicate
     */

    public static ItemMappingIterator filter(SequenceIterator base, ItemFilter.Lambda filterExpression) {
        return new ItemMappingIterator(base, ItemFilter.of(filterExpression));
    }

    /**
     * Construct an ItemMappingIterator that will apply a specified ItemMappingFunction to
     * each Item returned by the base iterator.
     *
     * @param base     the base iterator
     * @param action   the mapping function to be applied
     * @param oneToOne true if this iterator is one-to-one
     */

    public ItemMappingIterator(SequenceIterator base, ItemMappingFunction action, boolean oneToOne) {
        this.base = base;
        this.action = action;
        this.oneToOne = oneToOne;
    }

    /**
     * Say whether this ItemMappingIterator is one-to-one: that is, for every input item, there is
     * always exactly one output item. The default is false.
     *
     * @param oneToOne true if this iterator is one-to-one
     */

    public void setOneToOne(boolean oneToOne) {
        this.oneToOne = oneToOne;
    }

    /**
     * Ask whether this ItemMappingIterator is one-to-one: that is, for every input item, there is
     * always exactly one output item. The default is false.
     *
     * @return true if this iterator is one-to-one
     */

    public boolean isOneToOne() {
        return oneToOne;
    }

    /**
     * Get the base (input) iterator
     * @return the iterator over the input sequence
     */

    protected SequenceIterator getBaseIterator() {
        return base;
    }

    /**
     * Get the mapping function (the function applied to each item in the input sequence
     * @return the mapping function
     */

    protected ItemMappingFunction getMappingFunction() {
        return action;
    }

    @Override
    public boolean supportsHasNext() {
        return oneToOne && base instanceof LookaheadIterator && ((LookaheadIterator)base).supportsHasNext();
    }


    @Override
    public boolean hasNext() {
        // Must only be called if this is a lookahead iterator, which will only be true if the base iterator
        // is a lookahead iterator and one-to-one is true
        return ((LookaheadIterator) base).hasNext();
    }

    @Override
    @CSharpModifiers(code={"public", "virtual"})
    public Item next() {
        try {
            while (true) {
                Item nextSource = base.next();
                if (nextSource == null) {
                    return null;
                }
                // Call the supplied mapping function
                Item current = action.mapItem(nextSource);
                if (current != null) {
                    return current;
                }
                // otherwise go round the loop to get the next item from the base sequence
            }
        } catch (XPathException e) {
            throw new UncheckedXPathException(e);
        }
    }

    @Override
    @CSharpModifiers(code = {"public", "virtual"})
    public void close() {
        base.close();
    }

    @Override
    public boolean supportsGetLength() {
        return oneToOne && SequenceTool.supportsGetLength(base);
    }

    @Override
    public int getLength() {
        return SequenceTool.getLength(base);
    }

}

