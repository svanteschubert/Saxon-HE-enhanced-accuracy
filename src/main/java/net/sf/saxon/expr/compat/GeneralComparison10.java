////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.compat;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.functions.Number_1;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.PrependSequenceIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * GeneralComparison10: a boolean expression that compares two expressions
 * for equals, not-equals, greater-than or less-than. This implements the operators
 * =, !=, &lt;, &gt;, etc. This version of the class implements general comparisons
 * in XPath 1.0 backwards compatibility mode.
 */

public class GeneralComparison10 extends BinaryExpression implements Callable {

    protected int singletonOperator;
    protected AtomicComparer comparer;
    private boolean atomize0 = true;
    private boolean atomize1 = true;
    private boolean maybeBoolean0 = true;
    private boolean maybeBoolean1 = true;

    /**
     * Create a general comparison identifying the two operands and the operator
     *
     * @param p0 the left-hand operand
     * @param op the operator, as a token returned by the Tokenizer (e.g. Token.LT)
     * @param p1 the right-hand operand
     */

    public GeneralComparison10(Expression p0, int op, Expression p1) {
        super(p0, op, p1);
        singletonOperator = GeneralComparison.getCorrespondingSingletonOperator(op);
    }

    /**
     * Determine the static cardinality. Returns [1..1]
     */

    @Override
    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Type-check the expression
     *
     * @return the checked expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        getLhs().typeCheck(visitor, contextInfo);
        getRhs().typeCheck(visitor, contextInfo);

        StaticContext env = visitor.getStaticContext();
        StringCollator comp = visitor.getConfiguration().getCollation(env.getDefaultCollationName());
        if (comp == null) {
            comp = CodepointCollator.getInstance();
        }

        XPathContext context = env.makeEarlyEvaluationContext();
        comparer = new GenericAtomicComparer(comp, context);

        // evaluate the expression now if both arguments are constant

        if ((getLhsExpression() instanceof Literal) && (getRhsExpression() instanceof Literal)) {
            return Literal.makeLiteral(evaluateItem(context), this);
        }

