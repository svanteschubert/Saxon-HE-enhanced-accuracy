////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.AtomicValue;

import java.util.List;

/**
 * PullProvider is Saxon's pull-based interface for reading XML documents and XDM sequences.
 * A PullProvider can deliver any sequence of nodes or atomic values. An atomic value
 * in the sequence is delivered as a single event; a node is delivered as a sequence
 * of events equivalent to a recursive walk of the XML tree. Within this sequence,
 * the start and end of a document, or of an element, are delivered as separate
 * events; other nodes are delivered as individual events.
 */

public interface PullProvider {


    // Start by defining the different types of event

    enum Event {
        /**
         * START_OF_INPUT is the initial state when the PullProvider is instantiated.
         * This event is never notified by the next() method, but it is returned
         * from a call of current() prior to the first call on next().
         */

        START_OF_INPUT,

        /**
         * ATOMIC_VALUE is notified when the PullProvider is reading a sequence of items,
         * and one of the items is an atomic value rather than a node. This will always
         * be a top-level event (it will never be nested in Start/End Document or
         * Start/End Element).
         */

        ATOMIC_VALUE,

        /**
         * START_DOCUMENT is notified when a document node is encountered. This will
         * always be a top-level event (it will never be nested in Start/End Document or
         * Start/End Element). Note however that multiple document nodes can occur in
         * a sequence, and the start and end of each one will be notified.
         */

        START_DOCUMENT,

        /**
         * END_DOCUMENT is notified at the end of processing a document node, that is,
         * after all the descendants of the document node have been notified. The event
         * will always be preceded by the corresponding START_DOCUMENT event.
         */

        END_DOCUMENT,

        /**
         * START_ELEMENT is notified when an element node is encountered. This may either
         * be a top-level element (an element node that participates in the sequence being
         * read in its own right) or a nested element (reported because it is a descendant
         * of an element or document node that participates in the sequence.)
         * <p>Following the notification of START_ELEMENT, the client may obtain information
         * about the element node, such as its name and type annotation. The client may also
         * call getAttributes() to obtain information about the attributes of the element
         * node, and/or getNamespaceDeclarations() to get information about the namespace
         * declarations. The client may then do one of the following:</p>
         * <ul>
         * <li>Call skipToMatchingEnd() to move straight to the corresponding END_ELEMENT event (which
         * will then be the current event)</li>
         * <li>Call next(), repeatedly, to be notified of events relating to the children and
         * descendants of this element node</li>
         * <li>Call getStringValue() to obtain the string value of the element node, after which
         * the next event notified will be the corresponding END_ELEMENT event</li>
         * <li>Call getTypedValue() to obtain the typed value of the element node, after which
         * the next event notified will be the corresponding END_ELEMENT event</li>
         * </ul>
         */

        START_ELEMENT,

        /**
         * END_ELEMENT is notified at the end of an element node, that is, after all the children
         * and descendants of the element have either been processed or skipped. It may relate to
         * a top-level element, or to a nested element. For an empty element (one with no children)
         * the END_ELEMENT event will immediately follow the corresponding START_ELEMENT event.
         * No information (such as the element name) is available after an END_ELEMENT event: if the
         * client requires such information, it must remember it, typically on a Stack.
         */

        END_ELEMENT,

        /**
         * The ATTRIBUTE event is notified only for an attribute node that appears in its own right
         * as a top-level item in the sequence being read. ATTRIBUTE events are not notified for
         * the attributes of an element that has been notified: such attributes must be read using the
         * {@link #getAttributes()} method.
         */

        ATTRIBUTE,

        /**
         * The NAMESPACE event is notified only for a namespace node that appears in its own right
         * as a top-level item in the sequence being read. NAMESPACE events are not notified for
         * the namespaces of an element that has been notified: such attributes must be read using the
         * {@link #getNamespaceDeclarations()} method.
         */

        NAMESPACE,

        /**
         * A TEXT event is notified for a text node. This may either be a top-level text
         * node, or a text node nested within an element or document node. At the top level,
         * text nodes may be zero-length and may be consecutive in the sequence being read.
         * Nested within an element or document node, text nodes will never be zero-length,
         * and adjacent text nodes will have been coalesced into one. (This might not always
         * be true when reading third-party data models such as a DOM.) Whitespace-only
         * text nodes will be notified unless something has been done (e.g. xsl:strip-space)
         * to remove them.
         */

        TEXT,

        /**
         * A COMMENT event is notified for a comment node, which may be either a top-level
         * comment or one nested within an element or document node.
         */

        COMMENT,

        /**
         * A PROCESSING_INSTRUCTION event is notified for a processing instruction node,
         * which may be either a top-level comment or one nested within an element or document node.
         * As defined in the XPath data model, the "target" of a processing instruction is represented
         * as the node name (which only has a local part, no prefix or URI), and the "data" of the
         * processing instruction is represented as the string-value of the node.
         */

        PROCESSING_INSTRUCTION,

        /**
         * The END_OF_INPUT event is returned to indicate the end of the sequence being read.
         * After this event, the result of any further calls on the next() method is undefined.
         */

