////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.exslt;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.sort.GlobalOrderComparer;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;

/**
 * This class implements extension functions in the
 * http://exslt.org/sets namespace. <p>
 */

public abstract class Sets {

    private Sets() {
    }

    /**
     * Return the intersection of two node-sets (interpreted as sequences of nodes that must be supplied
     * in document order)
     *
     * @param p1 The first node-set
     * @param p2 The second node-set
     * @return A node-set containing all nodes that are in both p1 and p2
     */

    public static SequenceIterator intersection(SequenceIterator p1, SequenceIterator p2) throws XPathException {
        return new IntersectionEnumeration(p1, p2, GlobalOrderComparer.getInstance());
    }

    /**
     * Return the difference of two node-sets (interpreted as sequences of nodes that must be supplied
     * in document order)
     *
     * @param p1 The first node-set
     * @param p2 The second node-set
     * @return A node-set containing all nodes that are in p1 and not in p2
     */

    public static SequenceIterator difference(SequenceIterator p1, SequenceIterator p2) throws XPathException {
        return new DifferenceEnumeration(p1, p2, GlobalOrderComparer.getInstance());
    }

    /**
     * Determine whether two node-sets (interpreted as sequences of nodes that must be supplied
     * in document order) contain at least one node in common
     *
     * @param p1 The first node-set
     * @param p2 The second node-set
     * @return true if p1 and p2 contain at least one node in common (i.e. if the intersection
     *         is not empty)
     */

    public static boolean hasSameNode(SequenceIterator p1, SequenceIterator p2) throws XPathException {
        SequenceIterator intersection =
                new IntersectionEnumeration(p1, p2, GlobalOrderComparer.getInstance());
        return intersection.next() != null;
    }

    /**
     * Find all the nodes in ns1 that are before the first node in ns2.
     * Return empty set if ns2 is empty. For this function, Saxon does not assume that the two sequences
     * of nodes are supplied in document order.
     *
     * @param ns1 The first node-set
     * @param ns2 The second node-set
     * @return the nodes in the node set passed as the first argument that precede,
     *         in document order, the first node in the node set passed as the second argument.
     */

    public static SequenceIterator leading(
            XPathContext context,
            SequenceIterator ns1, SequenceIterator ns2) throws XPathException {

        NodeInfo first = null;

        // Find the first node in ns2 (in document order)

        GlobalOrderComparer comparer = GlobalOrderComparer.getInstance();
        while (true) {
            Item item = ns2.next();
            if (item == null) {
                if (first == null) {
                    return ns1;
                }
                break;
            }
            if (item instanceof NodeInfo) {
                NodeInfo node = (NodeInfo) item;
                if (first == null) {
                    first = node;
                } else {
                    if (comparer.compare(node, first) < 0) {
                        first = node;
                    }
                }
            } else {
                XPathException e = new XPathException("Operand of leading() contains an item that is not a node");
                e.setXPathContext(context);
                throw e;
            }
        }

        // Filter ns1 to select nodes that come before this one

        Expression filter = new IdentityComparison(
                new ContextItemExpression(),
                Token.PRECEDES,
                Literal.makeLiteral(new ZeroOrOne<>(first)));

        return new FilterIterator(ns1, filter, context);

    }

    /**
     * Find all the nodes in ns1 that are after the first node in ns2.
     * Return ns1 if ns2 is empty,
     *
     * @param ns1 The first node-set
     * @param ns2 The second node-set
     * @return the nodes in the node set passed as the first argument that follow,
     *         in document order, the first node in the node set passed as the second argument.
     */

    /*@Nullable*/
    public static SequenceIterator trailing(
            XPathContext context,
            SequenceIterator ns1, SequenceIterator ns2) throws XPathException {

        NodeInfo first = null;

        // Find the first node in ns2 (in document order)

        GlobalOrderComparer comparer = GlobalOrderComparer.getInstance();
        while (true) {
            Item item = ns2.next();
            if (item == null) {
                if (first == null) {
                    return ns1;
                } else {
                    break;
                }
            }
            if (item instanceof NodeInfo) {
                NodeInfo node = (NodeInfo) item;
                if (first == null) {
                    first = node;
                } else {
                    if (comparer.compare(node, first) < 0) {
                        first = node;
                    }
                }
            } else {
                XPathException e = new XPathException("Operand of trailing() contains an item that is not a node");
                e.setXPathContext(context);
                throw e;
            }
        }

        // Filter ns1 to select nodes that come after this one

        Expression filter = new IdentityComparison(
                new ContextItemExpression(),
                Token.FOLLOWS,
                Literal.makeLiteral(new ZeroOrOne<>(first)));

        return new FilterIterator(ns1, filter, context);

    }

}

