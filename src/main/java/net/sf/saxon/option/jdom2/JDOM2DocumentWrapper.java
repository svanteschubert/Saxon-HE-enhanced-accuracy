////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.jdom2;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.GenericTreeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.tree.iter.AxisIterator;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;

import java.util.HashMap;
import java.util.List;

/**
 * The tree information for a tree acting as a wrapper for a JDOM2 Document.
 *
 * @since 9.7: this class no longer implements NodeInfo; the document node itself
 * is now an instance of JDOM2NodeWrapper.
 */


public class JDOM2DocumentWrapper extends GenericTreeInfo {

    protected Configuration config;
    protected long documentNumber;
    private HashMap<String, Element> idIndex;
    private HashMap<String, Object> userData;

    /**
     * Create a Saxon wrapper for a JDOM document
     *
     * @param doc     The JDOM document
     * @param config  The Saxon Configuration
     */

    public JDOM2DocumentWrapper(Document doc, Configuration config) {
        super(config);
        if (!config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
            config.requireProfessionalLicense("JDOM2");
        }
        setRootNode(wrap(doc));
        setSystemId(doc.getBaseURI());
    }


    /**
     * Wrap a node in the JDOM document.
     *
     * @param node The node to be wrapped. This must be a node in the same document
     *             (the system does not check for this).
     * @return the wrapping NodeInfo object
     */

    public JDOM2NodeWrapper wrap(Object node) {
        return JDOM2NodeWrapper.makeWrapper(node, this);
    }

    /**
     * Get the element with a given ID, if any
     *
     * @param id        the required ID value
     * @param getParent
     * @return the element node with the given ID if there is one, otherwise null.
     */

    @Override
    public NodeInfo selectID(String id, boolean getParent) {
        if (idIndex == null) {
            idIndex = new HashMap<String, Element>(100);
            AxisIterator iter = getRootNode().iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
            NodeInfo node;
            while ((node = iter.next()) != null) {
                Element element = (Element) ((JDOM2NodeWrapper) node).node;
                List attributes = element.getAttributes();
                for (Object attribute : attributes) {
                    Attribute att = (Attribute) attribute;
                    if (att.getAttributeType() == Attribute.ID_TYPE) {
                        idIndex.put(att.getValue(), element);
                    }
                }
            }
        }
        Element element = idIndex.get(id);
        return element == null ? null : wrap(element);
    }

}

// Original Code is Copyright (c) 2009-2020 Saxonica Limited
