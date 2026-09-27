////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
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
import net.sf.saxon.lib.*;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingException;
import net.sf.saxon.trans.packages.UsePack;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.tree.jiter.TopDownStackIterable;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.*;
import net.sf.saxon.type.coercion.CoercionRules;
import net.sf.saxon.value.*;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.function.Supplier;

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
    private final Stack<NamespaceUri> defaultNamespaceStack = new Stack<>();
    private final Stack<Integer> versionStack = new Stack<>();
    private final DateTimeValue currentDateTime = DateTimeValue.getCurrentDateTime(null);
    private final Compilation compilation;
    private final Stack<String> systemIdStack = new Stack<>();
    private final Stack<URI> baseUriStack = new Stack<>();
    private final NestedIntegerValue precedence;
    private int importCount = 0;
    private boolean dropUnderscoredAttributes;
    private final LinkedTreeBuilder treeBuilder;
    private NamespaceMap fixedNamespaces;


    /**
     * Create a UseWhenFilter
     *
     * @param compilation the compilation episode
     * @param next        the next receiver in the pipeline
     * @param precedence  the import precedence expressed as a dotted-decimal integer, e.g. 1.4.6
     */

    public UseWhenFilter(Compilation compilation, Receiver next, NestedIntegerValue precedence) {
        super(next);
        this.compilation = compilation;
        this.precedence = precedence;
        assert (next instanceof LinkedTreeBuilder); // Currently always true; but the design
        // tries to avoid assuming it will always be true
        treeBuilder = (LinkedTreeBuilder) next;
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
        boolean inXsltNamespace = elemName.hasURI(NamespaceUri.XSLT);
        NamespaceUri stdAttUri = (inXsltNamespace && fp != StandardNames.XSL_RECORD)
                                          ? NamespaceUri.NULL : NamespaceUri.XSLT;

        DocumentImpl includedDoc = null;

        ParsedAttributes pa = startElementProcessAttributes(elemName, attributes, namespaces, inXsltNamespace, stdAttUri);

        defaultNamespaceStack.push(pa.xpathDefaultNamespaceAtt);

        if (emptyStylesheetElement) {
            depthOfHole++;
            return;
        }

        if (depthOfHole == 0) {
            URI baseUri = processBaseUri(location, pa.xmlBaseAtt);

            boolean ignore = false;

            int version = getVersion(pa, fp);

            versionStack.push(version);

            if (inXsltNamespace && defaultNamespaceStack.size() == 2
                    && version > 30 && !ElementAvailable.isXslt30Element(fp)) {
                // top level unknown XSLT element is ignored in forwards-compatibility mode
                ignore = true;
            }

            if (pa.hasShadowAttributes && !ignore) {
                attributes = processShadowAttributes(elemName, attributes, namespaces, location, baseUri);
                String uw = attributes.getValue(stdAttUri, "use-when");
                if (uw != null) {
                    pa.useWhenAtt = uw;
                }
            }

            if (!ignore) {
                if (checkUseEvaluateWhen(pa, fp, location, baseUri, namespaces, elemName, stdAttUri)) {
                    return;
                }
            }

            if (inXsltNamespace) {
                includedDoc = handleXsltElement(elemName, baseUri, fp, pa, attributes, namespaces, location);
            }

            dropUnderscoredAttributes = inXsltNamespace;

            nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);

            checkTargetDocument(includedDoc);
        } else {
            depthOfHole++;
        }
    }

    private void checkTargetDocument(DocumentImpl includedDoc) {
        if (includedDoc != null) {
            XSLGeneralIncorporate node = (XSLGeneralIncorporate) treeBuilder.getCurrentParentNode();
            node.setTargetDocument(includedDoc);
        }
    }

    private boolean checkUseEvaluateWhen(ParsedAttributes pa, int fp, Location location, URI baseUri, NamespaceMap namespaces,
                                         NodeName elemName, NamespaceUri stdAttUri) throws XPathException {
        if (pa.useWhenAtt != null) {
            AttributeLocation attLoc = new AttributeLocation(elemName.getStructuredQName(),
                                                             new StructuredQName("", stdAttUri, "use-when"), location);

            if (!evaluateUseWhen(pa.useWhenAtt, attLoc, baseUri.toString(), namespaces)) {
                if (fp == StandardNames.XSL_STYLESHEET || fp == StandardNames.XSL_TRANSFORM || fp == StandardNames.XSL_PACKAGE) {
                    emptyStylesheetElement = true;
                } else {
                    depthOfHole = 1;
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get the effective version, as defined by [xsl]:version attributes in the stylesheet
     * @param pa the attributes of the current element
     * @param fp the fingerprint of the name of the current element
     * @return the effective version
     * @throws XPathException if things go wrong (for example, bad version attribute)
     */
    private int getVersion(ParsedAttributes pa, int fp) throws XPathException {
        int version = Integer.MIN_VALUE;
        if (pa.versionAtt != null && fp != StandardNames.XSL_OUTPUT) {
            version = processVersionAttribute(pa.versionAtt);
        }

        if (version == Integer.MIN_VALUE) {
            version = versionStack.isEmpty() ? 30 : versionStack.peek();
        }

        return version;
    }

    public NamespaceMap getFixedNamespaces() {
        return fixedNamespaces;
    }

    private DocumentImpl processIncludeImport(
            NodeName elemName, Location location, URI baseUri,
            String href, boolean isImport, boolean is40) throws XPathException {
        if (href == null) {
            throw new XPathException("Missing href attribute on " + elemName.getDisplayName(), "XTSE0010");
        }
        //System.err.println((isImport ? "import" : "include") + " at precedence " + precedence);
        NestedIntegerValue newPrecedence = precedence;
        if (isImport) {
            newPrecedence = precedence.getStem().append(precedence.getLeaf() - 1).append(2 * ++importCount);
        }
        Configuration config = getConfiguration();
        ResourceResolver resolver = compilation.getCompilerInfo().getResourceResolver();
        String baseUriStr = baseUri.toString();
        DocumentKey key = DocumentFn.computeStylesheetDocumentKey(href, baseUriStr, compilation.getPackageData());
        Map<DocumentKey, TreeInfo> map = compilation.getStylesheetModules();
        if (map.containsKey(key)) {
            // In 4.0, ignore an included stylesheet module if it has already been included at the
            // same stylesheet level.
            if (is40 && compilation.existsLoadedModule(key, newPrecedence)) {
                return null;
            } else {
                compilation.addLoadedModule(key, newPrecedence);
                return (DocumentImpl) map.get(key);
            }
        } else {
            compilation.addLoadedModule(key, newPrecedence);
            ResourceRequest request = new ResourceRequest();
            request.relativeUri = href;
            request.baseUri = baseUriStr;
            request.uri = key.getAbsoluteURI();
            request.nature = ResourceRequest.XSLT_NATURE;
            request.purpose = ResourceRequest.ANY_PURPOSE;
            Source source = request.resolve(resolver,
                                            config.getResourceResolver(),
                                            new DirectResourceResolver(config));

            if (source == null) {
                throw new XPathException("Unable to resolve " + elemName.getDisplayName()
                                                 + " stylesheet URI " + href, "XTSE0165")
                        .withLocation(location);
            }
            if (source instanceof EmptySource) {
                source = new StreamSource(
                        new StringReader("<xsl:transform version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'/>"));
            }

            try {
                DocumentImpl includedDoc = StylesheetModule.loadStylesheetModule(source, false, compilation, newPrecedence);
                if (includedDoc != null) {
                    map.put(key, includedDoc);
                }
                return includedDoc;
            } catch (XPathException e) {
                e.maybeSetErrorCode("XTSE0165");
                if (e.hasErrorCode("SXXP0003")) {
                    e.setErrorCode("XTSE0165");
                } else if (e.hasErrorCode("XTSE0180")) {
                    if (isImport) {
                        e.setErrorCode("XTSE0210");
                    }
                }
                e.maybeSetLocation(location);
                if (!e.hasBeenReported()) {
                    compilation.reportError(e);
                }
                throw e;
            }
        }
    }

    private static class ParsedAttributes {
        NamespaceUri xpathDefaultNamespaceAtt = null;
        String versionAtt = null;
        String xmlBaseAtt = null;
        String useWhenAtt = null;
        String staticAtt = null;
        boolean hasShadowAttributes = false;
    }

    private ParsedAttributes startElementProcessAttributes(
            NodeName elemName, AttributeMap attributes, NamespaceMap namespaces, boolean inXsltNamespace, NamespaceUri stdAttUri) {
        boolean inSaxonNamespace = elemName.hasURI(NamespaceUri.SAXON);
        boolean isXslRecord = elemName.getFingerprint() == StandardNames.XSL_RECORD;
        ParsedAttributes pa = new ParsedAttributes();

        for (AttributeInfo att : attributes) {
            NodeName attName = att.getNodeName();
            attName.obtainFingerprint(getNamePool());
            String local = attName.getLocalPart();
            boolean underscored = local.startsWith("_") && !isXslRecord;
            if (local.equals("default-mode") && (attName.hasURI(NamespaceUri.XSLT) != inXsltNamespace) && !isXslRecord) {
                registerModeName(att.getValue(), namespaces);
            }
            if (attName.hasURI(stdAttUri)) {
                processAttributeLocal(att, local, pa);
                if (underscored && attName.hasURI(NamespaceUri.NULL)
                        && (inXsltNamespace || inSaxonNamespace)) {
                    pa.hasShadowAttributes = true;
                }
            } else if (inSaxonNamespace || attName.hasURI(NamespaceUri.SAXON)) {
                if (underscored) {
                    pa.hasShadowAttributes = true;
                }
            } else if (attName.hasURI(NamespaceUri.XML)) {
                if (local.equals("base")) {
                    pa.xmlBaseAtt = att.getValue();
                }
            }
        }

        return pa;
    }

    private void processAttributeLocal(AttributeInfo att, String local, ParsedAttributes pa) {
        switch (local) {
            case "xpath-default-namespace":
                pa.xpathDefaultNamespaceAtt = NamespaceUri.of(att.getValue());
                break;
            case "version":
                pa.versionAtt = att.getValue();
                break;
            case "use-when":
                pa.useWhenAtt = att.getValue();
                break;
            case "static":
                pa.staticAtt = att.getValue();
                break;
        }
    }

    /**
     * Handle an element in the XSLT namespace. If it's an xsl:include or xsl:import element, return
     * the referenced document; otherwise return null;
     * @param elemName the element name
     * @param baseUri the base URI
     * @param fp the fingerpring of the element name
     * @param pa values of special attributes applying to any element
     * @param attributes all the attributes on the start tag
     * @param namespaces all the in-scope namespaces
     * @param location location of the start tag for diagnostics
     * @return the document referenced by xsl:include or xsl:import; otherwise null
     * @throws XPathException if things go wrong
     */
    private DocumentImpl handleXsltElement(NodeName elemName, URI baseUri, int fp, ParsedAttributes pa, AttributeMap attributes, NamespaceMap namespaces, Location location) throws XPathException {
        DocumentImpl includedDoc = null;


        if (fp == StandardNames.XSL_APPLY_TEMPLATES) {
            registerModeName(attributes.getValue("mode"), namespaces);
            return null;
        } else if (defaultNamespaceStack.size() == 1) {
            switch (fp) {
                case StandardNames.XSL_STYLESHEET:
                case StandardNames.XSL_TRANSFORM:
                case StandardNames.XSL_PACKAGE:
                    int processorVersion = compilation.getCompilerInfo().getXsltVersion();
                    if (processorVersion >= 40) {
                        String fixedNamespacesAtt = attributes.getValue("fixed-namespaces");
                        if (fixedNamespacesAtt != null) {
                            fixedNamespaces = parseFixedNamespacesAtt(fixedNamespacesAtt, namespaces, baseUri);
                            getPipelineConfiguration().setComponent("FixedNamespaces", fixedNamespaces);
                        }
                    }
                    break;
            }
        } else if (defaultNamespaceStack.size() == 2) {
                switch (fp) {
                case StandardNames.XSL_VARIABLE:
                case StandardNames.XSL_PARAM:
                    if (pa.hasShadowAttributes) {
                        pa.staticAtt = attributes.getValue("static");
                    }
                    if (pa.staticAtt != null) {
                        String staticStr = Whitespace.trim(pa.staticAtt);
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
                    String href = attributes.getValue("href");
                    int processorVersion = compilation.getCompilerInfo().getXsltVersion();
                    boolean is40 = processorVersion >= 40;
                    boolean isImport = fp == StandardNames.XSL_IMPORT;
                    includedDoc = processIncludeImport(elemName, location, baseUri, href, isImport, is40);
                    break;

                case StandardNames.XSL_IMPORT_SCHEMA:
                    compilation.setSchemaAware(true);   // bug 3105
                    break;

                case StandardNames.XSL_USE_PACKAGE:
                    if (precedence.getDepth() > 1) {
                        throw new XPathException("xsl:use-package cannot appear in an imported stylesheet", "XTSE3008");
                    }
                    String name = attributes.getValue("name");
                    String pversion = attributes.getValue("package-version");
                    if (name != null) {
                        try {
                            UsePack use = new UsePack(name, pversion, location.saveLocation());
                            compilation.registerPackageDependency(use);
                        } catch (XPathException err) {
                            // No action, error will be reported later.
                        }

                    }
                    break;

                case StandardNames.XSL_MODE:
                    registerModeName(attributes.getValue("name"), namespaces);
                    break;

                case StandardNames.XSL_TEMPLATE:
                    registerModeNames(attributes.getValue("mode"), namespaces);
                    break;
            }
        }

        return includedDoc;
    }

    private void registerModeName(String modeAtt, NamespaceResolver nsResolver) {
        if (modeAtt != null && !modeAtt.startsWith("#")) {
            try {
                int version = versionStack.isEmpty() ? 30 : versionStack.peek();
                int qNameFormat =  version >= 40 ? StructuredQName.QUPL : StructuredQName.QUL;
                StructuredQName qName = StructuredQName.fromLexicalQName((modeAtt), false, qNameFormat, nsResolver);
                compilation.getAllKnownModeNames().add(qName);
            } catch (XPathException e) {
                // No action, the error (if any) will be reported later
            }
        }
    }

    private void registerModeNames(String modeAtt, NamespaceResolver nsResolver) {
        if (modeAtt != null) {
            String[] tokens = Whitespace.trim(modeAtt).split("[ \t\n\r]+");
            for (String token : tokens) {
                registerModeName(token, nsResolver);
            }
        }
    }


    private void processStaticVariable(NodeName elemName, AttributeMap attributes,
                                       NamespaceResolver nsResolver,
                                       Location location, URI baseUri,
                                       NestedIntegerValue precedence) throws XPathException {
        String nameStr = attributes.getValue(NamespaceUri.NULL, "name");
        String asStr = attributes.getValue(NamespaceUri.NULL, "as");
        String requiredStr = Whitespace.trim(attributes.getValue(NamespaceUri.NULL, "required"));
        boolean isRequired = StyleElement.isYes(requiredStr);

        if (fixedNamespaces != null) {
            nsResolver = fixedNamespaces;
        }

        UseWhenStaticContext staticContext = new UseWhenStaticContext(compilation, nsResolver);
        staticContext.setBaseURI(baseUri.toString());
        staticContext.setContainingLocation(
                new AttributeLocation(elemName.getStructuredQName(), NamespaceUri.NULL.qName("as"), location));
        SequenceType requiredType = SequenceType.ANY_SEQUENCE;

        Configuration config = compilation.getConfiguration();
        int languageLevel = config.getConfigurationProperty(Feature.XPATH_VERSION_FOR_XSLT);
        CoercionRules coercionRules = CoercionRules.forVersion(config, languageLevel);
        if (languageLevel == 30) {
            languageLevel = 305; // XPath 3.0 + XSLT extensions
        }
        staticContext.setXPathLanguageLevel(languageLevel);

        if (asStr != null) {
            XPathParser parser = compilation.getConfiguration().newExpressionParser("XP", false, staticContext);
            requiredType = parser.parseSequenceType(asStr, staticContext);
        }

        StructuredQName varName;
        try {
            int qNameFormat = languageLevel >= 40 ? StructuredQName.QUPL : StructuredQName.QUL;
            varName = StructuredQName.fromLexicalQName((nameStr), false, qNameFormat, nsResolver);
        } catch (XPathException err) {
            throw createXPathException(
                    "Invalid variable name:" + nameStr + ". " + err.getMessage(),
                    err.getErrorCodeQName(), location);
        }

        boolean isVariable = elemName.getLocalPart().equals("variable");
        boolean isParam = elemName.getLocalPart().equals("param");
        boolean isSupplied = isParam && compilation.getParameters().containsKey(varName);
        AttributeLocation attLoc =
                new AttributeLocation(elemName.getStructuredQName(), NamespaceUri.NULL.qName("select"), location);

        if (isParam) {
            if (isRequired && !isSupplied) {
                String selectStr = attributes.getValue(NamespaceUri.NULL, "select");
                if (selectStr != null) {
                    throw createXPathException("Cannot supply a default value when required='yes'",
                                               NamespaceUri.ERR.qName("XTSE0010"), attLoc);
                } else {
                    throw createXPathException(
                            "No value was supplied for the required static parameter $" + varName.getDisplayName(),
                            NamespaceUri.ERR.qName("XTDE0050"), location);
                }
            }

            if (isSupplied) {
                Sequence suppliedValue = compilation.getParameters()
                        .convertParameterValue(varName, requiredType, languageLevel, staticContext.makeEarlyEvaluationContext());

                compilation.declareStaticVariable(varName, suppliedValue.materialize(), precedence, isParam);
            }
        }

        if (isVariable || !isSupplied) {
            String selectStr = attributes.getValue(NamespaceUri.NULL, "select");
            GroundedValue value;
            if (selectStr == null) {
                if (isVariable) {
                    throw createXPathException(
                            "The select attribute is required for a static global variable",
                            NamespaceUri.ERR.qName("XTSE0010"), location);
                } else if (!Cardinality.allowsZero(requiredType.getCardinality())) {
                    throw createXPathException("The parameter is implicitly required because it does not accept an "
                                                       + "empty sequence, but no value has been supplied",
                                               NamespaceUri.ERR.qName("XTDE0700"), location);
                } else {
                    if (asStr == null) {
                        value = StringValue.EMPTY_STRING;
                    } else {
                        value = EmptySequence.INSTANCE;
                    }
                    compilation.declareStaticVariable(varName, value, precedence, isParam);
                }

            } else {
                try {
                    staticContext.setContainingLocation(attLoc);
                    Sequence sequence = evaluateStatic(selectStr, location, staticContext);
                    value = sequence.materialize();
                } catch (XPathException e) {
                    throw createXPathException("Error in " + elemName.getLocalPart() + " expression. " + e.getMessage(),
                                               e.getErrorCodeQName(), attLoc);
                }
            }
            if (requiredType != SequenceType.ANY_SEQUENCE) {
                Supplier<RoleDiagnostic> role =
                        () -> new RoleDiagnostic(RoleDiagnostic.VARIABLE, varName.getDisplayName(), 0, "XTDE0050");
                value = coercionRules.coerce(
                                value, requiredType, SequenceType.ANY_SEQUENCE, getConfiguration(), role, attLoc)
                        .materialize();
            }
            try {
                compilation.declareStaticVariable(varName, value, precedence, isParam);
            } catch (XPathException e) {
                throw createXPathException(e.getMessage(), e.getErrorCodeQName(), attLoc);
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
            NamespaceUri uri = attName.getNamespaceUri();
            if (local.startsWith("_") && (uri.isEmpty() || uri.equals(NamespaceUri.SAXON)) && local.length() >= 2) {
                String value = att.getValue();
                AttributeLocation attLocation =
                        new AttributeLocation(elemName.getStructuredQName(), attName.getStructuredQName(), location);
                String newValue = processShadowAttribute(value, baseUri.toString(), nsResolver, attLocation);
                String plainName = local.substring(1);
                NodeName newName;
                if (uri.isEmpty()) {
                    newName = new NoNamespaceName(plainName);
                } else {
                    newName = new FingerprintedQName(attName.getPrefix(), NamespaceUri.SAXON, plainName);
                }

                // if a corresponding attribute exists with no underscore, overwrite it.
                // Drop the shadow attribute itself.
                AttributeInfo newAtt = new AttributeInfo(newName, att.getType(), newValue, att.getLocation(), ReceiverOption.NONE);
                attMap.put(newName, newAtt);
                attMap.remove(attName);
            }
        }
        AttributeMap resultAtts = EmptyAttributeMap.INSTANCE;
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
            DecimalValue d = (DecimalValue) cr.asAtomic();
            return d.getDecimalValue().multiply(BigDecimal.TEN).intValue();
        } else {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Perform evaluation of the nested expressions within a shadow attribute
     *
     * @param expression the value of the shadow attribute as written
     * @param baseUri    the base URI of the containing element
     * @param loc        the location of the attribute, for diagnostics
     * @return the result of evaluating nested expressions in the value
     * @throws XPathException if the syntax is invalid, or if evaluation of nested expressions fails
     */

    private String processShadowAttribute(String expression, String baseUri, NamespaceResolver nsResolver, AttributeLocation loc) throws XPathException {

        if (fixedNamespaces != null) {
            nsResolver = fixedNamespaces;
        }

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

    private XPathException createXPathException(String message, StructuredQName errorCode, Location location) {
        XPathException err = new XPathException(message);
        err.setErrorCodeQName(errorCode);
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
    public void characters(UnicodeString chars, Location locationId, int properties) throws XPathException {
        if (depthOfHole == 0) {
            nextReceiver.characters(chars, locationId, properties);
        }
    }

    /**
     * Processing Instruction
     */

    @Override
    public void processingInstruction(String target, UnicodeString data, Location locationId, int properties) {
        // these are ignored in a stylesheet
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(UnicodeString chars, Location locationId, int properties) throws XPathException {
        // these are ignored in a stylesheet
    }

    /**
     * Evaluate a use-when attribute
     *
     * @param expression the expression to be evaluated
     * @param location   identifies the location of the expression in case error need to be reported
     * @param baseUri    the base URI of the element containing the expression
     * @return the effective boolean value of the result of evaluating the expression
     * @throws XPathException if evaluation of the expression fails
     */

    private boolean evaluateUseWhen(String expression, AttributeLocation location, String baseUri, NamespaceResolver nsResolver) throws XPathException {

        if (fixedNamespaces != null) {
            nsResolver = fixedNamespaces;
        }

        UseWhenStaticContext staticContext = new UseWhenStaticContext(compilation, nsResolver);
        staticContext.setBaseURI(baseUri);
        staticContext.setContainingLocation(location);
        setNamespaceBindings(staticContext);
        Expression expr = ExpressionTool.make(expression, staticContext,
                                              0, Tokenizer.END_OF_INPUT, null);
        expr.setRetainedStaticContext(staticContext.makeRetainedStaticContext());
        expr = typeCheck(expr, staticContext);
        SlotManager stackFrameMap = allocateSlots(expression, expr);
        XPathContext dynamicContext = makeDynamicContext(staticContext);
        //dynamicContext.getController().getExecutable().setFunctionLibrary((FunctionLibraryList)staticContext.getFunctionLibrary());
        ((XPathContextMajor) dynamicContext).openStackFrame(stackFrameMap);
        return expr.effectiveBooleanValue(dynamicContext);
    }

    private NamespaceMap parseFixedNamespacesAtt(String att, NamespaceMap nsContext, URI baseUri) throws XPathException {
        NamespaceMap nsMap = NamespaceMap.emptyMap();
        StringTokenizer st = new StringTokenizer(att, " \t\n\r", false);

        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            if (s.equals("#standard")) {
                nsMap = nsMap.put("xsl", NamespaceUri.XSLT);
                nsMap = nsMap.put("xs", NamespaceUri.SCHEMA);
                nsMap = nsMap.put("fn", NamespaceUri.FN);
                nsMap = nsMap.put("map", NamespaceUri.MAP_FUNCTIONS);
                nsMap = nsMap.put("array", NamespaceUri.ARRAY_FUNCTIONS);
                nsMap = nsMap.put("math", NamespaceUri.MATH);
                // etc
            } else if (s.equals("xsl")) {
                nsMap = nsMap.put("xsl", NamespaceUri.XSLT);
            } else if (s.equals("xs")) {
                nsMap = nsMap.put("xs", NamespaceUri.SCHEMA);
            } else if (s.equals("fn")) {
                nsMap = nsMap.put("fn", NamespaceUri.FN);
            } else if (s.equals("map")) {
                nsMap = nsMap.put("map", NamespaceUri.MAP_FUNCTIONS);
            } else if (s.equals("array")) {
                nsMap = nsMap.put("array", NamespaceUri.ARRAY_FUNCTIONS);
            } else if (s.equals("math")) {
                nsMap = nsMap.put("math", NamespaceUri.MATH);
            } else if (NameChecker.isValidNCName(s)) {
                NamespaceUri uri = nsContext.getNamespaceUri(s);
                if (uri == null) {
                    throw new XPathException("Namespace prefix " + s + " has not been declared");
                } else {
                    nsMap = nsMap.put(s, uri);
                }
            } else {
                int eq = s.indexOf('=');
                if (eq > 0 && eq + 1 < s.length() && NameChecker.isValidNCName(s.substring(0, eq))) {
                    nsMap = nsMap.put(s.substring(0, eq), NamespaceUri.of(s.substring(eq + 1)));
                } else {
                    NodeInfo doc = null;

                    Configuration config = getConfiguration();
                    ResourceResolver resolver = compilation.getCompilerInfo().getResourceResolver();
                    String baseUriStr = baseUri.toString();
                    DocumentKey key = DocumentFn.computeStylesheetDocumentKey(s, baseUri.toString(), compilation.getPackageData());
                    Map<DocumentKey, TreeInfo> map = compilation.getStylesheetModules();
                    if (map.containsKey(key)) {
                        doc = (DocumentImpl) map.get(key);
                        NodeInfo outer = Navigator.getOutermostElement(doc.getTreeInfo());
                        nsMap = nsMap.addAll(outer.getAllNamespaces());
                    } else {
                        ResourceRequest request = new ResourceRequest();
                        request.relativeUri = s;
                        request.baseUri = baseUriStr;
                        request.uri = key.getAbsoluteURI();
                        request.nature = ResourceRequest.XSLT_NATURE;
                        request.purpose = ResourceRequest.ANY_PURPOSE;
                        Source source = request.resolve(resolver,
                                                        config.getResourceResolver(),
                                                        new DirectResourceResolver(config));

                        if (source == null) {
                            throw new XPathException("Unable to resolve URI " + s
                                                             + " in fixed-namespaces attribute ", "XTSE0165");
                        }

                        TreeInfo tree = config.buildDocumentTree(source);
                        NodeInfo outer = Navigator.getOutermostElement(tree);
                        nsMap = nsMap.addAll(outer.getAllNamespaces());

                    }
                }
            }
        }
        return nsMap;

    }

    private SlotManager allocateSlots(String expression, Expression expr) {
        SlotManager stackFrameMap = getPipelineConfiguration().getConfiguration().makeSlotManager();
        if (expression.indexOf('$') >= 0) {
            ExpressionTool.allocateSlots(expr, stackFrameMap.getNumberOfVariables(), stackFrameMap);
        }
        return stackFrameMap;
    }


    private void setNamespaceBindings(UseWhenStaticContext staticContext) {
        staticContext.setDefaultElementNamespace(NamespaceUri.NULL);
        for (NamespaceUri uri : new TopDownStackIterable<>(defaultNamespaceStack)) {
            if (uri != null) {
                staticContext.setDefaultElementNamespace(uri);
                break;
            }
        }
    }

    private Expression typeCheck(Expression expr, UseWhenStaticContext staticContext) throws XPathException {
        ItemType contextItemType = Type.ITEM_TYPE;
        ContextItemStaticInfo cit = getConfiguration().makeContextItemStaticInfo(contextItemType, Optionality.OPTIONAL);
        ExpressionVisitor visitor = ExpressionVisitor.make(staticContext);
        return expr.typeCheck(visitor, cit);
    }

    private XPathContext makeDynamicContext(UseWhenStaticContext staticContext) throws XPathException {
        Controller controller = new Controller(getConfiguration());
        controller.getExecutable().setFunctionLibrary((FunctionLibraryList) staticContext.getFunctionLibrary());
        if (staticContext.getXPathVersion() < 30) {
            controller.setResourceResolver(new ResourceResolverDelegate(request -> {
                throw new UncheckedXPathException("No external documents are available within an [xsl]use-when expression");
            }));
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
     * @param expression    the expression to be evaluated
     * @param locationId    identifies the location of the expression in case error need to be reported
     * @param staticContext the static context for evaluation of the expression
     * @return the effective boolean value of the result of evaluating the expression
     * @throws XPathException if evaluation of the expression fails
     */

    public Sequence evaluateStatic(String expression, Location locationId, UseWhenStaticContext staticContext) throws XPathException {
        try {
            setNamespaceBindings(staticContext);
            Expression expr = ExpressionTool.make(expression, staticContext,
                                                  0, Tokenizer.END_OF_INPUT, null);
            expr = typeCheck(expr, staticContext);
            SlotManager stackFrameMap = getPipelineConfiguration().getConfiguration().makeSlotManager();
            ExpressionTool.allocateSlots(expr, stackFrameMap.getNumberOfVariables(), stackFrameMap);
            XPathContext dynamicContext = makeDynamicContext(staticContext);
            ((XPathContextMajor) dynamicContext).openStackFrame(stackFrameMap);
            return SequenceTool.toGroundedValue(expr.iterate(dynamicContext));
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }


}

