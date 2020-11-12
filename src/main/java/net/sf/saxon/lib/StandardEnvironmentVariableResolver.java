////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import java.util.Map;
import java.util.Set;

/**
 * Default implementation of the {@link EnvironmentVariableResolver}. This implementation
 * maps "environment variables" as defined in the XPath context to environment variables
 * delivered by Java using the {@link System#getenv()} method.
 */

public class StandardEnvironmentVariableResolver implements EnvironmentVariableResolver {

    /**
     * Get the list of available environment variables.
     *
     * @return a set of strings; each such string should be an acceptable argument to the
     *         method {@link #getEnvironmentVariable(String)}
     */
    @Override
    public Set<String> getAvailableEnvironmentVariables() {
        Map<String, String> vars = System.getenv();
        return vars.keySet();
    }

    /**
     * Get the value of a specific environment variable
     *
     * @param name the name of the required environment variable
     * @return the value of the named environment variable, or null if the variable is
     *         not defined. The method must not return null if the name is in the list of variables
     *         returned by the method {@link #getAvailableEnvironmentVariables()}
     */
    @Override
    public String getEnvironmentVariable(String name) {
        return System.getenv(name);
    }
}

