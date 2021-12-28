////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NodeName;

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

    static Set<String> emptyTags1 = new HashSet<String>(31);

    private static String[] emptyTagNames1 = {
            "area", "base", "basefont", "br", "col", "embed", "frame", "hr", "img", "input", "isindex", "link", "meta", "param"
            // added "embed" in 9.5
    };


    static {
        Collections.addAll(emptyTags1, emptyTagNames1);
    }


    private boolean isRecognizedHtmlElement(NodeName name) {
        return name.hasURI(NamespaceConstant.XHTML);

    }

    /**
     * Close an empty element tag.
     */

    @Override
    protected String emptyElementTagCloser(String displayName, /*@NotNull*/ NodeName name) {
        if (isRecognizedHtmlElement(name) && emptyTags1.contains(name.getLocalPart())) {
            return " />";
        } else {
            return "></" + displayName + '>';
        }
    }


}

