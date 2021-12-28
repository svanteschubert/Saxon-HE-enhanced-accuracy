////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.xpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.sxpath.AbstractStaticContext;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaException;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.Source;
import javax.xml.xpath.XPathFunctionResolver;
import javax.xml.xpath.XPathVariableResolver;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

/**
 * A JAXPXPathStaticContext provides a context for parsing an XPath expression
 * in a context other than a stylesheet. In particular, it is used to support
 * the JAXP 1.3 XPath API. The JAXP API does not actually expose the StaticContext
 * object directly; rather, the static context (namespaces, variables, and functions)
 * is manipulated through the XPath object, implemented in Saxon by the {@link XPathEvaluator}
 */

public class JAXPXPathStaticContext extends AbstractStaticContext
        implements NamespaceResolver {

    private SlotManager stackFrameMap;
    private XPathFunctionLibrary xpathFunctionLibrary;

    private NamespaceContext namespaceContext = new MinimalNamespaceContext();
    private XPathVariableResolver variableResolver;

    /**
     * Create a JAXPXPathStaticContext using a specific Configuration.
     *
     * @param config the Configuration. For schema-aware XPath expressions, this must be an EnterpriseConfiguration.
     */

    public JAXPXPathStaticContext(/*@NotNull*/ Configuration config) {
        setConfiguration(config);
        stackFrameMap = config.makeSlotManager();
        setDefaultFunctionLibrary(31);
        xpathFunctionLibrary = new XPathFunctionLibrary();
        addFunctionLibrary(xpathFunctionLibrary);
        setPackageData(new PackageData(getConfiguration()));
    }

    /**
     * Supply the NamespaceContext used to resolve namespaces.
     */

    public void setNamespaceContext(NamespaceContext context) {
        this.namespaceContext = context;
    }

    /**
     * Get the NamespaceContext that was set using {@link #setNamespaceContext}
     */

    public NamespaceContext getNamespaceContext() {
        return namespaceContext;
    }

    /**
     * Get the stack frame map containing the slot number allocations for the variables declared
     * in this static context
     */

    public SlotManager getStackFrameMap() {
        return stackFrameMap;
    }

    /**
     * Set an XPathVariableResolver. This is used to resolve variable references
     * if no variable has been explicitly declared.
     *
     * @param resolver A JAXP 1.3 XPathVariableResolver
     */

    public void setXPathVariableResolver(XPathVariableResolver resolver) {
        this.variableResolver = resolver;
    }

    /**
     * Get the XPathVariableResolver
     */

    public XPathVariableResolver getXPathVariableResolver() {
        return variableResolver;
    }

    public void setXPathFunctionResolver(XPathFunctionResolver xPathFunctionResolver) {
        if (xpathFunctionLibrary != null) {
            xpathFunctionLibrary.setXPathFunctionResolver(xPathFunctionResolver);
        }
    }

    /*@Nullable*/
    public XPathFunctionResolver getXPathFunctionResolver() {
        if (xpathFunctionLibrary != null) {
            return xpathFunctionLibrary.getXPathFunctionResolver();
        } else {
            return null;
        }
    }

    /*@NotNull*/
    @Override
    public NamespaceResolver getNamespaceResolver() {
        return this;
    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope. This method searches
     * any namespace context supplied using {@link #setNamespaceContext(javax.xml.namespace.NamespaceContext)}.
     *
     * @param prefix     the namespace prefix
     * @param useDefault true if the default namespace for elements and types is to be used when the
     *                   prefix is ""
     * @return the uri for the namespace, or null if the prefix is not in scope.
     *         Return "" if the prefix maps to the null namespace.
     */

    @Override
    public String getURIForPrefix(/*@NotNull*/ String prefix, boolean useDefault) {
        if (prefix.isEmpty()) {
            if (useDefault) {
                return getDefaultElementNamespace();
            } else {
                return NamespaceConstant.NULL;
            }
        } else {
            return namespaceContext.getNamespaceURI(prefix);
        }
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This method is implemented
     * only in the case where the NamespaceContext supplied using {@link #setNamespaceContext} is an
     * instance of Saxon's {@link NamespaceResolver} class. In other cases the method throws an
     * UnsupportedOperationException
     *
     * @return an iterator over all the inscope namespace prefixes, if available
     * @throws UnsupportedOperationException if the NamespaceContext object is not a NamespaceResolver.
     */

    @Override
    public Iterator<String> iteratePrefixes() {
        if (namespaceContext instanceof NamespaceResolver) {
            return ((NamespaceResolver) namespaceContext).iteratePrefixes();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Bind a variable used in an XPath Expression to the XSLVariable element in which it is declared.
     * This method is provided for use by the XPath parser, and it should not be called by the user of
     * the API.
     *
     * @param qName the name of the variable to be bound
     * @throws XPathException if no VariableResolver has been supplied.
     */

    /*@NotNull*/
    @Override
    public final Expression bindVariable(StructuredQName qName) throws XPathException {
        // bindVariable is called at compile time, but the JAXP variable resolver
        // is designed to be called at run time. So we need to create a variable now,
        // which will call the variableResolver when called upon to return the run-time value
        if (variableResolver != null) {
            // Note that despite its name, JAXPVariableReference is not actually a VariableReference
            return new JAXPVariableReference(qName, variableResolver);
        } else {
            throw new XPathException(
                    "Variable is used in XPath expression, but no JAXP VariableResolver is available");
        }
    }

    /**
     * Import a schema. This is possible only if Saxon-EE is being used,
     * and if the Configuration is a EnterpriseConfiguration. Having imported a schema, the types
     * defined in that schema become part of the static context.
     *
     * @param source A Source object identifying the schema document to be loaded
     * @throws net.sf.saxon.type.SchemaException
     *                                       if the schema contained in this document is invalid
     * @throws UnsupportedOperationException if the configuration is not schema-aware
     */

    public void importSchema(Source source) throws SchemaException {
        getConfiguration().addSchemaSource(source, getConfiguration().makeErrorReporter());
        setSchemaAware(true);
    }

    /**
     * Determine whether a Schema for a given target namespace has been imported. Note that the
     * in-scope element declarations, attribute declarations and schema types are the types registered
     * with the (schema-aware) configuration, provided that their namespace URI is registered
     * in the static context as being an imported schema namespace. (A consequence of this is that
     * within a Configuration, there can only be one schema for any given namespace, including the
     * null namespace).
     *
     * @return true if schema components for the given namespace have been imported into the
     *         schema-aware configuration
     */

    @Override
    public boolean isImportedSchema(String namespace) {
        return getConfiguration().isSchemaAvailable(namespace);
    }

    /**
     * Get the set of imported schemas
     *
     * @return a Set, the set of URIs representing the names of imported schemas
     */

    @Override
    public Set<String> getImportedSchemaNamespaces() {
        return getConfiguration().getImportedNamespaces();
    }

    /**
     * Define a minimal namespace context for use when no user-defined namespace context has been
     * registered
     */

    private static class MinimalNamespaceContext implements NamespaceContext, NamespaceResolver {

        /**
         * Get the namespace URI bound to a prefix in the current scope.</p>
         *
         * @param prefix the prefix to look up
         * @return Namespace URI bound to prefix in the current scope
         */
        /*@Nullable*/
        @Override
        public String getNamespaceURI(/*@Nullable*/ String prefix) {
            if (prefix == null) {
                throw new IllegalArgumentException("prefix");
            } else if (prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
                return ""; //XMLConstants.NULL_NS_URI;
            } else if (prefix.equals("xml")) {
                return NamespaceConstant.XML;
            } else if (prefix.equals("xs")) {
                return NamespaceConstant.SCHEMA;
            } else if (prefix.equals("xsi")) {
                return NamespaceConstant.SCHEMA_INSTANCE;
            } else if (prefix.equals("saxon")) {
                return NamespaceConstant.SAXON;
            } else {
                return null;
            }
        }

        /**
         * <p>Get prefix bound to Namespace URI in the current scope.</p>
         *
         * @throws UnsupportedOperationException (always)
         */
        /*@NotNull*/
        @Override
        public String getPrefix(String namespaceURI) {
            throw new UnsupportedOperationException();
        }

        /**
         * <p>Get all prefixes bound to a Namespace URI in the current
         *
         * @throws UnsupportedOperationException (always)
         */
        /*@NotNull*/
        @Override
        public Iterator getPrefixes(String namespaceURI) {
            throw new UnsupportedOperationException();
        }

        /**
         * Get an iterator over all the prefixes declared in this namespace context. This will include
         * the default namespace (prefix="") and the XML namespace where appropriate
         */

        @Override
        public Iterator<String> iteratePrefixes() {
            String[] prefixes = {"", "xml", "xs", "xsi", "saxon"};
            return Arrays.asList(prefixes).iterator();
        }

        /**
         * Get the namespace URI corresponding to a given prefix. Return null
         * if the prefix is not in scope.
         *
         * @param prefix     the namespace prefix. May be the zero-length string, indicating
         *                   that there is no prefix. This indicates either the default namespace or the
         *                   null namespace, depending on the value of useDefault.
         * @param useDefault true if the default namespace is to be used when the
         *                   prefix is "". If false, the method returns "" when the prefix is "".
         * @return the uri for the namespace, or null if the prefix is not in scope.
         *         The "null namespace" is represented by the pseudo-URI "".
         */

        /*@Nullable*/
        @Override
        public String getURIForPrefix(String prefix, boolean useDefault) {
            return getNamespaceURI(prefix);
        }
    }

}

