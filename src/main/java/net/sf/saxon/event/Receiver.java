////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import javax.xml.transform.Result;

/**
 * Receiver: This interface represents a recipient of XML tree-walking (push) events. It is
 * based on SAX2's ContentHandler, but adapted to handle additional events. Namespaces and
 * Attributes are handled by separate events following the startElement event. Schema types
 * can be defined for elements and attributes.
 * <p>The Receiver interface is an important internal interface within Saxon, and provides a powerful
 * mechanism for integrating Saxon with other applications. It has been designed with extensibility
 * and stability in mind. However, it should be considered as an interface designed primarily for
 * internal use, and not as a completely stable part of the public Saxon API.</p>
 *
 * @since 8.0. Extended in 9.9 to support additional methods with default implementations.
 * Changed in 10.0 to accept all the attributes and namespaces as part of the startElement event
 * (thus eliminating the need for a separate startContent event).
 */

public interface Receiver extends Result {

    /**
     * Set the pipeline configuration
     *
     * @param pipe the pipeline configuration
     */

    void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe);

    /**
     * Get the pipeline configuration
     *
     * @return the pipeline configuration
     */

    /*@NotNull*/
    PipelineConfiguration getPipelineConfiguration();

    /**
     * Set the System ID of the tree represented by this event stream
     *
     * @param systemId the system ID (which is used as the base URI of the nodes
     *                 if there is no xml:base attribute)
     */

    @Override
    void setSystemId(String systemId);

    /**
     * Notify the start of the event stream
     *
     * @throws XPathException if an error occurs
     */

    void open() throws XPathException;

    /**
     * Notify the start of a document node
     *
     * @param properties bit-significant integer indicating properties of the document node.
     *                   The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */

    void startDocument(int properties) throws XPathException;

    /**
     * Notify the end of a document node
     *
     * @throws XPathException if an error occurs
     */

    void endDocument() throws XPathException;

    /**
     * Notify an unparsed entity URI.
     *
     * @param name     The name of the unparsed entity
     * @param systemID The system identifier of the unparsed entity
     * @param publicID The identifier of the unparsed entity
     * @throws XPathException if an error occurs
     */

    void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException;

    /**
     * Notify the start of an element
     *
     * @param elemName   the name of the element.
     * @param type   the type annotation of the element.
     * @param attributes the attributes of this element
     * @param namespaces the in-scope namespaces of this element: generally this is all the in-scope
     *                   namespaces, without relying on inheriting namespaces from parent elements
     * @param location   an object providing information about the module, line, and column where the node originated
     * @param properties bit-significant properties of the element node. If there are no relevant
     *                   properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */

    void startElement(NodeName elemName, SchemaType type,
                      AttributeMap attributes, NamespaceMap namespaces,
                      Location location, int properties)
            throws XPathException;

    /**
     * Notify the end of an element. The receiver must maintain a stack if it needs to know which
     * element is ending.
     *
     * @throws XPathException if an error occurs
     */

    void endElement() throws XPathException;

    /**
     * Notify character data. Note that some receivers may require the character data to be
     * sent in a single event, but in general this is not a requirement.
     *
     * @param chars      The characters
     * @param location  provides information such as line number and system ID.
     * @param properties Bit significant value. The following bits are defined:
     *                   <dl>
     *                   <dt>DISABLE_ESCAPING</dt>           <dd>Disable escaping for this text node</dd>
     *                   <dt>USE_CDATA</dt>                  <dd>Output as a CDATA section</dd>
     *                   </dl>
     * @throws XPathException if an error occurs
     */

    void characters(CharSequence chars, Location location, int properties)
            throws XPathException;

    /**
     * Output a processing instruction
     *
     * @param name       The PI name. This must be a legal name (it will not be checked).
     * @param data       The data portion of the processing instruction
     * @param location   provides information such as line number and system ID.
     * @param properties Additional information about the PI.
     * @throws IllegalArgumentException  if the content is invalid for an XML processing instruction
     * @throws XPathException            if an error occurs
     */

    void processingInstruction(String name, CharSequence data, Location location, int properties)
            throws XPathException;

    /**
     * Notify a comment. Comments are only notified if they are outside the DTD.
     *
     * @param content    The content of the comment
     * @param location  provides information such as line number and system ID.
     * @param properties Additional information about the comment.
     * @throws IllegalArgumentException  if the content is invalid for an XML comment
     * @throws XPathException            if an error occurs
     */

    void comment(CharSequence content, Location location, int properties) throws XPathException;

    /**
     * Append an arbitrary item (node, atomic value, or function) to the output. The default
     * implementation throws {@code UnsupportedOperationException}.
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param properties if the item is an element node, this indicates whether its namespaces
*                       need to be copied. Values are {@link ReceiverOption#ALL_NAMESPACES}; the default (0) means
     * @throws XPathException if an error occurs
     * @throws UnsupportedOperationException if this Receiver does not allow appending of items (at any time,
     * or at this particular point in the sequence of events)
     */

     default void append(Item item, Location locationId, int properties) throws XPathException {
         throw new UnsupportedOperationException();
     }

    /**
     * Append an arbitrary item (node, atomic value, or function) to the output.
     * By default, if the item is an element node, it is copied with all namespaces.
     * The default implementation calls {@link #append(Item, Location, int)}, whose
     * default implementation throws {@code UnsupportedOperationException}
     *
     * @param item the item to be appended
     * @throws XPathException if the operation fails
     */

    default void append(Item item) throws XPathException {
        append(item, Loc.NONE, ReceiverOption.ALL_NAMESPACES);
    }

    /**
     * Notify the end of the event stream
     *
     * @throws XPathException if an error occurs
     */

    void close() throws XPathException;

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events. The default implementation of this method
     * returns false.
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation (or conversely, it may
     *         avoid stripping unwanted type annotations)
     */

    default boolean usesTypeAnnotations() {
        return false;
    }

    /**
     * Ask whether this Receiver can handle arbitrary items in its {@link #append} and
     * {@link #append(Item, Location, int)} methods. If it cannot, then calling
     * these methods will raise an exception (typically but not necessarily an
     * {@code UnsupportedOperationException}). The default implementation of this
     * method returns false.
     *
     * @return true if the Receiver is able to handle items supplied to
     * its {@link #append} and {@link #append(Item, Location, int)} methods. A
     * receiver that returns true may still reject some kinds of item, for example
     * it may reject function items.
     */

    default boolean handlesAppend() {
        return false;
    }


}

