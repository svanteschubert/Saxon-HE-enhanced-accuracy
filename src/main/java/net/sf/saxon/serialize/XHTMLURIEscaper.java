////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import java.text.Normalizer;
import java.util.HashSet;


/**
 * This class performs URI escaping for the XHTML output method. The logic for performing escaping
 * is the same as the HTML output method, but the way in which attributes are identified for escaping
 * is different, because XHTML is case-sensitive.
 */

public class XHTMLURIEscaper extends HTMLURIEscaper {

    /**
     * Table of attributes whose value is a URL
     */

    private static final HashSet<String> urlTable = new HashSet<String>(70);
    private static final HashSet<String> attTable = new HashSet<String>(20);

    private static void setUrlAttributeX(String element, String attribute) {
        attTable.add(attribute);
        urlTable.add(element + "+" + attribute);
    }

    static {
        setUrlAttributeX("form", "action");
        setUrlAttributeX("object", "archive");
        setUrlAttributeX("body", "background");
        setUrlAttributeX("q", "cite");
        setUrlAttributeX("blockquote", "cite");
        setUrlAttributeX("del", "cite");
        setUrlAttributeX("ins", "cite");
        setUrlAttributeX("object", "classid");
        setUrlAttributeX("object", "codebase");
        setUrlAttributeX("applet", "codebase");
        setUrlAttributeX("object", "data");
        setUrlAttributeX("button", "datasrc");
        setUrlAttributeX("div", "datasrc");
        setUrlAttributeX("input", "datasrc");
        setUrlAttributeX("object", "datasrc");
        setUrlAttributeX("select", "datasrc");
        setUrlAttributeX("span", "datasrc");
        setUrlAttributeX("table", "datasrc");
        setUrlAttributeX("textarea", "datasrc");
        setUrlAttributeX("script", "for");
        setUrlAttributeX("a", "href");
        setUrlAttributeX("a", "name");       // see second note in section B.2.1 of HTML 4 specification
        setUrlAttributeX("area", "href");
        setUrlAttributeX("link", "href");
        setUrlAttributeX("base", "href");
        setUrlAttributeX("img", "longdesc");
        setUrlAttributeX("frame", "longdesc");
        setUrlAttributeX("iframe", "longdesc");
        setUrlAttributeX("head", "profile");
        setUrlAttributeX("script", "src");
        setUrlAttributeX("input", "src");
        setUrlAttributeX("frame", "src");
        setUrlAttributeX("iframe", "src");
        setUrlAttributeX("img", "src");
        setUrlAttributeX("img", "usemap");
        setUrlAttributeX("input", "usemap");
        setUrlAttributeX("object", "usemap");
    }

    public XHTMLURIEscaper(Receiver next) {
        super(next);
    }

    /**
     * Determine whether a given attribute is a URL attribute
     */

    private static boolean isURLAttribute(NodeName elcode, NodeName atcode) {
        if (!elcode.hasURI(NamespaceUri.XHTML)) {
            return false;
        }
        if (!atcode.hasURI(NamespaceUri.NULL)) {
            return false;
        }
        String attName = atcode.getLocalPart();
        return attTable.contains(attName) && urlTable.contains(elcode.getLocalPart() + "+" + attName);
    }

    /**
     * Notify the start of an element
     *
     * @param nameCode   the name of the element
     * @param type       the type annotation of the element
     * @param attributes the attributes of this element
     * @param namespaces the in-scope namespaces of this element: generally this is all the in-scope
     *                   namespaces, without relying on inheriting namespaces from parent elements
     * @param location   an object providing information about the module, line, and column where the node originated
     * @param properties bit-significant properties of the element node. If there are no relevant
     *                   properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */
    @Override
    public void startElement(NodeName nameCode, SchemaType type, AttributeMap attributes, NamespaceMap namespaces, Location location, int properties) throws XPathException {

        currentElement = nameCode;
        AttributeMap atts2 = attributes;
        if (escapeURIAttributes) {
            try {
                atts2 = attributes.apply(att -> {
                    if (!ReceiverOption.contains(att.getProperties(), ReceiverOption.DISABLE_ESCAPING)) {
                        NodeName attName = att.getNodeName();
                        if (isUrlAttribute(nameCode, attName)) {
                            String value = att.getValue();
                            try {
                                String normalized = (isAllAscii(value)
                                                               ? value
                                                               : Normalizer.normalize(value, Normalizer.Form.NFC));
                                return new AttributeInfo(
                                        attName,
                                        att.getType(),
                                        escapeURL(normalized, true, getConfiguration()),
                                        att.getLocation(),
                                        att.getProperties() | ReceiverOption.DISABLE_CHARACTER_MAPS);
                            } catch (XPathException e) {
                                throw new UncheckedXPathException(e);
                            }
                        } else {
                            return att;
                        }
                    } else {
                        return att;
                    }
                });
            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }
        }

        nextReceiver.startElement(nameCode, type, atts2, namespaces, location, properties);
    }

    private static boolean isAllAscii(/*@NotNull*/ CharSequence value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

}

