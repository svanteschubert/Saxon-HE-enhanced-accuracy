////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.oper.OperandArray;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.JavaExternalObjectType;
import net.sf.saxon.value.SequenceType;

import java.util.Collections;

/**
 * Abstract superclass for calls to system-defined and user-defined functions
 */

public abstract class FunctionCall extends Expression {

    private OperandArray operanda;

    /**
     * Set the data structure for the operands of this expression. This must be created during initialisation of the
     * expression and must not be subsequently changed
     *
     * @param operanda the data structure for expression operands
     */

    protected void setOperanda(OperandArray operanda) {
        this.operanda = operanda;
    }

    /**
     * Get the data structure holding the operands of this expression.
     *
     * @return the data structure holding expression operands
     */

    public OperandArray getOperanda() {
        return operanda;
    }

    @Override
    public Iterable<Operand> operands() {
        if (operanda != null) {
            return operanda.operands();
        } else {
            // happens during expression tree construction
            return Collections.emptyList();
        }
    }

    /**
     * Get the target function to be called
     *
     * @param context the dynamic evaluation context
     * @return the target function
     * @throws XPathException if the target function cannot be determined
     */

    public abstract Function getTargetFunction(XPathContext context) throws XPathException;

    /**
     * Get the qualified of the function being called
     *
     * @return the qualified name. May be null if the function is anonymous.
     */

    public abstract StructuredQName getFunctionName();

    /**
     * Determine the number of actual arguments supplied in the function call
     *
     * @return the arity (the number of arguments)
     */

    public final int getArity() {
        return getOperanda().getNumberOfOperands();
    }

    /**
     * Method called by the expression parser when all arguments have been supplied
     *
     * @param args the expressions contained in the argument list of the function call
     */

    public void setArguments(Expression[] args) {
        setOperanda(new OperandArray(this, args));
    }

    protected void setOperanda(Expression[] args, OperandRole[] roles) {
        setOperanda(new OperandArray(this, args, roles));
    }

    /**
     * Get the expressions supplied as actual arguments to the function
     *
     * @return the array of expressions supplied in the argument list of the function call. The array
     * is newly constructed to ensure that modifications to the array have no effect.
     */

    public Expression[] getArguments() {
        Expression[] result = new Expression[getArity()];
        int i = 0;
        for (Operand o : operands()) {
            result[i++] = o.getChildExpression();
        }
        return result;
    }

    /**
     * Get the expression supplied as the Nth argument
     *
     * @param n the required argument, zero-based
     * @return the expression supplied in the relevant position
     * @throws java.lang.IllegalArgumentException if the value of n is out of range
     */

    public Expression getArg(int n) {
        return getOperanda().getOperandExpression(n);
    }

    /**
     * Set the expression to be used as the Nth argument
     *
     * @param n the required argument, zero-based
     * @param child the expression to be used in the relevant position
     * @throws java.lang.IllegalArgumentException if the value of n is out of range
     */

    public void setArg(int n, Expression child) {
        getOperanda().setOperand(n, child);
        adoptChildExpression(child);
    }

    /**
     * Simplify the arguments of the function.
     * Called from the simplify() method of each function.
     *
     * @param env the static context
     * @return the result of simplifying the arguments of the expression
     * @throws net.sf.saxon.trans.XPathException if an error occurs
     */

    protected final Expression simplifyArguments(StaticContext env) throws XPathException {
        for (int i = 0; i < getArguments().length; i++) {
            Expression exp = getArg(i).simplify();
            if (exp != getArg(i)) {
                adoptChildExpression(exp);
                setArg(i, exp);
            }
        }
        return this;
    }

    /**
     * Type-check the expression. This also calls preEvaluate() to evaluate the function
     * if all the arguments are constant; functions that do not require this behavior
     * can override the preEvaluate method.
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        checkArguments(visitor);
        return preEvaluateIfConstant(visitor);
    }

    protected Expression preEvaluateIfConstant(ExpressionVisitor visitor) throws XPathException {
        Optimizer opt = visitor.obtainOptimizer();
        if (opt.isOptionSet(OptimizerOptions.CONSTANT_FOLDING)) {
            boolean fixed = true;
            for (Operand o : operands()) {
                if (!(o.getChildExpression() instanceof Literal)) {
                    fixed = false;
                }
            }
            if (fixed) {
                try {
                    return preEvaluate(visitor);
                } catch (NoDynamicContextException err) {
                    // Early evaluation failed, typically because the implicit timezone is not yet known.
                    // Try again later at run-time.
                    return this;
                }
            }
        }
        return this;
    }

    /**
     * Check the function call against the declared function signature, applying the
     * function conversion rules to each argument as necessary
     *
     * @param target  the function being called
     * @param visitor an expression visitor
     * @throws XPathException if there is a type error
     */

