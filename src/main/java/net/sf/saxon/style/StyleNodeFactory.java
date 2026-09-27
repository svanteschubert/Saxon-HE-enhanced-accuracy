////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.accum.AccumulatorRegistry;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingIncident;
import net.sf.saxon.tree.linked.ElementImpl;
import net.sf.saxon.tree.linked.NodeFactory;
import net.sf.saxon.tree.linked.NodeImpl;
import net.sf.saxon.tree.linked.TextImpl;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Class StyleNodeFactory. <br>
 * A Factory for nodes in the stylesheet tree. <br>
 * Currently only allows Element nodes to be user-constructed.
 */

public class StyleNodeFactory implements NodeFactory {


    protected Configuration config;
    protected NamePool namePool;
    private final Compilation compilation;
    private boolean topLevelModule;

    /**
     * Create the node factory for representing an XSLT stylesheet as a tree structure
     *
     * @param config the Saxon configuration
     * @param compilation the compilation episode (compiling one package)
     */

    public StyleNodeFactory(Configuration config, Compilation compilation) {
        this.config = config;
        this.compilation = compilation;
        namePool = config.getNamePool();
    }

    /**
     * Say that this is the top-level module of a package
     * @param topLevelModule true if this stylesheet module is the top level of a package; false
     * if it is included or imported
     */

    public void setTopLevelModule(boolean topLevelModule) {
        this.topLevelModule = topLevelModule;
    }

    /**
     * Ask whether this is the top-level module of a package
     * @return true if this stylesheet module is the top level of a package; false
     * if it is included or imported
     */

    public boolean isTopLevelModule() {
        return topLevelModule;
    }

    public Compilation getCompilation() {
        return compilation;
    }


    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Create an Element node. Note, if there is an error detected while constructing
     * the Element, we add the element anyway, and return success, but flag the element
     * with a validation error. This allows us to report more than
     * one error from a single compilation.
     */

