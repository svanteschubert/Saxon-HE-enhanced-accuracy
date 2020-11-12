////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;

import java.io.LineNumberReader;
import java.io.Reader;
import java.net.URI;

/**
 * Class UnparsedTextIterator, iterates over a file line by line
 */
public class UnparsedTextIterator extends TextLinesIterator {

    XPathContext context;
    String encoding = null;

    /**
     * Create a UnparsedTextIterator over a given file
     *
     * @param absoluteURI the URI identifying the file
     * @param context     the dynamic evaluation context
     * @param encoding    the expected encoding of the file
     * @param location    the location of the instruction being executed
     * @throws XPathException if a dynamic error occurs
     */

    public UnparsedTextIterator(URI absoluteURI, /*@NotNull*/ XPathContext context, String encoding, Location location) throws XPathException {
        Configuration config = context.getConfiguration();
        Reader reader = context.getController().getUnparsedTextURIResolver().resolve(absoluteURI, encoding, config);
        // Note: this relies on the fact that LineNumberReader use the same definition of line endings as the unparsed-text-lines()
        // function.

        this.reader = new LineNumberReader(reader);
        this.uri = absoluteURI;
        this.context = context;
        this.checker = context.getConfiguration().getValidCharacterChecker();
        this.encoding = encoding;
        this.location = location;
    }

    public UnparsedTextIterator(LineNumberReader reader, URI absoluteURI, /*@NotNull*/ XPathContext context, String encoding) throws XPathException {
        this.reader = reader;
        this.uri = absoluteURI;
        this.context = context;
        this.checker = context.getConfiguration().getValidCharacterChecker();
        this.encoding = encoding;
        this.location = null;
    }

}

