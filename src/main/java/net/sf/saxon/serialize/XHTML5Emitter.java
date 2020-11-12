////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.serialize;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/**
 * XHTML5Emitter is an Emitter that generates XHTML 5 output.
 * It is the same as XMLEmitter except that it follows the legacy HTML browser
 * compatibility rules: for example, generating empty elements such as [BR /], and
 * using [p][/p] for empty paragraphs rather than [p/]
 */

public class XHTML5Emitter extends XMLEmitter {

    private static String[] html5ElementNames = {
            "a", "abbr", "address", "area", "article", "aside", "audio",
            "b", "base", "bdi", "bdo", "blockquote", "body", "br", "button",
            "canvas", "caption", "cite", "code", "col", "colgroup", /*"command",*/
            "datalist", "dd", "del", "details", "dfn", "dialog", "div", "dl", "dt",
            "em", "embed",
            "fieldset", "figcaption", "figure", "footer", "form",
            "h1", "h2", "h3", "h4", "h5", "h6", "head", "header", "hgroup", "hr", "html",
            "i", "iframe", "img", "input", "ins",
            "kbd", "keygen",
            "label", "legend", "li", "link",
            "map", "mark", "menu", "meta", "meter",
            "nav", "noscript",
            "object", "ol", "optgroup", "option", "output",
            "p", "param", "pre", "progress",
            "q",
            "rp", "rt", "ruby",
            "s", "samp", "script", "section", "select", "small", "source", "span", "strong", "style", "sub", "summary", "sup",
            "table", "tbody", "td", "textarea", "tfoot", "th", "thead", "time", "title", "tr", "track",
            "u", "ul",
            "var", "video",
            "wbr"
    };

    static Set<String> html5Elements = new HashSet<String>(128);

    static Set<String> emptyTags5 = new HashSet<String>(31);

    private static String[] emptyTagNames5 = {
            "area", "base", "br", "col", /*"command",*/ "embed", "hr", "img", "input", "keygen", "link", "meta", "param",
            "source", "track", "wbr"
    };


    static {
        Collections.addAll(emptyTags5, emptyTagNames5);
        Collections.addAll(html5Elements, html5ElementNames);
    }


    private boolean isRecognizedHtmlElement(NodeName name) {
        return name.hasURI(NamespaceConstant.XHTML) ||
                name.hasURI("") && html5Elements.contains(name.getLocalPart().toLowerCase());

    }

    /**
     * Output the document type declaration
     *
     * @param name        the qualified name of the element
     * @param displayName The element name as displayed
     * @param systemId    The DOCTYPE system identifier
     * @param publicId    The DOCTYPE public identifier
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs writing to the output
     */
    @Override
    protected void writeDocType(NodeName name, String displayName, String systemId, String publicId) throws XPathException {
        if (systemId == null &&
                isRecognizedHtmlElement(name) && name.getLocalPart().toLowerCase().equals("html")) {
            try {
                writer.write("<!DOCTYPE " + displayName + ">");
            } catch (IOException e) {
                throw new XPathException(e);
            }
        } else if (systemId != null) {
            super.writeDocType(name, displayName, systemId, publicId);
        }
    }

    @Override
    protected boolean writeDocTypeWithNullSystemId() {
        return true;
    }

    /**
     * Close an empty element tag.
     */

    @Override
    protected String emptyElementTagCloser(String displayName, /*@NotNull*/ NodeName name) {
        if (isRecognizedHtmlElement(name) && emptyTags5.contains(name.getLocalPart())) {
            return "/>";
        } else {
            return "></" + displayName + '>';
        }
    }

    /**
     * Character data.
     */
    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (!started && Whitespace.isWhite(chars)) {
            // Ignore whitespace before the first start tag. This isn't explicit in the spec, but
            // we would otherwise need to buffer such whitespace, because we need to output a DOCTYPE
            // declaration based on the content of the first element tag.
        } else {
            super.characters(chars, locationId, properties);
        }
    }
}