    @Override
    public ElementImpl makeElementNode(
            NodeInfo parent,
            NodeName elemName,
            SchemaType elemType,
            boolean isNilled,
            AttributeMap attlist,
            NamespaceMap namespaces,
            PipelineConfiguration pipe,
            Location location,
            int sequence) {
        int f = elemName.obtainFingerprint(pipe.getConfiguration().getNamePool());
        boolean toplevel = parent instanceof XSLModuleRoot;
        String baseURI = location.getSystemId();
        int lineNumber = location.getLineNumber();
        int columnNumber = location.getColumnNumber();
        int processorVersion = compilation.getCompilerInfo().getXsltVersion();

        if (parent instanceof DataElement) {
            DataElement d = new DataElement();
            d.setNamespaceMap(namespaces);
            d.initialise(elemName, elemType, attlist, parent, sequence);
            d.setLocation(baseURI, lineNumber, columnNumber);
            return d;
        }

        // Try first to make an XSLT element

        StyleElement e = makeXSLElement(f, (NodeImpl)parent);
        if ((e instanceof XSLStylesheet || e instanceof XSLPackage) && parent.getNodeKind() != Type.DOCUMENT) {
            e = new AbsentExtensionElement();
            final XmlProcessingIncident reason =
                    new XmlProcessingIncident(elemName.getDisplayName() + " can only appear at the outermost level", "XTSE0010");
            e.setValidationError(reason, StyleElement.OnFailure.REPORT_ALWAYS);
        }
        if (e instanceof XSLPackage) {
            ((XSLPackage)e).setFixedNamespaces((NamespaceMap)pipe.getComponent("FixedNamespaces"));
        }

        if (e != null) {  // recognized as an XSLT element

            NamespaceUri specialNs = f == StandardNames.XSL_RECORD
                    ? NamespaceUri.XSLT : NamespaceUri.NULL;
            e.setCompilation(compilation);
            e.setNamespaceMap(namespaces);
            e.initialise(elemName, elemType, attlist, parent, sequence);
            e.setLocation(baseURI, lineNumber, columnNumber);
            e.processExtensionElementAttribute(specialNs);
            e.processExcludedNamespaces(specialNs);
            e.processVersionAttribute(specialNs);
            e.processDefaultXPathNamespaceAttribute(specialNs);
            e.processExpandTextAttribute(specialNs);
            e.processDefaultValidationAttribute(specialNs);

            if (toplevel && !e.isDeclaration() && !(e instanceof XSLExpose) && e.forwardsCompatibleModeIsEnabled()) {
                DataElement d = new DataElement();
                d.setNamespaceMap(namespaces);
                d.initialise(elemName, elemType, attlist, parent, sequence);
                d.setLocation(baseURI, lineNumber, columnNumber);
                return d;
            }

            if (parent instanceof AbsentExtensionElement &&
                    ((AbsentExtensionElement)parent).forwardsCompatibleModeIsEnabled() &&
                    ((AbsentExtensionElement)parent).isInXsltNamespace() &&
                    !(e instanceof XSLFallback)) {
                // Parent is an unknown XSLT element in forwards-compatibility mode; siblings of xsl:fallback are ignored
                AbsentExtensionElement temp = new AbsentExtensionElement();
                temp.initialise(elemName, elemType, attlist, parent, sequence);
                temp.setLocation(baseURI, lineNumber, columnNumber);
                temp.setCompilation(compilation);
                temp.setIgnoreInstruction();
                return temp;
            }
            return e;

        }

        NamespaceUri uri = elemName.getNamespaceUri();

        if (toplevel && !uri.equals(NamespaceUri.XSLT)) {
            DataElement d = new DataElement();
            d.setNamespaceMap(namespaces);
            d.initialise(elemName, elemType, attlist, parent, sequence);
            d.setLocation(baseURI, lineNumber, columnNumber);
            return d;

        } else {   // not recognized as an XSLT element, not top-level

            String localname = elemName.getLocalPart();
            StyleElement temp = null;

            // Detect a mis-spelt XSLT element, or a 3.0 element used in a 2.0 stylesheet

            if (uri.equals(NamespaceUri.XSLT)) {
                if (parent instanceof XSLStylesheet) {
                    if (((XSLStylesheet) parent).getEffectiveVersion() <= processorVersion) {
                        temp = new AbsentExtensionElement();
                        temp.setCompilation(compilation);
                        temp.setValidationError(new XmlProcessingIncident(
                                "Unknown top-level XSLT declaration " + elemName.getDisplayName(), "XTSE0010", location.saveLocation()),
                                StyleElement.OnFailure.REPORT_UNLESS_FORWARDS_COMPATIBLE);
                    }
                } else {
                    temp = new AbsentExtensionElement();
                    temp.initialise(elemName, elemType, attlist, parent, sequence);
                    temp.setLocation(baseURI, lineNumber, columnNumber);
                    temp.setCompilation(compilation);
                    temp.processStandardAttributes(NamespaceUri.NULL);
                    temp.setValidationError(
                            new XmlProcessingIncident("Unknown XSLT instruction " + elemName.getDisplayName(), "XTSE0010", location.saveLocation()),
                            temp.getEffectiveVersion() > processorVersion
                                    ? StyleElement.OnFailure.REPORT_STATICALLY_UNLESS_FALLBACK_AVAILABLE
                                    : StyleElement.OnFailure.REPORT_ALWAYS);
                }
            }

            // Detect an unrecognized element in the Saxon namespace

            if (uri.equals(NamespaceUri.SAXON)) {
                // The clever way to do this in Java is with a switch statement.
                // But C# switch statements don't allow fall-through, so that's less convenient.
                // Trying to be clever about constructing the list also ran afoul of
                // transpiler problems. FIXME: revisit this.
                List<String> validNames = new ArrayList<>();
                validNames.add("assign");
                validNames.add("deep-update");
                validNames.add("do");
                validNames.add("doctype");
                validNames.add("entity-ref");
                validNames.add("import-query");
                validNames.add("while");
                if (validNames.contains(elemName.getLocalPart())) {
                    String message = elemName.getDisplayName() + " is not recognized as a Saxon instruction";
                    if (config.getEditionCode().equals("HE")) {
                        message += ". Saxon extensions require Saxon-PE or higher";
                    } else if (!config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
                        message += ". No Saxon-PE or -EE license was found";
                    }
                    XmlProcessingIncident err = new XmlProcessingIncident(message, SaxonErrorCode.SXWN9008, location.saveLocation()).asWarning();
                    pipe.getErrorReporter().report(err);
                }
            }

            // We can't work out the final class of the node until we've examined its attributes
            // such as extension-element-prefixes.

            boolean extensionElement = isExtensionNamespace(uri, parent, namespaces, attlist);
            if (temp == null) {
                if (extensionElement) {
                    temp = new AbsentExtensionElement();
                } else {
                    temp = new LiteralResultElement();
                }
            }

            temp.setNamespaceMap(namespaces);
            temp.setCompilation(compilation);
            temp.initialise(elemName, elemType, attlist, parent, sequence);
            temp.setLocation(baseURI, lineNumber, columnNumber);
            temp.processStandardAttributes(NamespaceUri.XSLT);

            XmlProcessingIncident reason;

            if (uri.equals(NamespaceUri.XSLT)) {
                //reason = new XmlProcessingIncident("Unknown XSLT element: " + Err.wrap(localname, Err.ELEMENT), "XTSE0010");
                //temp.setValidationError(reason, StyleElement.OnFailure.REPORT_STATICALLY_UNLESS_FALLBACK_AVAILABLE);

            } else if (extensionElement) {

                // if we can't instantiate an extension element, we don't give up
                // immediately, because there might be an xsl:fallback defined. We
                // create a surrogate element called AbsentExtensionElement, and
                // save the reason for failure just in case there is no xsl:fallback

                if (NamespaceUri.isReserved(uri)) {
                    reason = new XmlProcessingIncident(
                            "Cannot use a reserved namespace for extension instructions",
                            "XTSE0800", location.saveLocation());
                    temp.setValidationError(reason, StyleElement.OnFailure.REPORT_ALWAYS);
                } else {
                    reason = new XmlProcessingIncident(
                            "Unknown extension instruction " + Err.wrap(elemName.getDisplayName(), Err.ELEMENT),
                            "XTDE1450", location.saveLocation());
                    temp.setValidationError(reason, StyleElement.OnFailure.REPORT_DYNAMICALLY_UNLESS_FALLBACK_AVAILABLE);
                }

            }

            return temp;
        }
    }

