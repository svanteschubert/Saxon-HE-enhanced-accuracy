////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.KeyFn;
import net.sf.saxon.functions.SuperId;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeSetPattern;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.Traceable;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.jiter.MonoIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntIterator;

import java.net.URI;
import java.util.*;

/**
 * Interface supported by an XPath expression. This includes both compile-time
 * and run-time methods.
 *
 * <p>Two expressions are considered equal if they return the same result when evaluated in the
 * same context.</p>
 *
 * <p>Expressions manage their own subexpressions but must observe certain conventions when doing
 * so. Every expression must implement the methods operands() and getOperand(N) to access the
 * operands of the expression; here N is an integer identifier for the subexpression which
 * is unique among the subexpressions and whose allocation is a matter for each expression
 * to determined. So a subexpression of an expression can change (during optimization rewrite,
 * for example), then (a) the expression must implement the method setOperandExpression(N, expr)
 * to effect the change, and (b) calling this method must cause a call on adoptChildExpression(N, expr)
 * which is implemented in the expression class itself, and takes responsibility for maintaining
 * the consistency of the tree, e.g. by invalidating cached information.</p>
 *
 * <p>The default implementations of methods such as operands() work for expressions that have
 * no subexpressions.</p>
 */

public abstract class Expression implements IdentityComparable, ExportAgent, Locatable, Traceable {

    public static final int EVALUATE_METHOD = 1;
    public static final int ITERATE_METHOD = 2;
    public static final int PROCESS_METHOD = 4;
    public static final int WATCH_METHOD = 8;
    public static final int ITEM_FEED_METHOD = 16;
    public static final int EFFECTIVE_BOOLEAN_VALUE = 32;
    public static final int UPDATE_METHOD = 64;


    protected int staticProperties = -1;
    private Location location = Loc.NONE;
    private Expression parentExpression;
    private RetainedStaticContext retainedStaticContext;
    private int[] slotsUsed;
    private int evaluationMethod;
    private Map<String, Object> extraProperties;
    private double cost = -1;
    private int cachedHashCode = -1;

//    public int serial;  // used to identify expressions for internal diagnostics
//    private static int nextSerial = 0;

    public Expression() {
        //serial = nextSerial++;
    }


    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in export() output displaying the expression.
     */

