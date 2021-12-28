////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.dom4j;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;
import org.dom4j.*;
import org.dom4j.tree.*;

import java.util.HashMap;
import java.util.Stack;

/**
 * JDOMWriter is a Receiver that constructs a DOM4J document from the stream of events
 */

public class DOM4JWriter extends net.sf.saxon.event.Builder {

    private Document document;
    private Stack<Branch> ancestors = new Stack<>();
    private Stack<NamespaceMap> nsStack = new Stack<>();
    private boolean implicitDocumentNode = false;
    private FastStringBuffer textBuffer = new FastStringBuffer(FastStringBuffer.C256);
    private HashMap<String, Element> idIndex = new HashMap<>();

    /**
     * Create a JDOMWriter using the default node factory
     * @param pipe the pipeline configuration
     */

    public DOM4JWriter(PipelineConfiguration pipe) {
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
        document = new DefaultDocument();
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
        QName name = new QName(local, new Namespace(prefix, uri));
        if (ancestors.size() == 1) {
            element = new DefaultElement(name);
            // document.setRootElement(element) wipes out any existing children of the document node, e.g. PIs and comments
            //document.setRootElement(element);
            document.add(element);
        } else {
            element = ancestors.peek().addElement(name);
        }
        ancestors.push(element);

        NamespaceMap parentNamespaces = nsStack.peek();
        if (namespaces != parentNamespaces) {
            NamespaceBinding[] declarations = namespaces.getDifferences(parentNamespaces, false);
            for (NamespaceBinding ns : declarations) {
                element.addNamespace(ns.getPrefix(), ns.getURI());
            }
        }
        nsStack.push(namespaces);

        for (AttributeInfo att : attributes) {
            NodeName nameCode = att.getNodeName();
            String attlocal = nameCode.getLocalPart();
            String atturi = nameCode.getURI();
            String attprefix = nameCode.getPrefix();
            String value = att.getValue();
            Namespace ns = new Namespace(attprefix, atturi);
            if (uri.equals(NamespaceConstant.XML) && attlocal.equals("id")) {
                value = Whitespace.trim(value);
                idIndex.put(value, (Element) ancestors.peek());
            }
            Attribute attr = new DefaultAttribute(attlocal, value, ns);
            element.add(attr);
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
            Text text = new DefaultText(textBuffer.toString());
            ancestors.peek().add(text);
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
        ProcessingInstruction pi = new DefaultProcessingInstruction(target, data.toString());
        ancestors.peek().add(pi);
    }

    /**
     * Handle a comment.
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        flush();
        Comment comment = new DefaultComment(chars.toString());
        ancestors.peek().add(comment);
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

    /*@Nullable*/
    @Override
    public NodeInfo getCurrentRoot() {
        DOM4JDocumentWrapper wrapper = new DOM4JDocumentWrapper(document, systemId, config);
        wrapper.setUserData("saxon-id-index", idIndex);
        return wrapper.getRootNode();
    }
}

