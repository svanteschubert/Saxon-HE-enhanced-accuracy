////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.xom;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import nu.xom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * XOMWriter is a Receiver that constructs a XOM document from the stream of events
 */

public class XOMWriter extends net.sf.saxon.event.Builder {

    private Document document;
    private Stack<ParentNode> ancestors = new Stack<>();
    private Stack<NamespaceMap> nsStack = new Stack<>();
    private NodeFactory nodeFactory;
    private boolean implicitDocumentNode = false;
    private FastStringBuffer textBuffer = new FastStringBuffer(FastStringBuffer.C64);

    /**
     * Create a XOMWriter using the default node factory
     *
     * @param pipe the pipeline configuration
     */

    public XOMWriter(/*@NotNull*/ PipelineConfiguration pipe) {
        super(pipe);
        this.nodeFactory = new NodeFactory();
        this.nsStack.push(NamespaceMap.emptyMap());
    }

    /**
     * Create a XOMWriter
     *
     * @param pipe    the pipeline configuration
     * @param factory the XOM NodeFactory to be used
     */

    public XOMWriter(/*@NotNull*/ PipelineConfiguration pipe, /*@NotNull*/ NodeFactory factory) {
        super(pipe);
        this.nodeFactory = factory;
        this.nsStack.push(NamespaceMap.emptyMap());
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
     * @param properties
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        document = nodeFactory.startMakingDocument();
        try {
            document.setBaseURI(systemId);
        } catch (MalformedURIException e) {
            // XOM objects if the URI is invalid
            throw new XPathException(e);
        }
        ancestors.push(document);
        textBuffer.setLength(0);
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        nodeFactory.finishMakingDocument(document);
        ancestors.pop();
    }

    /**
     * Start of an element.
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        flush();
        String qname = elemName.getDisplayName();
        String uri = elemName.getURI();
        Element element;
        if (ancestors.isEmpty()) {
            startDocument(ReceiverOption.NONE);
            implicitDocumentNode = true;
        }
        if (ancestors.size() == 1) {
            element = nodeFactory.makeRootElement(qname, uri);
            document.setRootElement(element);
            // At this point, any other children of the document node must be reinserted before the root element
            int c = document.getChildCount();
            if (c > 1) {
                List<Node> otherChildren = new ArrayList<Node>(c);
                for (int i=1; i<c; i++) {
                    Node n = document.removeChild(1);
                    otherChildren.add(n);
                }
                for (int i=0; i<otherChildren.size(); i++) {
                    document.insertChild(otherChildren.get(i), i);
                }
            }
        } else {
            element = nodeFactory.startMakingElement(qname, uri);
        }
        if (element == null) {
            throw new XPathException("XOM node factory returned null");
        }
        ancestors.push(element);

        NamespaceMap parentNamespaces = nsStack.peek();
        if (namespaces != parentNamespaces) {
            NamespaceBinding[] declarations = namespaces.getDifferences(parentNamespaces, false);
            for (NamespaceBinding ns : declarations) {
                String nsprefix = ns.getPrefix();
                String nsuri = ns.getURI();
                try {
                    element.addNamespaceDeclaration(nsprefix, nsuri);
                } catch (MalformedURIException e) {
                    throw new XPathException("XOM requires namespace names to be legal URIs: " + uri);
                }
            }
        }
        nsStack.push(namespaces);

        for (AttributeInfo att : attributes) {
            String attqname = att.getNodeName().getDisplayName();
            String atturi = att.getNodeName().getURI();
            Nodes nodes = null;
            try {
                nodes = nodeFactory.makeAttribute(attqname, atturi, att.getValue(), Attribute.Type.CDATA);
            } catch (nu.xom.IllegalNameException e) {
                // e.g. invalid value for xml:id attribute, QT3 test fn-doc-31
                throw new XPathException(e.getMessage());
            }
            for (int n = 0; n < nodes.size(); n++) {
                Node node = nodes.get(n);
                if (node instanceof Attribute) {
                    element.addAttribute((Attribute) node);
                } else {
                    element.appendChild(node);
                }
            }
        }
    }

    /**
     * End of an element.
     */

    @Override
    public void endElement() throws XPathException {
        flush();
        nsStack.pop();
        Element element = (Element) ancestors.pop();
        Node parent = ancestors.peek();
        Nodes nodes = nodeFactory.finishMakingElement(element);
        if (parent == document) {
            if (implicitDocumentNode) {
                endDocument();
            }
        } else {
            for (int n = 0; n < nodes.size(); n++) {
                Node node = nodes.get(n);
                if (node instanceof Attribute) {
                    ((Element) parent).addAttribute((Attribute) node);
                } else {
                    ((Element) parent).appendChild(node);
                }
            }
        }
    }

    /**
     * Character data.
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        textBuffer.cat(chars);
    }

    private void flush() {
        if (textBuffer.length() != 0) {
            Nodes nodes = nodeFactory.makeText(textBuffer.toString());
            for (int n = 0; n < nodes.size(); n++) {
                Node node = nodes.get(n);
                if (node instanceof Attribute) {
                    ((Element) ancestors.peek()).addAttribute((Attribute) node);
                } else {
                    ancestors.peek().appendChild(node);
                }
            }
            textBuffer.setLength(0);
        }
    }


    /**
     * Handle a processing instruction.
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties)
            throws XPathException {
        flush();
        Nodes nodes = nodeFactory.makeProcessingInstruction(target, data.toString());
        for (int n = 0; n < nodes.size(); n++) {
            Node node = nodes.get(n);
            if (node instanceof Attribute) {
                ((Element) ancestors.peek()).addAttribute((Attribute) node);
            } else {
                ancestors.peek().appendChild(node);
            }
        }
    }

    /**
     * Handle a comment.
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        flush();
        Nodes nodes = nodeFactory.makeComment(chars.toString());
        for (int n = 0; n < nodes.size(); n++) {
            Node node = nodes.get(n);
            if (node instanceof Attribute) {
                ((Element) ancestors.peek()).addAttribute((Attribute) node);
            } else {
                ancestors.peek().appendChild(node);
            }
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
     * Get the constructed document node
     *
     * @return the document node of the constructed XOM tree
     */

    public Document getDocument() {
        return document;
    }

    /**
     * Get the current root node.
     *
     * @return a Saxon wrapper around the constructed XOM document node
     */

    @Override
    public NodeInfo getCurrentRoot() {
        return new XOMDocumentWrapper(document, config);
    }
}

