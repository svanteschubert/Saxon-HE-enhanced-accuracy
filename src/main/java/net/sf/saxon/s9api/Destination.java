////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.serialize.SerializationProperties;

import java.net.URI;
import java.util.function.Consumer;

/**
 * A <tt>Destination</tt> represents a place where XDM values can be sent. It is used, for example,
 * to define the output of a transformation or query.
 * <p>
 * A {@code Destination} is either a tree destination or a raw destination. A <b>tree destination</b>
 * performs <i>sequence normalization</i> on the stream of events passed to it, to construct
 * a tree rooted at a single XDM document node, as defined in the W3C serialization specification
 * (even if the destination is not actually a serializer). A <b>raw destination</b> omits this step.
 * Examples of tree destinations are those designed to accept XML: {@link DOMDestination},
 * {@link SAXDestination}, {@link XdmDestination}, {@link SchemaValidator}.
 * <p>
 * The {@link Serializer} acts as a tree destination when the output methods XML, HTML, XHTML, or TEXT
 * are used, but as a raw destination when the output method is JSON or ADAPTIVE.
 * <p>
 * The interface {@code Destination} has some similarities with the JAXP
 * {@link javax.xml.transform.Result} class. It differs, however, in that implementations
 * of this interface can be written by users or third parties to define new kinds of
 * destination, and any such implementation can be supplied to the Saxon methods that
 * take a {@code Destination} as an argument.
 * <p>
 * Implementing a new {@code Destination} will generally require access
 * to implementation-level classes and methods within the Saxon product. The only method that
 * needs to be supplied is {@link #getReceiver}, which returns an instance of
 * {@link Outputter}, and unless you use an existing implementation of
 * {@code Receiver}, you will need to handle internal Saxon concepts such as name codes
 * and name pools.
 * <p>
 * In general a Destination is not thread-safe (cannot be used from more than one thread),
 * and is not serially reusable. So a Destination should only be used once. A Destination
 * supplied to Saxon may be modified by Saxon.
 * <p>
 * The {@link #close} method is called by the system when
 * it finishes writing the document, and this should cause all resources held by the Destination
 * to be released.
 */
public interface Destination {

    /**
     * Set the base URI of the resource being written to this destination
     * @param baseURI the base URI to be used
     */

    void setDestinationBaseURI(URI baseURI);

    /**
     * Get the base URI of the resource being written to this destination
     * @return the baseURI, or null if none is known
     */

    URI getDestinationBaseURI();

    /**
     * Return a {@link Receiver}. Saxon calls this method to obtain a Receiver, to which it then sends
     * a sequence of events representing an XDM value. The method is intended
     * primarily for internal use, and may give poor diagnostics if used incorrectly.
     * <p>
     * This method is normally only called once. However, in the case where a stylesheet includes
     * a call of <code>xsl:result-document</code> with no <code>href</code> attribute (or with an
     * <code>href</code> attribute that resolves to the base output URI of the transformation), the
     * method may be called a second time (with a potentially different set of serialization parameters,
     * and perhaps a different validation request) to return a second {@link Outputter}, which will typically
     * write to the same destination. The XSLT rules ensure that it is not possible to write principal and
     * secondary output to the same destination, so only one of these Receivers will actually be used.
     *
     * @param pipe The pipeline configuration. This is supplied so that the destination can
     *               use information from the configuration (for example, a reference to the name pool)
     *               to construct or configure the returned Receiver.
     * @param params Serialization parameters known to the caller of the method; typically,
     *               output properties defined in a stylesheet or query. These will mainly
     *               be of interest if the destination is performing serialization, but some
     *               properties (such as {@code item-separator}) are also used in other
     *               situations. These properties are typically subordinate to any properties
     *               defined on the (serializer) destination itself: for example if {@code indent=yes}
     *               was explicitly specified on a {@code Serializer}, this takes precedence
     *               over {@code indent=no} defined in a query or stylesheet.
     *               <p>
     *               The {@link SerializationProperties} object may also contain a factory object for
     *               generating a validator to add to the output pipeline. The {@link Destination} object
     *               is responsible for instantiating this validator and inserting it into the pipeline.
     *               In most cases this is done by invoking the helper method
     *               {@link SerializationProperties#makeSequenceNormalizer(Receiver)}. Validation can be skipped
     *               in the case of non-XML destinations.
     * @return the Receiver to which events are to be sent.
     *         <p>It is the caller's responsibility to
     *         initialize this Receiver with a {@link net.sf.saxon.event.PipelineConfiguration} before calling
     *         its {@code open()} method.</p>
     *         <p>The {@code Receiver} is expected to handle a <b>regular event sequence</b> as defined in
     *         {@link net.sf.saxon.event.RegularSequenceChecker}. It is the caller's responsibility to
     *         ensure that the sequence of calls to the {@code Receiver} satisfies these rules, and it
     *         is the responsibility of the implementation to accept any sequence conforming these rules;
     *         the implementation is not expected to check that the sequence is valid, but it can do so
     *         if it wishes by inserting a {@link net.sf.saxon.event.RegularSequenceChecker} into the pipeline.</p>
     *         <p>The sequence of events passed to the {@code Receiver} represents the <b>raw results</b> of
     *         the query or transformation. If the {@code Destination} is to perform sequence normalization,
     *         this is typically done by returning a {@link net.sf.saxon.event.SequenceNormalizer} as the
     *         result of this method.</p>
     *         <p>The returned {@code Receiver} is responsible for ensuring that when its {@link Outputter#close()}
     *         method is called, this results in all registered {@code onClose} actions being invoked.
     *         An implementation returning a {@code SequenceNormalizer} can achieve this by registering
     *         the actions with the {@link net.sf.saxon.event.SequenceNormalizer#onClose} method.</p>
     *         <p>Only a single call on this method will be made during the lifetime of the {@code Destination}
     *         object, with the exception of the case noted above where a secondary result document is written
     *         to the same destination as the principal transformation result.</p>
     * @throws SaxonApiException if the Receiver cannot be created
     */

    Receiver getReceiver(PipelineConfiguration pipe, SerializationProperties params) throws SaxonApiException;

    /**
     * Register a listener to be notified when a {@link Outputter} linked to this destination is closed.
     * <p>Example: <code>destination.onClose(() -&gt; System.out.println("Finished writing to " + uri)</code></p>
     * <p>The method must be called before the call on {@link #getReceiver(PipelineConfiguration, SerializationProperties)}; the
     * effect of calling it after getting a {@code Receiver}, but before closing the
     * {@link Outputter}, is undefined.</p>
     *
     * @param listener an object to be notified when writing to the destination
     *                 is successfully completed
     */

    void onClose(Action listener);

    /**
     * Close the destination and notify all registered listeners that it has been closed.
     * This method is intended for internal use by Saxon. The method first calls {@link #close}
     * to close the destination, then it calls {@link Consumer#accept} on each of the
     * listeners in turn to notify the fact that it has been closed.
     * @throws SaxonApiException if the close() method throws {@code SaxonApiException}.
     */

    void closeAndNotify() throws SaxonApiException;

    /**
     * Close the destination, allowing resources to be released. Saxon calls this method when
     * it has finished writing to the destination.
     * <p>
     * The {@code close()} method should not cause any adverse effects if it is called more than
     * once. If any other method is called after the {@code close()} call, the results are undefined.
     * This means that a Destination is not, in general, serially reusable.
     * <p>
     * If an {@link #onClose} action has been associated with the destination,
     * this will be called after the destination is closed.</p>
     *
     * @throws SaxonApiException if any failure occurs
     */

    void close() throws SaxonApiException;

}

