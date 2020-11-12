////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.SequenceNormalizerWithSpaceSeparator;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.GlobalParameterSet;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.instruct.UserFunctionParameter;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.*;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An <code>Xslt30Transformer</code> represents a compiled and loaded stylesheet ready for execution.
 * The <code>Xslt30Transformer</code> holds details of the dynamic evaluation context for the stylesheet.
 * <p>The <code>Xslt30Transformer</code> differs from {@link XsltTransformer} is supporting new options
 * for invoking a stylesheet, corresponding to facilities defined in the XSLT 3.0 specification. However,
 * it is not confined to use with XSLT 3.0, and most of the new invocation facilities (for example,
 * calling a stylesheet-defined function directly) work equally well with XSLT 2.0 and in some cases
 * XSLT 1.0 stylesheets.</p>
 * <p>An <code>Xslt30Transformer</code> must not be used concurrently in multiple threads.
 * It is safe to reuse the object within a single thread to run several transformations using
 * the same stylesheet, but the values of the global context item and of stylesheet parameters
 * must be initialized before any transformations are run, and must remain unchanged thereafter.</p>
 * <p>Some of the entry point methods are synchronized. This is not
 * because multi-threaded execution is permitted; rather it is to reduce the damage if it is attempted.</p>
 * <p>An <code>Xslt30Transformer</code> is always constructed by running the
 * method {@link XsltExecutable#load30()}.</p>
 * <p>Unlike <code>XsltTransformer</code>, an <code>Xslt30Transformer</code> is not a <code>Destination</code>.
 * To pipe the results of one transformation into another, the target should be an <code>XsltTransfomer</code>
 * rather than an <code>Xslt30Transformer</code>.</p>
 * <p>Evaluation of an Xslt30Transformer proceeds in a number of phases:</p>
 * <ol>
 * <li>First, values may be supplied for stylesheet parameters and for the global context item. The
 * global context item is used when initializing global variables. Unlike earlier transformation APIs,
 * the global context item is quite independent of the "principal Source document".</li>
 * <li>The stylesheet may now be repeatedly invoked. Each invocation takes one of three forms:
 * <ol>
 * <li>Invocation by applying templates. In this case, the information required is (i) an initial
 * mode (which defaults to the unnamed mode), (ii) an initial match sequence, which is any
 * XDM value, which is used as the effective "select" expression of the implicit apply-templates
 * call, and (iii) optionally, values for the tunnel and non-tunnel parameters defined on the
 * templates that get invoked (equivalent to using <code>xsl:with-param</code> on the implicit
 * <code>apply-templates</code> call).</li>
 * <li>Invocation by calling a named template. In this case, the information required is
 * (i) the name of the initial template (which defaults to "xsl:initial-template"), and
 * (ii) optionally, values for the tunnel and non-tunnel parameters defined on the
 * templates that get invoked (equivalent to using <code>xsl:with-param</code> on the implicit
 * <code>call-template</code> instruction).</li>
 * <li>Invocation by calling a named function. In this case, the information required is
 * the sequence of arguments to the function call.</li>
 * </ol>
 * </li>
 * <li>Whichever invocation method is chosen, the result may either be returned directly, as an arbitrary
 * XDM value, or it may effectively be wrapped in an XML document. If it is wrapped in an XML document,
 * that document can be processed in a number of ways, for example it can be materialized as a tree in
 * memory, it can be serialized as XML or HTML, or it can be subjected to further transformation.</li>
 * </ol>
 * <p>Once the stylesheet has been invoked (using any of these methods), the values of the global context
 * item and stylesheet parameters cannot be changed. If it is necessary to run another transformation with
 * a different context item or different stylesheet parameters, a new <code>Xslt30Transformer</code>
 * should be created from the original <code>XsltExecutable</code>.</p>
 *
 * @since 9.6
 */
public class Xslt30Transformer extends AbstractXsltTransformer {

    private GlobalParameterSet globalParameterSet;
    private boolean primed = false;
    private Item globalContextItem = null;
    private boolean alreadyStripped;

    /**
     * Protected constructor
     *
     * @param processor        the S9API processor
     * @param controller       the Saxon controller object
     * @param staticParameters the static parameters supplied at stylesheet compile time
     */

    protected Xslt30Transformer(Processor processor, XsltController controller, GlobalParameterSet staticParameters) {
        super(processor, controller);
        globalParameterSet = new GlobalParameterSet(/*staticParameters*/);
    }

    /**
     * Supply the context item to be used when evaluating global variables and parameters. A call on
     * <code>setGlobalContextItem(node)</code> is equivalent to a call on <code>setGlobalContextItem(node, false)</code>:
     * that is, it is assumed that stripping of type annotations and whitespace text nodes has not yet been
     * performed.
     *
     * <p>Note: It is more efficient to do whitespace stripping while constructing the source tree. This can be
     * achieved by building the source tree using a {@link DocumentBuilder} initialized using
     * {@link DocumentBuilder#setWhitespaceStrippingPolicy(WhitespaceStrippingPolicy)}, supplying a
     * {@link WhitespaceStrippingPolicy} obtained by calling {@link XsltExecutable#getWhitespaceStrippingPolicy()}.</p>
     *
     * @param globalContextItem the item to be used as the context item within the initializers
     *                          of global variables and parameters. This argument can be null if no context item is to be
     *                          supplied.
     * @throws IllegalStateException if the transformation has already been evaluated by calling one of the methods
     *                               <code>applyTemplates</code>, <code>callTemplate</code>, or <code>callFunction</code>
     * @throws SaxonApiException Not thrown, but retained in the method signature for compatibility reasons. Note that
     * no error is thrown at this stage if the context item is of the wrong type.
     */

    public void setGlobalContextItem(XdmItem globalContextItem) throws SaxonApiException {
        setGlobalContextItem(globalContextItem, false);
    }

    /**
     * Supply the context item to be used when evaluating global variables and parameters.
     *
     * @param globalContextItem the item to be used as the context item within the initializers
     *                          of global variables and parameters. This argument can be null if no context item is to be
     *                          supplied.
     * @param alreadyStripped   true if any stripping of type annotations or whitespace text nodes specified
     *                          by the stylesheet has already taken place
     * @throws IllegalStateException if the transformation has already been evaluated by calling one of the methods
     *                               <code>applyTemplates</code>, <code>callTemplate</code>, or <code>callFunction</code>
     * @throws SaxonApiException Not thrown, but retained in the method signature for compatibility reasons. Note that
     * no error is thrown at this stage if the context item is of the wrong type.
     */

    public synchronized void setGlobalContextItem(XdmItem globalContextItem, boolean alreadyStripped) throws SaxonApiException {
        if (primed) {
            throw new IllegalStateException("Stylesheet has already been evaluated");
        }
        this.globalContextItem = globalContextItem == null ? null : globalContextItem.getUnderlyingValue();
        this.alreadyStripped = alreadyStripped;
    }

    /**
     * Supply the values of global stylesheet parameters.
     *
     * <p>If a value is supplied for a parameter with a particular name, and the stylesheet does not declare
     * a parameter with that name, then the value is simply ignored.</p>
     *
     * <p>If a value is supplied for a parameter with a particular name, and the stylesheet declaration of
     * that parameter declares a required type, then the supplied value will be checked against that type
     * when the value of the parameter is first used (and any error will be reported only then). Parameter
     * values are converted to the required type using the function conversion rules.</p>
     *
     * <p>If a value is supplied for a parameter with a particular name, and the stylesheet declaration of
     * that parameter declares it with the attribute <code>static="yes"</code>, then an error will be reported
     * when the transformation is initiated.</p>
     *
     * <p>If a value is supplied for a parameter with a particular name, and a parameter with the same
     * name was supplied at compile time using {@link XsltCompiler#setParameter(QName, XdmValue)},
     * then an error will be reported with the transformation is initiated.</p>
     *
     * @param parameters a map whose keys are QNames identifying global stylesheet parameters,
     *                   and whose corresponding values are the values to be assigned to those parameters.
     *                   The contents of the supplied map are copied by this method,
     *                   so subsequent changes to the map have no effect.
     * @throws IllegalStateException if the transformation has already been evaluated by calling one of the methods
     *                               <code>applyTemplates</code>, <code>callTemplate</code>, or <code>callFunction</code>
     * @throws SaxonApiException     currently occurs only if the supplied value of a parameter cannot be evaluated
     */

    public synchronized <T extends XdmValue> void setStylesheetParameters(Map<QName, T> parameters) throws SaxonApiException {
        if (primed) {
            throw new IllegalStateException("Stylesheet has already been evaluated");
        }
        if (globalParameterSet == null) {
            globalParameterSet = new GlobalParameterSet();
        }
        for (Map.Entry<QName, T> param : parameters.entrySet()) {
            StructuredQName name = param.getKey().getStructuredQName();
            try {
                globalParameterSet.put(name,
                                       ((Sequence) param.getValue().getUnderlyingValue()).materialize());
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }
    }


    private void prime() throws SaxonApiException {
        if (!primed) {
            if (globalParameterSet == null) {
                globalParameterSet = new GlobalParameterSet();
            }
            try {
                controller.setGlobalContextItem(globalContextItem, alreadyStripped);
                controller.initializeController(globalParameterSet);
            } catch (XPathException e) {
                throw new SaxonApiException(e);
            }
        }
        primed = true;
    }


    /**
     * Set parameters to be passed to the initial template. These are used
     * whether the transformation is invoked by applying templates to an initial source item,
     * or by invoking a named template. The parameters in question are the xsl:param elements
     * appearing as children of the xsl:template element.
     * <p>The parameters are supplied in the form of a map; the key is a QName which must
     * match the name of the parameter; the associated value is an XdmValue containing the
     * value to be used for the parameter. If the initial template defines any required
     * parameters, the map must include a corresponding value. If the initial template defines
     * any parameters that are not present in the map, the default value is used. If the map
     * contains any parameters that are not defined in the initial template, these values
     * are silently ignored.</p>
     * <p>The supplied values are converted to the required type using the function conversion
     * rules. If conversion is not possible, a run-time error occurs (not now, but later, when
     * the transformation is actually run).</p>
     * <p>The <code>XsltTransformer</code> retains a reference to the supplied map, so parameters can be added or
     * changed until the point where the transformation is run.</p>
     * <p>The XSLT 3.0 specification makes provision for supplying parameters to the initial
     * template, as well as global stylesheet parameters. Although there is no similar provision
     * in the XSLT 1.0 or 2.0 specifications, this method works for all stylesheets, regardless whether
     * XSLT 3.0 is enabled or not.</p>
     *
     * @param parameters the parameters to be used for the initial template
     * @param tunnel     true if these values are to be used for setting tunnel parameters;
     *                   false if they are to be used for non-tunnel parameters
     * @throws SaxonApiException not currently used, but retained in the method signature for compatibility reasons
     */

    public synchronized <T extends XdmValue> void setInitialTemplateParameters(Map<QName, T> parameters, boolean tunnel) throws SaxonApiException {
        Map<StructuredQName, Sequence> templateParams = new HashMap<>();
        for (Map.Entry<QName, T> entry : parameters.entrySet()) {
            templateParams.put(entry.getKey().getStructuredQName(), entry.getValue().getUnderlyingValue());
        }
        controller.setInitialTemplateParameters(templateParams, tunnel);
    }

    /**
     * Invoke the stylesheet by applying templates to a supplied Source document, sending the results (wrapped
     * in a document node) to a given Destination. The invocation uses any initial mode set using {@link #setInitialMode(QName)},
     * and any template parameters set using {@link #setInitialTemplateParameters(java.util.Map, boolean)}.
     *
     * <p>This method does not set the global context item for the transformation. If that is required, it
     * can be done separately using the method {@link #setGlobalContextItem(XdmItem)}.</p>
     *
     * @param source      the source document. For streamed processing, this must be a SAXSource or StreamSource.
     *                    <p>Note: supplying a <code>DOMSource</code> is allowed, but is much less efficient than using a
     *                    <code>StreamSource</code> or <code>SAXSource</code> and leaving Saxon to build the tree in its own
     *                    internal format. To apply more than one transformation to the same source document, the source document
     *                    tree can be pre-built using a {@link DocumentBuilder}.</p>
     * @param destination the destination of the principal result of the transformation.
     *                    If the destination is a {@link Serializer}, then the serialization
     *                    parameters set in the serializer are combined with those defined in the stylesheet
     *                    (the parameters set in the serializer take precedence).
     * @throws SaxonApiException if the transformation fails
     */

    public synchronized void applyTemplates(Source source, Destination destination) throws SaxonApiException {

        Objects.requireNonNull(destination);
        if (source == null) {
            XPathException err = new XPathException("No initial match selection supplied", "XTDE0044");
            throw new SaxonApiException(err);
        }
        prime();
        try {
            Receiver sOut = getDestinationReceiver(controller, destination);
            applyTemplatesToSource(source, sOut);
            destination.closeAndNotify();
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                try {
                    getErrorListener().fatalError(e);
                } catch (TransformerException e1) {
                    // ignore secondary error
                }
            }
            throw new SaxonApiException(e);
        }
    }

    /**
     * Invoke the stylesheet by applying templates to a supplied Source document, returning the raw results
     * as an {@link XdmValue}. The invocation uses any initial mode set using {@link #setInitialMode(QName)},
     * and any template parameters set using {@link #setInitialTemplateParameters(java.util.Map, boolean)}.
     *
     * @param source the source document. For streamed processing, this must be a SAXSource or StreamSource.
     *               <p>Note: supplying a <code>DOMSource</code> is allowed, but is much less efficient than using a
     *               <code>StreamSource</code> or <code>SAXSource</code> and leaving Saxon to build the tree in its own
     *               internal format. To apply more than one transformation to the same source document, the source document
     *               tree can be pre-built using a {@link DocumentBuilder}.</p>
     * @return the raw result of processing the supplied Source using the selected template rule, without
     * wrapping the returned sequence in a document node
     * @throws SaxonApiException if the transformation fails
     */

    public synchronized XdmValue applyTemplates(Source source) throws SaxonApiException {
        Objects.requireNonNull(source);
        RawDestination raw = new RawDestination();
        applyTemplates(source, raw);
        return raw.getXdmValue();
    }

    /**
     * Invoke the stylesheet by applying templates to a supplied Source document, sending the results
     * to a given Destination. The invocation uses the initial mode set using {@link #setInitialMode(QName)}
     * (defaulting to the default mode defined in the stylesheet itself, which by default is the unnamed mode).
     * It also uses any template parameters set using {@link #setInitialTemplateParameters(java.util.Map, boolean)}.
     *
     * <p>The document supplied in the <code>source</code> argument also acts as the global
     * context item for the transformation. Any item previously supplied using {@link #setGlobalContextItem(XdmItem)}
     * is ignored.</p>
     *
     * <p>Because this method sets the global context item to the root node of the supplied <code>source</code>,
     * it cannot be used when the stylesheet is designed to process streamed input. For streamed processing,
     * use {@link #applyTemplates(Source, Destination)} instead.</p>
     *
     * @param source      the source document.
     *                    <p>Note: supplying a <code>DOMSource</code> is allowed, but is much less efficient than using a
     *                    <code>StreamSource</code> or <code>SAXSource</code> and leaving Saxon to build the tree in its own
     *                    internal format. To apply more than one transformation to the same source document, the source document
     *                    tree can be pre-built using a {@link DocumentBuilder}.</p>
     * @param destination the destination of the principal result of the transformation.
     *                    If the destination is a {@link Serializer}, then the serialization
     *                    parameters set in the serializer are combined with those defined in the stylesheet
     *                    (the parameters set in the serializer take precedence).
     * @throws SaxonApiException if the transformation fails, or if the initial mode in the stylesheet is
     * declared to be streamable.
     * @since 9.9.1.1
     */

    public synchronized void transform(Source source, Destination destination) throws SaxonApiException {

        Objects.requireNonNull(destination);
        if (source == null) {
            XPathException err = new XPathException("No initial match selection supplied", "XTDE0044");
            throw new SaxonApiException(err);
        }
        if (controller.getInitialMode().isDeclaredStreamable()) {
            throw new SaxonApiException("Cannot use the transform() method when the initial mode is streamable");
        }
        prime();
        try {
            NodeInfo sourceNode;
            if (source instanceof NodeInfo) {
                controller.setGlobalContextItem((NodeInfo)source);
                sourceNode = (NodeInfo) source;
            } else if (source instanceof DOMSource) {
                sourceNode = controller.prepareInputTree(source);
                controller.setGlobalContextItem(sourceNode);
            } else {
                sourceNode = controller.makeSourceTree(source, getSchemaValidationMode().getNumber());
                controller.setGlobalContextItem(sourceNode);
            }

            Receiver sOut = getDestinationReceiver(controller, destination);
            controller.applyTemplates(sourceNode, sOut);
            destination.closeAndNotify();
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                try {
                    getErrorListener().fatalError(e);
                } catch (TransformerException e1) {
                    // ignore secondary error
                }
            }
            throw new SaxonApiException(e);
        }
    }


    /**
     * Invoke the stylesheet by applying templates to a supplied input sequence, sending the results (wrapped
     * in a document node) to a given Destination. The invocation uses any initial mode set using {@link #setInitialMode(QName)},
     * and any template parameters set using {@link #setInitialTemplateParameters(java.util.Map, boolean)}.
     *
     * @param selection   the initial value to which templates are to be applied (equivalent to the <code>select</code>
     *                    attribute of <code>xsl:apply-templates</code>)
     * @param destination the destination for the result document. In most cases this causes the raw result of
     *                    the transformation to be wrapped in a document node. However, if the destination
     *                    is a Serializer and the output method is "json" or "adaptive", then
     *                    no wrapping takes place.
     * @throws SaxonApiException if the transformation fails
     * @since 9.6. Changed in 9.7.0.1 so that if a Serializer is supplied as the Destination, it will not
     * be modified by this method to set output properties from the stylesheet; instead, the Serializer
     * should be initialized by calling the <code>newSerializer</code> method on this <code>Xslt30Transformer</code>
     */

    public synchronized void applyTemplates(XdmValue selection, Destination destination) throws SaxonApiException {
        Objects.requireNonNull(selection);
        Objects.requireNonNull(destination);
        prime();
        try {
            Receiver sOut = getDestinationReceiver(controller, destination);
            if (baseOutputUriWasSet) {
                sOut.setSystemId(getBaseOutputURI());
            }
            controller.applyTemplates(selection.getUnderlyingValue(), sOut);
            destination.closeAndNotify();
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                try {
                    getErrorListener().fatalError(e);
                } catch (TransformerException e1) {
                    // ignore secondary error
                }
            }
            throw new SaxonApiException(e);
        }
    }

    /**
     * Invoke the stylesheet by applying templates to a supplied input sequence, returning the raw results.
     * as an {@link XdmValue}. The invocation uses any initial mode set using {@link #setInitialMode(QName)},
     * and any template parameters set using {@link #setInitialTemplateParameters(java.util.Map, boolean)}.
     *
     * @param selection the initial value to which templates are to be applied (equivalent to the <code>select</code>
     *                  attribute of <code>xsl:apply-templates</code>)
     * @return the raw result of applying templates to the supplied selection value, without wrapping in
     * a document node or serializing the result. If there is more that one item in the selection, the result
     * is the concatenation of the results of applying templates to each item in turn.
     * @throws SaxonApiException if the transformation fails
     */

    public synchronized XdmValue applyTemplates(XdmValue selection) throws SaxonApiException {
        Objects.requireNonNull(selection);
        RawDestination raw = new RawDestination();
        applyTemplates(selection, raw);
        return raw.getXdmValue();
    }

    /**
     * Invoke a transformation by calling a named template. The results of calling
     * the template are wrapped in a document node, which is then sent to the specified
     * destination. If {@link #setInitialTemplateParameters(java.util.Map, boolean)} has been
     * called, then the parameters supplied are made available to the called template (no error
     * occurs if parameters are supplied that are not used).
     *
     * @param templateName the name of the initial template. This must match the name of a
     *                     public named template in the stylesheet. If the value is null,
     *                     the QName <code>xsl:initial-template</code> is used.
     * @param destination  the destination for the result document.
     * @throws SaxonApiException if there is no named template with this name, or if any dynamic
     *                           error occurs during the transformation
     * @since 9.6. Changed in 9.7.0.1 so that if a Serializer is supplied as the Destination, it will not
     * be modified by this method to set output properties from the stylesheet; instead, the Serializer
     * should be initialized by calling the <code>newSerializer</code> method on this <code>Xslt30Transformer</code>
     */

    public synchronized void callTemplate(QName templateName, Destination destination) throws SaxonApiException {
        Objects.requireNonNull(destination);
        prime();
        if (templateName == null) {
            templateName = new QName("xsl", NamespaceConstant.XSLT, "initial-template");
        }
        try {
            Receiver sOut = getDestinationReceiver(controller, destination);
            if (baseOutputUriWasSet) {
                sOut.setSystemId(getBaseOutputURI());
            }
            //sOut.open();
            controller.callTemplate(templateName.getStructuredQName(), sOut);
            //sOut.close();
            destination.closeAndNotify();
        } catch (XPathException e) {
            destination.closeAndNotify();
            if (!e.hasBeenReported()) {
                getErrorReporter().report(new XmlProcessingException(e));
            }
            throw new SaxonApiException(e);
        }
    }

    /**
     * Invoke a transformation by calling a named template. The results of calling
     * the template are returned as a raw value, without wrapping in a document node
     * or serializing.
     *
     * @param templateName the name of the initial template. This must match the name of a
     *                     public named template in the stylesheet. If the value is null,
     *                     the QName <code>xsl:initial-template</code> is used.
     * @return the raw results of the called template, without wrapping in a document node
     * or serialization.
     * @throws SaxonApiException if there is no named template with this name, or if any dynamic
     *                           error occurs during the transformation
     */


    public synchronized XdmValue callTemplate(QName templateName) throws SaxonApiException {
        RawDestination dest = new RawDestination();
        callTemplate(templateName, dest);
        return dest.getXdmValue();
    }

    /**
     * Call a public user-defined function in the stylesheet.
     *
     * @param function  The name of the function to be called
     * @param arguments The values of the arguments to be supplied to the function. These
     *                  will be converted if necessary to the type as defined in the function signature, using
     *                  the function conversion rules.
     * @return the result of calling the function. This is the raw result, without wrapping in a document
     * node and without serialization.
     * @throws SaxonApiException if no function has been defined with the given name and arity;
     *                           or if any of the arguments does not match its required type according to the function
     *                           signature; or if a dynamic error occurs in evaluating the function.
     */

    public synchronized XdmValue callFunction(QName function, XdmValue[] arguments) throws SaxonApiException {
        Objects.requireNonNull(function);
        Objects.requireNonNull(arguments);
        prime();
        try {
            Component f = getFunctionComponent(function, arguments);
            UserFunction uf = (UserFunction) f.getActor();
            Sequence[] vr = typeCheckFunctionArguments(uf, arguments);

            XPathContextMajor context = controller.newXPathContext();
            context.setCurrentComponent(f);
            context.setTemporaryOutputState(StandardNames.XSL_FUNCTION);
            context.setCurrentOutputUri(null);
            Sequence result = uf.call(context, vr);
            result = result.materialize();
            return XdmValue.wrap(result);
        } catch (XPathException e) {
            if (!e.hasBeenReported()) {
                getErrorReporter().report(new XmlProcessingException(e));
            }
            throw new SaxonApiException(e);
        }
    }

    private synchronized Component getFunctionComponent(QName function, XdmValue[] arguments) throws XPathException {
        SymbolicName fName = new SymbolicName.F(function.getStructuredQName(), arguments.length);
        PreparedStylesheet pss = (PreparedStylesheet) controller.getExecutable();
        Component f = pss.getComponent(fName);
        if (f == null) {
            throw new XPathException("No public function with name " + function.getClarkName() +
                                             " and arity " + arguments.length + " has been declared in the stylesheet", "XTDE0041");
        } else if (f.getVisibility() != Visibility.FINAL && f.getVisibility() != Visibility.PUBLIC) {
            throw new XPathException("Cannot invoke " + fName + " externally, because it is not public", "XTDE0041");
        }
        return f;
    }

    private Sequence[] typeCheckFunctionArguments(UserFunction uf, XdmValue[] arguments) throws XPathException {
        Configuration config = processor.getUnderlyingConfiguration();
        UserFunctionParameter[] params = uf.getParameterDefinitions();
        GroundedValue[] vr =
                (GroundedValue[])new GroundedValue[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            net.sf.saxon.value.SequenceType type = params[i].getRequiredType();
            vr[i] = arguments[i].getUnderlyingValue();
            if (!type.matches(vr[i], config.getTypeHierarchy())) {
                RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION, uf.getFunctionName().getDisplayName(), i);
                Sequence converted = config.getTypeHierarchy().applyFunctionConversionRules(vr[i], type, role, Loc.NONE);
                vr[i] = converted.materialize();
            }
        }
        return vr;
    }

    /**
     * Call a public user-defined function in the stylesheet, wrapping the result in an XML document, and sending
     * this document to a specified destination
     *
     * @param function    The name of the function to be called
     * @param arguments   The values of the arguments to be supplied to the function. These
     *                    will be converted if necessary to the type as defined in the function signature, using
     *                    the function conversion rules.
     * @param destination the destination of the result document produced by wrapping the result of the apply-templates
     *                    call in a document node.
     * @throws SaxonApiException in the event of a dynamic error
     * @since 9.6. Changed in 9.7.0.1 so that if a Serializer is supplied as the Destination, it will not
     * be modified by this method to set output properties from the stylesheet; instead, the Serializer
     * should be initialized by calling the <code>newSerializer</code> method on this <code>Xslt30Transformer</code>
     */

    public synchronized void callFunction(QName function, XdmValue[] arguments, Destination destination) throws SaxonApiException {
        prime();
        try {
            Component f = getFunctionComponent(function, arguments);
            UserFunction uf = (UserFunction) f.getActor();
            Sequence[] vr = typeCheckFunctionArguments(uf, arguments);

            XPathContextMajor context = controller.newXPathContext();
            context.setCurrentComponent(f);
            context.setTemporaryOutputState(StandardNames.XSL_FUNCTION);
            context.setCurrentOutputUri(null);

            SerializationProperties params = controller.getExecutable().getPrimarySerializationProperties();
            Receiver receiver = destination.getReceiver(controller.makePipelineConfiguration(), params);
            receiver.open();
            uf.process(context, vr, new ComplexContentOutputter(receiver));
            receiver.close();

        } catch (XPathException e) {
            getErrorReporter().report(new XmlProcessingException(e));
            throw new SaxonApiException(e);
        }

        destination.closeAndNotify();
    }

    /**
     * Construct a {@link Destination} object whose effect is to perform this transformation
     * on any input that is sent to that {@link Destination}: for example, it allows this transformation
     * to post-process the results of another transformation.
     * <p>
     * This method allows a pipeline of transformations to be created in which
     * one transformation is used as the destination of another. The transformations
     * may use streaming, in which case intermediate results will not be materialized
     * in memory. If a transformation does not use streaming, then its input will
     * first be assembled in memory as a node tree.
     * <p>
     * The {@link Destination} returned by this method performs <em>sequence normalization</em>
     * as defined in the serialization specification: that is, the raw result of the transformation
     * sent to this destination is wrapped into a document node. Any item-separator present in
     * any serialization parameters is ignored (adjacent atomic values are separated by whitespace).
     * This makes the method unsuitable for passing intermediate results other than XML document
     * nodes.
     *
     * @return a {@link Destination} which accepts an XML document (typically as a stream
     * of events) and which transforms this supplied XML document (possibly using streaming)
     * as defined by the stylesheet from which which this {@code Xslt30Transformer} was generated,
     * sending the principal result of the transformation to the supplied {@code finalDestination}.
     * The transformation is performed as if by the {@link #applyTemplates(Source, Destination)}
     * method: that is, by applying templates to the root node of the supplied XML document.
     * @since 9.9
     */

    public Destination asDocumentDestination(Destination finalDestination) {
        return new AbstractDestination() {

            Receiver r;
            @Override
            public Receiver getReceiver(PipelineConfiguration pipe, SerializationProperties params) throws SaxonApiException {
                Receiver rt = getReceivingTransformer(controller, globalParameterSet, finalDestination);
                rt = new SequenceNormalizerWithSpaceSeparator(rt);
                rt.setPipelineConfiguration(pipe);
                return r = rt;
            }

            @Override
            public void close() throws SaxonApiException {
                try {
                    r.close();
                } catch (XPathException e) {
                    throw new SaxonApiException(e);
                }
            }
        };
    }

    /**
     * Create a serializer initialised to use the default output parameters defined in the stylesheet.
     * These serialization parameters can be overridden by use of
     * {@link Serializer#setOutputProperty(Serializer.Property, String)}.
     *
     * @since 9.7.0.1
     */

    public Serializer newSerializer() {
        Serializer serializer = processor.newSerializer();
        serializer.setOutputProperties(controller.getExecutable().getPrimarySerializationProperties());
        return serializer;
    }

    /**
     * Create a serializer initialised to use the default output parameters defined in the stylesheet.
     * These serialization parameters can be overridden by use of
     * {@link Serializer#setOutputProperty(Serializer.Property, String)}.
     *
     * @param file the output file to which the serializer will write its output. As well as initializing
     *             the serializer to write to this output file, this method sets the base output URI of this
     *             Xslt30Transformer to be the URI of this file.
     * @since 9.7.0.1
     */

    public Serializer newSerializer(File file) {
        Serializer serializer = processor.newSerializer(file);
        serializer.setOutputProperties(controller.getExecutable().getPrimarySerializationProperties());
        setBaseOutputURI(file.toURI().toString());
        return serializer;
    }

    /**
     * Create a serializer initialised to use the default output parameters defined in the stylesheet.
     * These serialization parameters can be overridden by use of
     * {@link Serializer#setOutputProperty(Serializer.Property, String)}.
     *
     * @param writer the Writer to which the serializer will write
     * @since 9.7.0.1
     */

    public Serializer newSerializer(Writer writer) {
        Serializer serializer = newSerializer();
        serializer.setOutputWriter(writer);
        return serializer;
    }

    /**
     * Create a serializer initialised to use the default output parameters defined in the stylesheet.
     * These serialization parameters can be overridden by use of
     * {@link Serializer#setOutputProperty(Serializer.Property, String)}.
     *
     * @param stream the output stream to which the serializer will write
     * @since 9.7.0.1
     */

    public Serializer newSerializer(OutputStream stream) {
        Serializer serializer = newSerializer();
        serializer.setOutputStream(stream);
        return serializer;
    }




}

