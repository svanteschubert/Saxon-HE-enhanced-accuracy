package net.sf.saxon.ma.jnode;

import net.sf.saxon.expr.Atomizer;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;

/**
 * Abstract implementation of JNode, covering both root and non-root JNodes
 */
public abstract class JNode implements GNode {

    /**
     * Get the value of the content property
     * @return the content of the JNode
     */
    public abstract GroundedValue getContent();

    /**
     * Ask whether the JNode has any children
     * @return true if there are children: that is, if the content includes
     * an array or map
     */
    public boolean hasChildNodes() {
        GroundedValue value = getContent();
        for (Item item : value.asIterable()) {
            if (item instanceof ArrayItem && !((ArrayItem)item).isEmpty()) {
                return true;
            }
            if (item instanceof MapItem && !((MapItem) item).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the value of the position property
     * @return the position
     */

    public abstract int getPosition();

    /**
     * Get the value of the selector property
     * @return the selector. Returns null if the property is absent (for a root JNode)
     */

    public abstract AtomicValue getSelector();

    /**
     * Get the parent of this JNode
     * @return the value of the parent property
     */
    @Override
    public abstract JNode getParent();

    /**
     * Get the root of this JTree
     * @return the root of the tree
     */

    public RootJNode getRoot() {
        JNode j = this;
        while (!(j instanceof RootJNode)) {
            j = j.getParent();
        }
        return (RootJNode)j;
    }

    /**
     * Get the node kind
     * @return {@link Type#JNODE}
     */
    public int getNodeKind() {
        return Type.JNODE;
    }

    /**
     * Get the genre of this item (to distinguish the top-level categories of item,
     * such as nodes, atomic values, and functions)
     *
     * @return the genre
     */
    @Override
    public Genre getGenre() {
        return Genre.JNODE;
    }

    /**
     * Get the value of the item as a Unicode string. For nodes, this is the string value of the
     * node as defined in the XPath 2.0 data model, except that all nodes are treated as being
     * untyped: it is not an error to get the string value of a node with a complex type.
     * For atomic values, the method returns the result of casting the atomic value to a string.
     *
     * @return the string value of the item
     * @throws UnsupportedOperationException if the item is a function item (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @since 8.4
     */
    @Override
    public UnicodeString getUnicodeStringValue() {
        try {
            return getContent().getUnicodeStringValue();
        } catch (XPathException e) {
            throw new UncheckedXPathException(e);
        }
    }

    /**
     * Atomize the item.
     *
     * @return the result of atomization
     * @throws XPathException if atomization is not allowed
     *                        for this kind of item
     */
    @Override
    public AtomicSequence atomize() throws XPathException {
        return Atomizer.atomize(getContent());
    }

    /**
     * Returns a string representation of the object.
     */
    @Override
    public String toString() {
        return "jnode(" + getContent().toString() + ")";
    }

    @Override
    public String toShortString() {
        return "jnode(" + getContent().toShortString() + ")";
    }

//    /**
//     * Get an iterator over all the children of this JNode
//     * @return an iterator over the children
//     */
//    public abstract SequenceIterator getChildren();


}

