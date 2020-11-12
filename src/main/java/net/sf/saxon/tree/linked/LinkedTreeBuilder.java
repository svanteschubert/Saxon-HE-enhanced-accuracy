////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.*;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;


/**
 * The LinkedTreeBuilder class is responsible for taking a stream of Receiver events and constructing
 * a Document tree using the linked tree implementation.
 *
 * @author Michael H. Kay
 */

public class LinkedTreeBuilder extends Builder

{
//    private static AttributeCollectionImpl emptyAttributeCollection =
//    				new AttributeCollectionImpl((Configuration)null);

    /*@Nullable*/ private ParentNodeImpl currentNode;
    private NodeFactory nodeFactory;
    /*@NotNull*/ private int[] size = new int[100];          // stack of number of children for each open node
    private int depth = 0;
    private ArrayList<NodeImpl[]> arrays = new ArrayList<>(20);       // reusable arrays for creating nodes
    private Stack<NamespaceMap> namespaceStack = new Stack<>();
    private boolean allocateSequenceNumbers = true;
    private int nextNodeNumber = 1;
    private boolean mutable;

    /**
     * Create a Builder and initialise variables
     *
     * @param pipe the pipeline configuration
     */

    public LinkedTreeBuilder(PipelineConfiguration pipe) {
        super(pipe);
        nodeFactory = DefaultNodeFactory.THE_INSTANCE;
        // System.err.println("new TreeBuilder " + this);
    }

    /**
     * Create a Builder and initialise variables
     *
     * @param pipe the pipeline configuration
     * @param mutable set to true if the tree is to be mutable
     */

    public LinkedTreeBuilder(PipelineConfiguration pipe, boolean mutable) {
        super(pipe);
        this.mutable = mutable;
        nodeFactory = DefaultNodeFactory.THE_INSTANCE;
        // System.err.println("new TreeBuilder " + this);
    }


    /**
     * Get the current root node. This will normally be a document node, but if the root of the tree
     * is an element node, it can be an element.
     *
     * @return the root of the tree that is currently being built, or that has been most recently built
     *         using this builder
     */

    /*@Nullable*/
    @Override
    public NodeInfo getCurrentRoot() {
        NodeInfo physicalRoot = currentRoot;
        if (physicalRoot instanceof DocumentImpl && ((DocumentImpl) physicalRoot).isImaginary()) {
            return ((DocumentImpl) physicalRoot).getDocumentElement();
        } else {
            return physicalRoot;
        }
    }

    @Override
    public void reset() {
        super.reset();
        currentNode = null;
        nodeFactory = DefaultNodeFactory.THE_INSTANCE;
        depth = 0;
        allocateSequenceNumbers = true;
        nextNodeNumber = 1;
    }

    /**
     * Set whether the builder should allocate sequence numbers to elements as they are added to the
     * tree. This is normally done, because it provides a quick way of comparing document order. But
     * nodes added using XQuery update are not sequence-numbered.
     *
     * @param allocate true if sequence numbers are to be allocated
     */

    public void setAllocateSequenceNumbers(boolean allocate) {
        allocateSequenceNumbers = allocate;
    }

    /**
     * Set the Node Factory to use. If none is specified, the Builder uses its own.
     *
     * @param factory the node factory to be used. This allows custom objects to be used to represent
     *                the elements in the tree.
     */

    public void setNodeFactory(NodeFactory factory) {
        nodeFactory = factory;
    }

    /**
     * Open the stream of Receiver events
     */

    @Override
    public void open() {
        started = true;
        depth = 0;
        size[depth] = 0;
        if (arrays == null) {
            arrays = new ArrayList<NodeImpl[]>(20);
        }
        super.open();
    }


    /**
     * Start of a document node.
     * This event is ignored: we simply add the contained elements to the current document
     * @param properties properties of the document node
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        DocumentImpl doc = new DocumentImpl();
        doc.setMutable(mutable);
        currentRoot = doc;
        doc.setSystemId(getSystemId());
        doc.setBaseURI(getBaseURI());
        doc.setConfiguration(config);
        currentNode = doc;
        depth = 0;
        size[depth] = 0;
        if (arrays == null) {
            arrays = new ArrayList<>(20);
        }
        doc.setRawSequenceNumber(0);
        if (lineNumbering) {
            doc.setLineNumbering();
        }
    }

    /**
     * Notify the end of the document
     */

