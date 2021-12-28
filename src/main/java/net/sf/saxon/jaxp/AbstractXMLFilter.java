////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.jaxp;

import org.xml.sax.*;
import org.xml.sax.ext.LexicalHandler;

import java.io.IOException;


/**
 * <B>AbstractFilterImpl</B> is skeletal implementation of the SAX XMLFilter interface, used
 * for both streaming and non-streaming filters.
 *
 * @author Michael H. Kay
 */

public abstract class AbstractXMLFilter implements XMLFilter {

    XMLReader parser;
    ContentHandler contentHandler;      // destination for output of this filter
    LexicalHandler lexicalHandler;      // destination for output of this filter


    //////////////////////////////////////////////////////////////////
    // Implement XMLFilter interface methods
    //////////////////////////////////////////////////////////////////

    /**
     * Set the parent reader.
     * <p>This method allows the application to link the filter to
     * a parent reader (which may be another filter).  The argument
     * may not be null.</p>
     *
     * @param parent The parent reader (the supplier of SAX events).
     */

    @Override
    public void setParent(XMLReader parent) {
        parser = parent;
    }

    /**
     * Get the parent reader.
     * <p>This method allows the application to query the parent
     * reader (which may be another filter).  It is generally a
     * bad idea to perform any operations on the parent reader
     * directly: they should all pass through this filter.</p>
     *
     * @return The parent filter, or null if none has been set.
     */

    @Override
    public XMLReader getParent() {
        return parser;
    }

    ///////////////////////////////////////////////////////////////////
    // implement XMLReader interface methods
    ///////////////////////////////////////////////////////////////////

    /**
     * Look up the value of a feature.
     * <p>The feature name is any fully-qualified URI.  It is
     * possible for an XMLReader to recognize a feature name but
     * to be unable to return its value; this is especially true
     * in the case of an adapter for a SAX1 Parser, which has
     * no way of knowing whether the underlying parser is
     * performing validation or expanding external entities.</p>
     * <p>All XMLReaders are required to recognize the
     * http://xml.org/sax/features/namespaces and the
     * http://xml.org/sax/features/namespace-prefixes feature names.</p>
     *
     * @param name The feature name, which is a fully-qualified URI.
     * @return The current state of the feature (true or false).
     * @throws SAXNotRecognizedException
     *          When the
     *          XMLReader does not recognize the feature name.
     * @throws SAXNotSupportedException
     *          When the
     *          XMLReader recognizes the feature name but
     *          cannot determine its value at this time.
     * @see #setFeature
     */

    @Override
    public boolean getFeature(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        return parser.getFeature(name);
    }


    /**
     * Set the state of a feature.
     * <p>The feature name is any fully-qualified URI.  It is
     * possible for an XMLReader to recognize a feature name but
     * to be unable to set its value</p>
     * <p>All XMLReaders are required to support setting
     * http://xml.org/sax/features/namespaces to true and
     * http://xml.org/sax/features/namespace-prefixes to false.</p>
     * <p>Some feature values may be immutable or mutable only
     * in specific contexts, such as before, during, or after
     * a parse.</p>
     *
     * @param name  The feature name, which is a fully-qualified URI.
     * @param value The requested state of the feature (true or false).
     * @throws SAXNotRecognizedException
     *          When the
     *          XMLReader does not recognize the feature name.
     * @throws SAXNotSupportedException
     *          When the
     *          XMLReader recognizes the feature name but
     *          cannot set the requested value.
     * @see #getFeature
     */

    @Override
    public void setFeature(String name, boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        parser.setFeature(name, value);
    }

    /**
     * Look up the value of a property.
     * <p>The property name is any fully-qualified URI.  It is
     * possible for an XMLReader to recognize a property name but
     * to be unable to return its state.</p>
     * <p>XMLReaders are not required to recognize any specific
     * property names, though an initial core set is documented for
     * SAX2.</p>
     * <p>Some property values may be available only in specific
     * contexts, such as before, during, or after a parse.</p>
     * <p>Implementors are free (and encouraged) to invent their own properties,
     * using names built on their own URIs.</p>
     *
     * @param name The property name, which is a fully-qualified URI.
     * @return The current value of the property.
     * @throws SAXNotRecognizedException
     *          When the
     *          XMLReader does not recognize the property name.
     * @see #setProperty
     */

    @Override
    public Object getProperty(String name)
            throws SAXNotRecognizedException {
        if (name.equals("http://xml.org/sax/properties/lexical-handler")) {
            return lexicalHandler;
        } else {
            throw new SAXNotRecognizedException(name);
        }
    }


