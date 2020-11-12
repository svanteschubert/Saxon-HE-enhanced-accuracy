////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.event.NamespaceReducer;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import org.w3c.dom.CharacterData;
import org.w3c.dom.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * DOMSender.java: pseudo-SAX driver for a DOM source document.
 * This class takes an existing
 * DOM Document and walks around it in a depth-first traversal,
 * calling a Receiver to process the nodes as it does so
 */

public class DOMSender {
    private final Receiver receiver;
    protected Node root;
    protected String systemId;
    private Stack<NamespaceMap> namespaces = new Stack<>();
    private Node currentNode;

    /**
     * Create a DOMSender that will send events representing the nodes in a tree
     * to a nominated receiver
     *
     * @param startNode the root node of the tree to be send. Usually a document or element node.
     * @param receiver  the object to be notified of the resulting events. The supplied Receiver must
     *                  be initialized with a PipelineConfiguration. The caller is responsible for opening
     *                  and closing the Receiver.
     */

    public DOMSender(Node startNode, Receiver receiver) {
        if (startNode == null) {
            throw new NullPointerException("startNode");
        }
        if (receiver == null) {
            throw new NullPointerException("receiver");
        }
        this.root = startNode;
        this.receiver = new NamespaceReducer(receiver);
        namespaces.push(NamespaceMap.emptyMap());
    }

    /**
     * Set the systemId of the source document (which will also be
     * used for the destination)
     *
     * @param systemId the systemId of the source document
     */

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Walk a tree (traversing the nodes depth first).
     *
     * @throws IllegalStateException if the
     *                               start node is of a node kind other than document, document fragment, element, text,
     *                               comment, or processing instruction (for example, if it is an attribute node).
     * @throws net.sf.saxon.trans.XPathException
     *                               On any error in the document
     */

    public void send() throws XPathException {
        receiver.setSystemId(systemId);

        //receiver.open();
        final Location loc = Loc.NONE;
        switch (root.getNodeType()) {
            case Node.DOCUMENT_NODE:
            case Node.DOCUMENT_FRAGMENT_NODE:
                receiver.startDocument(ReceiverOption.NONE);
                sendUnparsedEntities();
                walkNode(root);
                receiver.endDocument();
                break;
            case Node.ELEMENT_NODE:
                sendElement((Element) root);
                break;
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
                receiver.characters(((CharacterData) root).getData(), loc, ReceiverOption.NONE);
                break;
            case Node.COMMENT_NODE:
                receiver.comment(((Comment) root).getData(), loc, ReceiverOption.NONE);
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                receiver.processingInstruction(
                        ((ProcessingInstruction) root).getTarget(),
                        ((ProcessingInstruction) root).getData(), loc, ReceiverOption.NONE);
                break;
            default:
                throw new IllegalStateException("DOMSender: unsupported kind of start node (" + root.getNodeType() + ")");
        }
        //receiver.close();
    }

    private void sendUnparsedEntities() throws XPathException {
        if (root instanceof Document) {
            DocumentType docType = ((Document)root).getDoctype();
            if (docType != null) {
                NamedNodeMap map = docType.getEntities();
                if (map != null) {
                    for (int i = 0; i < map.getLength(); i++) {
                        Entity e = (Entity) map.item(i);
                        if (e.getNotationName() != null) {
                            // it is an unparsed entity
                            String name = e.getNodeName();
                            String systemId = e.getSystemId();
                            try {
                                URI systemIdURI = new URI(systemId);
                                if (!systemIdURI.isAbsolute()) {
                                    String base = root.getBaseURI();
                                    if (base != null) {
                                        systemId = ResolveURI.makeAbsolute(systemId, base).toString();
                                    } else {
                                        // base URI unknown: return the relative URI as written
                                    }
                                }
                            } catch (URISyntaxException err) {
                                // invalid URI: no action - return the "URI" as written
                            }
                            String publicId = e.getPublicId();
                            receiver.setUnparsedEntity(name, systemId, publicId);
                        }
                    }
                }
            }
        }
    }

    /**
     * Walk a tree starting from a particular element node. This has to make
     * sure that all the namespace declarations in scope for the element are
     * treated as if they were namespace declarations on the element itself.
     *
     * @param startNode the start element node from which the walk will start
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs
     */

