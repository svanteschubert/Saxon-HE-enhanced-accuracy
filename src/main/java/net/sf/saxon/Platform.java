////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.expr.sort.SimpleCollation;
import net.sf.saxon.lib.ModuleURIResolver;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ExternalObjectType;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.util.List;
import java.util.Properties;

/**
 * This interface provides access to methods whose implementation depends on the chosen platform
 * (typically Java or .NET)
 */
public interface Platform {

    /**
     * Perform platform-specific initialization of the configuration
     *
     * @param config the Saxon Configuration
     */

    void initialize(Configuration config);

    /**
     * Return true if this is the Java platform
     *
     * @return true if this is the Java platform
     */

    boolean isJava();

    /**
     * Return true if this is the .NET platform
     *
     * @return true if this is the .NET platform
     */

    boolean isDotNet();

    /**
     * Get the platform version
     *
     * @return the version of the platform, for example "Java version 1.5.09"
     */

    String getPlatformVersion();

    /**
     * Get a suffix letter to add to the Saxon version number to identify the platform
     *
     * @return "J" for Java, "N" for .NET
     */

    String getPlatformSuffix();

    /**
     * Get a parser by instantiating the SAXParserFactory
     *
     * @return the parser (XMLReader)
     */

    XMLReader loadParser();


    /**
     * Get an XML parser suitable for use for reading XML fragments. This may differ
     * from the parser used for reading complete XML documents, because some parsers
     * may lack the capability to read fragments. See bug 4253.
     *
     * @return the parser (XMLReader)
     */

    XMLReader loadParserForXmlFragments();

    /**
     * Convert a StreamSource to a different kind of Source, depending on the native
     * parser of the selected platform
     *
     * @param pipe          the pipeline Configuration
     * @param input         the supplied StreamSource
     * @param validation    required validation mode, for example Validation.STRICT
     * @param dtdValidation true if DTD-based input validation is required
     * @return the PullSource or SAXSource, initialized with a suitable parser, or the original
     *         input Source, if now special handling is required or possible
     */

    Source getParserSource(PipelineConfiguration pipe, StreamSource input,
                           int validation, boolean dtdValidation);

    /**
     * Obtain a collation with a given set of properties. The set of properties is extensible
     * and variable across platforms. Common properties with example values include lang=en-GB,
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
    StringCollator makeCollation(Configuration config, Properties props, String uri) throws XPathException;

    /**
     * Given a collation, determine whether it is capable of returning collation keys.
     * The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * equal under the equals() method.
     *
     * @param collation the collation being examined, provided as a Comparator
     * @return true if this collation can supply collation keys
     */

    boolean canReturnCollationKeys(StringCollator collation);

    /**
     * Given a collation, get a collation key. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * equal under the equals() method.
     *
     * @param namedCollation the collation in use
     * @param value          the string whose collation key is required
     * @return a representation of the collation key, such that two collation keys are
     *         equal() if and only if the string values they represent are equal under the specified collation.
     * @throws ClassCastException if the collation is not one that is capable of supplying
     *                            collation keys (this should have been checked in advance)
     */

    AtomicMatchKey getCollationKey(SimpleCollation namedCollation, String value);


    /**
     * Indicate whether the ICU library is available and supports Collations
     * @return   true if the ICU library class for collations appears to be loaded
     */
    boolean hasICUCollator();

    /**
     * Indicate whether the ICU library is available and supports Numberers
     * @return   true if the ICU library class for rule-based numbering appears to be loaded
     */
    boolean hasICUNumberer();

    /**
     * If available, make a UCA collation. Depending on the Saxon edition and licensing,
     * this will use the ICU-J Library if available, or the built-in Java collation support
     * otherwise.
     * @param uri the collation URI (which will always be a UCA collation URI as defined in XSLT 3.0)
     * @param config the Saxon configuration
     * @return the collation, or null if not available
     * @throws XPathException if the URI is malformed in some way
     */

    StringCollator makeUcaCollator(String uri, Configuration config)  throws XPathException;

    /**
     * Compile a regular expression
     *
     *
     * @param config       the Saxon configuration
     * @param regex        the regular expression as a string
     * @param flags        the value of the flags attribute
     * @param hostLanguage one of "XSD10", "XSD11", XP20" or "XP30". Also allow combinations, e.g. "XP20/XSD11".
     * @param warnings     if non-null, any warnings from the regular expression compiler will be added to this list.
     *                     If null, the warnings are ignored.
     * @return the compiled regular expression
     * @throws XPathException if the regular expression or the flags are invalid
     */

    RegularExpression compileRegularExpression(Configuration config, CharSequence regex, String flags, String hostLanguage, List<String> warnings)
            throws XPathException;

    /**
     * Get a SchemaType representing a wrapped external (Java or .NET) object
     *
     * @param config    the Saxon Configuration
     * @param uri       the namespace URI of the schema type
     * @param localName the local name of the schema type
     * @return the SchemaType object representing this type
     */

    ExternalObjectType getExternalObjectType(Configuration config, String uri, String localName);

    /**
     * Return the name of the directory in which the software is installed (if available)
     *
     * @param edition The edition of the software that is loaded ("HE", "PE", or "EE")
     * @param config  the Saxon configuration
     * @return the name of the directory in which Saxon is installed, if available, or null otherwise
     */

    String getInstallationDirectory(String edition, Configuration config);

    /**
     * Register all the external object models that are provided as standard
     * with the relevant edition of Saxon for this Configuration
     *
     * @param config  the Saxon configuration
     * @since 9.3
     */

    void registerAllBuiltInObjectModels(Configuration config);

    /**
     * Set the default XML parser to be loaded by the SAXParserFactory on this platform.
     * Needed because the Apache catalog resolver uses the SAXParserFactory to instantiate
     * a parser, and if not customized this causes a failure on the .NET platform.
     *
     * @param config  the Saxon configuration
     * @since 9.4
     */

    void setDefaultSAXParserFactory(Configuration config);


    /**
     *  Checks if the supplied static context is an instance of the JAXP static context.
     *  On Java we create namespace information from the JAXP XPath static context.
     *  On the .NET platform we do nothing.
     *  @param retainedStaticContext If the static context is a JAXPStaticContext, then its namespaces
     *                               are used to update the retainedStaticContext
     *  @param sc the possible JAXPStaticContext
     *  @return boolean
     *  @since 9.7.0.5
     *
     */

    boolean JAXPStaticContextCheck(RetainedStaticContext retainedStaticContext, StaticContext sc);

    /**
     * Make an instance of the default module URI resolver for this platform
     * @param config  the Saxon configuration
     * @return an instance of ModuleURIResolver
     * @since 9.7.0.2
     */

    ModuleURIResolver makeStandardModuleURIResolver(Configuration config);



}

