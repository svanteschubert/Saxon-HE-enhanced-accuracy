////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.trans.XPathException;

import java.io.Reader;
import java.net.URI;

/**
 * An UnparsedTextURIResolver accepts an absolute URI and optionally an encoding name as input,
 * and returns a Reader as its result.
 */

public interface UnparsedTextURIResolver {

    /**
     * Resolve the URI passed to the XSLT unparsed-text() function, after resolving
     * against the base URI.
     * <p>Note that a user-written resolver is responsible for enforcing some of the rules in the
     * XSLT specification, such as the rules for inferring an encoding when none is supplied. Saxon
     * will not report any error if the resolver does this in a non-conformant way.</p>
     *
     * @param absoluteURI the absolute URI obtained by resolving the supplied
     *                    URI against the base URI
     * @param encoding    the encoding requested in the call of unparsed-text(), if any. Otherwise null.
     * @param config      The Saxon configuration. Provided in case the URI resolver
     *                    needs it.
     * @return a Reader, which Saxon will use to read the unparsed text. After the text has been read,
     *         the close() method of the Reader will be called. Returning null is not acceptable; if the
     *         resolver wishes to delegate to the standard resolver, it can do this by subclassing
     *         {@link StandardUnparsedTextResolver} and calling <code>super.resolve(...)</code>.
     * @throws net.sf.saxon.trans.XPathException
     *          if any failure occurs
     * @since 8.9
     */

    /*@NotNull*/
    Reader resolve(URI absoluteURI, String encoding, Configuration config) throws XPathException;
}

