////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.URIQueryParameters;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.query.InputStreamMarker;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.trans.Maker;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingIncident;
import org.xml.sax.XMLReader;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Optional;

/**
 * AbstractCollection is an abstract superclass for the various implementations
 * of ResourceCollection within Saxon. It provides common services such as
 * mapping of file extensions to MIME types, and mapping of MIME types to
 * resource factories.
 */
public abstract class AbstractResourceCollection implements ResourceCollection {

    protected Configuration config;
    protected String collectionURI;
    protected URIQueryParameters params = null;
    protected boolean noExceptions = false;


    /**
     * Create a resource collection
     * @param config the Saxon configuration
     */

    public AbstractResourceCollection(Configuration config) {
        this.config = config;
    }

    /**
     * If the collectionURI is null, report that no default collection exists
     * @param collectionURI the collection URI to be tested
     * @param context XPath evaluation context
     * @throws XPathException if the collectionURI is null
     */

    public static void checkNotNull(String collectionURI, XPathContext context) throws XPathException {
        if (collectionURI == null) {
            throw new XPathException("No default collection has been defined")
                    .withErrorCode("FODC0002")
                    .withXPathContext(context);
        }
    }

    @Override
    public String getCollectionURI() {
        return collectionURI;
    }

    /**
     * Ask whether the collection is stable. This method should only be called after
     * calling {@link #getResources(XPathContext)} or {@link #getResourceURIs(XPathContext)}
     *
     * @param context the XPath evaluation context.
     * @return true if the collection is defined to be stable, that is, if a subsequent call
     * on collection() with the same URI is guaranteed to return the same result. The method returns
     * true if the query parameter stable=yes is present in the URI, or if the configuration property
     * {@link FeatureKeys#STABLE_COLLECTION_URI} is set.
     */

    @Override
    public boolean isStable(XPathContext context) {
        if (params == null) {
            return false;
        }
        Optional<Boolean> stable = params.getStable();
        if (!stable.isPresent()) {
            return context.getConfiguration().getBooleanProperty(Feature.STABLE_COLLECTION_URI);
        } else {
            return stable.get();
        }
    }

    /**
     * Associate a media type with a resource factory.
     * Since 9.7.0.6 this registers the content type with the configuration, making the register
     * of content types more accessible to applications.
     * @param contentType a media type or MIME type, for example application/xsd+xml
     * @param factory a ResourceFactory used to parse (or otherwise process) resources of that type
     */

    public void registerContentType(String contentType, ResourceFactory factory) {
        config.registerMediaType(contentType, factory);
    }

    /**
     * Analyze URI query parameters and convert them to a set of parser options
     *
     * @param params  the query parameters extracted from the URI
     * @param context the XPath evaluation context
     * @return a set of options to control document parsing
     */

    @SuppressWarnings("OptionalIsPresent")
    protected ParseOptions optionsFromQueryParameters(URIQueryParameters params, XPathContext context) {
        ParseOptions options = context.getConfiguration().getParseOptions();

        if (params != null) {
            Optional<Integer> v = params.getValidationMode();
            if (v.isPresent()) {
                options = options.withSchemaValidationMode(v.get());
            }

            Optional<Boolean> xInclude = params.getXInclude();
            if (xInclude.isPresent()) {
                options = options.withXIncludeAware(xInclude.get());
            }

            Optional<SpaceStrippingRule> stripSpace = params.getSpaceStrippingRule();
            if (stripSpace.isPresent()) {
                options = options.withSpaceStrippingRule(stripSpace.get());
            }

            Optional<Maker<XMLReader>> p = params.getXMLReaderMaker();
            if (p.isPresent()) {
                options = options.withXMLReaderMaker(p.get());
            }

            // If the URI requested suppression of errors, or that errors should be treated
            // as warnings, we set up a special ErrorListener to achieve this

            int onError = URIQueryParameters.ON_ERROR_FAIL;
            if (params.getOnError().isPresent()) {
                onError = params.getOnError().get();
            }
            final Controller controller = context.getController();

            final ErrorReporter oldErrorReporter =
                    controller == null ? context.getConfiguration().makeErrorReporter() : controller.getErrorReporter();

            options = setupErrorHandlingForCollection(options, onError, oldErrorReporter);
        }
        return options;
    }

