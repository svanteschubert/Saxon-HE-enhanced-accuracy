////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.ResourceFactory;
import net.sf.saxon.ma.json.ParseJsonFn;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.StringValue;

import java.util.HashMap;
import java.util.Map;

/**
 * A Resource (that is, an item in a collection) holding JSON content
 */

public class JSONResource implements Resource {
    private String href;
    private String jsonStr;
    private AbstractResourceCollection.InputDetails details;

    public final static ResourceFactory FACTORY = (config, details) -> new JSONResource(details);

    /**
     * Create the resource
     *
     * @param details the inputstream holding the JSON content plus details of encoding etc
     */

    public JSONResource(AbstractResourceCollection.InputDetails details) {
        this.href = details.resourceUri;
        if (details.encoding == null) {
            details.encoding = "UTF-8";
        }
        this.details = details;
    }


    @Override
    public String getResourceURI() {
        return href;
    }

    @Override
    public Item getItem(XPathContext context) throws XPathException {
        if (jsonStr == null) {
            jsonStr = details.obtainCharacterContent();
        }
        Map<String, Sequence> options = new HashMap<>();
        options.put("liberal", BooleanValue.FALSE);
        options.put("duplicates", new StringValue("use-first"));
        options.put("escape", BooleanValue.FALSE);
        return ParseJsonFn.parse(jsonStr, options, context);
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
