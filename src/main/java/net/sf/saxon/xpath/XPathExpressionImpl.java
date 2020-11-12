////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.xpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.functions.Number_1;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.wrapper.VirtualNode;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.NumericValue;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.sax.SAXSource;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

/**
 * <p>The JAXP XPathExpression interface represents a compiled XPath expression that can be repeatedly
 * evaluated. This class is Saxon's implementation of that interface.</p>
 * <p>The class also includes some methods retained from Saxon's original XPath API. When these methods
 * are used, the object contains the context node and other state, so it is not thread-safe.</p>
 *
 * @author Michael H. Kay
 */


public class XPathExpressionImpl implements XPathExpression {

    private Configuration config;
    private Executable executable;
    private Expression expression;
    private Expression atomizer;
    private SlotManager stackFrameMap;

    /**
     * The constructor is protected, to ensure that instances can only be
     * created using the compile() method of XPathEvaluator
     *
     * @param exp  the compiled expression
     * @param exec the executable
     */

    protected XPathExpressionImpl(Expression exp, /*@NotNull*/ Executable exec) {
        expression = exp;
        executable = exec;
        config = exec.getConfiguration();
    }

    /**
     * Define the number of slots needed for local variables within the expression.
     * This method is for internal use only.
     *
     * @param map description of the stack frame
     */

    protected void setStackFrameMap(SlotManager map) {
        stackFrameMap = map;
    }

    /**
     * Get the stack frame map. This holds information about the allocation of slots to variables.
     * This is needed by applications using low-level interfaces for evaluating the expression
     *
     * @return a description of the stack frame
     */

    public SlotManager getStackFrameMap() {
        return stackFrameMap;
    }

