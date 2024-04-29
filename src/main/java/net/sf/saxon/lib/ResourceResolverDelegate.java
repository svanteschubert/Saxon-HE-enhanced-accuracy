////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Source;
import java.util.function.Function;

/**
 * Convenience class to allow a ResourceResolver to be implemented as a lambda expression.
 * Needed for C# transpilation, so that ResourceResolvers can be supplied both as classes
 * implementing an interface, and as lambda functions.
 */

public class ResourceResolverDelegate implements ResourceResolver {

    private final Function<ResourceRequest, Source> lambda;

    /**
     * Create a {@link ResourceResolver} implemented using (typically) a lambda expression
     * @param lambda a function, typically supplied as a lambda expression, to map satisfy
     *               a {@link ResourceRequest} and return a {@link Source}. Note that this
     *               doesn't mean the resource actually has to be fetched: the returned
     *               {@link Source} might simply contain a URI.
     *
     */

    public ResourceResolverDelegate(Function<ResourceRequest, Source> lambda) {
        this.lambda = lambda;
    }

    @Override
    public Source resolve(ResourceRequest request) throws XPathException {
        try {
            return lambda.apply(request);
        } catch (UncheckedXPathException err) {
            throw err.getXPathException();
        }
    }
}


