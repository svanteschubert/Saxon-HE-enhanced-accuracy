////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.XsltExecutable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cache of the stylesheets (as XsltExecutables) used in calls to the fn:transform function, in a stylesheet or query.
 */

public class StylesheetCache {

    private Map<String, XsltExecutable> cacheByText = new ConcurrentHashMap<String, XsltExecutable>();
    private Map<String, XsltExecutable> cacheByLocation = new ConcurrentHashMap<String, XsltExecutable>();
    private Map<NodeInfo, XsltExecutable> cacheByNode = new ConcurrentHashMap<NodeInfo, XsltExecutable>();

    /**
     * Get the stylesheet (XsltExecutable) in the cache associated with the supplied stylesheet text string.
     * @param style the stylesheet text as a string (as supplied in the stylesheet-text option of fn:transform())
     * @return the XsltExecutable corresponding to this stylesheet-text, if already cached.
     */

    public XsltExecutable getStylesheetByText(String style) {
        return cacheByText.get(style);
    }

    /**
     * Get the stylesheet (XsltExecutable) in the cache associated with the supplied stylesheet location string.
     * @param style the URI for the stylesheet location (as supplied in the stylesheet-location option of fn:transform())
     * @return the XsltExecutable corresponding to this stylesheet-location, if already cached.
     */

    public XsltExecutable getStylesheetByLocation(String style) {
        return cacheByLocation.get(style);
    }

    /**
     * Get the stylesheet (XsltExecutable) in the cache associated with the supplied stylesheet node.
     * @param style the root node of the stylesheet (as supplied in the stylesheet-node option of fn:transform())
     * @return the XsltExecutable corresponding to this stylesheet-node, if already cached.
     */

    public XsltExecutable getStylesheetByNode(NodeInfo style) {
        return cacheByNode.get(style);
    }

    /**
     * Set a key-value pair in the cache for stylesheets referenced by stylesheet text.
     * @param style the stylesheet text as a string (as supplied in the stylesheet-text option of fn:transform())
     * @param xsltExecutable the compiled stylesheet
     */

    public void setStylesheetByText(String style, XsltExecutable xsltExecutable) {
        cacheByText.put(style, xsltExecutable);
    }

    /**
     * Set a key-value pair in the cache for stylesheets referenced by stylesheet location.
     * @param style the URI for the stylesheet location (as supplied in the stylesheet-location option of fn:transform())
     * @param xsltExecutable the compiled stylesheet
     */

    public void setStylesheetByLocation(String style, XsltExecutable xsltExecutable) {
        cacheByLocation.put(style, xsltExecutable);
    }

    /**
     * Set a key-value pair in the cache for stylesheets referenced by stylesheet root node.
     * @param style the root node of the stylesheet (as supplied in the stylesheet-node option of fn:transform())
     * @param xsltExecutable the compiled stylesheet
     */

    public void setStylesheetByNode(NodeInfo style, XsltExecutable xsltExecutable) {
        cacheByNode.put(style, xsltExecutable);
    }
}
