////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
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
     *         {@link PullEvent#END_OF_INPUT} is returned at the end of the sequence.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs
     */

    PullEvent next() throws XPathException;

    /**
     * Get the event most recently returned by next(), or by other calls that change
     * the position, for example getStringValue() and skipToMatchingEnd(). This
     * method does not change the position of the PullProvider.
     *
     * @return the current event
     */

    PullEvent current();

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

    PullEvent skipToMatchingEnd() throws XPathException;

    /**
     * Close the event reader. This indicates that no further events are required.
     * It is not necessary to close an event reader after {@link PullEvent#END_OF_INPUT} has
     * been reported, but it is recommended to close it if reading terminates
     * prematurely. Once an event reader has been closed, the effect of further
     * calls on next() is undefined.
     */

    void close();

    /**
     * Get the NodeName identifying the name of the current node. This method
     * can be used after the {@link PullEvent#START_ELEMENT}, {@link PullEvent#PROCESSING_INSTRUCTION},
     * {@link PullEvent#ATTRIBUTE}, or {@link PullEvent#NAMESPACE} events. With some PullProvider implementations,
     * it can also be used after {@link PullEvent#END_ELEMENT}, but this is not guaranteed.
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
     * <p>If the most recent event was a {@link PullEvent#START_ELEMENT}, this method causes the content
     * of the element to be read. The current event on completion of this method will be the
     * corresponding {@link PullEvent#END_ELEMENT}. The next call of next() will return the event following
     * the END_ELEMENT event.</p>
     *
     * @return the String Value of the node in question, defined according to the rules in the
     *         XPath data model.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs
     */

    /*@Nullable*/
    UnicodeString getStringValue() throws XPathException;

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

