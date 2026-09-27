////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;


/**
 * This class is the standard ModuleURIResolver used to implement the "import module" declaration
 * in a Query Prolog. It is used when no user-defined ModuleURIResolver has been specified, or when
 * the user-defined ModuleURIResolver decides to delegate to the standard ModuleURIResolver.
 * It relies on location hints being supplied in the "import module" declaration, and attempts
 * to locate a module by dereferencing the URI given as the location hint. It accepts standard
 * URIs recognized by the Java URL class, including the <code>jar</code> URI scheme; it also
 * accepts <code>classpath</code> URIs as defined in the Spring framework.
 */

public class StandardModuleURIResolver implements ModuleURIResolver {
    
    Configuration config = null;

    /**
     * Create a StandardModuleURIResolver. If this constructor is used, a configuration must
     * be supplied in a subsequent call. The constructor is provided to allow instantiation
     * from a Configuration file.
     */

    public StandardModuleURIResolver() {

    }


    /**
     * Create a StandardModuleURIResolver, with a supplied configuration
     * @param config the Saxon Configuration object
     */

    public StandardModuleURIResolver(Configuration config) {
        this.config = config;
    }

    /**
     * Set the Configuration that this resolver uses. Has no effect if the configuration
     * has already been supplied by the constructor.
     * @param config the Saxon Configuration object
     */
    public void setConfiguration(Configuration config) {
        if (this.config == null) {
            this.config = config;
        }
    }

    /**
     * Resolve a module URI and associated location hints.
     * <p>The logic of this is as follows:</p>
     * <ol>
     *     <li>First, call the configuration-level ResourceResolver to resolve the module URI
     *     as a namespace (via the method resolveModuleURI, which can be overridden in a subclass).
     *     </li>
     *     <li>If this returns null and there are no location hints, throw XQST0059.</li>
     *     <li>Otherwise attempt to resolve each of the supplied location hints in turn,
     *     using first the configuration-level ResourceResolver and then the fallback DirectResourceResolver,
     *     via the method resolveLocationHint, which can be overridden in a subclass. Return an array containing
     *     any non-null results; if there are none, throw XQST0059.</li>
     * </ol>
     *
     * @param moduleURI The module namespace URI of the module to be imported; or null when
     *                  loading a non-library module.
     * @param baseURI   The base URI of the module containing the "import module" declaration;
     *                  null if no base URI is known
     * @param locations The set of URIs specified in the "at" clause of "import module",
     *                  which serve as location hints for the module
     * @return an array of StreamSource objects each identifying the contents of a module to be
     *         imported. Each StreamSource must contain a
     *         non-null absolute System ID which will be used as the base URI of the imported module,
     *         and either an InputSource or a Reader representing the text of the module.
     * @throws XPathException (error XQST0059) if the module cannot be located
     */

    @Override
    public StreamSource[] resolve(String moduleURI, String baseURI, String[] locations) throws XPathException {
        if (config == null) {
            throw new NullPointerException("No Configuration available in StandardModuleResolver");
        }
        StreamSource source = (moduleURI != null || baseURI != null) ? resolveModuleURI(moduleURI, baseURI) : null;
        if (source != null) {
            return new StreamSource[]{source};
        }
        if (locations.length == 0) {
            throw new XPathException("Cannot locate module for namespace " + moduleURI)
                    .withErrorCode("XQST0059").asStaticError();
        }

        // One or more locations given: import modules from all these locations

        List<StreamSource> moduleSources = new ArrayList<>();

        for (String hint : locations) {
            StreamSource ss = resolveLocationHint(baseURI, hint);
            if (ss != null) {
                moduleSources.add(ss);
            }
        }

        return moduleSources.toArray(new StreamSource[]{});
    }

    /**
     * Attempt to resolve the module namespace URI without the help of location hints.
     * @param moduleURI the namespace URI of the module being imported
     * @param baseURI the base URI of the module containing the "import module" declaration
     * @return a StreamSource delivering the contents of the module.
     */                                   
    protected StreamSource resolveModuleURI(String moduleURI, String baseURI) {
        try {
            Source source = null;
            if (config != null) {
                ResourceRequest rr = new ResourceRequest();
                rr.uri = moduleURI;
                rr.uriIsNamespace = true;
                rr.baseUri = baseURI;
                rr.nature = ResourceRequest.XQUERY_NATURE;
                source = config.getResourceResolver().resolve(rr);
            }
            if (source != null) {
                return toStreamSource(source);
            }
        } catch (TransformerException e) {
            // nevermind
        }
        return null;
    }

