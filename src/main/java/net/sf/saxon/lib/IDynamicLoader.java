////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.trans.XPathException;

import java.io.InputStream;

/**
 * Interface to a class used to perform dynamic loading of classes such as
 * user-hook implementations, as well as product-supplied resources like stylesheets
 * and DTDs.
 */
public interface IDynamicLoader {

    /**
     * Set the ClassLoader to be used
     * @param classLoader the ClassLoader to be used
     */

    void setClassLoader(ClassLoader classLoader);

    /**
     * Load a class using the class name provided.
     * Note that the method does not check that the object is of the right class.
     * <p>This method is intended for internal use only.</p>
     *
     * @param className   A string containing the name of the
     *                    class, for example "com.microstar.sax.LarkDriver"
     * @param traceOut    if diagnostic tracing is required, the destination for the output; otherwise null
     * @param classLoader The ClassLoader to be used to load the class. If this is null, then
     *                    the implementation uses its own class loader.
     * @return an instance of the class named, or null if it is not
     *         loadable.
     * @throws XPathException if the class cannot be loaded.
     */

    Class<?> getClass(String className, Logger traceOut, ClassLoader classLoader) throws XPathException;

    /**
     * Instantiate a class using the class name provided.
     * Note that the method does not check that the object is of the right class.
     * <p>This method is intended for internal use only.</p>
     *
     * @param className   A string containing the name of the
     *                    class, for example "com.microstar.sax.LarkDriver"
     * @param classLoader The ClassLoader to be used to load the class. If this is null, then
     *                    the implementation uses its own class loader.
     * @return an instance of the class named, or null if it is not
     *         loadable.
     * @throws XPathException if the class cannot be loaded.
     */

    Object getInstance(String className, /*@Nullable*/ ClassLoader classLoader) throws XPathException;

    /**
     * Instantiate a class using the class name provided, with the option of tracing
     * Note that the method does not check that the object is of the right class.
     * <p>This method is intended for internal use only.</p>
     *
     * @param className   A string containing the name of the
     *                    class, for example "com.microstar.sax.LarkDriver"
     * @param traceOut    if attempts to load classes are to be traced, then the destination
     *                    for the trace output; otherwise null
     * @param classLoader The ClassLoader to be used to load the class. If this is null, then
     *                    the implementation uses its own choice of ClassLoader
     * @return an instance of the class named, or null if it is not
     *         loadable.
     * @throws XPathException if the class cannot be loaded.
     */

    Object getInstance(String className, Logger traceOut, /*@Nullable*/ ClassLoader classLoader) throws XPathException;

    /**
     * Get a resource from a supplied URI using the classpath URI scheme.
     * @param name the path name from the URI
     * @return the content of the relevant resource
     */

    InputStream getResourceAsStream(String name);


}

