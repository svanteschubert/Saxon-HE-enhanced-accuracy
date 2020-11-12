////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.*;
import net.sf.saxon.functions.CurrentGroupCall;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

import java.net.URI;
import java.net.URISyntaxException;


/**
 * Handler for xsl:for-each-group elements in stylesheet. This is a new instruction
 * defined in XSLT 2.0
 */

public class ForEachGroup extends Instruction
        implements SortKeyEvaluator, ContextMappingFunction, ContextSwitchingExpression {

    public static final int GROUP_BY = 0;
    public static final int GROUP_ADJACENT = 1;
    public static final int GROUP_STARTING = 2;
    public static final int GROUP_ENDING = 3;

    private byte algorithm;
    private int keyItemType;
    private StringCollator collator = null;             // collation used for the grouping comparisons
    private transient AtomicComparer[] sortComparators = null;    // comparators used for sorting the groups
    private boolean composite = false;
    private boolean isInFork = false;

    private Operand selectOp;
    private Operand actionOp;
    private Operand keyOp;
    private Operand collationOp;
    private Operand sortKeysOp;


    /**
     * Create a for-each-group instruction
     * @param select                  the select expression (selects the population to be grouped)
     * @param action                  the body of the for-each-group (applied to each group in turn)
     * @param algorithm               one of group-by, group-adjacent, group-starting-with, group-ending-with
     * @param key                     expression to evaluate the grouping key
     * @param collator                user for comparing strings
     * @param collationNameExpression expression that yields the name of the collation to be used
     * @param sortKeys                list of xsl:sort keys for sorting the groups. May be null.
     */

    public ForEachGroup(Expression select,
                        Expression action,
                        byte algorithm,
                        Expression key,
                        StringCollator collator,
                        Expression collationNameExpression,
                        SortKeyDefinitionList sortKeys) {
        selectOp = new Operand(this, select, OperandRole.FOCUS_CONTROLLING_SELECT);
        actionOp = new Operand(this, action, OperandRole.FOCUS_CONTROLLED_ACTION);
        OperandRole keyRole = (algorithm == GROUP_ENDING || algorithm == GROUP_STARTING) ? OperandRole.PATTERN : OperandRole.NEW_FOCUS_ATOMIC;
        keyOp = new Operand(this, key, keyRole);
        if (collationNameExpression != null) {
            collationOp = new Operand(this, collationNameExpression, OperandRole.SINGLE_ATOMIC);
        }
        if (sortKeys != null) {
            sortKeysOp = new Operand(this, sortKeys, OperandRole.SINGLE_ATOMIC);
        }
        this.algorithm = algorithm;
        this.collator = collator;
        for (Operand o : operands()) {
            adoptChildExpression(o.getChildExpression());
        }
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     *
     * @return the name of the instruction
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_FOR_EACH_GROUP;
    }

    @Override
    public Iterable<Operand> operands() {
        return operandSparseList(selectOp, actionOp, keyOp, collationOp, sortKeysOp);
    }

    /**
     * Get the select expression
     *
     * @return the select expression
     */

    @Override
    public Expression getSelectExpression() {
        return selectOp.getChildExpression();
    }

    /**
     * Get the action expression (the content of the for-each-group)
     *
     * @return the body of the xsl:for-each-group instruction
     */

    @Override
    public Expression getActionExpression() {
        return actionOp.getChildExpression();
    }

    /**
     * Get the grouping algorithm (one of group-by, group-adjacent, group-starting-with, group-ending-with)
     *
     * @return one of group-by, group-adjacent, group-starting-with, group-ending-with
     */

    public byte getAlgorithm() {
        return algorithm;
    }

    /**
     * Get the grouping key expression expression (the group-by or group-adjacent expression, or a
     * PatternSponsor containing the group-starting-with or group-ending-with expression)
     *
     * @return the expression used to calculate grouping keys
     */

    public Expression getGroupingKey() {
        return keyOp.getChildExpression();
    }

    /**
     * Get the primitive item type of the key
     *
     * @return the primitive item type of the grouping key
     */

    public int getKeyItemType() {
        return keyItemType;
    }

    /**
     * Get the sort keys defined at the for-each-group level, that is, the keys for sorting the groups
     *
     * @return the definitions of the sort keys defined as children of the xsl:for-each-group element,
     * or null if there are no sort keys
     */

    public SortKeyDefinitionList getSortKeyDefinitions() {
        return sortKeysOp == null ? null : (SortKeyDefinitionList)sortKeysOp.getChildExpression();
    }

    /**
     * Get the statically-allocated sort key comparators for sorting at the group level, if known
     *
     * @return the comparators used for comparing sort key values, one entry in the array for each
     *         nested xsl:sort element
     */

    public AtomicComparer[] getSortKeyComparators() {
        return sortComparators;
    }

    /**
     * Get the statically-determined collator, or null if the collation was not determined statically
     *
     * @return the collation, if known statically, or null if not
     */

    /*@Nullable*/
    public StringCollator getCollation() {
        return collator;
    }

    /**
     * Get the static base URI of the instruction
     *
     * @return the static base URI if known, or null otherwise
     */

    /*@Nullable*/
    public URI getBaseURI() {
        try {
            return getRetainedStaticContext().getStaticBaseUri();
        } catch (XPathException err) {
            return null;
        }
    }

    public boolean isComposite() {
        return composite;
    }

    public void setComposite(boolean composite) {
        this.composite = composite;
    }

    public boolean isInFork() {
        return isInFork;
    }

    public void setIsInFork(boolean inFork) {
        isInFork = inFork;
    }

    /**
     * Ask whether common subexpressions found in the operands of this expression can
     * be extracted and evaluated outside the expression itself. The result is irrelevant
     * in the case of operands evaluated with a different focus, which will never be
     * extracted in this way, even if they have no focus dependency.
     *
     * @return false for this kind of expression
     */
    @Override
    public boolean allowExtractingCommonSubexpressions() {
        return false;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        selectOp.typeCheck(visitor, contextInfo);
        if (collationOp != null) {
            collationOp.typeCheck(visitor, contextInfo);
        }

        ItemType selectedItemType = getSelectExpression().getItemType();
        if (selectedItemType == ErrorType.getInstance()) {
            return Literal.makeEmptySequence();
        }

        for (Operand o : operands()) {
            fixupGroupReferences(this, this, selectedItemType, false);
        }

        ContextItemStaticInfo cit = visitor.getConfiguration().makeContextItemStaticInfo(selectedItemType, false);
        cit.setContextSettingExpression(getSelectExpression());

        actionOp.typeCheck(visitor, cit);
        keyOp.typeCheck(visitor, cit);

        if (Literal.isEmptySequence(getSelectExpression())) {
            return getSelectExpression();
        }
        if (Literal.isEmptySequence(getActionExpression())) {
            return getActionExpression();
        }
        if (getSortKeyDefinitions() != null) {

            boolean allFixed = true;
            for (SortKeyDefinition sk : getSortKeyDefinitions()) {
                Expression sortKey = sk.getSortKey();
                sortKey = sortKey.typeCheck(visitor, cit);
                if (sk.isBackwardsCompatible()) {
                    sortKey = FirstItemExpression.makeFirstItemExpression(sortKey);
                } else {
                    RoleDiagnostic role =
                            new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:sort/select", 0);
                    role.setErrorCode("XTTE1020");
                    sortKey = CardinalityChecker.makeCardinalityChecker(sortKey, StaticProperty.ALLOWS_ZERO_OR_ONE, role);
                }
                sk.setSortKey(sortKey, true);
                sk.typeCheck(visitor, contextInfo);
                if (sk.isFixed()) {
                    AtomicComparer comp = sk.makeComparator(
                            visitor.getStaticContext().makeEarlyEvaluationContext());
                    sk.setFinalComparator(comp);
                } else {
                    allFixed = false;
                }
            }
            if (allFixed) {
                sortComparators = new AtomicComparer[getSortKeyDefinitions().size()];
                for (int i = 0; i < getSortKeyDefinitions().size(); i++) {
                    sortComparators[i] = getSortKeyDefinitions().getSortKeyDefinition(i).getFinalComparator();
                }
            }
        }
        keyItemType = getGroupingKey().getItemType().getPrimitiveType();

        return this;
    }

    private static void fixupGroupReferences(Expression exp, ForEachGroup feg, ItemType selectedItemType, boolean isInLoop) {
        if (exp == null) {
            // no action
        } else if (exp instanceof CurrentGroupCall) {
            ((CurrentGroupCall) exp).setControllingInstruction(feg, selectedItemType, isInLoop);
        } else if (exp instanceof ForEachGroup) {
            // a current-group() reference to the outer for-each-group can occur in the select expression
            // or in the AVTs of a contained xsl:sort
            ForEachGroup feg2 = (ForEachGroup)exp;
            if (feg2 == feg) {
                fixupGroupReferences(feg2.getActionExpression(), feg, selectedItemType, false);
            } else {
                fixupGroupReferences(feg2.getSelectExpression(), feg, selectedItemType, isInLoop);
                fixupGroupReferences(feg2.getGroupingKey(), feg, selectedItemType, isInLoop);
                if (feg2.getSortKeyDefinitions() != null) {
                    for (SortKeyDefinition skd : feg2.getSortKeyDefinitions()) {
                        fixupGroupReferences(skd.getOrder(), feg, selectedItemType, isInLoop);
                        fixupGroupReferences(skd.getCaseOrder(), feg, selectedItemType, isInLoop);
                        fixupGroupReferences(skd.getDataTypeExpression(), feg, selectedItemType, isInLoop);
                        fixupGroupReferences(skd.getLanguage(), feg, selectedItemType, isInLoop);
                        fixupGroupReferences(skd.getCollationNameExpression(), feg, selectedItemType, isInLoop);
                        fixupGroupReferences(skd.getOrder(), feg, selectedItemType, isInLoop);
                    }
                }
            }

        } else {
            for (Operand o : exp.operands()) {
                fixupGroupReferences(o.getChildExpression(), feg, selectedItemType, isInLoop || o.isHigherOrder());
            }
        }
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        selectOp.optimize(visitor, contextItemType);
        ItemType selectedItemType = getSelectExpression().getItemType();
        ContextItemStaticInfo sit = visitor.getConfiguration().makeContextItemStaticInfo(selectedItemType, false);
        sit.setContextSettingExpression(getSelectExpression());
        actionOp.optimize(visitor, sit);
        keyOp.optimize(visitor, sit);

        if (Literal.isEmptySequence(getSelectExpression())) {
            return getSelectExpression();
        }
        if (Literal.isEmptySequence(getActionExpression())) {
            return getActionExpression();
        }
        // Optimize the sort key definitions
        if (getSortKeyDefinitions() != null) {
            for (SortKeyDefinition skd : getSortKeyDefinitions()) {
                Expression sortKey = skd.getSortKey();
                sortKey = sortKey.optimize(visitor, sit);
                skd.setSortKey(sortKey, true);
            }
        }
        if (collationOp != null) {
            collationOp.optimize(visitor, contextItemType);
        }
        if (collator == null && (getCollationNameExpression() instanceof StringLiteral)) {
            String collation = ((StringLiteral) getCollationNameExpression()).getStringValue();
            URI collationURI;
            try {
                collationURI = new URI(collation);
                if (!collationURI.isAbsolute()) {
                    collationURI = getStaticBaseURI().resolve(collationURI);
                    final String collationNameString = collationURI.toString();
                    setCollationNameExpression(new StringLiteral(collationNameString));
                    collator = visitor.getConfiguration().getCollation(collationNameString);
                    if (collator == null) {
                        XPathException err = new XPathException("Unknown collation " + Err.wrap(collationURI.toString(), Err.URI));
                        err.setErrorCode("XTDE1110");
                        err.setLocation(getLocation());
                        throw err;
                    }
                }
            } catch (URISyntaxException err) {
                XPathException e = new XPathException("Collation name '" + getCollationNameExpression() + "' is not a valid URI");
                e.setErrorCode("XTDE1110");
                e.setLocation(getLocation());
                throw e;
            }
        }
        return this;
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
        SortKeyDefinition[] newKeyDef = null;
        if (getSortKeyDefinitions() != null) {
            newKeyDef = new SortKeyDefinition[getSortKeyDefinitions().size()];
            for (int i = 0; i < getSortKeyDefinitions().size(); i++) {
                newKeyDef[i] = getSortKeyDefinitions().getSortKeyDefinition(i).copy(rebindings);
            }
        }
        ForEachGroup feg = new ForEachGroup(
                getSelectExpression().copy(rebindings),
                getActionExpression().copy(rebindings),
                algorithm,
                getGroupingKey().copy(rebindings),
                collator,
                getCollationNameExpression().copy(rebindings),
                newKeyDef == null ? null : new SortKeyDefinitionList(newKeyDef));
        ExpressionTool.copyLocationInfo(this, feg);
        feg.setComposite(isComposite());
        fixupGroupReferences(feg, feg, getSelectExpression().getItemType(), false);
        return feg;
    }


    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getActionExpression().getItemType();
    }

    /**
     * Compute the dependencies of an expression, as the union of the
     * dependencies of its subexpressions. (This is overridden for path expressions
     * and filter expressions, where the dependencies of a subexpression are not all
     * propogated). This method should be called only once, to compute the dependencies;
     * after that, getDependencies should be used.
     *
     * @return the depencies, as a bit-mask
     */

    @Override
    public int computeDependencies() {
        // some of the dependencies in the "action" part and in the grouping and sort keys aren't relevant,
        // because they don't depend on values set outside the for-each-group expression
        int dependencies = 0;
        dependencies |= getSelectExpression().getDependencies();
        dependencies |= getGroupingKey().getDependencies() & ~StaticProperty.DEPENDS_ON_FOCUS;
        dependencies |= getActionExpression().getDependencies()
                & ~(StaticProperty.DEPENDS_ON_FOCUS | StaticProperty.DEPENDS_ON_CURRENT_GROUP);
        if (getSortKeyDefinitions() != null) {
            for (SortKeyDefinition skd : getSortKeyDefinitions()) {
                dependencies |= skd.getSortKey().getDependencies() & ~StaticProperty.DEPENDS_ON_FOCUS;
                Expression e = skd.getCaseOrder();
                if (e != null && !(e instanceof Literal)) {
                    dependencies |= e.getDependencies();
                }
                e = skd.getDataTypeExpression();
                if (e != null && !(e instanceof Literal)) {
                    dependencies |= e.getDependencies();
                }
                e = skd.getLanguage();
                if (e != null && !(e instanceof Literal)) {
                    dependencies |= e.getDependencies();
                }
            }
        }
        if (getCollationNameExpression() != null) {
            dependencies |= getCollationNameExpression().getDependencies();
        }
        return dependencies;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */
    @Override
    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        p |= getActionExpression().getSpecialProperties() & StaticProperty.ALL_NODES_UNTYPED;
        return p;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if the "action" creates new nodes.
     * (Nodes created by the condition can't contribute to the result).
     */

    @Override
    public final boolean mayCreateNewNodes() {
        int props = getActionExpression().getSpecialProperties();
        return (props & StaticProperty.NO_NODES_NEWLY_CREATED) == 0;
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "ForEachGroup";
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
     * @param pathMapNodeSet the set of nodes within the path map
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        PathMap.PathMapNodeSet target = getSelectExpression().addToPathMap(pathMap, pathMapNodeSet);
        if (getCollationNameExpression() != null) {
            getCollationNameExpression().addToPathMap(pathMap, pathMapNodeSet);
        }
        if (getSortKeyDefinitions() != null) {
            for (SortKeyDefinition skd : getSortKeyDefinitions()) {
                skd.getSortKey().addToPathMap(pathMap, target);
                Expression e = skd.getOrder();
                if (e != null) {
                    e.addToPathMap(pathMap, pathMapNodeSet);
                }
                e = skd.getCaseOrder();
                if (e != null) {
                    e.addToPathMap(pathMap, pathMapNodeSet);
                }
                e = skd.getDataTypeExpression();
                if (e != null) {
                    e.addToPathMap(pathMap, pathMapNodeSet);
                }
                e = skd.getLanguage();
                if (e != null) {
                    e.addToPathMap(pathMap, pathMapNodeSet);
                }
                e = skd.getCollationNameExpression();
                if (e != null) {
                    e.addToPathMap(pathMap, pathMapNodeSet);
                }
            }
        }
        return getActionExpression().addToPathMap(pathMap, target);
    }


    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    @Override
    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        getActionExpression().checkPermittedContents(parentType, false);
    }

    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        Controller controller = context.getController();
        assert controller != null;
        PipelineConfiguration pipe = output.getPipelineConfiguration();

        GroupIterator groupIterator = getGroupIterator(context);
        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        FocusIterator focusIterator = c2.trackFocus(groupIterator);
        c2.setCurrentGroupIterator(groupIterator);
        c2.setCurrentTemplateRule(null);
        pipe.setXPathContext(c2);

        if (controller.isTracing()) {
            TraceListener listener = controller.getTraceListener();
            assert listener != null;
            Item item;
            while ((item = focusIterator.next()) != null) {
                listener.startCurrentItem(item);
                getActionExpression().process(output, c2);
                listener.endCurrentItem(item);
            }
        } else {
            while (focusIterator.next() != null) {
                getActionExpression().process(output, c2);
            }
        }

        pipe.setXPathContext(context);
        return null;
    }

    /**
     * Get the expression which, on evaluation, yields the name of the collation to be used
     *
     * @return the expression that returns the collation name
     */

    public Expression getCollationNameExpression() {
        return collationOp == null ? null : collationOp.getChildExpression();
    }

    /**
     * Get (and if necessary, create) the comparator used for comparing grouping key values
     *
     * @param context XPath dynamic context
     * @return a StringCollator suitable for comparing the values of grouping keys
     * @throws XPathException if a failure occurs evaluating the expression that determines the collation name
     */

    private StringCollator getCollator(XPathContext context) throws XPathException {
        if (getCollationNameExpression() != null) {
            StringValue collationValue = (StringValue) getCollationNameExpression().evaluateItem(context);
            assert collationValue != null;
            String cname = collationValue.getStringValue();
            try {
                return context.getConfiguration().getCollation(cname, getStaticBaseURIString(), "FOCH0002");
            } catch (XPathException e) {
                e.setLocation(getLocation());
                throw e;
            }

        } else {
            // Fallback - this shouldn't happen
            return CodepointCollator.getInstance();
        }
    }

    private GroupIterator getGroupIterator(XPathContext context) throws XPathException {
        return getGroupIterator(getSelectExpression(), context);
    }

    public GroupIterator getGroupIterator(Expression select, XPathContext context) throws XPathException {

        // get an iterator over the groups in "order of first appearance"

        GroupIterator groupIterator;
        switch (algorithm) {
            case GROUP_BY: {
                StringCollator coll = collator;
                if (coll==null) {
                    // The collation is determined at run-time
                    coll = getCollator(context);
                }
                XPathContext c2 = context.newMinorContext();
                FocusIterator population = c2.trackFocus(select.iterate(context));
                groupIterator = new GroupByIterator(population, getGroupingKey(), c2, coll, composite);
                break;
            }
            case GROUP_ADJACENT: {
                StringCollator coll = collator;
                if (coll==null) {
                    // The collation is determined at run-time
                    coll = getCollator(context);
                }
                groupIterator = new GroupAdjacentIterator(select, getGroupingKey(), context, coll, composite);
                break;
            }
            case GROUP_STARTING:
                groupIterator = new GroupStartingIterator(select, (Pattern) getGroupingKey(), context);
                break;
            case GROUP_ENDING:
                groupIterator = new GroupEndingIterator(select, (Pattern) getGroupingKey(), context);
                break;
            default:
                throw new AssertionError("Unknown grouping algorithm");
        }

        // now iterate over the leading nodes of the groups

        if (getSortKeyDefinitions() != null) {
            AtomicComparer[] comps = sortComparators;
            XPathContext xpc = context.newMinorContext();
            if (comps == null) {
                comps = new AtomicComparer[getSortKeyDefinitions().size()];
                for (int s = 0; s < getSortKeyDefinitions().size(); s++) {
                    comps[s] = getSortKeyDefinitions().getSortKeyDefinition(s).makeComparator(xpc);
                }
            }
            groupIterator = new SortedGroupIterator(xpc, groupIterator, this, comps);

        }

        return groupIterator;
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation relies on the process() method: it
     * "pushes" the results of the instruction to a sequence in memory, and then
     * iterates over this in-memory sequence.
     * <p>In principle instructions should implement a pipelined iterate() method that
     * avoids the overhead of intermediate storage.</p>
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws XPathException if any dynamic error occurs evaluating the
     *                        expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        GroupIterator master = getGroupIterator(context);
        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        c2.trackFocus(master);
        c2.setCurrentGroupIterator(master);
        c2.setCurrentTemplateRule(null);
        return new ContextMappingIterator(this, c2);
    }

    /**
     * Map one item to a sequence.
     *
     * @param context The processing context. This is supplied only for mapping constructs that
     *                set the context node, position, and size. Otherwise it is null.
     * @return either (a) a SequenceIterator over the sequence of items that the supplied input
     *         item maps to, or (b) an Item if it maps to a single item, or (c) null if it maps to an empty
     *         sequence.
     */

    @Override
    public SequenceIterator map(XPathContext context) throws XPathException {
        return getActionExpression().iterate(context);
    }

    /**
     * Callback for evaluating the sort keys
     */

    @Override
    public AtomicValue evaluateSortKey(int n, XPathContext c) throws XPathException {
        return (AtomicValue) getSortKeyDefinitions().getSortKeyDefinition(n).getSortKey().evaluateItem(c);
    }


    public SortKeyDefinitionList getSortKeyDefinitionList() {
        if (sortKeysOp == null) {
            return null;
        }
        return (SortKeyDefinitionList) sortKeysOp.getChildExpression();
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("forEachGroup", this);
        out.emitAttribute("algorithm", getAlgorithmName(algorithm));
        String flags = "";
        if (composite) {
            flags = "c";
        }
        if (isInFork()) {
            flags += "k";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        out.setChildRole("select");
        getSelectExpression().export(out);
        if (algorithm == GROUP_BY || algorithm == GROUP_ADJACENT) {
            out.setChildRole("key");
            getGroupingKey().export(out);
        } else {
            out.setChildRole("match");
            getGroupingKey().export(out);
        }
        if (getSortKeyDefinitions() != null) {
            out.setChildRole("sort");
            getSortKeyDefinitionList().export(out);
        }
        if (getCollationNameExpression() != null) {
            out.setChildRole("collation");
            getCollationNameExpression().export(out);
        }
        out.setChildRole("content");
        getActionExpression().export(out);
        out.endElement();
    }

    private static String getAlgorithmName(byte algorithm) {
        switch (algorithm) {
            case GROUP_BY:
                return "by";
            case GROUP_ADJACENT:
                return "adjacent";
            case GROUP_STARTING:
                return "starting";
            case GROUP_ENDING:
                return "ending";
            default:
                return "** unknown algorithm **";
        }
    }

    public void setSelect(Expression select) {
        selectOp.setChildExpression(select);
    }

    public void setAction(Expression action) {
        actionOp.setChildExpression(action);
    }

    public void setKey(Expression key) {
        keyOp.setChildExpression(key);
    }

    public void setCollationNameExpression(Expression collationNameExpression) {
        if (collationOp == null) {
            collationOp = new Operand(this, collationNameExpression, OperandRole.SINGLE_ATOMIC);
        } else {
            collationOp.setChildExpression(collationNameExpression);
        }
    }

    public void setSortKeyDefinitions(SortKeyDefinitionList sortKeyDefinitions) {
        if (sortKeysOp == null) {
            sortKeysOp = new Operand(this, sortKeyDefinitions, OperandRole.SINGLE_ATOMIC);
        } else {
            sortKeysOp.setChildExpression(sortKeyDefinitions);
        }
    }
}

