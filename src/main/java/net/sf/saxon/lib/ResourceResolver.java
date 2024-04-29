////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpDelegate;

import javax.xml.transform.Source;

/**
 * Interface for processing a resource request to deliver a resource
 *
 * <p>It is a common feature of existing resolver APIs that they chain together. For example,
 * it is often the case that when a new <code>EntityResolver</code> is added to an <code>XMLReader</code>,
 * it is configured so that the existing (possibly underlying, implementation defined) resolver will be called
 * if the newly specified resolver fails to resolve the URI. Two <code>ResourceResolvers</code> can be chained
 * together into a single <code>ResourceResolver</code> by using the {@link ChainedResourceResolver} class.</p>
 *
 * <p>The resolver methods in this class will generally return <code>null</code> if the requested resource
 * could not be found. If instead the caller wants a failure to get the resource to be treated as an error,
 * it is possible to request this by setting a property on the {@link CatalogResourceResolver}.</p>
 *
 * <p>The usual mechanism for resolving a URI is to create a {@link ResourceRequest}, and pass it to a sequence
 * of {@link ResourceResolver}s using the method {@link ResourceRequest#resolve}. This will invoke each resolver
 * in turn until one of them returns a non-null result.</p>
 */


@FunctionalInterface
@CSharpDelegate(false)
public interface ResourceResolver {

    /**
     * Process a resource request to deliver a resource
     * @param request the resource request
     * @return the returned Source; or null to delegate resolution to another resolver. The type of Source
     * must correspond to the type of resource requested: for non-XML resources, it should generally be a
     * <code>StreamSource</code>.
     * @throws XPathException if the request is invalid in some way, or if the identified resource is unsuitable,
     * or if resolution is to fail rather than being delegated to another resolver.
     */

    Source resolve(ResourceRequest request) throws XPathException;

}

