package com.saxonica.xqj.pull;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.*;
import net.sf.saxon.pull.UnfailingPullProvider;
import net.sf.saxon.pull.UnparsedEntity;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

/**
 * This implementation of the Saxon pull interface starts from any NodeInfo,
 * and returns the events corresponding to that node and its descendants (including
 * their attributes and namespaces). This works with any tree model: alternative
 * implementations may be available that take advantage of particular implementations
 * of the tree model.
 */

public class TreeWalker implements UnfailingPullProvider, Location {

    private final NodeInfo startNode;
    private NodeInfo currentNode;
    private Event currentEvent = Event.START_OF_INPUT;
    private final Stack<FocusIterator> iteratorStack = new Stack<>();
    private PipelineConfiguration pipe;
    private final NamespaceBinding[] nsBuffer = new NamespaceBinding[10];

    /**
     * Factory method to get a tree walker starting an a given node
     *
     * @param startNode the start node
     * @return a PullProvider that delivers events associated with the subtree starting at the given node
     */

    public static UnfailingPullProvider makeTreeWalker(NodeInfo startNode) {
        return new TreeWalker(startNode);
    }

    /**
     * Private constructor: the class should be instantiated using the static factory method
     *
     * @param startNode the root node of the subtree to be walked
     */

    private TreeWalker(NodeInfo startNode) {
        this.startNode = startNode;
    }

    /**
     * Set configuration information. This must only be called before any events
     * have been read.
     */

    @Override
    public void setPipelineConfiguration(PipelineConfiguration pipe) {
        this.pipe = pipe;
    }

    /**
     * Get configuration information.
     */

    @Override
    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    /**
     * Get the next event
     *
     * @return an Event object indicating the type of event. The code
     *         {@link net.sf.saxon.pull.PullProvider.Event#END_OF_INPUT} is returned if there are no more events to return.
     */

    @Override
    public Event next() throws XPathException {
        switch (currentEvent) {
            case START_OF_INPUT:
                currentNode = startNode;
                switch (currentNode.getNodeKind()) {
                    case Type.DOCUMENT:
                        return currentEvent = Event.START_DOCUMENT;
                    case Type.ELEMENT:
                        return currentEvent = Event.START_ELEMENT;
                    case Type.TEXT:
                        return currentEvent = Event.TEXT;
                    case Type.COMMENT:
                        return currentEvent = Event.COMMENT;
                    case Type.PROCESSING_INSTRUCTION:
                        return currentEvent = Event.PROCESSING_INSTRUCTION;
                    case Type.ATTRIBUTE:
                        return currentEvent = Event.ATTRIBUTE;
                    case Type.NAMESPACE:
                        return currentEvent = Event.NAMESPACE;
                    default:
                        throw new IllegalStateException();
                }

            case START_DOCUMENT:
            case START_ELEMENT:
                FocusIterator kids = new FocusTrackingIterator(currentNode.iterateAxis(AxisInfo.CHILD));
                iteratorStack.push(kids);

                currentNode = (NodeInfo)kids.next();
                if (currentNode != null) {
                    return currentEvent = mapChildNodeEvent();
                } else {
                    iteratorStack.pop();
                    if (iteratorStack.isEmpty()) {
                        currentNode = startNode;
                    }
                    if (currentEvent == Event.START_DOCUMENT) {
                        return currentEvent = Event.END_DOCUMENT;
                    } else {
                        return currentEvent = Event.END_ELEMENT;
                    }
                }
            case TEXT:
            case COMMENT:
            case PROCESSING_INSTRUCTION:
            case END_ELEMENT:
                if (iteratorStack.isEmpty()) {
                    if (currentNode == startNode) {
                        currentNode = null;
                        return currentEvent = Event.END_OF_INPUT;
                    } else {
                        currentNode = startNode;
                        if (currentNode.getNodeKind() == Type.ELEMENT) {
                            return currentEvent = Event.END_ELEMENT;
                        } else {
                            return currentEvent = Event.END_DOCUMENT;
                        }
                    }
                }

                FocusIterator siblings =  iteratorStack.peek();
                currentNode = (NodeInfo)siblings.next();
                if (currentNode == null) {
                    iteratorStack.pop();
                    if (iteratorStack.isEmpty()) {
                        currentNode = startNode;
                        if (currentNode.getNodeKind() == Type.ELEMENT) {
                            return currentEvent = Event.END_ELEMENT;
                        } else {
                            return currentEvent = Event.END_DOCUMENT;
                        }
                    }
                    FocusIterator uncles = iteratorStack.peek();
                    currentNode = (NodeInfo)uncles.current();
                    if (currentNode.getNodeKind() == Type.DOCUMENT) {
                        return currentEvent = Event.END_DOCUMENT;
                    } else {
                        return currentEvent = Event.END_ELEMENT;
                    }
                } else {
                    return currentEvent = mapChildNodeEvent();
                }

            case ATTRIBUTE:
            case NAMESPACE:
            case END_DOCUMENT:
                return currentEvent = Event.END_OF_INPUT;

            case END_OF_INPUT:
                throw new IllegalStateException("Cannot call next() when input is exhausted");

            default:
                throw new IllegalStateException("Unrecognized event " + currentEvent);

        }
    }

