////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.Platform;
import net.sf.saxon.Version;
import net.sf.saxon.event.FilterFactory;
import net.sf.saxon.event.IDFilter;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.functions.EncodeForUri;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.functions.URIQueryParameters;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.resource.BinaryResource;
import net.sf.saxon.resource.DataURIScheme;
import net.sf.saxon.resource.UnparsedTextResource;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.Maker;
import net.sf.saxon.trans.NonDelegatingURIResolver;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Predicate;


/**
 * This class provides the service of converting a URI into an {@link Source}.
 * It is used to get stylesheet modules referenced by xsl:import and xsl:include,
 * and source documents referenced by the document() function. The standard version
 * handles anything that the java URL class will handle, plus the <code>classpath</code>
 * URI scheme defined in the Spring framework, and the <code>data</code> URI scheme defined in
 * RFC 2397.
 * <p>You can write a subclass to handle other kinds of URI, for example references to data in
 * a database, or to handle standard URIs in non-standard ways, for example by supplying
 * authentication credentials.</p>
 */

public class StandardURIResolver implements NonDelegatingURIResolver {

    /*@Nullable*/ private Configuration config = null;
    private boolean recognizeQueryParameters = false;

    private Predicate<URI> allowedUriTest = null;

    /**
     * Create a StandardURIResolver, with no reference to a Configuration.
     * Note: it is preferable but not essential to supply a Configuration, either in the constructor
     * or in a subsequent call of <code>setConfiguration()</code>
     */

    public StandardURIResolver() {
        this(null);
    }

    /**
     * Create a StandardURIResolver, with a reference to a Configuration
     *
     * @param config The Configuration object. May be null.
     *               This is used (if available) to get a reusable SAX Parser for a source XML document
     */

    public StandardURIResolver(/*@Nullable*/ Configuration config) {
        this.config = config;
    }

    /**
     * Indicate that query parameters (such as validation=strict) are to be recognized
     *
     * @param recognize Set to true if query parameters in the URI are to be recognized and acted upon.
     *                  The default (for compatibility and interoperability reasons) is false.
     */

    public void setRecognizeQueryParameters(boolean recognize) {
        recognizeQueryParameters = recognize;
    }

    /**
     * Determine whether query parameters (such as validation=strict) are to be recognized
     *
     * @return true if query parameters are recognized and interpreted by Saxon.
     */

    public boolean queryParametersAreRecognized() {
        return recognizeQueryParameters;
    }

    /**
     * Set a Predicate that is applied to a URI to determine whether the resolver should accept it.
     *
     * <p>It is possible to set the default predicate by means of the configuration property
     * {@link Feature#ALLOWED_PROTOCOLS}.</p>
     *
     * @param test the predicate to be applied to the absolute URI.
     */

    public void setAllowedUriTest(Predicate<URI> test) {
        this.allowedUriTest = test;
    }

    /**
     * Set a Predicate that is applied to a URI to determine whether the resolver should accept it.
     *
     * <p>The default predicate can be set by means of the configuration property
     * {@link Feature#ALLOWED_PROTOCOLS}.</p>
     */

    public Predicate<URI> getAllowedUriTest() {
        return allowedUriTest == null
                ? config == null ? uri -> true : config.getAllowedUriTest()
                : allowedUriTest;
    }

    /**
     * Get the relevant platform
     *
     * @return the platform
     */

    protected Platform getPlatform() {
        return Version.platform;
    }

    /**
     * Set the configuration
     *
     * @param config the configuration
     */

    public void setConfiguration(Configuration config) {
        this.config = config;
    }

    /**
     * Get the configuration if available
     *
     * @return the configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Resolve a URI
     *
     * @param href The relative or absolute URI. May be an empty string. May contain
     *             a fragment identifier starting with "#", which must be the value of an ID attribute
     *             in the referenced XML document.
     * @param base The base URI that should be used. May be null if uri is absolute.
     * @return a Source object representing an XML document
     */

