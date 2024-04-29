////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
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

        if (e != null) {  // recognized as an XSLT element

            e.setCompilation(compilation);
            e.setNamespaceMap(namespaces);
            e.initialise(elemName, elemType, attlist, parent, sequence);
            e.setLocation(baseURI, lineNumber, columnNumber);
            e.processExtensionElementAttribute(NamespaceUri.NULL);
            e.processExcludedNamespaces(NamespaceUri.NULL);
            e.processVersionAttribute(NamespaceUri.NULL);
            e.processDefaultXPathNamespaceAttribute(NamespaceUri.NULL);
            e.processExpandTextAttribute(NamespaceUri.NULL);
            e.processDefaultValidationAttribute(NamespaceUri.NULL);

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
                                "Unknown top-level XSLT declaration " + elemName.getDisplayName(), "XTSE0010", location),
                                StyleElement.OnFailure.REPORT_UNLESS_FORWARDS_COMPATIBLE);
                    }
                } else {
                    temp = new AbsentExtensionElement();
                    temp.initialise(elemName, elemType, attlist, parent, sequence);
                    temp.setLocation(baseURI, lineNumber, columnNumber);
                    temp.setCompilation(compilation);
                    temp.processStandardAttributes(NamespaceUri.NULL);
                    temp.setValidationError(
                            new XmlProcessingIncident("Unknown XSLT instruction " + elemName.getDisplayName(), "XTSE0010", location),
                            temp.getEffectiveVersion() > processorVersion
                                    ? StyleElement.OnFailure.REPORT_STATICALLY_UNLESS_FALLBACK_AVAILABLE
                                    : StyleElement.OnFailure.REPORT_ALWAYS);
                }
            }

            // Detect an unrecognized element in the Saxon namespace

            if (uri.equals(NamespaceUri.SAXON)) {
                String message = elemName.getDisplayName() + " is not recognized as a Saxon instruction";
                if (config.getEditionCode().equals("HE")) {
                    message += ". Saxon extensions require Saxon-PE or higher";
                } else if (!config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
                    message += ". No Saxon-PE or -EE license was found";
                }
                XmlProcessingIncident err = new XmlProcessingIncident(message, SaxonErrorCode.SXWN9008, location.saveLocation()).asWarning();
                pipe.getErrorReporter().report(err);
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
                    reason = new XmlProcessingIncident("Cannot use a reserved namespace for extension instructions", "XTSE0800");
                    temp.setValidationError(reason, StyleElement.OnFailure.REPORT_ALWAYS);
                } else {
                    reason = new XmlProcessingIncident("Unknown extension instruction " + Err.wrap(elemName.getDisplayName(), Err.ELEMENT), "XTDE1450");
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
        switch (f) {
            case StandardNames.XSL_ACCEPT:
                return new XSLAccept();
            case StandardNames.XSL_ACCUMULATOR:
                return new XSLAccumulator();
            case StandardNames.XSL_ACCUMULATOR_RULE:
                return new XSLAccumulatorRule();
            case StandardNames.XSL_ANALYZE_STRING:
                return new XSLAnalyzeString();
            case StandardNames.XSL_APPLY_IMPORTS:
                return new XSLApplyImports();
            case StandardNames.XSL_APPLY_TEMPLATES:
                return new XSLApplyTemplates();
            case StandardNames.XSL_ASSERT:
                return new XSLAssert();
            case StandardNames.XSL_ATTRIBUTE:
                return new XSLAttribute();
            case StandardNames.XSL_ATTRIBUTE_SET:
                return new XSLAttributeSet();
            case StandardNames.XSL_BREAK:
                return new XSLBreak();
            case StandardNames.XSL_CALL_TEMPLATE:
                return new XSLCallTemplate();
            case StandardNames.XSL_CATCH:
                return new XSLCatch();
            case StandardNames.XSL_CONTEXT_ITEM:
                return new XSLContextItem();
            case StandardNames.XSL_CHARACTER_MAP:
                return new XSLCharacterMap();
            case StandardNames.XSL_CHOOSE:
                return new XSLChoose();
            case StandardNames.XSL_COMMENT:
                return new XSLComment();
            case StandardNames.XSL_COPY:
                return new XSLCopy();
            case StandardNames.XSL_COPY_OF:
                return new XSLCopyOf();
            case StandardNames.XSL_DECIMAL_FORMAT:
                return new XSLDecimalFormat();
            case StandardNames.XSL_DOCUMENT:
                return new XSLDocument();
            case StandardNames.XSL_ELEMENT:
                return new XSLElement();
            case StandardNames.XSL_EVALUATE:
                return new XSLEvaluate();
            case StandardNames.XSL_EXPOSE:
                return new XSLExpose();
            case StandardNames.XSL_FALLBACK:
                return new XSLFallback();
            case StandardNames.XSL_FOR_EACH:
                return new XSLForEach();
            case StandardNames.XSL_FOR_EACH_GROUP:
                return new XSLForEachGroup();
            case StandardNames.XSL_FORK:
                return new XSLFork();
            case StandardNames.XSL_FUNCTION:
                return new XSLFunction();
            case StandardNames.XSL_GLOBAL_CONTEXT_ITEM:
                return new XSLGlobalContextItem();
            case StandardNames.XSL_IF:
                return new XSLIf();
            case StandardNames.XSL_IMPORT:
                return new XSLImport();
            case StandardNames.XSL_IMPORT_SCHEMA:
                return new XSLImportSchema();
            case StandardNames.XSL_INCLUDE:
                return new XSLInclude();
            case StandardNames.XSL_ITEM_TYPE:
                return new XSLItemType();
            case StandardNames.XSL_ITERATE:
                return new XSLIterate();
            case StandardNames.XSL_KEY:
                return new XSLKey();
            case StandardNames.XSL_MAP:
                return new XSLMap();
            case StandardNames.XSL_MAP_ENTRY:
                return new XSLMapEntry();
            case StandardNames.XSL_MATCHING_SUBSTRING:
                return new XSLMatchingSubstring();
            case StandardNames.XSL_MERGE:
                return new XSLMerge();
            case StandardNames.XSL_MERGE_ACTION:
                return new XSLMergeAction();
            case StandardNames.XSL_MERGE_KEY:
                return new XSLMergeKey();
            case StandardNames.XSL_MERGE_SOURCE:
                return new XSLMergeSource();
            case StandardNames.XSL_MESSAGE:
                return new XSLMessage();
            case StandardNames.XSL_MODE:
                return new XSLMode();
            case StandardNames.XSL_NEXT_ITERATION:
                return new XSLNextIteration();
            case StandardNames.XSL_NEXT_MATCH:
                return new XSLNextMatch();
            case StandardNames.XSL_NON_MATCHING_SUBSTRING:
                return new XSLMatchingSubstring();    //sic
            case StandardNames.XSL_NUMBER:
                return new XSLNumber();
            case StandardNames.XSL_NAMESPACE:
                return new XSLNamespace();
            case StandardNames.XSL_NAMESPACE_ALIAS:
                return new XSLNamespaceAlias();
            case StandardNames.XSL_ON_COMPLETION:
                return new XSLOnCompletion();
            case StandardNames.XSL_ON_EMPTY:
                return new XSLOnEmpty();
            case StandardNames.XSL_ON_NON_EMPTY:
                return new XSLOnNonEmpty();
            case StandardNames.XSL_OTHERWISE:
                return new XSLOtherwise();
            case StandardNames.XSL_OUTPUT:
                return new XSLOutput();
            case StandardNames.XSL_OUTPUT_CHARACTER:
                return new XSLOutputCharacter();
            case StandardNames.XSL_OVERRIDE:
                return new XSLOverride();
            case StandardNames.XSL_PACKAGE:
                return new XSLPackage();
            case StandardNames.XSL_PARAM:
                //noinspection RedundantCast
                return parent instanceof XSLModuleRoot || parent instanceof XSLOverride ? (StyleElement)new XSLGlobalParam() : (StyleElement)new XSLLocalParam();
            case StandardNames.XSL_PERFORM_SORT:
                return new XSLPerformSort();
            case StandardNames.XSL_PRESERVE_SPACE:
                return new XSLPreserveSpace();
            case StandardNames.XSL_PROCESSING_INSTRUCTION:
                return new XSLProcessingInstruction();
            case StandardNames.XSL_RESULT_DOCUMENT:
                compilation.setCreatesSecondaryResultDocuments(true);
                return new XSLResultDocument();
            case StandardNames.XSL_SEQUENCE:
                return new XSLSequence();
            case StandardNames.XSL_SORT:
                return new XSLSort();
            case StandardNames.XSL_SOURCE_DOCUMENT:
                return new XSLSourceDocument();
            case StandardNames.XSL_STRIP_SPACE:
                return new XSLPreserveSpace();
            case StandardNames.XSL_STYLESHEET:
            case StandardNames.XSL_TRANSFORM:
                //noinspection RedundantCast
                return topLevelModule ? (StyleElement)new XSLPackage() : (StyleElement)new XSLStylesheet();
            case StandardNames.XSL_TEMPLATE:
                return new XSLTemplate();
            case StandardNames.XSL_TEXT:
                return new XSLText();
            case StandardNames.XSL_TRY:
                return new XSLTry();
            case StandardNames.XSL_USE_PACKAGE:
                return new XSLUsePackage();
            case StandardNames.XSL_VALUE_OF:
                return new XSLValueOf();
            case StandardNames.XSL_VARIABLE:
                //noinspection RedundantCast
                return parent instanceof XSLModuleRoot || parent instanceof XSLOverride
                        ? (StyleElement)new XSLGlobalVariable() : (StyleElement)new XSLLocalVariable();
            case StandardNames.XSL_WITH_PARAM:
                return new XSLWithParam();
            case StandardNames.XSL_WHEN:
                return new XSLWhen();
            case StandardNames.XSL_WHERE_POPULATED:
                return new XSLWherePopulated();
            default:
                return null;
        }
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

