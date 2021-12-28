////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Controller;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.DocumentFn;
import net.sf.saxon.functions.ElementAvailable;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XmlProcessingException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.packages.UsePack;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * This is a filter inserted into the input pipeline for processing stylesheet modules, whose
 * task is to evaluate use-when expressions and discard those parts of the stylesheet module
 * for which the use-when attribute evaluates to false.
 *
 * <p>Originally, with XSLT 2.0, this class did use-when filtering and very little else.
 * In XSLT 3.0 its role has expanded: it evaluates shadow attributes and static variables,
 * and collects information about package dependencies.</p>
 */

public class UseWhenFilter extends ProxyReceiver {

    private int depthOfHole = 0;
    private boolean emptyStylesheetElement = false;
    private Stack<String> defaultNamespaceStack = new Stack<>();
    private Stack<Integer> versionStack = new Stack<>();
    private DateTimeValue currentDateTime = DateTimeValue.getCurrentDateTime(null);
    private Compilation compilation;
    private Stack<String> systemIdStack = new Stack<>();
    private Stack<URI> baseUriStack = new Stack<>();
    private NestedIntegerValue precedence;
    private int importCount = 0;
    private boolean dropUnderscoredAttributes;
    private LinkedTreeBuilder treeBuilder;



    /**
     * Create a UseWhenFilter
     *
     * @param compilation the compilation episode
     * @param next        the next receiver in the pipeline
     * @param precedence the import precedence expressed as a dotted-decimal integer, e.g. 1.4.6
     */

    public UseWhenFilter(Compilation compilation, Receiver next, NestedIntegerValue precedence) {
        super(next);
        this.compilation = compilation;
        this.precedence = precedence;
        assert (next instanceof LinkedTreeBuilder); // Currently always true; but the design
                  // tries to avoid assuming it will always be true
        treeBuilder = (LinkedTreeBuilder)next;
    }

    /**
     * Start of document
     */

    @Override
    public void open() throws XPathException {
        nextReceiver.open();

        String sysId = getSystemId();
        if (sysId == null) {
            sysId = "";
        }
        systemIdStack.push(sysId);
        try {
            baseUriStack.push(new URI(sysId));
        } catch (URISyntaxException e) {
            try {
                baseUriStack.push(new File(sysId).toURI());
            } catch (Exception ex) {
                //throw new XPathException("Invalid URI for stylesheet: " + getSystemId());
            }

        }

    }

