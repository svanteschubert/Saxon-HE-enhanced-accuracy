////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.SequenceEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.ma.Parcel;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.ma.map.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;


/**
 * Filter expression for maps and arrays, introduced in 4.0.
 * Syntax {@code value?[filter]}
 */

public class FilterExpressionAM extends BinaryExpression
        implements ContextSwitchingExpression {

    /**
     * Constructor
     *
     * @param start The left hand operand (which must always select a sequence of nodes).
     * @param step  The step to be followed from each node in the start expression to yield a new
     *              sequence; this may return either nodes or atomic values (but not a mixture of the two)
     */

    public FilterExpressionAM(Expression start, Expression step) {
        super(start, OperatorSymbol.AM_FILTER, step);
    }

    @Override
    protected OperandRole getOperandRole(int arg) {
        return arg==0 ? OperandRole.FOCUS_CONTROLLING_SELECT : OperandRole.FOCUS_CONTROLLED_ACTION;
    }

    /**
     * Get the left-hand operand
     * @return the left-hand operand
     */

    public Expression getStart() {
        return getLhsExpression();
    }

    /**
     * Set the left-hand operand
     * @param start the left-hand operand
     */

    public void setStart(Expression start) {
        setLhsExpression(start);
    }

    /**
     * Set the right-hand operand
     * @param step the right-hand operand
     */

    public void setStep(Expression step) {
        setRhsExpression(step);
    }

    @Override
    public String getExpressionName() {
        return "filterExprAM";
    }

    /**
     * Get the start expression (the left-hand operand)
     *
     * @return the first operand
     */

    @Override
    public Expression getSelectExpression() {
        return getStart();
    }

    /**
     * Get the step expression (the right-hand operand)
     *
     * @return the second operand
     */

    @Override
    public Expression getActionExpression() {
        return getRhsExpression();
    }

    /**
     * Determine the data type of the items returned by this exprssion
     *
     * @return the type of the start expression
     */

    /*@NotNull*/
    @Override
    public final ItemType getItemType() {
        return getStart().getItemType();
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return getStart().getStaticUType(contextItemType);
    }


    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        getLhs().typeCheck(visitor, contextInfo);

        // If the first expression is known to be empty, just return empty without checking the step expression.
        // (Checking the step expression can cause spurious errors, such as "the context item is absent")

        if (Literal.isEmptySequence(getStart())) {
            return getStart();
        }

        // The first operand must be of type (map(*)|array(*))?

        Configuration config = visitor.getConfiguration();
        TypeChecker tc = config.getTypeChecker(false);
        Supplier<RoleDiagnostic> roleSupplier =
                () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "?[]", 0, "XPTY0004");
        ItemType mapOrArray = ChoiceItemType.of(MapType.ANY_MAP_TYPE, ArrayItemType.ANY_ARRAY_TYPE);
        SequenceType optionalMapOrArray = SequenceType.optional(mapOrArray);
        setStart(tc.staticTypeCheck(getStart(), optionalMapOrArray, roleSupplier, visitor));


        ItemType startType = getStart().getItemType();
        if (startType == ErrorType.getInstance()) {
            // implies the start expression will return an empty sequence, so the whole expression is void
            return Literal.makeEmptySequence();
        }

        SequenceType entryType = SequenceType.ANY_SEQUENCE;
        if (startType instanceof ArrayItemType) {
            entryType = ((ArrayItemType)startType).getMemberType();
        } else if (startType instanceof MapType) {
            entryType = SequenceType.one(RecordType.nonExtensible(
                    new RecordType.Field("key", SequenceType.SINGLE_ATOMIC, false),
                    new RecordType.Field("value", SequenceType.ANY_SEQUENCE, false)));
        }
        ContextItemStaticInfo cit = config.makeContextItemStaticInfo(entryType)
                .withContextSetter(getStart());

        getRhs().typeCheck(visitor, cit);

        Supplier<RoleDiagnostic> roleSupplier1 =
                () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "?[]", 1, "XPTY0004");
        //setStep(tc.staticTypeCheck(getRhsExpression(), SequenceType.OPTIONAL_BOOLEAN, roleSupplier1, visitor));
        // TODO: no, this will invoke coercion which we don't want

        return this;
    }


    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        getLhs().optimize(visitor, contextItemType);
        if (Literal.isEmptySequence(getSelectExpression())) {
            return getSelectExpression();
        }
        getLhsExpression().setFiltered(true);

        ContextItemStaticInfo baseItemType = visitor.getConfiguration().makeContextItemStaticInfo(
                        AnyItemType.INSTANCE)      // TODO: improve this
                .withContextSetter(getSelectExpression());
        getRhs().optimize(visitor, baseItemType);
        // TODO: recognize positional filters such as position() lt 5.
        return this;
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
        return EVALUATE_METHOD;
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
        return new FilterExpressionAM(getLhsExpression().copy(rebindings), getRhsExpression().copy(rebindings));
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    @Override
    protected int computeSpecialProperties() {
        return getLhsExpression().getSpecialProperties();
    }


    /**
     * Determine the static cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        return getLhsExpression().getCardinality();
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        if (!(other instanceof FilterExpressionAM)) {
            return false;
        }
        FilterExpressionAM p = (FilterExpressionAM) other;
        return getLhsExpression().isEqual(p.getLhsExpression()) && getRhsExpression().isEqual(p.getRhsExpression());
    }

    /**
     * get HashCode for comparing two expressions
     */

    @Override
    protected int computeHashCode() {
        return "FilterExprAM".hashCode() + getLhsExpression().hashCode() + getRhsExpression().hashCode();
    }


    /**
     * Export expression structure to SEF file. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("filterAM", this);
        getLhsExpression().export(destination);
        getRhsExpression().export(destination);
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
        return ExpressionTool.parenthesize(getLhsExpression())
                + "?[" + getRhsExpression() + "]";
    }

    @Override
    public String toShortString() {
        return ExpressionTool.parenthesizeShort(getLhsExpression())
                + "?[" + getRhsExpression().toShortString() + "]";
    }

    public Item evaluateItem(XPathContext context) throws XPathException {
        return makeElaborator().elaborateForItem().eval(context);
    }


    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new FilterExprAMElaborator();
    }

    /**
     * Elaborator for a filterAM expression.
     */

    public static class FilterExprAMElaborator extends ItemElaborator {

        @Override
        public ItemEvaluator elaborateForItem() {
            FilterExpressionAM expr = (FilterExpressionAM) getExpression();
            ItemEvaluator select = expr.getSelectExpression().makeElaborator().elaborateForItem();
            SequenceEvaluator predicate = expr.getActionExpression().makeElaborator().eagerly();
            return context -> {
                Item input = select.eval(context);
                if (input == null) {
                    return null;
                } else if (input instanceof MapItem) {
                    return filteredMap(context, (MapItem)input, predicate);
                } else if (input instanceof ArrayItem) {
                    return filteredArray(context, (ArrayItem) input, predicate);
                } else {
                    throw new AssertionError("Type checking failure");
                }
            };
        }

        private MapItem filteredMap(XPathContext context, MapItem input, SequenceEvaluator predicate) throws XPathException {
            int size = input.size();
            GeneralMapBuilder result = FixedMap.getBuilder(40);
            ManualIterator focus = new ManualIterator();
            XPathContext c2 = context.newMinorContext();
            c2.setCurrentIterator(focus);
            focus.setLengthFinder(() -> size);
            int position = 0;
            for (KeyValuePair pair : input.keyValuePairs()) {
                position++;
                ShapedMap kvp = new ShapedMap(Shape.KEY_VALUE_PAIR, pair.key(), pair.value());
                focus.setContextItem(kvp);
                focus.incrementPosition();
                GroundedValue predicateValue = predicate.evaluate(c2).materialize();
                if (predicateValue.itemAt(0) instanceof NumericValue) {
                    for (Item it : predicateValue.asIterable()) {
                        if (it instanceof NumericValue) {
                            if (((NumericValue) it).isWholeNumber()
                                    && ((NumericValue) it).longValue() == position) {
                                result.put(pair.key(), pair.value());
                                break;
                            }
                        } else {
                            throw new XPathException("If the first item in a predicate is numeric, remaining items must also be numeric", "FORG0006");
                        }
                    }
                } else if (predicateValue.effectiveBooleanValue()) {
                    result.put(pair.key(), pair.value());
                }
            }
            return result.getCompletedMap(context);
        }

        private ArrayItem filteredArray(XPathContext context, ArrayItem input, SequenceEvaluator predicate) throws XPathException {
            ArrayItem result = SimpleArrayItem.EMPTY_ARRAY;
            int size = input.arrayLength();
            ManualIterator focus = new ManualIterator();
            XPathContext c2 = context.newMinorContext();
            c2.setCurrentIterator(focus);
            focus.setLengthFinder(() -> size);
            int position = 0;
            for (GroundedValue member : input.members()) {
                position++;
                Item contextItem = member.getLength() == 1 ? member.head() : new Parcel(member);
                focus.setContextItem(contextItem);
                focus.incrementPosition();
                GroundedValue predicateValue = predicate.evaluate(c2).materialize();
                if (predicateValue.getLength() == 0) {
                    continue;
                }
                if (predicateValue.itemAt(0) instanceof NumericValue) {
                    for (Item it : predicateValue.asIterable()) {
                        if (it instanceof NumericValue) {
                            if (((NumericValue) it).isWholeNumber()
                                    && ((NumericValue) it).longValue() == position) {
                                result = result.append(member);
                                break;
                            }
                        } else {
                            throw new XPathException("If the first item in a predicate is numeric, remaining items must also be numeric", "FORG0006");
                        }
                    }
                } else if (predicateValue.effectiveBooleanValue()) {
                    result = result.append(member);
                }
            }
            return result;
        }


    }
}

