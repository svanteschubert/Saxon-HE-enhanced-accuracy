////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.xpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.type.SchemaException;
import net.sf.saxon.type.Type;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.xpath.*;

/**
 * <p>XPathEvaluator implements the JAXP API for standalone XPath processing (that is,
 * executing XPath expressions in the absence of an XSLT stylesheet). It is an implementation
 * of the JAXP 1.3 XPath interface, with additional methods provided (a) for backwards
 * compatibility (b) to give extra control over the XPath evaluation, and (c) to support
 * later XPath versions (2.0, 3.0, 3.1).</p>
 * <p>The JAXP API is designed at one level to be object-model independent, but in other
 * respects (especially in Java SE 9) it is designed specifically with DOM in mind. The Saxon
 * implementation makes its own decisions about how to handle non-DOM nodes. Specifically,
 * when an expression with return type {@link XPathConstants#NODE} is evaluated, Saxon
 * returns the underlying node from the native object model; when the return type is given
 * as {@link XPathConstants#NODESET}, the nodes are delivered as a DOM {@link org.w3c.dom.NodeList}
 * if they are DOM nodes, or as a {@link java.util.List} otherwise.</p>
 * <p>For an alternative XPath API, offering more complete access to Saxon capabilities,
 * see {@link net.sf.saxon.s9api.XPathCompiler}.</p>
 * <p>Note that the <code>XPathEvaluator</code> links to a Saxon {@link Configuration}
 * object. By default a new <code>Configuration</code> is created automatically. In many
 * applications, however, it is desirable to share a configuration. The default configuration
 * is not schema aware. All source documents used by XPath expressions under this evaluator
 * must themselves be built using the <code>Configuration</code> used by this evaluator.</p>
 */

public class XPathEvaluator implements XPath {

    private Configuration config;
    private JAXPXPathStaticContext staticContext;

    /**
     * Default constructor. Creates an XPathEvaluator with Configuration appropriate
     * to the version of the Saxon software being run.
     */

    public XPathEvaluator() {
        this(Configuration.newConfiguration());
    }

    /**
     * Construct an XPathEvaluator with a specified configuration.
     *
     * @param config the configuration to be used. If schema-aware XPath expressions are to be used,
     *               this must be an EnterpriseConfiguration.
     */
    public XPathEvaluator(/*@NotNull*/ Configuration config) {
        this.config = config;
        staticContext = new JAXPXPathStaticContext(config);
    }

    /**
     * Get the Configuration used by this XPathEvaluator
     *
     * @return the Configuration used by this XPathEvaluator
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the current static context. The caller can set properties of the returned
     * static context to influence the way in which XPath expressions are compiled and
     * evaluated; for example it is possible to set whether XPath 1.0 backwards compatibility
     * is enabled or not.
     *
     * @return the static context
     */

    public JAXPXPathStaticContext getStaticContext() {
        return staticContext;
    }


    @Override
    public void reset() {
        staticContext = new JAXPXPathStaticContext(config);
    }

    /**
     * Set the resolver for XPath variables
     *
     * @param xPathVariableResolver a resolver for variables
     */

    @Override
    public void setXPathVariableResolver(XPathVariableResolver xPathVariableResolver) {
        staticContext.setXPathVariableResolver(xPathVariableResolver);
    }

    /**
     * Get the resolver for XPath variables
     *
     * @return the resolver, if one has been set
     */
    @Override
    public XPathVariableResolver getXPathVariableResolver() {
        return staticContext.getXPathVariableResolver();
    }

    /**
     * Set the resolver for XPath functions
     *
     * @param xPathFunctionResolver a resolver for XPath function calls
     */

    @Override
    public void setXPathFunctionResolver(XPathFunctionResolver xPathFunctionResolver) {
        staticContext.setXPathFunctionResolver(xPathFunctionResolver);
    }

    /**
     * Get the resolver for XPath functions
     *
     * @return the resolver, if one has been set
     */

    /*@Nullable*/
    @Override
    public XPathFunctionResolver getXPathFunctionResolver() {
        return staticContext.getXPathFunctionResolver();
    }

    /**
     * Set the namespace context to be used.
     *
     * @param namespaceContext The namespace context
     */

    @Override
    public void setNamespaceContext(NamespaceContext namespaceContext) {
        staticContext.setNamespaceContext(namespaceContext);
    }

    /**
     * Get the namespace context, if one has been set using {@link #setNamespaceContext}
     *
     * @return the namespace context if set, or null otherwise
     */

    @Override
    public NamespaceContext getNamespaceContext() {
        return staticContext.getNamespaceContext();
    }

    /**
     * Import a schema. This is possible only if Saxon-EE is being used,
     * and if the Configuration is an EnterpriseConfiguration. Having imported a schema, the types
     * defined in that schema become part of the static context.
     *
     * @param source A Source object identifying the schema document to be loaded
     * @throws net.sf.saxon.type.SchemaException
     *                                       if the schema contained in this document is invalid
     * @throws UnsupportedOperationException if the configuration is not schema-aware
     */

    public void importSchema(Source source) throws SchemaException {
        staticContext.importSchema(source);
        staticContext.setSchemaAware(true);
    }