    /**
     * Notify the start of an element.
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        int fp = elemName.obtainFingerprint(getNamePool());
        boolean inXsltNamespace = elemName.hasURI(NamespaceConstant.XSLT);
        boolean inSaxonNamespace = elemName.hasURI(NamespaceConstant.SAXON);
        String stdAttUri = inXsltNamespace ? "" : NamespaceConstant.XSLT;

        String xpathDefaultNamespaceAtt = null;
        String versionAtt = null;
        String xmlBaseAtt = null;
        String useWhenAtt = null;
        String staticAtt = null;
        boolean hasShadowAttributes = false;
        DocumentImpl includedDoc = null;

        for (AttributeInfo att : attributes) {
            NodeName attName = att.getNodeName();
            attName.obtainFingerprint(getNamePool());
            String uri = attName.getURI();
            if (uri.equals(stdAttUri)) {
                String local = attName.getLocalPart();
                switch (local) {
                    case "xpath-default-namespace":
                        xpathDefaultNamespaceAtt = att.getValue();
                        break;
                    case "version":
                        versionAtt = att.getValue();
                        break;
                    case "use-when":
                        useWhenAtt = att.getValue();
                        break;
                    case "static":
                        staticAtt = att.getValue();
                        break;
                }
                if (local.startsWith("_")
                        && uri.equals("")
                        && (inXsltNamespace || inSaxonNamespace)) {
                    hasShadowAttributes = true;
                }
            } else if (inSaxonNamespace || uri.equals(NamespaceConstant.SAXON)) {
                if (attName.getLocalPart().startsWith("_")) {
                    hasShadowAttributes = true;
                }
            } else if (uri.equals(NamespaceConstant.XML)) {
                if (attName.getLocalPart().equals("base")) {
                    xmlBaseAtt = att.getValue();
                }
            }
        }

        defaultNamespaceStack.push(xpathDefaultNamespaceAtt);
        if (emptyStylesheetElement) {
            depthOfHole++;
            return;
        }
        if (depthOfHole == 0) {
            URI baseUri = processBaseUri(location, xmlBaseAtt);

            boolean ignore = false;

            int version = Integer.MIN_VALUE;
            if (versionAtt != null && fp != StandardNames.XSL_OUTPUT) {
                version = processVersionAttribute(versionAtt);
            }

            if (version == Integer.MIN_VALUE) {
                version = versionStack.isEmpty() ? 30 : versionStack.peek();
            }
            versionStack.push(version);

            if (inXsltNamespace && defaultNamespaceStack.size() == 2
                    && version > 30 && !ElementAvailable.isXslt30Element(fp)) {
                // top level unknown XSLT element is ignored in forwards-compatibility mode
                ignore = true;
            }

            if (hasShadowAttributes && !ignore) {
                attributes = processShadowAttributes(elemName, attributes, namespaces, location, baseUri);
                String uw = attributes.getValue(stdAttUri, "use-when");
                if (uw != null) {
                    useWhenAtt = uw;
                }
            }

            if (!ignore) {
                if (useWhenAtt != null) {
                    AttributeLocation attLoc = new AttributeLocation(
                            elemName.getStructuredQName(), new StructuredQName("", stdAttUri, "use-when"), location);
                    boolean use = evaluateUseWhen(useWhenAtt, attLoc, baseUri.toString(), namespaces);
                    ignore = !use;
                }

                if (ignore) {
                    if (fp == StandardNames.XSL_STYLESHEET || fp == StandardNames.XSL_TRANSFORM || fp == StandardNames.XSL_PACKAGE) {
                        emptyStylesheetElement = true;
                    } else {
                        depthOfHole = 1;
                        return;
                    }
                }
            }

            if (inXsltNamespace) {

                if (defaultNamespaceStack.size() == 2) {
                    switch (fp) {
                        case StandardNames.XSL_VARIABLE:
                        case StandardNames.XSL_PARAM:
                            if (hasShadowAttributes) {
                                staticAtt = attributes.getValue("", "static");
                            }
                            if (staticAtt != null) {
                                String staticStr = Whitespace.trim(staticAtt);
                                if (StyleElement.isYes(staticStr)) {
                                    processStaticVariable(elemName, attributes, namespaces,
                                                          location, baseUri, precedence);
                                }
                            }
                            break;

                        case StandardNames.XSL_INCLUDE:
                        case StandardNames.XSL_IMPORT:
                            // We need to process the included/imported stylesheet now, because its static variables
                            // can be used later in this module
                            String href = attributes.getValue("", "href");
                            includedDoc = processIncludeImport(elemName, location, baseUri, href, fp == StandardNames.XSL_IMPORT);
                            break;

                        case StandardNames.XSL_IMPORT_SCHEMA:
                            compilation.setSchemaAware(true);   // bug 3105
                            break;

                        case StandardNames.XSL_USE_PACKAGE:
                            if (precedence.getDepth() > 1) {
                                throw new XPathException("xsl:use-package cannot appear in an imported stylesheet", "XTSE3008");
                            }
                            String name = attributes.getValue("", "name");
                            String pversion = attributes.getValue("", "package-version");
                            if (name != null) {
                                try {
                                    UsePack use = new UsePack(name, pversion, location.saveLocation());
                                    compilation.registerPackageDependency(use);
                                } catch (XPathException err) {
                                    // No action, error will be reported later.
                                }

                            }
                            break;
                    }
                }
            }
            dropUnderscoredAttributes = inXsltNamespace;
            nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
            if (includedDoc != null) {
                XSLGeneralIncorporate node = (XSLGeneralIncorporate)treeBuilder.getCurrentParentNode();
                node.setTargetDocument(includedDoc);
            }
        } else {
            depthOfHole++;
        }
    }

    private DocumentImpl processIncludeImport(
            NodeName elemName, Location location, URI baseUri, String href, boolean isImport) throws XPathException {
        if (href == null) {
            throw new XPathException("Missing href attribute on " + elemName.getDisplayName(), "XTSE0010");
        }

        URIResolver resolver = compilation.getCompilerInfo().getURIResolver();
        String baseUriStr = baseUri.toString();
        DocumentKey key = DocumentFn.computeDocumentKey(href, baseUriStr, compilation.getPackageData(), resolver, false);
        Map<DocumentKey, TreeInfo> map = compilation.getStylesheetModules();
        if (map.containsKey(key)) {
            return (DocumentImpl)map.get(key);
        } else {
            Source source;
            try {
                source = resolver.resolve(href, baseUriStr);
            } catch (TransformerException e) {
                throw XPathException.makeXPathException(e);
            }
            if (source == null) {
                source = getConfiguration().getSystemURIResolver().resolve(href, baseUriStr);
            }
            NestedIntegerValue newPrecedence = precedence;
            if (isImport) {
                newPrecedence = precedence.getStem().append(precedence.getLeaf() - 1).append(2 * ++importCount);
            }
            try {
                DocumentImpl includedDoc = StylesheetModule.loadStylesheetModule(source, false, compilation, newPrecedence);
                map.put(key, includedDoc);
                return includedDoc;
            } catch (XPathException e) {
                e.maybeSetLocation(location);
                e.maybeSetErrorCode("XTSE0165");
                if ("XTSE0180".equals(e.getErrorCodeLocalPart())) {
                    if (isImport) {
                        e.setErrorCode("XTSE0210");
                    }
                }
                if (!e.hasBeenReported()) {
                    compilation.reportError(e);
                }
                throw e;
            }
        }
    }

    private void processStaticVariable(NodeName elemName, AttributeMap attributes,
                                       NamespaceResolver nsResolver,
                                       Location location, URI baseUri,
                                       NestedIntegerValue precedence) throws XPathException {
        String nameStr = attributes.getValue("", "name");
        String asStr = attributes.getValue("", "as");
        String requiredStr = Whitespace.trim(attributes.getValue("", "required"));
        boolean isRequired = StyleElement.isYes(requiredStr);


        UseWhenStaticContext staticContext = new UseWhenStaticContext(compilation, nsResolver);
        staticContext.setBaseURI(baseUri.toString());
        staticContext.setContainingLocation(
                new AttributeLocation(elemName.getStructuredQName(), new StructuredQName("", "", "as"), location));
        SequenceType requiredType = SequenceType.ANY_SEQUENCE;

        int languageLevel = compilation.getConfiguration().getConfigurationProperty(Feature.XPATH_VERSION_FOR_XSLT);
        if (languageLevel == 30) {
            languageLevel = 305; // XPath 3.0 + XSLT extensions
        }
        if (asStr != null) {
            XPathParser parser = compilation.getConfiguration().newExpressionParser("XP", false, languageLevel);
            requiredType = parser.parseSequenceType(asStr, staticContext);
        }

        StructuredQName varName;
        try {
            varName = StructuredQName.fromLexicalQName(nameStr, false, true, nsResolver);
        } catch (XPathException err) {
            throw createXPathException(
                    "Invalid variable name:" + nameStr + ". " + err.getMessage(),
                    err.getErrorCodeLocalPart(), location);
        }

        boolean isVariable = elemName.getLocalPart().equals("variable");
        boolean isParam = elemName.getLocalPart().equals("param");
        boolean isSupplied = isParam && compilation.getParameters().containsKey(varName);
        AttributeLocation attLoc =
                new AttributeLocation(elemName.getStructuredQName(), new StructuredQName("", "", "select"), location);

        if (isParam) {
            if (isRequired && !isSupplied) {
                String selectStr = attributes.getValue("", "select");
                if (selectStr != null) {
                    throw createXPathException("Cannot supply a default value when required='yes'", "XTSE0010", attLoc);
                } else {
                    throw createXPathException(
                            "No value was supplied for the required static parameter $" + varName.getDisplayName(),
                            "XTDE0050", location);
                }
            }

            if (isSupplied) {
                Sequence suppliedValue = compilation.getParameters()
                        .convertParameterValue(varName, requiredType, true, staticContext.makeEarlyEvaluationContext());

                compilation.declareStaticVariable(varName, suppliedValue.materialize(), precedence, isParam);
            }
        }

        if (isVariable || !isSupplied) {
            String selectStr = attributes.getValue("", "select");
            GroundedValue value;
            if (selectStr == null) {
                if (isVariable) {
                    throw createXPathException(
                            "The select attribute is required for a static global variable",
                            "XTSE0010", location);
                } else if (!Cardinality.allowsZero(requiredType.getCardinality())) {
                    throw createXPathException("The parameter is implicitly required because it does not accept an "
                                                       + "empty sequence, but no value has been supplied", "XTDE0700", location);
                } else {
                    value = asStr == null ? StringValue.EMPTY_STRING : EmptySequence.getInstance();
                    compilation.declareStaticVariable(varName, value, precedence, isParam);
                }

            } else {
                try {
                    staticContext.setContainingLocation(attLoc);
                    Sequence seq = evaluateStatic(selectStr, location, staticContext);
                    value = seq.materialize();
                } catch (XPathException e) {
                    throw createXPathException("Error in " + elemName.getLocalPart() + " expression. " + e.getMessage(),
                            e.getErrorCodeLocalPart(), attLoc);
                }
            }
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.VARIABLE, varName.getDisplayName(), 0);
            role.setErrorCode("XTDE0050");
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            Sequence seq = th.applyFunctionConversionRules(value, requiredType, role, attLoc);
            value = seq.materialize();
            try {
                compilation.declareStaticVariable(varName, value, precedence, isParam);
            } catch (XPathException e) {
                throw createXPathException(e.getMessage(), e.getErrorCodeLocalPart(), attLoc);
            }
        }
    }

    private AttributeMap processShadowAttributes(NodeName elemName, AttributeMap attributes, NamespaceResolver nsResolver, Location location, URI baseUri) throws XPathException {
        Map<NodeName, AttributeInfo> attMap = new HashMap<>();
        for (AttributeInfo att : attributes) {
            NodeName attName = att.getNodeName();
            attMap.put(attName, att);
        }
        for (AttributeInfo att : attributes) {
            NodeName attName = att.getNodeName();
            String local = attName.getLocalPart();
            String uri = attName.getURI();
            if (local.startsWith("_") && (uri.isEmpty() || uri.equals(NamespaceConstant.SAXON)) && local.length() >= 2) {
                String value = att.getValue();
                AttributeLocation attLocation =
                        new AttributeLocation(elemName.getStructuredQName(), attName.getStructuredQName(), location);
                String newValue = processShadowAttribute(value, baseUri.toString(), nsResolver, attLocation);
                String plainName = local.substring(1);
                NodeName newName = uri.isEmpty() ?
                        new NoNamespaceName(plainName) : new FingerprintedQName(attName.getPrefix(), NamespaceConstant.SAXON, plainName);
                // if a corresponding attribute exists with no underscore, overwrite it.
                // Drop the shadow attribute itself.
                AttributeInfo newAtt = new AttributeInfo(newName, att.getType(), newValue, att.getLocation(),ReceiverOption.NONE);
                attMap.put(newName, newAtt);
                attMap.remove(attName);
            }
        }
        AttributeMap resultAtts = EmptyAttributeMap.getInstance();
        for (AttributeInfo att : attMap.values()) {
            resultAtts = resultAtts.put(new AttributeInfo(
                    att.getNodeName(), att.getType(), att.getValue(), att.getLocation(), att.getProperties()));
        }
        return resultAtts;
    }

    private URI processBaseUri(Location location, String xmlBaseAtt) throws XPathException {
        String systemId = location.getSystemId();
        if (systemId == null) {
            systemId = getSystemId();
        }
        URI baseUri;
        if (systemId == null || systemId.equals(systemIdStack.peek())) {
            baseUri = baseUriStack.peek();
        } else {
            try {
                baseUri = new URI(systemId);
            } catch (URISyntaxException e) {
                throw new XPathException("Invalid URI for stylesheet entity: " + systemId);
            }
        }
        if (xmlBaseAtt != null) {
            try {
                baseUri = baseUri.resolve(xmlBaseAtt);
            } catch (IllegalArgumentException iae) {
                throw new XPathException("Invalid URI in xml:base attribute: " + xmlBaseAtt + ". " + iae.getMessage());
            }
        }
        baseUriStack.push(baseUri);
        systemIdStack.push(systemId);
        return baseUri;
    }

    private int processVersionAttribute(String version) throws XPathException {
        if (version != null) {
            ConversionResult cr = BigDecimalValue.makeDecimalValue(version, true);
            if (cr instanceof ValidationFailure) {
                throw new XPathException("Invalid version number: " + version, "XTSE0110");
            }
            BigDecimalValue d = (BigDecimalValue)cr.asAtomic();
            return d.getDecimalValue().multiply(BigDecimal.TEN).intValue();
        } else {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Perform evaluation of the nested expressions within a shadow attribute
     * @param expression the value of the shadow attribute as written
     * @param baseUri the base URI of the containing element
     * @param loc the location of the attribute, for diagnostics
     * @return the result of evaluating nested expressions in the value
     * @throws XPathException if the syntax is invalid, or if evaluation of nested expressions fails
     */