    private static boolean isExtensionNamespace(NamespaceUri uri, NodeInfo parent, NamespaceMap namespaces, AttributeMap attlist) {
        String attValue = attlist.getValue(NamespaceUri.XSLT, "extension-element-prefixes");
        if (attValue != null) {
            StringTokenizer st2 = new StringTokenizer(attValue, " \t\n\r", false);
            while (st2.hasMoreTokens()) {
                String s = st2.nextToken();
                if ("#default".equals(s)) {
                    s = "";
                }
                NamespaceUri ns = namespaces.getURIForPrefix(s, false);
                if (uri.equals(ns)) {
                    return true;
                }
            }
        }
        return parent instanceof StyleElement && ((StyleElement)parent).isExtensionNamespace(uri);
    }
    
    /**
     * Make an XSL element node
     *
     * @param f      the fingerprint of the node name
     * @param parent the parent node
     * @return the constructed element node
     */

    /*@Nullable*/
    protected StyleElement makeXSLElement(int f, NodeImpl parent) {
        if (f == StandardNames.XSL_RESULT_DOCUMENT) {
            compilation.setCreatesSecondaryResultDocuments(true);
        }
        return switch (f) {
            case StandardNames.XSL_ACCEPT -> new XSLAccept();
            case StandardNames.XSL_ACCUMULATOR -> new XSLAccumulator();
            case StandardNames.XSL_ACCUMULATOR_RULE -> new XSLAccumulatorRule();
            case StandardNames.XSL_ANALYZE_STRING -> new XSLAnalyzeString();
            case StandardNames.XSL_APPLY_IMPORTS -> new XSLApplyImports();
            case StandardNames.XSL_APPLY_TEMPLATES -> new XSLApplyTemplates();
            case StandardNames.XSL_ASSERT -> new XSLAssert();
            case StandardNames.XSL_ATTRIBUTE -> new XSLAttribute();
            case StandardNames.XSL_ATTRIBUTE_SET -> new XSLAttributeSet();
            case StandardNames.XSL_BREAK -> new XSLBreak();
            case StandardNames.XSL_CALL_TEMPLATE -> new XSLCallTemplate();
            case StandardNames.XSL_CATCH -> new XSLCatch();
            case StandardNames.XSL_CONTEXT_ITEM -> new XSLContextItem();
            case StandardNames.XSL_CHARACTER_MAP -> new XSLCharacterMap();
            case StandardNames.XSL_CHOOSE -> new XSLChoose();
            case StandardNames.XSL_COMMENT -> new XSLComment();
            case StandardNames.XSL_COPY -> new XSLCopy();
            case StandardNames.XSL_COPY_OF -> new XSLCopyOf();
            case StandardNames.XSL_DECIMAL_FORMAT -> new XSLDecimalFormat();
            case StandardNames.XSL_DOCUMENT -> new XSLDocument();
            case StandardNames.XSL_ELEMENT -> new XSLElement();
            case StandardNames.XSL_EVALUATE -> new XSLEvaluate();
            case StandardNames.XSL_EXPOSE -> new XSLExpose();
            case StandardNames.XSL_FALLBACK -> new XSLFallback();
            case StandardNames.XSL_FOR_EACH -> new XSLForEach();
            case StandardNames.XSL_FOR_EACH_GROUP -> new XSLForEachGroup();
            case StandardNames.XSL_FORK -> new XSLFork();
            case StandardNames.XSL_FUNCTION -> new XSLFunction();
            case StandardNames.XSL_GLOBAL_CONTEXT_ITEM -> new XSLGlobalContextItem();
            case StandardNames.XSL_IF -> new XSLIf();
            case StandardNames.XSL_IMPORT -> new XSLImport();
            case StandardNames.XSL_IMPORT_SCHEMA -> new XSLImportSchema();
            case StandardNames.XSL_INCLUDE -> new XSLInclude();
            case StandardNames.XSL_ITEM_TYPE -> new XSLItemType();
            case StandardNames.XSL_ITERATE -> new XSLIterate();
            case StandardNames.XSL_KEY -> new XSLKey();
            case StandardNames.XSL_MAP -> new XSLMap();
            case StandardNames.XSL_MAP_ENTRY -> new XSLMapEntry();
            case StandardNames.XSL_MATCHING_SUBSTRING -> new XSLMatchingSubstring();
            case StandardNames.XSL_MERGE -> new XSLMerge();
            case StandardNames.XSL_MERGE_ACTION -> new XSLMergeAction();
            case StandardNames.XSL_MERGE_KEY -> new XSLMergeKey();
            case StandardNames.XSL_MERGE_SOURCE -> new XSLMergeSource();
            case StandardNames.XSL_MESSAGE -> new XSLMessage();
            case StandardNames.XSL_MODE -> new XSLMode();
            case StandardNames.XSL_NEXT_ITERATION -> new XSLNextIteration();
            case StandardNames.XSL_NEXT_MATCH -> new XSLNextMatch();
            case StandardNames.XSL_NON_MATCHING_SUBSTRING -> new XSLMatchingSubstring();    //sic
            case StandardNames.XSL_NUMBER -> new XSLNumber();
            case StandardNames.XSL_NAMESPACE -> new XSLNamespace();
            case StandardNames.XSL_NAMESPACE_ALIAS -> new XSLNamespaceAlias();
            case StandardNames.XSL_ON_COMPLETION -> new XSLOnCompletion();
            case StandardNames.XSL_ON_EMPTY -> new XSLOnEmpty();
            case StandardNames.XSL_ON_NON_EMPTY -> new XSLOnNonEmpty();
            case StandardNames.XSL_OTHERWISE -> new XSLOtherwise();
            case StandardNames.XSL_OUTPUT -> new XSLOutput();
            case StandardNames.XSL_OUTPUT_CHARACTER -> new XSLOutputCharacter();
            case StandardNames.XSL_OVERRIDE -> new XSLOverride();
            case StandardNames.XSL_PACKAGE -> new XSLPackage();
            case StandardNames.XSL_PARAM ->
                //noinspection RedundantCast
                    parent instanceof XSLModuleRoot || parent instanceof XSLOverride ? (StyleElement) new XSLGlobalParam() : (StyleElement) new XSLLocalParam();
            case StandardNames.XSL_PERFORM_SORT -> new XSLPerformSort();
            case StandardNames.XSL_PRESERVE_SPACE -> new XSLPreserveSpace();
            case StandardNames.XSL_PROCESSING_INSTRUCTION -> new XSLProcessingInstruction();
            case StandardNames.XSL_RESULT_DOCUMENT -> new XSLResultDocument();
            case StandardNames.XSL_SEQUENCE -> new XSLSequence();
            case StandardNames.XSL_SORT -> new XSLSort();
            case StandardNames.XSL_SOURCE_DOCUMENT -> new XSLSourceDocument();
            case StandardNames.XSL_STRIP_SPACE -> new XSLPreserveSpace();
            case StandardNames.XSL_STYLESHEET, StandardNames.XSL_TRANSFORM ->
                //noinspection RedundantCast
                    topLevelModule ? (StyleElement) new XSLPackage() : (StyleElement) new XSLStylesheet();
            case StandardNames.XSL_TEMPLATE -> new XSLTemplate();
            case StandardNames.XSL_TEXT -> new XSLText();
            case StandardNames.XSL_TRY -> new XSLTry();
            case StandardNames.XSL_USE_PACKAGE -> new XSLUsePackage();
            case StandardNames.XSL_VALUE_OF -> new XSLValueOf();
            case StandardNames.XSL_VARIABLE ->
                //noinspection RedundantCast
                    parent instanceof XSLModuleRoot || parent instanceof XSLOverride
                            ? (StyleElement) new XSLGlobalVariable() : (StyleElement) new XSLLocalVariable();
            case StandardNames.XSL_WITH_PARAM -> new XSLWithParam();
            case StandardNames.XSL_WHEN -> new XSLWhen();
            case StandardNames.XSL_WHERE_POPULATED -> new XSLWherePopulated();
            default -> null;
        };
    }

