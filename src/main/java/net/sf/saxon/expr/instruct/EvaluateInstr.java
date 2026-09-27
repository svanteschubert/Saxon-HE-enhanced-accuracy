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
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.LFUCache;
import net.sf.saxon.functions.ExecutableFunctionLibrary;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.ma.map.EmptyMap;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.str.Latin1;
import net.sf.saxon.style.PublicStylesheetFunctionLibrary;
import net.sf.saxon.style.StylesheetFunctionLibrary;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.sxpath.XPathVariable;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Schema;
import net.sf.saxon.value.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;


/**
 * An EvaluateInstr is the compiled form of an xsl:evaluate instruction
 * <p>The implementation maintains a cache of compiled expressions, provided that the namespace context
 * is not constructed dynamically. The cache is an LRU cache with a fixed size of 100 entries. This is
 * to avoid uncontrolled use of memory when the XPath expressions are completely dynamic and each one
 * is unique.</p>
 */

public final class EvaluateInstr extends Expression {

    private Operand xpathOp;
    private final SequenceType requiredType;
    private Operand contextItemOp;
    private Operand baseUriOp;
    private Operand namespaceContextOp;
    private Operand schemaAwareOp;
    private Operand optionsOp;
    private WithParam[] actualParams;
    private Operand dynamicParamsOp;
    private NamespaceUri defaultXPathNamespace = null;

    private final static StringValue SV_DEFAULT_COLLATION = new StringValue(Latin1.of("default-collation"));
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