    @Override
    public void endDocument() throws XPathException {
        //System.err.println("End document depth=" + depth);
        currentNode.compact(size[depth]);
    }

    /**
     * Close the stream of Receiver events
     */

    @Override
    public void close() throws XPathException {
        // System.err.println("TreeBuilder: " + this + " End document");
        if (currentNode == null) {
            return;    // can be called twice on an error path
        }
        currentNode.compact(size[depth]);
        currentNode = null;

        // we're not going to use this Builder again so give the garbage collector
        // something to play with
        arrays = null;

        super.close();
        nodeFactory = DefaultNodeFactory.THE_INSTANCE;
    }

    /**
     * Notify the start of an element
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap suppliedAttributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        //System.err.println("LinkedTreeBuilder: " + this + " Start element depth=" + depth);
        if (currentNode == null) {
            startDocument(ReceiverOption.NONE);
            ((DocumentImpl) currentRoot).setImaginary(true);
        }
        boolean isNilled = ReceiverOption.contains(properties, ReceiverOption.NILLED_ELEMENT);

        namespaceStack.push(namespaces);

        boolean isTopWithinEntity = location instanceof ReceivingContentHandler.LocalLocator &&
                ((ReceivingContentHandler.LocalLocator) location).levelInEntity == 0;

        AttributeInfo xmlId = suppliedAttributes.get(NamespaceConstant.XML, "id");
        if (xmlId != null && Whitespace.containsWhitespace(xmlId.getValue())) {
            suppliedAttributes = suppliedAttributes.put(new AttributeInfo(
                    xmlId.getNodeName(), xmlId.getType(), Whitespace.trim(xmlId.getValue()), xmlId.getLocation(), xmlId.getProperties()));
        }

        ElementImpl elem = nodeFactory.makeElementNode(
                currentNode, elemName, type, isNilled,
                suppliedAttributes, namespaceStack.peek(),
                pipe,
                location, allocateSequenceNumbers ? nextNodeNumber++ : -1);

        // the initial array used for pointing to children will be discarded when the exact number
        // of children in known. Therefore, it can be reused. So we allocate an initial array from
        // a pool of reusable arrays. A nesting depth of >20 is so rare that we don't bother.

        while (depth >= arrays.size()) {
            arrays.add(new NodeImpl[20]);
        }
        elem.setChildren(arrays.get(depth));

        currentNode.addChild(elem, size[depth]++);
        if (depth >= size.length - 1) {
            size = Arrays.copyOf(size, size.length * 2);
        }
        size[++depth] = 0;

        if (currentNode instanceof TreeInfo) {
            ((DocumentImpl) currentNode).setDocumentElement(elem);
        }

        if (isTopWithinEntity) {
            currentNode.getPhysicalRoot().markTopWithinEntity(elem);
        }

        currentNode = elem;
    }

    /**
     * Notify the end of an element
     */

    @Override
    public void endElement() throws XPathException {
        //System.err.println("End element depth=" + depth);
        currentNode.compact(size[depth]);
        depth--;
        currentNode = (ParentNodeImpl) currentNode.getParent();
        namespaceStack.pop();
    }

    /**
     * Notify a text node. Adjacent text nodes must have already been merged
     */

    @Override
    public void characters(/*@NotNull*/ CharSequence chars, Location locationId, int properties) throws XPathException {
        // System.err.println("Characters: " + chars.toString() + " depth=" + depth);
        if (chars.length() > 0) {
            NodeInfo prev = currentNode.getNthChild(size[depth] - 1);
            if (prev instanceof TextImpl) {
                ((TextImpl) prev).appendStringValue(chars.toString());
            } else {
                TextImpl n = nodeFactory.makeTextNode(currentNode, chars);
                //TextImpl n = new TextImpl(chars.toString());
                currentNode.addChild(n, size[depth]++);
            }
        }
    }

    /**
     * Notify a processing instruction
     */

