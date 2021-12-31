////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.LRUCache;
import net.sf.saxon.functions.ExecutableFunctionLibrary;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.functions.registry.XPath31FunctionSet;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.map.HashTrieMap;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.style.PublicStylesheetFunctionLibrary;
import net.sf.saxon.style.StylesheetFunctionLibrary;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.sxpath.XPathVariable;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.*;

import java.util.*;
import java.util.function.Predicate;


/**
 * An EvaluateInstr is the compiled form of an xsl:evaluate instruction
 * <p>The implementation maintains a cache of compiled expressions, provided that the namespace context
 * is not constructed dynamically. The cache is an LRU cache with a fixed size of 100 entries. This is
 * to avoid uncontrolled use of memory when the XPath expressions are completely dynamic and each one
 * is unique.</p>
 */

public final class EvaluateInstr extends Expression {

    private Operand xpathOp;
    private SequenceType requiredType;
    private Operand contextItemOp;
    private Operand baseUriOp;
    private Operand namespaceContextOp;
    private Operand schemaAwareOp;
    private Operand optionsOp;
    private Set<String> importedSchemaNamespaces;
    private WithParam[] actualParams;
    private Operand dynamicParamsOp;
    private String defaultXPathNamespace = null;


    /**
     * Create an xsl:evaluate instruction
     *
     * @param xpath                The expression that returns the string that is to be parsed as an XPath expression
     * @param requiredType         the required type of the result of evaluating the XPath expression
     * @param contextItemExpr      The expression that delivers the context item
     * @param baseUriExpr          the expression whose value is used as the static base URI of the XPath expression
     * @param namespaceContextExpr the expression that delivers a node whose namespace context provides the
     *                             static namespace context for the XPath expression
     * @param schemaAwareExpr      an expression whose value is true if the XPath expression is to be treated as schema
     *                             aware, or false otherwise
     */

    public EvaluateInstr(Expression xpath, SequenceType requiredType,
                         Expression contextItemExpr, Expression baseUriExpr,
                         Expression namespaceContextExpr, Expression schemaAwareExpr) {
        if (xpath != null) {
            xpathOp = new Operand(this, xpath, OperandRole.SINGLE_ATOMIC);
        }
        if (contextItemExpr != null) {
            contextItemOp = new Operand(this, contextItemExpr, OperandRole.NAVIGATE);
        }
        if (baseUriExpr != null) {
            baseUriOp = new Operand(this, baseUriExpr, OperandRole.SINGLE_ATOMIC);
        }
        if (namespaceContextExpr != null) {
            namespaceContextOp = new Operand(this, namespaceContextExpr, OperandRole.INSPECT);
        }
        if (schemaAwareExpr != null) {
            schemaAwareOp = new Operand(this, schemaAwareExpr, OperandRole.SINGLE_ATOMIC);
        }
        this.requiredType = requiredType;

    }

    public void setOptionsExpression(Expression options) {
        optionsOp = new Operand(this, options, OperandRole.ABSORB);
    }

    public void setActualParameters(WithParam[] params) {
        setActualParams(params);
    }

    public void setDefaultXPathNamespace(String defaultXPathNamespace) {
        this.defaultXPathNamespace = defaultXPathNamespace;
    }

    /**
     * Ask whether this expression is an instruction. In XSLT streamability analysis this
     * is used to distinguish constructs corresponding to XSLT instructions from other constructs,
     * typically XPath expressions.
     *
     * @return true (if this construct exists at all in an XSLT environment, then it represents an instruction)
     */
    @Override
    public boolean isInstruction() {
        return true;
    }


    /**
     * Add an imported schema namespace
     * @param ns the namespace to be imported ("" for the non-namespace)
     */

    public void importSchemaNamespace(String ns) {
        if (importedSchemaNamespaces == null) {
            importedSchemaNamespaces = new HashSet<>();
        }
        importedSchemaNamespaces.add(ns);
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        importedSchemaNamespaces = visitor.getStaticContext().getImportedSchemaNamespaces();
        typeCheckChildren(visitor, contextInfo);

        WithParam.typeCheck(getActualParams(), visitor, contextInfo);
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        optimizeChildren(visitor, contextItemType);
        return this;
    }


    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the data type
     */

    /*@NotNull*/
    @Override
    public final ItemType getItemType() {
        return requiredType.getPrimaryType();
    }

