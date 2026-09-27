////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.lib.Resource;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;

import java.util.function.Supplier;

/**
 * A Resource (that is, an item in a collection) that constructs or retrieves the
 * item on demand using an {@code Supplier<Item>} provided by the caller
 */

public class SuppliedItemResource implements Resource {
    private final Supplier<? extends Item> itemSupplier;
    private final String resourceUri;

    /**
     * Create the resource
     *
     * @param itemSupplier Function to retrieve or construct an item on demand
     * @param resourceUri URI identifying the resource
     */

    public SuppliedItemResource(Supplier<? extends Item> itemSupplier, String resourceUri) {
        this.itemSupplier = itemSupplier;
        this.resourceUri = resourceUri;
    }


    @Override
    public String getResourceURI() {
        return resourceUri;
    }

    @Override
    public Item getItem() throws XPathException {
        return itemSupplier.get();
    }

    /**
     * Get the media type (MIME type) of the resource if known
     * @return null
     */

    @Override
    public String getContentType() {
        return null;
    }


}