        END_OF_INPUT
    }

    /**
     * Set configuration information. This must only be called before any events
     * have been read.
     *
     * @param pipe the pipeline configuration
     */

    void setPipelineConfiguration(PipelineConfiguration pipe);

    /**
     * Get configuration information.
     *
     * @return the pipeline configuration
     */

    PipelineConfiguration getPipelineConfiguration();

    /**
     * Get the next event
     *
     * @return an Event object indicating the type of event. The code
     *         {@link net.sf.saxon.pull.PullProvider.Event#END_OF_INPUT} is returned at the end of the sequence.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs
     */

    Event next() throws XPathException;

    /**
     * Get the event most recently returned by next(), or by other calls that change
     * the position, for example getStringValue() and skipToMatchingEnd(). This
     * method does not change the position of the PullProvider.
     *
     * @return the current event
     */

    Event current();

    /**
     * Get the attributes associated with the current element. This method must
     * be called only after a START_ELEMENT event has been notified. The contents
     * of the returned AttributeMap are immutable.
     * <p>Attributes may be read before or after reading the namespaces of an element,
     * but must not be read after the first child node has been read, or after calling
     * one of the methods skipToMatchingEnd(), getStringValue(), or getTypedValue().</p>
     *
     * @return an AttributeMap representing the attributes of the element
     *         that has just been notified.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs
     */

    AttributeMap getAttributes() throws XPathException;

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
     * one of the methods skipToMatchingEnd(), getStringValue(), or getTypedValue().</p>
     *
     * @return the namespace declarations associated with the current START_ELEMENT event.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs
     */

    NamespaceBinding[] getNamespaceDeclarations() throws XPathException;

    /**
     * Skip the current subtree. This method may be called only immediately after
     * a START_DOCUMENT or START_ELEMENT event. This call returns the matching
     * END_DOCUMENT or END_ELEMENT event; the next call on next() will return
     * the event following the END_DOCUMENT or END_ELEMENT.
     *
     * @return the matching END_DOCUMENT or END_ELEMENT event
     * @throws IllegalStateException if the method is called at any time other than
     *                               immediately after a START_DOCUMENT or START_ELEMENT event.
     * @throws net.sf.saxon.trans.XPathException
     *                               if a dynamic error occurs
     */

    Event skipToMatchingEnd() throws XPathException;

    /**
     * Close the event reader. This indicates that no further events are required.
     * It is not necessary to close an event reader after {@link net.sf.saxon.pull.PullProvider.Event#END_OF_INPUT} has
     * been reported, but it is recommended to close it if reading terminates
     * prematurely. Once an event reader has been closed, the effect of further
     * calls on next() is undefined.
     */

    void close();

    /**
     * Get the NodeName identifying the name of the current node. This method
     * can be used after the {@link net.sf.saxon.pull.PullProvider.Event#START_ELEMENT}, {@link net.sf.saxon.pull.PullProvider.Event#PROCESSING_INSTRUCTION},
     * {@link net.sf.saxon.pull.PullProvider.Event#ATTRIBUTE}, or {@link net.sf.saxon.pull.PullProvider.Event#NAMESPACE} events. With some PullProvider implementations,
     * it can also be used after {@link net.sf.saxon.pull.PullProvider.Event#END_ELEMENT}, but this is not guaranteed.
     * If called at other times, the result is undefined and may result in an IllegalStateException.
     * If called when the current node is an unnamed namespace node (a node representing the default namespace)
     * the returned value is null.
     *
     * @return the NodeName. The NodeName can be used to obtain the prefix, local name,
     * and namespace URI.
     */

    NodeName getNodeName();

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
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs
     */

    /*@Nullable*/
    CharSequence getStringValue() throws XPathException;

    /**
     * Get the type annotation of the current attribute or element node, or atomic value.
     * The result of this method is undefined unless the most recent event was START_ELEMENT,
     * ATTRIBUTE, or ATOMIC_VALUE.
     *
     * @return the type annotation.
     * @since 9.4; replace the method getTypeAnnotation() which returned the integer fingerprint of the type
     */

    SchemaType getSchemaType();

    /**
     * Get an atomic value. This call may be used only when the last event reported was
     * ATOMIC_VALUE. This indicates that the PullProvider is reading a sequence that contains
     * a free-standing atomic value; it is never used when reading the content of a node.
     *
     * @return the atomic value
     */

    AtomicValue getAtomicValue();

    /**
     * Get the location of the current event.
     * For an event stream representing a real document, the location information
     * should identify the location in the lexical XML source. For a constructed document, it should
     * identify the location in the query or stylesheet that caused the node to be created.
     * A value of null can be returned if no location information is available.
     *
     * @return the SourceLocator giving the location of the current event, or null if
     *         no location information is available
     */

    Location getSourceLocator();

    /**
     * Get a list of unparsed entities.
     *
     * @return a list of unparsed entities, or null if the information is not available, or
     *         an empty list if there are no unparsed entities. Each item in the list will
     *         be an instance of {@link net.sf.saxon.pull.UnparsedEntity}
     */

    List<UnparsedEntity> getUnparsedEntities();

}

