////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Source;
import java.util.Objects;

/**
 * An ResourceResolver that first tries one supplied ResourceResolver, and if that
 * returns null, falls back to another. Either ResourceResolver may itself
 * be a <code>ChainedResourceResolver</code>, so a chain of any length can be
 * established.
 * @since 11.1
 */

public class ChainedResourceResolver implements ResourceResolver {

    private final ResourceResolver first;
    private final ResourceResolver second;

    /**
     * Create a composite entity resolver
     * @param first the first entity resolver to be used
     * @param second the entity resolver to be used if the first one returns null
     */
    public ChainedResourceResolver(ResourceResolver first, ResourceResolver second) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        this.first = first;
        this.second = second;
    }




    @Override
    public Source resolve(ResourceRequest request) throws XPathException {
        Source is = first.resolve(request);
        if (is == null) {
            is = second.resolve(request);
        }
        return is;
    }


}

