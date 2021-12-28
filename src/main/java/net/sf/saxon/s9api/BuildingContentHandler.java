////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import org.xml.sax.ContentHandler;

/**
 * A SAX {@link ContentHandler} that builds a Saxon tree, and allows the node at the root of the tree
 * to be retrieved on completion.
 * <p>To create a <code>BuildingContentHandler</code> for a particular tree model, use the method
 * {@link net.sf.saxon.s9api.DocumentBuilder#newBuildingContentHandler()}.</p>
 */
public interface BuildingContentHandler extends ContentHandler {

    /**
     * After building the document by writing a sequence of events, retrieve the root node
     * of the constructed document tree
     *
     * @return the root node of the constructed tree. The result is undefined (maybe null, maybe an exception)
     *         if the method is called before successfully completing the sequence of events (of which the last should be
     *         {@link #endDocument}) that constructs the tree.
     * @throws SaxonApiException if any failure occurs
     */

    public XdmNode getDocumentNode() throws SaxonApiException;

}

