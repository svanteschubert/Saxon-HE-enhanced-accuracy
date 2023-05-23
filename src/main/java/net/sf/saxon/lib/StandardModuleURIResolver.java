////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.stream.StreamSource;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;


/**
 * This class is the standard ModuleURIResolver used to implement the "import module" declaration
 * in a Query Prolog. It is used when no user-defined ModuleURIResolver has been specified, or when
 * the user-defined ModuleURIResolver decides to delegate to the standard ModuleURIResolver.
 * It relies on location hints being supplied in the "import module" declaration, and attempts
 * to locate a module by dereferencing the URI given as the location hint. It accepts standard
 * URIs recognized by the Java URL class, including the <code>jar</code> URI scheme; it also
 * accepts <code>classpath</code> URIs as defined in the Spring framework.
 *
 * @author Michael H. Kay
 */

public class StandardModuleURIResolver implements ModuleURIResolver {

    Configuration config;

    /**
     * Create a StandardModuleURIResolver. Although the class is generally used as a singleton,
     * a public constructor is provided so that the class can be named in configuration files and
     * instantiated in the same way as user-written module URI resolvers.
     */

    public StandardModuleURIResolver() {

    }


    /**
     * Create a StandardModuleURIResolver. Although the class is generally used as a singleton,
     * a public constructor is provided so that the class can be named in configuration files and
     * instantiated in the same way as user-written module URI resolvers.
     */

    public StandardModuleURIResolver(Configuration config) {
        this.config = config;
    }

    /**
     * Resolve a module URI and associated location hints.
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
        if (locations.length == 0) {
            XPathException err = new XPathException("Cannot locate module for namespace " + moduleURI);
            err.setErrorCode("XQST0059");
            err.setIsStaticError(true);
            throw err;
        } else {
            // One or more locations given: import modules from all these locations
            StreamSource[] sources = new StreamSource[locations.length];
            for (int m = 0; m < locations.length; m++) {
                String href = locations[m];
                URI absoluteURI;
                try {
                    absoluteURI = ResolveURI.makeAbsolute(href, baseURI);
                } catch (URISyntaxException err) {
                    XPathException se = new XPathException("Cannot resolve relative URI " + href, err);
                    se.setErrorCode("XQST0059");
                    se.setIsStaticError(true);
                    throw se;
                }
                if (config != null && !config.getAllowedUriTest().test(absoluteURI)) {
                    throw new XPathException("URI scheme '" + absoluteURI.getScheme() + "' has been disallowed");
                }

                sources[m] = getQuerySource(absoluteURI);
            }
            return sources;
        }
    }

    /**
     * Get a StreamSource object representing the source of a query, given its URI.
     * This method attempts to discover the encoding by reading any HTTP headers.
     * If the encoding can be determined, it returns a StreamSource containing a Reader that
     * performs the required decoding. Otherwise, it returns a StreamSource containing an
     * InputSource, leaving the caller to sort out encoding problems.
     *
     * @param absoluteURI the absolute URI of the source query
     * @return a StreamSource containing a Reader or InputSource, as well as a systemID representing
     *         the base URI of the query.
     * @throws XPathException if the URIs are invalid or cannot be resolved or dereferenced, or
     *                        if any I/O error occurs
     */

    /*@NotNull*/
    protected StreamSource getQuerySource(URI absoluteURI)
            throws XPathException {

        String encoding = null;
        try {
            InputStream is;
            if ("classpath".equals(absoluteURI.getScheme())) {
                String path = absoluteURI.getPath();
                is = config.getDynamicLoader().getResourceAsStream(path);
                if (is == null) {
                    XPathException se = new XPathException("Cannot locate module " + path + " on class path");
                    se.setErrorCode("XQST0059");
                    se.setIsStaticError(true);
                    throw se;
                }
            } else {
                URLConnection connection = RedirectHandler.resolveConnection(absoluteURI.toURL());
                is = connection.getInputStream();

                // Get any external (HTTP) encoding label.
                String contentType;

                // The file:// URL scheme gives no useful information...
                if (!"file".equals(connection.getURL().getProtocol())) {

                    // Use the contentType from the HTTP header if available
                    contentType = connection.getContentType();

                    if (contentType != null) {
                        int pos = contentType.indexOf("charset");
                        if (pos >= 0) {
                            pos = contentType.indexOf('=', pos + 7);
                            if (pos >= 0) {
                                contentType = contentType.substring(pos + 1);
                            }
                            if ((pos = contentType.indexOf(';')) > 0) {
                                contentType = contentType.substring(0, pos);
                            }

                            // attributes can have comment fields (RFC 822)
                            if ((pos = contentType.indexOf('(')) > 0) {
                                contentType = contentType.substring(0, pos);
                            }
                            // ... and values may be quoted
                            if ((pos = contentType.indexOf('"')) > 0) {
                                contentType = contentType.substring(pos + 1,
                                    contentType.indexOf('"', pos + 2));
                            }
                            encoding = contentType.trim();
                        }
                    }
                }
            }

            if (!is.markSupported()) {
                is = new BufferedInputStream(is);
            }


            StreamSource ss = new StreamSource();
            if (encoding == null) {
                ss.setInputStream(is);
            } else {
                ss.setReader(new InputStreamReader(is, encoding));
            }
            ss.setSystemId(absoluteURI.toString());
            return ss;
        } catch (IOException err) {
            XPathException se = new XPathException(err);
            se.setErrorCode("XQST0059");
            se.setIsStaticError(true);
            throw se;
        }

    }
}

