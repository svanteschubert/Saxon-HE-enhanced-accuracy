////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import java.util.Set;

/**
 * This interface defines a Saxon plug-in used to resolve calls on the XPath 3.0
 * functions available-environment-variables() and environment-variable(). The standard
 * implementation reads environment variables using the Java method {@link System#getenv()};
 * this can be overridden by a user-provided implementation that resolves environment variables
 * any way it likes.
 */
public interface EnvironmentVariableResolver {

    /**
     * Get the list of available environment variables.
     *
     * @return a set of strings; each such string should be an acceptable argument to the
     *         method {@link #getEnvironmentVariable(String)}
     */

    public Set<String> getAvailableEnvironmentVariables();

    /**
     * Get the value of a specific environment variable
     *
     * @param name the name of the required environment variable
     * @return the value of the named environment variable, or null if the variable is
     *         not defined. The method must not return null if the name is in the list of variables
     *         returned by the method {@link #getAvailableEnvironmentVariables()}
     */

    public String getEnvironmentVariable(String name);
}

