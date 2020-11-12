////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;

/**
 * A simple implementation of the DOMImplementation interface, for use when accessing
 * Saxon tree structure using the DOM API.
 */

class DOMImplementationImpl implements DOMImplementation {

    /**
     * Test if the DOM implementation implements a specific feature.
     *
     * @param feature The name of the feature to test (case-insensitive).
     * @param version This is the version number of the feature to test.
     * @return <code>true</code> if the feature is implemented in the
     *         specified version, <code>false</code> otherwise. This implementation
     *         returns true if the feature is "XML" or "Core" and the version is null,
     *         "", "3.0", "2.0", or "1.0".
     */

    @Override
    public boolean hasFeature(String feature, String version) {
        return (feature.equalsIgnoreCase("XML") || feature.equalsIgnoreCase("Core")) &&
                (version == null || version.isEmpty() ||
                    version.equals("3.0") || version.equals("2.0") || version.equals("1.0"));
    }

    /**
     * This method returns a specialized object which implements the
     * specialized APIs of the specified feature and version, as specified
     * in .
     *
     * @param feature The name of the feature requested.
     * @param version This is the version number of the feature to test.
     * @return Always returns null in this implementation
     * @since DOM Level 3
     */

    @Override
    public Object getFeature(String feature,
                             String version) {
        return null;
    }


    /**
     * Creates an empty <code>DocumentType</code> node.
     *
     * @param qualifiedName The  qualified name of the document type to be
     *                      created.
     * @param publicId      The external subset public identifier.
     * @param systemId      The external subset system identifier.
     * @return A new <code>DocumentType</code> node with
     *         <code>Node.ownerDocument</code> set to <code>null</code> .
     * @throws org.w3c.dom.DOMException INVALID_CHARACTER_ERR: Raised if the specified qualified name
     *                                  contains an illegal character.
     *                                  <br> NAMESPACE_ERR: Raised if the <code>qualifiedName</code> is
     *                                  malformed.
     * @since DOM Level 2
     */

    @Override
    public DocumentType createDocumentType(String qualifiedName,
                                           String publicId,
                                           String systemId)
            throws DOMException {
        NodeOverNodeInfo.disallowUpdate();
        return null;
    }

    /**
     * Creates an XML <code>Document</code> object of the specified type with
     * its document element.
     *
     * @param namespaceURI  The  namespace URI of the document element to
     *                      create.
     * @param qualifiedName The  qualified name of the document element to be
     *                      created.
     * @param doctype       The type of document to be created or <code>null</code>.
     * @return A new <code>Document</code> object.
     * @throws UnsupportedOperationException always
     * @since DOM Level 2
     */
/*@Nullable*/
    @Override
    public Document createDocument(String namespaceURI,
                                   String qualifiedName,
                                   DocumentType doctype)
            throws UnsupportedOperationException {
        NodeOverNodeInfo.disallowUpdate();
        return null;
    }

}

