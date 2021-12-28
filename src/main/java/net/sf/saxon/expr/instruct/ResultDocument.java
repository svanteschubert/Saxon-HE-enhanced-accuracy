////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.NamespaceReducer;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.IriToUri;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.serialize.PrincipalOutputGatekeeper;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.style.XSLResultDocument;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XmlProcessingException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


/**
 * The compiled form of an xsl:result-document element in the stylesheet.
 * <p>The xsl:result-document element takes an attribute href="filename". The filename will
 * often contain parameters, e.g. {position()} to ensure that a different file is produced
 * for each element instance.</p>
 * <p>There is a further attribute "format" which determines the format of the
 * output file, it identifies the name of an xsl:output element containing the output
 * format details. In addition, individual serialization properties may be specified as attributes.
 * These are attribute value templates, so they may need to be computed at run-time.</p>
 */

public class ResultDocument extends Instruction
        implements ValidatingInstruction, InstructionWithComplexContent, ContextOriginator {

    private Operand hrefOp;
    private Operand formatOp;    // null if format was known at compile time
    private Operand contentOp;
    private boolean async = false;
    private final Properties globalProperties;
    private final Properties localProperties;
    private ParseOptions validationOptions;
    private final Map<StructuredQName, Operand> serializationAttributes;
    private boolean resolveAgainstStaticBase = false;        // used with fn:put()
    private final CharacterMapIndex characterMapIndex;


    /**
     * Create a result-document instruction
     *
     * @param globalProperties        properties defined on static xsl:output
     * @param localProperties         non-AVT properties defined on result-document element
     * @param href                    href attribute of instruction
     * @param formatExpression        format attribute of instruction
     * @param validationAction        for example {@link net.sf.saxon.lib.Validation#STRICT}
     * @param schemaType              schema type against which output is to be validated
     * @param serializationAttributes computed local properties
     */

    public ResultDocument(Properties globalProperties,      // properties defined on static xsl:output
                          Properties localProperties,       // non-AVT properties defined on result-document element
                          Expression href,
                          Expression formatExpression,      // AVT defining the output format
                          int validationAction,
                          SchemaType schemaType,
                          Map<StructuredQName, Expression> serializationAttributes,  // computed local properties only
                          CharacterMapIndex characterMapIndex) {
        this.globalProperties = globalProperties;
        this.localProperties = localProperties;
        if (href != null) {
            hrefOp = new Operand(this, href, OperandRole.SINGLE_ATOMIC);
        }
        if (formatExpression != null) {
            formatOp = new Operand(this, formatExpression, OperandRole.SINGLE_ATOMIC);
        }
        setValidationAction(validationAction, schemaType);
        this.serializationAttributes = new HashMap<>(serializationAttributes.size());
        for (Map.Entry<StructuredQName, Expression> entry : serializationAttributes.entrySet()) {
            this.serializationAttributes.put(entry.getKey(), new Operand(this, entry.getValue(), OperandRole.SINGLE_ATOMIC));
        }
        this.characterMapIndex = characterMapIndex;
        //this.nsResolver = nsResolver;
        for (Expression e : serializationAttributes.values()) {
            adoptChildExpression(e);
        }
    }

    /**
     * Set the expression that constructs the content
     *
     * @param content the expression defining the content of the result document
     */

    public void setContentExpression(Expression content) {
        contentOp = new Operand(this, content, OperandRole.SINGLE_ATOMIC);
    }

    /**
     * Set the schema type to be used for validation
     *
     * @param type the type to be used for validation. (For a document constructor, this is the required
     *             type of the document element)
     */

    public void setSchemaType(SchemaType type) {
        if (validationOptions == null) {
            validationOptions = new ParseOptions();
        }
        validationOptions.setSchemaValidationMode(Validation.BY_TYPE);
        validationOptions.setTopLevelType(type);
    }

    /**
     * Get the schema type chosen for validation; null if not defined
     *
     * @return the type to be used for validation. (For a document constructor, this is the required
     * type of the document element)
     */

    @Override
    public SchemaType getSchemaType() {
        return validationOptions == null ? null : validationOptions.getTopLevelType();
    }


    public boolean isResolveAgainstStaticBase() {
        return resolveAgainstStaticBase;
    }

    /**
     * Get the validation options
     *
     * @return the validation options for the content of the constructed node. May be null if no
     * validation was requested.
     */

    public ParseOptions getValidationOptions() {
        return validationOptions;
    }

    /**
     * Set the validation mode for the new document
     *
     * @param mode       the validation mode, for example {@link Validation#STRICT}
     * @param schemaType the required type (for validation by type). Null if not
     *                   validating by type
     */


    public void setValidationAction(int mode, /*@Nullable*/ SchemaType schemaType) {
        boolean preservingTypes = mode == Validation.PRESERVE && schemaType == null;
        if (!preservingTypes) {
            if (validationOptions == null) {
                validationOptions = new ParseOptions();
                validationOptions.setSchemaValidationMode(mode);
                validationOptions.setTopLevelType(schemaType);
            }
        }
    }


    /**
     * Get the validation mode for this instruction
     *
     * @return the validation mode, for example {@link Validation#STRICT} or {@link Validation#PRESERVE}
     */
    @Override
    public int getValidationAction() {
        return validationOptions == null ? Validation.PRESERVE : validationOptions.getSchemaValidationMode();
    }


    public Expression getFormatExpression() {
        return formatOp == null ? null : formatOp.getChildExpression();
    }

    /**
     * Set whether the the instruction should resolve the href relative URI against the static
     * base URI (rather than the dynamic base output URI)
     *
     * @param staticBase set to true by fn:put(), to resolve against the static base URI of the query.
     *                   Default is false, which causes resolution against the base output URI obtained dynamically
     *                   from the Controller
     */

    public void setUseStaticBaseUri(boolean staticBase) {
        resolveAgainstStaticBase = staticBase;
    }


    public void setAsynchronous(boolean async) {
        this.async = async;
    }

    /**
     * Ask if the instruction is to be asynchronous
     *
     * @return true unless saxon:asynchronous="no" was specified (regardless of other options that might
     * suppress asychronous operation)
     */

    public boolean isAsynchronous() {
        return async;
    }

    @Override
    public boolean isMultiThreaded(Configuration config) {
        return isAsynchronous() && config.isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION)
                && config.getBooleanProperty(Feature.ALLOW_MULTITHREADING);
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        String method = getStaticSerializationProperty(XSLResultDocument.METHOD);
        boolean contentDependentMethod = method == null && formatOp == null &&
                !serializationAttributes.containsKey(XSLResultDocument.METHOD);
        boolean buildTree = "yes".equals(getStaticSerializationProperty(XSLResultDocument.BUILD_TREE));
        if (buildTree || contentDependentMethod ||
                "xml".equals(method) || "html".equals(method) || "xhtml".equals(method) || "text".equals(method)) {
            try {
                DocumentInstr.checkContentSequence(visitor.getStaticContext(), contentOp, validationOptions);
            } catch (XPathException err) {
                err.maybeSetLocation(getLocation());
                throw err;
            }
        }
        return this;
    }

    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.HAS_SIDE_EFFECTS;
    }

    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        optimizeChildren(visitor, contextInfo);
        if (isAsynchronous()) {
            Expression e = getParentExpression();
            while (e != null) {
                if (e instanceof LetExpression && ExpressionTool.dependsOnVariable(
                        getContentExpression(), new Binding[]{(LetExpression) e})) {
                    ((LetExpression) e).setNeedsEagerEvaluation(true);
                }
                e = e.getParentExpression();
            }

        }
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings variable bindings that need to be changed
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        Map<StructuredQName, Expression> map = new HashMap<>();
        for (Map.Entry<StructuredQName, Operand> entry : serializationAttributes.entrySet()) {
            map.put(entry.getKey(), entry.getValue().getChildExpression().copy(rebindings));
        }
        ResultDocument r = new ResultDocument(
                globalProperties,
                localProperties,
                getHref() == null ? null : getHref().copy(rebindings),
                getFormatExpression() == null ? null : getFormatExpression().copy(rebindings),
                getValidationAction(),
                getSchemaType(),
                map,
                characterMapIndex);
        ExpressionTool.copyLocationInfo(this, r);
        r.setContentExpression(getContentExpression().copy(rebindings));
        r.resolveAgainstStaticBase = resolveAgainstStaticBase;
        r.async = async;
        return r;
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     * (the string "xsl:result-document")
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_RESULT_DOCUMENT;
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction. This is empty: the result-document instruction
     * returns nothing.
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return ErrorType.getInstance();
    }

    @Override
    public Iterable<Operand> operands() {
        ArrayList<Operand> list = new ArrayList<>(6);
        list.add(contentOp);
        if (hrefOp != null) {
            list.add(hrefOp);
        }
        if (formatOp != null) {
            list.add(formatOp);
        }
        list.addAll(serializationAttributes.values());
        return list;
    }

    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNodeSet representing the points in the source document that are both reachable by this
     * expression, and that represent possible results of this expression. For an expression that does
     * navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     * expressions, it is the same as the input pathMapNode.
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet result = super.addToPathMap(pathMap, pathMapNodeSet);
        result.setReturnable(false);
        return new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this));
    }


    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        process(getContentExpression(), context);
        return null;
    }

    public void process(Expression content, XPathContext context) throws XPathException {
        checkNotTemporaryOutputState(context);
        context.getConfiguration().processResultDocument(this, content, context);
    }

    /**
     * Evaluation method designed for calling from compiled bytecode.
     *
     * @param content The content expression. When called from bytecode, this will be the compiled version
     *                of the interpreted content expression
     * @param context dynamic evaluation context
     * @throws XPathException if a dynamic error occurs
     */

    public void processInstruction(Expression content, XPathContext context) throws XPathException {
        final XsltController controller = (XsltController) context.getController();
        assert controller != null;

        String savedOutputUri = context.getCurrentOutputUri();
        ComplexContentOutputter out = processLeft(context);
        boolean failed = false;
        try {
            content.process(out, context);
        } catch (XPathException err) {
            failed = true;
            err.maybeSetContext(context);
            err.maybeSetLocation(getLocation());
            throw err;
        } finally {
            try {
                out.close();
            } catch (XPathException e) {
                if (!failed) {
                    //noinspection ThrowFromFinallyBlock
                    throw e;
                }
                // Otherwise, no further action; report the original error in preference. Bug 4227
            }
        }
        context.setCurrentOutputUri(savedOutputUri);

    }

    public ComplexContentOutputter processLeft(XPathContext context) throws XPathException {
        XsltController controller = (XsltController)context.getController();
        Configuration config = controller.getConfiguration();
        checkNotTemporaryOutputState(context);

        Properties computedLocalProps = gatherOutputProperties(context);
        if (computedLocalProps.getProperty(SaxonOutputKeys.PARAMETER_DOCUMENT) != null && getStaticBaseURIString() != null) {
            try {
                String abs = ResolveURI.makeAbsolute(
                        computedLocalProps.getProperty(SaxonOutputKeys.PARAMETER_DOCUMENT),
                        getStaticBaseURIString()).toASCIIString();
                computedLocalProps.setProperty(SaxonOutputKeys.PARAMETER_DOCUMENT, abs);
            } catch (URISyntaxException e) {
                throw XPathException.makeXPathException(e);
            }
            //computedLocalProps.setProperty(SaxonOutputKeys.PARAMETER_DOCUMENT_BASE_URI, getStaticBaseURIString());
        }
        SerializationProperties serParams = new SerializationProperties(computedLocalProps, characterMapIndex);

        // If validation was requested, create a function that instantiates a validator
        // which can then be injected at an appropriate point into the output pipeline

        if (validationOptions != null && validationOptions.getSchemaValidationMode() != Validation.PRESERVE) {
            serParams.setValidationFactory(
                    output -> {
                        // Validation can add redundant namespace declarations so we
                        // need to follow it with a namespace reducer
                        NamespaceReducer nr = new NamespaceReducer(output);
                        return config.getDocumentValidator(
                                nr, output.getSystemId(), validationOptions, getLocation());
                    }
            );
        }

        Receiver out = null;
        ResultDocumentResolver resolver = null;
        String hrefValue = "";
        if (getHref() != null) {
            hrefValue = IriToUri.iriToUri(getHref().evaluateAsString(context)).toString();
        }
        if (hrefValue.isEmpty() || hrefValue.equals(controller.getBaseOutputURI())) {
            PrincipalOutputGatekeeper gateKeeper = controller.getGatekeeper();
            if (gateKeeper != null) {
                gateKeeper.useAsSecondary();
                out = gateKeeper.makeReceiver(serParams);
            }
        }

        if (out == null) {
            try {
                resolver = controller.getResultDocumentResolver();
                out = makeReceiver(hrefValue, getStaticBaseURIString(), context,
                                   resolver, serParams, resolveAgainstStaticBase);
                traceDestination(context, out);
            } catch (XPathException e) {
                e.maybeSetLocation(getLocation());
                e.maybeSetContext(context);
                throw e;
            }
        }
        out.getPipelineConfiguration().setController(controller);
        String systemId = out.getSystemId();

        NamespaceReducer nr = new NamespaceReducer(out);
        ComplexContentOutputter cco = new ComplexContentOutputter(nr);
        cco.setSystemId(systemId);
        //context.setReceiver(cco);
        context.setCurrentOutputUri(systemId);

        cco.open();
        return cco;
    }

    public CharacterMapIndex getCharacterMapIndex() {
        return characterMapIndex;
    }

    private void checkNotTemporaryOutputState(XPathContext context) throws XPathException {
        if (context.getTemporaryOutputState() != 0) {
            XPathException err = new XPathException("Cannot execute xsl:result-document while evaluating xsl:" +
                                                            context.getNamePool().getLocalName(context.getTemporaryOutputState()));
            err.setErrorCode("XTDE1480");
            err.setLocation(getLocation());
            throw err;
        }
    }

    public static Receiver makeReceiver(String hrefValue, String baseURI,
                                        XPathContext context, ResultDocumentResolver resolver,
                                        SerializationProperties params,
                                        boolean resolveAgainstStaticBase) throws XPathException {
        String resultURI = null;
        Controller controller = context.getController();
        try {
            String base;
            if (resolveAgainstStaticBase) {
                base = baseURI;
            } else {
                base = controller.getBaseOutputURI();
            }

            try {
                Receiver out = resolver.resolve(context, hrefValue, base, params);
                String systemId = out.getSystemId();
                if (systemId == null) {
                    systemId = ResolveURI.makeAbsolute(hrefValue, base).toASCIIString();
                    out.setSystemId(systemId);
                }
                checkAcceptableUri(context, systemId);
                return out;
            } catch (XPathException e) {
                throw e;
            } catch (Exception err) {
                err.printStackTrace();
                throw new XPathException("Exception thrown by output resolver", err);
            }
        } catch (TransformerException e) {
            throw XPathException.makeXPathException(e);
        }

    }

    public static void traceDestination(XPathContext context, Result result) {
        Configuration config = context.getConfiguration();
        boolean timing = config.isTiming();
        if (timing) {
            String dest = result.getSystemId();
            if (dest == null) {
                if (result instanceof StreamResult) {
                    dest = "anonymous output stream";
                } else if (result instanceof SAXResult) {
                    dest = "SAX2 ContentHandler";
                } else if (result instanceof DOMResult) {
                    dest = "DOM tree";
                } else {
                    dest = result.getClass().getName();
                }
            }
            config.getLogger().info("Writing to " + dest);
        }
    }

    public static void checkAcceptableUri(XPathContext context, String uri) throws XPathException {
        XsltController controller = (XsltController) context.getController();
        assert controller != null;
        if (uri != null) {
            if (controller.getDocumentPool().find(uri) != null) {
                XPathException err = new XPathException("Cannot write to a URI that has already been read: " +
                                                                (uri.equals(Controller.ANONYMOUS_PRINCIPAL_OUTPUT_URI) ? "(implicit output URI)" : uri));
                err.setXPathContext(context);
                err.setErrorCode("XTDE1500");
                throw err;
            }

            DocumentKey documentKey = new DocumentKey(uri);
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized(controller) {
                if (!controller.checkUniqueOutputDestination(documentKey)) {
                    XPathException err = new XPathException("Cannot write more than one result document to the same URI: " +
                                                                    (uri.equals(Controller.ANONYMOUS_PRINCIPAL_OUTPUT_URI) ? "(implicit output URI)" : uri));
                    err.setXPathContext(context);
                    err.setErrorCode("XTDE1490");
                    throw err;
                } else {
                    controller.addUnavailableOutputDestination(documentKey);
                }
            }
        }
    }

    /**
     * Create a properties object that combines the serialization properties specified
     * on the xsl:result-document itself with those specified in the referenced xsl:output declaration
     *
     * @param context The XPath evaluation context
     * @return the assembled properties
     * @throws XPathException if invalid properties are found
     */

    public Properties gatherOutputProperties(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        Configuration config = context.getConfiguration();
        Properties computedGlobalProps = globalProperties;
        NamespaceResolver nsResolver = getRetainedStaticContext();
        assert nsResolver != null;

        if (getFormatExpression() != null) {
            // format was an AVT and now needs to be computed
            StructuredQName qName;
            String format = getFormatExpression().evaluateAsString(context).toString();
            if (format.startsWith("Q{")) {
                qName = StructuredQName.fromEQName(format);
            } else {
                String[] parts;
                try {
                    parts = NameChecker.getQNameParts(format);
                } catch (QNameException e) {
                    XPathException err = new XPathException("The requested output format " + Err.wrap(format) + " is not a valid QName");
                    err.maybeSetLocation(getFormatExpression().getLocation());
                    err.setErrorCode("XTDE1460");
                    err.setXPathContext(context);
                    throw err;
                }
                String uri = nsResolver.getURIForPrefix(parts[0], false);
                if (uri == null) {
                    XPathException err = new XPathException("The namespace prefix in the format name " + format + " is undeclared");
                    err.maybeSetLocation(getFormatExpression().getLocation());
                    err.setErrorCode("XTDE1460");
                    err.setXPathContext(context);
                    throw err;
                }
                qName = new StructuredQName(parts[0], uri, parts[1]);
            }
            computedGlobalProps = ((StylesheetPackage) getRetainedStaticContext().getPackageData()).getNamedOutputProperties(qName);
            if (computedGlobalProps == null) {
                XPathException err = new XPathException("There is no xsl:output format named " + format);
                err.setErrorCode("XTDE1460");
                err.setXPathContext(context);
                throw err;
            }

        }

        // Now combine the properties specified on xsl:result-document with those specified on xsl:output

        Properties computedLocalProps = new Properties(computedGlobalProps);

        // First handle the properties with fixed values on xsl:result-document

        for (Object keyo : localProperties.keySet()) {
            String key = (String) keyo;
            StructuredQName qName = StructuredQName.fromClarkName(key);
            try {
                setSerializationProperty(computedLocalProps, qName.getURI(), qName.getLocalPart(),
                                         localProperties.getProperty(key), nsResolver, true, config);
            } catch (XPathException e) {
                e.setErrorCode("XTDE0030");
                e.maybeSetLocation(getLocation());
                throw e;
            }
        }

        // Now add the properties that were specified as AVTs

        if (!serializationAttributes.isEmpty()) {
            for (Map.Entry<StructuredQName, Operand> entry : serializationAttributes.entrySet()) {
                String value = entry.getValue().getChildExpression().evaluateAsString(context).toString();
                String lname = entry.getKey().getLocalPart();
                String uri = entry.getKey().getURI();
                try {
                    setSerializationProperty(computedLocalProps, uri, lname, value, nsResolver, false, config);
                } catch (XPathException e) {
                    e.setErrorCode("XTDE0030");
                    e.maybeSetLocation(getLocation());
                    e.maybeSetContext(context);
                    if (NamespaceConstant.SAXON.equals(e.getErrorCodeNamespace()) &&
                            "SXWN".equals(e.getErrorCodeLocalPart().substring(0, 4))) {
                        XmlProcessingException ee = new XmlProcessingException(e);
                        ee.setWarning(true);
                        controller.getErrorReporter().report(ee);
                    } else {
                        throw e;
                    }
                }
            }
        }

        return computedLocalProps;
    }

    /**
     * Get the value of a serialization property if it is statically known
     *
     * @param name the name of the serialization property
     * @return the value of the serialization property if known statically; or null otherwise
     */

    public String getStaticSerializationProperty(StructuredQName name) {
        String clarkName = name.getClarkName();
        String local = localProperties.getProperty(clarkName);
        if (local != null) {
            return local;
        }
        if (serializationAttributes.containsKey(name)) {
            return null; // value is computed dynamically
        }
        return globalProperties.getProperty(clarkName);
    }

    /**
     * Validate a serialization property and add its value to a Properties collection
     *
     * @param details      the properties to be updated
     * @param uri          the uri of the property name
     * @param lname        the local part of the property name
     * @param value        the value of the serialization property. In the case of QName-valued values,
     *                     this will use lexical QNames if prevalidated is false and a NamespaceResolver is supplied;
     *                     otherwise they will use Clark-format names
     * @param nsResolver   resolver for lexical QNames; not needed if prevalidated, or if QNames are supplied in Clark
     *                     format
     * @param prevalidated true if values are already known to be valid and lexical QNames have been
     *                     expanded into Clark notation
     * @param config       the Saxon configuration
     * @throws XPathException if any serialization property has an invalid value
     */

    public static void setSerializationProperty(Properties details, String uri, String lname,
                                                String value, /*@Nullable*/ NamespaceResolver nsResolver,
                                                boolean prevalidated, Configuration config)
            throws XPathException {
        SerializerFactory sf = config.getSerializerFactory();
        String clarkName = lname;
        if (!uri.isEmpty()) {
            clarkName = "{" + uri + "}" + lname;
        }
        if (uri.isEmpty() || NamespaceConstant.SAXON.equals(uri)) {
            switch (clarkName) {
                case "method":
                    value = Whitespace.trim(value);
                    if (value.startsWith("Q{}") && value.length() > 3) {
                        value = value.substring(3);
                    }
                    if (value.equals("xml") || value.equals("html") ||
                            value.equals("text") || value.equals("xhtml") ||
                            value.equals("json") || value.equals("adaptive") ||
                            prevalidated || value.startsWith("{")) {
                        details.setProperty(OutputKeys.METHOD, value);
                    } else if (value.startsWith("Q{")) {
                        details.setProperty(OutputKeys.METHOD, value.substring(1));
                    } else {
                        String[] parts;
                        try {
                            parts = NameChecker.getQNameParts(value);
                            String prefix = parts[0];
                            if (prefix.isEmpty()) {
                                XPathException err = new XPathException("method must be xml, html, xhtml, text, json, adaptive, or a prefixed name");
                                err.setErrorCode("SEPM0016");
                                err.setIsStaticError(true);
                                throw err;
                            } else if (nsResolver != null) {
                                String muri = nsResolver.getURIForPrefix(prefix, false);
                                if (muri == null) {
                                    XPathException err = new XPathException("Namespace prefix '" + prefix + "' has not been declared");
                                    err.setErrorCode("SEPM0016");
                                    err.setIsStaticError(true);
                                    throw err;
                                }
                                details.setProperty(OutputKeys.METHOD, '{' + muri + '}' + parts[1]);
                            } else {
                                details.setProperty(OutputKeys.METHOD, value);
                            }
                        } catch (QNameException e) {
                            XPathException err = new XPathException("Invalid method name. " + e.getMessage());
                            err.setErrorCode("SEPM0016");
                            err.setIsStaticError(true);
                            throw err;
                        }
                    }
                    break;
                case "use-character-maps":
                    // The use-character-maps attribute is always turned into a Clark-format name at compile time
                    String existing = details.getProperty(SaxonOutputKeys.USE_CHARACTER_MAPS);
                    if (existing == null) {
                        existing = "";
                    }
                    details.setProperty(SaxonOutputKeys.USE_CHARACTER_MAPS, existing + value);
                    break;
                case "cdata-section-elements":
                    processListOfNodeNames(details, clarkName, value, nsResolver, true, prevalidated, false);
                    break;
                case "suppress-indentation":
                    processListOfNodeNames(details, clarkName, value, nsResolver, true, prevalidated, false);
                    break;
                case SaxonOutputKeys.DOUBLE_SPACE:
                    processListOfNodeNames(details, clarkName, value, nsResolver, true, prevalidated, false);
                    break;
                case SaxonOutputKeys.ATTRIBUTE_ORDER:
                    processListOfNodeNames(details, clarkName, value, nsResolver, false, prevalidated, true);
                    break;
                case SaxonOutputKeys.NEXT_IN_CHAIN:
//                XPathException e = new XPathException("saxon:next-in-chain property is available only on xsl:output");
//                e.setErrorCodeQName(
//                        new StructuredQName("saxon", NamespaceConstant.SAXON, SaxonErrorCode.SXWN9004));
//                throw e;
                    break;
                default:
                    // all other properties in the default or Saxon namespaces
                    if (clarkName.equals("output-version")) {
                        clarkName = "version";
                    }
                    if (!prevalidated) {
                        try {
                            if (!SaxonOutputKeys.isUnstrippedProperty(clarkName)) {
                                // TODO: whitespace rules seem to vary for different interfaces
                                value = Whitespace.trim(value);
                            }
                            value = sf.checkOutputProperty(clarkName, value);
                        } catch (XPathException err) {
                            err.maybeSetErrorCode("SEPM0016");
                            throw err;
                        }
                    }
                    details.setProperty(clarkName, value);
                    break;
            }
        } else {
            // properties in user-defined namespaces
            details.setProperty('{' + uri + '}' + lname, value);
        }

    }

    private static void processListOfNodeNames(Properties details, String key, String value,
                                               NamespaceResolver nsResolver, boolean useDefaultNS,
                                               boolean prevalidated, boolean allowStar) throws XPathException {
        String existing = details.getProperty(key);
        if (existing == null) {
            existing = "";
        }
        String s = SaxonOutputKeys.parseListOfNodeNames(value, nsResolver, useDefaultNS, prevalidated, allowStar, "SEPM0016");
        details.setProperty(key, existing + s);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("resultDoc", this);
        out.emitAttribute("global", exportProperties(globalProperties));
        out.emitAttribute("local", exportProperties(localProperties));
        if (getValidationAction() != Validation.SKIP && getValidationAction() != Validation.BY_TYPE) {
            out.emitAttribute("validation", Validation.toString(getValidationAction()));
        }
        final SchemaType schemaType = getSchemaType();
        if (schemaType != null) {
            out.emitAttribute("type", schemaType.getStructuredQName());
        }
        if (getHref() != null) {
            out.setChildRole("href");
            getHref().export(out);
        }
        if (getFormatExpression() != null) {
            out.setChildRole("format");
            getFormatExpression().export(out);
        }
        for (Map.Entry<StructuredQName, Operand> p : serializationAttributes.entrySet()) {
            StructuredQName name = p.getKey();
            Expression value = p.getValue().getChildExpression();
            out.setChildRole(name.getEQName());
            value.export(out);
        }
        out.setChildRole("content");
        getContentExpression().export(out);
        out.endElement();
    }

    private String exportProperties(Properties props) {
        StringBuilder writer = new StringBuilder();
        for (String key : props.stringPropertyNames()) {
            String val = props.getProperty(key);
            if (key.equals(SaxonOutputKeys.ITEM_SEPARATOR) || key.equals(SaxonOutputKeys.NEWLINE)) {
                val = ExpressionPresenter.jsEscape(val);
            }
            if (key.equals(SaxonOutputKeys.USE_CHARACTER_MAPS) || key.equals(OutputKeys.METHOD)) {
                // TODO: other QName-valued fields such as cdata-section-elements??
                val = val.replace("{", "Q{");
            }
            if (key.startsWith("{")) {
                key = "Q" + key;
            }
            writer.append(key).append("=").append(val).append("\n");
        }
        return writer.toString();
    }

    /**
     * Construct a set of output properties from an xsl:output element supplied at run-time
     *
     * @param element an xsl:output element
     * @param props   Properties object to which will be added the values of those serialization properties
     *                that were specified
     * @param c       the XPath dynamic context
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs
     */

    public static void processXslOutputElement(NodeInfo element, Properties props, XPathContext c) throws XPathException {
        NamespaceResolver resolver = element.getAllNamespaces();
        for (AttributeInfo att : element.attributes()) {
            String uri = att.getNodeName().getURI();
            String local = att.getNodeName().getLocalPart();
            String val = Whitespace.trim(att.getValue());
            setSerializationProperty(props, uri, local, val, resolver, false, c.getConfiguration());
        }
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "ResultDocument";
    }


    public Expression getHref() {
        return hrefOp == null ? null : hrefOp.getChildExpression();
    }

    public void setHref(Expression href) {
        hrefOp.setChildExpression(href);
    }

    public void setFormatExpression(Expression formatExpression) {
        formatOp.setChildExpression(formatExpression);
    }

    @Override
    public Expression getContentExpression() {
        return contentOp.getChildExpression();
    }

}