    public void setDefaultXPathNamespace(NamespaceUri defaultXPathNamespace) {
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
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
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
        return makeElaborator().elaborateForPull().iterate(context);
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
        if (defaultXPathNamespace != null) {
            out.emitAttribute("dxns", defaultXPathNamespace.toString());
        }
        String schemaRole = getRetainedStaticContext().getImportedSchemaRoleName();
        if (!schemaRole.isEmpty()) {
            out.emitAttribute("schemaRole", schemaRole);
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

    /**
     * Make an elaborator for this expression
     *
     * @return an appropriate {@link Elaborator}
     */
    @Override
    public Elaborator getElaborator() {
        return new EvaluateInstrElaborator();
    }

    private record CacheKey (
            String exprText, String baseUri, boolean schemaAware, Optional<NodeInfo> namespaceContext) {}

    private record CacheValue (
            Expression expr, SlotManager slotManager, Collection<XPathVariable> variables) {}


    private static class EvaluateInstrElaborator extends PullElaborator {

        @Override
        public PullEvaluator elaborateForPull() {
            EvaluateInstr instr = (EvaluateInstr) getExpression();
            StringEvaluator exprTextEval = instr.getXpath().makeElaborator().elaborateForString(false);
            StringEvaluator baseUriEval = instr.getBaseUriExpr() == null
                    ? null : instr.getBaseUriExpr().makeElaborator().elaborateForString(false);
            ItemEvaluator contextItemEval = instr.getContextItemExpr().makeElaborator().elaborateForItem();
            ItemEvaluator namespaceContextEval = instr.getNamespaceContextExpr() == null
                    ? null : instr.getNamespaceContextExpr().makeElaborator().elaborateForItem();
            StringEvaluator schemaAwareEval = instr.getSchemaAwareExpr().makeElaborator().elaborateForString(false);
            ItemEvaluator dynamicParamsEval = instr.getDynamicParams() == null
                    ? null : instr.getDynamicParams().makeElaborator().elaborateForItem();
            ItemEvaluator optionsEval = instr.optionsOp == null ? null : instr.optionsOp.getChildExpression().makeElaborator().elaborateForItem();
            return context -> {
                Configuration config = context.getConfiguration();
                if (config.getBooleanProperty(Feature.DISABLE_XSL_EVALUATE)) {
                    throw new XPathException("xsl:evaluate has been disabled", "XTDE3175");
                }

                final String exprText = exprTextEval.eval(context);
                String baseUri =
                        baseUriEval == null ? instr.getStaticBaseURIString() : Whitespace.trim(baseUriEval.eval(context));

                Item focus = contextItemEval.eval(context);

                NodeInfo namespaceContextBase = null;
                if (namespaceContextEval != null) {
                    namespaceContextBase = (NodeInfo) namespaceContextEval.eval(context);
                }

                String schemaAwareAttr = Whitespace.trim(schemaAwareEval.eval(context));
                boolean isSchemaAware;
                if ("yes".equals(schemaAwareAttr) || "true".equals(schemaAwareAttr) || "1".equals(schemaAwareAttr)) {
                    isSchemaAware = true;
                } else if ("no".equals(schemaAwareAttr) || "false".equals(schemaAwareAttr) || "0".equals(schemaAwareAttr)) {
                    isSchemaAware = false;
                } else {
                    throw new XPathException("The schema-aware attribute of xsl:evaluate must be yes|no|true|false|0|1")
                            .withErrorCode("XTDE0030")
                            .withLocation(instr.getLocation())
                            .withXPathContext(context);
                }

                Expression expr = null;
                SlotManager slotMap = null;

                // Create a cache key so the compiled expression can be reused

                Optional<NodeInfo> namespaceContext = Optional.ofNullable(namespaceContextBase);
                CacheKey cacheKey = new CacheKey(exprText,
                                                 baseUri,
                                                 isSchemaAware,
                                                 namespaceContext);

//                StringBuilder fsb = new StringBuilder(exprText.length() + (baseUri == null ? 4 : baseUri.length()) + 40);
//                fsb.append(baseUri);
//                fsb.append("##");
//                fsb.append(schemaAwareAttr);
//                fsb.append("##");
//                fsb.append(exprText);
//                if (namespaceContextBase != null) {
//                    fsb.append("##");
//                    namespaceContextBase.generateId(fsb);
//                }
//                String cacheKey = fsb.toString();
                Collection<XPathVariable> declaredVars = null;

                Controller controller = context.getController();
                LFUCache<CacheKey, CacheValue> cache;
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized(controller) {
                    cache = (LFUCache<CacheKey, CacheValue>) controller.getUserData(instr.getLocation(), "xsl:evaluate");
                    if (cache == null) {
                        cache = new LFUCache<>(100);
                        controller.setUserData(instr.getLocation(), "xsl:evaluate", cache);
                    } else {
                        CacheValue o = cache.get(cacheKey);
                        if (o != null) {
                            expr = o.expr();
                            slotMap = o.slotManager();
                            declaredVars = o.variables();
                        }
                    }
                }

                MapItem dynamicParams = null;
                if (dynamicParamsEval != null) {
                    dynamicParams = (MapItem) dynamicParamsEval.eval(context);
                }

                if (expr == null) {

                    // Expression needs to be compiled. First create the static context...

                    int version = instr.getRetainedStaticContext().getPackageData().getHostLanguageVersion();
//                    if (version == 30) {
//                        version = 31;
//                    }
                    MapItem options = (optionsEval == null ? EmptyMap.INSTANCE_40 : (MapItem) optionsEval.eval(context));

                    IndependentContext env = new IndependentContext(config);
                    env.setWarningHandler((str, loc) -> {
                        String message = "In dynamic expression {" + exprText + "}: " + str;
                        context.getController().warning(message, null, loc);
                    });
                    env.setBaseURI(baseUri);
                    env.setExecutable(context.getController().getExecutable());
                    env.setXPathLanguageLevel(version == 40 ? 40 : config.getConfigurationProperty(Feature.XPATH_VERSION_FOR_XSLT));
                    env.setDefaultCollationName(instr.getRetainedStaticContext().getDefaultCollationName());
                    if (namespaceContextEval != null) {
                        env.setNamespaces(namespaceContextBase);
                    } else {
                        env.setNamespaceResolver(instr.getRetainedStaticContext());
                        env.setDefaultElementNamespace(instr.getRetainedStaticContext().getDefaultElementNamespace());
                    }
                    if (isSchemaAware) {
                        Schema schema = instr.getRetainedStaticContext().getImportedSchema();
                        env.setImportedSchema(schema);
                        env.getPackageData().setImportedSchema("", schema);
                    }
                    // Copy the function library list, except for XSLT-defined system functions and private user-written functions
                    FunctionLibraryList libraryList0 = ((StylesheetPackage) instr.getRetainedStaticContext().getPackageData()).getFunctionLibrary();
                    FunctionLibraryList libraryList1 = new FunctionLibraryList();
                    for (FunctionLibrary lib : libraryList0.getLibraryList()) {
                        if (lib instanceof BuiltInFunctionSet && ((BuiltInFunctionSet) lib).getNamespace().equals(NamespaceUri.FN)) {
                            // Exclude XSLT-defined functions
                            libraryList1.addFunctionLibrary(config.getXPathFunctionSet(version == 40 ? 40 : 31));   // see bug 6221
                        } else if (lib instanceof StylesheetFunctionLibrary || lib instanceof ExecutableFunctionLibrary) {
                            libraryList1.addFunctionLibrary(new PublicStylesheetFunctionLibrary(lib));
                        } else {
                            libraryList1.addFunctionLibrary(lib);
                        }
                    }
                    env.setFunctionLibrary(libraryList1);
                    env.setDecimalFormatManager(instr.getRetainedStaticContext().getDecimalFormatManager());

                    GroundedValue defaultCollation = options.get(SV_DEFAULT_COLLATION);
                    if (defaultCollation != null) {
                        env.setDefaultCollationName(defaultCollation.head().getStringValue());
                    }

                    Map<StructuredQName, Integer> locals = new HashMap<>();
                    if (dynamicParams != null) {
                        SequenceTool.supply(dynamicParams.keys(), (ItemConsumer<? super Item>) paramName -> {
                            if (!(paramName instanceof QNameValue)) {
                                final AtomicType primitiveItemType = ((AtomicValue) paramName).getPrimitiveType();
                                XPathException err = new XPathException(
                                        "Parameter names supplied to xsl:evaluate must have type xs:QName, not " +
                                                primitiveItemType.getDisplayName(), "XTTE3165");
                                err.setIsTypeError(true);
                                throw err;
                            }
                            XPathVariable var = env.declareVariable((QNameValue) paramName);
                            locals.put(((QNameValue) paramName).getStructuredQName(), var.getLocalSlotNumber());
                        });
                    }

                    if (instr.getActualParams() != null) {
                        for (WithParam actualParam : instr.getActualParams()) {
                            StructuredQName name = actualParam.getVariableQName();
                            if (!locals.containsKey(name)) {
                                XPathVariable var = env.declareVariable(name);
                                locals.put(name, var.getLocalSlotNumber());
                            }
                        }
                    }

                    // Now compile the expression

                    try {
                        expr = ExpressionTool.make(exprText, env, 0, Tokenizer.END_OF_INPUT, null);
                    } catch (XPathException e) {
                        throw new XPathException("Static error in XPath expression supplied to xsl:evaluate: " +
                                                                        e.getMessage() + ". Expression: {" + exprText + "}")
                                .withErrorCode("XTDE3160")
                                .withLocation(instr.getLocation());
                    }

                    // Type check, and allocate slots for variables

                    expr.setRetainedStaticContext(env.makeRetainedStaticContext());
                    Supplier<RoleDiagnostic> role =
                            () -> new RoleDiagnostic(RoleDiagnostic.EVALUATE_RESULT, exprText, 0);
                    ExpressionVisitor visitor = ExpressionVisitor.make(env);
                    TypeChecker tc = config.getTypeChecker(false);
                    expr = tc.staticTypeCheck(expr, instr.requiredType, role, visitor);
                    expr = ExpressionTool.resolveCallsToCurrentFunction(expr);
                    ContextItemStaticInfo cit;
                    if (instr.getContextItemExpr() != null) {
                        boolean allowsEmpty = Cardinality.allowsZero(instr.getContextItemExpr().getCardinality());
                        cit = config.makeContextItemStaticInfo(
                                instr.getContextItemExpr().getItemType(),
                                allowsEmpty ? Optionality.OPTIONAL : Optionality.REQUIRED);
                    } else {
                        cit = ContextItemStaticInfo.ABSENT;
                    }
                    expr = expr.typeCheck(visitor, cit).optimize(visitor, cit);
                    slotMap = env.getStackFrameMap();
                    ExpressionTool.allocateSlots(expr, slotMap.getNumberOfVariables(), slotMap);
                    //expr.setContainer(env);

                    // Save the compiled expression in the cache for next time

                    //if (cacheKey != null) {
                        declaredVars = env.getDeclaredVariables();
                        cache.put(cacheKey, new CacheValue(expr, slotMap, declaredVars));
                        //System.err.println("Cache miss, size = " + cache.size());
                    //}
                }

                XPathContextMajor c2 = context.newContext();
                if (focus == null) {
                    c2.setCurrentIterator(null);
                } else {
                    ManualIterator mono = new ManualIterator(focus);
                    c2.setCurrentIterator(mono);
                }
                c2.openStackFrame(slotMap);

                if (instr.getActualParams() != null) {
                    for (int i = 0; i < instr.getActualParams().length; i++) {
                        final StructuredQName variableQName = instr.getActualParams()[i].getVariableQName();
                        if (dynamicParams != null && dynamicParams.get(new QNameValue(variableQName, BuiltInAtomicType.QNAME)) != null) {
                            // Don't evaluate xsl:with-param if there is a dynamic parameter of the same name
                            continue;
                        }
                        int slot = slotMap.getVariableMap().indexOf(variableQName);
                        c2.setLocalVariable(slot, instr.getActualParams()[i].getSelectValue(context));
                    }
                }

                if (dynamicParams != null) {
                    for (KeyValuePair kvp : dynamicParams.keyValuePairs()) {
                        QNameValue paramName = (QNameValue)  kvp.key();
                        int slot = slotMap.getVariableMap().indexOf(paramName.getStructuredQName());
                        if (slot >= 0) {
                            // can be false if the with-params changes from one call to the next
                            c2.setLocalVariable(slot, kvp.value());
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
                            !instr.isActualParam(name) &&
                            ExpressionTool.contains(expr, false, nameMatch)) {
                        throw new XPathException("No value has been supplied for variable " + name.getDisplayName(), "XPST0008");
                    }
                }
                try {
                    return expr.iterate(c2);
                } catch (XPathException err) {
                    throw err.withMessage("Dynamic error in expression {" + exprText + "} called using xsl:evaluate")
                            .withLocation(instr.getLocation());
                }
            };
        }
    }
}
