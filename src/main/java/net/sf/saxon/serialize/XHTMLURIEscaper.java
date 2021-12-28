////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.serialize.codenorm.Normalizer;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

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

    private static HashSet<String> urlTable = new HashSet<String>(70);
    private static HashSet<String> attTable = new HashSet<String>(20);

    private static void setUrlAttribute(String element, String attribute) {
        attTable.add(attribute);
        urlTable.add(element + "+" + attribute);
    }

    static {
        setUrlAttribute("form", "action");
        setUrlAttribute("object", "archive");
        setUrlAttribute("body", "background");
        setUrlAttribute("q", "cite");
        setUrlAttribute("blockquote", "cite");
        setUrlAttribute("del", "cite");
        setUrlAttribute("ins", "cite");
        setUrlAttribute("object", "classid");
        setUrlAttribute("object", "codebase");
        setUrlAttribute("applet", "codebase");
        setUrlAttribute("object", "data");
        setUrlAttribute("button", "datasrc");
        setUrlAttribute("div", "datasrc");
        setUrlAttribute("input", "datasrc");
        setUrlAttribute("object", "datasrc");
        setUrlAttribute("select", "datasrc");
        setUrlAttribute("span", "datasrc");
        setUrlAttribute("table", "datasrc");
        setUrlAttribute("textarea", "datasrc");
        setUrlAttribute("script", "for");
        setUrlAttribute("a", "href");
        setUrlAttribute("a", "name");       // see second note in section B.2.1 of HTML 4 specification
        setUrlAttribute("area", "href");
        setUrlAttribute("link", "href");
        setUrlAttribute("base", "href");
        setUrlAttribute("img", "longdesc");
        setUrlAttribute("frame", "longdesc");
        setUrlAttribute("iframe", "longdesc");
        setUrlAttribute("head", "profile");
        setUrlAttribute("script", "src");
        setUrlAttribute("input", "src");
        setUrlAttribute("frame", "src");
        setUrlAttribute("iframe", "src");
        setUrlAttribute("img", "src");
        setUrlAttribute("img", "usemap");
        setUrlAttribute("input", "usemap");
        setUrlAttribute("object", "usemap");
    }

    public XHTMLURIEscaper(Receiver next) {
        super(next);
    }

    /**
     * Determine whether a given attribute is a URL attribute
     */

    private static boolean isURLAttribute(NodeName elcode, NodeName atcode) {
        if (!elcode.hasURI(NamespaceConstant.XHTML)) {
            return false;
        }
        if (!atcode.hasURI("")) {
            return false;
        }
        String attName = atcode.getLocalPart();
        return attTable.contains(attName) && urlTable.contains(elcode.getLocalPart() + "+" + attName);
    }

    /**
     * Notify the start of an element
     *  @param nameCode
     * @param type
     * @param attributes
     * @param namespaces
     * @param location
     * @param properties
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
                                CharSequence normalized = (isAllAscii(value)
                                                               ? value
                                                               : Normalizer.make(Normalizer.C, getConfiguration()).normalize(value));
                                return new AttributeInfo(
                                        attName,
                                        att.getType(),
                                        escapeURL(normalized, true, getConfiguration()).toString(),
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