    public static ParseOptions setupErrorHandlingForCollection(ParseOptions options, int onError, ErrorReporter oldErrorReporter) {
        if (onError == URIQueryParameters.ON_ERROR_IGNORE) {
            options = options.withErrorReporter(new ErrorSuppressor());
        } else if (onError == URIQueryParameters.ON_ERROR_WARNING) {
            options = options.withErrorReporter(new ErrorAsWarningReporter(oldErrorReporter));
        }
        return options;
    }

    private static class ErrorSuppressor implements ErrorReporter {
        @Override
        public void report(XmlProcessingError error) {}
    }

    private static class ErrorAsWarningReporter implements ErrorReporter {
        private final ErrorReporter originalErrorReporter;
        public ErrorAsWarningReporter(ErrorReporter originalErrorReporter) {
            this.originalErrorReporter = originalErrorReporter;
        }
        @Override
        public void report(XmlProcessingError error) {
            if (error.isWarning()) {
                originalErrorReporter.report(error);
            } else {
                originalErrorReporter.report(error.asWarning());
                XmlProcessingIncident supp = new XmlProcessingIncident("The document will be excluded from the collection",
                                                                       SaxonErrorCode.SXWN9050).asWarning();
                supp.setLocation(error.getLocation());
                originalErrorReporter.report(supp);
            }
        }
    }

    /**
     * Information about a resource
     */

    public static class InputDetails {

        /**
         * The URI of the resource
         */
        public String resourceUri;
        /**
         * The binary content of the resource
         */
        public byte[] binaryContent;
        /**
         * The character content of the resource
         */
        public String characterContent;
        /**
         * The media type of the resource
         */
        public String contentType;
        /**
         * The encoding of the resource (if it is text, represented in binary)
         */
        public String encoding;
        /**
         * Options for parsing the content of an XML resource
         */
        public ParseOptions parseOptions;
        /**
         * Action to be taken in the event of an error, for example {@code URIQueryParameters#ON_ERROR_FAIL},
         * {@code URIQueryParameters#ON_ERROR_WARNING}, or {@code URIQueryParameters#ON_ERROR_IGNORE}
         */
        public int onError = URIQueryParameters.ON_ERROR_FAIL;

        /**
         * Get an input stream that delivers the binary content of the resource
         *
         * @return the content, as an input stream
         * @throws IOException if the input cannot be read
         */
        public InputStream getInputStream(Configuration config) throws IOException {
            return ResourceLoader.urlStream(config, resourceUri);
        }

        /**
         * Get the binary content of the resource, either as stored,
         * or by encoding the character content, or by reading the input stream
         *
         * @return the binary content of the resource
         * @throws XPathException if the binary content cannot be obtained
         */

        public byte[] obtainBinaryContent(Configuration config) throws XPathException {
            if (binaryContent != null) {
                return binaryContent;
            } else if (characterContent != null) {
                String e = encoding != null ? encoding : "UTF-8";
                return BinaryResource.encode(characterContent, e);
            } else {
                try (InputStream stream = getInputStream(config)) {
                    return BinaryResource.readBinaryFromStream(stream, resourceUri);
                } catch (IOException e) {
                    throw new XPathException(e);
                }
            }
        }

        /**
         * Get the character content of the resource, either as stored, or by
         * reading and decoding the input stream
         *
         * @return the character content of the resource, or null if the resource cannot be read and
         * errors are suppressed.
         * @throws XPathException in the event of a failure, if the default setting on-error=fail is used;
         * otherwise, return null in the event of a failure.
         */