    @Override
    protected int computeCardinality() {
        return requiredType.getCardinality();
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
     * @param pathMapNodeSet the set of nodes in the path map that are affected
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        throw new UnsupportedOperationException("Cannot do document projection when xsl:evaluate is used");
    }
    
    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_FOCUS | StaticProperty.DEPENDS_ON_XSLT_CONTEXT; // assume the worst
    }

    @Override
    public Iterable<Operand> operands() {
        List<Operand> sub = new ArrayList<>(8);
        if (xpathOp != null) {
            sub.add(xpathOp);
        }
        if (contextItemOp != null) {
            sub.add(contextItemOp);
        }
        if (baseUriOp != null) {
            sub.add(baseUriOp);
        }
        if (namespaceContextOp != null) {
            sub.add(namespaceContextOp);
        }
        if (schemaAwareOp != null) {
            sub.add(schemaAwareOp);
        }
        if (dynamicParamsOp != null) {
            sub.add(dynamicParamsOp);
        }
        if (optionsOp != null) {
            sub.add(optionsOp);
        }
        WithParam.gatherOperands(this, getActualParams(), sub);
        return sub;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides the iterate() method natively.
     */

    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *                   that is used to update the bindings held in any
     *                   local variable references that are copied.
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        EvaluateInstr e2 = new EvaluateInstr(getXpath().copy(rebindings), requiredType,
                                             getContextItemExpr().copy(rebindings),
                                             getBaseUriExpr() == null ? null : getBaseUriExpr().copy(rebindings),
                                             getNamespaceContextExpr() == null ? null : getNamespaceContextExpr().copy(rebindings),
                                             getSchemaAwareExpr() == null ? null : getSchemaAwareExpr().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, e2);
        e2.setRetainedStaticContext(getRetainedStaticContext());
        e2.importedSchemaNamespaces = importedSchemaNamespaces;
        e2.setActualParams(WithParam.copy(e2, getActualParams(), rebindings));
        if (optionsOp != null) {
            e2.setOptionsExpression(optionsOp.getChildExpression().copy(rebindings));
        }
        if (dynamicParamsOp != null) {
            e2.setDynamicParams(dynamicParamsOp.getChildExpression().copy(rebindings));
        }
        return e2;
    }

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(final XPathContext context) throws XPathException {

        Configuration config = context.getConfiguration();
        if (config.getBooleanProperty(Feature.DISABLE_XSL_EVALUATE)) {
            throw new XPathException("xsl:evaluate has been disabled", "XTDE3175");
        }

        final String exprText = getXpath().evaluateAsString(context).toString();
        String baseUri =
                getBaseUriExpr() == null ? getStaticBaseURIString() : Whitespace.trim(getBaseUriExpr().evaluateAsString(context));

        Item focus = getContextItemExpr().evaluateItem(context);

        NodeInfo namespaceContextBase = null;
        if (getNamespaceContextExpr() != null) {
            namespaceContextBase = (NodeInfo) getNamespaceContextExpr().evaluateItem(context);
        }

        String schemaAwareAttr = Whitespace.trim(getSchemaAwareExpr().evaluateAsString(context));
        boolean isSchemaAware;
        if ("yes".equals(schemaAwareAttr)||"true".equals(schemaAwareAttr)||"1".equals(schemaAwareAttr)) {
            isSchemaAware = true;
        } else if ("no".equals(schemaAwareAttr)||"false".equals(schemaAwareAttr)||"0".equals(schemaAwareAttr)) {
            isSchemaAware = false;
        } else {
            XPathException err = new XPathException("The schema-aware attribute of xsl:evaluate must be yes|no|true|false|0|1");
            err.setErrorCode("XTDE0030");
            err.setLocation(getLocation());
            err.setXPathContext(context);
            throw err;
        }

        Expression expr = null;
        SlotManager slotMap = null;

        // Create a cache key so the compiled expression can be reused

        FastStringBuffer fsb = new FastStringBuffer(exprText.length() + (baseUri == null ? 4 : baseUri.length()) + 40);
        fsb.append(baseUri);
        fsb.append("##");
        fsb.append(schemaAwareAttr);
        fsb.append("##");
        fsb.append(exprText);
        if (namespaceContextBase != null) {
            fsb.append("##");
            namespaceContextBase.generateId(fsb);
        }
        String cacheKey = fsb.toString();
        Collection<XPathVariable> declaredVars = null;

        Controller controller = context.getController();
        LRUCache<String, Object[]> cache;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (controller) {
            cache = (LRUCache<String, Object[]>) controller.getUserData(this.getLocation(), "xsl:evaluate");
            if (cache == null) {
                cache = new LRUCache<>(100);
                controller.setUserData(this.getLocation(), "xsl:evaluate", cache);
            } else {
                Object[] o = cache.get(cacheKey);
                if (o != null) {
                    expr = (Expression) o[0];
                    slotMap = (SlotManager) o[1];
                    declaredVars = (Collection<XPathVariable>) o[2];
                }
            }
        }

        MapItem dynamicParams = null;
        if (dynamicParamsOp != null) {
            dynamicParams = (MapItem)dynamicParamsOp.getChildExpression().evaluateItem(context);
        }

        if (expr == null) {

            // Expression needs to be compiled. First create the static context...

            MapItem options = (optionsOp == null  ? new HashTrieMap() :(MapItem)optionsOp.getChildExpression().evaluateItem(context));

            IndependentContext env = new IndependentContext(config) {
                @Override
                public void issueWarning(String s, Location locator) {
                    String message = "In dynamic expression {" + exprText + "}: " + s;
                    context.getController().warning(message, null, getLocation());
                }
            };
            env.setBaseURI(baseUri);
            env.setExecutable(context.getController().getExecutable());
            env.setXPathLanguageLevel(31);
            env.setDefaultCollationName(getRetainedStaticContext().getDefaultCollationName());
            if (getNamespaceContextExpr() != null) {
                env.setNamespaces(namespaceContextBase);
            } else {
                env.setNamespaceResolver(getRetainedStaticContext());
                env.setDefaultElementNamespace(getRetainedStaticContext().getDefaultElementNamespace());
            }
            // Copy the function library list, except for XSLT-defined system functions and private user-written functions
            FunctionLibraryList libraryList0 = ((StylesheetPackage)getRetainedStaticContext().getPackageData()).getFunctionLibrary();
            FunctionLibraryList libraryList1 = new FunctionLibraryList();
            for (FunctionLibrary lib : libraryList0.getLibraryList()) {
                if (lib instanceof BuiltInFunctionSet && ((BuiltInFunctionSet)lib).getNamespace().equals(NamespaceConstant.FN)) {
                    // Exclude XSLT-defined functions
                    libraryList1.addFunctionLibrary(XPath31FunctionSet.getInstance());
                } else if (lib instanceof StylesheetFunctionLibrary || lib instanceof ExecutableFunctionLibrary) {
                    libraryList1.addFunctionLibrary(new PublicStylesheetFunctionLibrary(lib));
                } else {
                    libraryList1.addFunctionLibrary(lib);
                }
            }
            env.setFunctionLibrary(libraryList1);
            env.setDecimalFormatManager(getRetainedStaticContext().getDecimalFormatManager());
            env.setXPathLanguageLevel(config.getConfigurationProperty(Feature.XPATH_VERSION_FOR_XSLT));
            if (isSchemaAware) {
                GroundedValue allowAny = options.get(new StringValue("allow-any-namespace"));
                if (allowAny != null && allowAny.effectiveBooleanValue()) {
                    env.setImportedSchemaNamespaces(config.getImportedNamespaces());
                } else {
                    env.setImportedSchemaNamespaces(importedSchemaNamespaces);
                }
            }

            GroundedValue defaultCollation = options.get(new StringValue("default-collation"));
            if (defaultCollation != null) {
                env.setDefaultCollationName(defaultCollation.head().getStringValue());
            }

            Map<StructuredQName, Integer> locals = new HashMap<>();
            if (dynamicParams != null) {
                dynamicParams.keys().forEachOrFail(paramName -> {
                    if (!(paramName instanceof QNameValue)) {
                        XPathException err = new XPathException(
                                "Parameter names supplied to xsl:evaluate must have type xs:QName, not " +
                                        ((AtomicValue)paramName).getItemType().getPrimitiveItemType().getDisplayName(), "XTTE3165");
                        err.setIsTypeError(true);
                        throw err;
                    }
                    XPathVariable var = env.declareVariable((QNameValue) paramName);
                    locals.put(((QNameValue) paramName).getStructuredQName(), var.getLocalSlotNumber());
                });
            }

            if (getActualParams() != null) {
                for (WithParam actualParam : getActualParams()) {
                    StructuredQName name = actualParam.getVariableQName();
                    if (locals.get(name) == null) {
                        XPathVariable var = env.declareVariable(name);
                        locals.put(name, var.getLocalSlotNumber());
                    }
                }
            }

            // Now compile the expression

            try {
                expr = ExpressionTool.make(exprText, env, 0, Token.EOF, null);
            } catch (XPathException e) {
                XPathException err = new XPathException("Static error in XPath expression supplied to xsl:evaluate: " +
                        e.getMessage() + ". Expression: {" + exprText + "}");
                err.setErrorCode("XTDE3160");
                err.setLocation(getLocation());
                throw err;
            }

            // Type check, and allocate slots for variables

            expr.setRetainedStaticContext(env.makeRetainedStaticContext());
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.EVALUATE_RESULT, exprText, 0);
            ExpressionVisitor visitor = ExpressionVisitor.make(env);
            TypeChecker tc = config.getTypeChecker(false);
            expr = tc.staticTypeCheck(expr, requiredType, role, visitor);
            ItemType contextItemType = Type.ITEM_TYPE;
            expr = ExpressionTool.resolveCallsToCurrentFunction(expr);
            ContextItemStaticInfo cit = config.makeContextItemStaticInfo(contextItemType, context.getContextItem() == null);
            expr = expr.typeCheck(visitor, cit).optimize(visitor, cit);
            slotMap = env.getStackFrameMap();
            ExpressionTool.allocateSlots(expr, slotMap.getNumberOfVariables(), slotMap);
            //expr.setContainer(env);

            // Save the compiled expression in the cache for next time

            if (cacheKey != null) {
                declaredVars = env.getDeclaredVariables();
                cache.put(cacheKey, new Object[]{expr, slotMap, declaredVars});
                //System.err.println("Cache miss, size = " + cache.size());
            }
        }

        XPathContextMajor c2 = context.newContext();
        if (focus == null) {
            c2.setCurrentIterator(null);
        } else {
            ManualIterator mono = new ManualIterator(focus);
            c2.setCurrentIterator(mono);
        }
        c2.openStackFrame(slotMap);

        if (getActualParams() != null) {
            for (int i = 0; i < getActualParams().length; i++) {
                int slot = slotMap.getVariableMap().indexOf(getActualParams()[i].getVariableQName());
                c2.setLocalVariable(slot, getActualParams()[i].getSelectValue(context));
            }
        }

        if (dynamicParams != null) {
            AtomicIterator iter = dynamicParams.keys();
            QNameValue paramName;
            while ((paramName = (QNameValue) iter.next()) != null) {
                int slot = slotMap.getVariableMap().indexOf(paramName.getStructuredQName());
                if (slot >= 0) {
                    // can be false if the with-params changes from one call to the next
                    c2.setLocalVariable(slot, dynamicParams.get(paramName));
                }
            }
        }

        // Check that all required variables are present
        for (XPathVariable var : declaredVars) {
            final StructuredQName name = var.getVariableQName();
            Predicate<Expression> nameMatch = e ->
                    e instanceof LocalVariableReference &&
                        ((LocalVariableReference) e).getVariableName().equals(name) &&
                        ((LocalVariableReference) e).getBinding() instanceof XPathVariable;

            if (dynamicParams != null && dynamicParams.get(new QNameValue(name, BuiltInAtomicType.QNAME)) == null &&
                    !isActualParam(name) &&
                    ExpressionTool.contains(expr, false, nameMatch)) {
                throw new XPathException("No value has been supplied for variable " + name.getDisplayName(), "XPST0008");
            }
        }
        try {
            return expr.iterate(c2);
        } catch (XPathException err) {
            XPathException e2 = new XPathException("Dynamic error in expression {" + exprText + "} called using xsl:evaluate", err);
            e2.setLocation(getLocation());
            e2.setErrorCodeQName(err.getErrorCodeQName());
            throw e2;
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("evaluate", this);
        if (!SequenceType.ANY_SEQUENCE.equals(requiredType)) {
            out.emitAttribute("as", requiredType.toAlphaCode());
        }
        if (importedSchemaNamespaces != null && !importedSchemaNamespaces.isEmpty()) {
            FastStringBuffer buff = new FastStringBuffer(256);
            for (String s : importedSchemaNamespaces) {
                if (s.isEmpty()) {
                    s = "##";
                }
                buff.append(s);
                buff.cat(' ');
            }
            buff.setLength(buff.length() - 1);
            out.emitAttribute("schNS", buff.toString());
        }
        if(defaultXPathNamespace != null) {
            out.emitAttribute("dxns", defaultXPathNamespace);
        }
        out.setChildRole("xpath");
        getXpath().export(out);
        if (getContextItemExpr() != null) {
            out.setChildRole("cxt");
            getContextItemExpr().export(out);
        }
        if (getBaseUriExpr() != null) {
            out.setChildRole("baseUri");
            getBaseUriExpr().export(out);
        }
        if (getNamespaceContextExpr() != null) {
            out.setChildRole("nsCxt");
            getNamespaceContextExpr().export(out);
        }
        if (getSchemaAwareExpr() != null) {
            out.setChildRole("sa");
            getSchemaAwareExpr().export(out);
        }
        if (optionsOp != null) {
            out.setChildRole("options");
            optionsOp.getChildExpression().export(out);
        }
        WithParam.exportParameters(actualParams, out, false);
        if (dynamicParamsOp != null) {
            out.setChildRole("wp");
            getDynamicParams().export(out);
        }
        out.endElement();
    }


    public Expression getXpath() {
        return xpathOp.getChildExpression();
    }

    public void setXpath(Expression xpath) {
        if (xpathOp == null) {
            xpathOp = new Operand(this, xpath, OperandRole.SINGLE_ATOMIC);
        } else {
            xpathOp.setChildExpression(xpath);
        }
    }


    public Expression getContextItemExpr() {
        return contextItemOp == null ? null : contextItemOp.getChildExpression();
    }

    public void setContextItemExpr(Expression contextItemExpr) {
        if (contextItemOp == null) {
            contextItemOp = new Operand(this, contextItemExpr, OperandRole.NAVIGATE);
        } else {
            contextItemOp.setChildExpression(contextItemExpr);
        }
    }

    public Expression getBaseUriExpr() {
        return baseUriOp == null ? null : baseUriOp.getChildExpression();
    }

    public void setBaseUriExpr(Expression baseUriExpr) {
        if (baseUriOp == null) {
            baseUriOp = new Operand(this, baseUriExpr, OperandRole.SINGLE_ATOMIC);
        } else {
            baseUriOp.setChildExpression(baseUriExpr);
        }
    }

    public Expression getNamespaceContextExpr() {
        return namespaceContextOp == null ? null : namespaceContextOp.getChildExpression();
    }

    public void setNamespaceContextExpr(Expression namespaceContextExpr) {
        if (namespaceContextOp == null) {
            namespaceContextOp = new Operand(this, namespaceContextExpr, OperandRole.INSPECT);
        } else {
            namespaceContextOp.setChildExpression(namespaceContextExpr);
        }
    }

    public Expression getSchemaAwareExpr() {
        return schemaAwareOp == null ? null : schemaAwareOp.getChildExpression();
    }

    public void setSchemaAwareExpr(Expression schemaAwareExpr) {
        if (schemaAwareOp == null) {
            schemaAwareOp = new Operand(this, schemaAwareExpr, OperandRole.SINGLE_ATOMIC);
        } else {
            schemaAwareOp.setChildExpression(schemaAwareExpr);
        }
    }

    public WithParam[] getActualParams() {
        return actualParams;
    }

    public void setActualParams(WithParam[] actualParams) {
        this.actualParams = actualParams;
    }

    public boolean isActualParam(StructuredQName name) {
        for (WithParam wp : actualParams) {
            if (wp.getVariableQName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void setDynamicParams(Expression params) {
        if (dynamicParamsOp == null) {
            dynamicParamsOp = new Operand(this, params, OperandRole.SINGLE_ATOMIC);
        } else {
            dynamicParamsOp.setChildExpression(params);
        }
    }

    public Expression getDynamicParams() {
        return dynamicParamsOp.getChildExpression();
    }
}
