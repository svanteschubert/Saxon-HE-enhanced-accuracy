////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.query.StaticQueryContext;

/**
 * Factory class for creating a customized instance of StaticQueryContext
 */

public class StaticQueryContextFactory {

    /**
     * Create a new instance of {@link StaticQueryContext}. The default implementation
     * creates a new {@code StaticQueryContext} as a copy of the {@code Configuration}'s
     * default static query context. User implementations are encouraged to do the same,
     * but this is not mandatory. If the {@code Configuration}'s default static query context
     * is ignored, then configuration settings specific to XQuery (construction mode,
     * default element namespace, default function namespace, etc) are ignored for
     * queries created under this {@code StaticQueryContext}
     *
     * @param config the configuration
     * @param copyFromDefault true if the properties of the new static query context object
     *                        are to be copied from the default query context object held
     *                        in the Configuration
     * @return the new {@code StaticQueryContext} instance
     */
    
    public StaticQueryContext newStaticQueryContext(Configuration config, boolean copyFromDefault) {
        return new StaticQueryContext(config, copyFromDefault);
    }
}
