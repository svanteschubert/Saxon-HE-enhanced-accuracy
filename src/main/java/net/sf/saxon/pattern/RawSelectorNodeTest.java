////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

/**
 * A RawSelectorNodeTest represents a nodetest written using the syntax get(Expression)
 *
 * <p>TEMP: only a single literal is supported</p>
 */

public class RawSelectorNodeTest /*extends NodeTest*/ {

//    private final AtomicMatchKey key;
//    private final int defaultNodeKind;
//
//    public RawSelectorNodeTest(Expression expression, int defaultNodeKind) {
//        this.defaultNodeKind = defaultNodeKind;
//        if (!(expression instanceof Literal)) {
//            // Temporary restriction
//            throw new UnsupportedOperationException();
//        }
//        AtomicValue value = (AtomicValue) ((Literal)expression).getGroundedValue();
//        key = value.asMapKey();
//    }
//
//    /**
//     * Convert this RawNodeTest to an executable node test using type information
//     * about the origin node of the axis step.
//     *
//     * @param origin the type of the origin node
//     * @return a refined NodeTest.
//     */
//    @Override
//    public NodeTest cook(ItemType origin) {
//        if (origin instanceof JNodeType) {
//            Set<AtomicMatchKey> keys = new HashSet<>();
//            keys.add(key);
//            return new JNodeSelectorTest(keys);
//        }
//        if (origin instanceof XNodeTest) {
//            throw new UnsupportedOperationException();
//        }
//        return this;
//    }
//
//    /**
//     * Convert this RawNodeTest to an executable node test knowing the
//     * actual origin node. This method can take advantage of the actual
//     * tree model used, e.g. if the origin is a node in a TinyTree.
//     *
//     * @param origin the origin node
//     * @return a refined NodeTest.
//     */
//    @Override
//    public NodePredicate cook(GNode origin) {
//        if (origin instanceof JNode) {
//            return node -> ((JNode)origin).getSelector().asMapKey().equals(key) ;
//        }
//        if (origin instanceof NodeInfo) {
//            switch (defaultNodeKind) {
//                case Type.ATTRIBUTE:
//                    return NodeKindType.ATTRIBUTE;
//                case Type.NAMESPACE:
//                    return NodeKindType.NAMESPACE;
//                default:
//                    return NodeKindType.ELEMENT;
//            }
//        }
//        return this;
//    }
//
//    @Override
//    public String toString() {
//        return "get(" + key + ")";
//    }
}

