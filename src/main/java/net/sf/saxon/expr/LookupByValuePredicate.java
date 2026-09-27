////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.ma.MapOrArray;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SquareArrayConstructor;
import net.sf.saxon.ma.jnode.AnyJNodeType;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.ma.jnode.JNodeType;
import net.sf.saxon.ma.jnode.RootJNode;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.ma.map.RecordType;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;


/**
 * The {@code LookupAllExpression} handles lookup expressions of the form {@code expr?*} (which
 * returns all entries in a map or array) and {@code expr?type(T)} (which returns all entries
 * whose value matches a given sequence type).
 *
 * The code handles expressions with any axis (or none), and the LHS may be a map, an array,
 * a JNode, or any sequence of maps, arrays, and JNodes.
 */

public class LookupByValuePredicate extends UnaryExpression {

    private final int axis;
    private final Function<GroundedValue, Boolean> predicate;

    /**
     * Constructor
     *
     * @param lhs The left hand operand (which must always select a sequence of maps or arrays).
     */

    public LookupByValuePredicate(Expression lhs, int axis, Function<GroundedValue, Boolean> predicate) {
        super(lhs);
        this.axis = axis;
        this.predicate = predicate;
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.INSPECT;
    }


    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the type of the step
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return AnyItemType.getInstance();
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType The type of the context item (not used)
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return getItemType().getUType();
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        Configuration config = visitor.getConfiguration();
        TypeHierarchy th = config.getTypeHierarchy();

        // Check the first operand
        getOperand().typeCheck(visitor, contextInfo);

        ItemType containerType = getBaseExpression().getItemType();
        boolean isArrayLookup = containerType instanceof ArrayItemType;
        boolean isMapLookup = containerType instanceof MapType || containerType instanceof RecordType;
        boolean isJNodeLookup = containerType instanceof JNodeType;

//        if (!isArrayLookup && !isMapLookup) {
//            if (th.relationship(containerType, MapType.ANY_MAP_TYPE) == Affinity.DISJOINT &&
//                    th.relationship(containerType, ArrayItemType.getInstance()) == Affinity.DISJOINT) {
//                if (Cardinality.allowsZero(getBaseExpression().getCardinality())) {
//                    visitor.issueWarning("The left-hand operand of '?' must be a map or an array; the expression can succeed only if the operand is an empty sequence "
//                                                 + containerType, SaxonErrorCode.SXWN9026, getLocation());
//                } else {
//                    throw new XPathException("The left-hand operand of '?' must be a map or an array; "
//                                                     + "the supplied expression is of type " + containerType, "XPTY0004")
//                            .withLocation(getLocation())
//                            .asTypeError()
//                            .withFailingExpression(this);
//                }
//            }
//        }

        if (getBaseExpression() instanceof Literal) {
            try {
                return new Literal(SequenceTool.toGroundedValue(iterate(visitor.makeDynamicContext())));
            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }
        }

