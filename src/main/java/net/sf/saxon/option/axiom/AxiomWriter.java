////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.axiom;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import org.apache.axiom.om.*;

import java.util.Stack;

/**
 * JDOMWriter is a Receiver that constructs a JDOM document from the stream of events
 */

public class AxiomWriter extends net.sf.saxon.event.Builder {

    private OMFactory factory;
    private OMDocument document;
    private Stack<OMContainer> ancestors = new Stack<>();
    private Stack<NamespaceMap> nsStack = new Stack<>();
    private boolean implicitDocumentNode = false;
    private FastStringBuffer textBuffer = new FastStringBuffer(FastStringBuffer.C256);

    /**
     * Create an AxiomWriter using the default node factory
     *
     * @param pipe information about the Saxon pipeline
     */

    public AxiomWriter(PipelineConfiguration pipe) {
        super(pipe);
        factory = OMAbstractFactory.getOMFactory();
        nsStack.push(NamespaceMap.emptyMap());
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
        document = factory.createOMDocument();
        ancestors.push(document);
        textBuffer.setLength(0);
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
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
        String local = elemName.getLocalPart();
        String uri = elemName.getURI();
        String prefix = elemName.getPrefix();
        if (ancestors.isEmpty()) {
            startDocument(ReceiverOption.NONE);
            implicitDocumentNode = true;
        }
        OMElement element;
        if (uri.length() != 0) {
            OMNamespace ns = factory.createOMNamespace(uri, prefix);
            element = factory.createOMElement(local, ns);
        } else {
            element = factory.createOMElement(local, null);
        }
        //OMElement element = factory.createOMElement(new QName(uri, local, prefix));
        if (ancestors.size() == 1) {
            document.setOMDocumentElement(element);
        } else {
            ancestors.peek().addChild(element);
        }
        ancestors.push(element);

        NamespaceMap parentNamespaces = nsStack.peek();
        if (namespaces != parentNamespaces) {
            NamespaceBinding[] declarations = namespaces.getDifferences(parentNamespaces, false);
            for (NamespaceBinding ns : declarations) {
                String nsprefix = ns.getPrefix();
                String nsuri = ns.getURI();
                if (nsprefix.equals("")) {
                    element.declareDefaultNamespace(nsuri);
                } else if (nsuri.equals("")) {
                    // ignore namespace undeclarations - Axiom can't handle them
                } else {
                    OMNamespace ons = factory.createOMNamespace(nsuri, nsprefix);
                    element.declareNamespace(ons);
                }
            }
        }
        nsStack.push(namespaces);

        for (AttributeInfo att : attributes) {
            NodeName nameCode = att.getNodeName();
            String attlocal = nameCode.getLocalPart();
            String atturi = nameCode.getURI();
            String attprefix = nameCode.getPrefix();
            OMNamespace ns = atturi.isEmpty() ? null : factory.createOMNamespace(atturi, attprefix);
            OMAttribute attr = factory.createOMAttribute(attlocal, ns, att.getValue());
            if (ReceiverOption.contains(properties, ReceiverOption.IS_ID) || (attlocal.equals("id") && atturi.equals(NamespaceConstant.XML))) {
                attr.setAttributeType("ID");
            } else if (ReceiverOption.contains(properties, ReceiverOption.IS_IDREF)) {
                attr.setAttributeType("IDREF");
            }
            element.addAttribute(attr);
        }
    }

    /**
     * End of an element.
     */

    @Override
    public void endElement() throws XPathException {
        flush();
        ancestors.pop();
        nsStack.pop();
        Object parent = ancestors.peek();
        if (parent == document && implicitDocumentNode) {
            endDocument();
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
            OMText text = factory.createOMText(textBuffer.toString());
            ancestors.peek().addChild(text);
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
        OMContainer parent = ancestors.peek();
        OMProcessingInstruction pi = factory.createOMProcessingInstruction(parent, target, data.toString());
        //parent.addChild(pi);
    }

    /**
     * Handle a comment.
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        flush();
        OMContainer parent = ancestors.peek();
        OMComment comment = factory.createOMComment(parent, chars.toString());
        //parent.addChild(comment);
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

    public OMDocument getDocument() {
        return document;
    }

    /**
     * Get the current root node.
     *
     * @return a Saxon wrapper around the constructed XOM document node
     */

    /*@Nullable*/
    @Override
    public NodeInfo getCurrentRoot() {
        return new AxiomDocumentNodeWrapper(document, systemId, config);
    }
}

