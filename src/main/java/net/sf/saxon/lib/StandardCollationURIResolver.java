////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;
import java.util.StringTokenizer;


/**
 * StandardCollationURIResolver allows a Collation to be created given
 * a URI starting with "http://saxon.sf.net/collation" followed by a set of query parameters.
 */

public class StandardCollationURIResolver implements CollationURIResolver {

    private static final StandardCollationURIResolver theInstance = new StandardCollationURIResolver();

    /**
     * The class is normally used as a singleton, but the constructor is public to allow the class to be named
     * as a value of the configuration property COLLATION_URI_RESOLVER
     */
    public StandardCollationURIResolver() {
    }

    /**
     * Return the singleton instance of this class
     *
     * @return the singleton instance
     */

    public static StandardCollationURIResolver getInstance() {
        return theInstance;
    }


    /**
     * Create a collator from a parameterized URI
     *
     * @return null if the collation URI is not recognized. If the collation URI is recognized but contains
     * errors, the method returns null after sending a warning to the ErrorListener.
     */

    /*@Nullable*/
    @Override
    public StringCollator resolve(String uri, Configuration config) throws XPathException {
        if (uri.equals("http://saxon.sf.net/collation")) {
            return Version.platform.makeCollation(config, new Properties(), uri);
        } else if (uri.startsWith("http://saxon.sf.net/collation?")) {
            URI uuri;
            try {
                uuri = new URI(uri);
            } catch (URISyntaxException err) {
                throw new XPathException(err);
            }
            Properties props = new Properties();
            String query = uuri.getRawQuery();
            StringTokenizer queryTokenizer = new StringTokenizer(query, ";&");
            while (queryTokenizer.hasMoreElements()) {
                String param = queryTokenizer.nextToken();
                int eq = param.indexOf('=');
                if (eq > 0 && eq < param.length() - 1) {
                    String kw = param.substring(0, eq);
                    String val = AnyURIValue.decode(param.substring(eq + 1));
                    props.setProperty(kw, val);
                }
            }
            return Version.platform.makeCollation(config, props, uri);
        } else if (uri.startsWith("http://www.w3.org/2013/collation/UCA")) {
            StringCollator uca = Version.platform.makeUcaCollator(uri, config);
            if (uca != null) {
                return uca;
            }
            if (uri.contains("fallback=no")) {
                return null;
            } else {
                URI uuri;
                try {
                    uuri = new URI(uri);
                } catch (URISyntaxException err) {
                    throw new XPathException(err);
                }
                Properties props = new Properties();
                String query = AnyURIValue.decode(uuri.getRawQuery());
                for (String param : query.split(";")) {
                    String tokens[] = param.split("=");
                    if (tokens.length == 2) {
                        String kw = tokens[0];
                        String val = tokens[1];
                        if (kw.equals("fallback")) {
                            if (val.equals("no")) {
                                return null;
                            } else if (!val.equals("yes")) {
                                // effect is implementation-defined, but it seems best to reject it
                                return null;
                            }
                        }
                        switch (kw) {
                            case "strength":
                                switch (val) {
                                    case "1":
                                        val = "primary";
                                        break;
                                    case "2":
                                        val = "secondary";
                                        break;
                                    case "3":
                                        val = "tertiary";
                                        break;
                                    case "quaternary":
                                    case "4":
                                    case "5":
                                        val = "identical";
                                        break;
                                }
                                break;
                            case "caseFirst":
                                kw = "case-order";
                                val += "-first";          // Should check correct?

                                break;
                            case "numeric":
                                kw = "alphanumeric";
                                break;
                        }
                        props.setProperty(kw, val);
                    }
                }
                return Version.platform.makeCollation(config, props, uri);
            }
        } else {
            return null;
        }


    }

}

