////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.SortKeyDefinition;
import net.sf.saxon.expr.sort.SortKeyDefinitionList;
import net.sf.saxon.functions.Current;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.trans.*;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.linked.ElementImpl;
import net.sf.saxon.tree.linked.NodeImpl;
import net.sf.saxon.tree.linked.TextImpl;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.BigDecimalValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.SourceLocator;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;


/**
 * Abstract superclass for all element nodes in the stylesheet.
 * <p>Note: this class implements Locator. The element retains information about its own location
 * in the stylesheet, which is useful when an XSLT static error is found.</p>
 */

public abstract class StyleElement extends ElementImpl {

    /*@Nullable*/ protected String[] extensionNamespaces = null;        // a list of URIs
    private String[] excludedNamespaces = null;           // a list of URIs
    protected int version = -1;                 // the effective version of this element
    protected ExpressionContext staticContext = null;
    protected XmlProcessingIncident validationError = null;
    protected OnFailure reportingCircumstances = OnFailure.REPORT_ALWAYS;
    protected String defaultXPathNamespace = null;
    protected String defaultCollationName = null;
    protected StructuredQName defaultMode;
    protected boolean expandText = false;
    private StructuredQName objectName;  // for instructions that define an XSLT named object, the name of that object
    private String baseURI;
    private Compilation compilation;
    private Loc savedLocation = null;
    private int defaultValidation = Validation.DEFAULT;

    // Conditions under which an error is to be reported

    public enum OnFailure {
        REPORT_ALWAYS, REPORT_UNLESS_FORWARDS_COMPATIBLE, REPORT_IF_INSTANTIATED,
        REPORT_STATICALLY_UNLESS_FALLBACK_AVAILABLE, REPORT_DYNAMICALLY_UNLESS_FALLBACK_AVAILABLE,
        IGNORED_INSTRUCTION
    }

    protected int actionsCompleted = 0;
    public static final int ACTION_VALIDATE = 1;
    public static final int ACTION_COMPILE = 2;
    public static final int ACTION_TYPECHECK = 4;
    public static final int ACTION_OPTIMIZE = 8;
    public static final int ACTION_FIXUP = 16;
    public static final int ACTION_PROCESS_ATTRIBUTES = 32;


    /**
     * Constructor
     */

    public StyleElement() {
    }

    public Compilation getCompilation() {
        return compilation;
    }

    public void setCompilation(Compilation compilation) {
        this.compilation = compilation;
    }

    public StylesheetPackage getPackageData() {
        return getPrincipalStylesheetModule().getStylesheetPackage();
    }

    @Override
    public Configuration getConfiguration() {
        return compilation.getConfiguration();
    }

    /**
     * Get the static context for expressions on this element
     *
     * @return the static context
     */

    public ExpressionContext getStaticContext() {
        if (staticContext == null) {
            staticContext = new ExpressionContext(this, null);
        }
        return staticContext;
    }

    public ExpressionContext getStaticContext(StructuredQName attributeName) {
        return new ExpressionContext(this, attributeName);
    }

    /**
     * Get the base URI of the element, which acts as the static base URI for XPath expressions defined
     * on this element. This is an expensive operation so the result is cached
     *
     * @return the base URI
     */

    @Override
    public String getBaseURI() {
        if (baseURI == null) {
            baseURI = super.getBaseURI();
        }
        return baseURI;
    }

    /**
     * Make an expression visitor
     *
     * @return the expression visitor
     */

    public ExpressionVisitor makeExpressionVisitor() {
        return ExpressionVisitor.make(getStaticContext());
    }

    /**
     * Ask whether the code is compiled in schema-aware mode
     *
     * @return true if the compilation is schema-aware
     */

    public boolean isSchemaAware() {
        return getCompilation().isSchemaAware();
    }

    /**
     * Make this node a substitute for a temporary one previously added to the tree. See
     * StyleNodeFactory for details. "A node like the other one in all things but its class".
     * Note that at this stage, the node will not yet be known to its parent, though it will
     * contain a reference to its parent; and it will have no children.
     *
     * @param temp the element which this one is substituting for
     */

    public void substituteFor(StyleElement temp) {
        setRawParent(temp.getRawParent());
        setAttributes(temp.attributes());
        //setNamespaceList(temp.getNamespaceList());
        setNamespaceMap(temp.getAllNamespaces());
        setNodeName(temp.getNodeName());
        setRawSequenceNumber(temp.getRawSequenceNumber());
        extensionNamespaces = temp.extensionNamespaces;
        excludedNamespaces = temp.excludedNamespaces;
        version = temp.version;
        staticContext = temp.staticContext;
        validationError = temp.validationError;
        reportingCircumstances = temp.reportingCircumstances;
        compilation = temp.compilation;
        //lineNumber = temp.lineNumber;
    }

    /**
     * Set a validation error. This is an error detected during construction of this element on the
     * stylesheet, but which is not to be reported until later.
     *
     * @param reason        the details of the error
     * @param circumstances a code identifying the circumstances under which the error is to be reported
     */

    public void setValidationError(XmlProcessingIncident reason,
                                   OnFailure circumstances) {
        validationError = reason;
        reportingCircumstances = circumstances;
    }

    void setIgnoreInstruction() {
        reportingCircumstances = OnFailure.IGNORED_INSTRUCTION;
    }

    /**
     * Ask whether this node is an instruction. The default implementation says it isn't.
     *
     * @return true if this element is an instruction
     */

