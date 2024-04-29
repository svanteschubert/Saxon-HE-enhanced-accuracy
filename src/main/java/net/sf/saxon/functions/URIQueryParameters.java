////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.AllElementsSpaceStrippingRule;
import net.sf.saxon.om.IgnorableSpaceStrippingRule;
import net.sf.saxon.om.NoElementsSpaceStrippingRule;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.regex.JavaRegularExpression;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.trans.Instantiator;
import net.sf.saxon.trans.Maker;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpNullable;
import net.sf.saxon.transpile.CSharpReplaceBody;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Optional;
import java.util.StringTokenizer;

/**
 * A set of query parameters on a URI passed to the collection() or document() function
 */
@CSharpNullable("enable")
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class URIQueryParameters {

    Optional<FilenameFilter> filter = Optional.empty();
    Optional<Boolean> recurse = Optional.empty();
    Optional<Integer> validation = Optional.empty();
    Optional<SpaceStrippingRule> strippingRule = Optional.empty();
    Optional<Integer> onError = Optional.empty();

    Optional<Boolean> xinclude = Optional.empty();
    Optional<Boolean> stable = Optional.empty();
    Optional<Boolean> metadata = Optional.empty();
    Optional<String> contentType = Optional.empty();

    Optional<Maker<XMLReader>> parserMaker = Optional.empty();


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
            filter = Optional.of(makeGlobFilter(value));
        } else if (keyword.equals("match")) {
            ARegularExpression regex = new ARegularExpression(StringView.of(value).tidy(), "", "XP", new ArrayList<>(), config);
            filter = Optional.of(new RegexFilter(regex));
        } else if (keyword.equals("recurse")) {
            recurse = Optional.of("yes".equals(value));
        } else if (keyword.equals("validation")) {
            int v = Validation.getCode(value);
            if (v != Validation.INVALID) {
                validation = Optional.of(v);
            }
        } else if (keyword.equals("strip-space")) {
            switch (value) {
                case "yes":
                    strippingRule = Optional.of(AllElementsSpaceStrippingRule.getInstance());
                    break;
                case "ignorable":
                    strippingRule = Optional.of(IgnorableSpaceStrippingRule.getInstance());
                    break;
                case "no":
                    strippingRule = Optional.of(NoElementsSpaceStrippingRule.getInstance());
                    break;
            }
        } else if (keyword.equals("stable")) {
            if (value.equals("yes")) {
                stable = Optional.of(Boolean.TRUE);
            } else if (value.equals("no")) {
                stable = Optional.of(Boolean.FALSE);
            }
        } else if (keyword.equals("metadata")) {
            if (value.equals("yes")) {
                metadata = Optional.of(Boolean.TRUE);
            } else if (value.equals("no")) {
                metadata = Optional.of(Boolean.FALSE);
            }
        } else if (keyword.equals("xinclude")) {
            if (value.equals("yes")) {
                checkXIncludeIsSupported();
                xinclude = Optional.of(Boolean.TRUE);
            } else if (value.equals("no")) {
                xinclude = Optional.of(Boolean.FALSE);
            }
        } else if (keyword.equals("content-type")) {
            contentType = Optional.of(value);
        } else if (keyword.equals("on-error")) {
            switch (value) {
                case "warning":
                    onError = Optional.of(ON_ERROR_WARNING);
                    break;
                case "ignore":
                    onError = Optional.of(ON_ERROR_IGNORE);
                    break;
                case "fail":
                    onError = Optional.of(ON_ERROR_FAIL);
                    break;
            }
        } else if (keyword.equals("parser") && config != null) {
            parserMaker = Optional.of(new Instantiator<>(value, config));
        }
    }

    @CSharpReplaceBody(code="throw new Saxon.Hej.trans.XPathException(\"XInclude is not supported in SaxonCS\");")
    private static void checkXIncludeIsSupported() throws XPathException {
        // No action on SaxonJ
    }

    public static FilenameFilter makeGlobFilter(String value) throws XPathException {
        UnicodeBuilder sb = new UnicodeBuilder();
        sb.append('^');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '.') {
                // replace "." with "\."
                sb.appendLatin("\\.");
            } else if (c == '*') {
                // replace "*" with ".*"
                sb.appendLatin(".*");
            } else if (c == '?') {
                // replace "?" with ".?"
                sb.appendLatin(".?");
            } else {
                sb.append(c);
            }
        }
        sb.append('$');
        try {
            return new RegexFilter(new JavaRegularExpression(sb.toUnicodeString(), ""));
        } catch (XPathException e) {
            throw new XPathException("Invalid glob " + value + " in collection URI", "FODC0004");
        }
    }

    /**
     * Get the value of the strip-space=yes|no parameter.
     *
     * @return an instance of {@link AllElementsSpaceStrippingRule}, {@link IgnorableSpaceStrippingRule},
     * or {@link NoElementsSpaceStrippingRule}, or absent.
     */

    public Optional<SpaceStrippingRule> getSpaceStrippingRule() {
        return strippingRule;
    }

    /**
     * Get the value of the validation=strict|lax|preserve|strip parameter, or absent if unspecified
     */

    public Optional<Integer> getValidationMode() {
        return validation;
    }

    /**
     * Get the file name filter (select=pattern), or absent if unspecified
     */

    public Optional<FilenameFilter> getFilenameFilter() {
        return filter;
    }

    /**
     * Get the value of the recurse=yes|no parameter, or absent if unspecified
     */

    public Optional<Boolean> getRecurse() {
        return recurse;
    }

    /**
     * Get the value of the on-error=fail|warning|ignore parameter, or absent if unspecified
     */

    public Optional<Integer> getOnError() {
        return onError;
    }

    /**
     * Get the value of xinclude=yes|no, or absent if unspecified
     */

    public Optional<Boolean> getXInclude() {
        return xinclude;
    }

    /**
     * Get the value of metadata=yes|no, or absent if unspecified
     */

    public Optional<Boolean> getMetaData() {
        return metadata;
    }

    /**
     * Get the value of media-type, or absent if absent
     */

    public Optional<String> getContentType() {
        return contentType;
    }

    /**
     * Get the value of stable=yes|no, or absent if unspecified
     */

    public Optional<Boolean> getStable() {
        return stable;
    }

    /**
     * Get a factory for the selected XML parser class, or absent if unspecified
     */

    public Optional<Maker<XMLReader>> getXMLReaderMaker() {
        return parserMaker;
    }

    /**
     * Create ParseOptions based on these query parameters
     */

    @SuppressWarnings("OptionalIsPresent")
    public ParseOptions makeParseOptions(Configuration config) throws XPathException {

        ParseOptions options = new ParseOptions();
        Optional<SpaceStrippingRule> stripSpace = getSpaceStrippingRule();
        if (stripSpace.isPresent()) {
            options = options.withSpaceStrippingRule(stripSpace.get());
        }

        Optional<Integer> validation = getValidationMode();
        if (validation.isPresent()) {
            options = options.withSchemaValidationMode(validation.get());
        }

        Optional<Boolean> xinclude = getXInclude();
        if (xinclude.isPresent()) {
            options = options.withXIncludeAware(xinclude.get());
        }

        return options;
    }

    /**
     * A FilenameFilter that tests file names against a regular expression
     */

    public static class RegexFilter implements FilenameFilter {

        private final RegularExpression pattern;


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
            return new File(dir, name).isDirectory() || pattern.matches(StringView.of(name).tidy());
        }

        /**
         * Test whether a name matches the pattern (regardless whether it is a directory or not)
         *
         * @param name the name (last component) of the file
         * @return true if the name matches the pattern.
         */
        public boolean matches(String name) {
            return pattern.matches(StringView.of(name).tidy());
        }
    }


}