    /**
     * Set the value of a property.
     * <p>The property name is any fully-qualified URI.  It is
     * possible for an XMLReader to recognize a property name but
     * to be unable to set its value.</p>
     * <p>XMLReaders are not required to recognize setting
     * any specific property names, though a core set is provided with
     * SAX2.</p>
     * <p>Some property values may be immutable or mutable only
     * in specific contexts, such as before, during, or after
     * a parse.</p>
     * <p>This method is also the standard mechanism for setting
     * extended handlers.</p>
     *
     * @param name  The property name, which is a fully-qualified URI.
     * @param value The requested value for the property.
     * @throws SAXNotRecognizedException
     *          When the
     *          XMLReader does not recognize the property name.
     * @throws SAXNotSupportedException
     *          When the
     *          XMLReader recognizes the property name but
     *          cannot set the requested value.
     */

    @Override
    public void setProperty(String name, Object value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals("http://xml.org/sax/properties/lexical-handler")) {
            if (value instanceof LexicalHandler) {
                lexicalHandler = (LexicalHandler) value;
            } else {
                throw new SAXNotSupportedException(
                        "Lexical Handler must be instance of org.xml.sax.ext.LexicalHandler");
            }
        } else {
            throw new SAXNotRecognizedException(name);
        }
    }

    /**
     * Register a content handler to receive the output of the transformation
     * filter. If the content handler is also a LexicalHandler, and if no LexicalHandler
     * is separately registered, the ContentHandler will also act as the LexicalHandler
     */

    @Override
    public void setContentHandler(ContentHandler handler) {
        contentHandler = handler;
        if (handler instanceof LexicalHandler && lexicalHandler == null) {
            lexicalHandler = (LexicalHandler) handler;
        }
    }

    /**
     * Get the ContentHandler registered using setContentHandler()
     */

    @Override
    public ContentHandler getContentHandler() {
        return contentHandler;
    }


    /**
     * Allow an application to register an entity resolver.
     * <p>If the application does not register an entity resolver,
     * the XMLReader will perform its own default resolution.</p>
     * <p>Applications may register a new or different resolver in the
     * middle of a parse, and the SAX parser must begin using the new
     * resolver immediately.</p>
     *
     * @param resolver The entity resolver.
     * @throws NullPointerException If the resolver
     *                                        argument is null.
     * @see #getEntityResolver
     */

    @Override
    public void setEntityResolver(EntityResolver resolver) {
        // XSLT output does not use entities, so the resolver is never used
    }


    /**
     * Return the current entity resolver.
     *
     * @return Always null, since no entity resolver is used even if one
     *         is supplied.
     * @see #setEntityResolver
     */

    /*@Nullable*/
    @Override
    public EntityResolver getEntityResolver() {
        return null;
    }


    /**
     * Allow an application to register a DTD event handler.
     * <p>If the application does not register a DTD handler, all DTD
     * events reported by the SAX parser will be silently ignored.</p>
     * <p>Applications may register a new or different handler in the
     * middle of a parse, and the SAX parser must begin using the new
     * handler immediately.</p>
     *
     * @param handler The DTD handler.
     * @throws NullPointerException If the handler
     *                                        argument is null.
     * @see #getDTDHandler
     */

    @Override
    public void setDTDHandler(DTDHandler handler) {
        // XSLT output does not include a DTD
    }


    /**
     * Return the current DTD handler.
     *
     * @return Always null, since no DTD handler is used even if one has been
     *         supplied.
     * @see #setDTDHandler
     */

    /*@Nullable*/
    @Override
    public DTDHandler getDTDHandler() {
        return null;
    }


    /**
     * Allow an application to register an error event handler.
     * <p>If the application does not register an error handler, all
     * error events reported by the SAX parser will be silently
     * ignored; however, normal processing may not continue.  It is
     * highly recommended that all SAX applications implement an
     * error handler to avoid unexpected bugs.</p>
     * <p>Applications may register a new or different handler in the
     * middle of a parse, and the SAX parser must begin using the new
     * handler immediately.</p>
     *
     * @param handler The error handler.
     * @throws NullPointerException If the handler
     *                                        argument is null.
     * @see #getErrorHandler
     */

    @Override
    public void setErrorHandler(ErrorHandler handler) {
        // No effect
    }

    /**
     * Return the current error handler.
     *
     * @return The current error handler, or null if none
     *         has been registered.
     * @see #setErrorHandler
     */
    /*@Nullable*/
    @Override
    public ErrorHandler getErrorHandler() {
        return null;
    }

    /**
     * Parse (that is, transform) an XML document given a system identifier (URI).
     * <p>This method is a shortcut for the common case of reading a
     * document from a system identifier.  It is the exact
     * equivalent of the following:</p>
     * <pre>
     * parse(new InputSource(systemId));
     * </pre>
     * <p>If the system identifier is a URL, it must be fully resolved
     * by the application before it is passed to the parser.</p>
     *
     * @param systemId The system identifier (URI).
     * @throws SAXException Any SAX exception, possibly
     *                                  wrapping another exception.
     * @throws IOException      An IO exception from the parser,
     *                                  possibly from a byte stream or character stream
     *                                  supplied by the application.
     * @see #parse(InputSource)
     */

    @Override
    public void parse(String systemId) throws IOException, SAXException {
        InputSource input = new InputSource(systemId);
        parse(input);
    }

}