    private String processShadowAttribute(String expression, String baseUri, NamespaceResolver nsResolver, AttributeLocation loc) throws XPathException {
        UseWhenStaticContext staticContext = new UseWhenStaticContext(compilation, nsResolver);
        staticContext.setBaseURI(baseUri);
        staticContext.setContainingLocation(loc);
        setNamespaceBindings(staticContext);
        Expression expr = AttributeValueTemplate.make(expression, staticContext);
        expr = typeCheck(expr, staticContext);
        SlotManager stackFrameMap = allocateSlots(expression, expr);
        XPathContext dynamicContext = makeDynamicContext(staticContext);
        ((XPathContextMajor) dynamicContext).openStackFrame(stackFrameMap);
        return expr.evaluateAsString(dynamicContext).toString();
    }

    private XPathException createXPathException(String message, String errorCode, Location location) {
        XPathException err = new XPathException(message);
        err.setErrorCode(errorCode);
        err.setIsStaticError(true);
        err.setLocator(location.saveLocation());
        getPipelineConfiguration().getErrorReporter().report(new XmlProcessingException(err));
        err.setHasBeenReported(true);
        return err;
    }

    /**
     * End of element
     */

    @Override
    public void endElement() throws XPathException {
        defaultNamespaceStack.pop();
        if (depthOfHole > 0) {
            depthOfHole--;
        } else {
            systemIdStack.pop();
            baseUriStack.pop();
            versionStack.pop();
            nextReceiver.endElement();
        }
    }

