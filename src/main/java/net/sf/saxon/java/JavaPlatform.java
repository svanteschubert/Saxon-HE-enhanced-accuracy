////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.java;

import net.sf.saxon.Configuration;
import net.sf.saxon.Platform;
import net.sf.saxon.dom.DOMEnvelope;
import net.sf.saxon.dom.DOMObjectModel;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.expr.sort.*;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.lib.ModuleURIResolver;
import net.sf.saxon.lib.StandardModuleURIResolver;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.regex.JavaRegularExpression;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.resource.StandardCollectionFinder;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ExternalObjectType;
import net.sf.saxon.xpath.JAXPXPathStaticContext;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamSource;
import java.lang.reflect.Method;
import java.text.CollationKey;
import java.text.Collator;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Implementation of the Platform class containing methods specific to the Java platform
 * (as distinct from .NET). This is a singleton class with no instance data.
 */
public class JavaPlatform implements Platform {

    static boolean tryJdk9 = true;

    /**
     * The constructor is called during the static initialization of the Configuration
     */

    public JavaPlatform() {
    }


    /**
     * Checks if the supplied static context is an instance of the JAXP static context.
     * On Java we create namespace information from the JAXP XPath static context.
     * On the .NET platform we do nothing.
     *
     * @param retainedStaticContext
     * @param sc
     * @return boolean
     * @since 9.7.0.5
     */

    @Override
    public boolean JAXPStaticContextCheck(RetainedStaticContext retainedStaticContext, StaticContext sc) {
        if (sc instanceof JAXPXPathStaticContext && !(((JAXPXPathStaticContext) sc).getNamespaceContext() instanceof NamespaceResolver)) {
            setNamespacesFromJAXP(retainedStaticContext, (JAXPXPathStaticContext) sc);
            return true;
        }
        return false;
    }

    /**
     * Create namespace information from the JAXP XPath static context. This
     * case needs special treatment because the JAXP static context holds namespace information
     * using a NamespaceContext object, which (absurdly) offers no way to iterate over all the
     * contained namespaces.
     *
     * @param retainedStaticContext the retained static context to be updated
     * @param sc                    JAXP static context
     */

    private void setNamespacesFromJAXP(RetainedStaticContext retainedStaticContext, JAXPXPathStaticContext sc) {
        final NamespaceContext nc = sc.getNamespaceContext();
        retainedStaticContext.setNamespaces(new NamespaceResolver() {

            @Override
            public String getURIForPrefix(String prefix, boolean useDefault) {
                return nc.getNamespaceURI(prefix);
            }

            @Override
            public Iterator<String> iteratePrefixes() {
                throw new UnsupportedOperationException();
            }
        });

    }

    /**
     * Perform platform-specific initialization of the configuration
     */

    @Override
    public void initialize(Configuration config) {
        config.registerExternalObjectModel(DOMEnvelope.getInstance());
        config.registerExternalObjectModel(DOMObjectModel.getInstance());
        config.setCollectionFinder(new StandardCollectionFinder());
    }

    /**
     * Return true if this is the Java platform
     */

    @Override
    public boolean isJava() {
        return true;
    }

    /**
     * Return true if this is the .NET platform
     */

    @Override
    public boolean isDotNet() {
        return false;
    }

    /**
     * Get the platform version
     */

    @Override
    public String getPlatformVersion() {
        return "Java version " + System.getProperty("java.version");
    }

    /**
     * Get a suffix letter to add to the Saxon version number to identify the platform
     */

    @Override
    public String getPlatformSuffix() {
        return "J";
    }


    /**
     * Get a parser by instantiating the SAXParserFactory
     *
     * @return the parser (XMLReader)
     */

    @Override
    public XMLReader loadParser() {
        XMLReader parser;
        try {
            parser = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        } catch (ParserConfigurationException | SAXException err) {
            throw new TransformerFactoryConfigurationError(err);
        }
        return parser;
    }

