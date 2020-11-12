////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is an implementation of the DOM Attr class that wraps a Saxon NodeInfo
 * representation of an attribute or namespace node.
 */

public class AttrOverNodeInfo extends NodeOverNodeInfo implements Attr {

    /**
     * Get the name of an attribute node (the lexical QName) (DOM method)
     */

    @Override
    public String getName() {
        if (node.getNodeKind() == Type.NAMESPACE) {
            String local = node.getLocalPart();
            if (local.equals("")) {
                return "xmlns";
            } else {
                return "xmlns:" + local;
            }
        }
        return node.getDisplayName();
    }

    /**
     * Return the character value of an attribute node (DOM method)
     *
     * @return the attribute value
     */

    @Override
    public String getValue() {
        return node.getStringValue();
    }

    /**
     * Determine whether the node has any children.
     *
     * @return <code>true</code>: a DOM Attribute has a text node as a child.
     */

    @Override
    public boolean hasChildNodes() {
        return true;
    }

    /**
     * Get first child
     *
     * @return the first child node of this node. In this model an attribute node always has a single text
     *         node as its child.
     */

    @Override
    public Node getFirstChild() {
        return new TextOverAttrInfo(this);
    }

    /**
     * Get last child
     *
     * @return last child of this node, or null if it has no children
     */

    @Override
    public Node getLastChild() {
        return getFirstChild();
    }

    /**
     * Return a <code>NodeList</code> that contains all children of this node. If
     * there are no children, this is a <code>NodeList</code> containing no
     * nodes.
     */

    @Override
    public NodeList getChildNodes() {
        List<Node> list = new ArrayList<>(1);
        list.add(getFirstChild());
        return new DOMNodeList(list);
    }

    /**
     * If this attribute was explicitly given a value in the original
     * document, this is <code>true</code> ; otherwise, it is
     * <code>false</code>. (DOM method)
     *
     * @return Always true in this implementation.
     */

    @Override
    public boolean getSpecified() {
        return true;
    }

    /**
     * Set the value of an attribute node. (DOM method).
     * Always fails (because tree is readonly)
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    @Override
    public void setValue(String value) throws DOMException {
        disallowUpdate();
    }

    /**
     * Determine whether this (attribute) node is an ID. This method is introduced
     * in DOM Level 3.
     */

    @Override
    public boolean isId() {
        return node.isId();
    }


    /**
     * The <code>Element</code> node this attribute is attached to or
     * <code>null</code> if this attribute is not in use.
     *
     * @since DOM Level 2
     */

    @Override
    public Element getOwnerElement() {
        if (node.getNodeKind() == Type.ATTRIBUTE || node.getNodeKind() == Type.NAMESPACE) {
            return (Element) wrap(node.getParent());
        } else {
            throw new UnsupportedOperationException(
                    "This method is defined only on attribute and namespace nodes");
        }
    }

    /**
     * Get the schema type information for this node. Returns null for an untyped node.
     */

    /*@Nullable*/
    @Override
    public TypeInfo getSchemaTypeInfo() {
        SchemaType type = node.getSchemaType();
        if (type == null || BuiltInAtomicType.UNTYPED_ATOMIC.equals(type)) {
            return null;
        }
        return new TypeInfoImpl(node.getConfiguration(), type);
    }

}