    /**
     * Character data
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (depthOfHole == 0) {
            nextReceiver.characters(chars, locationId, properties);
        }
    }

    /**
     * Processing Instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) {
        // these are ignored in a stylesheet
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        // these are ignored in a stylesheet
    }

    /**
     * Evaluate a use-when attribute
     *
     * @param expression the expression to be evaluated
     * @param location identifies the location of the expression in case error need to be reported
     * @param baseUri the base URI of the element containing the expression
     * @return the effective boolean value of the result of evaluating the expression
     * @throws XPathException if evaluation of the expression fails
     */

    private boolean evaluateUseWhen(String expression, AttributeLocation location, String baseUri, NamespaceResolver nsResolver) throws XPathException {
        UseWhenStaticContext staticContext = new UseWhenStaticContext(compilation, nsResolver);
        staticContext.setBaseURI(baseUri);
        staticContext.setContainingLocation(location);
        setNamespaceBindings(staticContext);
        Expression expr = ExpressionTool.make(expression, staticContext,
            0, Token.EOF, null);
        expr.setRetainedStaticContext(staticContext.makeRetainedStaticContext());
        expr = typeCheck(expr, staticContext);
        SlotManager stackFrameMap = allocateSlots(expression, expr);
        XPathContext dynamicContext = makeDynamicContext(staticContext);
        //dynamicContext.getController().getExecutable().setFunctionLibrary((FunctionLibraryList)staticContext.getFunctionLibrary());
        ((XPathContextMajor) dynamicContext).openStackFrame(stackFrameMap);
        return expr.effectiveBooleanValue(dynamicContext);
    }