        return this;
    }

    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        getOperand().optimize(visitor, contextItemType);

        if (getBaseExpression() instanceof Literal) {
            try {
                return new Literal(SequenceTool.toGroundedValue(iterate(visitor.makeDynamicContext())));
            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }
        }
        
        // See W3C bug 30228. In the interests of keeping certain tests streamable, we do a rewrite of [A,B,C]?*
        // to (A, B, C).
        if (getBaseExpression() instanceof SquareArrayConstructor) {
            List<Expression> children = new ArrayList<>();
            for (Operand o : getBaseExpression().operands()) {
                children.add(o.getChildExpression().copy(new RebindingMap()));
            }
            Expression[] childExpressions = children.toArray(new Expression[0]);
            Block block = new Block(childExpressions);
            ExpressionTool.copyLocationInfo(this, block);
            return block;
        }
        return this;
    }


    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     * @return the cost estimate
     */
    @Override
    public double getCost() {
        return getBaseExpression().getCost() + 1;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */
    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound.
     */

    /*@NotNull*/
    @Override
    public LookupByValuePredicate copy(RebindingMap rebindings) {
        return new LookupByValuePredicate(getBaseExpression().copy(rebindings), axis, predicate);
    }

    /**
     * Determine the static cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        if (!(other instanceof LookupByValuePredicate)) {
            return false;
        }
        LookupByValuePredicate p = (LookupByValuePredicate) other;
        return getBaseExpression().isEqual(p.getBaseExpression())
                && axis == p.axis && predicate == p.predicate;
    }

    /**
     * get HashCode for comparing two expressions
     */

    @Override
    protected int computeHashCode() {
        return "LookupAll".hashCode()
                ^ getBaseExpression().hashCode()
                ^ axis;
    }

    /**
     * Iterate the lookup-expression in a given context
     *
     * @param context the evaluation context
     */

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
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("lookupAll", this);
        getBaseExpression().export(destination);
        destination.endElement();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        return ExpressionTool.parenthesize(getBaseExpression()) + "?*";
    }

    @Override
    public String toShortString() {
        return getBaseExpression().toShortString() + "?*";
    }

    @Override
    public Elaborator getElaborator() {
        return new LookupAllElaborator();
    }

    public static class LookupAllElaborator extends PullElaborator {

        public PullEvaluator elaborateForPull() {
            LookupByValuePredicate expr = (LookupByValuePredicate) getExpression();
            int axis = expr.axis;
            Function<GroundedValue, Boolean> predicate = expr.predicate;


//            ItemType containerType = expr.getItemType();
//            boolean isArrayLookup = containerType instanceof ArrayItemType;
//            boolean isMapLookup = containerType instanceof MapType || containerType instanceof RecordType;
//            boolean isJNodeLookup = containerType instanceof JNodeType;
//            boolean isSingleton = !Cardinality.allowsMany(expr.getCardinality());

            // Special case: LHS is a singleton map, axis is unspecified

//            if (expr.axis == -1 && isSingleton && isMapLookup) {
//                ItemEvaluator baseEval = expr.getBaseExpression().makeElaborator().elaborateForItem();
//                return context -> {
//                    // TODO: lazy evaluation
//                    MapItem map = (MapItem)baseEval.eval(context);
//                    if (map == null) {
//                        return EmptyIterator.getInstance();
//                    }
//                    ZenoSequence results = new ZenoSequence();
//                    for (KeyValuePair pair : map.keyValuePairs()) {
//                        if (predicate == null || predicate.apply(pair.value)) {
//                            results = results.appendSequence(pair.value);
//                        }
//                    }
//                    return results.iterate();
//                };
//            }
//
//            // Special case: LHS is a singleton array, axis is unspecified
//
//            if (expr.axis == -1 && isSingleton && isArrayLookup) {
//                // TODO: lazy evaluation
//                ItemEvaluator baseEval = expr.getBaseExpression().makeElaborator().elaborateForItem();
//                return context -> {
//                    ArrayItem array = (ArrayItem) baseEval.eval(context);
//                    if (array == null) {
//                        return EmptyIterator.getInstance();
//                    }
//                    ZenoSequence results = new ZenoSequence();
//                    for (GroundedValue value : array.members()) {
//                        if (predicate == null || predicate.apply(value)) {
//                            results = results.appendSequence(value);
//                        }
//                    }
//                    return results.iterate();
//                };
//            }

            // General case

            PullEvaluator baseEval = expr.getBaseExpression().makeElaborator().elaborateForPull();

            return context -> {
                SequenceIterator lhs = baseEval.iterate(context);
                return new MappingIterator(lhs, item -> {
                    JNode jNode;
                    if (item instanceof MapOrArray) {
                        jNode = RootJNode.obtainRootJNode((MapOrArray)item);
                    } else {
                        jNode = (JNode)item;
                    }
                    SequenceIterator allNodes = jNode.iterateChildAxis(AnyJNodeType.getInstance());
                    SequenceIterator selectedNodes =
                            predicate == null
                                    ? allNodes
                                    : new ItemMappingIterator(allNodes, node -> predicate.apply(node) ? node : null);
                    if (item instanceof MapOrArray && axis == -1) {
                        // backwards compatibility case, return the node values. not the nodes
                        return new MappingIterator(selectedNodes, node -> ((JNode)node).getContent().iterate());
                    } else {
                        return selectedNodes;
                    }
                });

            };
        }
    }


}