        return this;
    }

    public void setAtomicComparer(AtomicComparer comparer) {
        this.comparer = comparer;
    }

    /**
     * Optimize the expression
     *
     * @return the checked expression
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        Configuration config = visitor.getConfiguration();
        StaticContext env = visitor.getStaticContext();

        getLhs().optimize(visitor, contextInfo);
        getRhs().optimize(visitor, contextInfo);

        // Neither operand needs to be sorted

        setLhsExpression(getLhsExpression().unordered(false, false));
        setRhsExpression(getRhsExpression().unordered(false, false));

        // evaluate the expression now if both arguments are constant

        if ((getLhsExpression() instanceof Literal) && (getRhsExpression() instanceof Literal)) {
            return Literal.makeLiteral(
                    evaluateItem(env.makeEarlyEvaluationContext()), this);
        }

        final TypeHierarchy th = config.getTypeHierarchy();
        ItemType type0 = getLhsExpression().getItemType();
        ItemType type1 = getRhsExpression().getItemType();

        if (type0.isPlainType()) {
            atomize0 = false;
        }
        if (type1.isPlainType()) {
            atomize1 = false;
        }

        if (th.relationship(type0, BuiltInAtomicType.BOOLEAN) == Affinity.DISJOINT) {
            maybeBoolean0 = false;
        }
        if (th.relationship(type1, BuiltInAtomicType.BOOLEAN) == Affinity.DISJOINT) {
            maybeBoolean1 = false;
        }

        if (!maybeBoolean0 && !maybeBoolean1) {
            // First atomize the operands where necessary. We didn't do this earlier because of the
            // special 1.0 (node-set=boolean) semantics, but if we don't have a boolean we can do it now.
            if (!(type0 instanceof AtomicType)) {
                setLhsExpression(Atomizer.makeAtomizer(getLhsExpression(), null).simplify());
                type0 = getLhsExpression().getItemType();
            }
            if (!(type1 instanceof AtomicType)) {
                setRhsExpression(Atomizer.makeAtomizer(getRhsExpression(), null).simplify());
                type1 = getRhsExpression().getItemType();
            }
            // Now consider numeric operands
            Affinity n0 = th.relationship(type0, NumericType.getInstance());
            Affinity n1 = th.relationship(type1, NumericType.getInstance());
            boolean maybeNumeric0 = n0 != Affinity.DISJOINT;
            boolean maybeNumeric1 = n1 != Affinity.DISJOINT;
            boolean numeric0 = n0 == Affinity.SUBSUMED_BY || n0 == Affinity.SAME_TYPE;
            boolean numeric1 = n1 == Affinity.SUBSUMED_BY || n1 == Affinity.SAME_TYPE;
            // Use the 2.0 path if we don't have to deal with the possibility of boolean values,
            // or the complications of converting values to numbers

            if (operator == Token.EQUALS || operator == Token.NE) {
                if ((!maybeNumeric0 && !maybeNumeric1) || (numeric0 && numeric1)) {
                    GeneralComparison gc = new GeneralComparison20(getLhsExpression(), operator, getRhsExpression());
                    gc.setRetainedStaticContext(getRetainedStaticContext());
                    gc.setAtomicComparer(comparer);
                    Expression binExp = visitor.obtainOptimizer()
                            .optimizeGeneralComparison(visitor, gc, false, contextInfo);
                    ExpressionTool.copyLocationInfo(this, binExp);
                    return binExp.typeCheck(visitor, contextInfo).optimize(visitor, contextInfo);
                }
            } else if (numeric0 && numeric1) {
                GeneralComparison gc = new GeneralComparison20(getLhsExpression(), operator, getRhsExpression());
                gc.setRetainedStaticContext(getRetainedStaticContext());
                Expression binExp = visitor.obtainOptimizer()
                        .optimizeGeneralComparison(visitor, gc, false, contextInfo);
                ExpressionTool.copyLocationInfo(this, binExp);
                return binExp.typeCheck(visitor, contextInfo).optimize(visitor, contextInfo);
            }
        }

        return this;
    }


    /**
     * Evaluate the expression in a given context
     *
     * @param context the given context for evaluation
     * @return a BooleanValue representing the result of the numeric comparison of the two operands
     */

    @Override
    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        return BooleanValue.get(effectiveBooleanValue(context));
    }

    /**
     * Evaluate the expression: interface for use by compiled bytecode
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws XPathException if a dynamic error occurs during the evaluation of the expression
     */

    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        return BooleanValue.get(effectiveBooleanValue(arguments[0].iterate(), arguments[1].iterate(), context));
    }

    /**
     * Evaluate the expression giving a boolean result
     *
     * @param context the given context for evaluation
     * @return a boolean representing the result of the comparison of the two operands
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        return effectiveBooleanValue(getLhsExpression().iterate(context), getRhsExpression().iterate(context), context);
    }

    /**
     * Evaluate the expression giving a boolean result
     *
     * @param iter0   iterator over the value of the first argument
     * @param iter1   iterator over the value of the second argument
     * @param context the given context for evaluation
     * @return a boolean representing the result of the comparison of the two operands
     * @throws XPathException if an error occurs evaluating either operand or if the results are not comparable
     */

    private boolean effectiveBooleanValue(SequenceIterator iter0, SequenceIterator iter1, XPathContext context)
            throws XPathException {

        // If the first operand is a singleton boolean,
        // compare it with the effective boolean value of the other operand

        //boolean iter0used = false;
        boolean iter1used = false;

        if (maybeBoolean0) {
            Item i01 = iter0.next();
            Item i02 = i01 == null ? null : iter0.next();
            if (i01 instanceof BooleanValue && i02 == null) {
                iter0.close();
                boolean b = ExpressionTool.effectiveBooleanValue(iter1);
                return compare((BooleanValue) i01, singletonOperator, BooleanValue.get(b), comparer, context);
            }
            if (i01 == null && !maybeBoolean1) {
                iter0.close();
                return false;
            }
            // Reconstitute the original iterator
            if (i02 != null) {
                iter0 = new PrependSequenceIterator(i02, iter0);
            }
            if (i01 != null) {
                iter0 = new PrependSequenceIterator(i01, iter0);
            }
        }

        // If the second operand is a singleton boolean,
        // compare it with the effective boolean value of the other operand

        if (maybeBoolean1) {
            Item i11 = iter1.next();
            Item i12 = i11 == null ? null : iter1.next();
            if (i11 instanceof BooleanValue && i12 == null) {
                iter1.close();
                boolean b = ExpressionTool.effectiveBooleanValue(iter0);
                return compare(BooleanValue.get(b), singletonOperator, (BooleanValue) i11, comparer, context);
            }
            if (i11 == null && !maybeBoolean0) {
                iter1.close();
                return false;
            }
            // Reconstitute the original iterator
            if (i12 != null) {
                iter1 = new PrependSequenceIterator(i12, iter1);
            }
            if (i11 != null) {
                iter1 = new PrependSequenceIterator(i11, iter1);
            }
        }

        // Atomize both operands where necessary

        if (atomize0) {
            iter0 = Atomizer.getAtomizingIterator(iter0, false);
        }

        if (atomize1) {
            iter1 = Atomizer.getAtomizingIterator(iter1, false);
        }

        // If either iterator is known to be empty, quit now

        if (iter0 instanceof EmptyIterator || iter1 instanceof EmptyIterator) {
            return false;
        }

        // If the operator is one of <, >, <=, >=, then convert both operands to sequences of xs:double
        // using the number() function

        if (operator == Token.LT || operator == Token.LE || operator == Token.GT || operator == Token.GE) {
            final Configuration config = context.getConfiguration();
            ItemMappingFunction map = new ItemMappingFunction() {
                @Override
                public DoubleValue mapItem(Item item) throws XPathException {
                    return Number_1.convert((AtomicValue)item, config);
                }
            };
            iter0 = new ItemMappingIterator(iter0, map, true);
            iter1 = new ItemMappingIterator(iter1, map, true);
        }

        // Compare all pairs of atomic values in the two atomized sequences

        List<AtomicValue> seq1 = null;
        AtomicValue item0;
        while ((item0 = (AtomicValue) iter0.next()) != null) {
            if (iter1 != null) {
                while (true) {
                    AtomicValue item1 = (AtomicValue) iter1.next();
                    if (item1 == null) {
                        iter1 = null;
                        if (seq1 == null) {
                            // second operand is empty
                            return false;
                        }
                        break;
                    }
                    try {
                        if (compare(item0, singletonOperator, item1, comparer, context)) {
                            return true;
                        }
                        if (seq1 == null) {
                            seq1 = new ArrayList<AtomicValue>(40);
                        }
                        seq1.add(item1);
                    } catch (XPathException e) {
                        // re-throw the exception with location information added
                        e.maybeSetLocation(getLocation());
                        e.maybeSetContext(context);
                        throw e;
                    }
                }
            } else {
                for (AtomicValue item1 : seq1) {
                    if (compare(item0, singletonOperator, item1, comparer, context)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        GeneralComparison10 gc = new GeneralComparison10(getLhsExpression().copy(rebindings), operator, getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, gc);
        gc.setRetainedStaticContext(getRetainedStaticContext());
        gc.comparer = comparer;
        gc.atomize0 = atomize0;
        gc.atomize1 = atomize1;
        gc.maybeBoolean0 = maybeBoolean0;
        gc.maybeBoolean1 = maybeBoolean1;
        return gc;
    }

    /**
     * Compare two atomic values
     *
     * @param a0       the first value to be compared
     * @param operator the comparison operator
     * @param a1       the second value to be compared
     * @param comparer the comparer to be used (perhaps including a collation)
     * @param context  the XPath dynamic context
     * @return the result of the comparison
     * @throws XPathException if the values are not comparable
     */

    private static boolean compare(AtomicValue a0,
                                   int operator,
                                   AtomicValue a1,
                                   AtomicComparer comparer,
                                   XPathContext context) throws XPathException {

        comparer = comparer.provideContext(context);
        ConversionRules rules = context.getConfiguration().getConversionRules();

        BuiltInAtomicType t0 = a0.getPrimitiveType();
        BuiltInAtomicType t1 = a1.getPrimitiveType();

        // If either operand is a number, convert both operands to xs:double using
        // the rules of the number() function, and compare them

        if (t0.isPrimitiveNumeric() || t1.isPrimitiveNumeric()) {
            DoubleValue v0 = Number_1.convert(a0, context.getConfiguration());
            DoubleValue v1 = Number_1.convert(a1, context.getConfiguration());
            return ValueComparison.compare(v0, operator, v1, comparer, false);
        }

        // If either operand is a string, or if both are untyped atomic, convert
        // both operands to strings and compare them

        if (t0.equals(BuiltInAtomicType.STRING) || t1.equals(BuiltInAtomicType.STRING) ||
                (t0.equals(BuiltInAtomicType.UNTYPED_ATOMIC) && t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC))) {
            StringValue s0 = StringValue.makeStringValue(a0.getStringValueCS());
            StringValue s1 = StringValue.makeStringValue(a1.getStringValueCS());
            return ValueComparison.compare(s0, operator, s1, comparer, false);
        }

        // If either operand is untyped atomic,
        // convert it to the type of the other operand, and compare

        if (t0.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            a0 = t1.getStringConverter(rules).convert((StringValue)a0).asAtomic();
        }

        if (t1.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            a1 = t0.getStringConverter(rules).convert((StringValue)a1).asAtomic();
        }

        return ValueComparison.compare(a0, operator, a1, comparer, false);
    }

    /**
     * Determine the data type of the expression
     *
     * @return Type.BOOLEAN
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return BuiltInAtomicType.BOOLEAN;
    }


    @Override
    protected void explainExtraAttributes(ExpressionPresenter out) {
        out.emitAttribute("cardinality", "many-to-many (1.0)");
        out.emitAttribute("comp", comparer.save());
    }

    @Override
    public String tag() {
        return "gc10";
    }
}

