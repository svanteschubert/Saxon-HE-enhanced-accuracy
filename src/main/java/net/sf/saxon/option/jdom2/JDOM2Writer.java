////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.jdom2;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;
import org.jdom2.*;

import java.util.Stack;

/**
 * JDOMWriter is a Receiver that constructs a JDOM2 document from the stream of events
 */

public class JDOM2Writer extends net.sf.saxon.event.Builder {

    private Document document;
    private Stack<Parent> ancestors = new Stack<>();
    private boolean implicitDocumentNode = false;
    private FastStringBuffer textBuffer = new FastStringBuffer(FastStringBuffer.C256);
    private Stack<NamespaceMap> nsStack = new Stack<>();

    /**
     * Create a JDOM2Writer using the default node factory
     *
     * @param pipe information about the Saxon pipeline
     */

    public JDOM2Writer(PipelineConfiguration pipe) {
        super(pipe);
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
        document = new Document();
        document.setBaseURI(systemId);
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
        Element element;
        if (ancestors.isEmpty()) {
            startDocument(ReceiverOption.NONE);
            implicitDocumentNode = true;
        }
        element = new Element(local, prefix, uri);
        if (ancestors.size() == 1) {
            document.setRootElement(element);
        } else {
            ancestors.peek().addContent(element);
        }
        ancestors.push(element);

        NamespaceMap parentMap = nsStack.peek();
        if (namespaces != parentMap) {
            NamespaceBinding[] declarations = namespaces.getDifferences(parentMap, false);
            for (NamespaceBinding ns : declarations) {
                namespace(element, ns);
            }
        }
        nsStack.push(namespaces);

        for (AttributeInfo att : attributes) {
            NodeName nameCode = att.getNodeName();
            String attlocal = nameCode.getLocalPart();
            String atturi = nameCode.getURI();
            String attprefix = nameCode.getPrefix();
            String value = att.getValue();
            Namespace ns = attprefix.isEmpty() ?
                    Namespace.getNamespace(atturi) :
                    Namespace.getNamespace(attprefix, atturi);
            boolean isXmlId = uri.equals(NamespaceConstant.XML) && attlocal.equals("id");
            if (isXmlId) {
                value = Whitespace.trim(value);
            }
            Attribute attr = new Attribute(attlocal, value, ns);
            if (isXmlId) {
                attr.setAttributeType(AttributeType.ID);
            }
            element.getAttributes().add(attr);
        }
    }

    private void namespace(Element element, NamespaceBinding namespaceBinding) throws XPathException {
        String prefix = namespaceBinding.getPrefix();
        String uri = namespaceBinding.getURI();
        if (uri.isEmpty() && prefix.length() != 0) {
            // ignore XML 1.1 namespace undeclarations because JDOM can't handle them
            return;
        }
        Namespace ns = prefix.isEmpty() ?
                Namespace.getNamespace(uri) :
                Namespace.getNamespace(prefix, uri);
        element.addNamespaceDeclaration(ns);
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
            Text text = new Text(textBuffer.toString());
            ancestors.peek().addContent(text);
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
        ProcessingInstruction pi = new ProcessingInstruction(target, data.toString());
        ancestors.peek().addContent(pi);
    }

    /**
     * Handle a comment.
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        flush();
        Comment comment = new Comment(chars.toString());
        ancestors.peek().addContent(comment);
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
        return new JDOM2DocumentWrapper(document, config).getRootNode();
    }
}


// Original Code is Copyright (c) 2009-2020 Saxonica Limited
