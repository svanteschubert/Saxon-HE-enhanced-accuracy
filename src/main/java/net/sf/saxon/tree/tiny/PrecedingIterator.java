////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;

import java.util.function.IntPredicate;

/**
 * Enumerate all the nodes on the preceding axis from a given start node.
 * The calling code ensures that the start node is not a root, attribute,
 * or namespace node. As well as the standard XPath preceding axis, this
 * class also implements a Saxon-specific "preceding-or-ancestor" axis
 * which returns ancestor nodes as well as preceding nodes. This is used
 * when performing xsl:number level="any".
 */

final class PrecedingIterator implements AxisIterator {

    private TinyTree tree;
    private NodeInfo current;
    private int nextAncestorDepth;
    private boolean includeAncestors;
    private final IntPredicate matcher;
    private NodeInfo pending = null;
    private NodeTest nodeTest;
    private boolean matchesTextNodes;

    public PrecedingIterator(/*@NotNull*/ TinyTree doc, /*@NotNull*/ TinyNodeImpl node,
                             NodeTest nodeTest, boolean includeAncestors) {

        this.includeAncestors = includeAncestors;
        tree = doc;
        current = node;
        nextAncestorDepth = doc.depth[node.nodeNr] - 1;
        this.nodeTest = nodeTest;
        this.matcher = nodeTest.getMatcher(doc);
        matchesTextNodes = nodeTest.getUType().overlaps(UType.TEXT);
    }

    /*@Nullable*/
    @Override
    public NodeInfo next() {
        if (pending != null) {
            current = pending;
            pending = null;
            return current;
        }
        if (current == null) {
            return null;
        }
        if (current instanceof TinyTextualElement.TinyTextualElementText) {
            current = current.getParent();
        }
        int nextNodeNr = ((TinyNodeImpl) current).nodeNr;
        while (true) {
            if (!includeAncestors) {
                nextNodeNr--;
                // skip over ancestor elements
                while (nextAncestorDepth >= 0 && tree.depth[nextNodeNr] == nextAncestorDepth) {
                    if (nextAncestorDepth-- <= 0) {  // bug 1121528
                        current = null;
                        return null;
                    }
                    nextNodeNr--;
                }
            } else {
                if (tree.depth[nextNodeNr] == 0) {
                    current = null;
                    return null;
                } else {
                    nextNodeNr--;
                }
            }
            if (matchesTextNodes && tree.nodeKind[nextNodeNr] == Type.TEXTUAL_ELEMENT) {
                TinyTextualElement element = (TinyTextualElement)tree.getNode(nextNodeNr);
                TinyTextualElement.TinyTextualElementText text = element.getTextNode();
                if (nodeTest.test(text)) {
                    if (nodeTest.test(element)) {
                        pending = element;
                    }
                    return current = text;
                } else if (nodeTest.test(element)) {
                    return current = element;
                }
            } else {
                if (matcher.test(nextNodeNr)) {
                    current = tree.getNode(nextNodeNr);
                    return current;
                }
                if (tree.depth[nextNodeNr] == 0) {
                    current = null;
                    return null;
                }
            }
        }
    }

}

