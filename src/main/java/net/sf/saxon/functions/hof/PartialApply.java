////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AnyFunctionType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This expression class implements the operation of currying (or performing partial application) of a function.
 * That is, it takes a function and a set of argument bindings as input, and produces a new function as output.
 */

public class PartialApply extends Expression {

    private Operand baseOp;
    private Operand[] boundArgumentsOp; // contains null where the question marks appear

    /**
     * Create a partial function application expression
     *
     * @param base           the expression that returns the function to be called
     * @param boundArguments an array in which each element is either an expression to be bound to the corresponding
     *                       argument, or null to represent a placeholder ("?" in the source syntax)
     */

    public PartialApply(Expression base, Expression[] boundArguments) {
        baseOp = new Operand(this, base, OperandRole.INSPECT);
        adoptChildExpression(base);
        boundArgumentsOp = new Operand[boundArguments.length];
        for (int i=0; i<boundArguments.length; i++) {
            if (boundArguments[i] != null) {
                boundArgumentsOp[i] = new Operand(this, boundArguments[i], OperandRole.NAVIGATE);
                adoptChildExpression(boundArguments[i]);
            }
        }
    }

    public Expression getBaseExpression() {
        return baseOp.getChildExpression();
    }

    public void setBaseExpression(Expression base) {
        baseOp.setChildExpression(base);
    }