    public void checkFunctionCall(Function target,
                                  ExpressionVisitor visitor) throws XPathException {
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(visitor.getStaticContext().isInBackwardsCompatibleMode());
        SequenceType[] argTypes = target.getFunctionItemType().getArgumentTypes();
        int n = target.getArity();
        for (int i = 0; i < n; i++) {
            String name = getFunctionName() == null ? "" : getFunctionName().getDisplayName();
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION, name, i);
            setArg(i, tc.staticTypeCheck(
                    getArg(i),
                    argTypes[i],
                    role, visitor));
        }
    }


    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        optimizeChildren(visitor, contextItemType);

        Optimizer opt = visitor.obtainOptimizer();
        if (opt.isOptionSet(OptimizerOptions.CONSTANT_FOLDING)) {
            boolean fixed = true;
            for (Operand o : operands()) {
                if (!(o.getChildExpression() instanceof Literal)) {
                    fixed = false;
                    break;
                }
            }
            if (fixed) {
                return preEvaluate(visitor);
            }
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
     */
    @Override
    public int getNetCost() {
        return 5;
    }

    /**
     * Pre-evaluate a function at compile time. Functions that do not allow
     * pre-evaluation, or that need access to context information, can override this method.
     *
     * @param visitor an expression visitor
     * @return the result of the early evaluation, or the original expression, or potentially
     * a simplified expression
     * @throws net.sf.saxon.trans.XPathException if evaluation fails
     */

    public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
        if ((getIntrinsicDependencies() & ~StaticProperty.DEPENDS_ON_STATIC_CONTEXT) != 0) {
            return this;
        }
        try {
            Literal lit = Literal.makeLiteral(iterate(visitor.getStaticContext().makeEarlyEvaluationContext()).materialize(), this);
            Optimizer.trace(visitor.getConfiguration(), "Pre-evaluated function call " + toShortString(), lit);
            return lit;
        } catch (NoDynamicContextException e) {
            // early evaluation failed, usually because implicit timezone required
            return this;
        } catch (UnsupportedOperationException e) {
            //e.printStackTrace();
            if (e.getCause() instanceof NoDynamicContextException) {
                return this;
            } else {
                throw e;
            }
        }
    }

    /**
     * Method supplied by each class of function to check arguments during parsing, when all
     * the argument expressions have been read
     *
     * @param visitor the expression visitor
     * @throws net.sf.saxon.trans.XPathException if the arguments are incorrect
     */

    protected void checkArguments(ExpressionVisitor visitor) throws XPathException {
        // default: do nothing
    }

    /**
     * Check number of arguments. <BR>
     * A convenience routine for use in subclasses.
     *
     * @param min the minimum number of arguments allowed
     * @param max the maximum number of arguments allowed
     * @return the actual number of arguments
     * @throws net.sf.saxon.trans.XPathException if the number of arguments is out of range
     */

    protected int checkArgumentCount(int min, int max) throws XPathException {
        int numArgs = getArity();
        String msg = null;
        if (min == max && numArgs != min) {
            msg = "Function " + getDisplayName() + " must have " + pluralArguments(min);
        } else if (numArgs < min) {
            msg = "Function " + getDisplayName() + " must have at least " + pluralArguments(min);
        } else if (numArgs > max) {
            msg = "Function " + getDisplayName() + " must have no more than " + pluralArguments(max);
        }
        if (msg != null) {
            XPathException err = new XPathException(msg, "XPST0017");
            err.setIsStaticError(true);
            err.setLocation(getLocation());
            throw err;
        }
        return numArgs;
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
     * Utility routine used in constructing error messages: get the word "argument" or "arguments"
     *
     * @param num the number of arguments
     * @return the singular or plural word
     */

    public static String pluralArguments(int num) {
        return num == 1 ? "one argument" : (num + " arguments");
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
     * @param pathMap      the PathMap to which the expression should be added
     * @param pathMapNodes the node in the PathMap representing the focus at the point where this expression
     *                     is called. Set to null if this expression appears at the top level, in which case the expression, if it
     *                     is registered in the path map at all, must create a new path map root.
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     * expression is the first operand of a path expression or filter expression. For an expression that does
     * navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     * expressions, it is the same as the input pathMapNode.
     */

    public PathMap.PathMapNodeSet addExternalFunctionCallToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodes) {
        // Except in the case of system functions, we have no idea where a function call might
        // navigate, so we assume the worst, and register that the path has unknown dependencies
        PathMap.PathMapNodeSet result = new PathMap.PathMapNodeSet();
        for (Operand o : operands()) {
            result.addNodeSet(o.getChildExpression().addToPathMap(pathMap, pathMapNodes));
        }
        result.setHasUnknownDependencies();
        return result;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */

    @Override
    public String getExpressionName() {
        return "functionCall";
    }

    /**
     * Get the name of the function for display in messages
     *
     * @return the name of the function as a lexical QName
     */

    public final String getDisplayName() {
        StructuredQName fName = getFunctionName();
        return fName == null ? "(anonymous)" : fName.getDisplayName();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        FastStringBuffer buff = new FastStringBuffer(FastStringBuffer.C64);
        StructuredQName fName = getFunctionName();
        String f;
        if (fName == null) {
            f = "$anonymousFunction";
        } else if (fName.hasURI(NamespaceConstant.FN)) {
            f = fName.getLocalPart();
        } else {
            f = fName.getEQName();
        }
        buff.append(f);
        boolean first = true;
        for (Operand o : operands()) {
            buff.append(first ? "(" : ", ");
            buff.append(o.getChildExpression().toString());
            first = false;
        }
        buff.append(first ? "()" : ")");
        return buff.toString();
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        StructuredQName fName = getFunctionName();
        return (fName == null ? "$anonFn" : fName.getDisplayName()) +
                "(" + (getArity() == 0 ? "" : "...") + ")";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("functionCall", this);
        if (getFunctionName() == null) {
            throw new AssertionError("Exporting call to anonymous function");
        } else {
            out.emitAttribute("name", getFunctionName().getDisplayName());
        }
        for (Operand o : operands()) {
            o.getChildExpression().export(out);
        }
        out.endElement();
    }

    /**
     * Determine whether two expressions are equivalent
     */

    public boolean equals(Object o) {
        if (!(o instanceof FunctionCall)) {
            return false;
        }
        if (getFunctionName() == null) {
            return this == o;
        }
        FunctionCall f = (FunctionCall) o;
        if (!getFunctionName().equals(f.getFunctionName())) {
            return false;
        }
        if (getArity() != f.getArity()) {
            return false;
        }
        for (int i = 0; i < getArity(); i++) {
            if (!getArg(i).isEqual(f.getArg(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get hashCode in support of equals() method
     */

    @Override
    public int computeHashCode() {
        if (getFunctionName() == null) {
            return super.computeHashCode();
        }
        int h = getFunctionName().hashCode();
        for (int i = 0; i < getArity(); i++) {
            h ^= getArg(i).hashCode();
        }
        return h;
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     * of the expression
     * @throws net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *                                           expression
     */
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        Function target = getTargetFunction(context);
        Sequence[] actualArgs = evaluateArguments(context);
        try {
            return target.call(context, actualArgs).iterate();
        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            e.maybeSetFailingExpression(this);
            throw e;
        }
    }

    public Sequence[] evaluateArguments(XPathContext context) throws XPathException {
        int numArgs = getArity();
        Sequence[] actualArgs = new Sequence[numArgs];
        for (int i = 0; i < numArgs; i++) {
            actualArgs[i] = ExpressionTool.lazyEvaluate(getArg(i), context, false);
        }
        return actualArgs;
    }

   /**
     * When a call to a Java extension function appears in a context where there the required type
     * is a Java external object (typically an xsl:variable with a declared type, or as an argument
     * to another Java extension function), notify this required type so that the process of converting
     * the result to an XDM value can be short-circuited.
     *
     * @param requiredType the required type of the result of the function, determined by the context
     *                     in which the function call appears
     * @return Ok if the type has been successfully adjusted
     * @throws XPathException if the required return type is incompatible with the type actually returned
     *                        by the Java method
     */

    public boolean adjustRequiredType(JavaExternalObjectType requiredType) throws XPathException {
        return false;
    }

}

