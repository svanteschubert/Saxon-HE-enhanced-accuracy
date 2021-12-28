////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

/**
 * Filter to change elements in the XHTML, SVG, or MathML namespaces so they have no prefix (that is,
 * to make these the default namespace). This filter must be followed by a NamespaceReducer in case
 * there are any attributes in these namespaces, as this will cause the namespace declarations to
 * be reinstated.
 */

public class XHTMLPrefixRemover extends ProxyReceiver {

    public XHTMLPrefixRemover(Receiver next) {
        super(next);
    }
    /**
     * Is the active namespace one that requires special (X)HTML(5) prefix treatment?
     * @param uri URI of the namespace
     * @return  true if requires special treatment
     */
    private boolean isSpecial(String uri) {
        return uri.equals(NamespaceConstant.XHTML) ||
                uri.equals(NamespaceConstant.SVG) ||
                uri.equals(NamespaceConstant.MATHML);
    }
    /**
     * Notify the start of an element
     */
    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {

        for (NamespaceBinding ns : namespaces) {
            if (isSpecial(ns.getURI())) {
                namespaces = namespaces.remove(ns.getPrefix());
            }
        }

        if (isSpecial(elemName.getURI())) {
            String uri = elemName.getURI();
            if (!elemName.getPrefix().isEmpty()) {
                elemName = new FingerprintedQName("", uri, elemName.getLocalPart());
            }
            namespaces = namespaces.put("", uri);
        }

        for (AttributeInfo att : attributes) {
            if (isSpecial(att.getNodeName().getURI())) {
                namespaces = namespaces.put(att.getNodeName().getPrefix(), att.getNodeName().getURI());
            }
        }

        nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
    }

}
