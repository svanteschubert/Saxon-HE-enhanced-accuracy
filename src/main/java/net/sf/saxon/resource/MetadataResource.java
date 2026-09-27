////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.SimpleLazyFunction;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.ma.map.StringMapBuilder;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.Map;

/**
 * Represents information about a resource, as well as a pointer to the resource itself
 */
public class MetadataResource implements Resource {

    private final Map<String, GroundedValue> properties;
    private final String resourceURI;
    private final Resource content;

    public MetadataResource(String resourceURI, Resource content, Map<String, GroundedValue> properties, XPathContext context) {
        this.resourceURI = resourceURI;
        this.content = content;
        this.properties = properties;
    }

    @Override
    public String getContentType() {
        return content.getContentType();
    }

    @Override
    public String getResourceURI() {
        return resourceURI;
    }

    @Override
    public Item getItem() throws XPathException {

        // Create a map for the result
        StringMapBuilder builder = new StringMapBuilder(10);

        // Add the custom properties of the resource
        for (Map.Entry<String, GroundedValue> entry : properties.entrySet()) {
             builder.put(StringView.of(entry.getKey()), entry.getValue());
        }

        // Add the resourceURI of the resource as the "name" property
        builder.put(new Twine8("name"), StringValue.makeStringValue(resourceURI));

        // Add a fetch() function, which can be used to fetch the resource
        SimpleLazyFunction fetcherFunction = new SimpleLazyFunction(content::getItem, SequenceType.SINGLE_ITEM);

        builder.put(new Twine8("fetch"), fetcherFunction);
        return builder.getCompletedMap();
    }
}