    /**
     * Get the Configuration under which this XPath expression was compiled
     *
     * @return the Saxon configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * JAXP 1.3 evaluate() method
     *
     * @param node  The context node. This must use a representation of nodes that this implementation understands.
     *              This may be a Saxon NodeInfo, or a node in one of the external object models supported, for example
     *              DOM, DOM4J, JDOM, or XOM, provided the support module for that object model is loaded.
     *
     *              <p>An instance of {@link TreeInfo} is also accepted.</p>
     *
     *              <p><b>Contrary to the interface specification, Saxon does not supply an empty
     *              document when the value is null. This is because XPath 2.0 allows the context
     *              item to be "absent" (null). So Saxon executes the XPath expression with the
     *              context item undefined.</b></p>
     * @param qName Indicates the type of result required. This must be one of the constants defined in
     *              the JAXP {@link XPathConstants} class.
     *              Saxon will attempt to convert the actual result of the expression to the required type using the
     *              XPath 1.0 conversion rules.
     * @return the result of the evaluation, as a Java object of the appropriate type. Saxon interprets the
     *         rules as follows:
     *         <table>
     *         <thead><tr><td>QName</td><td>Return Value</td></thead>
     *         <tbody>
     *         <tr><td valign="top">BOOLEAN</td>
     *         <td>The effective boolean value of the actual result,
     *         as a Java Boolean object</td></tr>
     *         <tr><td valign="top">STRING</td>
     *         <td>The result of applying the string() function to the actual result,
     *         as a Java String object</td></tr>
     *         <tr><td valign="top">NUMBER</td>
     *         <td>The result of applying the number() function to the actual result,
     *         as a Java Double object</td></tr>
     *         <tr><td valign="top">NODE</td>
     *         <td>A single node, in the native data model supplied as input. If the
     *         expression returns more than one node, the first is returned. If
     *         the expression returns an empty sequence, null is returned. If the
     *         expression returns an atomic value, or if the first item in the
     *         result sequence is an atomic value, an exception is thrown.</td></tr>
     *         <tr><td valign="top">NODESET</td>
     *         <td>This is interpreted as allowing any sequence, of nodes or atomic values.
     *         If the first argument is a wrapper around a DOM Node, then the result is
     *         returned as a DOM NodeList, and an exception is then thrown if the result sequence
     *         contains a value that is not a DOM Node. In all other cases
     *         the result is returned as a Java List object, unless it is empty, in which
     *         case null is returned. The contents of the list may be node objects (in the
     *         native data model supplied as input), or Java objects representing the XPath
     *         atomic values in the actual result: String for an xs:string, Double for a xs:double,
     *         Long for an xs:integer, and so on. (For safety, cast the values to a type
     *         such as xs:string within the XPath expression). </td></tr></tbody></table>
     * @throws XPathExpressionException if evaluation of the expression fails or if the
     *                                  result cannot be converted to the requested type.
     */
    /*@Nullable*/
    @Override
    public Object evaluate(/*@Nullable*/ Object node, /*@NotNull*/ QName qName) throws XPathExpressionException {
        Item contextItem;
        if (node instanceof ZeroOrOne) {
            node = ((ZeroOrOne) node).head();
        }
        if (node instanceof TreeInfo) {
            node = ((TreeInfo)node).getRootNode();
        }
        if (node instanceof NodeInfo) {
            if (!((NodeInfo) node).getConfiguration().isCompatible(config)) {
                throw new XPathExpressionException(
                        "Supplied node must be built using the same or a compatible Configuration");
            }
            if (node instanceof TreeInfo && ((TreeInfo) node).isTyped() && !executable.isSchemaAware()) {
                throw new XPathExpressionException(
                        "The expression was compiled to handled untyped data, but the input is typed");
            }
            contextItem = (NodeInfo) node;
        } else if (node instanceof Item) {
            contextItem = (Item) node;
        } else {
            JPConverter converter = JPConverter.allocate(node.getClass(), null, config);
            Sequence val;
            try {
                val = converter.convert(node, new EarlyEvaluationContext(config));
            } catch (XPathException e) {
                throw new XPathExpressionException(
                        "Failure converting a node of class " + node.getClass().getName() +
                                ": " + e.getMessage());
            }
            if (val instanceof NodeInfo) {
                if (!((NodeInfo) val).getConfiguration().isCompatible(config)) {
                    throw new XPathExpressionException(
                            "Supplied node must be built using the same or a compatible Configuration");
                }
                if (((NodeInfo) val).getTreeInfo().isTyped() && !executable.isSchemaAware()) {
                    throw new XPathExpressionException(
                            "The expression was compiled to handled untyped data, but the input is typed");
                }
                contextItem = (NodeInfo) val;
            } else {
                throw new XPathExpressionException(
                        "Cannot locate an object model implementation for nodes of class "
                                + node.getClass().getName());
            }
        }

        XPathContextMajor context = new XPathContextMajor(contextItem, executable);
        context.openStackFrame(stackFrameMap);
        try {
            if (qName.equals(XPathConstants.BOOLEAN)) {
                return expression.effectiveBooleanValue(context);
            } else if (qName.equals(XPathConstants.STRING)) {
                SequenceIterator iter = expression.iterate(context);

                Item first = iter.next();
                if (first == null) {
                    return "";
                }
                return first.getStringValue();

            } else if (qName.equals(XPathConstants.NUMBER)) {
                if (atomizer == null) {
                    atomizer = Atomizer.makeAtomizer(expression, null);
                }
                SequenceIterator iter = atomizer.iterate(context);

                Item first = iter.next();
                if (first == null) {
                    return Double.NaN;
                }
                if (first instanceof NumericValue) {
                    return ((NumericValue) first).getDoubleValue();
                } else {
                    DoubleValue v = Number_1.convert((AtomicValue) first, getConfiguration());
                    return v.getDoubleValue();
                }

            } else if (qName.equals(XPathConstants.NODE)) {
                SequenceIterator iter = expression.iterate(context);
                Item first = iter.next();
                if (first instanceof VirtualNode) {
                    return ((VirtualNode) first).getRealNode();
                }
                if (first == null || first instanceof NodeInfo) {
                    return first;
                }
                throw new XPathExpressionException("Expression result is not a node");
            } else if (qName.equals(XPathConstants.NODESET)) {
                context.openStackFrame(stackFrameMap);
                SequenceIterator iter = expression.iterate(context);
                GroundedValue extent = iter.materialize();
                PJConverter converter = PJConverter.allocateNodeListCreator(config, node);
                return converter.convert(extent, Object.class, context);
            } else {
                throw new IllegalArgumentException("qName: Unknown type for expected result");
            }
        } catch (XPathException e) {
            throw new XPathExpressionException(e);
        }
    }

