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
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.expr.sort.GlobalOrderComparer;
import net.sf.saxon.functions.CurrentGroupCall;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;


/**
 * An expression representing a nodeset that is a union, difference, or
 * intersection of two other NodeSets
 */

public class VennExpression extends BinaryExpression {

    /**
     * Constructor
     *
     * @param p1 the left-hand operand
     * @param op the operator (union, intersection, or difference)
     * @param p2 the right-hand operand
     */

    public VennExpression(final Expression p1, final OperatorSymbol op, final Expression p2) {
        super(p1, op, p2);
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation simplifies its operands.
     *
     * @return the simplified expression (or the original if unchanged, or if modified in-situ)
     * @throws net.sf.saxon.trans.XPathException if an error is discovered during expression
     *                                           rewriting
     */
    @Override
    public Expression simplify() throws XPathException {
        // Force both operands to be sorted in document order. If this turns out to be unnecessary, it will
        // get optimized away
        if (!(getLhsExpression() instanceof DocumentSorter)) {
            setLhsExpression(new DocumentSorter(getLhsExpression()));
        }
        if (!(getRhsExpression() instanceof DocumentSorter)) {
            setRhsExpression(new DocumentSorter(getRhsExpression()));
        }
        return super.simplify();
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    @Override
    public String getExpressionName() {
        return operator.toString();
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the data type
     */

    /*@NotNull*/
    @Override
    public final ItemType getItemType() {
        final ItemType t1 = getLhsExpression().getItemType();
        if (operator == OperatorSymbol.UNION) {
            ItemType t2 = getRhsExpression().getItemType();
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            return Type.getCommonSuperType(t1, t2, th);
        } else {
            return t1;
        }
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType the static type of the context item
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return switch (operator) {
            case UNION ->
                    getLhsExpression().getStaticUType(contextItemType).union(getRhsExpression().getStaticUType(contextItemType));
            case INTERSECT ->
                    getLhsExpression().getStaticUType(contextItemType).intersection(getRhsExpression().getStaticUType(contextItemType));
            default -> getLhsExpression().getStaticUType(contextItemType);
        };
    }


    /**
     * Determine the static cardinality of the expression
     */

    @Override
    protected final int computeCardinality() {
        final int c1 = getLhsExpression().getCardinality();
        final int c2 = getRhsExpression().getCardinality();
        switch (operator) {
            case UNION:
                if (Literal.isEmptySequence(getLhsExpression())) {
                    return c2;
                }
                if (Literal.isEmptySequence(getRhsExpression())) {
                    return c1;
                }
                return c1 | c2 | StaticProperty.ALLOWS_ONE | StaticProperty.ALLOWS_MANY;
            // allows ZERO only if one operand allows ZERO
            case INTERSECT:
                if (Literal.isEmptySequence(getLhsExpression())) {
                    return StaticProperty.EMPTY;
                }
                if (Literal.isEmptySequence(getRhsExpression())) {
                    return StaticProperty.EMPTY;
                }
                return (c1 & c2) | StaticProperty.ALLOWS_ZERO | StaticProperty.ALLOWS_ONE;
            // allows MANY only if both operands allow MANY
            case EXCEPT:
                if (Literal.isEmptySequence(getLhsExpression())) {
                    return StaticProperty.EMPTY;
                }
                if (Literal.isEmptySequence(getRhsExpression())) {
                    return c1;
                }
                return c1 | StaticProperty.ALLOWS_ZERO | StaticProperty.ALLOWS_ONE;
            // allows MANY only if first operand allows MANY
        }
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    @Override
    protected int computeSpecialProperties() {
        final int prop0 = getLhsExpression().getSpecialProperties();
        final int prop1 = getRhsExpression().getSpecialProperties();
        int props = StaticProperty.ORDERED_NODESET;
        if (testContextDocumentNodeSet(prop0, prop1)) {
            props |= StaticProperty.CONTEXT_DOCUMENT_NODESET;
        }
        if (testSubTree(prop0, prop1)) {
            props |= StaticProperty.SUBTREE_NODESET;
        }
        if (createsNoNewNodes(prop0, prop1)) {
            props |= StaticProperty.NO_NODES_NEWLY_CREATED;
        }
        return props;
    }

    /**
     * Determine whether all the nodes in the node-set are guaranteed to
     * come from the same document as the context node. Used for optimization.
     *
     * @param prop0 contains the Context Document Nodeset property of the first operand
     * @param prop1 contains the Context Document Nodeset property of the second operand
     * @return true if all the nodes come from the context document
     */

    private boolean testContextDocumentNodeSet(final int prop0, final int prop1) {
        return switch (operator) {
            case UNION -> (prop0 & prop1 & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0;
            case INTERSECT -> ((prop0 | prop1) & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0;
            case EXCEPT -> (prop0 & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0;
            default -> false;
        };
    }

    /**
     * Gather the component operands of a union or intersect expression
     *
     * @param operator union or intersect
     * @param set      the set into which the components are to be gathered. If the operator
     *                 is union, this follows the tree gathering all operands of union expressions. Ditto,
     *                 mutatis mutandis, for intersect expressions.
     */

    public void gatherComponents(OperatorSymbol operator, Set<Expression> set) {
        if (getLhsExpression() instanceof VennExpression && ((VennExpression) getLhsExpression()).operator == operator) {
            ((VennExpression) getLhsExpression()).gatherComponents(operator, set);
        } else {
            set.add(getLhsExpression());
        }
        if (getRhsExpression() instanceof VennExpression && ((VennExpression) getRhsExpression()).operator == operator) {
            ((VennExpression) getRhsExpression()).gatherComponents(operator, set);
        } else {
            set.add(getRhsExpression());
        }
    }

    /**
     * Determine whether all the nodes in the node-set are guaranteed to
     * come from a subtree rooted at the context node. Used for optimization.
     *
     * @param prop0 contains the SubTree property of the first operand
     * @param prop1 contains the SubTree property of the second operand
     * @return true if all the nodes come from the tree rooted at the context node
     */

    private boolean testSubTree(final int prop0, final int prop1) {
        return switch (operator) {
            case UNION -> (prop0 & prop1 & StaticProperty.SUBTREE_NODESET) != 0;
            case INTERSECT -> ((prop0 | prop1) & StaticProperty.SUBTREE_NODESET) != 0;
            case EXCEPT -> (prop0 & StaticProperty.SUBTREE_NODESET) != 0;
            default -> false;
        };
    }

    /**
     * Determine whether the expression can create new nodes
     *
     * @param prop0 contains the noncreative property of the first operand
     * @param prop1 contains the noncreative property of the second operand
     * @return true if the expression can create new nodes
     */

    private boolean createsNoNewNodes(final int prop0, final int prop1) {
        return (prop0 & StaticProperty.NO_NODES_NEWLY_CREATED) != 0 &&
                (prop1 & StaticProperty.NO_NODES_NEWLY_CREATED) != 0;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, final ContextItemStaticInfo contextInfo) throws XPathException {

        Configuration config = visitor.getConfiguration();
        TypeChecker tc = config.getTypeChecker(false);
        getLhs().typeCheck(visitor, contextInfo);
        getRhs().typeCheck(visitor, contextInfo);

        Expression lhs2 = getLhsExpression();
        if (!(lhs2 instanceof Pattern)) {
            Supplier<RoleDiagnostic> role0 = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, operator.toString(), 0);
            lhs2 = tc.staticTypeCheck(getLhsExpression(), SequenceType.GNODE_SEQUENCE, role0, visitor);
        }

        Expression rhs2 = getRhsExpression();
        if (!(getRhsExpression() instanceof Pattern)) {
            Supplier<RoleDiagnostic> role1 = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, operator.toString(), 1);
            rhs2 = tc.staticTypeCheck(getRhsExpression(), SequenceType.GNODE_SEQUENCE, role1, visitor);
        }


        if (Literal.isEmptySequence(lhs2)) {
            return operator == OperatorSymbol.UNION ? sorted(rhs2) : lhs2;
        } else if (Literal.isEmptySequence(rhs2)) {
            return operator == OperatorSymbol.INTERSECT ? rhs2 : sorted(lhs2);
        } 


        // For the intersect and except operators, if the types are disjoint then we can simplify
        if (operator != OperatorSymbol.UNION) {
            TypeHierarchy th = config.getTypeHierarchy();
            ItemType t0 = getLhsExpression().getItemType();
            ItemType t1 = getRhsExpression().getItemType();
            if (th.relationship(t0, t1) == Affinity.DISJOINT) {
                if (operator == OperatorSymbol.INTERSECT) {
                    return Literal.makeEmptySequence();
                } else {
                    if (getLhsExpression().hasSpecialProperty(StaticProperty.ORDERED_NODESET)) {
                        return getLhsExpression();
                    } else {
                        return new DocumentSorter(getLhsExpression());
                    }
                }
            }
        }

        return this;
    }

    private Expression sorted(Expression operand) {
        if ((operand.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
            return operand;
        }
        return new DocumentSorter(operand);
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
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during this phase
     *          (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Expression e = super.optimize(visitor, contextItemType);
        if (e != this) {
            return e;
        }

        final Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();

        // If either operand is an empty sequence, simplify the expression. This can happen
        // after reduction with constructs of the form //a[condition] | //b[not(condition)],
        // common in XPath 1.0 because there were no conditional expressions.

        Expression lhs = getLhsExpression();
        Expression rhs = getRhsExpression();
        
        switch (operator) {
            case UNION:
                if (Literal.isEmptySequence(lhs) &&
                        (rhs.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
                    return rhs;
                }
                if (Literal.isEmptySequence(rhs) &&
                        (lhs.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
                    return lhs;
                }
                if (contextItemWithCurrentGroup(lhs, rhs)) {
                    return rhs;
                }
                if (contextItemWithCurrentGroup(rhs, lhs)) {
                    return lhs;
                }
                break;
            case INTERSECT:
                if (Literal.isEmptySequence(lhs)) {
                    return lhs;
                }
                if (Literal.isEmptySequence(rhs)) {
                    return rhs;
                }
                if (contextItemWithCurrentGroup(lhs, rhs)) {
                    return lhs;
                }
                if (contextItemWithCurrentGroup(rhs, lhs)) {
                    return rhs;
                }
                break;
            case EXCEPT:
                if (Literal.isEmptySequence(lhs)) {
                    return lhs;
                }
                if (Literal.isEmptySequence(rhs) &&
                        (lhs.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
                    return lhs;
                }
                if (contextItemWithCurrentGroup(lhs, rhs)) {
                    return Literal.makeEmptySequence();
                }
                if (contextItemWithCurrentGroup(rhs, lhs)) {
                    // Test case si-group-055.
                    // The streaming code has problems with (current-group() except .) so we
                    // optimize it away. This is a bit of a hack, because the difficulty may
                    // affect other expressions as well. The problem arises because part of the
                    // pattern needs to be evaluated with each node in the group as anchor node,
                    // and part with only the first node in the group as anchor node.
                    return new TailExpression(lhs, 2);
                }
                break;
        }

        // If both are axis expressions on the same axis, merge them
        // ie. rewrite (axis::test1 | axis::test2) as axis::(test1 | test2)

        if (lhs instanceof AxisExpression a1 && rhs instanceof AxisExpression a2) {
            if (a1.getAxis() == a2.getAxis()) {
                if (a1.getNodeTest().equals(a2.getNodeTest())) {
                    if (operator == OperatorSymbol.EXCEPT) {
                        return Literal.makeEmptySequence();
                    } else {
                        return a1;
                    }
                } else {
                    AxisExpression ax = new AxisExpression(a1.getAxis(),
                                                           new CombinedNodeTest(a1.getNodeTest(),
                                                                                operator,
                                                                                a2.getNodeTest()));
                    ExpressionTool.copyLocationInfo(this, ax);
                    return ax;
                }
            }
        }

        // If both are path expressions starting the same way, merge them
        // i.e. rewrite (/X | /Y) as /(X|Y). This applies recursively, so that
        // /A/B/C | /A/B/D becomes /A/B/child::(C|D)

        // This optimization was previously done for all three operators. However, it's not safe for "except":
        // A//B except A//C//B cannot be rewritten as A/descendant-or-self::node()/(B except C//B). As a quick
        // fix, the optimization has been retained for "union" but dropped for "intersect" and "except". Need to
        // do a more rigorous analysis of the conditions under which it is safe.

        // TODO: generalize this code to handle all distributive operators, and expressions involving multiple
        //   unions (p/x | p/y | p/z)

        if (lhs instanceof SlashExpression && rhs instanceof SlashExpression && operator == OperatorSymbol.UNION) {
            final SlashExpression path1 = (SlashExpression) lhs;
            final SlashExpression path2 = (SlashExpression) rhs;

            if (path1.getFirstStep().isEqual(path2.getFirstStep())) {
                final VennExpression venn = new VennExpression(
                        path1.getRemainingSteps(),
                        operator,
                        path2.getRemainingSteps());
                ExpressionTool.copyLocationInfo(this, venn);
                final Expression path = ExpressionTool.makePathExpression(path1.getFirstStep(), venn);
                ExpressionTool.copyLocationInfo(this, path);
                return path.optimize(visitor, contextItemType);
            }
        }

        // Try merging two non-positional filter expressions:
        // A[exp0] | A[exp1] becomes A[exp0 or exp1]

        if (lhs instanceof FilterExpression exp0 && rhs instanceof FilterExpression exp1) {

            if (!exp0.isPositional(th) &&
                    !exp1.isPositional(th) &&
                    exp0.getSelectExpression().isEqual(exp1.getSelectExpression())) {
                final Expression filter = switch (operator) {
                    case UNION -> new OrExpression(exp0.getFilter(),
                                                   exp1.getFilter());
                    case INTERSECT -> new AndExpression(exp0.getFilter(),
                                                        exp1.getFilter());
                    case EXCEPT -> new AndExpression(exp0.getFilter(),
                                                     SystemFunction.makeCall("not", getRetainedStaticContext(), exp1.getFilter()));
                    default -> throw new AssertionError("Unknown operator " + operator);
                };
                ExpressionTool.copyLocationInfo(this, filter);
                FilterExpression f = new FilterExpression(exp0.getSelectExpression(), filter);
                ExpressionTool.copyLocationInfo(this, f);
                return f.simplify()
                        .typeCheck(visitor, contextItemType)
                        .optimize(visitor, contextItemType);
            }
        }

        // Convert @*|node() into @*,node() to eliminate the sorted merge operation
        // Avoid doing this when streaming because xsl:value-of select="@*,node()" is not currently streamable
        if (!visitor.isOptimizeForStreaming() && operator == OperatorSymbol.UNION &&
                lhs instanceof AxisExpression ax0 && rhs instanceof AxisExpression ax1) {
            if (ax0.getAxis() == AxisInfo.ATTRIBUTE && ax1.getAxis() == AxisInfo.CHILD) {
                return new Block(new Expression[]{lhs, rhs});
            } else if (ax1.getAxis() == AxisInfo.ATTRIBUTE && ax0.getAxis() == AxisInfo.CHILD) {
                return new Block(new Expression[]{rhs, lhs});
            }
        }

        // Convert (A intersect B) to use a serial search where one operand is a singleton
        if (operator == OperatorSymbol.INTERSECT && !Cardinality.allowsMany(lhs.getCardinality())) {
            return new SingletonIntersectExpression(lhs, operator, rhs.unordered(false, false));
        }
        if (operator == OperatorSymbol.INTERSECT && !Cardinality.allowsMany(rhs.getCardinality())) {
            return new SingletonIntersectExpression(rhs, operator, lhs.unordered(false, false));
        }

        // If the types of the operands are disjoint, simplify "intersect" and "except"
        if (operandsAreDisjoint(th)) {
            if (operator == OperatorSymbol.INTERSECT) {
                return Literal.makeEmptySequence();
            } else if (operator == OperatorSymbol.EXCEPT) {
                if ((lhs.getSpecialProperties() & StaticProperty.ORDERED_NODESET) != 0) {
                    return lhs;
                } else {
                    return new DocumentSorter(lhs);
                }
            }
        }
        return this;
    }

    private boolean operandsAreDisjoint(TypeHierarchy th) {
        return th.relationship(getLhsExpression().getItemType(), getRhsExpression().getItemType()) == Affinity.DISJOINT;
    }


    /**
     * Return true if the operands are, respectively, "." and "current-group()", and
     * if the context-setting scope for both operands is the same. This implies that the context
     * item must necessarily be a member of the current group.
     *
     * @param lhs the left-hand operand
     * @param rhs the right-hand operand
     * @return true if the LHS is "." and the RHS is "current-group()" and they the focus
     * setting container is the containing xsl:for-each-group instruction
     */

    private boolean contextItemWithCurrentGroup(Expression lhs, Expression rhs) {
        if (lhs instanceof ContextItemExpression && rhs instanceof CurrentGroupCall) {
            Expression focusSetter = ExpressionTool.getFocusSettingContainer(lhs);
            Expression forEachGroup = ((CurrentGroupCall) rhs).getControllingInstruction();
            return forEachGroup != null && focusSetter == forEachGroup;
        }
        return false;
    }

    /**
      * Replace this expression by an expression that returns the same result but without
      * regard to order
      *
     * @param retainAllNodes true if all nodes in the result must be retained; false
     *                       if duplicates can be eliminated
     * @param forStreaming  set to true if optimizing for streaming
     */
     @Override
     public Expression unordered(boolean retainAllNodes, boolean forStreaming) {
         if (operator == OperatorSymbol.UNION && !forStreaming &&
                 operandsAreDisjoint(getConfiguration().getTypeHierarchy())) {
             // replace union operator by comma operator to avoid cost of sorting into document order. See XMark q7
             Block block = new Block(new Expression[]{getLhsExpression(), getRhsExpression()});
             ExpressionTool.copyLocationInfo(this, block);
             return block;
         }
         return this;
     }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be rebound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        VennExpression exp = new VennExpression(getLhsExpression().copy(rebindings), operator, getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
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
     * Get the operand role (applies to both operands)
     * @return the operand role
     * @param arg which argument: 0 for the lhs, 1 for the rhs
     */

    @Override
    protected OperandRole getOperandRole(int arg) {
        return OperandRole.SAME_FOCUS_ACTION;
    }


    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        // NOTE: it's possible that the method in the superclass is already adequate for this
        if (other instanceof VennExpression b) {
            if (operator != b.operator) {
                return false;
            }
            if (getLhsExpression().isEqual(b.getLhsExpression()) && getRhsExpression().isEqual(b.getRhsExpression())) {
                return true;
            }
            if (operator == OperatorSymbol.UNION || operator == OperatorSymbol.INTERSECT) {
                // These are commutative and associative, so for example (A|B)|C equals B|(A|C)
                Set<Expression> s0 = new HashSet<>(10);
                gatherComponents(operator, s0);
                Set<Expression> s1 = new HashSet<>(10);
                b.gatherComponents(operator, s1);
                return s0.equals(s1);
            }
        }
        return false;
    }

    @Override
    protected int computeHashCode() {
        return getLhsExpression().hashCode() ^ getRhsExpression().hashCode();
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config      the Saxon configuration
     * @param firstInPath
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config, boolean firstInPath) throws XPathException {
        if (isPredicatePattern(getLhsExpression()) || isPredicatePattern(getRhsExpression())) {
            throw new XPathException(
                    "Cannot use a predicate pattern as an operand of a union, intersect, or except operator",
                    "XTSE0340");
        }
        if (operator == OperatorSymbol.UNION) {
            return new UnionPattern(
                    getLhsExpression().toPattern(config, firstInPath),
                    getRhsExpression().toPattern(config, firstInPath));
        } else {
            // Bug #5368 means it's dangerous to assume that the expression (A except B) can be translated
            // into a pattern that matches a node if A matches and B does not. We can only do this in special
            // cases, in particular (a) where both operands use the attribute or child axis, and (b) where
            // one of the patterns is anchored at the root of the tree (for example //xxx/yyy or $var/xxx or id('x')/xxx)
            // The semantics are changed in 4.0, see PR 1773.
            int commonAxis = ExpressionTool.getAxisNavigation(this);
            if (commonAxis == AxisInfo.CHILD || commonAxis == AxisInfo.ATTRIBUTE
                    || independentOfContextItem(getLhsExpression())
                    || independentOfContextItem(getRhsExpression())) {
                if (operator == OperatorSymbol.EXCEPT) {
                    return new ExceptPattern(
                            getLhsExpression().toPattern(config, firstInPath),
                            getRhsExpression().toPattern(config, firstInPath));
                } else {
                    return new IntersectPattern(
                            getLhsExpression().toPattern(config, firstInPath),
                            getRhsExpression().toPattern(config, firstInPath));
                }
            }
            return new GeneralNodePattern(this, getItemType());
        } 
    }

    private boolean independentOfContextItem(Expression exp) {
        return (exp.getDependencies() & StaticProperty.DEPENDS_ON_CONTEXT_ITEM) == 0;
    }

    private boolean isPredicatePattern(Expression exp) {
        if (exp instanceof ItemChecker) {
            exp = ((ItemChecker)exp).getBaseExpression();
        }
        return exp instanceof FilterExpression && (((FilterExpression)exp).getSelectExpression() instanceof ContextItemExpression);
    }

    /**
     * Get the element name used to identify this expression in exported expression format
     *
     * @return the element name used to identify this expression
     */
    @Override
    protected String tag() {
        if (operator == OperatorSymbol.UNION) {
            return "union";
        }
        return operator.toString();
    }

    /**
     * Iterate over the value of the expression. The result will always be sorted in document order,
     * with duplicates eliminated
     *
     * @param c The context for evaluation
     * @return a SequenceIterator representing the union of the two operands
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(final XPathContext c) throws XPathException {
        switch (operator) {
            case UNION: {
                // If either of the operands is a union expression, then we merge its component
                // iterators into a single multi-way union iterator
                List<SequenceIterator> operands = new ArrayList<>();
                gatherUnionLeafIterators(operands, c);
                return new UnionIterator(operands, GlobalOrderComparer.getInstance());
            }
            case INTERSECT: {
                SequenceIterator i1 = getLhsExpression().iterate(c);
                SequenceIterator i2 = getRhsExpression().iterate(c);
                return new IntersectionIterator(i1, i2,
                                                GlobalOrderComparer.getInstance());
            }
            case EXCEPT: {
                SequenceIterator i1 = getLhsExpression().iterate(c);
                SequenceIterator i2 = getRhsExpression().iterate(c);
                return new DifferenceIterator(i1, i2,
                                              GlobalOrderComparer.getInstance());
            }
        }
        throw new UnsupportedOperationException("Unknown operator in Venn Expression");
    }

    private void gatherUnionLeafIterators(List<SequenceIterator> leafIterators, XPathContext context) throws XPathException {
        Expression e1 = getLhsExpression();
        if (e1 instanceof VennExpression && ((VennExpression)e1).operator == OperatorSymbol.UNION) {
            ((VennExpression)e1).gatherUnionLeafIterators(leafIterators, context);
        } else {
            leafIterators.add(e1.iterate(context));
        }
        Expression e2 = getRhsExpression();
        if (e2 instanceof VennExpression && ((VennExpression) e2).operator == OperatorSymbol.UNION) {
            ((VennExpression) e2).gatherUnionLeafIterators(leafIterators, context);
        } else {
            leafIterators.add(e2.iterate(context));
        }
    }

    /**
     * Get the effective boolean value. In the case of a union expression, this
     * is reduced to an OR expression, for efficiency
     */

    @Override
    public boolean effectiveBooleanValue(final XPathContext context) throws XPathException {
        if (operator == OperatorSymbol.UNION) {
            // NOTE: this optimization was probably already done statically
            return getLhsExpression().effectiveBooleanValue(context) || getRhsExpression().effectiveBooleanValue(context);
        } else {
            return super.effectiveBooleanValue(context);
        }
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "VennExpression";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new VennElaborator();
    }

    /**
     * Elaborator for a Venn expression: that is {@code A union B}, {@code A intersect B},
     * or {@code A except B}
     */

    public static class VennElaborator extends PullElaborator {

        public PullEvaluator elaborateForPull() {

            final VennExpression exp = (VennExpression)getExpression();

            if (exp.getOperator() == OperatorSymbol.UNION) {
                final List<PullEvaluator> leafEvaluators = new ArrayList<>();
                gatherUnionLeafEvaluators(exp, leafEvaluators);
                return context -> {
                    List<SequenceIterator> iterators = new ArrayList<>(leafEvaluators.size());
                    for (PullEvaluator evaluator : leafEvaluators) {
                        iterators.add(evaluator.iterate(context));
                    }
                    return new UnionIterator(iterators, GlobalOrderComparer.getInstance());
                };
            } else {
                PullEvaluator p1 = exp.getLhsExpression().makeElaborator().elaborateForPull();
                PullEvaluator p2 = exp.getRhsExpression().makeElaborator().elaborateForPull();
                if (exp.getOperator() == OperatorSymbol.INTERSECT) {
                    return context -> new IntersectionIterator(
                            p1.iterate(context), p2.iterate(context), GlobalOrderComparer.getInstance());
                } else {
                    return context -> new DifferenceIterator(
                            p1.iterate(context), p2.iterate(context), GlobalOrderComparer.getInstance());
                }
            }
        }

        private static void gatherUnionLeafEvaluators(VennExpression exp, List<PullEvaluator> leafEvaluators) {
            Expression e1 = exp.getLhsExpression();
            if (e1 instanceof VennExpression && ((VennExpression) e1).getOperator() == OperatorSymbol.UNION) {
                gatherUnionLeafEvaluators(((VennExpression) e1), leafEvaluators);
            } else {
                leafEvaluators.add(e1.makeElaborator().elaborateForPull());
            }
            Expression e2 = exp.getRhsExpression();
            if (e2 instanceof VennExpression && ((VennExpression) e2).getOperator() == OperatorSymbol.UNION) {
                gatherUnionLeafEvaluators(((VennExpression) e2), leafEvaluators);
            } else {
                leafEvaluators.add(e2.makeElaborator().elaborateForPull());
            }
        }

    }
}

