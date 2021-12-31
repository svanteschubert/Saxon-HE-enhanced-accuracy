////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import com.saxonica.ee.stream.adjunct.MergeInstrAdjunct;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.accum.AccumulatorManager;
import net.sf.saxon.expr.instruct.Instruction;
import net.sf.saxon.expr.instruct.TailCall;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.*;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MergeInstr extends Instruction {

    protected MergeSource[] mergeSources;
    private Operand actionOp;
    protected AtomicComparer[] comparators;


    /**
     * Inner class representing one merge source
     */

    public static class MergeSource {

        private MergeInstr instruction;
        public Location location;
        private Operand forEachItemOp = null;
        private Operand forEachStreamOp = null;
        private Operand rowSelectOp = null;
        public String sourceName = null;
        public SortKeyDefinitionList mergeKeyDefinitions = null;
        public String baseURI = null;
        public int validation;
        public SchemaType schemaType;
        public boolean streamable;
        public Set<Accumulator> accumulators;
        public Object invertedAction; // used when streaming

        public MergeSource(MergeInstr mi) {
            this.instruction = mi;
        }

        /**
         * Create a MergeSource object
         *
         * @param forEachItem   the expression that selects anchor nodes, one per input sequence
         * @param forEachStream the expression that selects URIs of anchor nodes, one per input sequence
         * @param rSelect       the select expression that selects items for the merge inputs, evaluated one per anchor node
         * @param name          the name of the xsl:merge-source, or null if none specified
         * @param sKeys         the merge key definitions
         * @param baseURI       the base URI of the xsl:merge-source instruction
         */

        public MergeSource(MergeInstr instruction,
                           Expression forEachItem, Expression forEachStream, Expression rSelect, String name, SortKeyDefinitionList sKeys, String baseURI) {
            this.instruction = instruction;
            if (forEachItem != null) {
                initForEachItem(instruction, forEachItem);
            }
            if (forEachStream != null) {
                initForEachStream(instruction, forEachStream);
            }
            if (rSelect != null) {
                initRowSelect(instruction, rSelect);
            }
            this.sourceName = name;
            this.mergeKeyDefinitions = sKeys;
            this.baseURI = baseURI;
        }

        public void initForEachItem(MergeInstr instruction, Expression forEachItem) {
            forEachItemOp = new Operand(instruction, forEachItem, OperandRole.INSPECT);
        }

        public void initForEachStream(MergeInstr instruction, Expression forEachStream) {
            forEachStreamOp = new Operand(instruction, forEachStream, OperandRole.INSPECT);
        }

        public void initRowSelect(MergeInstr instruction, Expression rowSelect) {
            rowSelectOp = new Operand(instruction, rowSelect, ROW_SELECT);
        }

        public void setStreamable(boolean streamable) {
            this.streamable = streamable;
            if (streamable && instruction.getConfiguration().getBooleanProperty(Feature.STREAMING_FALLBACK)) {
                this.streamable = false;
                Expression select = rowSelectOp.getChildExpression();
                rowSelectOp.setChildExpression(
                        SystemFunction.makeCall("snapshot", select.getRetainedStaticContext(), select));
            }
        }

        public MergeSource copyMergeSource(MergeInstr newInstr, RebindingMap rebindings) {
            SortKeyDefinition[] newKeyDef = new SortKeyDefinition[mergeKeyDefinitions.size()];

            for (int i = 0; i < mergeKeyDefinitions.size(); i++) {
                newKeyDef[i] = mergeKeyDefinitions.getSortKeyDefinition(i).copy(rebindings);

            }

            MergeSource ms = new MergeSource(newInstr,
                                             copy(getForEachItem(), rebindings), copy(getForEachSource(), rebindings),
                                             copy(getRowSelect(), rebindings), sourceName, new SortKeyDefinitionList(newKeyDef), baseURI);
            ms.validation = validation;
            ms.schemaType = schemaType;
            ms.streamable = streamable;
            ms.location = location;
            return ms;
        }

        private static Expression copy(Expression exp, RebindingMap rebindings) {
            return exp == null ? null : exp.copy(rebindings);
        }

        public Expression getForEachItem() {
            return forEachItemOp == null ? null : forEachItemOp.getChildExpression();
        }

        public void setForEachItem(Expression forEachItem) {
            if (forEachItem != null) {
                forEachItemOp.setChildExpression(forEachItem);
            }
        }

        public Expression getForEachSource() {
            return forEachStreamOp == null ? null : forEachStreamOp.getChildExpression();
        }

        public void setForEachStream(Expression forEachStream) {
            if (forEachStream != null) {
                forEachStreamOp.setChildExpression(forEachStream);
            }
        }

        public Expression getRowSelect() {
            return rowSelectOp.getChildExpression();
        }

        public void setRowSelect(Expression rowSelect) {
            rowSelectOp.setChildExpression(rowSelect);
        }

        public SortKeyDefinitionList getMergeKeyDefinitionSet() {
            return mergeKeyDefinitions;
        }

        public void setMergeKeyDefinitionSet(SortKeyDefinitionList keys) {
            mergeKeyDefinitions = keys;
        }

        public void prepareForStreaming() throws XPathException {

        }
    }

    public MergeInstr() {}

    /**
     * Initialise the merge instruction
     *
     * @param mSources the set of merge source definitions
     * @param action   the action to be performed on each group of items with identical merge keys
     * @return the merge instruction (to allow call chaining)
     */

    public MergeInstr init(MergeSource[] mSources, Expression action) {

        actionOp = new Operand(this, action, OperandRole.FOCUS_CONTROLLED_ACTION);
        this.mergeSources = mSources;
        for (MergeSource mSource : mSources) {
            adoptChildExpression(mSource.getForEachItem());
            adoptChildExpression(mSource.getForEachSource());
            adoptChildExpression(mSource.getRowSelect());

        }
        adoptChildExpression(action);
        //verifyParentPointers();
        return this;
    }

    public MergeSource[] getMergeSources() {
        return mergeSources;
    }


    public void setAction(Expression action) {
        actionOp.setChildExpression(action);
    }

    public Expression getAction() {
        return actionOp.getChildExpression();
    }

    /**
     * Get the namecode of the instruction for use in diagnostics
     *
     * @return a code identifying the instruction: typically but not always
     *         the fingerprint of a name in the XSLT namespace
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_MERGE;
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
        getAction().checkPermittedContents(parentType, false);
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

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return getAction().getItemType();
    }


    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        final Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();
        final TypeChecker tc = config.getTypeChecker(false);


        ItemType inputType = null;


        for (MergeSource mergeSource : mergeSources) {
            ContextItemStaticInfo rowContextItemType = contextInfo;
            if (mergeSource.getForEachItem() != null) {
                mergeSource.forEachItemOp.typeCheck(visitor, contextInfo);
                rowContextItemType = config.makeContextItemStaticInfo(mergeSource.getForEachItem().getItemType(), false);
            } else if (mergeSource.getForEachSource() != null) {
                mergeSource.forEachStreamOp.typeCheck(visitor, contextInfo);

                RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:merge/for-each-source", 0);
                mergeSource.setForEachStream(tc.staticTypeCheck(
                        mergeSource.getForEachSource(), SequenceType.STRING_SEQUENCE, role, visitor));

                rowContextItemType = config.makeContextItemStaticInfo(NodeKindTest.DOCUMENT, false);
            }
            mergeSource.rowSelectOp.typeCheck(visitor, rowContextItemType);
            ItemType rowItemType = mergeSource.getRowSelect().getItemType();
            if (inputType == null) {
                inputType = rowItemType;
            } else {
                inputType = Type.getCommonSuperType(inputType, rowItemType, th);
            }
            ContextItemStaticInfo cit = config.makeContextItemStaticInfo(inputType, false);
            if (mergeSource.mergeKeyDefinitions != null) {
                for (SortKeyDefinition skd : mergeSource.mergeKeyDefinitions) {
                    Expression sortKey = skd.getSortKey();
                    sortKey = sortKey.typeCheck(visitor, cit);
                    if (sortKey != null) {
                        RoleDiagnostic role =
                                new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:merge-key/select", 0);
                        role.setErrorCode("XTTE1020");
                        sortKey = CardinalityChecker.makeCardinalityChecker(sortKey, StaticProperty.ALLOWS_ZERO_OR_ONE, role);

                        skd.setSortKey(sortKey, true);
                    }
                    Expression exp = skd.getLanguage().typeCheck(visitor, config.makeContextItemStaticInfo(inputType, false));
                    skd.setLanguage(exp);

                    exp = skd.getOrder().typeCheck(visitor, cit);
                    skd.setOrder(exp);

                    exp = skd.getCollationNameExpression();
                    if (exp != null) {
                        exp = exp.typeCheck(visitor, cit);
                        skd.setCollationNameExpression(exp);
                    }

                    exp = skd.getCaseOrder().typeCheck(visitor, cit);
                    skd.setCaseOrder(exp);

                    exp = skd.getDataTypeExpression();
                    if (exp != null) {
                        exp = exp.typeCheck(visitor, cit);
                        skd.setDataTypeExpression(exp);
                    }
                }

            }

        }

        actionOp.typeCheck(visitor, config.makeContextItemStaticInfo(inputType, false));

        if (Literal.isEmptySequence(getAction())) {
            return getAction();
        }
        if (mergeSources.length == 1 && Literal.isEmptySequence(mergeSources[0].getRowSelect())) {
            return mergeSources[0].getRowSelect();
        }

        fixupGroupReferences();

        return this;
    }

    public void fixupGroupReferences() {
        fixupGroupReferences(this, this, false);
    }

    private static void fixupGroupReferences(Expression exp, MergeInstr instr, boolean isInLoop) {
        if (exp == null) {
            // no action
        } else if (exp.isCallOn(CurrentMergeGroup.class)) {
            CurrentMergeGroup fn = (CurrentMergeGroup)((SystemFunctionCall)exp).getTargetFunction();
            fn.setControllingInstruction(instr, isInLoop);
        } else if (exp.isCallOn(CurrentMergeKey.class)) {
            CurrentMergeKey fn = (CurrentMergeKey) ((SystemFunctionCall) exp).getTargetFunction();
            fn.setControllingInstruction(instr);
        } else if (exp instanceof MergeInstr) {
            // a current-merge-group() reference to the outer xsl:merge can occur in the
            //  AVTs of a contained xsl:merge-key
            MergeInstr instr2 = (MergeInstr) exp;
            if (instr2 == instr) {
                fixupGroupReferences(instr2.getAction(), instr, false);
            } else {
                for (MergeSource m : instr2.getMergeSources()) {
                    for (SortKeyDefinition skd : m.mergeKeyDefinitions) {
                        fixupGroupReferences(skd.getOrder(), instr, isInLoop);
                        fixupGroupReferences(skd.getCaseOrder(), instr, isInLoop);
                        fixupGroupReferences(skd.getDataTypeExpression(), instr, isInLoop);
                        fixupGroupReferences(skd.getLanguage(), instr, isInLoop);
                        fixupGroupReferences(skd.getCollationNameExpression(), instr, isInLoop);
                        fixupGroupReferences(skd.getOrder(), instr, isInLoop);
                    }
                    if (m.forEachItemOp != null) {
                        fixupGroupReferences(m.getForEachItem(), instr, isInLoop);
                    }
                    if (m.forEachStreamOp != null) {
                        fixupGroupReferences(m.getForEachSource(), instr, isInLoop);
                    }
                    if (m.rowSelectOp != null) {
                        fixupGroupReferences(m.getRowSelect(), instr, isInLoop);
                    }
                }
            }

        } else {
            for (Operand o : exp.operands()) {
                fixupGroupReferences(o.getChildExpression(), instr, isInLoop || o.isEvaluatedRepeatedly());
            }
        }
    }


    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true if the "action" creates new nodes.
     * (Nodes created by the condition can't contribute to the result).
     */

    @Override
    public final boolean mayCreateNewNodes() {
        int props = getAction().getSpecialProperties();
        return (props & StaticProperty.NO_NODES_NEWLY_CREATED) == 0;
    }


    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        final Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();
        ItemType inputType = null;

        for (MergeSource mergeSource : mergeSources) {
            ContextItemStaticInfo rowContextItemType = contextInfo;
            if (mergeSource.getForEachItem() != null) {
                mergeSource.forEachItemOp.optimize(visitor, contextInfo);
                rowContextItemType = config.makeContextItemStaticInfo(mergeSource.getForEachItem().getItemType(), false);
            } else if (mergeSource.getForEachSource() != null) {
                mergeSource.forEachStreamOp.optimize(visitor, contextInfo);
                rowContextItemType = config.makeContextItemStaticInfo(NodeKindTest.DOCUMENT, false);
            }
            mergeSource.rowSelectOp.optimize(visitor, rowContextItemType);
            ItemType rowItemType = mergeSource.getRowSelect().getItemType();
            if (inputType == null) {
                inputType = rowItemType;
            } else {
                inputType = Type.getCommonSuperType(inputType, rowItemType, th);
            }
            //mergeSource.prepareForStreaming();
        }

        ContextItemStaticInfo cit = config.makeContextItemStaticInfo(inputType, false);
        setAction(getAction().optimize(visitor, cit));

        if (Literal.isEmptySequence(getAction())) {
            return getAction();
        }
        if (mergeSources.length == 1 && Literal.isEmptySequence(mergeSources[0].getRowSelect())) {
            return mergeSources[0].getRowSelect();
        }

        return this;
    }

    @Override
    public void prepareForStreaming() throws XPathException {
        for (MergeSource mergeSource : mergeSources) {
            mergeSource.prepareForStreaming();
        }
    }

    /**
     * Check that the sort key definitions are all compatible
     *
     * @param sortKeyDefs the list of sort key definitions
     * @throws XPathException if the sort keys are not compatible
     */

    private void checkMergeAtt(SortKeyDefinition[] sortKeyDefs) throws XPathException {
        for (int i = 1; i < sortKeyDefs.length; i++) {
            if (!sortKeyDefs[0].isEqual(sortKeyDefs[i])) {
                throw new XPathException("Corresponding xsl:merge-key attributes in different xsl:merge-source elements " +
                        "do not have the same effective values", "XTDE2210");
            }
        }
    }

    /**
     * A call on last() while merging is unavoidably expensive. If it happens, we evaluate the whole expression
     * from scratch to count how many groups there are going to be. This method returns a function to perform
     * this computation; the resulting function is made known to the MergeGroupIterator, which thereby becomes
     * capable of computing its own length on demand, without actually disrupting its operation.
     * @param context the context to be used for the re-evaluation of the merge when calculating the last()
     *                position
     * @return a function capable of computing the number of merge groups
     */

    private LastPositionFinder getLastPositionFinder(final XPathContext context) {
        return new LastPositionFinder() {
            private int last  = -1;

            @Override
            public int getLength() throws XPathException {
                if (last >= 0) {
                    return last;
                } else {
                    AtomicComparer[] comps = getComparators(context);

                    GroupIterator mgi = context.getCurrentMergeGroupIterator();
                    final XPathContextMajor c1 = context.newContext();
                    c1.setCurrentMergeGroupIterator(mgi);
                    SequenceIterator inputIterator = getMergedInputIterator(context, comps, c1);

                    // Now perform the merge into a grouped sequence
                    inputIterator = new MergeGroupingIterator(inputIterator, getComparer(mergeSources[0].mergeKeyDefinitions, comps), null);

                    return last = Count.steppingCount(inputIterator);
                }
            }
        };
    }


    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {

        try {
            AtomicComparer[] comps = getComparators(context);

            GroupIterator mgi = context.getCurrentMergeGroupIterator();
            final XPathContextMajor c1 = context.newContext();
            c1.setCurrentMergeGroupIterator(mgi);
            SequenceIterator inputIterator = getMergedInputIterator(context, comps, c1);

            // Now perform the merge into a grouped sequence
            inputIterator = new MergeGroupingIterator(inputIterator, getComparer(mergeSources[0].mergeKeyDefinitions, comps), getLastPositionFinder(context));

            // and apply the merging action to each group of duplicate items within this sequence
            c1.setCurrentMergeGroupIterator((GroupIterator) inputIterator);
            XPathContext c3 = c1.newMinorContext();
            c3.trackFocus(inputIterator);
            return new ContextMappingIterator(cxt -> getAction().iterate(cxt), c3);
        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            throw e;
        }
    }

    /**
     * Get an iterator which will iterate over all the selected items in each of the input sources, in merged order
     * (but without actually combining duplicate entries)
     * @param context the dynamic evaluation context
     * @param comps the comparers to be used for comparing adjacent items in the sequence
     * @param c1  TODO not sure why we need this
     * @return an iterator over the merged sources
     * @throws XPathException if anything goes wrong
     */

    private SequenceIterator getMergedInputIterator(XPathContext context, AtomicComparer[] comps, final XPathContextMajor c1) throws XPathException {
        // Now construct a tree of merge iterators, one for each merge sequence, for each merge source.

        SequenceIterator inputIterator = EmptyIterator.getInstance();
        for (final MergeSource ms : mergeSources) {

            SequenceIterator anchorsIter = null;

            if (ms.streamable && ms.getForEachSource() != null) {
            } else if (ms.getForEachSource() != null) {
                final ParseOptions options = new ParseOptions(context.getConfiguration().getParseOptions());
                options.setSchemaValidationMode(ms.validation);
                options.setTopLevelType(ms.schemaType);
                options.setApplicableAccumulators(ms.accumulators);
                SequenceIterator uriIter = ms.getForEachSource().iterate(c1);
                XsltController controller = (XsltController)context.getController();
                final AccumulatorManager accumulatorManager = controller.getAccumulatorManager();
                anchorsIter = new ItemMappingIterator(uriIter, baseItem -> {
                    String uri = baseItem.getStringValue();
                    NodeInfo node = DocumentFn.makeDoc(uri, getRetainedStaticContext().getStaticBaseUriString(),
                                                       getPackageData(), options, c1, getLocation(), true);
                    if (node != null) {
                        accumulatorManager.setApplicableAccumulators(node.getTreeInfo(), ms.accumulators);
                    }
                    return node;
                });
                XPathContext c2 = c1.newMinorContext();
                FocusIterator anchorsIterFocus = c2.trackFocus(anchorsIter);
                while (anchorsIterFocus.next() != null) {
                    XPathContext c4 = c2.newMinorContext();
                    FocusIterator rowIntr = c4.trackFocus(ms.getRowSelect().iterate(c2));
                    MergeKeyMappingFunction addMergeKeys = new MergeKeyMappingFunction(c4, ms);
                    ContextMappingIterator contextMapKeysItr =
                            new ContextMappingIterator(addMergeKeys, c4);
                    inputIterator = makeMergeIterator(inputIterator, comps, ms, contextMapKeysItr);
                }
            } else if (ms.getForEachItem() != null) {
                anchorsIter = ms.getForEachItem().iterate(c1);
                XPathContext c2 = c1.newMinorContext();
                FocusIterator anchorsIterFocus = c2.trackFocus(anchorsIter);
                while (anchorsIterFocus.next() != null) {
                    inputIterator = getInputIterator(comps, inputIterator, ms, c2);
                }
            } else {
                inputIterator = getInputIterator(comps, inputIterator, ms, c1);

            }

        }
        return inputIterator;
    }

    private SequenceIterator getInputIterator(AtomicComparer[] comps, SequenceIterator inputIterator, MergeSource ms, XPathContext c2) throws XPathException {
        XPathContext c4 = c2.newMinorContext();
        c4.setTemporaryOutputState(StandardNames.XSL_MERGE_KEY);
        FocusIterator rowIntr = c4.trackFocus(ms.getRowSelect().iterate(c2));
        MergeKeyMappingFunction addMergeKeys = new MergeKeyMappingFunction(c4, ms);
        ContextMappingIterator contextMapKeysItr =
                new ContextMappingIterator(addMergeKeys, c4);
        inputIterator = makeMergeIterator(inputIterator, comps, ms, contextMapKeysItr);
        return inputIterator;
    }

    /**
     * Get an array of comparers to be used for comparing items according to their merge keys. Ideally this
     * will have been done at compile time, in which case the compile-time result is simply returned.
     * @param context the dynamic evaluation context
     * @return an array of atomic comparers, one for each merge key component
     * @throws XPathException typically if errors occur evaluating expressions in attribute-value templates
     * of the merge key definitions
     */

    private AtomicComparer[] getComparators(XPathContext context) throws XPathException {
        // First establish an array of comparators to be used for comparing items according to their
        // merge keys. Ideally this will have been done at compile time.
        AtomicComparer[] comps = comparators;
        if (comparators == null) {
            SortKeyDefinition[] tempSKeys = new SortKeyDefinition[mergeSources.length];

            for (int i = 0; i < mergeSources[0].mergeKeyDefinitions.size(); i++) {
                for (int j = 0; j < mergeSources.length; j++) {
                    tempSKeys[j] = mergeSources[j].mergeKeyDefinitions.getSortKeyDefinition(i).fix(context);
                }
                checkMergeAtt(tempSKeys);
            }

            comps = new AtomicComparer[mergeSources[0].mergeKeyDefinitions.size()];
            for (int s = 0; s < mergeSources[0].mergeKeyDefinitions.size(); s++) {
                AtomicComparer comp = mergeSources[0].mergeKeyDefinitions.getSortKeyDefinition(s).getFinalComparator();
                if (comp == null) {
                    comp = mergeSources[0].mergeKeyDefinitions.getSortKeyDefinition(s).makeComparator(context);
                }
                comps[s] = comp;
            }
        }
        return comps;
    }

    /**
     * Make a merging iterator that merges the results of two iterators based on their merge keys
     * @param result an existing iterator that delivers items together with their merge keys
     * @param comps the comparators for merge keys
     * @param ms the merge source that contributes this iterator
     * @param contextMapKeysItr the new iterator to be merged with the existing iterator
     * @return a new merging iterator
     * @throws XPathException if a failure occurs
     */

    private SequenceIterator makeMergeIterator(
            SequenceIterator result, AtomicComparer[] comps, MergeSource ms,
            ContextMappingIterator contextMapKeysItr) throws XPathException {
        if (result == null || result instanceof EmptyIterator) {
            result = contextMapKeysItr;
        } else {
            result = new MergeIterator(result, contextMapKeysItr, getComparer(ms.mergeKeyDefinitions, comps));
        }
        return result;
    }


    private final static OperandRole ROW_SELECT =
            new OperandRole(OperandRole.USES_NEW_FOCUS | OperandRole.HIGHER_ORDER, OperandUsage.INSPECTION, SequenceType.ANY_SEQUENCE);

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * returns a zero-length array, appropriate for an expression that has no
     * sub-expressions.
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterable<Operand> operands() {
        List<Operand> list = new ArrayList<>(6);
        list.add(actionOp);
        if (mergeSources != null) {
            for (final MergeSource ms : mergeSources) {
                if (ms.forEachItemOp != null) {
                    list.add(ms.forEachItemOp);
                }
                if (ms.forEachStreamOp != null) {
                    list.add(ms.forEachStreamOp);
                }
                if (ms.rowSelectOp != null) {
                    list.add(ms.rowSelectOp);
                }
                list.add(new Operand(this, ms.mergeKeyDefinitions, OperandRole.SINGLE_ATOMIC));
            }
        }
        return list;
    }

    /**
     * Get the grouping key expression expression (the group-by or group-adjacent expression, or a
     * PatternSponsor containing the group-starting-with or group-ending-with expression)
     *
     * @return the expression used to calculate grouping keys
     */

    public Expression getGroupingKey() {
        return mergeSources[0].mergeKeyDefinitions.getSortKeyDefinition(0).getSortKey();
    }

    public ItemOrderComparer getComparer(final SortKeyDefinitionList sKeys, final AtomicComparer[] comps) {
        return (a, b) -> {
            ObjectValue aObj = (ObjectValue) a;
            ObjectValue bObj = (ObjectValue) b;
            ItemWithMergeKeys aItem = (ItemWithMergeKeys) aObj.getObject();
            ItemWithMergeKeys bItem = (ItemWithMergeKeys) bObj.getObject();

            for (int i = 0; i < sKeys.size(); i++) {
                int val;
                try {
                    val = comps[i].compareAtomicValues(aItem.sortKeyValues.get(i), bItem.sortKeyValues.get(i));
                } catch (NoDynamicContextException e) {
                    throw new IllegalStateException(e);
                }

                if (val != 0) {
                    return val;
                }
            }
            return 0;
        };
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        MergeInstr newMerge = new MergeInstr();
        MergeSource[] c2 = new MergeSource[mergeSources.length];
        Expression a2 = getAction().copy(rebindings);
        for (int c = 0; c < mergeSources.length; c++) {
            c2[c] = mergeSources[c].copyMergeSource(newMerge, rebindings);
        }
        return newMerge.init(c2, a2);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("merge", this);
        for (MergeSource mergeSource : mergeSources) {
            out.startSubsidiaryElement("mergeSrc");
            if (mergeSource.sourceName != null && !mergeSource.sourceName.startsWith("saxon-merge-source-")) {
                out.emitAttribute("name", mergeSource.sourceName);
            }
            if (mergeSource.validation != Validation.SKIP && mergeSource.validation != Validation.BY_TYPE) {
                out.emitAttribute("validation", Validation.toString(mergeSource.validation));
            }
            if (mergeSource.validation == Validation.BY_TYPE) {
                SchemaType type = mergeSource.schemaType;
                if (type != null) {
                    out.emitAttribute("type", type.getStructuredQName());
                }
            }
            if (mergeSource.accumulators != null && !mergeSource.accumulators.isEmpty()) {
                FastStringBuffer fsb = new FastStringBuffer(256);
                for (Accumulator acc : mergeSource.accumulators) {
                    if (!fsb.isEmpty()) {
                        fsb.append(" ");
                    }
                    fsb.append(acc.getAccumulatorName().getEQName());
                }
                out.emitAttribute("accum", fsb.toString());
            }
            if (mergeSource.streamable) {
                out.emitAttribute("flags", "s");
            }
            if (mergeSource.getForEachItem() != null) {
                out.setChildRole("forEachItem");
                mergeSource.getForEachItem().export(out);
            }
            if (mergeSource.getForEachSource() != null) {
                out.setChildRole("forEachStream");
                mergeSource.getForEachSource().export(out);
            }
            out.setChildRole("selectRows");
            mergeSource.getRowSelect().export(out);
            mergeSource.getMergeKeyDefinitionSet().export(out);
            out.endSubsidiaryElement();
        }
        out.setChildRole("action");
        getAction().export(out);
        out.endElement();
    }


    /*@Nullable*/
    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context)
            throws XPathException {

        try (SequenceIterator iter = iterate(context)) {
            iter.forEachOrFail(it -> output.append(it, getLocation(), ReceiverOption.ALL_NAMESPACES));
        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            throw e;
        }
        return null;
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "MergeInstr";
    }

    /**
     * Mapping function for items encountered during the merge; the mapping function wraps the merged
     * item and its merge keys into a single composite object
     */

    public static class MergeKeyMappingFunction implements ContextMappingFunction {
        private MergeSource ms;
        private XPathContext baseContext;
        private XPathContext keyContext;
        private ManualIterator manualIterator;

        public MergeKeyMappingFunction(XPathContext baseContext, MergeSource ms) {
            this.baseContext = baseContext;
            this.ms = ms;
            keyContext = baseContext.newMinorContext();
            keyContext.setTemporaryOutputState(StandardNames.XSL_MERGE_KEY);
            //keyContext.setCurrentOutputUri(null);   // See bug 4160
            manualIterator = new ManualIterator();
            manualIterator.setPosition(1);
            keyContext.setCurrentIterator(manualIterator);
        }
        @Override
        public SequenceIterator map(XPathContext context) throws XPathException {
            Item currentItem = context.getContextItem();
            manualIterator.setContextItem(currentItem);
            ItemWithMergeKeys newItem = new ItemWithMergeKeys(currentItem, ms.mergeKeyDefinitions, ms.sourceName, keyContext);
            return SingletonIterator.makeIterator(new ObjectValue<>(newItem));

        };
    }
}