    /**
     * Get a parser suitable for parsing XML fragments
     *
     * <p>For background, see bugs 4127 and 4253. The problem is that to implement parse-xml-fragment(),
     * we need to set an EntityResolver on the returned parser. But if an Apache catalog resolver is
     * in use, the JAXP system properties may be set so that JAXP returns a custom XMLReader supplied
     * by the catalog resolver, and that XMLReader ignores any attempt to set an EntityResolver. So
     * we bypass JAXP and try to load the built-in parser within the JDK, which we know we can trust;
     * only if this fails (presumably because this is not the Oracle JDK) do we fall back to using a
     * JAXP-supplied parser. And if this turns out to ignore {@code setEntityResolver()} calls, we're
     * hosed.
     * </p>
     *
     * @return the parser (XMLReader)
     */
    @Override
    public XMLReader loadParserForXmlFragments() {
        SAXParserFactory factory = null;
        if (tryJdk9) {
            try {
                // Try the JDK 9 approach (unless we know it ain't gonna work)
                @SuppressWarnings("JavaReflectionMemberAccess")
                Method method = SAXParserFactory.class.getMethod("newDefaultInstance");
                Object result = method.invoke(null);
                factory = (SAXParserFactory)result;
            } catch (Exception e) {
                tryJdk9 = false;
                // keep trying
            }
        }
        if (factory == null) {
            // Try the JDK 8 approach (causes problems in JDK 9 with illegal access warnings)
            try {
                Class<?> factoryClass = Class.forName("com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");
                factory = (SAXParserFactory) factoryClass.newInstance();
            } catch (Exception e2) {
                // keep trying
            }
        }
        if (factory != null) {
            try {
                return factory.newSAXParser().getXMLReader();
            } catch (Exception e) {
                // keep trying
            }
        }
        // Fallback - use whatever parser we can find
        return loadParser();
    }

    /**
     * Convert a StreamSource to either a SAXSource or a PullSource, depending on the native
     * parser of the selected platform
     *
     * @param pipe          the pipeline configuration
     * @param input         the supplied StreamSource
     * @param validation    indicates whether schema validation is required
     * @param dtdValidation indicates whether DTD validation is required
     * @return the PullSource or SAXSource, initialized with a suitable parser, or the original
     * input Source, if now special handling is required or possible. This implementation
     * always returns the original input unchanged.
     */

    @Override
    public Source getParserSource(PipelineConfiguration pipe, StreamSource input, int validation,
                                  boolean dtdValidation) {
        return input;
    }

    /**
     * Obtain a collation with a given set of properties. The set of properties is extensible
     * and variable across platforms. Common properties with example values include lang=ed-GB,
     * strength=primary, case-order=upper-first, ignore-modifiers=yes, alphanumeric=yes.
     * Properties that are not supported are generally ignored; however some errors, such as
     * failing to load a requested class, are fatal.
     *
     * @param config the configuration object
     * @param props  the desired properties of the collation
     * @param uri    the collation URI
     * @return a collation with these properties
     * @throws XPathException if a fatal error occurs
     */

    /*@Nullable*/
    @Override
    public StringCollator makeCollation(Configuration config, Properties props, String uri) throws XPathException {
        return JavaCollationFactory.makeCollation(config, uri, props);
    }

    /**
     * Given a collation, determine whether it is capable of returning collation keys.
     * The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * equal under the equals() method.
     *
     * @param collation the collation, provided as a Comparator
     * @return true if this collation can supply collation keys
     */

    @Override
    public boolean canReturnCollationKeys(StringCollator collation) {
        return !(collation instanceof SimpleCollation) ||
                ((SimpleCollation) collation).getComparator() instanceof Collator;
    }

    /**
     * Given a collation, get a collation key. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * compare correctly under the equals() method.
     *
     * @throws ClassCastException if the collation is not one that is capable of supplying
     *                            collation keys (this should have been checked in advance)
     */

    @Override
    public AtomicMatchKey getCollationKey(SimpleCollation namedCollation, String value) {
        CollationKey ck = ((Collator) namedCollation.getComparator()).getCollationKey(value);
        return new CollationMatchKey(ck);
    }

    /**
     * No ICU features
     */

    @Override
    public boolean hasICUCollator() {
        return false;
    }

