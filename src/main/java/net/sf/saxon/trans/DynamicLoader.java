////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.serialize.MessageEmitter;

import java.io.InputStream;
import java.util.HashMap;

/**
 * Utility class used to perform dynamic loading of user-hook implementations
 */
public class DynamicLoader {

    private ClassLoader classLoader;

    protected HashMap<String, Class> knownClasses = new HashMap<String, Class>(20);

    public DynamicLoader() {
        registerKnownClasses();
    }

    /**
     * Register classes that might be dynamically loaded even though they are contained
     * within Saxon itself. This typically occurs for default implementations of classes that
     * can be substituted or subclassed by the user.
     */

    protected void registerKnownClasses() {
        knownClasses.put("net.sf.saxon.serialize.MessageEmitter", MessageEmitter.class);
        //knownClasses.put("net.sf.saxon.java.JavaPlatform", JavaPlatform.class);  // not available on .NET
        knownClasses.put("net.sf.saxon.Configuration", Configuration.class);
    }

    /**
     * Set a ClassLoader to be used when loading external classes. Examples of classes that are
     * loaded include SAX parsers, localization modules for formatting numbers and dates,
     * extension functions, external object models. In an environment such as Eclipse that uses
     * its own ClassLoader, this ClassLoader should be nominated to ensure that any class loaded
     * by Saxon is identical to a class of the same name loaded by the external environment.
     *
     * @param loader the ClassLoader to be used in this configuration
     */

    public void setClassLoader(ClassLoader loader) {
        classLoader = loader;
    }

    /**
     * Get the ClassLoader supplied using the method {@link #setClassLoader}.
     * If none has been supplied, return null.
     *
     * @return the ClassLoader used in this configuration
     */

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Load a class using the class name provided.
     * Note that the method does not check that the object is of the right class.
     * <p>This method is intended for internal use only.</p>
     *
     * @param className   A string containing the name of the
     *                    class, for example "com.microstar.sax.LarkDriver"
     * @param traceOut    if diagnostic tracing is required, the destination for the output; otherwise null
     * @param classLoader The ClassLoader to be used to load the class. If this is null, then
     *                    the classLoader used will be the first one available of: the classLoader registered
     *                    with the Configuration using {@link #setClassLoader}; the context class loader for
     *                    the current thread; or failing that, the class loader invoked implicitly by a call
     *                    of Class.forName() (which is the ClassLoader that was used to load the Configuration
     *                    object itself).
     * @return an instance of the class named, or null if it is not
     *         loadable.
     * @throws XPathException if the class cannot be loaded.
     */

    public Class getClass(String className, Logger traceOut, ClassLoader classLoader) throws XPathException {
        Class known = knownClasses.get(className);
        if (known != null) {
            return known;
        }

        boolean tracing = traceOut != null;
        if (tracing) {
            traceOut.info("Loading " + className);
        }

        try {
            ClassLoader loader = classLoader;
            if (loader == null) {
                loader = this.classLoader;
            }
            if (loader == null) {
                loader = Thread.currentThread().getContextClassLoader();
            }
            if (loader != null) {
                try {
                    return loader.loadClass(className);
                } catch (Throwable ex) {
                    // Catching Exception is not enough; Java sometimes throws a NoClassDefFoundError
                    return Class.forName(className);
                }
            } else {
                return Class.forName(className);
            }
        } catch (Throwable e) {
            if (tracing) {
                // The exception is often masked, especially when calling extension
                // functions
                traceOut.error("The class " + className + " could not be loaded: " + e.getMessage());
            }
            throw new XPathException("Failed to load " + className + getMissingJarFileMessage(className), e);
        }

    }

    /**
     * Instantiate a class using the class name provided.
     * Note that the method does not check that the object is of the right class.
     * <p>This method is intended for internal use only.</p>
     *
     * @param className   A string containing the name of the
     *                    class, for example "com.microstar.sax.LarkDriver"
     * @param classLoader The ClassLoader to be used to load the class. If this is null, then
     *                    the classLoader used will be the first one available of: the classLoader registered
     *                    with the Configuration using {@link #setClassLoader}; the context class loader for
     *                    the current thread; or failing that, the class loader invoked implicitly by a call
     *                    of Class.forName() (which is the ClassLoader that was used to load the Configuration
     *                    object itself).
     * @return an instance of the class named, or null if it is not
     *         loadable.
     * @throws XPathException if the class cannot be loaded.
     */

    public Object getInstance(String className, /*@Nullable*/ ClassLoader classLoader) throws XPathException {
        Class theclass = getClass(className, null, classLoader);
        try {
            return theclass.newInstance();
        } catch (Exception err) {
            throw new XPathException("Failed to instantiate class " + className +
                    " (does it have a public zero-argument constructor?)", err);
        }
    }

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
     *                    the classLoader used will be the first one available of: the classLoader registered
     *                    with the Configuration using {@link #setClassLoader}; the context class loader for
     *                    the current thread; or failing that, the class loader invoked implicitly by a call
     *                    of Class.forName() (which is the ClassLoader that was used to load the Configuration
     *                    object itself).
     * @return an instance of the class named, or null if it is not
     *         loadable.
     * @throws XPathException if the class cannot be loaded.
     */

    public Object getInstance(String className, Logger traceOut, /*@Nullable*/ ClassLoader classLoader) throws XPathException {
        Class theclass = getClass(className, traceOut, classLoader);
        try {
            return theclass.newInstance();
        } catch (NoClassDefFoundError err) {
            throw new XPathException("Failed to load instance of class " + className + getMissingJarFileMessage(className), err);
        } catch (Exception err) {
            throw new XPathException("Failed to instantiate class " + className, err);
        }
    }

    /**
     * If a Saxon class is in a particular JAR file, identify the JAR file
     * @param className the name of a class
     * @return the name of the JAR file that the class should be found in, if known; otherwise null
     */

    private String getJarFileForClass(String className) {
        if (className.startsWith("net.sf.saxon.option.sql.")) {
            return "saxon-sql-"+ Version.getProductVersion() +".jar";
        } else if (className.startsWith("com.ibm.icu.")) {
            return "icu4j-59.1.jar";
        } else if (className.startsWith("com.saxonica")) {
            return "saxon-"+Version.softwareEdition.toLowerCase()+"-"+Version.getProductVersion() +".jar";
        } else {
            return null;
        }
    }

    private String getMissingJarFileMessage(String className) {
        String jar = getJarFileForClass(className);
        return jar==null ? "" : ". Check that " + jar + " is on the classpath";
    }

    public InputStream getResourceAsStream(String name) {
        ClassLoader loader = getClassLoader();
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        return loader.getResourceAsStream(name);
    }


}

