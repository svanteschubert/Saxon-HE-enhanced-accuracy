////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.str.StringConstants;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/**
 * XHTMLEmitter is an Emitter that generates XHTML 1 output.
 * It is the same as XMLEmitter except that it follows the legacy HTML browser
 * compatibility rules: for example, generating empty elements such as [BR /], and
 * using [p][/p] for empty paragraphs rather than [p/]
 */

public class XHTML1Emitter extends XMLEmitter {

    /**
     * Table of XHTML tags that have no closing tag
     */

    static Set<String> emptyTags1 = new HashSet<>(31);

    private static final String[] emptyTagNames1 = {
            "area", "base", "basefont", "br", "col", "embed", "frame", "hr", "img", "input", "isindex", "link", "meta", "param"
            // added "embed" in 9.5
    };


    static {
        Collections.addAll(emptyTags1, emptyTagNames1);
    }


    private boolean isRecognizedHtmlElement(NodeName name) {
        return name.hasURI(NamespaceUri.XHTML);

    }

    /**
     * Close an empty element tag.
     */

    @Override
    protected void writeEmptyElementTagCloser(String displayName, /*@NotNull*/ NodeName name) throws IOException {
        if (isRecognizedHtmlElement(name) && emptyTags1.contains(name.getLocalPart())) {
            writer.writeAscii(StringConstants.EMPTY_TAG_END_XHTML);
        } else {
            writer.writeAscii(StringConstants.EMPTY_TAG_MIDDLE);
            writer.write(displayName);
            writer.writeCodePoint('>');
        }
    }


}

