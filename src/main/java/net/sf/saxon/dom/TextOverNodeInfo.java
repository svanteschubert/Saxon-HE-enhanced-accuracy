////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.type.ComplexType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMException;
import org.w3c.dom.Text;

/**
 * This class is an implementation of the DOM Text and Comment interfaces that wraps a Saxon NodeInfo
 * representation of a text or comment node.
 */

public class TextOverNodeInfo extends NodeOverNodeInfo implements Text, Comment {


    /**
     * Get the character data of a Text or Comment node.
     * DOM method.
     */

    @Override
    public String getData() {
        return node.getStringValue();
    }

    /**
     * Set the character data of a Text or Comment node.
     * DOM method: always fails, Saxon tree is immutable.
     */

    @Override
    public void setData(String data) throws DOMException {
        disallowUpdate();
    }

    /**
     * Get the length of a Text or Comment node.
     * DOM method.
     */

    @Override
    public int getLength() {
        return node.getStringValue().length();
    }

    /**
     * Extract a range of data from a Text or Comment node. DOM method.
     *
     * @param offset Start offset of substring to extract.
     * @param count  The number of 16-bit units to extract.
     * @return The specified substring. If the sum of <code>offset</code> and
     *         <code>count</code> exceeds the <code>length</code> , then all 16-bit
     *         units to the end of the data are returned.
     * @throws org.w3c.dom.DOMException INDEX_SIZE_ERR: Raised if the specified <code>offset</code> is
     *                                  negative or greater than the number of 16-bit units in
     *                                  <code>data</code> , or if the specified <code>count</code> is
     *                                  negative.
     */

    @Override
    public String substringData(int offset, int count) throws DOMException {
        try {
            return node.getStringValue().substring(offset, offset + count);
        } catch (IndexOutOfBoundsException err2) {
            throw new DOMExceptionImpl(DOMException.INDEX_SIZE_ERR,
                    "substringData: index out of bounds");
        }
    }

    /**
     * Append the string to the end of the character data of the node.
     * DOM method: always fails.
     *
     * @param arg The <code>DOMString</code> to append.
     * @throws org.w3c.dom.DOMException NO_MODIFICATION_ALLOWED_ERR: Raised if this node is readonly.
     */

    @Override
    public void appendData(String arg) throws DOMException {
        disallowUpdate();
    }

    /**
     * Insert a string at the specified character offset.
     * DOM method: always fails.
     *
     * @param offset The character offset at which to insert.
     * @param arg    The <code>DOMString</code> to insert.
     * @throws UnsupportedOperationException always
     */

    @Override
    public void insertData(int offset, String arg) throws DOMException {
        disallowUpdate();
    }

    /**
     * Remove a range of 16-bit units from the node.
     * DOM method: always fails.
     *
     * @param offset The offset from which to start removing.
     * @param count  The number of 16-bit units to delete.
     * @throws UnsupportedOperationException always
     */

    @Override
    public void deleteData(int offset, int count) throws DOMException {
        disallowUpdate();
    }

    /**
     * Replace the characters starting at the specified 16-bit unit offset
     * with the specified string. DOM method: always fails.
     *
     * @param offset The offset from which to start replacing.
     * @param count  The number of 16-bit units to replace.
     * @param arg    The <code>DOMString</code> with which the range must be
     *               replaced.
     * @throws org.w3c.dom.DOMException NO_MODIFICATION_ALLOWED_ERR: Raised if this node is readonly.
     */

    @Override
    public void replaceData(int offset,
                            int count,
                            String arg) throws DOMException {
        disallowUpdate();
    }


    /**
     * Break this node into two nodes at the specified offset,
     * keeping both in the tree as siblings. DOM method, always fails.
     *
     * @param offset The 16-bit unit offset at which to split, starting from 0.
     * @return The new node, of the same type as this node.
     * @throws org.w3c.dom.DOMException
     */

    @Override
    public Text splitText(int offset) throws DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Replaces the text of the current node and all logically-adjacent text
     * nodes with the specified text. All logically-adjacent text nodes are
     * removed including the current node unless it was the recipient of the
     * replacement text.
     * <br>This method returns the node which received the replacement text.
     * The returned node is:
     * <ul>
     * <li><code>null</code>, when the replacement text is
     * the empty string;
     * </li>
     * <li>the current node, except when the current node is
     * read-only;
     * </li>
     * <li> a new <code>Text</code> node of the same type (
     * <code>Text</code> or <code>CDATASection</code>) as the current node
     * inserted at the location of the replacement.
     * </li>
     * </ul>
     * <br>For instance, in the above example calling
     * <code>replaceWholeText</code> on the <code>Text</code> node that
     * contains "bar" with "yo" in argument results in the following:
     * <br>Where the nodes to be removed are read-only descendants of an
     * <code>EntityReference</code>, the <code>EntityReference</code> must
     * be removed instead of the read-only nodes. If any
     * <code>EntityReference</code> to be removed has descendants that are
     * not <code>EntityReference</code>, <code>Text</code>, or
     * <code>CDATASection</code> nodes, the <code>replaceWholeText</code>
     * method must fail before performing any modification of the document,
     * raising a <code>DOMException</code> with the code
     * <code>NO_MODIFICATION_ALLOWED_ERR</code>.
     * <br>For instance, in the example below calling
     * <code>replaceWholeText</code> on the <code>Text</code> node that
     * contains "bar" fails, because the <code>EntityReference</code> node
     * "ent" contains an <code>Element</code> node which cannot be removed.
     *
     * @param content The content of the replacing <code>Text</code> node.
     * @return The <code>Text</code> node created with the specified content.
     * @throws org.w3c.dom.DOMException NO_MODIFICATION_ALLOWED_ERR: Raised if one of the <code>Text</code>
     *                                  nodes being replaced is readonly.
     * @since DOM Level 3
     */
    /*@Nullable*/
    @Override
    public Text replaceWholeText(String content) throws DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Returns whether this text node contains <a href='http://www.w3.org/TR/2004/REC-xml-infoset-20040204#infoitem.character'>
     * element content whitespace</a>, often abusively called "ignorable whitespace". The text node is
     * determined to contain whitespace in element content during the load
     * of the document or if validation occurs while using
     * <code>Document.normalizeDocument()</code>.
     *
     * @since DOM Level 3
     */
    @Override
    public boolean isElementContentWhitespace() {
        if (node.getNodeKind() != Type.TEXT) {
            throw new UnsupportedOperationException("Method is defined only on text nodes");
        }
        if (!Whitespace.isWhite(node.getStringValue())) {
            return false;
        }
        NodeInfo parent = node.getParent();
        if (parent == null) {
            return false;
        }
        SchemaType type = parent.getSchemaType();
        return type.isComplexType() && !((ComplexType) type).isMixedContent();
    }

    /**
     * Returns all text of <code>Text</code> nodes logically-adjacent text
     * nodes to this node, concatenated in document order.
     * <br>For instance, in the example below <code>wholeText</code> on the
     * <code>Text</code> node that contains "bar" returns "barfoo", while on
     * the <code>Text</code> node that contains "foo" it returns "barfoo".
     *
     * @since DOM Level 3
     */
    @Override
    public String getWholeText() {
        if (node.getNodeKind() != Type.TEXT) {
            throw new UnsupportedOperationException("Method is defined only on text nodes");
        }
        return node.getStringValue();
    }


}

