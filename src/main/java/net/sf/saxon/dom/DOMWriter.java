////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.Stack;


/**
 * DOMWriter is a Receiver that attaches the result tree to a specified Node in the DOM Document
 */

public class DOMWriter extends Builder {

    private PipelineConfiguration pipe;
    private Node currentNode;
    /*@Nullable*/ private Document document;
    private Node nextSibling;
    private int level = 0;
    private boolean canNormalize = true;
    private String systemId;
    private final Stack<NamespaceMap> nsStack = new Stack<>();

    public DOMWriter() {
        nsStack.push(NamespaceMap.emptyMap());
    }

    /**
     * Set the pipelineConfiguration
     */

    @Override
    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        this.pipe = pipe;
        config = pipe.getConfiguration();
    }

    /**
     * Get the pipeline configuration used for this document
     */

    /*@NotNull*/
    @Override
    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    /**
     * Set the System ID of the destination tree
     */

    @Override
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Notify an unparsed entity URI.
     *
     * @param name     The name of the unparsed entity
     * @param systemID The system identifier of the unparsed entity
     * @param publicID The public identifier of the unparsed entity
     */

    @Override
    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
        // no-op
    }

    /**
     * Get the system identifier that was set with setSystemId.
     *
     * @return The system identifier that was set with setSystemId,
     *         or null if setSystemId was not called.
     */
    @Override
    public String getSystemId() {
        return systemId;
    }

    /**
     * Start of the document.
     */

    @Override
    public void open() {
    }

    /**
     * End of the document.
     */

    @Override
    public void close() {
    }

    /**
     * Start of a document node.
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        if (document == null) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                document = factory.newDocumentBuilder().newDocument();
                currentNode = document;
            } catch (ParserConfigurationException err) {
                throw new XPathException(err);
            }
        }
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
    }

    /**
     * Start of an element.
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        String qname = elemName.getDisplayName();
        String uri = elemName.getURI();
        try {
            Element element = document.createElementNS("".equals(uri) ? null : uri, qname);
            if (nextSibling != null && level == 0) {
                currentNode.insertBefore(element, nextSibling);
            } else {
                currentNode.appendChild(element);
            }
            currentNode = element;

            NamespaceMap parentNamespaces = nsStack.peek();
            if (namespaces != parentNamespaces) {
                NamespaceBinding[] declarations = namespaces.getDifferences(parentNamespaces, false);
                for (NamespaceBinding ns : declarations) {
                    String prefix = ns.getPrefix();
                    String nsuri = ns.getURI();
                    if (!nsuri.equals(NamespaceConstant.XML)) {
                        if (prefix.isEmpty()) {
                            element.setAttributeNS(NamespaceConstant.XMLNS, "xmlns", nsuri);
                        } else {
                            element.setAttributeNS(NamespaceConstant.XMLNS, "xmlns:" + prefix, nsuri);

                        }
                    }
                }
            }
            nsStack.push(namespaces);

            for (AttributeInfo att : attributes) {
                NodeName attName = att.getNodeName();
                String atturi = attName.getURI();
                element.setAttributeNS("".equals(atturi) ? null : atturi, attName.getDisplayName(), att.getValue());
                if (attName.equals(StandardNames.XML_ID_NAME) ||
                        ReceiverOption.contains(properties, ReceiverOption.IS_ID) ||
                        attName.hasURI(NamespaceConstant.XML) && attName.getLocalPart().equals("id")) {
                    String localName = attName.getLocalPart();
                    element.setIdAttributeNS("".equals(atturi) ? null : atturi, localName, true);
                }
            }

        } catch (DOMException err) {
            throw new XPathException(err);
        }
        level++;
    }

    /**
     * End of an element.
     */

    @Override
    public void endElement() throws XPathException {
        nsStack.pop();
        if (canNormalize) {
            try {
                currentNode.normalize();
            } catch (Throwable err) {
                canNormalize = false;
            }      // in case it's a Level 1 DOM
        }

        currentNode = currentNode.getParentNode();
        level--;
    }


    /**
     * Character data.
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (level == 0 && nextSibling == null && Whitespace.isWhite(chars)) {
            return; // no action for top-level whitespace
        }
        try {
            Text text = document.createTextNode(chars.toString());
            if (nextSibling != null && level == 0) {
                currentNode.insertBefore(text, nextSibling);
            } else {
                currentNode.appendChild(text);
            }
        } catch (DOMException err) {
            throw new XPathException(err);
        }
    }


    /**
     * Handle a processing instruction.
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties)
            throws XPathException {
        try {
            ProcessingInstruction pi =
                    document.createProcessingInstruction(target, data.toString());
            if (nextSibling != null && level == 0) {
                currentNode.insertBefore(pi, nextSibling);
            } else {
                currentNode.appendChild(pi);
            }
        } catch (DOMException err) {
            throw new XPathException(err);
        }
    }

    /**
     * Handle a comment.
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        try {
            Comment comment = document.createComment(chars.toString());
            if (nextSibling != null && level == 0) {
                currentNode.insertBefore(comment, nextSibling);
            } else {
                currentNode.appendChild(comment);
            }
        } catch (DOMException err) {
            throw new XPathException(err);
        }
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    @Override
    public boolean usesTypeAnnotations() {
        return false;
    }

    /**
     * Set the attachment point for the new subtree
     *
     * @param node the node to which the new subtree will be attached
     */

    public void setNode(Node node) {
        if (node == null) {
            return;
        }
        currentNode = node;
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            document = (Document) node;
        } else {
            document = currentNode.getOwnerDocument();
            if (document == null) {
                // which might be because currentNode() is a parentless ElementOverNodeInfo.
                // we create a DocumentOverNodeInfo, which is immutable, and will cause the DOMWriter to fail
                document = new DocumentOverNodeInfo();
            }
            nsStack.clear();
            nsStack.push(getAllNamespaces(node));
        }
    }

    /**
     * Set next sibling
     *
     * @param nextSibling the node, which must be a child of the attachment point, before which the new subtree
     *                    will be created. If this is null the new subtree will be added after any existing children of the
     *                    attachment point.
     */

    public void setNextSibling(Node nextSibling) {
        this.nextSibling = nextSibling;
    }

    /**
     * Get the current root node. This will normally be a document node, but if the root of the tree
     * is an element node, it can be an element.
     *
     * @return the root of the tree that is currently being built, or that has been most recently built
     *         using this builder
     */

    @Override
    public NodeInfo getCurrentRoot() {
        return new DocumentWrapper(document, systemId, config).getRootNode();
    }

    /**
     * Get the constructed DOM Document node
     * @return the DOM Document node
     */

    protected Document getDOMDocumentNode() {
        return document;
    }

    /**
     * Get all in-scope namespaces for the node to which we are attaching a new subtree
     * @param anchor the (document or element) node to which we are attaching
     * @return the in-scope namespaces of a supplied DOM node
     */

    private NamespaceMap getAllNamespaces(Node anchor) {
        if (anchor.getNodeType() == Type.ELEMENT) {
            NamespaceMap nsMap = NamespaceMap.emptyMap();
            Element elem = (Element) anchor;
            while (true) {
                NamedNodeMap atts = elem.getAttributes();
                if (atts != null) {
                    int attsLen = atts.getLength();
                    for (int i = 0; i < attsLen; i++) {
                        Attr att = (Attr) atts.item(i);
                        String attName = att.getName();
                        if (attName.startsWith("xmlns")) {
                            if (attName.length() == 5) {
                                if (nsMap.getURI("") == null) {
                                    nsMap = nsMap.bind("", att.getValue());
                                }
                            } else if (attName.charAt(5) == ':') {
                                String prefix = attName.substring(6);
                                if (nsMap.getURI(prefix) == null) {
                                    nsMap = nsMap.bind(attName.substring(6), att.getValue());
                                }
                            }
                        }
                    }
                }
                Node parent = elem.getParentNode();
                if (parent == null || parent.getNodeType() != Type.ELEMENT) {
                    return nsMap;
                }
                elem = (Element)parent;
            }
        } else {
            // not an element node
            return NamespaceMap.emptyMap();
        }
    }
}

