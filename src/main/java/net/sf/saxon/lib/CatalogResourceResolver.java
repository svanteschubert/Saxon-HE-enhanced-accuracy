////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.resource.TypedStreamSource;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;
import org.xmlresolver.*;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.Collections;

/**
 * The standard implementation of the {@link ResourceResolver} interface for use with catalogs.
 * @since 11
 */

public class CatalogResourceResolver implements
        ResourceResolver, ConfigurableResourceResolver,
        EntityResolver, EntityResolver2 {

    private org.xmlresolver.Resolver catalogBasedResolver;

    /**
     * Creates a new CatalogResourceResolver resolver with a default {@link org.xmlresolver.XMLResolverConfiguration}.
     * This default configuration will read a configuration file from the class path
     * and interrogate system properties to determine its initial configuration.
     *
     * The configuration can be updated by calling {@link #setFeature}. Alternatively,
     * the constructor can be passed an existing resolver.
     */
    public CatalogResourceResolver() {
        // We explicitly pass in an empty list so that in the absence of any other configuration,
        // the resolver doesn't use ./catalog.xml as a default. Historically, Saxon didn't use a
        // resolver unless a catalog was specified, so this default could have unexpected consequences.
        catalogBasedResolver = new Resolver(new XMLResolverConfiguration(null, Collections.emptyList()));
        catalogBasedResolver.getConfiguration().setFeature(ResolverFeature.THROW_URI_EXCEPTIONS, true);
    }

    /**
     * Creates a new CatlaogResourceResolver using the provided resolver as its underlying resolver.
     * @param resolver The resolver to wrap.
     */
    public CatalogResourceResolver(Resolver resolver) {
        this.catalogBasedResolver = resolver;
    }

    /**
     * Calls {@link org.xmlresolver.XMLResolverConfiguration#getFeature} on the underlying
     * resolver configuration.
     * @param feature The feature setting
     * @param <T> The feature type
     * @return The value for the specified feature.
     */
    public <T> T getFeature(ResolverFeature<T> feature) {
        if (catalogBasedResolver == null) {
            return null;
        }
        return catalogBasedResolver.getConfiguration().getFeature(feature);
    }

    /**
     * Calls {@link org.xmlresolver.XMLResolverConfiguration#setFeature} on the underlying
     * resolver configuration.
     * @param feature The feature setting
     * @param value The desired value for that feature
     * @param <T> The feature type
     * @throws NullPointerException if the underlying resolver is null. Some features will
     * also throw this exception if the value provided is null and that's not a meaningful
     * feature value.
     */
    public <T> void setFeature(ResolverFeature<T> feature, T value) {
        if (catalogBasedResolver == null) {
            throw new NullPointerException();
        }
        catalogBasedResolver.getConfiguration().setFeature(feature, value);
    }

    /**
     * Resolve a resource request.
     *
     * If catalog resolution fails and a fallback URI resolver has been chained, the
     * fallback resolver will be attempted.
     *
     * @param request details of the resource request
     * @return The resolved resource, or null if it could not be resolved.
     * @throws XPathException if an error occurs during the attempt to resolve the URI.
     */
    @Override
    public Source resolve(ResourceRequest request) throws XPathException {
        if (catalogBasedResolver != null) {
            CatalogResolver cr = catalogBasedResolver.getCatalogResolver();
            ResolvedResource rr;
            if (request.uriIsNamespace) {
                try {
                    rr = cr.resolveNamespace(
                            request.uri, request.baseUri, request.nature, request.purpose);
                    if (rr == null) {
                        return null;
                    }

                } catch (IllegalArgumentException e) {
                    throw new XPathException("Exception from catalog resolver resolveNamespace(): ", e);
                }
            } else if (ResourceRequest.DTD_NATURE.equals(request.nature)) {
                return null;
            } else if (ResourceRequest.EXTERNAL_ENTITY_NATURE.equals(request.nature)) {
                try {
                    rr = cr.resolveEntity(
                            request.entityName, request.publicId, request.baseUri, request.uri);
                } catch (IllegalArgumentException e) {
                    throw new XPathException("Exception from catalog resolver resolveEntity():", e);
                }
            } else {
                String href = request.relativeUri == null ? request.uri : request.relativeUri;
                String baseUri = request.baseUri == null ? request.uri : request.baseUri;
                try {
                    rr = cr.resolveURI(href, baseUri);
                } catch (IllegalArgumentException e) {
                    throw new XPathException("Exception from catalog resolver resolverURI()", e);
                }
            }
            if (rr != null) {
                TypedStreamSource result = new TypedStreamSource();
                result.setSystemId(rr.getResolvedURI().toString());
                result.setInputStream(rr.getInputStream());
                result.setContentType(rr.getContentType());
                return result;
            }
        }
        return null;
    }

    /**
     * Resolves an external subset. This method is part of the {@link org.xml.sax.ext.EntityResolver2} interface.
     * The resolver will attempt to find the external subset through the catalog resolver.
     *
     * If catalog resolution fails and a fallback EntityResolver2 resolver has been chained, the
     * fallback resolver will be attempted.
     *
     * @param name The doctype name.
     * @param baseURI The base URI.
     * @return The external subset, or null if it could not be found.
     * @throws SAXException If an error occurs during the attempt to resolve the external subset.
     * @throws IOException If it isn't possible to create the input source or if the base URI is invalid.
     */
    @Override
    public InputSource getExternalSubset(String name, String baseURI) throws SAXException, IOException {
        InputSource result = null;
        if (catalogBasedResolver != null) {
            result = catalogBasedResolver.getExternalSubset(name, baseURI);
        }
//        if (result == null && nextEntityResolver2 != null) {
//            result = nextEntityResolver2.getExternalSubset(name, baseURI);
//        }
        return result;
    }

    /**
     * Resolves an entity. This method attempts to resolve the entity with the catalog resolver.
     *
     * If catalog resolution fails and a fallback EntityResolver2 resolver has been chained, the
     * fallback resolver will be attempted. If that fails and a further EntityResolver has been chained,
     * that fallback will also be attempted.
     *
     * Depending on whether various aspects of the entity are provided (public and system identifiers,
     * the name and baseURI, etc.), different aspects of the catalog will be queried. Not all parsers
     * provide all of these parameters. It's common for the name and baseURI to be null, for example.
     * If the parser doesn't provide them, then the catalog resolver will not be able to resolve with them.
     *
     * @param name The name of the entity, often null
     * @param publicId The public identifier of the entity, often null
     * @param baseURI The base URI of the entity, often null
     * @param systemId The system identifier of the entity
     * @return The entity, or null if it could not be found.
     * @throws SAXException If an error occurs during the attempt to resolve the external subset.
     * @throws IOException If it isn't possible to create the input source or if the base URI is invalid.
     */
    @Override
    public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) throws SAXException, IOException {
        try {
            InputSource result = null;
            if (catalogBasedResolver != null) {
                result = catalogBasedResolver.resolveEntity(name, publicId, baseURI, systemId);
            }
//            if (result == null && nextEntityResolver2 != null) {
//                result = nextEntityResolver2.resolveEntity(name, publicId, baseURI, systemId);
//            }
//            if (result == null && nextEntityResolver != null && nextEntityResolver != nextEntityResolver2) {
//                result = nextEntityResolver.resolveEntity(publicId, systemId);
//            }
            return result;
        } catch (IllegalArgumentException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException)e.getCause();
            } else {
                throw new SAXException(e);
            }
        }
    }

    /**
     * Resolves an entity. This method attempts to resolve the entity with the catalog resolver.
     *
     * If catalog resolution fails and a fallback EntityResolver2 resolver has been chained, the
     * fallback resolver will be attempted. If that fails and a further EntityResolver has been chained,
     * that fallback will also be attempted.
     *
     * @param publicId The public identifier of the entity, often null
     * @param systemId The system identifier of the entity
     * @return The entity, or null if it could not be found.
     * @throws SAXException If an error occurs during the attempt to resolve the external subset.
     * @throws IOException If it isn't possible to create the input source or if the base URI is invalid.
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        InputSource result = null;
        if (catalogBasedResolver != null) {
            result = catalogBasedResolver.resolveEntity(publicId, systemId);
        }
//        if (result == null && nextEntityResolver2 != null) {
//            result = nextEntityResolver2.resolveEntity(publicId, systemId);
//        }
//        if (result == null && nextEntityResolver != null && nextEntityResolver != nextEntityResolver2) {
//            result = nextEntityResolver.resolveEntity(publicId, systemId);
//        }
        return result;
    }

    /**
     * Resolves a URI that is known to be a namespace URI.
     *
     * This intereface allows a resolver to request a particular kind of resource (one with a particular nature,
     * possibly for a particular purpose) for a URI. The URI is usually the namespace URI. Namespace URIs are
     * often not usefully resolvable on the web, but a catalog resolver can still offer resolution.
     *
     * If neither a nature or a purpose are provided, or if using them produces no results, this method
     * simply attempts to lookup the URI in the catalog. If that also fails, and if a fallback namespace
     * resolver has been chained, resolution will be attempted with the fallback resolver.
     *
     * @param uri The namespace URI
     * @param nature The nature of the resource requested, for example, the URI of the media type
     * @param purpose The purpose of the request, for example "validation"
     * @return The resource or null if it could not be found.
     * @throws TransformerException if an error occurs during the attempt to resolve the URI.
     */
    //@Override
    public Source resolveNamespace(String uri, String nature, String purpose) throws TransformerException {
        Source result = null;
        if (catalogBasedResolver != null) {
            result = catalogBasedResolver.resolveNamespace(uri, nature, purpose);
        }
//        if (catalogBasedResolver == null && nextNamespaceResolver != null) {
//            result = nextNamespaceResolver.resolveNamespace(uri, nature, purpose);
//        }
        return result;
    }
}