        public String obtainCharacterContent(Configuration config) throws XPathException {
            if (characterContent != null) {
                return characterContent;
            } else if (binaryContent != null && encoding != null) {
                return BinaryResource.decode(binaryContent, encoding);
            } else {
                try {
                    InputStream stream = getInputStream(config);
                    String enc = encoding;
                    if (enc == null) {
                        stream = InputStreamMarker.ensureMarkSupported(stream);
                        enc = EncodingDetector.inferStreamEncoding(stream, "UTF-8", null);
                    }
                    return characterContent = CatalogCollection.makeStringFromStream(stream, enc);
                } catch (IOException e) {
                    if (onError == URIQueryParameters.ON_ERROR_FAIL) {
                        throw new XPathException(e);
                    } else {
                        return null;
                    }
                }
            }
        }
    }

    /**
     * Get details of a resource given the resource URI
     *
     * @param resourceURI the resource URI
     * @return details of the resource
     * @throws XPathException if the information cannot be obtained
     */

    protected InputDetails getInputDetails(String resourceURI) throws XPathException {

        InputDetails inputDetails = new InputDetails();
        try {
            inputDetails.resourceUri = resourceURI;
            URI uri = new URI(resourceURI);
            if ("file".equals(uri.getScheme())) {
                if (params != null && params.getContentType().isPresent()) {
                    inputDetails.contentType = params.getContentType().get();
                } else {
                    inputDetails.contentType = guessContentTypeFromName(resourceURI);
                }
            } else {
                URLConnection connection = ResourceLoader.urlConnection(uri.toURL());
                inputDetails.contentType = connection.getContentType();
                inputDetails.encoding = connection.getContentEncoding();
                for (String param : inputDetails.contentType.replace(" ", "").split(";")) {
                    if (param.startsWith("charset=")) {
                        inputDetails.encoding = param.split("=", 2)[1];
                    } else {
                        inputDetails.contentType = param;
                    }
                }

            }

            if (inputDetails.contentType == null || config.getResourceFactoryForMediaType(inputDetails.contentType) == null) {
                InputStream stream;
                if ("file".equals(uri.getScheme())) {
                    File file = new File(uri);
                    stream = new BufferedInputStream(new FileInputStream(file));
                    if (file.length() <= 1024) {
                        inputDetails.binaryContent = BinaryResource.readBinaryFromStream(stream, resourceURI);
                        stream.close();
                        stream = new ByteArrayInputStream(inputDetails.binaryContent);
                    }
                } else {
                    stream = ResourceLoader.urlStream(config, uri.toString());
                }
                inputDetails.contentType = guessContentTypeFromContent(stream);
                stream.close();
            }
            if (params != null && params.getOnError().isPresent()) {
                inputDetails.onError = params.getOnError().get();
            }
            return inputDetails;

        } catch (URISyntaxException | IOException e) {
            throw new XPathException(e);
        }

    }

    /**
     * Attempt to establish the media type of a resource, given the resource URI.
     * This makes use of {@code URLConnection#guessContentTypeFromName}, and failing that
     * the mapping from file extensions to media types held in the Saxon {@link Configuration}
     *
     * @param resourceURI the resource URI
     * @return the media type if it can be gleaned, otherwise null.
     */

    protected String guessContentTypeFromName(String resourceURI) {
        String contentTypeFromName = URLConnection.guessContentTypeFromName(resourceURI);
        String extension = null;
        if (contentTypeFromName == null) {
            extension = getFileExtension(resourceURI);
            if (extension != null) {
                contentTypeFromName = config.getMediaTypeForFileExtension(extension);
            }
        }
        return contentTypeFromName;
    }

    /**
     * Attempt to establish the media type of a resource, given the actual content.
     * This makes use of {@code URLConnection#guessContentTypeFromStream}
     *
     * @param stream the input stream. This should be positioned at the start; the
     *               reading position is not affected by the call.
     * @return the media type if it can be gleaned, otherwise null.
     */