    private void sendElement(Element startNode) throws XPathException {
        List<Element> ancestors = new ArrayList<>();
        NamespaceMap inScopeNamespaces = NamespaceMap.emptyMap();
        Node parent = startNode;
        while (parent != null && parent.getNodeType() == Type.ELEMENT) {
            ancestors.add((Element) parent);
            parent = parent.getParentNode();
        }
        for (int i=ancestors.size()-1; i>=0; i--) {
            inScopeNamespaces = inScopeNamespaces.applyDifferences(gatherNamespaces(ancestors.get(i)));
        }

        namespaces.push(inScopeNamespaces);
        outputElement(startNode, true);
        namespaces.pop();
    }

    /**
     * Convert a lexical QName to a NodeName object encapsulating the prefix, local part, and namespace URI
     * @param name the lexical QName
     * @param useDefaultNS true for an element name, false for an attribute name
     * @return the expanded node name
     */

    private NodeName getNodeName(String name, boolean useDefaultNS) {
        int colon = name.indexOf(':');
        if (colon < 0) {
            if (useDefaultNS) {
                String uri = getUriForPrefix("");
                if (!uri.isEmpty()) {
                    return new FingerprintedQName("", uri, name);
                }
            }
            return new NoNamespaceName(name);
        } else {
            String prefix = name.substring(0, colon);
            String uri = getUriForPrefix(prefix);
            if (uri == null) {
                throw new IllegalStateException("Prefix " + prefix + " is not bound to any namespace");
            }
            return new FingerprintedQName(prefix, uri, name.substring(colon+1));
        }
    }

    /**
     * Walk an element of a document (traversing the children depth first)
     *
     * @param node The DOM Element object to walk
     * @throws net.sf.saxon.trans.XPathException
     *          On any error in the document
     */

    private void walkNode(Node node) throws XPathException {
        final Location loc = Loc.NONE;
        if (node.hasChildNodes()) {
            NodeList nit = node.getChildNodes();
            final int len = nit.getLength();
            for (int i = 0; i < len; i++) {
                Node child = nit.item(i);
                currentNode = child;
                switch (child.getNodeType()) {
                    case Node.DOCUMENT_NODE:
                    case Node.DOCUMENT_FRAGMENT_NODE:
                        break;                  // should not happen
                    case Node.ELEMENT_NODE:
                        Element element = (Element) child;
                        NamespaceMap parentNamespaces = namespaces.peek();
                        NamespaceMap childNamespaces = parentNamespaces.applyDifferences(gatherNamespaces(element));
                        namespaces.push(childNamespaces);
                        outputElement(element, !childNamespaces.isEmpty());
                        namespaces.pop();
                        break;
                    case Node.ATTRIBUTE_NODE:        // have already dealt with attributes
                        break;
                    case Node.PROCESSING_INSTRUCTION_NODE:
                        receiver.processingInstruction(
                                ((ProcessingInstruction) child).getTarget(),
                                ((ProcessingInstruction) child).getData(),
                                loc, ReceiverOption.NONE);
                        break;
                    case Node.COMMENT_NODE: {
                        String text = ((Comment) child).getData();
                        if (text != null) {
                            receiver.comment(text, loc, ReceiverOption.NONE);
                        }
                        break;
                    }
                    case Node.TEXT_NODE:
                    case Node.CDATA_SECTION_NODE: {
                        String text = ((CharacterData) child).getData();
                        if (text != null) {
                            receiver.characters(text, loc, ReceiverOption.NONE);
                        }
                        break;
                    }
                    case Node.ENTITY_REFERENCE_NODE:
                        walkNode(child);
                        break;
                    default:
                        break;                  // should not happen
                }
            }
        }

    }

    /**
     * While an event is being processed by the receiver, the DOM Node to which it relates
     * is available as the value of currentNode
     * @return the node currently being processed by the Receiver
     */

    public Node getCurrentNode() {
        return currentNode;
    }

    /**
     * Process an element node. On entry, the namespace bindings have already been added to the stack,
     * and this method removes them from the stack before exit.
     *
     * @param element                  the element to be processed.
     * @param hasNamespaceDeclarations true if the attributes of the element contain namespace declaration
     *                                 attributes (which the method then ignores)
     * @throws XPathException if a dynamic error occurs
     */

