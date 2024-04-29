////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.ResourceFactory;
import net.sf.saxon.ma.json.ParseJsonFn;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;

import java.util.HashMap;
import java.util.Map;

/**
 * A Resource (that is, an item in a collection) holding JSON content
 */

public class JSONResource implements Resource {
    private final Configuration config;
    private final String href;
    private String jsonStr;
    private Item item;
    private final XPathContext context;
    private AbstractResourceCollection.InputDetails details;

    public final static ResourceFactory FACTORY = (context, details) -> new JSONResource(context, details);

    /**
     * Create the resource
     *
     * @param context XPath dynamic context
     * @param details the input stream holding the JSON content plus details of encoding etc
     */

    public JSONResource(XPathContext context, AbstractResourceCollection.InputDetails details) {
        this.config = context.getConfiguration();
        this.href = details.resourceUri;
        this.context = context;
        if (details.encoding == null) {
            details.encoding = "UTF-8";
        }
        this.details = details;
    }

//    public JSONResource(String uri, MapItem map) {
//        this.item = map;
//        this.href = uri;
//    }
//
//    public JSONResource(String uri, ArrayItem array) {
//        this.item = array;
//        this.href = uri;
//    }


    @Override
    public String getResourceURI() {
        return href;
    }

    @Override
    public Item getItem() throws XPathException {
        if (item != null) {
            return item;
        }
        if (jsonStr == null) {
            jsonStr = details.obtainCharacterContent(config);
            if (jsonStr == null) {
                // implies errors are being ignored
                return null;
            }
        }
        Map<String, GroundedValue> options = new HashMap<>();
        options.put("liberal", BooleanValue.FALSE);
        options.put("duplicates", StringValue.bmp("use-first"));
        options.put("escape", BooleanValue.FALSE);
        return item = ParseJsonFn.parse(jsonStr, options, context);
    }

    /**
     * Get the media type (MIME type) of the resource if known
     *
     * @return the string "application/json"
     */

    @Override
    public String getContentType() {
        return "application/json";
    }


}