    public String getExpressionName() {
        return getClass().getSimpleName();
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * works off the results of iterateSubExpressions()
     *
     * <p>If the expression is a Callable, then it is required that the order of the operands
     * returned by this function is the same as the order of arguments supplied to the corresponding
     * call() method.</p>
     *
     * @return an iterator containing the sub-expressions of this expression
     */

    /*@NotNull*/
    public Iterable<Operand> operands() {
        return Collections.emptyList();
    }

    /**
     * Get the interpreted form of the expression. Normally this returns the expression unchanged;
     * but on a CompiledExpression (the result of bytecode generation) it returns the original
     * interpreted form.
     * @return the interpreted form of this expression
     */

    public Expression getInterpretedExpression() {
        return this;
    }

    /**
     * Get the immediate sub-expressions of this expression, verifying that the parent pointers
     * in the child expressions are correct.
     * @return the list of sub-expressions, in the same way as with the {@link #operands()} method,
     * but with additional checking
     */

    public final Iterable<Operand> checkedOperands() {
        Iterable<Operand> ops = operands();
        for (Operand o : ops) {
            Expression child = o.getChildExpression();
            boolean badOperand = o.getParentExpression() != this;
            boolean badExpression = child.getParentExpression() != this;
            if (badOperand || badExpression) {
                String message = "*** Bad parent pointer found in " +
                        (badOperand ? "operand " : "expression ") +
                        child.toShortString() +
                        " at " + child.getLocation().getSystemId() + "#" + child.getLocation().getLineNumber() + " ***";
                try {
                    Configuration config = getConfiguration();
                    Logger logger = config == null ? null : config.getLogger();
                    if (logger != null) {
                        logger.warning(message);
                    } else {
                        throw new IllegalStateException(message);
                    }
                } catch (Exception err) {
                    throw new IllegalStateException(message);
                }
                child.setParentExpression(Expression.this);
            }
            if (child.getRetainedStaticContext() == null) {
                child.setRetainedStaticContext(getRetainedStaticContext());
            }
        }
        return ops;
    }

    /**
     * Helper method for subclasses to build a list of operands
     * @param a the sequence of operands (which must all be non-null)
     * @return a list of operands
     */

    protected List<Operand> operandList(Operand... a) {
        return Arrays.asList(a);
    }

    /**
     * Helper method for subclasses to build a list of operands
     * @param a the sequence of operands; any null values in the list are ignored
     * @return a list of operands
     */

    protected List<Operand> operandSparseList(Operand... a) {
        List<Operand> operanda = new ArrayList<>();
        for (Operand o : a) {
            if (o != null) {
                operanda.add(o);
            }
        }
        return operanda;
    }

    /**
     * Get the parent expression of this expression in the expression tree.
     * @return the parent expression. Null if at the root of the tree, for example,
     * the expression representing the body of a function or template.
     */

    public Expression getParentExpression() {
        return parentExpression;
    }

    /**
     * Set the parent expression of this expression in the expression tree.
     * @param parent the parent expression
     */

    public void setParentExpression(Expression parent) {
//        if (parent != null && parent != parentExpression) {
//            System.err.println("Set parent of " + this.getClass() + serial + " to " + parent.getClass() + parent.serial);
//        }
        parentExpression = parent;
    }

    /**
     * Verify that parent pointers are correct throughout the subtree rooted at this expression
     * @throws IllegalStateException if invalid parent pointers are found
     * @return the expression
     */

    public Expression verifyParentPointers() throws IllegalStateException {
        for (Operand o : operands()) {
            Expression parent = o.getChildExpression().getParentExpression();
            if (parent != this) {
                throw new IllegalStateException("Invalid parent pointer in " + parent.toShortString() +
                                                        " subexpression " + o.getChildExpression().toShortString());
            }
            if (o.getParentExpression() != this) {
                throw new IllegalStateException("Invalid parent pointer in operand object " + parent.toShortString() +
                                                        " subexpression " + o.getChildExpression().toShortString());
            }
            if (ExpressionTool.findOperand(parent, o.getChildExpression()) == null) {
                throw new IllegalStateException("Incorrect parent pointer in " + parent.toShortString() +
                                                        " subexpression " + o.getChildExpression().toShortString());
            }
            o.getChildExpression().verifyParentPointers();
        }
        return this;
    }

    /**
     * Restore parent pointers for the subtree rooted at this expression
     */

    public void restoreParentPointers() {
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            child.setParentExpression(Expression.this);
            child.restoreParentPointers();
        }
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     *         {@link #PROCESS_METHOD}
     */

    public abstract int getImplementationMethod();

    /**
     * Determine whether this expression implements its own method for static type checking
     *
     * @return true if this expression has a non-trivial implementation of the staticTypeCheck()
     *         method
     */

    public boolean implementsStaticTypeCheck() {
        return false;
    }

    /**
     * Ask whether this expression is, or contains, the binding of a given variable
     * @param binding the variable binding
     * @return true if this expression is the variable binding (for example a ForExpression
     * or LetExpression) or if it is a FLWOR expression that binds the variable in one of its
     * clauses.
     */

    public boolean hasVariableBinding(Binding binding) {
        return false;
    }

    /**
     * Ask whether the expression can be lifted out of a loop, assuming it has no dependencies
     * on the controlling variable/focus of the loop
     * @param forStreaming true if we are optimizing for streamed evaluation
     * @return true if the expression can be loop lifted
     */

    public boolean isLiftable(boolean forStreaming) {
        int p = getSpecialProperties();
        int d = getDependencies();
        return (p & StaticProperty.NO_NODES_NEWLY_CREATED) != 0 && (p & StaticProperty.HAS_SIDE_EFFECTS) == 0
                && ((d & StaticProperty.DEPENDS_ON_ASSIGNABLE_GLOBALS) == 0)
                && ((d & StaticProperty.DEPENDS_ON_POSITION) == 0)   // bug 3758
                && ((d & StaticProperty.DEPENDS_ON_LAST) == 0);
    }

    /**
     * Get the innermost scoping expression of this expression, for expressions that directly
     * depend on something in the dynamic context. For example, in the case of a local variable
     * reference this is the expression that causes the relevant variable to be bound; for a
     * context item expression it is the innermost focus-setting container. For expressions
     * that have no intrinsic dependency on the dynamic context, the value returned is null;
     * the scoping container for such an expression is the innermost scoping container of its
     * operands.
     * @return the innermost scoping container of this expression
     */

    public Expression getScopingExpression() {
        int d = getIntrinsicDependencies() & StaticProperty.DEPENDS_ON_FOCUS;
        if (d != 0) {
            if (d == StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT) {
                return ExpressionTool.getContextDocumentSettingContainer(this);
            } else {
                return ExpressionTool.getFocusSettingContainer(this);
            }
        } else {
            return null;
        }
    }

    /**
     * Ask whether the expression is multithreaded (that is, whether its operands are
     * evaluated in parallel)
     * @param config the Saxon configuration
     * @return true if execution will be multithreaded
     */

    public boolean isMultiThreaded(Configuration config) {
        return false;
    }

    /**
     * Ask whether common subexpressions found in the operands of this expression can
     * be extracted and evaluated outside the expression itself. The result is irrelevant
     * in the case of operands evaluated with a different focus, which will never be
     * extracted in this way, even if they have no focus dependency.
     * @return true by default, unless overridden in a subclass
     */

    public boolean allowExtractingCommonSubexpressions() {
        return true;
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation simplifies its operands.
     * @return the simplified expression (or the original if unchanged, or if modified in-situ)
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression
     *          rewriting
     */

    /*@NotNull*/
    public Expression simplify() throws XPathException {
        simplifyChildren();
        return Expression.this;
    }

    /**
     * Simplify this expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation simplifies its operands.
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression
     *          rewriting
     */

    /*@NotNull*/
    protected final void simplifyChildren() throws XPathException {
        for (Operand o : operands()) {
            if (o != null) {
                Expression e = o.getChildExpression();
                if (e != null) {
                    Expression f = e.simplify();
                    o.setChildExpression(f);
                }
            }
        }
    }


    /**
     * Set the retained static context
     * @param rsc the static context to be retained
     */

    public void setRetainedStaticContext(RetainedStaticContext rsc) {
        if (rsc != null) {
            retainedStaticContext = rsc;
            for (Operand o : operands()) {
                if (o != null) {
                    Expression child = o.getChildExpression();
                    if (child != null && child.retainedStaticContext == null) {
                        child.setRetainedStaticContext(rsc);
                    }
                }
            }
        }
    }

    /**
     * Set the retained static context on this expression and on all subexpressions in the expression tree.
     * The supplied retained static context is applied to all subexpressions, unless they have a closer
     * ancestor with an existing retained static context, in which case that is used instead,
     *
     * @param rsc the static context to be retained
     */

    public void setRetainedStaticContextThoroughly(RetainedStaticContext rsc) {
        if (rsc != null) {
            retainedStaticContext = rsc;
            for (Operand o : operands()) {
                if (o != null) {
                    Expression child = o.getChildExpression();
                    if (child != null) {
                        if (child.getLocalRetainedStaticContext() == null) {
                            child.setRetainedStaticContextThoroughly(rsc);
                        } else {
                            rsc = child.getLocalRetainedStaticContext();
                            for (Operand p : child.operands()) {
                                Expression grandchild = p.getChildExpression();
                                if (grandchild != null) {
                                    grandchild.setRetainedStaticContextThoroughly(rsc);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Set the parts of the static context that might be needed by the function, without
     * passing them on to subexpressions. Used for dynamic function calls only.
     * @param rsc the retained static context
     */

    public void setRetainedStaticContextLocally(RetainedStaticContext rsc) {
        if (rsc != null) {
            retainedStaticContext = rsc;
        }
    }

    /**
     * Get the retained static context of the expression
     * @return the retained static context
     */

    public final RetainedStaticContext getRetainedStaticContext() {
        if (retainedStaticContext == null) {
            Expression parent = getParentExpression();
            assert parent != null;
            retainedStaticContext = parent.getRetainedStaticContext();
            assert retainedStaticContext != null;
        }
        return retainedStaticContext;
    }

    public RetainedStaticContext getLocalRetainedStaticContext() {
        return retainedStaticContext;
    }

    /**
     * Get the saved static base URI as a string
     *
     * @return the static base URI
     */

    public String getStaticBaseURIString() {
        return getRetainedStaticContext().getStaticBaseUriString();
    }

    /**
     * Get the saved static base URI as a URI
     *
     * @return the static base URI as a URI
     * @throws XPathException if the static base URI is not a valid URI
     */

    public URI getStaticBaseURI() throws XPathException {
        return getRetainedStaticContext().getStaticBaseUri();
    }

    /**
     * Ask whether this expression is a call on a particular function
     * @param function the implementation class of the function in question
     * @return true if the expression is a call on the function
     */

    public boolean isCallOn(Class<? extends SystemFunction> function) {
        return false;
    }

    /**
     * Perform type checking of an expression and its subexpressions. This is the second phase of
     * static optimization.
     * <p>This checks statically that the operands of the expression have
     * the correct type; if necessary it generates code to do run-time type checking or type
     * conversion. A static type error is reported only if execution cannot possibly succeed, that
     * is, if a run-time type error is inevitable. The call may return a modified form of the expression.</p>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable. However, the types of such functions and
     * variables may not be accurately known if they have not been explicitly declared.</p>
     *
     *
     * @param visitor         an expression visitor
     * @param contextInfo     Information available statically about the context item: whether it is (possibly)
     *                        absent; its static type; its streaming posture.
     * @return the original expression, rewritten to perform necessary run-time type checks,
     *         and to perform other type-related optimizations
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    public Expression typeCheck(ExpressionVisitor visitor,
                                ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        return Expression.this;
    }

    /**
     * Perform type checking of the children of this expression (and their children, recursively)
     * This method is provided as a helper for implementations of the
     * {@link #typeCheck(net.sf.saxon.expr.parser.ExpressionVisitor, net.sf.saxon.expr.parser.ContextItemStaticInfo)}
     * method, since checking the children is an inevitable part of checking the expression itse.f
     *
     * @param visitor         an expression visitor
     * @param contextInfo     Information available statically about the context item: whether it is (possibly)
     *                        absent; its static type; its streaming posture.
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    protected final void typeCheckChildren(ExpressionVisitor visitor,
                                ContextItemStaticInfo contextInfo) throws XPathException {
        for (Operand o : operands()) {
            o.typeCheck(visitor, contextInfo);
        }
    }


    /**
     * Static type checking of some expressions is delegated to the expression itself, by calling
     * this method. The default implementation of the method throws UnsupportedOperationException.
     * If there is a non-default implementation, then implementsStaticTypeCheck() will return true
     *
     *
     * @param req                 the required type
     * @param backwardsCompatible true if backwards compatibility mode applies
     * @param role                the role of the expression in relation to the required type
     * @param visitor             an expression visitor
     * @return the expression after type checking (perhaps augmented with dynamic type checking code)
     * @throws XPathException if failures occur, for example if the static type of one branch of the conditional
     *                        is incompatible with the required type
     */

    public Expression staticTypeCheck(SequenceType req,
                                      boolean backwardsCompatible,
                                      RoleDiagnostic role, ExpressionVisitor visitor)
            throws XPathException {
        throw new UnsupportedOperationException("staticTypeCheck");
    }

    /**
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    public Expression optimize(ExpressionVisitor visitor,
                               /*@Nullable*/ ContextItemStaticInfo contextInfo) throws XPathException {
        if (visitor.incrementAndTestDepth()) {  // protect against infinite recursion
            optimizeChildren(visitor, contextInfo);
            visitor.decrementDepth();
        }
        return Expression.this;
    }

    /**
     * Perform optimization of the children of this expression (and their children, recursively)
     * This method is provided as a helper for implementations of the
     * {@link #optimize(net.sf.saxon.expr.parser.ExpressionVisitor, net.sf.saxon.expr.parser.ContextItemStaticInfo)}
     * method, since optimizing the children is an inevitable part of optimizing the expression itself
     *
     * @param visitor         an expression visitor
     * @param contextInfo     Information available statically about the context item: whether it is (possibly)
     *                        absent; its static type; its streaming posture.
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    protected final void optimizeChildren(ExpressionVisitor visitor,
                                ContextItemStaticInfo contextInfo) throws XPathException {
        for (Operand o : operands()) {
            o.optimize(visitor, contextInfo);
        }
    }

    public void prepareForStreaming() throws XPathException {
        // no action by default
    }

    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     * @return an estimate of the gross cost of evaluating the expression, including the cost
     * of evaluating its operands.
     */

    public double getCost() {
        if (cost < 0) {
            double i = getNetCost();
            for (Operand o : operands()) {
                i += o.getChildExpression().getCost();
                if (i > MAX_COST) {
                    break;
                }
            }
            cost = i;
        }
        return cost;
    }

    public final static double MAX_COST = 1e9;

    /**
     * Return the net cost of evaluating this expression, excluding the cost of evaluating
     * its operands. We take the cost of evaluating a simple scalar comparison or arithmetic
     * expression as 1 (one).
     * @return the intrinsic cost of this operation, excluding the costs of evaluating
     * the operands
     */

    public int getNetCost() {
        return 1;
    }


    /**
     * Replace this expression by a simpler expression that delivers the results without regard
     * to order.
     *
     * @param retainAllNodes set to true if the result must contain exactly the same nodes as the
     * original; set to false if the result can eliminate (or introduce) duplicates.
     * @param forStreaming set to true if the result is to be optimized for streaming
     * @return an expression that delivers the same nodes in a more convenient order
     * @throws XPathException if the rewrite fails
     */

    public Expression unordered(boolean retainAllNodes, boolean forStreaming) throws XPathException {
        return Expression.this;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */

    public final int getSpecialProperties() {
        if (staticProperties == -1) {
            computeStaticProperties();
        }
        return staticProperties & StaticProperty.SPECIAL_PROPERTY_MASK;
    }

    /**
     * Ask whether a particular "special property" is present for this expression
     * @param property the property in question, as an integer code in the {@link StaticProperty} class
     * @return true if the given property is present
     */

    public boolean hasSpecialProperty(int property) {
        return (getSpecialProperties() & property) != 0;
    }

    /**
     * Determine the static cardinality of the expression. This establishes how many items
     * there will be in the result of the expression, at compile time (i.e., without
     * actually evaluating the result.
     *
     * @return one of the values Cardinality.ONE_OR_MORE,
     *         Cardinality.ZERO_OR_MORE, Cardinality.EXACTLY_ONE,
     *         Cardinality.ZERO_OR_ONE, Cardinality.EMPTY. This default
     *         implementation returns ZERO_OR_MORE (which effectively gives no
     *         information).
     */

    public int getCardinality() {
        if (staticProperties == -1) {
            computeStaticProperties();
        }
        return staticProperties & StaticProperty.CARDINALITY_MASK;
    }

    /**
     * Determine the static item type of the expression, as precisely possible. All expression return
     * sequences, in general; this method determines the type of the items within the
     * sequence, assuming that (a) this is known in advance, and (b) it is the same for
     * all items in the sequence.
     * <p>This method should always return a result, though it may be the best approximation
     * that is available at the time.</p>
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER,
     *         Type.NODE, or Type.ITEM (meaning not known at compile time)
     */

    /*@NotNull*/
    public abstract ItemType getItemType();

    /**
     * Get the static type of the expression as a SequenceType: this is the combination of the
     * item type and the cardinality.
     * @return the static type of the expression
     */

    public SequenceType getStaticType() {
        return SequenceType.makeSequenceType(getItemType(), getCardinality());
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType the static type of the context item
     */

    public UType getStaticUType(UType contextItemType) {
        return UType.ANY;
    }

    /**
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as XPathContext.VARIABLES and
     * XPathContext.CURRENT_NODE. The default implementation combines the intrinsic
     * dependencies of this expression with the dependencies of the subexpressions,
     * computed recursively. This is overridden for expressions such as FilterExpression
     * where a subexpression's dependencies are not necessarily inherited by the parent
     * expression.
     *
     * @return a set of bit-significant flags identifying the dependencies of
     *         the expression
     */

    public int getDependencies() {
        // Implemented as a memo function: we only compute the dependencies
        // for each expression once
        if (staticProperties == -1) {
            computeStaticProperties();
        }
        return staticProperties & StaticProperty.DEPENDENCY_MASK;
    }

    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */

    /*@Nullable*/
    public IntegerValue[] getIntegerBounds() {
        return null;
    }

    public static final IntegerValue UNBOUNDED_LOWER = (IntegerValue) IntegerValue.makeIntegerValue(new DoubleValue(-1e100));
    public static final IntegerValue UNBOUNDED_UPPER = (IntegerValue) IntegerValue.makeIntegerValue(new DoubleValue(+1e100));
    public static final IntegerValue MAX_STRING_LENGTH = Int64Value.makeIntegerValue(Integer.MAX_VALUE);
    public static final IntegerValue MAX_SEQUENCE_LENGTH = Int64Value.makeIntegerValue(Integer.MAX_VALUE);


    /**
     * Mark an expression as being "flattened". This is a collective term that includes extracting the
     * string value or typed value, or operations such as simple value construction that concatenate text
     * nodes before atomizing. The implication of all of these is that although the expression might
     * return nodes, the identity of the nodes has no significance. This is called during type checking
     * of the parent expression.
     *
     * @param flattened set to true if the result of the expression is atomized or otherwise turned into
     *                  an atomic value
     */

    public void setFlattened(boolean flattened) {
        // no action in general
    }

    /**
     * Mark an expression as filtered: that is, it appears as the base expression in a filter expression.
     * This notification currently has no effect except when the expression is a variable reference.
     *
     * @param filtered if true, marks this expression as the base of a filter expression
     */

    public void setFiltered(boolean filtered) {
        // default: do nothing
    }

    /**
     * Evaluate an expression as a single item. This always returns either a single Item or
     * null (denoting the empty sequence). No conversion is done. This method should not be
     * used unless the static type of the expression is a subtype of "item" or "item?": that is,
     * it should not be called if the expression may return a sequence. There is no guarantee that
     * this condition will be detected.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the node or atomic value that results from evaluating the
     *         expression; or null to indicate that the result is an empty
     *         sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    /*@Nullable*/
    public Item evaluateItem(XPathContext context) throws XPathException {
        return iterate(context).next();
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
     *         of the expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        Item value = evaluateItem(context);
        return value == null ? EmptyIterator.emptyIterator() : SingletonIterator.rawIterator(value);
    }

    /**
     * Get the effective boolean value of the expression. This returns false if the value
     * is the empty sequence, a zero-length string, a number equal to zero, or the boolean
     * false. Otherwise it returns true.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        try {
            return ExpressionTool.effectiveBooleanValue(iterate(context));
        } catch (XPathException e) {
            e.maybeSetFailingExpression(this);
            e.maybeSetContext(context);
            throw e;
        }
    }

    /**
     * Evaluate an expression as a String. This function must only be called in contexts
     * where it is known that the expression will return a single string (or where an empty sequence
     * is to be treated as a zero-length string). Implementations should not attempt to convert
     * the result to a string, other than converting () to "". This method is used mainly to
     * evaluate expressions produced by compiling an attribute value template.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the value of the expression, evaluated in the current context.
     *         The expression must return a string or (); if the value of the
     *         expression is (), this method returns "".
     * @throws net.sf.saxon.trans.XPathException
     *                            if any dynamic error occurs evaluating the
     *                            expression
     * @throws ClassCastException if the result type of the
     *                            expression is not xs:string?, xs:untypedAtomic?, or xs:anyURI?
     */

    public CharSequence evaluateAsString(XPathContext context) throws XPathException {
        Item o = evaluateItem(context);
        StringValue value = (StringValue) o;  // the ClassCastException is deliberate
        if (value == null) {
            return "";
        }
        return value.getStringValueCS();
    }

    /**
     * Process the instruction, without returning any tail calls
     *
     *
     * @param output the destination for the result
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs
     */

    public void process(Outputter output, XPathContext context) throws XPathException {
        int m = getImplementationMethod();

        boolean hasEvaluateMethod = (m & EVALUATE_METHOD) != 0;
        boolean hasIterateMethod = (m & ITERATE_METHOD) != 0;

        try {
            if (hasEvaluateMethod && (!hasIterateMethod || !Cardinality.allowsMany(getCardinality()))) {
                Item item = evaluateItem(context);
                if (item != null) {
                    output.append(item, getLocation(), ReceiverOption.ALL_NAMESPACES);
                }
            } else if (hasIterateMethod) {
                iterate(context).forEachOrFail(it -> output.append(it, getLocation(), ReceiverOption.ALL_NAMESPACES));
            } else {
                throw new AssertionError("process() is not implemented in the subclass " + getClass());
            }
        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            throw e;
        }
    }

    /**
     * Evaluate an updating expression, adding the results to a Pending Update List.
     * The default implementation of this method, which is used for non-updating expressions,
     * throws an UnsupportedOperationException
     *
     * @param context the XPath dynamic evaluation context
     * @param pul     the pending update list to which the results should be written
     * @throws net.sf.saxon.trans.XPathException
     *                                       if evaluation fails
     * @throws UnsupportedOperationException if the expression is not an updating expression
     */

    public void evaluatePendingUpdates(XPathContext context, PendingUpdateList pul) throws XPathException {
        if (isVacuousExpression()) {
            iterate(context).next(); // typically, a call on fn:error
        } else {
            throw new UnsupportedOperationException("Expression " + getClass() + " is not an updating expression");
        }
    }

    /**
     * <p>The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form.</p>
     * <p>For subclasses of Expression that represent XPath expressions, the result should always be a string that
     * parses as an XPath 3.0 expression. The expression produced should be equivalent to the original making certain
     * assumptions about the static context. In general the expansion will make no assumptions about namespace bindings,
     * except that (a) the prefix "xs" is used to refer to the XML Schema namespace, and (b) the default function namespace
     * is assumed to be the "fn" namespace.</p>
     * <p>In the case of XSLT instructions and XQuery expressions, the toString() method gives an abstracted view of the syntax
     * that is not designed in general to be parseable.</p>
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        // fallback implementation
        FastStringBuffer buff = new FastStringBuffer(FastStringBuffer.C64);
        String className = getClass().getName();
        while (true) {
            int dot = className.indexOf('.');
            if (dot >= 0) {
                className = className.substring(dot + 1);
            } else {
                break;
            }
        }
        buff.append(className);
        boolean first = true;
        for (Operand o : operands()) {
            buff.append(first ? "(" : ", ");
            buff.append(o.getChildExpression().toString());
            first = false;
        }
        if (!first) {
            buff.append(")");
        }
        return buff.toString();
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     * @return a short string, sufficient to identify the expression
     */

    public String toShortString() {
        // fallback implementation
        return getExpressionName();
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     * @throws XPathException if the export fails, for example if an expression is found that won't work
     * in the target environment.
     */

    @Override
    public abstract void export(ExpressionPresenter out) throws XPathException;

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied outputstream.
     *
     * @param out the expression presenter used to display the structure
     */

    public final void explain(Logger out) {
        ExpressionPresenter ep = new ExpressionPresenter(getConfiguration(), out);
        ExpressionPresenter.ExportOptions options = new ExpressionPresenter.ExportOptions();
        options.explaining = true;
        ep.setOptions(options);
        try {
            export(ep);
        } catch (XPathException e) {
            ep.startElement("failure");
            ep.emitAttribute("message", e.getMessage());
            ep.endElement();
        }
        ep.close();
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     *
     * @param parentType the "given complex type": the method is checking that the nodes returned by this
     *                   expression are acceptable members of the content model of this type
     * @param whole      if true, we want to check that the value of this expression satisfies the content model
     *                   as a whole; if false we want to check that the value of the expression is acceptable as one part
     *                   of the content
     * @throws XPathException if the value delivered by this expression cannot be part of the content model
     *                        of the given type
     */

    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        //
    }


    /**
     * Set up a parent-child relationship between this expression and a given child expression.
     * <p>Note: many calls on this method are now redundant, but are kept in place for "belt-and-braces"
     * reasons. The rule is that an implementation of simplify(), typeCheck(), or optimize() that returns
     * a value other than "this" is required to set the location information and parent pointer in the new
     * child expression. However, in the past this was often left to the caller, which did it by calling
     * this method, either unconditionally on return from one of these methods, or after testing that the
     * returned object was not the same as the original.</p>
     *
     * @param child the child expression
     */

    public void adoptChildExpression(Expression child) {
        if (child == null) {
            return;
        }

//        if (child.getParentExpression() != null && child.getParentExpression() != this) {
//            for (Operand o : child.getParentExpression().operands()) {
//                if (o.getChildExpression() == child) {
//                    o.detachChild();
//                }
//            }
//        }
        child.setParentExpression(Expression.this);

        if (child.retainedStaticContext == null) {
            child.retainedStaticContext = retainedStaticContext;
        }

        if (getLocation() == null || getLocation() == Loc.NONE) {
            ExpressionTool.copyLocationInfo(child, Expression.this);
        } else if (child.getLocation() == null || child.getLocation() == Loc.NONE) {
            ExpressionTool.copyLocationInfo(Expression.this, child);
        }
        resetLocalStaticProperties();
    }

    /**
     * Set the location on an expression.
     *
     * @param id the location
     */

    public void setLocation(Location id) {
        location = id;
    }

    /**
     * Get the location of the expression
     *
     * @return a location identifier, which can be turned into real
     *         location information by reference to a location provider
     */

    @Override
    public final Location getLocation() {
        int limit = 0;
        Expression exp = this;
        while (limit < 10) {
            if ((exp.location == null || exp.location == Loc.NONE)
                    && exp.getParentExpression() != null) {
                // avoid infinite recursion: bug 3703#1
                exp = exp.getParentExpression();
                limit++;
            } else {
                break;
            }
        }
        return exp.location == null ? Loc.NONE : exp.location;
    }

    /**
     * Get the configuration containing this expression
     *
     * @return the containing Configuration
     */

    public Configuration getConfiguration() {
        try {
            return getRetainedStaticContext().getConfiguration();
        } catch (NullPointerException e) {
            throw new NullPointerException("Internal error: expression " + toShortString() + " has no retained static context");
        }
    }

    /**
     * Get information about the containing package
     * @return package data
     */

    public PackageData getPackageData() {
        try {
            return getRetainedStaticContext().getPackageData();
        } catch (NullPointerException e) {
            throw new NullPointerException("Internal error: expression " + toShortString() + " has no retained static context");
        }
    }

    /**
     * Ask whether this expression is an instruction. In XSLT streamability analysis this
     * is used to distinguish constructs corresponding to XSLT instructions from other constructs,
     * typically XPath expressions (but also things like attribute constructors on a literal result element,
     * references to attribute sets, etc. However, an XPath expression within a text value template
     * in a text node of an XSLT stylesheet is treated as an instruction.
     * In a non-XSLT environment (such as XQuery) this property has no meaning and its setting is undefined.
     * @return true if this construct originates as an XSLT instruction
     */

    public boolean isInstruction() {
        return false;
    }

    /**
     * Compute the static properties. This should only be done once for each
     * expression.
     */

    public final void computeStaticProperties() {
        staticProperties =
                computeDependencies() |
                        computeCardinality() |
                        computeSpecialProperties();
    }

    /**
     * Reset the static properties of the expression to -1, so that they have to be recomputed
     * next time they are used.
     */

    public void resetLocalStaticProperties() {
        staticProperties = -1;
        cachedHashCode = -1;
    }

    /**
     * Ask whether the static properties of the expression have been computed
     * @return true if the static properties have been computed
     */

    public boolean isStaticPropertiesKnown() {
        return staticProperties != -1;
    }

    /**
     * Compute the static cardinality of this expression
     *
     * @return the computed cardinality, as one of the values {@link StaticProperty#ALLOWS_ZERO_OR_ONE},
     *         {@link StaticProperty#EXACTLY_ONE}, {@link StaticProperty#ALLOWS_ONE_OR_MORE},
     *         {@link StaticProperty#ALLOWS_ZERO_OR_MORE}. May also return {@link StaticProperty#ALLOWS_ZERO} if
     *         the result is known to be an empty sequence, or {@link StaticProperty#ALLOWS_MANY} if
     *         if is known to return a sequence of length two or more.
     */

    protected abstract int computeCardinality();

    /**
     * Compute the special properties of this expression. These properties are denoted by a bit-significant
     * integer, possible values are in class {@link StaticProperty}. The "special" properties are properties
     * other than cardinality and dependencies, and most of them relate to properties of node sequences, for
     * example whether the nodes are in document order.
     *
     * @return the special properties, as a bit-significant integer
     */

    protected int computeSpecialProperties() {
        return 0;
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

    public int computeDependencies() {
        int dependencies = getIntrinsicDependencies();
        for (Operand o : operands()) {
            if (o.hasSameFocus()) {
                dependencies |= o.getChildExpression().getDependencies();
            } else {
                dependencies |= o.getChildExpression().getDependencies() & ~StaticProperty.DEPENDS_ON_FOCUS;
            }
        }
        return dependencies;
    }

    /**
     * Determine the intrinsic dependencies of an expression, that is, those which are not derived
     * from the dependencies of its subexpressions. For example, position() has an intrinsic dependency
     * on the context position, while (position()+1) does not. The default implementation
     * of the method returns 0, indicating "no dependencies".
     *
     * @return a set of bit-significant flags identifying the "intrinsic"
     *         dependencies. The flags are documented in class net.sf.saxon.value.StaticProperty
     */

    public int getIntrinsicDependencies() {
        return 0;
    }

    /**
     * Set a static property on an expression. Used when inlining functions to retain properties
     * of the inlined function (though this is only partially successful because the properties
     * can be recomputed later
     * @param prop the property to be set
     */

    public void setStaticProperty(int prop) {
        if (staticProperties == -1) {
            computeStaticProperties();
        }
        staticProperties |= prop;
    }

    /**
     * Check to ensure that this expression does not contain any inappropriate updating subexpressions.
     * This check is overridden for those expressions that permit updating subexpressions.
     *
     * @throws XPathException if the expression has a non-permitted updating subexpression
     */

    public void checkForUpdatingSubexpressions() throws XPathException {
        for (Operand o : operands()) {
            Expression sub = o.getChildExpression();
            if (sub == null) {
                throw new NullPointerException();
            }
            sub.checkForUpdatingSubexpressions();
            if (sub.isUpdatingExpression()) {
                XPathException err = new XPathException(
                        "Updating expression appears in a context where it is not permitted", "XUST0001");
                err.setLocation(sub.getLocation());
                throw err;
            }
        }
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     *
     * @return true if this is an updating expression
     */

    public boolean isUpdatingExpression() {
        for (Operand o : operands()) {
            if (o.getChildExpression().isUpdatingExpression()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     *
     * @return true if this expression is vacuous
     */

    public boolean isVacuousExpression() {
        return false;
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
    public abstract Expression copy(RebindingMap rebindings);

    /**
     * Suppress validation on contained element constructors, on the grounds that the parent element
     * is already performing validation. The default implementation does nothing.
     *
     * @param parentValidationMode the kind of validation being performed on the parent expression
     */

    public void suppressValidation(int parentValidationMode) {
        // do nothing
    }

    /**
     * Mark tail-recursive calls on stylesheet functions. For most expressions, this does nothing.
     *
     * @param qName the name of the function
     * @param arity the arity (number of parameters) of the function
     * @return {@link UserFunctionCall#NOT_TAIL_CALL} if no tail call was found;
     *         {@link UserFunctionCall#FOREIGN_TAIL_CALL} if a tail call on a different function was found;
     *         {@link UserFunctionCall#SELF_TAIL_CALL} if a tail recursive call was found and if this call accounts for the whole of the value.
     */

    public int markTailFunctionCalls(StructuredQName qName, int arity) {
        return UserFunctionCall.NOT_TAIL_CALL;
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @return the equivalent pattern
     * @throws XPathException if conversion is not possible
     */

    public Pattern toPattern(Configuration config) throws XPathException {
        ItemType type = getItemType();
        if (((getDependencies() & StaticProperty.DEPENDS_ON_NON_DOCUMENT_FOCUS) == 0) &&
                (type instanceof NodeTest || this instanceof VariableReference)) {
            return new NodeSetPattern(Expression.this);
        }
        if (isCallOn(KeyFn.class) || isCallOn(SuperId.class)) {
            return new NodeSetPattern(Expression.this);
        }
        throw new XPathException("Cannot convert the expression {" + this + "} to a pattern");
    }

    /**
     * Get the local variables (identified by their slot numbers) on which this expression depends.
     * Should only be called if the caller has established that there is a dependency on local variables.
     *
     * @return an array of integers giving the slot numbers of the local variables referenced in this
     *         expression.
     */

    public final synchronized int[] getSlotsUsed() {
        // synchronized because it's calculated lazily at run-time the first time it's needed
        if (slotsUsed != null) {
            return slotsUsed;
        }
        IntHashSet slots = new IntHashSet(10);
        gatherSlotsUsed(Expression.this, slots);
        slotsUsed = new int[slots.size()];
        int i = 0;
        IntIterator iter = slots.iterator();
        while (iter.hasNext()) {
            slotsUsed[i++] = iter.next();
        }
        Arrays.sort(slotsUsed);
        return slotsUsed;
    }

    private static void gatherSlotsUsed(Expression exp, IntHashSet slots) {
        exp = exp.getInterpretedExpression();
        if (exp instanceof LocalVariableReference) {
            slots.add(((LocalVariableReference)exp).getSlotNumber());
        } else if (exp instanceof SuppliedParameterReference) {
            int slot = ((SuppliedParameterReference) exp).getSlotNumber();
            slots.add(slot);
        } else {
            for (Operand o : exp.operands()) {
                gatherSlotsUsed(o.getChildExpression(), slots);
            }
        }
    }

    /**
     * Method used in subclasses to signal a dynamic error
     *
     * @param message the error message
     * @param code    the error code
     * @param context the XPath dynamic context
     * @throws XPathException always thrown, to signal a dynamic error
     */

    protected void dynamicError(String message, String code, XPathContext context) throws XPathException {
        XPathException err = new XPathException(message, code, getLocation());
        err.setXPathContext(context);
        err.setFailingExpression(this);
        throw err;
    }

    /**
     * Method used in subclasses to signal a runtime type error
     *
     * @param message   the error message
     * @param errorCode the error code
     * @param context   the XPath dynamic context
     * @throws XPathException always thrown, to signal a dynamic error
     */

    protected void typeError(String message, String errorCode, XPathContext context) throws XPathException {
        XPathException e = new XPathException(message, errorCode, getLocation());
        e.setIsTypeError(true);
        e.setXPathContext(context);
        e.setFailingExpression(this);
        throw e;
    }

    public String getTracingTag() {
        return getExpressionName();
    }

    /*@Nullable*/
    @Override
    public StructuredQName getObjectName() {
        return null;
    }

    /*@Nullable*/
    public Object getProperty(String name) {
        if (name.equals("expression")) {
            return Expression.this.getLocation();
        } else {
            return null;
        }
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property. The iterator may return properties whose
     * value is null.
     *
     * @return an iterator over the properties
     */

    public Iterator<String> getProperties() {
        return new MonoIterator<>("expression");
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
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNodeSet representing the points in the source document that are both reachable by this
     *         expression, and that represent possible results of this expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    /*@Nullable*/
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, /*@Nullable*/ PathMap.PathMapNodeSet pathMapNodeSet) {
        boolean dependsOnFocus = ExpressionTool.dependsOnFocus(Expression.this);
        PathMap.PathMapNodeSet attachmentPoint;
        if (pathMapNodeSet == null) {
            if (dependsOnFocus) {
                ContextItemExpression cie = new ContextItemExpression();
                ExpressionTool.copyLocationInfo(Expression.this, cie);
                pathMapNodeSet = new PathMap.PathMapNodeSet(pathMap.makeNewRoot(cie));
            }
            attachmentPoint = pathMapNodeSet;
        } else {
            attachmentPoint = dependsOnFocus ? pathMapNodeSet : null;
        }
        PathMap.PathMapNodeSet result = new PathMap.PathMapNodeSet();
        for (Operand o : operands()) {
            OperandUsage usage = o.getUsage();
            Expression child = o.getChildExpression();
            PathMap.PathMapNodeSet target = child.addToPathMap(pathMap, attachmentPoint);
            if (usage == OperandUsage.NAVIGATION) {
                // indicate that the function navigates to all elements in the document
                target = target.createArc(AxisInfo.ANCESTOR_OR_SELF, NodeKindTest.ELEMENT);
                target = target.createArc(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
            }
            result.addNodeSet(target);
        }
        if (getItemType() instanceof AtomicType) {
            // if expression returns an atomic value then any nodes accessed don't contribute to the result
            return null;
        } else {
            return result;
        }
    }

    /**
     * Determine whether the expression can be evaluated without reference to the part of the context
     * document outside the subtree rooted at the context node.
     *
     * @return true if the expression has no dependencies on the context node, or if the only dependencies
     *         on the context node are downward selections using the self, child, descendant, attribute, and namespace
     *         axes.
     */

    public boolean isSubtreeExpression() {
        if (ExpressionTool.dependsOnFocus(Expression.this)) {
            if ((getIntrinsicDependencies() & StaticProperty.DEPENDS_ON_FOCUS) != 0) {
                return false;
            } else {
                for (Operand o : operands()) {
                    if (!o.getChildExpression().isSubtreeExpression()) {
                        return false;
                    }
                }
                return true;
            }
        } else {
            return true;
        }
    }

    public void setEvaluationMethod(int method) {
        Expression.this.evaluationMethod = method;
    }

    public int getEvaluationMethod() {
        return evaluationMethod;
    }

    /**
     * The equals() test for expressions returns true if it can be determined that two expressions (given
     * their static context) will return the same result in all circumstances. The value false is returned
     * if this is not the case or if it cannot be determined to be the case.
     * <p>During the various phases of compilation, expression objects are mutable. Changing an expression
     * may change its hashCode, and may change the result of equals() comparisons. Expressions should therefore
     * not be held in data structures such as maps and sets that depend on equality comparison unless they
     * are no longer subject to such mutation.</p>
     *
     * @param obj the other operand; the result is false if this is not an Expression
     * @return true if the other operand is an expression and if it can be determined that the two
     * expressions are equivalent, in the sense that they will always return the same result.
     */

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * Test if this is expression is equivalent to another. This method first compares the hash codes,
     * allowing a quick exit if the expressions are not equal (except that computing the hash code may
     * itself be expensive...).
     * @param other the other expression
     * @return true if the two expressions are equal in the sense of the equals() method
     */

    public final boolean isEqual(Expression other) {
        return this == other || (hashCode() == other.hashCode() && equals(other));
    }

    /**
     * The hashCode() for an expression has semantics corresponding to equals(). Because computing
     * a hashCode may be expensive, it is computed lazily on first reference and saved with the
     * expression.
     *
     * @return a hashCode which is the same for two expressions that are equal().
     */

    public final int hashCode() {
        if (cachedHashCode == -1) {
            cachedHashCode = computeHashCode();
        }
        return cachedHashCode;
    }

    /**
     * Ask whether the static context of this expression is compatible with the static context
     * of another expression. The static contexts are considered compatible if either (a)
     * neither expression depends on the static context, or (b) both expressions depend on the static
     * context and the two static contexts compare equal.
     * @param other the other expression
     * @return true if the two expressions have equivalent static contexts
     */

    protected boolean hasCompatibleStaticContext(Expression other) {
        boolean d1 = (getIntrinsicDependencies() & StaticProperty.DEPENDS_ON_STATIC_CONTEXT) != 0;
        boolean d2 = (other.getIntrinsicDependencies() & StaticProperty.DEPENDS_ON_STATIC_CONTEXT) != 0;
        if (d1 != d2) {
            return false;
        }
        if (d1) {
            return getRetainedStaticContext().equals(other.getRetainedStaticContext());
        }
        return true;
    }


    /**
     * Compute a hash code, which will then be cached for later use
     * @return a computed hash code
     */

    protected int computeHashCode() {
        return super.hashCode();
    }

    /**
     * Determine whether two IdentityComparable objects are identical. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone. In the case of expressions, we test object identity, since the normal
     * equality test ignores the location of the expression.
     *
     * @param other the value to be compared with
     * @return true if the two values are indentical, false otherwise
     */
    @Override
    public boolean isIdentical(IdentityComparable other) {
        return this == other;
    }

    /**
     * Get a hashCode that offers the guarantee that if A.isIdentical(B), then A.identityHashCode() == B.identityHashCode()
     *
     * @return a hashCode suitable for use when testing for identity.
     */
    @Override
    public int identityHashCode() {
        return System.identityHashCode(Expression.this.getLocation());
    }

    /**
     * Set an extra property on the expression. (Used for Saxon-EE properties, e.g. properties
     * relating to streaming).
     * @param name the name of the property
     * @param value the value of the property, or null to remove the property
     */

    public void setExtraProperty(String name, Object value) {
        if (extraProperties == null) {
            if (value == null) {
                return;
            }
            extraProperties = new HashMap<>(4);
        }
        if (value == null) {
            extraProperties.remove(name);
        } else {
            extraProperties.put(name, value);
        }
    }

    /**
     * Get the value of an extra property on the expression
     * @param name  the name of the property
     * @return the value of the property if set, or null otherwise
     */

    public Object getExtraProperty(String name) {
        if (extraProperties == null) {
            return null;
        } else {
            return extraProperties.get(name);
        }
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */

    public String getStreamerName() {
        return null;
    }


}