    private Event mapChildNodeEvent() {
        switch (currentNode.getNodeKind()) {
            case Type.ELEMENT:
                return Event.START_ELEMENT;
            case Type.TEXT:
                return Event.TEXT;
            case Type.COMMENT:
                return Event.COMMENT;
            case Type.PROCESSING_INSTRUCTION:
                return Event.PROCESSING_INSTRUCTION;
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Get the event most recently returned by next(), or by other calls that change
     * the position, for example getStringValue() and skipToMatchingEnd(). This
     * method does not change the position of the PullProvider.
     *
     * @return the current event
     */

    @Override
    public Event current() {
        return currentEvent;
    }

    /**
     * Get the attributes associated with the current element. This method must
     * be called only after a START_ELEMENT event has been notified. The contents
     * of the returned AttributeMap are immutable.
     * <p>Attributes may be read before or after reading the namespaces of an element,
     * but must not be read after the first child node has been read, or after calling
     * one of the methods skipToEnd(), getStringValue(), or getTypedValue().</p>
     *
     * @return an AttributeMap representing the attributes of the element
     *         that has just been notified.
     */

    @Override
    public AttributeMap getAttributes() {
        if (currentNode == null) {
            throw new IllegalStateException("No current node");
        }
        if (currentNode.getNodeKind() == Type.ELEMENT) {
            return currentNode.attributes();
        } else {
            throw new IllegalStateException("getAttributes() called when current event is not ELEMENT_START");
        }
    }

    /**
     * Get the namespace declarations associated with the current element. This method must
     * be called only after a START_ELEMENT event has been notified. In the case of a top-level
     * START_ELEMENT event (that is, an element that either has no parent node, or whose parent
     * is not included in the sequence being read), the NamespaceDeclarations object returned
     * will contain a namespace declaration for each namespace that is in-scope for this element
     * node. In the case of a non-top-level element, the NamespaceDeclarations will contain
     * a set of namespace declarations and undeclarations, representing the differences between
     * this element and its parent.
     * <p>It is permissible for this method to return namespace declarations that are redundant.</p>
     * <p>The NamespaceDeclarations object is guaranteed to remain unchanged until the next START_ELEMENT
     * event, but may then be overwritten. The object should not be modified by the client.</p>
     * <p>Namespaces may be read before or after reading the attributes of an element,
     * but must not be read after the first child node has been read, or after calling
     * one of the methods skipToEnd(), getStringValue(), or getTypedValue().</p>
     */

    @Override
    public NamespaceBinding[] getNamespaceDeclarations() {
        if (currentNode == null) {
            throw new IllegalStateException("No current node");
        }
        if (currentNode.getNodeKind() == Type.ELEMENT) {
            if (iteratorStack.isEmpty()) {
                // get all inscope namespaces for a top-level element in the sequence.
                Iterator<NamespaceBinding> iter = currentNode.getAllNamespaces().iterator();
                List<NamespaceBinding> list = new ArrayList<>();
                while (iter.hasNext()) {
                    list.add(iter.next());
                }
                return list.toArray(NamespaceBinding.EMPTY_ARRAY);
            } else {
                // only namespace declarations (and undeclarations) on this element are required
                return currentNode.getDeclaredNamespaces(nsBuffer);
            }
        }
        throw new IllegalStateException("getNamespaceDeclarations() called when current event is not ELEMENT_START");
    }

    /**
     * Skip the current subtree. This method may be called only immediately after
     * a START_DOCUMENT or START_ELEMENT event. This call returns the matching
     * END_DOCUMENT or END_ELEMENT event; the next call on next() will return
     * the event following the END_DOCUMENT or END_ELEMENT.
     */

    @Override
    public Event skipToMatchingEnd()  {
        // For this implementation, we simply leave the current node unchanged, and change
        // the current event
        switch (currentEvent) {
            case START_DOCUMENT:
                return currentEvent = Event.END_DOCUMENT;
            case START_ELEMENT:
                return currentEvent = Event.END_ELEMENT;
            default:
                throw new IllegalStateException(
                        "Cannot call skipToMatchingEnd() except when at start of element or document");
        }
    }

    /**
     * Close the event reader. This indicates that no further events are required.
     * It is not necessary to close an event reader after {@link net.sf.saxon.pull.PullProvider.Event#END_OF_INPUT} has
     * been reported, but it is recommended to close it if reading terminates
     * prematurely. Once an event reader has been closed, the effect of further
     * calls on next() is undefined.
     */

    @Override
    public void close() {
        // no action
    }

    /**
     * Get the namePool used to lookup all name codes and namespace codes
     *
     * @return the namePool
     */

    public NamePool getNamePool() {
        return pipe.getConfiguration().getNamePool();
    }

    /**
     * Get the NodeName identifying the name of the current node. This method
     * can be used after the {@link net.sf.saxon.pull.PullProvider.Event#START_ELEMENT}, {@link net.sf.saxon.pull.PullProvider.Event#PROCESSING_INSTRUCTION},
     * {@link net.sf.saxon.pull.PullProvider.Event#ATTRIBUTE}, or {@link net.sf.saxon.pull.PullProvider.Event#NAMESPACE} events. With some PullProvider implementations,
     * it can also be used after {@link net.sf.saxon.pull.PullProvider.Event#END_ELEMENT}, but this is not guaranteed: a client who
     * requires the information at that point (for example, to do serialization) should insert an
     * {@link com.saxonica.xqj.pull.ElementNameTracker} into the pipeline.
     * If called at other times, the result is undefined and may result in an IllegalStateException.
     * If called when the current node is an unnamed namespace node (a node representing the default namespace)
     * the returned value is null.
     *
     * @return the NodeName. The NodeName can be used to obtain the prefix, local name,
     * and namespace URI.
     */
    @Override
    public NodeName getNodeName() {
        if (currentNode == null) {
            throw new IllegalStateException("No current node");
        }
        return NameOfNode.makeName(currentNode);
    }

    /**
     * Get the string value of the current attribute, text node, processing-instruction,
     * or atomic value.
     * This method cannot be used to obtain the string value of an element, or of a namespace
     * node. If the most recent event was anything other than {@link net.sf.saxon.pull.PullProvider.Event#START_ELEMENT}, {@link net.sf.saxon.pull.PullProvider.Event#TEXT},
     * {@link net.sf.saxon.pull.PullProvider.Event#PROCESSING_INSTRUCTION}, or {@link net.sf.saxon.pull.PullProvider.Event#ATOMIC_VALUE}, the result is undefined.
     */

    @Override
    public CharSequence getStringValue() {
        if (currentNode == null) {
            throw new IllegalStateException("No current node");
        }
        if (currentNode.getNodeKind() == Type.ELEMENT) {
            skipToMatchingEnd();
        }
        return currentNode.getStringValueCS();
    }

    /**
     * Get the type annotation of the current attribute or element node, or atomic value.
     * The result of this method is undefined unless the most recent event was START_ELEMENT,
     * START_CONTENT, ATTRIBUTE, or ATOMIC_VALUE.
     *
     * @return the type code.
     */

    @Override
    public SchemaType getSchemaType() {
        if (currentNode == null) {
            throw new IllegalStateException("No current node");
        }
        return currentNode.getSchemaType();
    }

    /**
     * Get an atomic value. This call may be used only when the last event reported was
     * ATOMIC_VALUE. This indicates that the PullProvider is reading a sequence that contains
     * a free-standing atomic value; it is never used when reading the content of a node.
     */

    @Override
    public AtomicValue getAtomicValue() {
        throw new IllegalStateException();
    }

    /**
     * Get the location of the current event.
     * For an event stream representing a real document, the location information
     * should identify the location in the lexical XML source. For a constructed document, it should
     * identify the location in the query or stylesheet that caused the node to be created.
     * A value of null can be returned if no location information is available.
     */

    @Override
    public Location getSourceLocator() {
        return this;
    }

    /**
     * Return the public identifier for the current document event.
     * <p>The return value is the public identifier of the document
     * entity or of the external parsed entity in which the markup that
     * triggered the event appears.</p>
     *
     * @return A string containing the public identifier, or
     *         null if none is available.
     * @see #getSystemId
     */
    @Override
    public String getPublicId() {
        return null;
    }

    /**
     * Return the system identifier for the current document event.
     * <p>The return value is the system identifier of the document
     * entity or of the external parsed entity in which the markup that
     * triggered the event appears.</p>
     * <p>If the system identifier is a URL, the parser must resolve it
     * fully before passing it to the application.</p>
     *
     * @return A string containing the system identifier, or null
     *         if none is available.
     * @see #getPublicId
     */
    @Override
    public String getSystemId() {
        return currentNode == null ? startNode.getSystemId() : currentNode.getSystemId();
    }

    /**
     * Return the line number where the current document event ends.
     * <p><strong>Warning:</strong> The return value from the method
     * is intended only as an approximation for the sake of error
     * reporting; it is not intended to provide sufficient information
     * to edit the character content of the original XML document.</p>
     * <p>The return value is an approximation of the line number
     * in the document entity or external parsed entity where the
     * markup that triggered the event appears.</p>
     *
     * @return The line number, or -1 if none is available.
     * @see #getColumnNumber
     */
    @Override
    public int getLineNumber() {
        return currentNode == null ? -1 : currentNode.getLineNumber();
    }

    /**
     * Return the character position where the current document event ends.
     * <p><strong>Warning:</strong> The return value from the method
     * is intended only as an approximation for the sake of error
     * reporting; it is not intended to provide sufficient information
     * to edit the character content of the original XML document.</p>
     * <p>The return value is an approximation of the column number
     * in the document entity or external parsed entity where the
     * markup that triggered the event appears.</p>
     *
     * @return The column number, or -1 if none is available.
     * @see #getLineNumber
     */
    @Override
    public int getColumnNumber() {
        return -1;
    }

    /**
     * Get an immutable copy of this Location object. By default Location objects may be mutable, so they
     * should not be saved for later use. The result of this operation holds the same location information,
     * but in an immutable form.
     */
    @Override
    public Location saveLocation() {
        return new Loc(this);
    }

    /**
     * Get a list of unparsed entities.
     *
     * @return a list of unparsed entities, or null if the information is not available, or
     *         an empty list if there are no unparsed entities.
     */

    /*@Nullable*/
    @Override
    public List<UnparsedEntity> getUnparsedEntities() {
        return null;
    }
}

// Copyright (c) 2009-2020 Saxonica Limited
