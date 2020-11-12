////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;


import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;

/**
 * This interface defines a Resource. The Resource objects belong to a collection.
 * It is used to support the fn:collection() and fn:uri-collection() functions.
 *
 * <p>It is recommended (but is not universally the case) that fetching (and where necessary parsing)
 * the content of a Resource should be delayed until the {@link #getItem} method is called. This means
 * that errors in fetching the resource or parsing its contents may go undetected until the resource
 * is materialized in this way.</p>
 * @since 9.7
 */
public interface Resource {

    /**
     * Get a URI that identifies this resource
     * @return a URI identifying this resource
     */

    String getResourceURI();

    /**
     * Get an XDM Item holding the contents of this resource.
     * @param context the XPath evaluation context
     * @return an item holding the contents of the resource. The type of item will
     * reflect the type of the resource: a document node for XML resources, a string
     * for text resources, a map or array for JSON resources, a base64Binary value
     * for binary resource. May also return null if the resource cannot be materialized
     * and this is not to be treated as an error.
     * @throws XPathException if a failure occurs materializing the resource
     */

    Item getItem(XPathContext context) throws XPathException;

    /**
     * Get the media type (MIME type) of the resource if known
     * @return the media type if known; otherwise null
     */

    String getContentType();


}
