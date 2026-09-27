////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.type.Type;

/**
 * TinyParentNodeImpl is an implementation of a non-leaf node (specifically, an Element node
 * or a Document node)
 *
 */


public abstract class TinyParentNodeImpl extends TinyNodeImpl {

    protected TinyParentNodeImpl(TinyTree tree, int nodeNr) {
        super(tree, nodeNr);
    }

    /**
     * Determine if the node has children.
     */

    @Override
    public boolean hasChildNodes() {
        return nodeNr + 1 < tree.numberOfNodes &&
                tree.depth[nodeNr + 1] > tree.depth[nodeNr];
    }

    /**
     * Return the string-value of the node, that is, the concatenation
     * of the character content of all descendent elements and text nodes.
     *
     * @return the accumulated character content of the element, including descendant elements.
     */

    @Override
    public UnicodeString getUnicodeStringValue() {
        return getStringValue(tree, nodeNr);
    }

    /**
     * Get the string value of a node. This static method allows the string value of a node
     * to be obtained without instantiating the node as a Java object. The method also returns
     * a CharSequence rather than a string, which means it can sometimes avoid copying the
     * data.
     *
     * @param tree   The containing document
     * @param nodeNr identifies the node whose string value is required. This must be a
     *               document or element node. The caller is trusted to ensure this.
     * @return the string value of the node, as a CharSequence
     */

    public static UnicodeString getStringValue(/*@NotNull*/ TinyTree tree, int nodeNr) {
        int level = tree.depth[nodeNr];

        // note, we can't rely on the value being contiguously stored because of whitespace
        // nodes: the data for these may still be present.

        int next = nodeNr + 1;

        // we optimize two special cases: firstly, where the node has no children, and secondly,
        // where it has a single text node as a child.

        if (tree.nodeKind[nodeNr] == Type.TEXTUAL_ELEMENT) {
            return TinyTextImpl.getStringValue(tree, nodeNr);
        } else if (next < tree.numberOfNodes) {    // bug 4445
            if (tree.depth[next] <= level) {
                return EmptyUnicodeString.getInstance();
            } else if (tree.nodeKind[next] == Type.TEXT && (next + 1 >= tree.numberOfNodes || tree.depth[next + 1] <= level)) {
                return TinyTextImpl.getStringValue(tree, next);
            }
        }

        // now handle the general case

        UnicodeBuilder sb = null;
        while (next < tree.numberOfNodes && tree.depth[next] > level) {
            final byte kind = tree.nodeKind[next];
            if (kind == Type.TEXT || kind == Type.TEXTUAL_ELEMENT) {
                if (sb == null) {
                    sb = new UnicodeBuilder();
                }
                sb.accept(TinyTextImpl.getStringValue(tree, next));
            } else if (kind == Type.WHITESPACE_TEXT) {
                if (sb == null) {
                    sb = new UnicodeBuilder();
                }
                WhitespaceTextImpl.appendStringValue(tree, next, sb);
            }
            next++;
        }
        if (sb == null) {
            return EmptyUnicodeString.getInstance();
        }
        return sb.toUnicodeString();
    }

}