    @Override
    public void processingInstruction(String name, /*@NotNull*/ CharSequence remainder, Location locationId, int properties) {
        ProcInstImpl pi = new ProcInstImpl(name, remainder.toString());
        currentNode.addChild(pi, size[depth]++);
        pi.setLocation(locationId.getSystemId(), locationId.getLineNumber(), locationId.getColumnNumber());
    }

    /**
     * Notify a comment
     */

    @Override
    public void comment(/*@NotNull*/ CharSequence chars, Location locationId, int properties) throws XPathException {
        CommentImpl comment = new CommentImpl(chars.toString());
        currentNode.addChild(comment, size[depth]++);
        comment.setLocation(locationId.getSystemId(), locationId.getLineNumber(), locationId.getColumnNumber());
    }

    /**
     * Get the current document or element node
     *
     * @return the most recently started document or element node (to which children are currently being added)
     *         In the case of elements, this is only available after startContent() has been called
     */

    /*@Nullable*/
    public ParentNodeImpl getCurrentParentNode() {
        return currentNode;
    }

    /**
     * Get the current text, comment, or processing instruction node
     *
     * @return if any text, comment, or processing instruction nodes have been added to the current parent
     *         node, then return that text, comment, or PI; otherwise return null
     */

    /*@NotNull*/
    public NodeImpl getCurrentLeafNode() {
        return currentNode.getLastChild();
    }


    /**
     * graftElement() allows an element node to be transferred from one tree to another.
     * This is a dangerous internal interface which is used only to contruct a stylesheet
     * tree from a stylesheet using the "literal result element as stylesheet" syntax.
     * The supplied element is grafted onto the current element as its only child.
     *
     * @param element the element to be grafted in as a new child.
     */

    public void graftElement(ElementImpl element) {
        currentNode.addChild(element, size[depth]++);
    }

    /**
     * Set an unparsed entity URI for the document
     */

    @Override
    public void setUnparsedEntity(String name, String uri, String publicId) {
        if (((DocumentImpl) currentRoot).getUnparsedEntity(name) == null) {
            // bug 2187
            ((DocumentImpl) currentRoot).setUnparsedEntity(name, uri, publicId);
        }
    }

    /**
     * Get a builder monitor for this builder. This must be called immediately after opening the builder,
     * and all events to the builder must thenceforth be sent via the BuilderMonitor.
     *
     * @return a new BuilderMonitor appropriate to this kind of Builder; or null if the Builder does
     *         not provide this service
     */

    /*@NotNull*/
    @Override
    public BuilderMonitor getBuilderMonitor() {
        return new LinkedBuilderMonitor(this);
    }

    //////////////////////////////////////////////////////////////////////////////
    // Inner class DefaultNodeFactory. This creates the nodes in the tree.
    // It can be overridden, e.g. when building the stylesheet tree
    //////////////////////////////////////////////////////////////////////////////

    private static class DefaultNodeFactory implements NodeFactory {

        public static DefaultNodeFactory THE_INSTANCE = new DefaultNodeFactory();

        /*@NotNull*/
        @Override
        public ElementImpl makeElementNode(
                /*@NotNull*/ NodeInfo parent,
                /*@NotNull*/ NodeName nodeName,
                             SchemaType elementType,
                             boolean isNilled,
                             AttributeMap attlist,
                             NamespaceMap namespaces,
                /*@NotNull*/ PipelineConfiguration pipe,
                             Location locationId,
                             int sequenceNumber)

        {
            ElementImpl e = new ElementImpl();
            e.setNamespaceMap(namespaces);

            e.initialise(nodeName, elementType, attlist, parent, sequenceNumber);
            if (isNilled) {
                e.setNilled();
            }
            if (locationId != Loc.NONE && sequenceNumber >= 0) {
                String baseURI = locationId.getSystemId();
                int lineNumber = locationId.getLineNumber();
                int columnNumber = locationId.getColumnNumber();
                e.setLocation(baseURI, lineNumber, columnNumber);
            }
            return e;
        }

        /**
         * Make a text node
         *
         * @param parent  the parent element
         * @param content the content of the text node
         * @return the constructed text node
         */
        @Override
        public TextImpl makeTextNode(NodeInfo parent, CharSequence content) {
            return new TextImpl(content.toString());
        }
    }


}