    private SlotManager allocateSlots(String expression, Expression expr) {
        SlotManager stackFrameMap = getPipelineConfiguration().getConfiguration().makeSlotManager();
        if (expression.indexOf('$') >= 0) {
            ExpressionTool.allocateSlots(expr, stackFrameMap.getNumberOfVariables(), stackFrameMap);
        }
        return stackFrameMap;
    }


    private void setNamespaceBindings(UseWhenStaticContext staticContext) {
        staticContext.setDefaultElementNamespace(NamespaceConstant.NULL);
        for (int i = defaultNamespaceStack.size() - 1; i >= 0; i--) {
            String uri = defaultNamespaceStack.get(i);
            if (uri != null) {
                staticContext.setDefaultElementNamespace(uri);
                break;
            }
        }
    }

    private Expression typeCheck(Expression expr, UseWhenStaticContext staticContext) throws XPathException {
        ItemType contextItemType = Type.ITEM_TYPE;
        ContextItemStaticInfo cit = getConfiguration().makeContextItemStaticInfo(contextItemType, true);
        ExpressionVisitor visitor = ExpressionVisitor.make(staticContext);
        return expr.typeCheck(visitor, cit);
    }

    private XPathContext makeDynamicContext(UseWhenStaticContext staticContext) throws XPathException {
        Controller controller = new Controller(getConfiguration());
        controller.getExecutable().setFunctionLibrary((FunctionLibraryList) staticContext.getFunctionLibrary());
        if (staticContext.getXPathVersion() < 30) {
            controller.setURIResolver(new URIPreventer());
        }
        controller.setCurrentDateTime(currentDateTime);
        // this is to ensure that all use-when expressions in a module use the same date and time
        XPathContext dynamicContext = controller.newXPathContext();
        dynamicContext = dynamicContext.newCleanContext();
        return dynamicContext;
    }