    /**
     * Make a text node
     *
     * @param parent  the parent element
     * @param content the content of the text node
     * @return the constructed text node
     */
    @Override
    public TextImpl makeTextNode(NodeInfo parent, UnicodeString content) {
        if (parent instanceof StyleElement && ((StyleElement) parent).isExpandingText()) {
            return new TextValueTemplateNode(content);
        } else {
            return new TextImpl(content);
        }
    }

    /**
     * Method to support the element-available() function
     *
     *
     * @param uri       the namespace URI
     * @param localName the local Name
     * @param instructionsOnly true if only instruction elements qualify
     * @return true if an extension element of this name is recognized
     */

    public boolean isElementAvailable(NamespaceUri uri, String localName, boolean instructionsOnly) {
        int fingerprint = namePool.getFingerprint(uri, localName);
        if (uri.equals(NamespaceUri.XSLT)) {
            if (fingerprint == -1) {
                return false;     // all names are pre-registered
            }
            StyleElement e = makeXSLElement(fingerprint, null);
            if (e != null) {
                return !instructionsOnly || e.isInstruction();
            }
        }
        return false;
    }

    public AccumulatorRegistry makeAccumulatorManager() {
        return new AccumulatorRegistry();
    }

    /**
     * Create a stylesheet package
     * @param node the XSLPackage element
     * @return a new stylesheet package
     * @throws XPathException if things go wrong
     */

    public PrincipalStylesheetModule newPrincipalModule(XSLPackage node) throws XPathException {
        return new PrincipalStylesheetModule(node);
    }

}

