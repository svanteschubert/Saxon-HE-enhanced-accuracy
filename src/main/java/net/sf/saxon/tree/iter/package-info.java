/**
 * <p>This package defines implementations and subinterfaces of the interface SequenceIterator, which is
 * used to iterate over an XDM sequence.</p>
 * <p>The subinterfaces mostly represent iterators with additional capability: for example a LastPositionFinder
 * can determine the number of items in the sequence without reading to the end; a GroundedIterator can deliver
 * the original sequence as a list in memory; a LookaheadIterator is capable of one-item look-ahead. Note
 * that just because a class implements such an interface does not mean it necessarily has this capability;
 * it is necessary to check the properties of the specific iterator before assuming this.</p>
 */
package net.sf.saxon.tree.iter;
