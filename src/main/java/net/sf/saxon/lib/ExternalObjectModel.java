////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.JPConverter;
import net.sf.saxon.expr.PJConverter;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Result;
import javax.xml.transform.Source;

/**
 * This interface must be implemented by any third-party object model that can
 * be wrapped with a wrapper that implements the Saxon Object Model (the NodeInfo interface).
 * <p>This interface is designed to enable advanced applications to implement and register
 * new object model implementations that Saxon can then use without change. Although it is intended
 * for external use, it cannot at this stage be considered part of the stable Saxon Public API.
 * In particular, it is likely that the interface will grow by the addition of new methods.</p>
 * <p>For maximum integration, an object may extend {@link net.sf.saxon.om.TreeModel} as well as implementing
 * this interface. To implement <code>TreeModel</code>, it must supply a Builder; in effect this
 * means that it will be possible to use the external object model for output as well as for
 * input.</p>
 */

public interface ExternalObjectModel {

    /**
     * Get the name of a characteristic class, which, if it can be loaded, indicates that the supporting libraries
     * for this object model implementation are available on the classpath
     * @return by convention (but not necessarily) the class that implements a document node in the relevant
     * external model
     */

    String getDocumentClassName();

    /**
     * Get the URI of the external object model as used in the JAXP factory interfaces for obtaining
     * an XPath implementation
     *
     * @return the URI used to identify this object model in the JAXP XPath factory mechanism.
     */

    String getIdentifyingURI();

    /**
     * Get a converter from XPath values to values in the external object model
     *
     * @param targetClass the required class of the result of the conversion. If this class represents
     *                    a node or list of nodes in the external object model, the method should return a converter that takes
     *                    a native node or sequence of nodes as input and returns a node or sequence of nodes in the
     *                    external object model representation. Otherwise, it should return null.
     * @return a converter, if the targetClass is recognized as belonging to this object model;
     *         otherwise null
     */

    PJConverter getPJConverter(Class<?> targetClass);

    /**
     * Get a converter from values in the external object model to XPath values.
     *
     * @param sourceClass the class (static or dynamic) of values to be converted
     * @param config the Saxon Configuration object
     * @return a converter, if the sourceClass is recognized as belonging to this object model;
     *         otherwise null
     */

    JPConverter getJPConverter(Class sourceClass, Configuration config);

    /**
     * Get a converter that converts a sequence of XPath nodes to this model's representation
     * of a node list.
     * <p><i>This method is primarily for the benefit of DOM, which uses its own NodeList
     * class to represent collections of nodes. Most other object models use standard
     * Java collection objects such as java.util.List</i></p>
     *
     * @param node an example of the kind of node used in this model
     * @return if the model does not recognize this node as one of its own, return null. Otherwise
     *         return a PJConverter that takes a list of XPath nodes (represented as NodeInfo objects) and
     *         returns a collection of nodes in this object model
     */

    PJConverter getNodeListCreator(Object node);

    /**
     * Test whether this object model recognizes a particular kind of JAXP Result object,
     * and if it does, return a Receiver that builds an instance of this data model from
     * a sequence of events. If the Result is not recognised, return null.
     *
     * @param result a JAXP result object
     * @return a Receiver that writes to that result, if available; or null otherwise
     * @throws net.sf.saxon.trans.XPathException
     *          if any failure occurs
     */

    Receiver getDocumentBuilder(Result result) throws XPathException;

    /**
     * Test whether this object model recognizes a particular kind of JAXP Source object,
     * and if it does, send the contents of the document to a supplied Receiver, and return true.
     * Otherwise, return false.
     *
     * @param source   a JAXP Source object
     * @param receiver the Receiver that is to receive the data from the Source. The caller
     *                 is responsible for opening and closing the receiver.
     * @return true if the data from the Source has been sent to the Receiver, false otherwise
     * @throws net.sf.saxon.trans.XPathException
     *          if any failure occurs
     */

    boolean sendSource(Source source, Receiver receiver) throws XPathException;

    /**
     * Wrap or unwrap a node using this object model to return the corresponding Saxon node. If the supplied
     * source does not belong to this object model, return null
     *
     * @param source a JAXP Source object
     * @param config the Saxon configuration
     * @return a NodeInfo corresponding to the Source, if this can be constructed; otherwise null
     */

    NodeInfo unravel(Source source, Configuration config);

}