    public boolean isInstruction() {
        return false;
    }

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import). The default implementation returns false
     *
     * @return true if the element is a permitted child of xsl:stylesheet or xsl:transform
     */

    public boolean isDeclaration() {
        return false;
    }

    /**
     * Get the visibility of the component. Returns the actual value of the visibility attribute,
     * after validation, unless this is absent, in which case it returns the default value of PRIVATE.
     * Invokes {@link #invalidAttribute(String, String)} if the value is invalid.
     *
     * @return the declared visibility of the component, or {@link Visibility#PRIVATE}
     * if the visibility attribute is absent.
     */

    public Visibility getVisibility() {
        String vis = getAttributeValue("", "visibility");
        if (vis == null) {
            return Visibility.PRIVATE;
        } else {
            return interpretVisibilityValue(vis, "");
        }
    }

    /**
     * Get the visibility of the component. Returns the actual value of the visibility attribute,
     * after validation, unless this is absent, in which case it returns null.
     *
     * @return the declared visibility of the component, or null if the visibility attribute is absent.
     */

    public Visibility getDeclaredVisibility() {
        String vis = getAttributeValue("", "visibility");
        if (vis == null) {
            return null;
        } else {
            return interpretVisibilityValue(vis, "");
        }
    }

    /**
     * Mark tail-recursive calls on templates and functions.
     * For most instructions, this returns false.
     *
     * @return true if one or more tail calls were identified
     */

    protected boolean markTailCalls() {
        return false;
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true if this instruction is allowed to contain a sequence constructor
     */

    protected boolean mayContainSequenceConstructor() {
        return false;
    }

    /**
     * Determine whether this type of element is allowed to contain an xsl:fallback
     * instruction. Note that this is only relevant if the element is an instruction.
     *
     * @return true if this element is allowed to contain an xsl:fallback
     */

    protected boolean mayContainFallback() {
        return mayContainSequenceConstructor();
    }

    /**
     * Determine whether this type of element is allowed to contain an xsl:param element
     *
     * @return true if this element is allowed to contain an xsl:param
     */

    protected boolean mayContainParam() {
        return false;
    }

    /**
     * Get the effective value of the default-validation attribute
     *
     * @return the value of default-validation, as a constant from the {@link Validation} class,
     * or Validation.STRIP if there is no containing element with a default-validation
     * attribute.
     */

    int getDefaultValidation() {
        int v = defaultValidation;
        NodeInfo p = this;
        while (v == Validation.DEFAULT) {
            p = p.getParent();
            if (!(p instanceof StyleElement)) {
                return Validation.STRIP;
                //return getCompilation().isSchemaAware() ? Validation.PRESERVE : Validation.STRIP;
            }
            v = ((StyleElement) p).defaultValidation;
        }
        return v;
    }


    /**
     * Make a structured QName, using this Element as the context for namespace resolution.
     * If the name is unprefixed, the default namespace is <b>not</b> used. If the name is
     * not valid, then a compileError is reported, and the value saxon:error-name is returned.
     *
     * @param lexicalQName  The lexical QName as written, in the form "[prefix:]localname". Leading and trailing whitespace
     *                      will be trimmed. The EQName syntax "Q{uri}local" is also
     *                      accepted.
     * @param errorCode     The error code to be used if the QName is not valid. If this is set to null,
     *                      then the code used is XTSE0280 for an undeclared prefix, and XTSE0020
     *                      for all other errors. The code XTSE0080 is used if the URI is a reserved
     *                      namespace, regardless of the supplied error code.
     * @param attributeName EQName of the attribute containing the QName, for use in error reporting.
     *                      May be null.
     * @return the StructuredQName representation of this lexical QName
     */

    public final StructuredQName makeQName(String lexicalQName, String errorCode, String attributeName) {

        StructuredQName qName;
        try {
            qName = StructuredQName.fromLexicalQName(lexicalQName, false,
                                                     true, this);
        } catch (XPathException e) {
            e.setIsStaticError(true);
            if (errorCode == null) {
                String code = e.getErrorCodeLocalPart();
                if ("FONS0004".equals(code)) {
                    e.setErrorCode("XTSE0280");
                } else if ("FOCA0002".equals(code)) {
                    e.setErrorCode("XTSE0020");
                } else if (code == null) {
                    e.setErrorCode("XTSE0020");
                }
            } else {
                e.setErrorCode(errorCode);
            }
            if (attributeName == null) {
                e.setLocator(this);
            } else {
                e.setLocator(new AttributeLocation(this, StructuredQName.fromEQName(attributeName)));
            }
            compileError(e);
            qName = new StructuredQName("saxon", NamespaceConstant.SAXON, "error-name");
        }
        if (NamespaceConstant.isReserved(qName.getURI())) {
            if (qName.hasURI(NamespaceConstant.XSLT)) {
                if (qName.getLocalPart().equals("initial-template")
                        && (this instanceof XSLTemplate || this instanceof XSLCallTemplate)) {
                    return qName;
                }
                if (qName.getLocalPart().equals("original")) {
                    // OK if within xsl:override
                    if (findAncestorElement(StandardNames.XSL_OVERRIDE) != null) {
                        return qName;
                    }
                }
            }
            XmlProcessingIncident err = new XmlProcessingIncident("Namespace prefix " +
                                                      qName.getPrefix() + " refers to a reserved namespace", "XTSE0080");
            compileError(err);
            qName = new StructuredQName("saxon", NamespaceConstant.SAXON, "error-name");
        }
        return qName;
    }

    /**
     * Get the first ancestor element in the stylesheet tree that has a given name, supplied by
     * fingerprint.
     *
     * @param fingerprint the name of the required element
     * @return the first (innermost) ancestor with the required name, or null if none is found
     */

    StyleElement findAncestorElement(int fingerprint) {
        NodeInfo parent = getParent();
        while (true) {
            if (parent instanceof StyleElement) {
                if (parent.getFingerprint() == fingerprint) {
                    return (StyleElement) parent;
                } else {
                    parent = parent.getParent();
                }
            } else {
                return null;
            }
        }
    }

    /**
     * Assuming this is an xsl:use-package element, find the package to which it refers.
     *
     * @return the package referenced by this xsl:use-package element; or null if this is not
     * an xsl:use-package element
     */

    public StylesheetPackage getUsedPackage() {
        return null;
    }

    /**
     * Check that a reference to xsl:original appears within an xsl:override element, and that
     * the name of the containing component matches the name of a component in the used stylesheet
     * package; return the component in that package with matching symbolic name
     *
     * @param componentKind the kind of component required, e.g. StandardNames.XSL_TEMPLATE
     * @return the component with matching name in the used stylesheet
     * @throws XPathException if the xsl:original reference appears in an invalid context
     */

    public Actor getXslOriginal(int componentKind) throws XPathException {
        StyleElement container = componentKind == getFingerprint() ? this : findAncestorElement(componentKind);
        if (!(container instanceof StylesheetComponent)) {
            throw new XPathException(
                    "A reference to xsl:original appears within the wrong kind of component: in this case" +
                            ", it must be within xsl:" + getNamePool().getLocalName(componentKind), "XTSE0650", this);
        }
        SymbolicName originalName = ((StylesheetComponent) container).getSymbolicName();
        StyleElement xslOverride = container.findAncestorElement(StandardNames.XSL_OVERRIDE);
        if (xslOverride == null) {
            throw new XPathException("A reference to xsl:original can be used only within an xsl:override element");
        }
        StyleElement usePackage = xslOverride.findAncestorElement(StandardNames.XSL_USE_PACKAGE);
        if (usePackage == null) {
            throw new XPathException("The parent of xsl:override must be an xsl:use-package element", "XTSE0010", xslOverride);
        }

        Component overridden = usePackage.getUsedPackage().getComponent(originalName);
        if (overridden == null) {
            // the error will be detected and reported elsewhere
            return null;
        }
        return overridden.getActor();
    }

    /**
     * Get the component that this declaration overrides, or null if this is not an overriding declaration
     *
     * @return the overridden component, or null
     */

    Component getOverriddenComponent() {
        if (!(this instanceof StylesheetComponent)) {
            return null;
        }
        SymbolicName originalName = ((StylesheetComponent) this).getSymbolicName();
        StyleElement xslOverride = findAncestorElement(StandardNames.XSL_OVERRIDE);
        if (xslOverride == null) {
            return null;
        }
        StyleElement usePackage = xslOverride.findAncestorElement(StandardNames.XSL_USE_PACKAGE);
        if (usePackage == null) {
            return null;
        }

        return usePackage.getUsedPackage().getComponent(originalName);
    }


    public RetainedStaticContext makeRetainedStaticContext() {
        return getStaticContext().makeRetainedStaticContext();
    }

    /**
     * Ask whether this instruction requires a different retained static context from the containing
     * (parent) instruction. That is, this instruction changes the static base URI, the default collation,
     * or the set of in-scope namespaces.
     *
     * @return true if the context for evaluating this instruction differs in relevant ways from that
     * of the calling instruction
     */
    boolean changesRetainedStaticContext() {
        NodeImpl parent = getParent();
        return parent == null
                || !ExpressionTool.equalOrNull(getBaseURI(), parent.getBaseURI())
                || defaultCollationName != null
                || defaultXPathNamespace != null
                || !(parent instanceof StyleElement)
                || getAllNamespaces() != parent.getAllNamespaces()
                || getEffectiveVersion() != ((StyleElement)parent).getEffectiveVersion();
    }

    /**
     * Get the namespace context of the instruction.
     *
     * @return the namespace context. This method does not make a copy of the namespace context,
     * so a reference to the returned NamespaceResolver will lock the stylesheet tree in memory.
     */

    public NamespaceResolver getNamespaceResolver() {
        return this;
    }

    /**
     * Process the attributes of this element and all its children
     *
     * @throws XPathException in the event of a static error being detected
     */

    public void processAllAttributes() throws XPathException {
        processDefaultCollationAttribute();
        processDefaultMode();
        staticContext = new ExpressionContext(this, null);
        processAttributes();
        for (NodeInfo child : children()) {
            if (child instanceof StyleElement) {
                ((StyleElement) child).processAllAttributes();
            } else if (child instanceof TextValueTemplateNode) {
                ((TextValueTemplateNode)child).parse();
            }
        };
    }

    /**
     * Process the standard attributes such as {@code [xsl:]expand-text}. Invokes
     * {@link #compileError(String)} or similar if the value of any of these attributes
     * is invalid.
     *
     * <p>The method processes:</p>
     * <ul>
     * <li>{@code extension-element-prefixes}</li>
     * <li>{@code exclude-result-prefixes}</li>
     * <li>{@code version}</li>
     * <li>{@code default-xpath-namespace}</li>
     * <li>{@code default-validation}</li>
     * <li>{@code expand-text}</li>
     * </ul>
     *
     * <p>but not:</p>
     * <ul>
     * <li>{@code default-collation}</li>
     * <li>{@code default-mode}</li>
     * </ul>
     *
     * @param namespace either "" to find the attributes in the null namespace,
     *                  or NamespaceConstant.XSLT to find them in the XSLT namespace
     */

    public void processStandardAttributes(String namespace) {
        processExtensionElementAttribute(namespace);
        processExcludedNamespaces(namespace);
        processVersionAttribute(namespace);
        processDefaultXPathNamespaceAttribute(namespace);
        processDefaultValidationAttribute(namespace);
        processExpandTextAttribute(namespace);
    }

    /**
     * Get an attribute value given the Clark name of the attribute (that is,
     * the name in {uri}local format).
     *
     * @param clarkName the name of the attribute in {uri}local format
     * @return the value of the attribute if it exists, or null otherwise
     */

    public String getAttributeValue(String clarkName) {
        NodeName nn = FingerprintedQName.fromClarkName(clarkName);
        return getAttributeValue(nn.getURI(), nn.getLocalPart());
    }

    /**
     * Process the attribute list for the element. This is a wrapper method that calls
     * prepareAttributes (provided in the subclass) and traps any exceptions
     */

    final void processAttributes() {
        prepareAttributes();
    }

    /**
     * Check whether an unknown attribute is permitted.
     *
     * @param nc The name code of the attribute name
     */

    protected void checkUnknownAttribute(NodeName nc) {

        String attributeURI = nc.getURI();
        String elementURI = getURI();
        String clarkName = nc.getStructuredQName().getClarkName();

        if (forwardsCompatibleModeIsEnabled()) {
            // then unknown attributes are permitted and ignored
            return;
        }

        // allow xsl:extension-element-prefixes etc on an extension element

        if (isInstruction() &&
                attributeURI.equals(NamespaceConstant.XSLT) &&
                !elementURI.equals(NamespaceConstant.XSLT) &&
                (clarkName.endsWith("}default-collation") ||
                         clarkName.endsWith("}default-mode") ||
                         clarkName.endsWith("}xpath-default-namespace") ||
                         clarkName.endsWith("}expand-text") ||
                         clarkName.endsWith("}extension-element-prefixes") ||
                         clarkName.endsWith("}exclude-result-prefixes") ||
                         clarkName.endsWith("}version") ||
                         clarkName.endsWith("}default-validation") ||
                         clarkName.endsWith("}use-when"))) {
            return;
        }

        // allow standard attributes on an XSLT element

        if (elementURI.equals(NamespaceConstant.XSLT) &&
                (clarkName.equals("default-collation") ||
                         clarkName.equals("default-mode") ||
                         clarkName.equals("expand-text") ||
                         clarkName.equals("xpath-default-namespace") ||
                         clarkName.equals("extension-element-prefixes") ||
                         clarkName.equals("exclude-result-prefixes") ||
                         clarkName.equals("version") ||
                         clarkName.equals("default-validation") ||
                         clarkName.equals("use-when"))) {
            return;
        }

        if ("".equals(attributeURI) || NamespaceConstant.XSLT.equals(attributeURI)) {
            compileErrorInAttribute("Attribute " + Err.wrap(nc.getDisplayName(), Err.ATTRIBUTE) +
                                            " is not allowed on element " + Err.wrap(getDisplayName(), Err.ELEMENT),
                                    "XTSE0090", clarkName);
        } else if (NamespaceConstant.SAXON.equals(attributeURI)) {
            compileWarning("Unrecognized attribute in Saxon namespace: " + nc.getDisplayName(), "XTSE0090");
        }
    }

    /**
     * Set the attribute list for the element. This is called to process the attributes (note
     * the distinction from processAttributes in the superclass).
     * Must be supplied in a subclass
     */

    protected abstract void prepareAttributes();

    /**
     * Find the last child instruction of this instruction. Returns null if
     * there are no child instructions, or if the last child is a text node.
     *
     * @return the last child instruction, or null if there are no child instructions
     */

    StyleElement getLastChildInstruction() {
        StyleElement last = null;
        for (NodeInfo child : children()) {
            if (child instanceof StyleElement) {
                last = (StyleElement) child;
            } else {
                last = null;
            }
        }
        return last;
    }

    /**
     * Compile an XPath expression in the context of this stylesheet element
     *
     * @param expression the source text of the XPath expression
     * @param att   the attribute containing the XPath expression, or
     *                   null if the expression is in a text node
     * @return the compiled expression tree for the XPath expression. In the case of an error,
     * returns an ErrorExpression that will fail at run-time if executed.
     */

    public Expression makeExpression(String expression, AttributeInfo att) {
        try {
            StaticContext env = staticContext;
            if (att != null) {
                StructuredQName attName = att.getNodeName().getStructuredQName();
                env = getStaticContext(attName);
            }
            return ExpressionTool.make(expression, env, 0, Token.EOF,
                                       getCompilation().getCompilerInfo().getCodeInjector());
        } catch (XPathException err) {
            err.maybeSetLocation(allocateLocation());
            if (err.isReportableStatically()) {
                compileError(err);
            }
            ErrorExpression erexp = new ErrorExpression(new XmlProcessingException(err));
            erexp.setRetainedStaticContext(makeRetainedStaticContext());
            erexp.setLocation(allocateLocation());
            return erexp;
        }
    }

    /**
     * Make a pattern in the context of this stylesheet element
     *
     * @param pattern the source text of the pattern
     * @return the compiled pattern
     */

    Pattern makePattern(String pattern, String attributeName) {
        try {
            StaticContext env = getStaticContext(new StructuredQName("", "", attributeName));
            Pattern p = Pattern.make(pattern, env, getCompilation().getPackageData());
            p.setOriginalText(pattern);
            p.setLocation(allocateLocation());
            return p;
        } catch (XPathException err) {
            err.maybeSetErrorCode("XTSE0340");
            if ("XPST0003".equals(err.getErrorCodeLocalPart())) {
                err.setErrorCode("XTSE0340");
            }
            compileError(err);
            NodeTestPattern nsp = new NodeTestPattern(AnyNodeTest.getInstance());
            nsp.setLocation(allocateLocation());
            return nsp;
        }
    }

    /**
     * Make an attribute value template in the context of this stylesheet element
     *
     * @param expression the source text of the attribute value template
     * @param att the attribute containing the AVT, or null in the case of a text value template
     * @return a compiled XPath expression that computes the value of the attribute (including
     * concatenating the results of embedded expressions with any surrounding fixed text)
     */

    protected Expression makeAttributeValueTemplate(String expression, AttributeInfo att) {
        StaticContext env = att == null ?
                staticContext :
                getStaticContext(att.getNodeName().getStructuredQName());
        if (att != null) {
            StructuredQName attName = att.getNodeName().getStructuredQName();
            env = getStaticContext(attName);
        }
        try {
            return AttributeValueTemplate.make(expression, env);
        } catch (XPathException err) {
            compileError(err);
            return new StringLiteral(expression);
        }
    }


    /**
     * Check the value of an attribute, as supplied statically
     *
     * @param name    the name of the attribute
     * @param value   the value of the attribute
     * @param avt     set to true if the value is permitted to be an attribute value template
     * @param allowed list of permitted values, which must be in alphabetical order
     */

    void checkAttributeValue(String name, String value, boolean avt, String[] allowed) {
        if (avt && value.contains("{")) {
            return;
        }
        if (Arrays.binarySearch(allowed, value) < 0) {
            FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C64);
            sb.append("Invalid value for ");
            sb.append("@");
            sb.append(name);
            sb.append(". Value must be one of (");
            for (int i = 0; i < allowed.length; i++) {
                sb.append(i == 0 ? "" : "|");
                sb.append(allowed[i]);
            }
            sb.append(")");
            compileError(sb.toString(), "XTSE0020");
        }
    }

    final static String[] YES_NO = new String[]{"0", "1", "false", "no", "true", "yes"};


    /**
     * Process an attribute whose value is yes, no, true, false, 1, or 0; returning true or false.
     *
     * @param name  the name of the attribute (used for diagnostics)
     * @param value the value of the attribute
     */

    public boolean processBooleanAttribute(String name, String value) {
        String s = Whitespace.trim(value);
        if (isYes(s)) {
            return true;
        } else if (isNo(s)) {
            return false;
        } else {
            invalidAttribute(name, "yes|no | true|false | 1|0");
            return false; // never get here
        }

    }

    static boolean isYes(String s) {
        return "yes".equals(s) || "true".equals(s) || "1".equals(s);
    }

    static boolean isNo(String s) {
        return "no".equals(s) || "false".equals(s) || "0".equals(s);
    }

    boolean processStreamableAtt(String streamableAtt) {
        boolean streamable = processBooleanAttribute("streamable", streamableAtt);
        if (streamable) {
            if (!getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT)) {
                compileWarning("Request for streaming ignored: this Saxon configuration does not support streaming", SaxonErrorCode.SXST0068);
                return false;
            }
            if ("off".equals(getConfiguration().getConfigurationProperty(Feature.STREAMABILITY))) {
                compileWarning("Request for streaming ignored: streaming is disabled in this Saxon configuration", SaxonErrorCode.SXST0068);
                return false;
            }
        }
        return streamable;
    }

    /**
     * Process an attribute whose value is a SequenceType
     *
     * @param sequenceType the source text of the attribute
     * @return the processed sequence type
     * @throws XPathException if the syntax is invalid or for example if it refers to a type
     *                        that is not in the static context
     */

    public SequenceType makeSequenceType(String sequenceType)
            throws XPathException {
        ExpressionContext env = getStaticContext();
        int languageLevel = env.getXPathVersion();
        if (languageLevel == 30) {
            languageLevel = 305; // XPath 3.0 + XSLT extensions
        }
        XPathParser parser =
                getConfiguration().newExpressionParser("XP", false, languageLevel);

        QNameParser qp = new QNameParser(staticContext.getNamespaceResolver())
                .withAcceptEQName(staticContext.getXPathVersion() >= 30)
                .withErrorOnBadSyntax("XPST0003")
                .withErrorOnUnresolvedPrefix("XPST0081");

        parser.setQNameParser(qp);
        return parser.parseSequenceType(sequenceType, staticContext);
    }

    SequenceType makeExtendedSequenceType(String sequenceType)
            throws XPathException {
        getStaticContext();
        XPathParser parser =
                getConfiguration().newExpressionParser("XP", false, 31);
        QNameParser qp = new QNameParser(staticContext.getNamespaceResolver())
                .withAcceptEQName(staticContext.getXPathVersion() >= 30)
                .withErrorOnBadSyntax("XPST0003")
                .withErrorOnUnresolvedPrefix("XPST0081");
        parser.setQNameParser(qp);
        return parser.parseExtendedSequenceType(sequenceType, staticContext);
    }

    /**
     * Process the [xsl:]extension-element-prefixes attribute if there is one
     *
     * @param ns the namespace URI of the attribute - either the XSLT namespace or "" for the null namespace
     */

    void processExtensionElementAttribute(String ns) {
        String ext = getAttributeValue(ns, "extension-element-prefixes");
        if (ext != null) {
            // go round twice, once to count the values and next to add them to the array
            int count = 0;
            StringTokenizer st1 = new StringTokenizer(ext, " \t\n\r", false);
            while (st1.hasMoreTokens()) {
                st1.nextToken();
                count++;
            }
            extensionNamespaces = new String[count];
            count = 0;
            StringTokenizer st2 = new StringTokenizer(ext, " \t\n\r", false);
            while (st2.hasMoreTokens()) {
                String s = st2.nextToken();
                if ("#default".equals(s)) {
                    s = "";
                }
                String uri = getURIForPrefix(s, false);
                if (uri == null) {
                    extensionNamespaces = null;
                    compileError("Namespace prefix " + s + " is undeclared", "XTSE1430");
                } else if (NamespaceConstant.isReserved(uri)) {
                    compileError("Namespace " + uri + " is reserved: it cannot be used for extension instructions " +
                                           "(perhaps exclude-result-prefixes was intended).",
                                   "XTSE0085");
                    extensionNamespaces[count++] = uri;
                } else {
                    extensionNamespaces[count++] = uri;
                }
            }
        }
    }

    /**
     * Process the [xsl:]exclude-result-prefixes attribute if there is one
     *
     * @param ns the namespace URI of the attribute required, either the XSLT namespace or ""
     */

    void processExcludedNamespaces(String ns) {
        String ext = getAttributeValue(ns, "exclude-result-prefixes");
        if (ext != null) {
            if ("#all".equals(Whitespace.trim(ext))) {
                List<String> excluded = new ArrayList<>();
                for (NamespaceBinding binding : getAllNamespaces()) {
                    excluded.add(binding.getURI());
                }
                excludedNamespaces = excluded.toArray(new String[0]);
            } else {
                // go round twice, once to count the values and next to add them to the array
                int count = 0;
                StringTokenizer st1 = new StringTokenizer(ext, " \t\n\r", false);
                while (st1.hasMoreTokens()) {
                    st1.nextToken();
                    count++;
                }
                excludedNamespaces = new String[count];
                count = 0;
                StringTokenizer st2 = new StringTokenizer(ext, " \t\n\r", false);
                while (st2.hasMoreTokens()) {
                    String s = st2.nextToken();
                    if ("#default".equals(s)) {
                        s = "";
                    } else if ("#all".equals(s)) {
                        compileError("In exclude-result-prefixes, cannot mix #all with other values", "XTSE0020");
                    }
                    String uri = getURIForPrefix(s, true);
                    if (uri == null) {
                        excludedNamespaces = null;
                        compileError("Namespace prefix " + s + " is not declared", "XTSE0808");
                        break;
                    }
                    excludedNamespaces[count++] = uri;
                    if (s.isEmpty() && uri.isEmpty()) {
                        compileError("Cannot exclude the #default namespace when no default namespace is declared",
                                     "XTSE0809");
                    }
                }
            }
        }
    }

    /**
     * Process the [xsl:]version attribute if there is one
     *
     * @param ns the namespace URI of the attribute required, either the XSLT namespace or ""
     */

    protected void processVersionAttribute(String ns) {
        String v = Whitespace.trim(getAttributeValue(ns, "version"));
        if (v != null) {
            ConversionResult val = BigDecimalValue.makeDecimalValue(v, true);
            if (val instanceof ValidationFailure) {
                version = 30;
                compileError("The version attribute must be a decimal literal", "XTSE0110");
            } else {
                // Note this will normalize the decimal so that trailing spaces are not significant
                version = ((BigDecimalValue) val).getDecimalValue().multiply(BigDecimal.TEN).intValue();
                if (version < 20 && version != 10) {
                    // XSLT 2.0 says use backwards compatible mode. XSLT 3.0 says we can raise an error.
                    // Both allow a warning
                    issueWarning("Unrecognized version " + val + ": treated as 1.0", this);
                    version = 10;
                } else if (version > 20 && version < 30) {
                    issueWarning("Unrecognized version " + val + ": treated as 2.0", this);
                    version = 20;
                }
            }
        }
    }

    /**
     * Get the numeric value of the version number appearing as an attribute on this element,
     * or inherited from its ancestors
     *
     * @return the version number times ten as an integer
     */

    int getEffectiveVersion() {
        if (version == -1) {
            NodeInfo node = getParent();
            if (node instanceof StyleElement) {
                version = ((StyleElement) node).getEffectiveVersion();
            } else {
                return 20;    // defensive programming
            }
        }
        return version;
    }

    /**
     * Validate the value of the [xsl:]validation attribute
     *
     * @param value the raw value of the attribute
     * @return the encoded value of the attribute
     */

    protected int validateValidationAttribute(String value) {
        int code = Validation.getCode(value);
        if (code == Validation.INVALID) {
            String prefix = this instanceof LiteralResultElement ? "xsl:" : "";
            compileError("Invalid value of " + prefix + "validation attribute: '" + value + "'", "XTSE0020");
            code = getDefaultValidation();
        }
        if (!isSchemaAware()) {
            if (code == Validation.STRICT) {
                compileError("To perform validation, a schema-aware XSLT processor is needed", "XTSE1660");
            }
            code = Validation.STRIP;
        }
        return code;
    }

    /**
     * Ask if an extension attribute is allowed; if no Professional Edition license is available,
     * issue a warning saying the attribute is ignored, and return false
     * @param attribute the name of the attribute
     */

    protected boolean isExtensionAttributeAllowed(String attribute)  {
        if (getConfiguration().isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
            return true;
        } else {
            issueWarning("The option " + getDisplayName() + "/@" + attribute + " is ignored because it requires a Saxon-PE license", this);
            return false;
        }
    }

    /**
     * Determine whether forwards-compatible mode is enabled for this element
     *
     * @return true if forwards-compatible mode is enabled
     */

    boolean forwardsCompatibleModeIsEnabled() {
        return getEffectiveVersion() > 30;
    }

    /**
     * Determine whether 1.0-compatible mode is enabled for this element
     *
     * @return true if 1.0 compatable mode is enabled, that is, if this or an enclosing
     * element specifies an [xsl:]version attribute whose value is less than 2.0
     */

    boolean xPath10ModeIsEnabled() {
        return getEffectiveVersion() < 20;
    }

    /**
     * Process the [xsl:]default-collation attribute if there is one.
     */

    void processDefaultCollationAttribute() {
        String ns = getURI().equals(NamespaceConstant.XSLT) ? "" : NamespaceConstant.XSLT;
        String v = getAttributeValue(ns, "default-collation");
        StringBuilder reasons = new StringBuilder();
        if (v != null) {
            StringTokenizer st = new StringTokenizer(v, " \t\n\r", false);
            while (st.hasMoreTokens()) {
                String uri = st.nextToken();
                if (uri.equals(NamespaceConstant.CODEPOINT_COLLATION_URI)) {
                    defaultCollationName = uri;
                    return;
                } else {
                    URI collationURI;
                    try {
                        collationURI = new URI(uri);
                        if (!collationURI.isAbsolute()) {
                            URI base = new URI(getBaseURI());
                            collationURI = base.resolve(collationURI);
                            uri = collationURI.toString();
                        }
                    } catch (URISyntaxException err) {
                        compileError("default collation '" + uri + "' is not a valid URI");
                        uri = NamespaceConstant.CODEPOINT_COLLATION_URI;
                    }

                    try {
                        if (getConfiguration().getCollation(uri) != null) {
                            defaultCollationName = uri;
                            return;
                        } else {
                            if (reasons.length() != 0) {
                                reasons.append("; ");
                            }
                            reasons.append("Collation ").append(uri).append(" is not recognized");

                        }
                    } catch (XPathException e) {
                        if (reasons.length() != 0) {
                            reasons.append("; ");
                        }
                        reasons.append("Collation ").append(uri).append(" is not recognized (").append(e.getMessage()).append(")");
                        // Ignore an unrecognized collation URI
                    }

                }
                // if not recognized, try the next URI in order
            }
            String msg = "No recognized collation URI found in default-collation attribute";
            if (reasons.length() != 0) {
                msg += ". ";
                msg += reasons.toString();
            }
            compileErrorInAttribute(msg, "XTSE0125",
                                    new StructuredQName("", ns, "default-collation").getClarkName());
        }
    }

    /**
     * Get the default collation for this stylesheet element. If no default collation is
     * specified in the stylesheet, return the Unicode codepoint collation name.
     *
     * @return the name of the default collation
     */

    protected String getDefaultCollationName() {
        StyleElement e = this;
        while (true) {
            if (e.defaultCollationName != null) {
                return e.defaultCollationName;
            }
            NodeInfo p = e.getParent();
            if (!(p instanceof StyleElement)) {
                break;
            }
            e = (StyleElement) p;
        }
        return getConfiguration().getDefaultCollationName();
    }

    /**
     * Find a named collation. Note this method should only be used at compile-time, before declarations
     * have been pre-processed. After that time, use getCollation().
     *
     * @param name    identifies the name of the collation required
     * @param baseURI the base URI to be used for resolving the collation name if it is relative
     * @return null if the collation is not found
     * @throws XPathException if either URI is invalid as a URI
     */

    StringCollator findCollation(String name, String baseURI) throws XPathException {
        return getConfiguration().getCollation(name, baseURI);
    }

    /**
     * Process the [xsl:]default-mode attribute if there is one
     */

    void processDefaultMode() {
        String ns = getURI().equals(NamespaceConstant.XSLT) ? "" : NamespaceConstant.XSLT;
        String v = getAttributeValue(ns, "default-mode");
        if (v != null) {
            if (v.equals("#unnamed")) {
                defaultMode = Mode.UNNAMED_MODE_NAME;
            } else {
                defaultMode = makeQName(v, null, "default-mode");
            }
        }
        PrincipalStylesheetModule psm = compilation.getPrincipalStylesheetModule();
        final StructuredQName checkedName = defaultMode;
        if (psm != null && psm.isDeclaredModes()) {
            // It will be null on the xsl:package element itself
            psm.addFixupAction(() -> {
                if (psm.getRuleManager().obtainMode(checkedName, false) == null) {
                    XPathException err = new XPathException("Mode " + checkedName.getDisplayName() + " is not declared in an xsl:mode declaration", "XTSE3085");
                    err.setLocation(this);
                    throw err;
                }
            });
        }
    }

    /**
     * Get the default mode for this stylesheet element.
     *
     * @return the name of the default mode, obtained by looking for the default-mode attribute on this element
     * and all all its ancestors. In the absence of a default-mode attribute, returns the magic value
     * {@link Mode#UNNAMED_MODE_NAME}
     */

    StructuredQName getDefaultMode() throws XPathException {
        if (defaultMode == null) {
            processDefaultMode();
            if (defaultMode == null) {
                NodeInfo p = getParent();
                if (p instanceof StyleElement) {
                    return defaultMode = ((StyleElement) p).getDefaultMode();
                } else {
                    return defaultMode = Mode.UNNAMED_MODE_NAME;
                }
            }
        }
        return defaultMode;
    }


    /**
     * Check whether a particular extension element namespace is defined on this node.
     * This checks this node only, not the ancestor nodes.
     * The implementation checks whether the prefix is included in the
     * [xsl:]extension-element-prefixes attribute.
     *
     * @param uri the namespace URI being tested
     * @return true if this namespace is defined on this element as an extension element namespace
     */

    private boolean definesExtensionElement(String uri) {
        if (extensionNamespaces == null) {
            return false;
        }
        for (String extensionNamespace : extensionNamespaces) {
            if (extensionNamespace.equals(uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a namespace uri defines an extension element. This checks whether the
     * namespace is defined as an extension namespace on this or any ancestor node.
     *
     * @param uri the namespace URI being tested
     * @return true if the URI is an extension element namespace URI
     */

    public boolean isExtensionNamespace(String uri) {
        NodeInfo anc = this;
        while (anc instanceof StyleElement) {
            if (((StyleElement) anc).definesExtensionElement(uri)) {
                return true;
            }
            anc = anc.getParent();
        }
        return false;
    }

    /**
     * Check whether this node excludes a particular namespace from the result.
     * This method checks this node only, not the ancestor nodes.
     *
     * @param uri the namespace URI being tested
     * @return true if the namespace is excluded by virtue of an [xsl:]exclude-result-prefixes attribute
     */

    private boolean definesExcludedNamespace(String uri) {
        if (excludedNamespaces == null) {
            return false;
        }
        for (String excludedNamespace : excludedNamespaces) {
            if (excludedNamespace.equals(uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a namespace uri defines an namespace excluded from the result.
     * This checks whether the namespace is defined as an excluded namespace on this
     * or any ancestor node.
     *
     * @param uri the namespace URI being tested
     * @return true if this namespace URI is a namespace excluded by virtue of exclude-result-prefixes
     * on this element or on an ancestor element
     */

    boolean isExcludedNamespace(String uri) {
        if (uri.equals(NamespaceConstant.XSLT) || uri.equals(NamespaceConstant.XML)) {
            return true;
        }
        if (isExtensionNamespace(uri)) {
            return true;
        }
        NodeInfo anc = this;
        while (anc instanceof StyleElement) {
            if (((StyleElement) anc).definesExcludedNamespace(uri)) {
                return true;
            }
            anc = anc.getParent();
        }
        return false;
    }

    /**
     * Process the [xsl:]xpath-default-namespace attribute if there is one
     *
     * @param ns the namespace URI of the attribute required  (the default namespace or the XSLT namespace.)
     */

    void processDefaultXPathNamespaceAttribute(String ns) {
        String v = getAttributeValue(ns, "xpath-default-namespace");
        if (v != null) {
            defaultXPathNamespace = v;
        }
    }

    /**
     * Get the default XPath namespace for elements and types
     *
     * @return the default namespace for elements and types.
     * Return {@link NamespaceConstant#NULL} for the non-namespace
     */

    public String getDefaultXPathNamespace() {
        NodeInfo anc = this;
        while (anc instanceof StyleElement) {
            String x = ((StyleElement) anc).defaultXPathNamespace;
            if (x != null) {
                return x;
            }
            anc = anc.getParent();
        }
        return compilation.getCompilerInfo().getDefaultElementNamespace();
    }

    /**
     * Process the [xsl:]expand-text attribute if there is one (and if XSLT 3.0 is enabled)
     *
     * @param ns the namespace URI of the attribute required  (the default namespace or the XSLT namespace.)
     */

    void processExpandTextAttribute(String ns) {
        String v = getAttributeValue(ns, "expand-text");
        if (v != null) {
            expandText = processBooleanAttribute("expand-text", v);
        } else {
            NodeInfo parent = getParent();
            expandText = parent instanceof StyleElement && ((StyleElement) parent).expandText;
        }
    }

    /**
     * Process the [xsl:]expand-text attribute if there is one
     *
     * @param ns the namespace URI of the attribute required  (the default namespace or the XSLT namespace.)
     */

    void processDefaultValidationAttribute(String ns) {
        String v = getAttributeValue(ns, "default-validation");
        if (v != null) {
            int val = Validation.getCode(v);
            if (val == Validation.STRIP || val == Validation.PRESERVE) {
                defaultValidation = val;
            } else {
                compileErrorInAttribute("@default-validation must be preserve|strip", "XTSE0020", "default-validation");
            }
        }
    }

    /**
     * Ask whether content value templates are available within this element
     *
     * @return true if content value templates are enabled
     */

    boolean isExpandingText() {
        return expandText;
    }


    /**
     * Get the Schema type definition for a type named in the stylesheet (in a
     * "type" attribute).
     *
     * @param typeAtt the value of the type attribute
     * @return the corresponding schema type
     */

    public SchemaType getSchemaType(String typeAtt) {
        try {
            String uri;
            String lname;
            if (typeAtt.startsWith("Q{")) {
                StructuredQName q = makeQName(typeAtt, "XTSE1520", "type");
                uri = q.getURI();
                lname = q.getLocalPart();
            } else {
                String[] parts = NameChecker.getQNameParts(typeAtt);
                lname = parts[1];
                if ("".equals(parts[0])) {
                    // Name is unprefixed: use the default-xpath-namespace
                    uri = getDefaultXPathNamespace();
                } else {
                    uri = getURIForPrefix(parts[0], false);
                    if (uri == null) {
                        compileError("Namespace prefix for type annotation is undeclared", "XTSE1520");
                        return null;
                    }
                }
            }
            if (uri.equals(NamespaceConstant.SCHEMA)) {
                SchemaType t = BuiltInType.getSchemaTypeByLocalName(lname);
                if (t == null) {
                    compileError("Unknown built-in type " + typeAtt, "XTSE1520");
                    return null;
                }
                return t;
            }

            // not a built-in type: look in the imported schemas

            if (!getPrincipalStylesheetModule().isImportedSchema(uri)) {
                compileError("There is no imported schema for the namespace of type " + typeAtt, "XTSE1520");
                return null;
            }
            StructuredQName qName = new StructuredQName("", uri, lname);
            SchemaType stype = getConfiguration().getSchemaType(qName);
            if (stype == null) {
                compileError("There is no type named " + typeAtt + " in an imported schema", "XTSE1520");
            }
            return stype;

        } catch (QNameException err) {
            compileError("Invalid type name. " + err.getMessage(), "XTSE1520");
        }
        return null;
    }

    /**
     * Get the type annotation to use for a given schema type
     *
     * @param schemaType the schema type
     * @return the corresponding numeric type annotation
     */

    public SimpleType getTypeAnnotation(SchemaType schemaType) {
        return (SimpleType) schemaType;
    }

    /**
     * Check that the stylesheet element is valid. This is called once for each element, after
     * the entire tree has been built. As well as validation, it can perform first-time
     * initialisation. The default implementation does nothing; it is normally overriden
     * in subclasses.
     *
     * @param decl the declaration to be validated
     * @throws XPathException if any error is found during validation
     */

    public void validate(ComponentDeclaration decl) throws XPathException {
    }

    /**
     * Hook to allow additional validation of a parent element immediately after its
     * children have been validated.
     *
     * @throws XPathException if any error is found during post-traversal validation
     */

    public void postValidate() throws XPathException {
    }

    /**
     * Method supplied by declaration elements to add themselves to a stylesheet-level index
     *
     * @param decl the Declaration being indexed. (This corresponds to the StyleElement object
     *             except in cases where one module is imported several times with different precedence.)
     * @param top  represents the outermost XSLStylesheet or XSLPackage element
     * @throws XPathException if any error is encountered
     */

    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
    }

    /**
     * Type-check an expression. This is called to check each expression while the containing
     * instruction is being validated. It is not just a static type-check, it also adds code
     * to perform any necessary run-time type checking and/or conversion.
     *
     * @param name the name of the attribute containing the expression to be checked (used for diagnostics)
     * @param exp  the expression to be checked
     * @return the (possibly rewritten) expression after type checking
     * @throws XPathException if type-checking fails statically, that is, if it can be determined that the
     *                        supplied value for the expression cannot possibly be of the required type
     */

    // Note: the typeCheck() call is done at the level of individual path expression; the optimize() call is done
    // for a template or function as a whole. We can't do it all at the function/template level because
    // the static context (e.g. namespaces) changes from one XPath expression to another.
    public Expression typeCheck(String name, Expression exp) throws XPathException {

        if (exp == null) {
            return null;
        }
        Configuration config = getConfiguration();
        if (config.getBooleanProperty(Feature.STRICT_STREAMABILITY)) {
            return exp;
        }
        try {
            exp = exp.typeCheck(makeExpressionVisitor(), config.makeContextItemStaticInfo(Type.ITEM_TYPE, true));
            exp = ExpressionTool.resolveCallsToCurrentFunction(exp);
            //            if (explaining) {
            //                System.err.println("Attribute '" + name + "' of element '" + getDisplayName() + "' at line " + getLineNumber() + ':');
            //                System.err.println("Static type: " +
            //                        SequenceType.makeSequenceType(exp.getItemType(), exp.getCardinality()));
            //                System.err.println("Optimized expression tree:");
            //                exp.display(10, getNamePool(), System.err);
            //            }
//            CodeInjector injector = getCompilation().getCompilerInfo().getCodeInjector();
//            if (injector != null) {
//                return injector.inject(exp, getStaticContext(), LocationKind.XPATH_IN_XSLT, new StructuredQName("", "", name));
//            }
            return exp;
        } catch (XPathException err) {
            // we can't report a dynamic error such as divide by zero unless the expression
            // is actually executed.
            //err.printStackTrace();
            if (err.isReportableStatically()) {
                err.setLocation(new AttributeLocation(this, StructuredQName.fromClarkName(name)));
                compileError(err);
                return exp;
            } else {
                ErrorExpression erexp = new ErrorExpression(new XmlProcessingException(err));
                ExpressionTool.copyLocationInfo(exp, erexp);
                return erexp;
            }
        }
    }

    /**
     * Allocate slots in the local stack frame to range variables used in an XPath expression
     *
     * @param exp the XPath expression for which slots are to be allocated
     */

    void allocateLocalSlots(Expression exp) {
        SlotManager slotManager = getContainingSlotManager();
        if (slotManager == null) {
            throw new AssertionError("Slot manager has not been allocated");
        } else {
            int firstSlot = slotManager.getNumberOfVariables();
            int highWater = ExpressionTool.allocateSlots(exp, firstSlot, slotManager);
            if (highWater > firstSlot) {
                slotManager.setNumberOfVariables(highWater);
                // This algorithm is not very efficient because it never reuses
                // a slot when a variable goes out of scope. But at least it is safe.
                // Note that range variables within XPath expressions need to maintain
                // a slot until the instruction they are part of finishes, e.g. in
                // xsl:for-each.
            }
        }
    }

    /**
     * Type-check a pattern. This is called to check each pattern while the containing
     * instruction is being validated. It is not just a static type-check, it also adds code
     * to perform any necessary run-time type checking and/or conversion.
     *
     * @param name    the name of the attribute holding the pattern, for example "match": used in
     *                diagnostics
     * @param pattern the compiled pattern
     * @return the original pattern, or a substitute pattern if it has been rewritten. Returns null
     * if and only if the supplied pattern is null.
     * @throws net.sf.saxon.trans.XPathException if the pattern fails optimistic static type-checking
     */

    public Pattern typeCheck(String name, Pattern pattern) throws XPathException {
        if (pattern == null) {
            return null;
        }
        try {
            ItemType cit = Type.ITEM_TYPE;
            pattern = pattern.typeCheck(makeExpressionVisitor(), getConfiguration().makeContextItemStaticInfo(cit, true));
            boolean usesCurrent = false;

            for (Operand o : pattern.operands()) {
                Expression filter = o.getChildExpression();
                if (ExpressionTool.callsFunction(filter, Current.FN_CURRENT, false)) {
                    usesCurrent = true;
                    break;
                }
            }
            if (usesCurrent) {
                PatternThatSetsCurrent p2 = new PatternThatSetsCurrent(pattern);
                pattern.bindCurrent(p2.getCurrentBinding());
                pattern = p2;
            }

            return pattern;
        } catch (XPathException err) {
            // we can't report a dynamic error such as divide by zero unless the pattern
            // is actually executed. We don't have an error pattern available, so we
            // construct one
            if (err.isReportableStatically()) {
                XPathException e2 = new XPathException("Error in " + name + " pattern", err);
                e2.setLocator(this);
                e2.setErrorCodeQName(err.getErrorCodeQName());
                throw e2;
            } else {
                Pattern p = new BasePatternWithPredicate(
                        new NodeTestPattern(ErrorType.getInstance()),
                        new ErrorExpression(new XmlProcessingException(err)));
                p.setLocation(allocateLocation());
                return p;
            }
        }
    }

    /**
     * Fix up references from XPath expressions. Overridden for function declarations
     * and variable declarations
     *
     * @throws net.sf.saxon.trans.XPathException if any references cannot be fixed up.
     */

    public void fixupReferences() throws XPathException {
        for (NodeInfo child : children(StyleElement.class::isInstance)) {
            ((StyleElement) child).fixupReferences();
        }
    }

    /**
     * Get the SlotManager for the containing Procedure definition
     *
     * @return the SlotManager associated with the containing Function, Template, etc,
     * or null if there is no such containing Function, Template etc.
     */

    public SlotManager getContainingSlotManager() {
        NodeImpl node = this;
        while (true) {
            NodeImpl next = node.getParent();
            assert next != null;
            if (next instanceof XSLModuleRoot || next.getFingerprint() == StandardNames.XSL_OVERRIDE) {
                if (node instanceof StylesheetComponent) {
                    return ((StylesheetComponent) node).getSlotManager();
                } else {
                    return null;
                }
            }
            node = next;
        }
    }


    /**
     * Recursive walk through the stylesheet to validate all nodes
     *
     * @param decl              the declaration to be validated
     * @param excludeStylesheet true if the XSLStylesheet element is to be excluded
     * @throws XPathException if validation fails
     */

    public void validateSubtree(ComponentDeclaration decl, boolean excludeStylesheet) throws XPathException {
        if (isActionCompleted(StyleElement.ACTION_VALIDATE)) {
            return;
        }
        setActionCompleted(StyleElement.ACTION_VALIDATE);
        if (validationError != null) {
            if (reportingCircumstances == OnFailure.REPORT_ALWAYS) {
                compileError(validationError);
            } else if (reportingCircumstances == OnFailure.REPORT_UNLESS_FORWARDS_COMPATIBLE
                    && !forwardsCompatibleModeIsEnabled()) {
                compileError(validationError);
            } else if (reportingCircumstances == OnFailure.REPORT_STATICALLY_UNLESS_FALLBACK_AVAILABLE) {
                boolean hasFallback = false;
                for (NodeInfo child : children(XSLFallback.class::isInstance)) {
                    hasFallback = true;
                    ((XSLFallback) child).validateSubtree(decl, false);
                }
                if (!hasFallback) {
                    compileError(validationError);
                }
            } else if (reportingCircumstances == OnFailure.REPORT_DYNAMICALLY_UNLESS_FALLBACK_AVAILABLE) {
                for (NodeInfo child : children(XSLFallback.class::isInstance)) {
                    ((XSLFallback) child).validateSubtree(decl, false);
                }
            }
        } else {
            try {
                validate(decl);
            } catch (XPathException err) {
                compileError(err);
            }
            validateChildren(decl, excludeStylesheet);
            if (getCompilation().getErrorCount() == 0) {
                postValidate();
            }
        }
    }

    /**
     * Validate the children of this node, recursively. Overridden for top-level
     * data elements.
     *
     * @param decl              the declaration whose children are to be validated
     * @param excludeStylesheet true if the xsl:stylesheet element is to be excluded
     * @throws XPathException if validation fails
     */

    protected void validateChildren(ComponentDeclaration decl, boolean excludeStylesheet) throws XPathException {
        boolean containsInstructions = mayContainSequenceConstructor();
        StyleElement lastChild = null;
        boolean endsWithTextTemplate = false;
        for (NodeInfo child : children()) {
            if (child instanceof StyleElement) {
                if (!(excludeStylesheet && child instanceof XSLStylesheet)) {
                    endsWithTextTemplate = false;
                    if (containsInstructions && !((StyleElement) child).isInstruction()
                            && !isPermittedChild((StyleElement) child)) {
                        ((StyleElement) child).compileError("An " + getDisplayName() + " element must not contain an " +
                                                                    child.getDisplayName() + " element", "XTSE0010");
                    }
                    ((StyleElement) child).validateSubtree(decl, excludeStylesheet);
                    lastChild = (StyleElement) child;
                }
            } else {
                endsWithTextTemplate = examineTextNode(child);
            }
        }
        if (lastChild instanceof XSLLocalVariable &&
                !(this instanceof XSLStylesheet) && !endsWithTextTemplate) {
            lastChild.compileWarning("A variable with no following sibling instructions has no effect",
                                     SaxonErrorCode.SXWN9001);
        }
    }

    /**
     * Examine a text node in the stylesheet to see if it is a text value template;
     * at the same time, perform type-checking on any contained expressions.
     *
     * @param node the text node
     * @return true if the node is is a text value template with variable content
     * @throws XPathException if type checking of a TVT fails.
     */

    private boolean examineTextNode(NodeInfo node) throws XPathException {
        if (node instanceof TextValueTemplateNode) {
            ((TextValueTemplateNode) node).validate();
            return !(((TextValueTemplateNode) node).getContentExpression() instanceof Literal);
        } else {
            return false;
        }
    }

    /**
     * Check whether a given child is permitted for this element. This method is used when a non-instruction
     * child element such as xsl:sort is encountered in a context where instructions would normally be expected.
     *
     * @param child the child that may or may not be permitted
     * @return true if the child is permitted.
     */

    protected boolean isPermittedChild(StyleElement child) {
        return false;
    }

    /**
     * Get the principal stylesheet module of the package in which
     * this XSLT element appears
     *
     * @return the containing package
     */

    public PrincipalStylesheetModule getPrincipalStylesheetModule() {
        return getCompilation().getPrincipalStylesheetModule();
    }

    /**
     * Get the containing package (the principal stylesheet module of the package in which
     * this XSLT element appears)
     *
     * @return the containing package. May be null if the method is called during initialization.
     */

    public StylesheetPackage getContainingPackage() {
        PrincipalStylesheetModule psm = getPrincipalStylesheetModule();
        return psm==null ? null : psm.getStylesheetPackage();
    }

    /**
     * Check that among the children of this element, any xsl:sort elements precede any other elements
     *
     * @param sortRequired true if there must be at least one xsl:sort element
     */

    void checkSortComesFirst(boolean sortRequired) {
        boolean sortFound = false;
        boolean nonSortFound = false;
        for (NodeInfo child : children()) {
            if (child instanceof XSLSort) {
                if (nonSortFound) {
                    ((XSLSort) child).compileError("Within " + getDisplayName() +
                                                           ", xsl:sort elements must come before other instructions", "XTSE0010");
                }
                sortFound = true;
            } else if (child.getNodeKind() == Type.TEXT) {
                // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    nonSortFound = true;
                }
            } else {
                nonSortFound = true;
            }
        }
        if (sortRequired && !sortFound) {
            compileError(getDisplayName() + " must have at least one xsl:sort child", "XTSE0010");
        }
    }

    /**
     * Convenience method to check that the stylesheet element is at the top level (that is,
     * as a child of xsl:stylesheet or xsl:transform)
     *
     * @param errorCode     the error to throw if it is not at the top level; defaults to XTSE0010
     *                      if the value is null
     * @param allowOverride true if the element is allowed to appear as a child of xsl:override
     */

    public void checkTopLevel(/*@NotNull*/ String errorCode, boolean allowOverride) {
        NodeImpl parent = getParent();
        assert parent != null;
        if (parent.getFingerprint() == StandardNames.XSL_OVERRIDE) {
            if (!allowOverride) {
                compileError("Element " + getDisplayName() + " is not allowed as a child of xsl:override");
            }
        } else if (!isTopLevel()) {
            compileError("Element " + getDisplayName() + " must be top-level (a child of xsl:stylesheet, xsl:transform, or xsl:package)", errorCode);
        }
    }

    /**
     * Convenience method to check that the stylesheet element is empty
     */

    public void checkEmpty() {
        if (hasChildNodes()) {
            compileError("Element must be empty", "XTSE0260");
        }
    }

    /**
     * Convenience method to report the absence of a mandatory attribute
     *
     * @param attribute the name of the attribute whose absence is to be reported
     */

    public void reportAbsence(String attribute) {
        compileError("Element must have an " + Err.wrap(attribute, Err.ATTRIBUTE) + " attribute", "XTSE0010");
    }


    /**
     * Compile the instruction on the stylesheet tree into an executable instruction
     * for use at run-time.
     *
     * @param compilation the compilation episode
     * @param decl        the containing top-level declaration, for example xsl:function or xsl:template
     * @return either a ComputedExpression, or null. The value null is returned when compiling an instruction
     * that returns a no-op, or when compiling a top-level object such as an xsl:template that compiles
     * into something other than an instruction.
     * @throws net.sf.saxon.trans.XPathException if validation fails
     */

    public Expression compile(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // no action: default for non-instruction elements
        return null;
    }

    protected boolean isWithinDeclaredStreamableConstruct() {
        if (getURI().equals(NamespaceConstant.XSLT)) {
            String streamableAtt = getAttributeValue("streamable");
            if (streamableAtt != null) {
                return processStreamableAtt(streamableAtt);
            }
        }
        NodeInfo parent = getParent();
        return parent instanceof StyleElement && ((StyleElement) parent).isWithinDeclaredStreamableConstruct();
    }

    protected String generateId() {
        FastStringBuffer buff = new FastStringBuffer(FastStringBuffer.C16);
        generateId(buff);
        return buff.toString();
    }

    /**
     * Compile a declaration in the stylesheet tree
     * for use at run-time.
     *
     * @param compilation the compilation episode
     * @param decl        the containing top-level declaration, for example xsl:function or xsl:template
     * @throws net.sf.saxon.trans.XPathException if compilation fails
     */

    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // no action: default for elements that are not declarations
    }

    /**
     * Compile the children of this instruction on the stylesheet tree, adding the
     * subordinate instructions to the parent instruction on the execution tree.
     *
     * @param compilation   the Executable
     * @param decl          the Declaration of the containing top-level stylesheet element
     * @param includeParams true if xsl:param elements are to be treated as child instructions (true
     *                      for templates but not for functions)
     * @return the compiled sequence constructor
     * @throws net.sf.saxon.trans.XPathException if compilation fails
     */

    public Expression compileSequenceConstructor(Compilation compilation, ComponentDeclaration decl,
                                                 boolean includeParams)
            throws XPathException {
        // If there are any xsl:on-empty or xsl:on-non-empty children, then reorder the children so
        // that local variable declarations come first. This is necessary to ensure that the instructions
        // remain part of a single "block", since the containing block affects the semantics of
        // on-empty and on-non-empty. Moving variables to come first would probably be a safe strategy in all
        // cases, but there might be a performance disadvantage in some cases, and it's unnecessarily disruptive,
        // especially if there are calls on user extension functions having side-effects.
        // Note: we have already bound variable references to their declarations at this stage, so the reordering
        // does not change the scope of variables.
        // We also move any on-empty instructions to the end of the list, since this makes streaming easier.
        boolean containsEmptyTest = false;
        for (NodeInfo child : children()) {
            int fp = child.getFingerprint();
            if (fp == StandardNames.XSL_ON_EMPTY || fp == StandardNames.XSL_ON_NON_EMPTY) {
                containsEmptyTest = true;
            }
        }
        if (containsEmptyTest) {
            List<NodeInfo> vars = new ArrayList<>();
            List<NodeInfo> onEmpties = new ArrayList<>();
            List<NodeInfo> others = new ArrayList<>();
            for (NodeInfo kid : children()) {
                int fp = kid.getFingerprint();
                if (fp == StandardNames.XSL_VARIABLE || fp == StandardNames.XSL_PARAM) {
                    vars.add(kid);
                } else if (fp == StandardNames.XSL_ON_EMPTY) {
                    onEmpties.add(kid);
                } else {
                    others.add(kid);
                }
            }
            vars.addAll(others);
            vars.addAll(onEmpties);
            return compileSequenceConstructor(compilation, decl, new ListIterator<>(vars), includeParams);
        } else {
            return compileSequenceConstructor(compilation, decl, iterateAxis(AxisInfo.CHILD), includeParams);
        }
    }


    /**
     * Compile the children of this instruction on the stylesheet tree, adding the
     * subordinate instructions to the parent instruction on the execution tree.
     *
     * @param compilation   the Executable
     * @param decl          the Declaration of the containing top-level stylesheet element
     * @param iter          Iterator over the children. This is used in the case where there are children
     *                      that are not part of the sequence constructor, for example the xsl:sort children of xsl:for-each;
     *                      the iterator can be positioned past such elements.
     * @param includeParams true if xsl:param elements are to be treated as child instructions (true
     *                      for templates but not for functions)
     * @return the compiled sequence constructor
     * @throws net.sf.saxon.trans.XPathException if compilation fails
     */

    public Expression compileSequenceConstructor(Compilation compilation, ComponentDeclaration decl,
                                                 SequenceIterator iter, boolean includeParams)
            throws XPathException {

        Location locationId = allocateLocation();
        List<Expression> contents = new ArrayList<>(10);
        boolean containsSpecials = false;
        NodeInfo node;
        while ((node = (NodeInfo) iter.next()) != null) {
            if (node.getNodeKind() == Type.TEXT) {
                if (isExpandingText()) {
                    compileContentValueTemplate((TextImpl) node, contents);
                } else {
                    // handle literal text nodes by generating an xsl:value-of instruction, unless expand-text is enabled
                    AxisIterator lookahead = node.iterateAxis(AxisInfo.FOLLOWING_SIBLING);
                    NodeInfo sibling = lookahead.next();
                    if (!(sibling instanceof XSLLocalParam || sibling instanceof XSLSort
                                  || sibling instanceof XSLContextItem || sibling instanceof XSLOnCompletion)) {
                        // The test for XSLParam and XSLSort is to eliminate whitespace nodes that have been retained
                        // because of xml:space="preserve"
                        Expression text = new ValueOf(new StringLiteral(node.getStringValue()), false, false);
                        text.setLocation(allocateLocation());

//                        CodeInjector injector = getCompilation().getCompilerInfo().getCodeInjector();
//                        if (injector != null) {
//                            Expression tracer = injector.inject(text);
//                            tracer.setLocation(text.getLocation());
//                            text = tracer;
//                        }

                        contents.add(text);
                    }
                }

            } else if (node instanceof XSLLocalVariable) {
                XSLLocalVariable var = (XSLLocalVariable) node;
                SourceBinding sourceBinding = var.getSourceBinding();
                var.compileLocalVariable(compilation, decl);

                Expression tail = compileSequenceConstructor(compilation, decl, iter, includeParams);
                if (tail == null || Literal.isEmptySequence(tail)) {
                    // this doesn't happen, because if there are no instructions following
                    // a variable, we'll have taken the var==null path above
                    //return result;
                } else {
                    LetExpression let = new LetExpression();
                    let.setInstruction(true);
                    let.setRequiredType(var.getRequiredType());
                    let.setVariableQName(sourceBinding.getVariableQName());
                    let.setSequence(sourceBinding.getSelectExpression());
                    let.setAction(tail);
                    sourceBinding.fixupBinding(let);
                    locationId = ((StyleElement) node).allocateLocation();
                    let.setLocation(locationId);
//                    if (getCompilation().getCompilerInfo().isCompileWithTracing()) {
//                        TraceExpression t = new TraceExpression(let);
//                        t.setConstructType(LocationKind.LET_EXPRESSION);
//                        t.setObjectName(var.getSourceBinding().getVariableQName());
//                        t.setNamespaceResolver(getNamespaceResolver());
//                        contents.add(t);
//                    } else {
                        contents.add(let);
//                    }
                    if (var.changesRetainedStaticContext()) {
                        let.setRetainedStaticContext(makeRetainedStaticContext());
                    }
                    //result.setLocationId(locationId);
                }


            } else if (node instanceof StyleElement) {
                StyleElement snode = (StyleElement) node;
                int fp = snode.getFingerprint();
                if (fp == StandardNames.XSL_ON_EMPTY || fp == StandardNames.XSL_ON_NON_EMPTY) {
                    containsSpecials = true;
                }
                Expression child;
                if (snode.validationError != null && !(snode instanceof AbsentExtensionElement)) {
                    if (snode.reportingCircumstances == OnFailure.REPORT_IF_INSTANTIATED) {
                        child = new ErrorExpression(snode.validationError);
                    } else {
                        child = fallbackProcessing(compilation, decl, snode);
                    }

                } else {
                    child = snode.compile(compilation, decl);
                    if (child != null) {
                        if (snode.changesRetainedStaticContext()) {
                            child.setRetainedStaticContext(snode.makeRetainedStaticContext());
                        }
                        setInstructionLocation(snode, child);
                    }
                }
                if (child != null) {
                    contents.add(child);
                }
            }
        }
        if (containsSpecials) {
            return new ConditionalBlock(contents);
        }
        Expression block = Block.makeBlock(contents);
        if (block.getLocation() == null) {
            block.setLocation(locationId);
        }
        if (block.getLocalRetainedStaticContext() == null) {
            block.setRetainedStaticContext(makeRetainedStaticContext());
        }
        return block;
    }

    /**
     * Compile a content value text node.
     *
     * @param node     the text node potentially containing the template
     * @param contents a list to which expressions representing the fixed and variable parts of the content template
     *                 will be appended
     */

    void compileContentValueTemplate(TextImpl node, List<Expression> contents) {
        if (node instanceof TextValueTemplateNode) {
            Expression exp = ((TextValueTemplateNode) node).getContentExpression();
            if (getConfiguration().getBooleanProperty(Feature.STRICT_STREAMABILITY) && !(exp instanceof Literal)) {
                exp = new SequenceInstr(exp);
            }
            contents.add(exp);
        } else {
            contents.add(new StringLiteral(node.getStringValue()));
        }
    }


    /**
     * Set location information on a compiled instruction
     *
     * @param source the parent element
     * @param child  the compiled expression tree for the instruction to be traced
     */

    static void setInstructionLocation(StyleElement source, Expression child) {
        if (child.getLocation() == null || child.getLocation() == Loc.NONE) {
            child.setLocation(source.saveLocation());
        }
    }

    /**
     * Perform fallback processing. Generate fallback code for an extension
     * instruction that is not recognized by the implementation.
     *
     * @param exec        the Executable
     * @param decl        the Declaration of the top-level element containing the extension instruction
     * @param instruction The unknown extension instruction
     * @return the expression tree representing the fallback code
     * @throws net.sf.saxon.trans.XPathException if any error occurs
     */

    Expression fallbackProcessing(Compilation exec, ComponentDeclaration decl, StyleElement instruction)
            throws XPathException {
        // process any xsl:fallback children; if there are none,
        // generate code to report the original failure reason
        Expression fallback = null;
        for (NodeInfo child : children(XSLFallback.class::isInstance)) {
            Expression b = ((XSLFallback) child).compileSequenceConstructor(exec, decl, true);
            if (b == null) {
                b = Literal.makeEmptySequence();
            }
            if (fallback == null) {
                fallback = b;
            } else {
                fallback = Block.makeBlock(fallback, b);
                fallback.setLocation(allocateLocation());
            }
        }
        if (fallback != null) {
            return fallback;
        } else {
            return new ErrorExpression(instruction.validationError);
        }

    }

    /**
     * Allocate a location
     *
     * @return an location which can be used to report the location of the instruction
     */

    protected Location allocateLocation() {
        if (savedLocation == null) {
            savedLocation = new Loc(this);
        }
        return savedLocation;
    }

    /**
     * Construct sort keys for a SortedIterator
     *
     * @param compilation the compilation episode
     * @param decl        the declaration containing the sort keys  @throws XPathException if any error is detected
     * @return an array of SortKeyDefinition objects if there are any sort keys;
     * or null if there are none.
     * @throws XPathException if an error is found
     */

    SortKeyDefinitionList makeSortKeys(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // handle sort keys if any
        int numberOfSortKeys = 0;
        for (NodeInfo child : children(XSLSortOrMergeKey.class::isInstance)) {
            ((XSLSortOrMergeKey) child).compile(compilation, decl);
            if (child instanceof XSLSort) {
                if (numberOfSortKeys != 0 && ((XSLSort) child).getStable() != null) {
                    compileError("stable attribute may appear only on the first xsl:sort element", "XTSE1017");
                }
            }
            numberOfSortKeys++;
        }

        if (numberOfSortKeys > 0) {
            SortKeyDefinition[] keys = new SortKeyDefinition[numberOfSortKeys];
            int k = 0;
            for (NodeInfo child : children(XSLSortOrMergeKey.class::isInstance)) {
                keys[k++] = (SortKeyDefinition) ((XSLSortOrMergeKey) child).getSortKeyDefinition().simplify();
            }
            return new SortKeyDefinitionList(keys);

        } else {
            return null;
        }
    }

    /**
     * Get the list of attribute-set names associated with this element.
     * This is used for xsl:element, xsl:copy, xsl:attribute-set, and on literal
     * result elements
     *
     * @param use the original value of the [xsl:]use-attribute-sets attribute
     * @return an array of names of the attribute sets
     */

    StructuredQName[] getUsedAttributeSets(String use) {

        List<StructuredQName> nameList = new ArrayList<>(4);
        StringTokenizer st = new StringTokenizer(use, " \t\n\r", false);
        while (st.hasMoreTokens()) {
            String asetname = st.nextToken();
            StructuredQName name = makeQName(asetname, "XTSE0710", "use-attribute-sets");
            nameList.add(name);
        }
        return nameList.toArray(new StructuredQName[0]);
    }

    /**
     * Process the value of the visibility attribute (XSLT 3.0). Invokes
     * {@link #invalidAttribute(String, String)} if the value is invalid.
     *
     * @param s     the value of the attribute after whitespace collapsing
     * @param flags contains "h" if the value "hidden" is allowed, "a" if the value "absent" is allowed
     * @return the corresponding visibility
     */

    Visibility interpretVisibilityValue(String s, String flags) {
        for (Visibility v : Visibility.values()) {
            if (v.show().equals(s) &&
                    (flags.contains("h") || !s.equals("hidden")) &&
                    (flags.contains("a") || !s.equals("absent"))) {
                return v;
            }
        }
        invalidAttribute("visibility", "public|final|private|abstract" +
                (flags.contains("h") ? "|hidden" : "") +
                (flags.contains("a") ? "|absent" : "")
        );
        return null;
    }

    /**
     * Get the list of xsl:with-param elements for a calling element (apply-templates,
     * call-template, apply-imports, next-match). This method can be used to get either
     * the tunnel parameters, or the non-tunnel parameters.
     *
     * @param compilation the compilation episode
     * @param decl        the containing stylesheet declaration
     * @param tunnel      true if the tunnel="yes" parameters are wanted, false to get
     * @return an array of WithParam objects for either the ordinary parameters
     * or the tunnel parameters, as an array containing the results of
     * compiling the xsl:with-param children of this instruction (if any)
     * @throws XPathException if any error is detected
     */

    public WithParam[] getWithParamInstructions(Expression parent, Compilation compilation,
                                                ComponentDeclaration decl, boolean tunnel)
            throws XPathException {
        int count = 0;
        for (NodeInfo child : children(XSLWithParam.class::isInstance)) {
            XSLWithParam wp = (XSLWithParam) child;
            if (wp.getSourceBinding().hasProperty(SourceBinding.BindingProperty.TUNNEL) == tunnel) {
                count++;
            }
        }
        if (count == 0) {
            return WithParam.EMPTY_ARRAY;
        }
        WithParam[] array = new WithParam[count];
        count = 0;
        for (NodeInfo child : children(XSLWithParam.class::isInstance)) {
            XSLWithParam wp = (XSLWithParam) child;
            if (wp.getSourceBinding().hasProperty(SourceBinding.BindingProperty.TUNNEL) == tunnel) {
                WithParam p = wp.compileWithParam(parent, compilation, decl);
                if (wp.getParent() instanceof XSLNextIteration && wp.hasChildNodes()) {
                    // Type-check against the declared type of the xsl:param, unless this was done earlier
                    SequenceType required = ((XSLNextIteration) wp.getParent()).getDeclaredParamType(
                            wp.getSourceBinding().getVariableQName());
                    wp.checkAgainstRequiredType(required);
                    p.getSelectOperand().setChildExpression(wp.sourceBinding.getSelectExpression());
                }
                array[count++] = p;
            }
        }
        return array;
    }

    /**
     * Report an error with diagnostic information
     *
     * @param error contains information about the error
     */

    public void compileError(XmlProcessingError error) {
        XmlProcessingIncident.maybeSetHostLanguage(error, HostLanguage.XSLT);
        // Set the location of the error if there is no current location information,
        // or if the current location information is local to an XPath expression, unless we are
        // positioned on an xsl:function or xsl:template, in which case this would lose too much information
        if (error.getLocation() == null ||
                ((error.getLocation() instanceof Loc ||
                          error.getLocation() instanceof Expression) && !(this instanceof StylesheetComponent))) {
            XmlProcessingIncident.maybeSetLocation(error, this);
        }
        getCompilation().reportError(error);
    }

    public void compileError(XPathException err) {
        if (err.getLocator() == null) {
            err.setLocation(this);
        }
        XmlProcessingIncident se = new XmlProcessingIncident(err.getMessage(), err.getErrorCodeLocalPart(), err.getLocator());
        se.setHostLanguage(HostLanguage.XSLT);
        compileError(se);
    }
    /**
     * Report a static error in the stylesheet
     *
     * @param message the error message
     */

    public void compileError(String message) {
        compileError(message, "XTSE0010");
    }

    /**
     * Compile time error, specifying an error code
     *
     * @param message   the error message
     * @param errorCode the error code. May be null if not known or not defined
     */

    public void compileError(String message, StructuredQName errorCode) {
        XmlProcessingIncident error = new XmlProcessingIncident(message, errorCode.getEQName(), this);
        error.setHostLanguage(HostLanguage.XSLT);
        compileError(error);
    }

    /**
     * Compile time error, specifying an error code
     *
     * @param message   the error message
     * @param errorCode the error code. May be null if not known or not defined
     */

    public void compileError(String message, String errorCode) {
        compileError(new XPathException(message, errorCode, this));
    }

    public void compileError(String message, String errorCode, Location loc) {
        compileError(new XPathException(message, errorCode, loc));
    }

    /**
     * Compile time error, specifying an error code and the name of the attribute that
     * is in error.
     *
     * @param message       the error message
     * @param errorCode     the error code. May be null if not known or not defined
     * @param attributeName the name of the attribute. For attributes in no namespace
     *                      this is the local part of the name; for namespaced attributes
     *                      a name in Clark format may be supplied.
     */

    public void compileErrorInAttribute(String message, String errorCode, String attributeName) {
        StructuredQName att = StructuredQName.fromClarkName(attributeName);
        Location location = new AttributeLocation(this, att);
        compileError(new XPathException(message, errorCode, location));
    }

    protected void invalidAttribute(String attributeName, String allowedValues) {
        compileErrorInAttribute("Attribute " + getDisplayName() + "/@" + attributeName + " must be " + allowedValues,
                                "XTSE0020", attributeName);
    }

    /**
     * Ask whether XSLT syntax extensions are allowed, for example xsl:when/@select. Returns true if
     * explicitly enabled in the configuration. Note, this is not affected by forwards-compatibility mode
     * (because it's too error-prone to simply ignore the presence of these attributes).
     */
    protected void requireSyntaxExtensions(String attributeName) {
        if (!getConfiguration().getBooleanProperty(Feature.ALLOW_SYNTAX_EXTENSIONS)) {
            compileErrorInAttribute("Attribute " + getDisplayName() + "/@" + attributeName + " is allowed only if syntax extensions are enabled",
                                    "XTSE0020", attributeName);
        }
    }

    void undeclaredNamespaceError(String prefix, String errorCode, String attributeName) {
        if (errorCode == null) {
            errorCode = "XTSE0280";
        }
        compileErrorInAttribute("Undeclared namespace prefix " + Err.wrap(prefix), errorCode, attributeName);
    }

    public void compileWarning(String message, StructuredQName errorCode) {
        getCompilation().reportWarning(message, errorCode.getEQName(), this);
    }

    public void compileWarning(String message, String errorCode) {
        getCompilation().reportWarning(message, errorCode, this);
    }

    public void compileWarning(String message, String errorCode, Location location) {
        getCompilation().reportWarning(message, errorCode, location);
    }

    /**
     * Report a warning to the error listener
     *
     * @param error an exception containing the warning text
     */

    protected void issueWarning(XPathException error) {
        if (error.getLocator() == null) {
            error.setLocator(this);
        }
        getCompilation().reportWarning(error);
    }

    /**
     * Report a warning to the error listener
     *
     * @param message the warning message text
     * @param locator the location of the problem in the source stylesheet
     */

    protected void issueWarning(String message, SourceLocator locator) {
        XPathException tce = new XPathException(message);
        if (locator == null) {
            tce.setLocator(this);
        } else {
            tce.setLocator(locator);
        }
        issueWarning(tce);
    }

    /**
     * Test whether this is a top-level element
     *
     * @return true if the element is a child of the xsl:stylesheet or xsl:package element
     */

    public boolean isTopLevel() {
        return getParent() instanceof XSLModuleRoot;
    }

    /**
     * Ask whether this is an instruction that is known to be constructing nodes which
     * will become children of a parent document or element node, and will not have an
     * independent existence of their own.
     *
     * @return true if it is known that this is an instruction that creates nodes that
     * will immediately be attached to a parent element or document node
     */

    boolean isConstructingComplexContent() {
        if (!isInstruction()) {
            return false;
        }
        NodeInfo parent = getParent();
        while (true) {
            if (!(parent instanceof StyleElement && ((StyleElement) parent).isInstruction())) {
                return false;
            }
            if (parent instanceof XSLGeneralVariable) {
                return ((XSLGeneralVariable) parent).getAttributeValue("as") == null;
            }
            if (parent instanceof XSLElement || parent instanceof LiteralResultElement || parent instanceof XSLDocument || parent instanceof XSLCopy) {
                return true;
            }
            parent = parent.getParent();
        }
    }

    /**
     * Ask whether this element contains a binding for a variable with a given name; and if it does,
     * return the source binding information
     *
     * @param name the variable name
     * @return the binding information if this element binds a variable of this name; otherwise null
     */

    public SourceBinding getBindingInformation(StructuredQName name) {
        return null;
    }

    /**
     * Bind a variable used in this element to the compiled form of the XSLVariable element in which it is
     * declared
     *
     * @param qName The name of the variable
     * @return the XSLVariableDeclaration (that is, an xsl:variable or xsl:param instruction) for the variable,
     * or null if no declaration of the variable can be found
     */

    public SourceBinding bindVariable(StructuredQName qName) {

        SourceBinding decl = bindLocalVariable(qName);
        if (decl != null) {
            return decl;
        }

        // Now check for a global variable
        // we rely on the search following the order of decreasing import precedence.
        SourceBinding binding = getPrincipalStylesheetModule().getGlobalVariableBinding(qName);
        if (binding == null || Navigator.isAncestorOrSelf(binding.getSourceElement(), this)) {
            // test case variable-0118
            return null;
        } else {
            return binding;
        }
    }

    /**
     * Bind a variable reference used in this element to the compiled form of the XSLVariable element in which it is
     * declared, considering only local variables and params
     *
     * @param qName The name of the variable
     * @return the XSLVariableDeclaration (that is, an xsl:variable or xsl:param instruction) for the variable,
     * or null if no local declaration of the variable can be found
     */

    public SourceBinding bindLocalVariable(StructuredQName qName) {
        NodeInfo curr = this;
        NodeInfo prev = this;

        SourceBinding implicit = hasImplicitBinding(qName);
        if (implicit != null) {
            return implicit;
        }

        // first search for a local variable declaration
        if (!isTopLevel()) {
            while (curr instanceof StyleElement && !((StyleElement) curr).seesAvuncularVariables()) {
                // a local variable is not visible within a sibling xsl:fallback or xsl:catch element
                curr = curr.getParent();
            }
            AxisIterator preceding = curr.iterateAxis(AxisInfo.PRECEDING_SIBLING);
            while (true) {
                curr = preceding.next();
                while (curr == null) {
                    curr = prev.getParent();
                    if (curr instanceof StyleElement) {
                        implicit = ((StyleElement) curr).hasImplicitBinding(qName);
                        if (implicit != null) {
                            return implicit;
                        }
                    }
                    while (curr instanceof StyleElement && !((StyleElement) curr).seesAvuncularVariables()) {
                        // a local variable is not visible within a sibling xsl:fallback or xsl:catch element
                        curr = curr.getParent();
                    }
                    prev = curr;
                    if (curr.getParent() instanceof XSLModuleRoot) {
                        break;   // top level
                    }
                    preceding = curr.iterateAxis(AxisInfo.PRECEDING_SIBLING);
                    curr = preceding.next();
                }
                if (curr.getParent() instanceof XSLModuleRoot) {
                    break;
                }
                if (curr instanceof XSLGeneralVariable) {
                    SourceBinding sourceBinding = ((XSLGeneralVariable) curr).getBindingInformation(qName);
                    if (sourceBinding != null) {
                        return sourceBinding;
                    }
                }

            }
        }
        return null;
    }

    /**
     * Ask whether variables declared in an "uncle" element are visible.
     *
     * @return true for all elements except xsl:fallback and xsl:catch
     */

    protected boolean seesAvuncularVariables() {
        return true;
    }

    /**
     * Ask whether this particular element implicitly binds a given variable (used for xsl:accumulator-rule)
     */

    protected SourceBinding hasImplicitBinding(StructuredQName name) {
        return null;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * If there is no name, the value will be null.
     *
     * @return the name of the object declared in this element, if any
     */

    public StructuredQName getObjectName() {
        return objectName;
    }

    /**
     * Set the object name, for example the name of a function, variable, or template declared on this element
     *
     * @param qName the object name as a QName
     */

    public void setObjectName(StructuredQName qName) {
        objectName = qName;
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property.
     */

    public Iterator<String> getProperties() {
        List<String> list = new ArrayList<>(10);
        for (AttributeInfo att : attributes()) {
            list.add(att.getNodeName().getStructuredQName().getClarkName());
        }
        return list.iterator();
    }

    /**
     * Ask if an action on this StyleElement has been completed
     *
     * @param action for example ACTION_VALIDATE
     * @return true if the action has already been performed
     */

    boolean isActionCompleted(int action) {
        return (actionsCompleted & action) != 0;
    }

    /**
     * Say that an action on this StyleElement has been completed
     *
     * @param action for example ACTION_VALIDATE
     */

    void setActionCompleted(int action) {
        actionsCompleted |= action;
    }

}