    /**
     * Evaluate the expression to return a string value
     *
     * @param node the initial context node. This must be either an instance of NodeInfo or a node
     *             recognized by a known external object model.
     *             <p><b>Contrary to the interface specification, Saxon does not supply an empty
     *             document when the value is null. This is because XPath 2.0 allows the context
     *             item to be "absent" (null). So Saxon executes the XPath expression with the
     *             context item undefined.</b></p>
     * @return the results of the expression, converted to a String
     * @throws XPathExpressionException if evaluation fails
     */

    /*@NotNull*/
    @Override
    public String evaluate(Object node) throws XPathExpressionException {
        return (String) evaluate(node, XPathConstants.STRING);
    }

    /**
     * Evaluate the XPath expression against an input source to obtain a result of a specified type
     *
     * @param inputSource The input source document against which the expression is evaluated.
     *                    <p>If any error occurs reading or parsing this document, the error is notified
     *                    to the {@link ErrorListener} registered with the {@link Configuration},
     *                    and also causes an exception to be thrown. </p>
     *                    <p>(Note that there is no caching. This will be parsed, and
     *                    the parsed result will be discarded.) </p>
     *                    <p>If the supplied value is null then (contrary to the JAXP specifications), the XPath expression
     *                    is evaluated with the context item undefined.</p>
     * @param qName       The type required, identified by a constant in {@link XPathConstants}
     * @return the result of the evaluation, as a Java object of the appropriate type:
     *         see {@link #evaluate(Object, javax.xml.namespace.QName)}
     * @throws XPathExpressionException
     */
    /*@Nullable*/
    @Override
    public Object evaluate(/*@Nullable*/ InputSource inputSource, /*@Nullable*/ QName qName) throws XPathExpressionException {
        if (qName == null) {
            throw new NullPointerException("qName");
        }
        try {
            NodeInfo doc = null;
            if (inputSource != null) {
                doc = config.buildDocumentTree(new SAXSource(inputSource)).getRootNode();
            }
            return evaluate(doc, qName);
        } catch (XPathException e) {
            throw new XPathExpressionException(e);
        }
    }

    /**
     * Evaluate the XPath expression against an input source to obtain a string result.
     *
     * @param inputSource The input source document against which the expression is evaluated.
     *                    <p>If any error occurs reading or parsing this document, the error is notified
     *                    to the {@link ErrorListener} registered with the {@link Configuration},
     *                    and also causes an exception to be thrown. </p>
     *                    <p>(Note that there is no caching. This will be parsed, and
     *                    the parsed result will be discarded.) </p>
     *                    <p>If the supplied value is null then (contrary to the JAXP specifications), the XPath expression
     *                    is evaluated with the context item undefined.</p>
     * @return the result of the evaluation, converted to a String
     * @throws XPathExpressionException in the event of an XPath dynamic error
     * @throws NullPointerException     If  <code>inputSource</code> is <code>null</code>.
     */

    /*@NotNull*/
    @Override
    public String evaluate(/*@Nullable*/ InputSource inputSource) throws XPathExpressionException {
        if (inputSource == null) {
            throw new NullPointerException("inputSource");
        }
        try {
            NodeInfo doc = config.buildDocumentTree(new SAXSource(inputSource)).getRootNode();
            return (String) evaluate(doc, XPathConstants.STRING);
        } catch (XPathException e) {
            throw new XPathExpressionException(e);
        }
    }


    /**
     * Low-level method to get the internal Saxon expression object. This exposes a wide range of
     * internal methods that may be needed by specialized applications, and allows greater control
     * over the dynamic context for evaluating the expression.
     *
     * @return the underlying Saxon expression object.
     */

    public Expression getInternalExpression() {
        return expression;
    }

}