    protected String guessContentTypeFromContent(InputStream stream) {
        try {
            stream = InputStreamMarker.ensureMarkSupported(stream);
            return URLConnection.guessContentTypeFromStream(stream);
        } catch (IOException err) {
            return null;
        }
    }

    /**
     * Get the file extension from a file name or URI
     *
     * @param name the file name or URI
     * @return the part after the last dot, or null if there is no dot after the last slash or backslash.
     */

    private String getFileExtension(String name) {
        int i = name.lastIndexOf('.');
        int p = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (i > p && i + 1 < name.length()) {
            return name.substring(i + 1);
        }
        return null;
    }

    /**
     * Internal method to make a resource for a single entry in the ZIP or JAR file. This involves
     * making decisions about the type of resource. This method can be overridden in a user-defined
     * subclass.
     *
     * @param context  The Saxon configuration
     * @param details Details of the input.
     * @return a newly created Resource representing the content of this entry in the ZIP or JAR file
     */

    public Resource makeResource(XPathContext context, InputDetails details) throws XPathException {

        ResourceFactory factory = null;
        String contentType = details.contentType;
        if (contentType != null) {
            factory = context.getConfiguration().getResourceFactoryForMediaType(contentType);
        }
        if (factory == null) {
            factory = BinaryResource.FACTORY;
        }

        return factory.makeResource(context, details);
    }

    /**
     * Given a resource whose type may be unknown, create a Resource of a specific type,
     * for example an XML or JSON resource
     *
     * @param context        the evaluation context
     * @param basicResource the resource, whose type may be unknown
     * @return a Resource of a specific type
     * @throws XPathException if a failure occurs (for example an XML or JSON parsing failure)
     */

    public Resource makeTypedResource(XPathContext context, Resource basicResource) throws XPathException {

        String mediaType = basicResource.getContentType();
        ResourceFactory factory = config.getResourceFactoryForMediaType(mediaType);
        if (factory == null) {
            return basicResource;
        }
        if (basicResource instanceof BinaryResource) {
            InputDetails details = new InputDetails();
            details.binaryContent = ((BinaryResource) basicResource).getData();
            details.contentType = mediaType;
            details.resourceUri = basicResource.getResourceURI();
            return factory.makeResource(context, details);
        } else if (basicResource instanceof UnparsedTextResource) {
            InputDetails details = new InputDetails();
            details.characterContent = ((UnparsedTextResource) basicResource).getContent();
            details.contentType = mediaType;
            details.resourceUri = basicResource.getResourceURI();
            return factory.makeResource(context, details);
        } else {
            return basicResource;
        }

    }

    /**
     * Default method to make a resource, given a resource URI
     *
     * @param resourceURI the resource URI
     * @return the corresponding resource
     */

    public Resource makeResource(XPathContext context, String resourceURI) throws XPathException {
        InputDetails details = getInputDetails(resourceURI);
        return makeResource(context, details);
    }

    /**
     * Supply information about the whitespace stripping rules that apply to this collection.
     * This method will only be called when the collection() function is invoked from XSLT.
     *
     * @param rules the space-stripping rules that apply to this collection, derived from
     *              the xsl:strip-space and xsl:preserve-space declarations in the stylesheet
     *              package containing the call to the collection() function.
     * @return true if the collection finder intends to take responsibility for whitespace
     * stripping according to these rules; false if it wishes Saxon itself to post-process
     * any returned XML documents to strip whitespace. Returning true may either indicate
     * that the collection finder will strip whitespace before returning a document, or it
     * may indicate that it does not wish the space stripping rules to be applied.  The
     * default (returned by this method if not overridden) is false.
     */

    public boolean stripWhitespace(SpaceStrippingRule rules) {
        return false;
    }


}

