////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.XPathException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

/**
 * This class contains the part of the static context of expressions that (a) can change from one expression
 * to another within a query or stylesheet, and (b) are potentially needed at run-time.
 * <p>From 9.6, the retained static context is available to every expression in the expression tree (previously,
 * expressions only retained that part of the static context needed by the particular expression). For economy,
 * a new RetainedStaticContext object is only created when the context changes: which is fairly rare (for
 * example, it never happens within an XPath expression).</p>
 */
public class RetainedStaticContext implements NamespaceResolver {

    private final Configuration config;
    private PackageData packageData;
    private URI staticBaseUri;
    private String staticBaseUriString;
    private String defaultCollationName;
    private NamespaceResolver namespaces;  // Normally a NamespaceMap, except for the JAXP case
    private NamespaceUri defaultFunctionNamespace = NamespaceUri.FN;
    private NamespaceUri defaultElementNamespace;
    private DecimalFormatManager decimalFormatManager;
    private boolean backwardsCompatibility;


    public RetainedStaticContext(Configuration config) {
        this.config = config;
        packageData = new PackageData(config);
        namespaces = NamespaceMap.emptyMap();
        defaultCollationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
    }

    public RetainedStaticContext(StaticContext sc) {
        this.config = sc.getConfiguration();
        this.packageData = sc.getPackageData();
        if (sc.getStaticBaseURI() != null) {
            staticBaseUriString = sc.getStaticBaseURI();
            try {
                this.staticBaseUri = ExpressionTool.getBaseURI(sc, null, true);
            } catch (XPathException e) {
                staticBaseUri = null;
            }
        }
        this.defaultCollationName = sc.getDefaultCollationName();
        this.decimalFormatManager = sc.getDecimalFormatManager();
        this.defaultElementNamespace = sc.getDefaultElementNamespace();
        defaultFunctionNamespace = sc.getDefaultFunctionNamespace();
        backwardsCompatibility = sc.isInBackwardsCompatibleMode();
        if (!Version.platform.JAXPStaticContextCheck(this, sc)) {
            namespaces = sc.getNamespaceResolver();
        }

    }


    /**
     * Get the Configuration
     *
     * @return the Saxon Configuration object
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set information about the package (unit of compilation
     *
     * @param packageData the package data
     */

    public void setPackageData(PackageData packageData) {
        this.packageData = packageData;
    }

    /**
     * Get information about the package (unit of compilation)
     *
     * @return the package data
     */

    public PackageData getPackageData() {
        return packageData;
    }

    /**
     * Set the static base URI as a string
     *
     * @param baseUri the base URI as a string
     */

    public void setStaticBaseUriString(String baseUri) {
        if (baseUri != null) {
            staticBaseUriString = baseUri;
            try {
                this.staticBaseUri = new URI(baseUri);
            } catch (URISyntaxException e) {
                staticBaseUri = null;
            }
        }
    }

    /**
     * Get the static base URI as a URI.
     *
     * @return the static base URI as a URI if it is known and valid. Return null if the base URI is
     * unknown.
     * @throws XPathException if the static base URI is not a valid URI.
     */

    public URI getStaticBaseUri() throws XPathException {
        if (staticBaseUri == null) {
            if (staticBaseUriString == null || staticBaseUriString.isEmpty()) {
                return null;
            } else {
                throw new XPathException("Supplied static base URI " + staticBaseUriString + " is not a valid URI");
            }
        }
        return staticBaseUri;
    }

    /**
     * Get the static base URI as a string.
     *
     * @return the static base URI as a string, or null if unknown
     */

    public String getStaticBaseUriString() {
        return staticBaseUriString;
    }

    /**
     * Get the name of the default collation for this static context
     *
     * @return the default collation URI
     */

    public String getDefaultCollationName() {
        return defaultCollationName;
    }

    /**
     * Set the name of the default collation for this static context
     *
     * @param defaultCollationName the default collation URI
     */

    public void setDefaultCollationName(String defaultCollationName) {
        this.defaultCollationName = defaultCollationName;
    }

    /**
     * Get the default namespace for functions
     *
     * @return the default namespace for functions
     */

    public NamespaceUri getDefaultFunctionNamespace() {
        return defaultFunctionNamespace;
    }

    /**
     * Set the default namespace for functions
     *
     * @param defaultFunctionNamespace the default namespace for functions
     */

