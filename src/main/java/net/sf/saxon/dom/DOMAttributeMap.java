////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.tree.NamespaceNode;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Implementation of DOM NamedNodeMap used to represent the attributes of an element, for use when
 * Saxon element and attribute nodes are accessed using the DOM API.
 * <p>Note that namespaces are treated as attributes.</p>
 */

class DOMAttributeMap implements NamedNodeMap {

    private NodeInfo element;
    private NamespaceBinding[] namespaceDeltas;
    private boolean excludeNamespaceUndeclarations;

    /**
     * Construct an AttributeMap for a given element node
     *
     * @param element the element node owning the attributes
     */

    public DOMAttributeMap(NodeInfo element) {
        this.element = element;
        if (element.getConfiguration().getXMLVersion() == Configuration.XML10) {
            excludeNamespaceUndeclarations = true;
        }
    }

    /**
     * Filter out redundant namespace declarations, and namespace undeclarations
     * (other than the undeclaration of the default namespace)
     */

    private NamespaceBinding[] getNamespaceDeltas() {
        NamespaceMap allNamespaces = element.getAllNamespaces();
        NamespaceBinding[] bindings;
        NodeInfo parent = element.getParent();
        if (parent != null && parent.getNodeKind() == Type.ELEMENT) {
            bindings = allNamespaces.getDifferences(parent.getAllNamespaces(), !excludeNamespaceUndeclarations);
        } else {
            bindings = allNamespaces.getNamespaceBindings();
        }
        return bindings;
    }

    /**
     * Get named attribute (DOM NamedNodeMap method)
     */

    @Override
    public Node getNamedItem(String name) {
        if (name.equals("xmlns")) {
            NamespaceBinding[] nsarray = getNamespaceBindings();
            for (int i = 0; i < nsarray.length; i++) {
                if (nsarray[i] == null) {
                    return null;
                } else if (nsarray[i].getPrefix().isEmpty()) {
                    NamespaceNode nn = new NamespaceNode(element, nsarray[i], i + 1);
                    return NodeOverNodeInfo.wrap(nn);
                }
            }
            return null;
        } else if (name.startsWith("xmlns:")) {
            String prefix = name.substring(6);
            NamespaceBinding[] nsarray = getNamespaceBindings();
            for (int i = 0; i < nsarray.length; i++) {
                if (nsarray[i] == null) {
                    return null;
                } else if (prefix.equals(nsarray[i].getPrefix())) {
                    NamespaceNode nn = new NamespaceNode(element, nsarray[i], i + 1);
                    return NodeOverNodeInfo.wrap(nn);
                }
            }
            return null;
        } else {
            AxisIterator atts = element.iterateAxis(AxisInfo.ATTRIBUTE, att -> att.getDisplayName().equals(name));
            NodeInfo att = atts.next();
            return att == null ? null : NodeOverNodeInfo.wrap(att);
        }
    }

    /**
     * Get n'th attribute (DOM NamedNodeMap method). Numbering is from zero.
     * In this implementation we number the attributes as follows:
     * 0..p namespace declarations
     * p+1..n "real" attribute declarations
     */

    @Override
    public Node item(int index) {
        if (index < 0) {
            return null;
        }
        NamespaceBinding[] namespaces = getNamespaceBindings();

        if (index < namespaces.length) {
            NamespaceBinding ns = namespaces[index];
            NamespaceNode nn = new NamespaceNode(element, ns, index);
            return NodeOverNodeInfo.wrap(nn);
        }
        int pos = 0;
        int attNr = index - namespaces.length;
        AxisIterator atts = element.iterateAxis(AxisInfo.ATTRIBUTE);
        NodeInfo att;
        while ((att = atts.next()) != null) {
            if (pos == attNr) {
                return NodeOverNodeInfo.wrap(att);
            }
            pos++;
        }
        return null;
    }

    /**
     * Get the number of (newly) declared namespaces. This counts the number of namespaces
     * that are declared on this element but are not declared on the parent element. The
     * XML namespace is not included. If the element undeclares the default namespace (xmlns="")
     * this is counted as a declaration. XML 1.1 namespace undeclarations (xmlns:p="") are counted
     * if the configuration supports XML 1.1; in other cases they are ignored.
     *
     * @return the number of namespaces declared on the containing element
     */

    private int getNumberOfNamespaces() {
        return getNamespaceBindings().length;
//        if (numberOfNamespaces == -1) {
//            NamespaceBinding[] nsList = removeUndeclarations(parent.getDeclaredNamespaces(null));
//            int count = nsList.length;
//            for (int i = 0; i < count; i++) {
//                if (nsList[i] == null) {
//                    count = i;
//                    break;
//                }
//            }
//            numberOfNamespaces = count + 1;   //+1 for the XML namespace
//        }
//        return numberOfNamespaces;
    }

    private NamespaceBinding[] getNamespaceBindings() {
        if (namespaceDeltas == null) {
            namespaceDeltas = getNamespaceDeltas();
        }
        return namespaceDeltas;
    }

    /**
     * Get number of attributes and namespaces (DOM NamedNodeMap method).
     */

    @Override
    public int getLength() {
        int length = 0;
        AxisIterator atts = element.iterateAxis(AxisInfo.ATTRIBUTE);
        while (atts.next() != null) {
            length++;
        }
        return getNumberOfNamespaces() + length;
    }

    /**
     * Get named attribute (DOM NamedNodeMap method)
     */

    @Override
    public Node getNamedItemNS(String uri, String localName) {
        if (uri == null) {
            uri = "";
        }
        if (NamespaceConstant.XMLNS.equals(uri)) {
            return getNamedItem("xmlns:" + localName);
        }
        if (uri.equals("") && localName.equals("xmlns")) {
            return getNamedItem("xmlns");
        }
        AxisIterator atts = element.iterateAxis(AxisInfo.ATTRIBUTE);
        while (true) {
            NodeInfo att = atts.next();
            if (att == null) {
                return null;
            }
            if (uri.equals(att.getURI()) && localName.equals(att.getLocalPart())) {
                return NodeOverNodeInfo.wrap(att);
            }
        }
    }

    /**
     * Set named attribute (DOM NamedNodeMap method: always fails)
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    @Override
    public Node setNamedItem(Node arg) throws DOMException {
        NodeOverNodeInfo.disallowUpdate();
        return null;
    }

    /**
     * Remove named attribute (DOM NamedNodeMap method: always fails)
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    @Override
    public Node removeNamedItem(String name) throws DOMException {
        NodeOverNodeInfo.disallowUpdate();
        return null;
    }

    /**
     * Set named attribute (DOM NamedNodeMap method: always fails)
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    @Override
    public Node setNamedItemNS(Node arg) throws DOMException {
        NodeOverNodeInfo.disallowUpdate();
        return null;
    }

    /**
     * Remove named attribute (DOM NamedNodeMap method: always fails)
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    /*@Nullable*/
    @Override
    public Node removeNamedItemNS(String uri, String localName) throws DOMException {
        NodeOverNodeInfo.disallowUpdate();
        return null;
    }

}

