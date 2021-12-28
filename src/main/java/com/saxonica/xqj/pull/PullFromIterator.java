package com.saxonica.xqj.pull;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.pull.UnfailingPullProvider;
import net.sf.saxon.pull.UnparsedEntity;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;

import java.util.List;

/**
 * This class delivers any XPath sequence through the pull interface. Atomic values
 * in the sequence are supplied unchanged, as are top-level text, comment, attribute,
 * namespace, and processing-instruction nodes. Elements and documents appearing in
 * the input sequence are supplied as a sequence of events that walks recursively
 * down the subtree rooted at that node. The input is supplied in the form of a
 * SequenceIterator.
 */

public class PullFromIterator implements UnfailingPullProvider {

    private final FocusIterator base;
    private UnfailingPullProvider treeWalker = null;
    private PipelineConfiguration pipe;
    private Event currentEvent = Event.START_OF_INPUT;

    /**
     * Create a PullProvider that wraps a supplied SequenceIterator
     *
     * @param base the sequence iterator to be wrapped
     */

    public PullFromIterator(SequenceIterator base) {
        this.base = base instanceof FocusIterator ? (FocusIterator)base : new FocusTrackingIterator(base);
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
     * @return an event code indicating the type of event. The code
     *         {@link net.sf.saxon.pull.PullProvider.Event#END_OF_INPUT} is returned at the end of the sequence.
     */

    @Override
    public Event next() throws XPathException {
        if (treeWalker == null) {
            Item item = base.next();
            if (item == null) {
                currentEvent = Event.END_OF_INPUT;
                return currentEvent;
            } else if (item instanceof AtomicValue) {
                currentEvent = Event.ATOMIC_VALUE;
                return currentEvent;
            } else {
                switch (((NodeInfo) item).getNodeKind()) {
                    case Type.TEXT:
                        currentEvent = Event.TEXT;
                        return currentEvent;

                    case Type.COMMENT:
                        currentEvent = Event.COMMENT;
                        return currentEvent;

                    case Type.PROCESSING_INSTRUCTION:
                        currentEvent = Event.PROCESSING_INSTRUCTION;
                        return currentEvent;

                    case Type.ATTRIBUTE:
                        currentEvent = Event.ATTRIBUTE;
                        return currentEvent;

                    case Type.NAMESPACE:
                        currentEvent = Event.NAMESPACE;
                        return currentEvent;

                    case Type.ELEMENT:
                    case Type.DOCUMENT:
                        treeWalker = TreeWalker.makeTreeWalker((NodeInfo) item);
                        treeWalker.setPipelineConfiguration(pipe);
                        currentEvent = treeWalker.next();
                        return currentEvent;

                    default:
                        throw new IllegalStateException();

                }
            }

        } else {
            // there is an active TreeWalker: just return its next event
            Event event = treeWalker.next();
            if (event == Event.END_OF_INPUT) {
                treeWalker = null;
                currentEvent = next();
            } else {
                currentEvent = event;
            }
            return currentEvent;

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
     * of the returned AttributeCollection are guaranteed to remain unchanged
     * until the next START_ELEMENT event, but may be modified thereafter. The object
     * should not be modified by the client.
     * <p>Attributes may be read before or after reading the namespaces of an element,
     * but must not be read after the first child node has been read, or after calling
     * one of the methods skipToEnd(), getStringValue(), or getTypedValue().</p>
     *
     * @return an AttributeCollection representing the attributes of the element
     *         that has just been notified.
     */

    @Override
    public AttributeMap getAttributes() {
        if (treeWalker != null) {
            return treeWalker.getAttributes();
        } else {
            throw new IllegalStateException();
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
        if (treeWalker != null) {
            return treeWalker.getNamespaceDeclarations();
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Skip the current subtree. This method may be called only immediately after
     * a START_DOCUMENT or START_ELEMENT event. This call returns the matching
     * END_DOCUMENT or END_ELEMENT event; the next call on next() will return
     * the event following the END_DOCUMENT or END_ELEMENT.
     */

    @Override
    public Event skipToMatchingEnd() {
        if (treeWalker != null) {
            return treeWalker.skipToMatchingEnd();
        } else {
            throw new IllegalStateException();
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
        if (treeWalker != null) {
            treeWalker.close();
        }
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
        if (treeWalker != null) {
            return treeWalker.getNodeName();
        } else {
            Item item = base.current();
            if (item instanceof NodeInfo) {
                return NameOfNode.makeName((NodeInfo)item);
            } else {
                throw new IllegalStateException();
            }
        }
    }

    /**
     * Get the string value of the current element, text node, processing-instruction,
     * or top-level attribute or namespace node, or atomic value.
     * <p>In other situations the result is undefined and may result in an IllegalStateException.</p>
     * <p>If the most recent event was a {@link net.sf.saxon.pull.PullProvider.Event#START_ELEMENT}, this method causes the content
     * of the element to be read. The current event on completion of this method will be the
     * corresponding {@link net.sf.saxon.pull.PullProvider.Event#END_ELEMENT}. The next call of next() will return the event following
     * the END_ELEMENT event.</p>
     *
     * @return the String Value of the node in question, defined according to the rules in the
     *         XPath data model.
     */

    @Override
    public CharSequence getStringValue() throws XPathException {
        if (treeWalker != null) {
            return treeWalker.getStringValue();
        } else {
            Item item = base.current();
            return item.getStringValueCS();
        }
    }

    /**
     * Get an atomic value. This call may be used only when the last event reported was
     * ATOMIC_VALUE. This indicates that the PullProvider is reading a sequence that contains
     * a free-standing atomic value; it is never used when reading the content of a node.
     */

    @Override
    public AtomicValue getAtomicValue() {
        if (currentEvent == Event.ATOMIC_VALUE) {
            return (AtomicValue) base.current();
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Get the type annotation of the current attribute or element node, or atomic value.
     * The result of this method is undefined unless the most recent event was START_ELEMENT,
     * ATTRIBUTE, or ATOMIC_VALUE.
     *
     * @return the type annotation.
     */

    @Override
    public SchemaType getSchemaType() {
        if (treeWalker != null) {
            return treeWalker.getSchemaType();
        } else {
            Item item = base.current();
            if (item instanceof NodeInfo) {
                return ((NodeInfo) item).getSchemaType();
            } else {
                return ((AtomicValue) item).getItemType();
            }
        }
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
        if (treeWalker != null) {
            return treeWalker.getSourceLocator();
        } else {
            return null;
        }
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
