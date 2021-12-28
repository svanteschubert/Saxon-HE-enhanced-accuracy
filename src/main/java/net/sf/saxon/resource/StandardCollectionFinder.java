////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.resource;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.functions.URIQueryParameters;
import net.sf.saxon.lib.CollectionFinder;
import net.sf.saxon.lib.ResourceCollection;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of the CollectionFinder interface. The standard CollectionFinder recognizes four
 * types of collection:
 * <p>
 * <ol>
 *     <li>Any URI may be explicitly registered and associated with an instance of {@link ResourceCollection}</li>
 *     <li>If the file: URI scheme is used, and the relevant file identifies a directory, the directory
 *     is treated as a collection: it is returned as an instance of {@link DirectoryCollection}</li>
 *     <li>If the URI ends with ".jar" or ".zip", or more generally, if the method {@link #isJarFileURI(String)} returns
 *     true, the URI is treated as identifying a JAR or ZIP archive, whose contents form the
 *     resources in the collection: it is returned as an instance of {@link JarCollection}</li>
 *     <li>In all other cases, the URI is treated as the URI of an XML document listing the URIs
 *     of the resources in the collection, which are then retrieved using the {@link javax.xml.transform.URIResolver}</li>
 * </ol>
 */

public class StandardCollectionFinder implements CollectionFinder {

    private Map<String, ResourceCollection> registeredCollections = new HashMap<>(2);

    /**
     * Register a specific URI and bind it to a specific ResourceCollection
     * @param collectionURI the collection URI to be registered. Must not be null.
     * @param collection the ResourceCollection to be associated with this URI. Must not be null.
     */

    public void registerCollection(String collectionURI, ResourceCollection collection) {
        registeredCollections.put(collectionURI, collection);
    }

    /**
     * Locate the collection of resources corresponding to a collection URI.
     *
     * @param context       The XPath dynamic evaluation context
     * @param collectionURI The collection URI: an absolute URI, formed by resolving the argument
     *                      supplied to the fn:collection or fn:uri-collection against the static
     *                      base URI
     * @return a ResourceCollection object representing the resources in the collection identified
     * by this collection URI
     * @throws XPathException if the collection URI cannot be resolved to a collection
     */

    @Override
    public ResourceCollection findCollection(XPathContext context, String collectionURI) throws XPathException {
        checkNotNull(collectionURI, context);

        ResourceCollection registeredCollection = registeredCollections.get(collectionURI);
        if (registeredCollection != null) {
            return registeredCollection;
        }

        URIQueryParameters params = null;
        String query = null;

        URI relativeURI;
        try {
            relativeURI = new URI(ResolveURI.escapeSpaces(collectionURI));
            query = relativeURI.getQuery();
            if (query != null) {
                int q = collectionURI.indexOf('?');
                params = new URIQueryParameters(query, context.getConfiguration());
                collectionURI = ResolveURI.escapeSpaces(collectionURI.substring(0, q));
            }

        } catch (URISyntaxException e) {
            XPathException err = new XPathException("Invalid relative URI " + Err.wrap(collectionURI, Err.VALUE) + " passed to collection() function");
            err.setErrorCode("FODC0004");
            err.setXPathContext(context);
            throw err;
        }

        URI resolvedURI;
        try {
            resolvedURI = new URI(collectionURI);
        } catch (URISyntaxException e) {
            throw new XPathException(e);
        }

        if (!context.getConfiguration().getAllowedUriTest().test(resolvedURI)) {
            throw new XPathException("URI scheme '" + resolvedURI.getScheme() + "' has been disallowed");
        }

        if ("file".equals(resolvedURI.getScheme())) {
            File file = new File(resolvedURI);
            checkFileExists(file, resolvedURI, context);
            if (file.isDirectory()) {
                return new DirectoryCollection(context.getConfiguration(), collectionURI, file, params);
            }
        }

        // check if file is a zip file

        if (isJarFileURI(collectionURI)) {
            return new JarCollection(context, collectionURI, params);
        }

        // otherwise assume the URI identifies a collection catalog

        return new CatalogCollection(context.getConfiguration(), collectionURI);
    }

    /**
     * If the collectionURI is null, report that no default collection exists
     * @param collectionURI the collection URI to be tested
     * @param context XPath evaluation context
     * @throws XPathException if the collectionURI is null
     */

    public static void checkNotNull(String collectionURI, XPathContext context) throws XPathException {
        if (collectionURI == null) {
            XPathException err = new XPathException("No default collection has been defined");
            err.setErrorCode("FODC0002");
            err.setXPathContext(context);
            throw err;
        }
    }

    /**
     * Ask whether the collection URI should be interpreted as identifying a JAR (or ZIP) file.
     * This method is provided so that it can be overridden in subclasses. The default implementation
     * returns true if the collection URI ends with the extension ".jar" or ".zip", or if it
     * is a URI using the "jar" scheme.
     * @param collectionURI the requested absolute collection URI
     * @return true if the collection URI should be interpreted as a JAR or ZIP file
     */

    protected boolean isJarFileURI(String collectionURI) {
        return collectionURI.endsWith(".jar") ||
            collectionURI.endsWith(".zip") ||
            collectionURI.startsWith("jar:");
    }


    public static void checkFileExists(File file, URI resolvedURI, XPathContext context) throws XPathException {
        if (!file.exists()) {
            XPathException err = new XPathException("The file or directory " + resolvedURI + " does not exist");
            err.setErrorCode("FODC0002");
            err.setXPathContext(context);
            throw err;
        }
    }
}
