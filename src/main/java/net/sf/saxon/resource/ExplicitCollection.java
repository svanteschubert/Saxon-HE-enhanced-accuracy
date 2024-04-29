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
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.tree.jiter.MappingJavaIterator;

import java.util.Iterator;
import java.util.List;

/**
 * This class represents a resource collection containing an explicitly enumerated set of resources
 */

public class ExplicitCollection extends AbstractResourceCollection {

    private List<Resource> resources;
    private SpaceStrippingRule whitespaceRules;

    /**
     * Create an explicit collection
     * @param config the Saxon Configuration
     * @param collectionURI the collection URI
     * @param resources the resources in the collection
     */
    public ExplicitCollection(Configuration config, String collectionURI, List<Resource> resources)  {
        super(config);
        if (collectionURI == null) {
            throw new NullPointerException();
        }
        this.resources = resources;
        this.collectionURI = collectionURI;
    }

    /**
     * Supply information about the whitespace stripping rules that apply to this collection.
     * This method will only be called when the collection() function is invoked from XSLT.
     *
     * @param rules the space-stripping rules that apply to this collection, derived from
     *              the xsl:strip-space and xsl:preserve-space declarations in the stylesheet
     *              package containing the call to the collection() function.
     * @return true if the collection finder intends to take responsibility for whitespace
     * stripping according to these rules; false if it wishes Saxon itself to post-process
     * any returned XML documents to strip whitespace. Returning true may either indicate
     * that the collection finder will strip whitespace before returning a document, or it
     * may indicate that it does not wish the space stripping rules to be applied.  The
     * default (returned by this method if not overridden) is false.
     */
    @Override
    public boolean stripWhitespace(SpaceStrippingRule rules) {
        this.whitespaceRules = rules;
        return false;
    }

    @Override
    public Iterator<String> getResourceURIs(XPathContext context) {
        return new MappingJavaIterator<Resource, String>(resources.iterator(), r -> r.getResourceURI());
    }

    @Override
    public Iterator<? extends Resource> getResources(final XPathContext context) {
        return resources.iterator();
    }





}
