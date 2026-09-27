////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * This class supports the resolve-uri() function in XPath 2.0
 */

public class ResolveURI extends SystemFunction {

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    /*@Nullable*/
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        AtomicValue arg0 = (AtomicValue) arguments[0].head();
        if (arg0 == null) {
            return EmptySequence.INSTANCE;
        }
        String relative = arg0.getStringValue();
        String base;
        Item baseArg = null;
        if (getArity() == 2) {
            baseArg = arguments[1].head();
        }
        if (baseArg != null) {
            base = baseArg.getStringValue();
        } else {
            base = getStaticBaseUriString();
            if (base == null) {
                throw new XPathException("Base URI in static context of resolve-uri() is unknown", "FONS0005", context);
            }
        }
        return resolve(base, relative, context);
    }

    /*@NotNull*/
    private AnyURIValue resolve(String base, String relative, XPathContext context) throws XPathException {
//        try {

        // Rule 4: "The function resolves the relative IRI reference $relative against the base IRI $base using
        // the algorithm defined in [RFC 3986], adapted by treating any ·character· that would not be valid in
        // an RFC3986 URI or relative reference in the same way that RFC3986 treats unreserved characters.
        // No percent-encoding takes place.

        // We rely on the Java implementation, but the Java implementation will not handle invalid characters
        // notably spaces. If there are spaces present, we escape them to prevent Java objecting, and then unescape
        // them at the end. We accept the consequence that if the input contains both escaped and unescaped spaces,
        // they will all be unescaped at the end.

        boolean escaped = false;
        if (relative.contains(" ")) {
            relative = escapeSpaces(relative);
            escaped = true;
        }
        if (base.contains(" ")) {
            base = escapeSpaces(base);
            escaped = true;
        }

        URI relativeURI = null;
        try {
            relativeURI = absoluteOrRelativeURI(relative);
        } catch (URISyntaxException e) {
            throw new XPathException("Relative URI " + Err.wrap(relative) + " is invalid: " + e.getMessage(),
                                     "FORG0002", context);
        }
        if (relativeURI.isAbsolute()) {
            return new AnyURIValue(relative);
        }

        URI absoluteURI = null;
        try {
            absoluteURI = new URI(base);
        } catch (URISyntaxException e) {
            throw new XPathException("Base URI " + Err.wrap(base) + " is invalid: " + e.getMessage(),
                                     "FORG0002", context);
        }
        if (!absoluteURI.isAbsolute()) {
            throw new XPathException("Base URI " + Err.wrap(base) + " is not an absolute URI", "FORG0002", context);
        }
        if (absoluteURI.isOpaque() && !base.startsWith("jar:")) {
            // Special-case JAR file URLs, even though non-conformant
            throw new XPathException("Base URI " + Err.wrap(base) + " is a non-hierarchic URI", "FORG0002", context);
        }
        String fragment = absoluteURI.getRawFragment();
        if (fragment != null && !fragment.isEmpty()) {
            throw new XPathException("Base URI " + Err.wrap(base) + " contains a fragment identifier", "FORG0002", context);
        }
        if (!base.startsWith("jar:") && absoluteURI.getPath() != null && absoluteURI.getPath().isEmpty()) {
            // This deals with cases like base=http://www.example.com - changing it to http://www.example.com/
            try {
                absoluteURI = new URI(absoluteURI.getScheme(), absoluteURI.getUserInfo(), absoluteURI.getHost(),
                                      absoluteURI.getPort(), "/", absoluteURI.getQuery(), absoluteURI.getFragment());
            } catch (URISyntaxException e) {
                throw new XPathException("Failed to parse JAR scheme URI " +
                                                 Err.wrap(absoluteURI.toASCIIString()), "FORG0002", context);

            }
            base = absoluteURI.toString();
        }
        URI resolved = null;
        try {
            resolved = makeAbsolute(relative, base);
        } catch (URISyntaxException e) {
            throw new XPathException(e.getMessage(), "FORG0002");
        }
        if (!resolved.toASCIIString().startsWith("file:////")) {
            resolved = resolved.normalize();
        }
        // The spec says that special characters are not escaped. But if the input was percent-escaped,
        // we want the output to be percent-escaped too. Java achieves this automatically, but on C#
        // it needs special attention.
        boolean inputIsPercentEncoded = base.contains("%") || relative.contains("%");
        String resolvedString = inputIsPercentEncoded ? resolved.toASCIIString() : resolved.toString();
        String result = escaped ? unescapeSpaces(resolvedString) : resolvedString;

        // Test case XSLT3 resolve-uri-022. Java even after normalization can leave a URI with trailing "../" or ".." parts.
        // Pragmatically, we just strip these off. This might not be enough if there are query or fragment parts, but it
        // gets us through the test

        while (result.endsWith("..")) {
            result = result.substring(0, result.length() - 2);
        }
        while (result.endsWith("../")) {
            result = result.substring(0, result.length() - 3);
        }

        return new AnyURIValue(result);

    }

    @CSharpReplaceBody(code="return new System.Uri(href, System.UriKind.RelativeOrAbsolute);")
    public static URI absoluteOrRelativeURI(String href) throws URISyntaxException {
        return new URI(href);
    }

    /**
     * If a system ID can't be parsed as a URL, try to expand it as a relative
     * URI using the current directory as the base URI.
     *
     * @param systemId the supplied systemId. Null is treated as equivalent to ""
     * @return the systemId itself if it is a valid URL; otherwise the result of resolving
     * the systemId as a relative file name in the current working directory; or if the
     * current working directory is not available (e.g. in an applet) the supplied systemId
     * unchanged (except that null is treated as "").
     */

    /*@NotNull*/
    public static String tryToExpand(/*@Nullable*/ String systemId) {
        if (systemId == null || systemId.isEmpty()) {
            return resolveAgainstCurrentDirectory("");
        }
        try {
            new URL(systemId);
            return systemId;   // all is well
        } catch (MalformedURLException err) {
            return resolveAgainstCurrentDirectory(systemId);
        }
    }

    private static String resolveAgainstCurrentDirectory(String systemId) {
        String dir;
        try {
            dir = System.getProperty("user.dir");
        } catch (Exception geterr) {
            // this doesn't work when running an applet
            return systemId;
        }
        if (!(dir.endsWith("/") || systemId.startsWith("/"))) {
            dir = dir + '/';
        }
        try {
            URI currentDirectoryURI = new File(dir).toURI();
            URI baseURI = currentDirectoryURI.resolve(systemId);
            return baseURI.toString();
        } catch (Exception e) {
            return systemId;
        }
    }

    /**
     * Construct an absolute URI from a relative URI and a base URI. The method uses the resolve
     * method of the java.net.URI class, except where the base URI uses the (non-standard) "jar:" scheme,
     * in which case the method used is <code>new URL(baseURL, relativeURL)</code>.
     * <p>Spaces in either URI are converted to %20</p>
     * <p>If no base URI is available, and the relative URI is not an absolute URI, then the current
     * directory is used as a base URI.</p>
     *
     * @param relativeURI the relative URI. Null is permitted provided that the base URI is an absolute URI
     * @param base        the base URI. Null is permitted provided that relativeURI is an absolute URI
     * @return the absolutized URI
     * @throws java.net.URISyntaxException if either of the strings is not a valid URI or
     *                                     if the resolution fails
     */

    /*@NotNull*/
    public static URI makeAbsolute(/*@Nullable*/ String relativeURI, /*@Nullable*/ String base) throws URISyntaxException {
        URI absoluteURI;
        StandardURIChecker checker = StandardURIChecker.getInstance();
        // System.err.println("makeAbsolute " + relativeURI + " against base " + base);
        if (relativeURI == null) {
            if (base == null) {
                throw failure("", "Relative and Base URI must not both be null");
            }
            absoluteURI = new URI(ResolveURI.escapeSpaces(base));
            checker.checkThoroughly(absoluteURI);
            if (!absoluteURI.isAbsolute()) {
                throw failure(base, "Relative URI not supplied, so base URI must be absolute");
            } else {
                return absoluteURI;
            }
        }

        if (relativeURI.startsWith("classpath:") || relativeURI.startsWith("data:")) {
            // Resolving a classpath: URI involves searching the classpath.
            // There's no sense in which it makes sense to attempt to make one absolute
            // against some base URI. They're effectively absolute already.
            // (If we don't do this, passing them to java.net.URL causes an exception
            // anyway.)
            //
            // Similarly, a data: URI is absolute even though java.net.URL doesn't recognize that.
            return new URI(relativeURI);
        }

        try {
            if (base == null || base.isEmpty()) {
                absoluteURI = new URI(relativeURI);
                if (!absoluteURI.isAbsolute()) {
                    String expandedBase = ResolveURI.tryToExpand(base);
                    if (!expandedBase.equals(base)) { // prevent infinite recursion
                        return makeAbsolute(relativeURI, expandedBase);
                    }
                }
            } else if (base.startsWith("jar:") || base.startsWith("file:////")) {

                // jar: URIs can't be resolved by the java.net.URI class, because they don't actually
                // conform with the RFC standards for hierarchic URI schemes (quite apart from not being
                // a registered URI scheme). But they seem to be widely used.

                // URIs starting file://// are accepted by the java.net.URI class, they are used to
                // represent Windows UNC filenames. However, the java.net.URI algorithm for resolving
                // a relative URI against such a base URI fails to produce a usable UNC filename (it's not
                // clear whether Java is implementing RFC 3986 correctly here, it depends on interpretation).
                // So we use the java.net.URL algorithm for this case too, because it works.

                try {
                    URL baseURL = new URL(base);
                    URL absoluteURL = new URL(baseURL, relativeURI);
                    absoluteURI = absoluteURL.toURI();
                } catch (MalformedURLException err) {
                    throw failure(base + " " + relativeURI, err.getMessage());
                }
            } else if (base.startsWith("classpath:")) {
                absoluteURI = new URI(relativeURI);
                if (!absoluteURI.isAbsolute()) {
                    // URIs in the classpath: scheme are a bit of a mess. Given "classpath:/path/to/thing",
                    // if you attempt to use ClassLoader.getSystemResourceAsStream("/path/to/thing"), it
                    // will fail because the leading slash is a problem. Conversely, if you have
                    // "classpath:path/to/thing" and you try to resolve "otherthing" against it,
                    // you'll get "classpath:otherthing" which is almost certainly wrong. The only
                    // way around it seems to be to fake the scheme long enough to get correct
                    // resolution.
                    String path = base.substring(10);
                    URI fakeURI;
                    if (path.startsWith("/")) {
                        fakeURI = URI.create("file://" + path).resolve(relativeURI);
                    } else {
                        fakeURI = URI.create("file:///" + path).resolve(relativeURI);
                    }
                    String cpath = fakeURI.getPath().substring(1);
                    if (cpath.startsWith("../")) {
                        throw new IllegalArgumentException("Attempt to navigate above root: classpath:" + cpath);
                    }
                    absoluteURI = URI.create("classpath:" + cpath);
                }
            } else {
                URI baseURI;
                try {
                    baseURI = new URI(base);
                } catch (URISyntaxException e) {
                    throw failure(base, "Invalid base URI: " + e.getMessage());
                }

                int hash = base.indexOf('#');
                if (hash >= 0) {
                    base = base.substring(0, hash);
                    try {
                        baseURI = new URI(base);
                        checker.checkThoroughly(baseURI);
                    } catch (URISyntaxException e) {
                        throw failure(base, "Invalid base URI: " + e.getMessage());
                    }
                }

                URI absOrRel;
                try {
                    absOrRel = absoluteOrRelativeURI(relativeURI);   // for validation only
                    checker.checkThoroughly(absOrRel);
                } catch (URISyntaxException e) {
                    throw failure(base, "Invalid relative URI: " + e.getMessage());
                }
                if (absOrRel.isAbsolute()) {
                    absoluteURI = absOrRel;
                } else {
                    absoluteURI = relativeURI.isEmpty() ? baseURI : baseURI.resolve(relativeURI);
                }
            }
        } catch (IllegalArgumentException err0) {
            // can be thrown by resolve() when given a bad URI
            throw failure(relativeURI, "Cannot resolve URI against base " + Err.wrap(base));
        }

        return absoluteURI;
    }

    @CSharpReplaceBody(code="return new System.UriFormatException(\"Failed to resolve \" + input + \": \" + reason);")
    private static URISyntaxException failure(String input, String reason) {
        return new URISyntaxException(input, reason);
    }


    /**
     * Replace spaces by %20
     *
     * @param s the input string
     * @return the input string with each space replaced by %20
     */

    public static String escapeSpaces(String s) {
        // It's not entirely clear why we have to escape spaces by hand, and not other special characters;
        // it's just that tests with a variety of filenames show that this approach seems to work.
        return s.replace(" ", "%20");
    }

    /**
     * Replace %20 by space
     *
     * @param uri the input uri
     * @return the input URI with each %20 replaced by space
     */

    public static String unescapeSpaces(String uri) {
        return uri.replace("%20", " ");
    }

}