    /**
     * Compile an XPath 3.1 expression
     *
     * @param expr the XPath 3.1 expression to be compiled, as a string
     * @return the compiled form of the expression
     * @throws XPathExpressionException if there are any static errors in the expression.
     *                                  Note that references to undeclared variables are not treated as static errors, because
     *                                  variables are not pre-declared using this API.
     */
    /*@NotNull*/
    @Override
    public XPathExpression compile(String expr) throws XPathExpressionException {
        if (expr == null) {
            throw new NullPointerException("expr");
        }
        try {
            Executable exec = new Executable(getConfiguration());
            exec.setSchemaAware(staticContext.getPackageData().isSchemaAware());
            Expression exp = ExpressionTool.make(expr, staticContext, 0, -1, null);
            ExpressionVisitor visitor = ExpressionVisitor.make(staticContext);
            final ContextItemStaticInfo contextItemType = getConfiguration().makeContextItemStaticInfo(Type.ITEM_TYPE, true);
            exp = exp.typeCheck(visitor, contextItemType).optimize(visitor, contextItemType);
            SlotManager map = staticContext.getConfiguration().makeSlotManager();
            ExpressionTool.allocateSlots(exp, 0, map);
            XPathExpressionImpl xpe = new XPathExpressionImpl(exp, exec);
            xpe.setStackFrameMap(map);
            return xpe;
        } catch (net.sf.saxon.trans.XPathException e) {
            throw new XPathExpressionException(e);
        }
    }

    /**
     * Single-shot method to compile and execute an XPath 3.1 expression.
     *
     * @param expr  The XPath 3.1 expression to be compiled and executed
     * @param node  The context node for evaluation of the expression.
     *              <p>This may be a NodeInfo object, representing a node in Saxon's native
     *              implementation of the data model, or it may be a node in any supported
     *              external object model: DOM, JDOM, DOM4J, or XOM, or any other model for
     *              which support has been configured in the Configuration. Note that the
     *              supporting libraries for the chosen model must be on the class path.</p>
     *              <p><b>Contrary to the interface specification, Saxon does not supply an empty
     *              document when the value is null. This is because XPath 2.0 allows the context
     *              item to be "absent" (null). So Saxon executes the XPath expression with the
     *              context item undefined.</b></p>
     * @param qName The type of result required. For details, see
     *              {@link XPathExpressionImpl#evaluate(Object, javax.xml.namespace.QName)}
     * @return the result of evaluating the expression, returned as described in
     *         {@link XPathExpressionImpl#evaluate(Object, javax.xml.namespace.QName)}
     * @throws XPathExpressionException if any static or dynamic error occurs
     *                                  in evaluating the expression.
     */

    @Override
    public Object evaluate(String expr, Object node, QName qName) throws XPathExpressionException {
        XPathExpression exp = compile(expr);
        return exp.evaluate(node, qName);
    }

    /**
     * Single-shot method to compile an execute an XPath 2.0 expression, returning
     * the result as a string.
     *
     * @param expr The XPath 2.0 expression to be compiled and executed
     * @param node The context node for evaluation of the expression
     *             <p>This may be a NodeInfo object, representing a node in Saxon's native
     *             implementation of the data model, or it may be a node in any supported
     *             external object model: DOM, JDOM, DOM4J, or XOM, or any other model for
     *             which support has been configured in the Configuration. Note that the
     *             supporting libraries for the chosen model must be on the class path.</p>
     *             <p><b>Contrary to the interface specification, Saxon does not supply an empty
     *             document when the value is null. This is because XPath 2.0 allows the context
     *             item to be "absent" (null). So Saxon executes the XPath expression with the
     *             context item undefined.</b></p>
     * @return the result of evaluating the expression, converted to a string as if
     *         by calling the XPath string() function
     * @throws XPathExpressionException if any static or dynamic error occurs
     *                                  in evaluating the expression.
     */

    @Override
    public String evaluate(String expr, Object node) throws XPathExpressionException {
        XPathExpression exp = compile(expr);
        return exp.evaluate(node);
    }

    /**
     * Single-shot method to parse and build a source document, and
     * compile an execute an XPath 2.0 expression, against that document
     *
     * @param expr        The XPath 2.0 expression to be compiled and executed
     * @param inputSource The source document: this will be parsed and built into a tree,
     *                    and the XPath expression will be executed with the root node of the tree as the
     *                    context node.
     * @param qName       The type of result required. For details, see
     *                    {@link XPathExpressionImpl#evaluate(Object, javax.xml.namespace.QName)}
     * @return the result of evaluating the expression, returned as described in
     *         {@link XPathExpressionImpl#evaluate(Object, javax.xml.namespace.QName)}
     * @throws XPathExpressionException if any static or dynamic error occurs
     *                                  in evaluating the expression.
     * @throws NullPointerException     if any of the three arguments is null
     */

    @Override
    public Object evaluate(/*@Nullable*/ String expr, /*@Nullable*/ InputSource inputSource, /*@Nullable*/ QName qName) throws XPathExpressionException {
        if (expr == null) {
            throw new NullPointerException("expr");
        }
        if (inputSource == null) {
            throw new NullPointerException("inputSource");
        }
        if (qName == null) {
            throw new NullPointerException("qName");
        }
        XPathExpression exp = compile(expr);
        return exp.evaluate(inputSource, qName);
    }

    /**
     * Single-shot method to parse and build a source document, and
     * compile an execute an XPath 2.0 expression, against that document,
     * returning the result as a string
     *
     * @param expr        The XPath 2.0 expression to be compiled and executed
     * @param inputSource The source document: this will be parsed and built into a tree,
     *                    and the XPath expression will be executed with the root node of the tree as the
     *                    context node
     * @return the result of evaluating the expression, converted to a string as
     *         if by calling the XPath string() function
     * @throws XPathExpressionException if any static or dynamic error occurs
     *                                  in evaluating the expression.
     * @throws NullPointerException     if either of the two arguments is null
     */

    @Override
    public String evaluate(/*@Nullable*/ String expr, /*@Nullable*/ InputSource inputSource) throws XPathExpressionException {
        if (expr == null) {
            throw new NullPointerException("expr");
        }
        if (inputSource == null) {
            throw new NullPointerException("inputSource");
        }
        XPathExpression exp = compile(expr);
        return exp.evaluate(inputSource);
    }

}

