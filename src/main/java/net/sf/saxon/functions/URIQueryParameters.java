////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.AllElementsSpaceStrippingRule;
import net.sf.saxon.om.IgnorableSpaceStrippingRule;
import net.sf.saxon.om.NoElementsSpaceStrippingRule;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.regex.JavaRegularExpression;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.Instantiator;
import net.sf.saxon.trans.Maker;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * A set of query parameters on a URI passed to the collection() or document() function
 */

public class URIQueryParameters {

    /*@Nullable*/ FilenameFilter filter = null;
    Boolean recurse = null;
    Integer validation = null;
    SpaceStrippingRule strippingRule = null;
    Integer onError = null;
    Maker<XMLReader> parserMaker = null;
    Boolean xinclude = null;
    Boolean stable = null;
    Boolean metadata = null;
    String contentType = null;

    public static final int ON_ERROR_FAIL = 1;
    public static final int ON_ERROR_WARNING = 2;
    public static final int ON_ERROR_IGNORE = 3;

    /**
     * Create an object representing the query part of a URI
     *
     * @param query  the part of the URI after the "?" symbol
     * @param config the Saxon configuration
     */

    public URIQueryParameters(String query, Configuration config) throws XPathException {
        if (query != null) {
            StringTokenizer t = new StringTokenizer(query, ";&");
            while (t.hasMoreTokens()) {
                String tok = t.nextToken();
                int eq = tok.indexOf('=');
                if (eq > 0 && eq < (tok.length() - 1)) {
                    String keyword = tok.substring(0, eq);
                    String value = tok.substring(eq + 1);
                    processParameter(config, keyword, value);
                }
            }
        }
    }

    private void processParameter(Configuration config, String keyword, String value) throws XPathException {
        if (keyword.equals("select")) {
            filter = makeGlobFilter(value);
        } else if (keyword.equals("match")) {
            ARegularExpression regex = new ARegularExpression(value, "", "XP", new ArrayList<>(), config);
            filter = new RegexFilter(regex);
        } else if (keyword.equals("recurse")) {
            recurse = "yes".equals(value);
        } else if (keyword.equals("validation")) {
            int v = Validation.getCode(value);
            if (v != Validation.INVALID) {
                validation = v;
            }
        } else if (keyword.equals("strip-space")) {
            switch (value) {
                case "yes":
                    strippingRule = AllElementsSpaceStrippingRule.getInstance();
                    break;
                case "ignorable":
                    strippingRule = IgnorableSpaceStrippingRule.getInstance();
                    break;
                case "no":
                    strippingRule = NoElementsSpaceStrippingRule.getInstance();
                    break;
            }
        } else if (keyword.equals("stable")) {
            if (value.equals("yes")) {
                stable = Boolean.TRUE;
            } else if (value.equals("no")) {
                stable = Boolean.FALSE;
            }
        } else if (keyword.equals("metadata")) {
            if (value.equals("yes")) {
                metadata = Boolean.TRUE;
            } else if (value.equals("no")) {
                metadata = Boolean.FALSE;
            }
        } else if (keyword.equals("xinclude")) {
            if (value.equals("yes")) {
                xinclude = Boolean.TRUE;
            } else if (value.equals("no")) {
                xinclude = Boolean.FALSE;
            }
        } else if (keyword.equals("content-type")) {
            contentType = value;
        } else if (keyword.equals("on-error")) {
            switch (value) {
                case "warning":
                    onError = ON_ERROR_WARNING;
                    break;
                case "ignore":
                    onError = ON_ERROR_IGNORE;
                    break;
                case "fail":
                    onError = ON_ERROR_FAIL;
                    break;
            }
        } else if (keyword.equals("parser") && config != null) {
            parserMaker = new Instantiator<>(value, config);
        }
    }

    public static FilenameFilter makeGlobFilter(String value) throws XPathException {
        FastStringBuffer sb = new FastStringBuffer(value.length() + 6);
        sb.cat('^');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '.') {
                // replace "." with "\."
                sb.append("\\.");
            } else if (c == '*') {
                // replace "*" with ".*"
                sb.append(".*");
            } else if (c == '?') {
                // replace "?" with ".?"
                sb.append(".?");
            } else {
                sb.cat(c);
            }
        }
        sb.cat('$');
        try {
            return new RegexFilter(new JavaRegularExpression(sb, ""));
        } catch (XPathException e) {
            throw new XPathException("Invalid glob " + value + " in collection URI", "FODC0004");
        }
    }

    /**
     * Get the value of the strip-space=yes|no parameter.
     *
     * @return an instance of {@link AllElementsSpaceStrippingRule}, {@link IgnorableSpaceStrippingRule},
     * or {@link NoElementsSpaceStrippingRule}, or null
     */

    public SpaceStrippingRule getSpaceStrippingRule() {
        return strippingRule;
    }

    /**
     * Get the value of the validation=strict|lax|preserve|strip parameter, or null if unspecified
     */

    public Integer getValidationMode() {
        return validation;
    }

    /**
     * Get the file name filter (select=pattern), or null if unspecified
     */

    public FilenameFilter getFilenameFilter() {
        return filter;
    }

    /**
     * Get the value of the recurse=yes|no parameter, or null if unspecified
     */

    public Boolean getRecurse() {
        return recurse;
    }

    /**
     * Get the value of the on-error=fail|warning|ignore parameter, or null if unspecified
     */

    public Integer getOnError() {
        return onError;
    }

    /**
     * Get the value of xinclude=yes|no, or null if unspecified
     */

    public Boolean getXInclude() {
        return xinclude;
    }

    /**
     * Get the value of metadata=yes|no, or null if unspecified
     */

    public Boolean getMetaData() {
        return metadata;
    }

    /**
     * Get the value of media-type, or null if absent
     */

    public String getContentType() {
        return contentType;
    }

    /**
     * Get the value of stable=yes|no, or null if unspecified
     */

    public Boolean getStable() {
        return stable;
    }

    /**
     * Get a factory for the selected XML parser class, or null if unspecified
     */

    public Maker<XMLReader> getXMLReaderMaker() {
        return parserMaker;
    }

    /**
     * A FilenameFilter that tests file names against a regular expression
     */

    public static class RegexFilter implements FilenameFilter {

        private RegularExpression pattern;


        public RegexFilter(RegularExpression regex) {
            this.pattern = regex;
        }

        /**
         * Tests if a specified file should be included in a file list.
         *
         * @param dir  the directory in which the file was found.
         * @param name the name (last component) of the file.
         * @return <code>true</code> if and only if the name should be
         *         included in the file list; <code>false</code> otherwise.
         *         Returns true if the file is a directory or if it matches the glob pattern.
         */

        @Override
        public boolean accept(File dir, String name) {
            return new File(dir, name).isDirectory() || pattern.matches(name);
        }

        /**
         * Test whether a name matches the pattern (regardless whether it is a directory or not)
         *
         * @param name the name (last component) of the file
         * @return true if the name matches the pattern.
         */
        public boolean matches(String name) {
            return pattern.matches(name);
        }
    }


}