    private StreamSource toStreamSource(Source src) throws XPathException {
        if (src instanceof AugmentedSource) {
            src = ((AugmentedSource) src).getContainedSource();
        }

        if (src instanceof StreamSource) {
            return (StreamSource) src;
        } else if (src instanceof SAXSource) {
            // not recommended but it can happen
            return ResourceResolverWrappingURIResolver.convertToStreamSource((SAXSource) src);
        } else {
            XPathException se = new XPathException("Resource resolver returned non-StreamSource for XQuery module", "XQST0059");
            se.setIsStaticError(true);
            throw se;
        }
    }


    /**
     * Resolve a location hint appearing in an "import module" declaration
     * @param baseURI  the base URI of the "import module" declaration
     * @param locationHint  the location hint, as written
     * @return  either a StreamSource representing the content of the module, or null
     * @throws XPathException if the URI is invalid or can't be resolved (but not if the
     * module simply doesn't exist at that location)
     */

    protected StreamSource resolveLocationHint(String baseURI, String locationHint) throws XPathException {
        ResourceRequest rr = new ResourceRequest();
        rr.baseUri = baseURI;
        rr.relativeUri = locationHint;
        rr.nature = ResourceRequest.XQUERY_NATURE;
        try {
            rr.uri = ResolveURI.makeAbsolute(rr.relativeUri, baseURI).toString();
            Source src = rr.resolve(config.getResourceResolver(), new DirectResourceResolver(config));
            return src == null ? null : toStreamSource(src);
        } catch (URISyntaxException err) {
            throw new XPathException("Cannot resolve relative URI " + rr.relativeUri, err)
                    .withErrorCode("XQST0059").asStaticError();
        }
    }


//    /**
//     * Get a StreamSource object representing the source of a query, given its URI.
//     * This method attempts to discover the encoding by reading any HTTP headers.
//     * If the encoding can be determined, it returns a StreamSource containing a Reader that
//     * performs the required decoding. Otherwise, it returns a StreamSource containing an
//     * InputSource, leaving the caller to sort out encoding problems.
//     *
//     * @param absoluteURI the absolute URI of the source query
//     * @return a StreamSource containing a Reader or InputSource, as well as a systemID representing
//     *         the base URI of the query.
//     * @throws XPathException if the URIs are invalid or cannot be resolved or dereferenced, or
//     *                        if any I/O error occurs
//     */
//
//    /*@NotNull*/
//    protected StreamSource getQuerySource(URI absoluteURI)
//            throws XPathException {
//
//        String encoding = null;
//        try {
//            InputStream is = null;
//            if ("classpath".equals(absoluteURI.getScheme())) {
//                String path = absoluteURI.getPath();
//                if (config != null) {
//                    is = config.getDynamicLoader().getResourceAsStream(path);
//                }
//                if (is == null) {
//                    XPathException se = new XPathException("Cannot locate module " + path + " on class path");
//                    se.setErrorCode("XQST0059");
//                    se.setIsStaticError(true);
//                    throw se;
//                }
//            } else {
//                URLConnection connection = Loader.urlConnection(absoluteURI.toURL());
//                is = connection.getInputStream();
//
//                // Get any external (HTTP) encoding label.
//                String contentType;
//
//                // The file:// URL scheme gives no useful information...
//                if (!"file".equals(connection.getURL().getProtocol())) {
//
//                    // Use the contentType from the HTTP header if available
//                    contentType = connection.getContentType();
//
//                    if (contentType != null) {
//                        int pos = contentType.indexOf("charset");
//                        if (pos >= 0) {
//                            pos = contentType.indexOf('=', pos + 7);
//                            if (pos >= 0) {
//                                contentType = contentType.substring(pos + 1);
//                            }
//                            if ((pos = contentType.indexOf(';')) > 0) {
//                                contentType = contentType.substring(0, pos);
//                            }
//
//                            // attributes can have comment fields (RFC 822)
//                            if ((pos = contentType.indexOf('(')) > 0) {
//                                contentType = contentType.substring(0, pos);
//                            }
//                            // ... and values may be quoted
//                            if ((pos = contentType.indexOf('"')) > 0) {
//                                contentType = contentType.substring(pos + 1,
//                                    contentType.indexOf('"', pos + 2));
//                            }
//                            encoding = contentType.trim();
//                        }
//                    }
//                }
//            }
//
//            if (!is.markSupported()) {
//                is = new BufferedInputStream(is);
//            }
//
//
//            StreamSource ss = new StreamSource();
//            if (encoding == null) {
//                ss.setInputStream(is);
//            } else {
//                ss.setReader(new InputStreamReader(is, encoding));
//            }
//            ss.setSystemId(absoluteURI.toString());
//            return ss;
//        } catch (IOException err) {
//            XPathException se = new XPathException(err);
//            se.setErrorCode("XQST0059");
//            se.setIsStaticError(true);
//            throw se;
//        }
//
//    }
}

