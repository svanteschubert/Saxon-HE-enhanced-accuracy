////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;
import java.util.Properties;

/**
 * The MetaTagAdjuster adds a meta element to the content of the head element, indicating
 * the required content type and encoding; it also removes any existing meta element
 * containing this information
 */

public class MetaTagAdjuster extends ProxyReceiver {

    private boolean seekingHead = true;
    private int droppingMetaTags = -1;
    private boolean inMetaTag = false;
    String encoding;
    private String mediaType;
    private int level = 0;
    private boolean isXHTML = false;
    private int htmlVersion = 4;

    /**
     * Create a new MetaTagAdjuster
     *
     * @param next the next receiver in the pipeline
     */

    public MetaTagAdjuster(Receiver next) {
        super(next);
    }

    /**
     * Set output properties
     *
     * @param details the serialization properties
     */

    public void setOutputProperties(Properties details) {
        encoding = details.getProperty(OutputKeys.ENCODING);
        if (encoding == null) {
            encoding = "UTF-8";
        }
        mediaType = details.getProperty(OutputKeys.MEDIA_TYPE);
        if (mediaType == null) {
            mediaType = "text/html";
        }
        String htmlVn = details.getProperty(SaxonOutputKeys.HTML_VERSION);
        if (htmlVn == null && !isXHTML) {
            htmlVn = details.getProperty(OutputKeys.VERSION);
        }
        if (htmlVn != null && htmlVn.startsWith("5")) {
            htmlVersion = 5;
        }
    }

    /**
     * Indicate whether we're handling HTML or XHTML
     */

    public void setIsXHTML(boolean xhtml) {
        isXHTML = xhtml;
    }

    /**
     * Compare a name: case-blindly in the case of HTML, case-sensitive for XHTML
     */

    private boolean comparesEqual(String name1, String name2) {
        if (isXHTML) {
            return name1.equals(name2);
        } else {
            return name1.equalsIgnoreCase(name2);
        }
    }

    private boolean matchesName(NodeName name, String local) {
        if (isXHTML) {
            if (!name.getLocalPart().equals(local)) {
                return false;
            }
            if (htmlVersion == 5) {
                return name.hasURI("") || name.hasURI(NamespaceConstant.XHTML);
            } else {
                return name.hasURI(NamespaceConstant.XHTML);
            }
        } else {
            return name.getLocalPart().equalsIgnoreCase(local);
        }
    }

    /**
     * Notify the start of an element
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties)
            throws XPathException {
        if (droppingMetaTags == level) {
            if (matchesName(elemName, "meta")) {
                // if there was an http-equiv="ContentType" attribute, discard the meta element entirely
                boolean found = false;
                for (AttributeInfo att : attributes) {
                    String name = att.getNodeName().getLocalPart();
                    if (comparesEqual(name, "http-equiv")) {
                        String value = Whitespace.trim(att.getValue());
                        if (value.equalsIgnoreCase("Content-Type")) {
                            // case-blind comparison even for XHTML
                            found = true;
                            break;
                        }
                    }
                }
                inMetaTag = found;
                if (found) {
                    return;
                }
            }
        }
        level++;
        nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
        if (seekingHead && matchesName(elemName, "head")) {
            String headPrefix = elemName.getPrefix();
            String headURI = elemName.getURI();
            FingerprintedQName metaCode = new FingerprintedQName(headPrefix, headURI, "meta");
            AttributeMap atts = EmptyAttributeMap.getInstance();
            atts = atts.put(new AttributeInfo(new NoNamespaceName("http-equiv"),
                              BuiltInAtomicType.UNTYPED_ATOMIC, "Content-Type", Loc.NONE, ReceiverOption.NONE));
            atts = atts.put(new AttributeInfo(new NoNamespaceName("content"),
                              BuiltInAtomicType.UNTYPED_ATOMIC, mediaType + "; charset=" + encoding, Loc.NONE, ReceiverOption.NONE));
            nextReceiver.startElement(metaCode, Untyped.getInstance(), atts, namespaces, location, ReceiverOption.NONE);
            droppingMetaTags = level;
            seekingHead = false;
            nextReceiver.endElement();
        }


    }



    /**
     * End of element
     */

    @Override
    public void endElement() throws XPathException {
        if (inMetaTag) {
            inMetaTag = false;
        } else {
            level--;
            if (droppingMetaTags == level + 1) {
                droppingMetaTags = -1;
            }
            nextReceiver.endElement();
        }
    }

}
