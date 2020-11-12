////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.serialize.codenorm.Normalizer;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;

/**
 * This class is used as a filter on the serialization pipeline; it performs the function
 * of escaping URI-valued attributes in HTML
 *
 * @author Michael H. Kay
 */

public class HTMLURIEscaper extends ProxyReceiver {

    /**
     * Table of attributes whose value is a URL
     */

    // we use two HashMaps to avoid unnecessary string concatenations

    private static HTMLTagHashSet urlAttributes = new HTMLTagHashSet(47);
    private static HTMLTagHashSet urlCombinations = new HTMLTagHashSet(101);

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

    private static void setUrlAttribute(String element, String attribute) {
        urlAttributes.add(attribute);
        urlCombinations.add(element + '+' + attribute);
    }

    public boolean isUrlAttribute(NodeName element, NodeName attribute) {
        if (pool == null) {
            pool = getNamePool();
        }
        String attributeName = attribute.getDisplayName();
        if (!urlAttributes.contains(attributeName)) {
            return false;
        }
        String elementName = element.getDisplayName();
        return urlCombinations.contains(elementName + '+' + attributeName);
    }

    protected NodeName currentElement;
    protected boolean escapeURIAttributes = true;
    protected NamePool pool;

    public HTMLURIEscaper(Receiver nextReceiver) {
        super(nextReceiver);
    }

    /**
     * Start of a document node.
     * @param properties
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        nextReceiver.startDocument(properties);
        pool = getPipelineConfiguration().getConfiguration().getNamePool();
    }

    /**
     * Notify the start of an element
     */

    @Override
    public void startElement(NodeName nameCode, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
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
                                return new AttributeInfo(
                                        att.getNodeName(),
                                        att.getType(),
                                        escapeURL(value, true, getConfiguration()).toString(),
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
        nextReceiver.startElement(nameCode, type,
                                  atts2,
                                  namespaces,
                                  location, properties);
    }

    /**
     * Escape a URI according to the HTML rules: that is, a non-ASCII character (specifically,
     * a character outside the range 32 - 126) is replaced by the %HH encoding of the octets in
     * its UTF-8 representation
     *
     * @param url       the URI to be escaped
     * @param normalize
     * @return the URI after escaping non-ASCII characters
     */

    /*@NotNull*/
    public static CharSequence escapeURL(CharSequence url, boolean normalize, Configuration config) throws XPathException {
        // optimize for the common case where the string is all ASCII characters
        for (int i = url.length() - 1; i >= 0; i--) {
            char ch = url.charAt(i);
            if (ch < 32 || ch > 126) {
                if (normalize) {
                    CharSequence normalized = Normalizer.make(Normalizer.C, config).normalize(url);
                    return reallyEscapeURL(normalized);
                } else {
                    return reallyEscapeURL(url);
                }
            }
        }
        return url;
    }

    private static CharSequence reallyEscapeURL(CharSequence url) {
        FastStringBuffer sb = new FastStringBuffer(url.length() + 20);
        final String hex = "0123456789ABCDEF";
        byte[] array = new byte[4];

        for (int i = 0; i < url.length(); i++) {
            char ch = url.charAt(i);
            if (ch < 32 || ch > 126) {
                int used = UTF8CharacterSet.getUTF8Encoding(ch,
                        (i + 1 < url.length() ? url.charAt(i + 1) : ' '), array);
                for (int b = 0; b < used; b++) {
                    //int v = (array[b]>=0 ? array[b] : 256 + array[b]);
                    int v = ((int) array[b]) & 0xff;
                    sb.cat('%');
                    sb.cat(hex.charAt(v / 16));
                    sb.cat(hex.charAt(v % 16));
                }

            } else {
                sb.cat(ch);
            }
        }
        return sb;
    }
}