    @Override
    public boolean hasICUNumberer() {
        return false;
    }

    /**
     * If available, make a collation using the ICU-J Library
     *
     * @param uri    the collation URI (which will always be a UCA collation URI as defined in XSLT 3.0)
     * @param config the Saxon configuration
     * @return the collation, or null if not available
     * @throws XPathException if the URI is malformed in some way
     */

    @Override
    public StringCollator makeUcaCollator(String uri, Configuration config) throws XPathException {
        UcaCollatorUsingJava collator = new UcaCollatorUsingJava(uri);
        if ("yes".equals(collator.getProperties().getProperty("numeric"))) {
            return new AlphanumericCollator(collator);
        } else {
            return collator;
        }
    }

    /**
     * Compile a regular expression
     *
     * @param config       the Saxon configuration
     * @param regex        the regular expression as a string
     * @param flags        the value of the flags attribute
     * @param hostLanguage one of "XSD10", "XSD11", "XP20" or "XP30". Also allow combinations, e.g. "XP20/XSD11".
     * @param warnings     if supplied, may capture warnings from the regular expression compiler
     * @return the compiled regular expression
     * @throws net.sf.saxon.trans.XPathException if the regular expression or the flags are invalid
     */
    @Override
    public RegularExpression compileRegularExpression(Configuration config, CharSequence regex, String flags, String hostLanguage, List<String> warnings) throws XPathException {
        // recognize "!" as a flag to mean: use native Java regex syntax
        if (flags.contains("!")) {
            // undocumented
            return new JavaRegularExpression(regex, flags.replace("!", ""));
        } else {
            // recognize implementation-defined flags following a semicolon in the flags string
            boolean useJava = false;
            boolean useSaxon = false;
            int semi = flags.indexOf(';');
            if (semi >= 0) {
                useJava = flags.indexOf('j', semi) >= 0;
                useSaxon = flags.indexOf('s', semi) >= 0;
                flags = flags.substring(0, semi);
            }
            if ("J".equals(config.getDefaultRegexEngine()) && !useSaxon) {
                useJava = true;
            }
            if (useJava) {
                return new JavaRegularExpression(regex, flags);
            } else {
                return new ARegularExpression(regex, flags, hostLanguage, warnings, config);
            }
        }
    }

    /**
     * Add the platform-specific function libraries to a function library list. This version
     * of the method does nothing
     *
     * @param list         the function library list that is to be extended
     * @param config       the Configuration
     * @param hostLanguage the host language, for example Configuration.XQUERY
     */

    public void addFunctionLibraries(FunctionLibraryList list, Configuration config, int hostLanguage) {
        // do nothing
    }

    @Override
    public ExternalObjectType getExternalObjectType(Configuration config, String uri, String localName) {
        throw new UnsupportedOperationException("getExternalObjectType for Java");
    }

    /**
     * Return the name of the directory in which the software is installed (if available)
     *
     * @param edition The edition of the software that is loaded ("HE", "PE", or "EE")
     * @param config  the Saxon configuration
     * @return the name of the directory in which Saxon is installed, if available, or null otherwise
     */

    @Override
    public String getInstallationDirectory(String edition, Configuration config) {
        try {
            return System.getenv("SAXON_HOME");
        } catch (SecurityException e) {
            return null;
        }
    }

    /**
     * Register all the external object models that are provided as standard
     * with the relevant edition of Saxon for this Configuration
     *
     * @since 9.3
     */

    @Override
    public void registerAllBuiltInObjectModels(Configuration config) {
        // No action for Saxon-HE
    }

    /**
     * Set the default XML parser to be loaded by the SAXParserFactory on this platform.
     * Needed because the Apache catalog resolver uses the SAXParserFactory to instantiate
     * a parser, and if not customized this causes a failure on the .NET platform.
     *
     * @since 9.4
     */

    @Override
    public void setDefaultSAXParserFactory(Configuration config) {
        // No action for Saxon on Java
    }

    @Override
    public ModuleURIResolver makeStandardModuleURIResolver(Configuration config) {
        return new StandardModuleURIResolver(config);
    }




}