    private void outputElement(Element element, boolean hasNamespaceDeclarations) throws XPathException {
        NodeName name = getNodeName(element.getTagName(), true);
        final Location loc = new Loc(systemId, -1, -1);

        AttributeMap attributes = EmptyAttributeMap.getInstance();
        NamedNodeMap atts = element.getAttributes();
        if (atts != null) {
            final int len = atts.getLength();
            for (int a2 = 0; a2 < len; a2++) {
                Attr att = (Attr) atts.item(a2);
                int props = att.isId() ? ReceiverOption.IS_ID : ReceiverOption.NONE;
                String attname = att.getName();
                if (hasNamespaceDeclarations && attname.startsWith("xmlns") && ((attname.length()==5 || attname.charAt(5) == ':'))) {
                    // do nothing: namespace declarations have already been processed
                } else {
                    //System.err.println("Processing attribute " + attname);
                    NodeName attNodeName = getNodeName(attname, false);

                    // Note, DOM gives no guarantee that the prefix and URI are actually consistent. For example,
                    // it's possible programmatically to construct attribute nodes that have a namespace URI but
                    // no prefix. We don't attempt to deal with such situations: garbage in, garbage out.

                    attributes = attributes.put(
                            new AttributeInfo(attNodeName, BuiltInAtomicType.UNTYPED_ATOMIC, att.getValue(), loc, props));
                }
            }
        }
        receiver.startElement(name, Untyped.getInstance(), attributes, namespaces.peek(), loc, ReceiverOption.NONE);
        walkNode(element);
        receiver.endElement();
    }

    /**
     * Get the namespace URI corresponding to a prefix. This is done by searching the stack of namespace
     * declarations maintained by this method (It could be done using DOM level-3 interfaces, but we need
     * to extract the namespace declarations anyway, so we can pass them down the receiver pipeline, so
     * we may as well use our own information to stay in control).
     * @param prefix the prefix: may be "" to represent the default namespace
     * @return the corresponding namespace URI: may be "" to represent the null namespace.
     * @throws IllegalStateException if there is no binding for the prefix (except that there
     * is always a binding for the prefix "")
     */

    private String getUriForPrefix(String prefix) {
        if (prefix.equals("xml")) {
            return NamespaceConstant.XML;
        }
        String uri = namespaces.peek().getURI(prefix);
        return uri==null ? "" : uri;
    }

    /**
     * Collect all the namespace attributes declared locally on a given element.
     *
     * @param element    The element whose namespace declarations are required
     * @return a set of namespace declarations and undeclarations
     */

    private NamespaceDeltaMap gatherNamespaces(Element element) {

        // we can't rely on namespace declaration attributes being present -
        // there may be undeclared namespace prefixes. So we
        // declare all namespaces encountered, to be on the safe side.

        NamespaceDeltaMap result = NamespaceDeltaMap.emptyMap();
        try {
            String prefix = element.getPrefix();
            String uri = element.getNamespaceURI();
            if (prefix == null) {
                prefix = "";
            }
            if (uri == null) {
                uri = "";
            }
            result = result.put(prefix, uri);
        } catch (Throwable err) {
            // it must be a level 1 DOM
        }

        NamedNodeMap atts = element.getAttributes();

        // Apparently the Oracle DOM returns null if there are no attributes:
        if (atts == null) {
            return result;
        }
        int alen = atts.getLength();
        for (int a1 = 0; a1 < alen; a1++) {
            Attr att = (Attr) atts.item(a1);
            String attname = att.getName();
            boolean possibleNamespace = attname.startsWith("xmlns");
            if (possibleNamespace && attname.length() == 5) {
                String uri = att.getValue();
                result = result.put("", uri);
            } else if (possibleNamespace && attname.startsWith("xmlns:")) {
                String prefix = attname.substring(6);
                String uri = att.getValue();
                result = result.put(prefix, uri);
            } else if (attname.indexOf(':') >= 0) {
                try {
                    String prefix = att.getPrefix();
                    String uri = att.getNamespaceURI();
                    result = result.put(prefix, uri);
                } catch (Throwable err) {
                    // it must be a level 1 DOM
                }
            }
        }
        return result;
    }

}

