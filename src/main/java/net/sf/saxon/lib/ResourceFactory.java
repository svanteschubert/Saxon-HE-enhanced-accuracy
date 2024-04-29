////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.resource.AbstractResourceCollection;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpDelegate;

/**
 * A ResourceFactory is used for constructing a particular type of resource
 */

@FunctionalInterface
@CSharpDelegate(true)
public interface ResourceFactory {
    /**
     * Create a Resource with given content
     * @param context the Saxon evaluation context
     * @param details the stream of bytes making up the binary content of the resource
     * @return the resource
     * @throws XPathException if a failure occurs creating the resource
     */
    Resource makeResource(XPathContext context, AbstractResourceCollection.InputDetails details)
        throws XPathException;
}