    public void setDefaultFunctionNamespace(NamespaceUri defaultFunctionNamespace) {
        this.defaultFunctionNamespace = defaultFunctionNamespace;
    }

    /**
     * Get the default namespace for elements and types
     *
     * @return the default namespace for elements and types. Return "" if the default is "no namespace"
     */

    public NamespaceUri getDefaultElementNamespace() {
        return defaultElementNamespace == null ? NamespaceUri.NULL : defaultElementNamespace;
    }

    /**
     * Set the default namespace for elements and type
     *
     * @param ns the default namespace for elements and types.
     */

    public void setDefaultElementNamespace(NamespaceUri ns) {
        defaultElementNamespace = ns;
    }

    /**
     * Get the decimal format manager
     *
     * @return the decimal format manager
     */

    public DecimalFormatManager getDecimalFormatManager() {
        return decimalFormatManager;
    }

    /**
     * Set the decimal format manager
     *
     * @param decimalFormatManager the decimal format manager
     */
    public void setDecimalFormatManager(DecimalFormatManager decimalFormatManager) {
        this.decimalFormatManager = decimalFormatManager;
    }

    public boolean isBackwardsCompatibility() {
        return backwardsCompatibility;
    }

    public void setBackwardsCompatibility(boolean backwardsCompatibility) {
        this.backwardsCompatibility = backwardsCompatibility;
    }


    /**
     * Add a namespace binding to the static namespace context ("in-scope namespaces")
     * Not supported when the static context is a JAXP XPath.
     * @param prefix the namespace prefix
     * @param uri    the namespace URI
     */

    public void declareNamespace(String prefix, NamespaceUri uri) {
        if (namespaces instanceof NamespaceMap) {
            namespaces = ((NamespaceMap) namespaces).put(prefix, uri);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix. May be the zero-length string, indicating
     *                   that there is no prefix. This indicates either the default namespace or the
     *                   null namespace, depending on the value of useDefault.
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is "".  If set to false, and the prefix is "", then the
     *                   value "" is returned regardless of the default namespace in force.
     *                   The "default namespace" here means the one bound to the prefix "".
     * @return the uri for the namespace, or null if the prefix is not in scope.
     * The "null namespace" is represented by the pseudo-URI "".
     */
    @Override
    public NamespaceUri getURIForPrefix(String prefix, boolean useDefault) {
        return namespaces.getURIForPrefix(prefix, useDefault);
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     *
     * @return an iterator over all the prefixes for which a namespace binding exists, including
     * the zero-length string to represent the null/absent prefix if it is bound
     */
    @Override
    public Iterator<String> iteratePrefixes() {
        return namespaces.iteratePrefixes();
    }

    /**
     * Test whether this static context declares the same namespaces as another static context
     * @param other the other static context
     * @return true if the namespace bindings (prefix:uri pairs) are the same
     */

    public boolean declaresSameNamespaces(RetainedStaticContext other) {
        return namespaces.equals(other.namespaces);
    }

    public int hashCode() {
        int h = 0x2457cbce;
        if (staticBaseUriString != null) {
            h ^= staticBaseUriString.hashCode();
        }
        h ^= defaultCollationName.hashCode();
        h ^= defaultFunctionNamespace.hashCode();
        h ^= namespaces.hashCode();
        return h;
    }


    public boolean equals(Object other) {
        if (!(other instanceof RetainedStaticContext)) {
            return false;
        }
        RetainedStaticContext r = (RetainedStaticContext) other;
        return ExpressionTool.equalOrNull(staticBaseUriString, r.staticBaseUriString)
                && defaultCollationName.equals(r.defaultCollationName)
                && defaultFunctionNamespace.equals(r.defaultFunctionNamespace)
                && namespaces.equals(r.namespaces);
    }

    /**
     * Set the in-scope namespaces.
     * @param namespaces the in-scope namespaces. The preferred representation is as a NamespaceMap;
     *                   however this is not possible in the case of the JAXP XPath API, which does
     *                   not allow the in-scope namespaces to be enumerated.
     */

    public void setNamespaces(NamespaceResolver namespaces) {
        this.namespaces = namespaces;
    }

    public NamespaceMap getNamespaceMap() {
        // This fails for a JAXP static context, whose namespaces cannot be enumerated
        return NamespaceMap.fromNamespaceResolver(namespaces);
    }


}

