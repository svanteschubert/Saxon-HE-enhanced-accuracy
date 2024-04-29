////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import org.xmlresolver.ResolverFeature;

/**
 * A ResourceResolver that allows properties to be set and examined
 */

public interface ConfigurableResourceResolver {

    /**
     * Calls {@link org.xmlresolver.XMLResolverConfiguration#getFeature} on the underlying
     * resolver configuration.
     *
     * @param feature The feature setting
     * @param <T>     The feature type
     * @return The value for the specified feature.
     */
    <T> T getFeature(ResolverFeature<T> feature);

    /**
     * Calls {@link org.xmlresolver.XMLResolverConfiguration#setFeature} on the underlying
     * resolver configuration.
     *
     * @param feature The feature setting
     * @param value   The desired value for that feature
     * @param <T>     The feature type
     * @throws NullPointerException if the underlying resolver is null. Some features will
     *                              also throw this exception if the value provided is null and that's not a meaningful
     *                              feature value.
     */
    <T> void setFeature(ResolverFeature<T> feature, T value);
}

