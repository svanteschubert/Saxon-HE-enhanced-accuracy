////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
 * This instruction performs the node-counting function of the xsl:number instruction.
 * It delivers as its result a sequence of integers. The parent expression is typically
 * a {@link NumberSequenceFormatter} which takes this sequence of integers and performs
 * the necessary formatting.
 */

public class NumberInstruction extends Expression {

    public static final int SINGLE = 0;
    public static final int MULTI = 1;
    public static final int ANY = 2;
    public static final int SIMPLE = 3;
    public static final String[] LEVEL_NAMES = new String[]{"single", "multi", "any", "simple"};

    private Operand selectOp;

    private int level;
    private Operand countOp;
    private Operand fromOp;
    private boolean hasVariablesInPatterns = false;

    /**
     * Construct a NumberInstruction
     *
     * @param select the expression supplied in the select attribute
     * @param level  one of "single", "level", "multi"
     * @param count  the pattern supplied in the count attribute
     * @param from   the pattern supplied in the from attribute
     */

    public NumberInstruction(Expression select,
                             int level,
                             Pattern count,
                             Pattern from) {

        assert select != null;

        selectOp = new Operand(this, select, new OperandRole(0, OperandUsage.NAVIGATION, SequenceType.SINGLE_NODE));

        this.level = level;
        if (count != null) {
            countOp = new Operand(this, count, OperandRole.INSPECT);
        }
        if (from != null) {
            fromOp = new Operand(this, from, OperandRole.INSPECT);
        }
        this.hasVariablesInPatterns = Pattern.patternContainsVariable(count) || Pattern.patternContainsVariable(from);
    }

    /**
     * Ask whether this expression is an instruction. In XSLT streamability analysis this
     * is used to distinguish constructs corresponding to XSLT instructions from other constructs,
     * typically XPath expressions.
     *
     * @return true -- this construct originates as an XSLT instruction
     */
    @Override
    public boolean isInstruction() {
        return true;
    }

    @Override
    public Iterable<Operand> operands() {
        return operandSparseList(selectOp, countOp, fromOp);
    }

    /**
     * Get the level attribute
     *
     * @return the coded value of the level attribute
     */

    public int getLevel() {
        return level;
    }

    /**
     * Get the count pattern, if specified
     *
     * @return the count pattern if there is one, otherwise null
     */

    public Pattern getCount() {
        return countOp == null ? null : (Pattern) countOp.getChildExpression();
    }

    /**
     * Get the from pattern, if specified
     *
     * @return the from pattern if there is one, otherwise null
     */

    public Pattern getFrom() {
        return fromOp == null ? null : (Pattern) fromOp.getChildExpression();
    }

    /**
     * Get the select expression
     *
     * @return the select expression (which defaults to a ContextItemExpression)
     */

    public Expression getSelect() {
        return selectOp.getChildExpression();
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *      *                   that is used to update the bindings held in any
     *      *                   local variable references that are copied.
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        NumberInstruction exp = new NumberInstruction(copy(selectOp, rebindings), level,
                                                      copy(getCount(), rebindings),
                                                      copy(getFrom(), rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    private Expression copy(Operand op, RebindingMap rebindings) {
        return op == null ? null : op.getChildExpression().copy(rebindings);
    }

    private Pattern copy(Pattern op, RebindingMap rebindings) {
        return op == null ? null : op.copy(rebindings);
    }


    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return BuiltInAtomicType.INTEGER;
    }

    @Override
    public int computeCardinality() {
        switch (level) {
            case SIMPLE:
            case SINGLE:
            case ANY:
                return StaticProperty.ALLOWS_ZERO_OR_ONE;
            case MULTI:
            default:
                return StaticProperty.ALLOWS_ZERO_OR_MORE;
        }
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
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                    The parameter is set to null if it is known statically that the context item will be undefined.
     *                    If the type of the context item is not known statically, the argument is set to
     *                    {@link Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e = super.optimize(visitor, contextInfo);
        if (e != this) {
            return e;
        }
        if ("EE".equals(getPackageData().getTargetEdition())) {
            e = visitor.obtainOptimizer().optimizeNumberInstruction(this, contextInfo);
            if (e != null) {
                return e;
            }
        }
        return this;
    }

    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        List<IntegerValue> vec = new ArrayList<>(1);
        NodeInfo source = (NodeInfo) selectOp.getChildExpression().evaluateItem(context);

        switch (level) {
            case SIMPLE: {
                long value = Navigator.getNumberSimple(source, context);
                if (value != 0) {
                    vec.add(Int64Value.makeIntegerValue(value));
                }
                break;
            }
            case SINGLE: {
                long value = Navigator.getNumberSingle(source, getCount(), getFrom(), context);
                if (value != 0) {
                    vec.add(Int64Value.makeIntegerValue(value));
                }
                break;
            }
            case ANY: {
                long value = Navigator.getNumberAny(this, source, getCount(), getFrom(), context, hasVariablesInPatterns);
                if (value != 0) {
                    vec.add(Int64Value.makeIntegerValue(value));
                }
                break;
            }
            case MULTI: {
                for (long n : Navigator.getNumberMulti(source, getCount(), getFrom(), context)) {
                    vec.add(Int64Value.makeIntegerValue(n));
                }
                break;
            }
        }

        return new ListIterator<>(vec);
    }

    @Override
    public String getExpressionName() {
        return "xsl:number";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("nodeNum", this);
        out.emitAttribute("level", LEVEL_NAMES[level]);
        out.setChildRole("select");
        selectOp.getChildExpression().export(out);
        if (countOp != null) {
            out.setChildRole("count");
            getCount().export(out);
        }
        if (fromOp != null) {
            out.setChildRole("from");
            getFrom().export(out);
        }
        out.endElement();
    }
}