    public int getNumberOfPlaceHolders() {
        int n = 0;
        for (Operand o : boundArgumentsOp) {
            if (o == null) {
                n++;
            }
        }
        return n;
    }


    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);

        ItemType baseType = getBaseExpression().getItemType();


        SequenceType requiredFunctionType;
        SequenceType[] argTypes = new SequenceType[boundArgumentsOp.length];
        Arrays.fill(argTypes, SequenceType.ANY_SEQUENCE);

        TypeChecker tc = visitor.getConfiguration().getTypeChecker(false);
        for (int i = 0; i < boundArgumentsOp.length; i++) {
            final Operand op = boundArgumentsOp[i];
            if (op != null) {
                Expression arg = op.getChildExpression();
                if (baseType instanceof SpecificFunctionType && i < ((SpecificFunctionType) baseType).getArity()) {
                    RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION, "saxon:call", i);
                    SequenceType requiredArgType = ((SpecificFunctionType) baseType).getArgumentTypes()[i];
                    argTypes[i] = requiredArgType;
                    Expression a3 = tc.staticTypeCheck(
                            arg, requiredArgType, role, visitor);
                    if (a3 != arg) {
                        op.setChildExpression(a3);
                    }
                }
            }
        }


        requiredFunctionType = SequenceType.makeSequenceType(
                new SpecificFunctionType(argTypes,
                        (baseType instanceof AnyFunctionType) ? ((AnyFunctionType) baseType).getResultType() : SequenceType.ANY_SEQUENCE),
                        StaticProperty.EXACTLY_ONE);

        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION, "saxon:call", 0);
        setBaseExpression(tc.staticTypeCheck(getBaseExpression(), requiredFunctionType, role, visitor));
        return this;
    }

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        ItemType baseItemType = getBaseExpression().getItemType();
        SequenceType resultType = SequenceType.ANY_SEQUENCE;
        if (baseItemType instanceof SpecificFunctionType) {
            resultType = ((SpecificFunctionType) baseItemType).getResultType();
        }
        int placeholders = getNumberOfPlaceHolders();
        SequenceType[] argTypes = new SequenceType[placeholders];
        if (baseItemType instanceof SpecificFunctionType) {
            for (int i = 0, j = 0; i < boundArgumentsOp.length; i++) {
                if (boundArgumentsOp[i] == null) {
                    argTypes[j++] = ((SpecificFunctionType) baseItemType).getArgumentTypes()[i];
                }
            }
        } else {
            Arrays.fill(argTypes, SequenceType.ANY_SEQUENCE);
        }
        return new SpecificFunctionType(argTypes, resultType);
    }

    @Override
    public Iterable<Operand> operands() {
        List<Operand> operanda = new ArrayList<Operand>(boundArgumentsOp.length + 1);
        operanda.add(baseOp);
        for (Operand o : boundArgumentsOp) {
            if (o != null) {
                operanda.add(o);
            }
        }
        return operanda;
    }

    public int getNumberOfArguments() {
        return boundArgumentsOp.length;
    }

    public Expression getArgument(int n) {
        Operand o = boundArgumentsOp[n];
        return o==null ? null : o.getChildExpression();
    }

    @Override
    public int getImplementationMethod() {
        return EVALUATE_METHOD;
    }

    /**
     * Is this expression the same as another expression?
     *
     * @param other the expression to be compared with this one
     * @return true if the two expressions are statically equivalent
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PartialApply)) {
            return false;
        } else {
            PartialApply pa2 = (PartialApply)other;
            if (!getBaseExpression().isEqual(pa2.getBaseExpression())) {
                return false;
            }
            if (boundArgumentsOp.length != pa2.boundArgumentsOp.length) {
                return false;
            }
            for (int i=0; i<boundArgumentsOp.length; i++) {
                if ((boundArgumentsOp[i] == null) != (pa2.boundArgumentsOp[i] == null)) {
                    return false;
                }
                if (boundArgumentsOp[i] != null && !boundArgumentsOp[i].equals(pa2.boundArgumentsOp[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Hashcode supporting equals()
     */

    @Override
    public int computeHashCode() {
        int h = 0x836b92a0;
        int i = 0;
        for (Operand o : operands()) {
            h ^= o == null ? i++ : o.getChildExpression().hashCode();
        }
        return h;
    }

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("partialApply", this);
        getBaseExpression().export(out);
        for (Operand o : boundArgumentsOp) {
            if (o == null) {
                out.startElement("null", this);
                out.endElement();
            } else {
                o.getChildExpression().export(out);
            }
        }
        out.endElement();
    }

    @Override
    protected int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        Expression[] boundArgumentsCopy = new Expression[boundArgumentsOp.length];
        for (int i = 0; i < boundArgumentsOp.length; i++) {
            if (boundArgumentsOp[i] == null) {
                boundArgumentsCopy[i] = null;
            } else {
                boundArgumentsCopy[i] = boundArgumentsOp[i].getChildExpression().copy(rebindings);
            }
        }
        PartialApply exp = new PartialApply(getBaseExpression().copy(rebindings), boundArgumentsCopy);
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression. The expression produced should be equivalent to the original making certain
     * assumptions about the static context. In general the expansion will make no assumptions about namespace bindings,
     * except that (a) the prefix "xs" is used to refer to the XML Schema namespace, and (b) the default funcion namespace
     * is assumed to be the "fn" namespace.</p>
     * <p>In the case of XSLT instructions and XQuery expressions, the toString() method gives an abstracted view of the syntax
     * that is not designed in general to be parseable.</p>
     *
     * @return a representation of the expression as a string
     */
    @Override
    public String toString() {
        FastStringBuffer buff = new FastStringBuffer(FastStringBuffer.C64);
        boolean par = getBaseExpression().operands().iterator().hasNext();
        if (par) {
            buff.append("(" + getBaseExpression().toString() + ")");
        } else {
            buff.append(getBaseExpression().toString());
        }
        buff.append("(");
        for (int i = 0; i < boundArgumentsOp.length; i++) {
            if (boundArgumentsOp[i] == null) {
                buff.append("?");
            } else {
                buff.append(boundArgumentsOp[i].getChildExpression().toString());
            }
            if (i != boundArgumentsOp.length - 1) {
                buff.append(", ");
            }
        }
        buff.append(")");
        return buff.toString();
    }

    /**
     * Evaluate this expression at run-time
     *
     * @param context The XPath dynamic evaluation context
     * @return the result of the function, or null to represent an empty sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during evaluation of the function.
     */

    @Override
    public Function evaluateItem(XPathContext context) throws XPathException {
        Function f = (Function) getBaseExpression().evaluateItem(context);
        assert f != null;
        Sequence[] values = new Sequence[boundArgumentsOp.length];
        for (int i = 0; i < boundArgumentsOp.length; i++) {
            if (boundArgumentsOp[i] == null) {
                values[i] = null;
            } else {
                values[i] = boundArgumentsOp[i].getChildExpression().iterate(context).materialize();
            }
        }
        return new CurriedFunction(f, values);
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "partialApply";
    }


}

// Copyright (c) 2011-2020 Saxonica Limited
