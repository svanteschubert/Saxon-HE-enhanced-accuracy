////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;

/**
 * FailedResource represents an item in a collection that could not be processed because of some kind of error
 */
public class FailedResource implements Resource {

    private String uri;
    private XPathException error;

    /**
     * Create a FailedResource
     * @param uri the URI of the resource
     * @param error the error that occurred when attempting to process the resource
     */

    public FailedResource(String uri, XPathException error) {
        this.uri = uri;
        this.error = error;
    }

    /**
     * Get the media type (MIME type) of the resource if known
     *
     * @return always null for this kind of resource
     */
    @Override
    public String getContentType() {
        return null;
    }

    /**
     * Get a URI that identifies this resource
     *
     * @return a URI identifying this resource
     */
    @Override
    public String getResourceURI() {
        return uri;
    }

    /**
     * Get an XDM Item holding the contents of this resource.  This method always
     * throws the error associated with the resource.
     *
     * @param context the XPath evaluation context
     * @return an item holding the contents of the resource. This version of the method
     * never returns an item; it always throws an error
     * @throws XPathException if a failure occurs materializing the resource
     */

    @Override
    public Item getItem(XPathContext context) throws XPathException {
        throw error;
    }

    /**
     * Get the underlying error
     */

    public XPathException getError() {
        return error;
    }
}