    /**
     * Evaluate a static expression (to initialize a static variable)
     *
     * @param expression the expression to be evaluated
     * @param locationId identifies the location of the expression in case error need to be reported
     * @param staticContext the static context for evaluation of the expression
     * @return the effective boolean value of the result of evaluating the expression
     * @throws XPathException if evaluation of the expression fails
     */

    public Sequence evaluateStatic(String expression, Location locationId, UseWhenStaticContext staticContext) throws XPathException {
        setNamespaceBindings(staticContext);
        Expression expr = ExpressionTool.make(expression, staticContext,
            0, Token.EOF, null);
        expr = typeCheck(expr, staticContext);
        SlotManager stackFrameMap = getPipelineConfiguration().getConfiguration().makeSlotManager();
        ExpressionTool.allocateSlots(expr, stackFrameMap.getNumberOfVariables(), stackFrameMap);
        XPathContext dynamicContext = makeDynamicContext(staticContext);
        ((XPathContextMajor) dynamicContext).openStackFrame(stackFrameMap);
        return expr.iterate(dynamicContext).materialize();
    }



    /**
     * Define a URIResolver that disallows all URIs
     */

    private static class URIPreventer implements URIResolver {
        /**
         * Called by the processor when it encounters
         * an xsl:include, xsl:import, or document() function.
         *
         * @param href An href attribute, which may be relative or absolute.
         * @param base The base URI against which the first argument will be made
         *             absolute if the absolute URI is required.
         * @return A Source object, or null if the href cannot be resolved,
         *         and the processor should try to resolve the URI itself.
         * @throws XPathException if an error occurs when trying to
         *                        resolve the URI.
         */
        /*@NotNull*/
        @Override
        public Source resolve(String href, String base) throws XPathException {
            throw new XPathException("No external documents are available within an [xsl]use-when expression");
        }
    }


}