    @Override
    public Source resolve(String href, String base)
            throws XPathException {

        if (config != null && config.isTiming()) {
            assert config != null;
            config.getLogger().info("URIResolver.resolve href=\"" + href + "\" base=\"" + base + "\"");
        }
        // System.err.println("StandardURIResolver, href=" + href + ", base=" + base);

        String relativeURI = href;
        String id = null;

        // Extract any fragment identifier. Note, this code is no longer used to
        // resolve fragment identifiers in URI references passed to the document()
        // function: the code of the document() function handles these itself.

        int hash = href.indexOf('#');
        if (hash >= 0) {
            relativeURI = href.substring(0, hash);
            id = href.substring(hash + 1);
            // System.err.println("StandardURIResolver, href=" + href + ", id=" + id);
        }

        URIQueryParameters params = null;
        URI uri;
        URI relative;
        try {
            relativeURI = ResolveURI.escapeSpaces(relativeURI);
            relative = new URI(relativeURI);
        } catch (URISyntaxException err) {
            throw new XPathException("Invalid relative URI " + Err.wrap(relativeURI), err);
        }

        String query = relative.getQuery();
        if (query != null && recognizeQueryParameters) {
            params = new URIQueryParameters(query, config);
            int q = relativeURI.indexOf('?');
            relativeURI = relativeURI.substring(0, q);
        }

        Source source = null;
        if (recognizeQueryParameters && relativeURI.endsWith(".ptree")) {
            throw new UnsupportedOperationException("PTree files are no longer supported (from Saxon 10.0)");
        }

        try {
            uri = ResolveURI.makeAbsolute(relativeURI, base);
        } catch (URISyntaxException err) {
            // System.err.println("Recovering from " + err);
            // last resort: if the base URI is null, or is itself a relative URI, we
            // try to expand it relative to the current working directory
            String expandedBase = ResolveURI.tryToExpand(base);
            if (!expandedBase.equals(base)) { // prevent infinite recursion
                return resolve(href, expandedBase);
            }
            //err.printStackTrace();
            throw new XPathException("Invalid URI " + Err.wrap(relativeURI) + " - base " + Err.wrap(base), err);
        }

        if (!getAllowedUriTest().test(uri)) {
            throw new XPathException("URI '" + uri.toString() + "' has been disallowed", "FODC0002");
        }

        // Check that any "%" sign in the URI is part of a well-formed percent-encoded UTF-8 character.
        // Without this check, dereferencing the resulting URL can fail with arbitrary unchecked exceptions

        final String uriString = uri.toString();
        EncodeForUri.checkPercentEncoding(uriString);

        // Handle a URI using the data: URI scheme
        if ("data".equals(uri.getScheme())) {
            Resource resource;
            try {
                resource = DataURIScheme.decode(uri);
            } catch (IllegalArgumentException e) {
                throw new XPathException("Invalid URI using 'data' scheme: " + e.getMessage());
            }
            if (resource instanceof BinaryResource) {
                byte[] contents  = ((BinaryResource)resource).getData();
                InputSource is = new InputSource(new ByteArrayInputStream(contents));
                source = new SAXSource(is);
                source.setSystemId(uriString);
            } else {
                assert resource instanceof UnparsedTextResource;
                Reader reader = new StringReader(((UnparsedTextResource) resource).getContent());
                source = new SAXSource(new InputSource(reader));
                source.setSystemId(uriString);
            }
        } else {
            source = new SAXSource();
            setSAXInputSource((SAXSource) source, uriString);
        }

        if (params != null) {
            Maker<XMLReader> parser = params.getXMLReaderMaker();
            if (parser != null) {
                ((SAXSource) source).setXMLReader(parser.make());
            }
        }

        if (((SAXSource) source).getXMLReader() == null) {
            if (config == null) {
                try {
                    ((SAXSource) source).setXMLReader(Version.platform.loadParser());
                } catch (Exception err) {
                    throw new XPathException(err);
                }
            } else {
                //((SAXSource)source).setXMLReader(config.getSourceParser());
                // Leave the Sender to allocate an XMLReader, so that it can be returned to the pool after use
            }
        }

        if (params != null) {
            SpaceStrippingRule stripSpace = params.getSpaceStrippingRule();
            source = AugmentedSource.makeAugmentedSource(source);
            ((AugmentedSource) source).getParseOptions().setSpaceStrippingRule(stripSpace);
        }

        if (id != null) {
            final String idFinal = id;
            FilterFactory factory = new FilterFactory() {
                @Override
                public ProxyReceiver makeFilter(Receiver next) {
                    return new IDFilter(next, idFinal);
                }
            };
            source = AugmentedSource.makeAugmentedSource(source);
            ((AugmentedSource) source).addFilter(factory);
        }

        if (params != null) {
            Integer validation = params.getValidationMode();
            if (validation != null) {
                source = AugmentedSource.makeAugmentedSource(source);
                ((AugmentedSource) source).setSchemaValidationMode(validation);
            }
        }

        if (params != null) {
            Boolean xinclude = params.getXInclude();
            if (xinclude != null) {
                source = AugmentedSource.makeAugmentedSource(source);
                ((AugmentedSource) source).setXIncludeAware(xinclude.booleanValue());
            }
        }

        return source;
    }

    /**
     * Handle a PTree source file (Saxon-EE only)
     *
     * @param href the relative URI
     * @param base the base URI
     * @return the new Source object
     */

    protected Source getPTreeSource(String href, String base) throws XPathException {
        throw new XPathException("PTree files can only be read using a Saxon-EE configuration");
    }

    /**
     * Set the InputSource part of the returned SAXSource. This is done in a separate
     * method to allow subclassing. The default implementation checks for the (Spring-defined)
     * "classpath" URI scheme, and if this is in use, it attempts to locate the resource on the
     * classpath and set the supplied SAXSource to use the corresponding input stream.
     * In other cases it simply places the URI in the
     * InputSource, allowing the XML parser to take responsibility for the dereferencing.
     * A subclass may choose to dereference the URI at this point and place an InputStream
     * in the SAXSource.
     *
     * @param source    the SAXSource being initialized
     * @param uriString the absolute (resolved) URI to be used
     */

    protected void setSAXInputSource(SAXSource source, String uriString) {
        if (uriString.startsWith("classpath:") && uriString.length() > 10) {
            InputStream is = getConfiguration().getDynamicLoader().getResourceAsStream(uriString.substring(10));
            if (is != null) {
                source.setInputSource(new InputSource(is));
                source.setSystemId(uriString);
                return;
            }
        }
        source.setInputSource(new InputSource(uriString));
        source.setSystemId(uriString);
    }

}

