////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api.push;

import net.sf.saxon.s9api.SaxonApiException;

/**
 * A {@link Container} representing a document node.
 *
 * <p>If the document is constrained to be well-formed then the permitted sequence of events is
 * {@code (COMMENT | PI)* ELEMENT (COMMENT | PI)* CLOSE}.</p>
 *
 * <p>If the document is NOT constrained to be well-formed then the permitted sequence of events is
 * {@code (COMMENT | PI | TEXT | ELEMENT)* CLOSE}.</p>
 */

public interface Document extends Container {

    @Override
    Document text(CharSequence value) throws SaxonApiException;

    @Override
    Document comment(CharSequence value) throws SaxonApiException;

    @Override
    Document processingInstruction(String name, CharSequence value) throws SaxonApiException;
}

