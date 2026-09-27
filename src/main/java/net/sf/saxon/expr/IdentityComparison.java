////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.BooleanEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.GlobalOrderComparer;
import net.sf.saxon.om.GNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;


/**
 * IdentityComparison: a boolean expression that compares two nodes
 * for equals, not-equals, greater-than or less-than based on identity and
 * document ordering
 */

public final class IdentityComparison extends BinaryExpression {

    private boolean generateIdEmulation = false;
    // this flag is set if an "X is Y" or "X isnot Y" comparison is being used
    // to emulate generate-id(X) = / != generate-id(Y). The handling of an empty
    // sequence in the two cases is different.

    /**
     * Create an identity comparison identifying the two operands and the operator
     *
     * @param p1 the left-hand operand
     * @param op the operator, as a token returned by the Tokenizer (e.g. Token.LT)
     * @param p2 the right-hand operand
     */

    public IdentityComparison(Expression p1, OperatorSymbol op, Expression p2) {
        super(p1, op, p2);
    }

    /**
     * Set flag to indicate different empty-sequence behavior when emulating
     * comparison of two generate-id's. This is relevant when operands evaluate
     * to an empty sequence; if both are empty, the "is" operator returns (), while the
     * generate-id() comparison compares two empty strings, which returns true; if one is
     * empty, the "is" operator returns (), while the generate-id() comparison returns false.
     *
     * @param flag true if this function is being used to compare generate-id() output
     */

    public void setGenerateIdEmulation(boolean flag) {
        generateIdEmulation = flag;
    }

    /**
     * Test the flag that indicates different empty-sequence behavior when emulating
     * comparison of two generate-id's
     *
     * @return true if this function is being used to compare generate-id() output
     */

    public boolean isGenerateIdEmulation() {
        return generateIdEmulation;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        getLhs().typeCheck(visitor, contextInfo);
        getRhs().typeCheck(visitor, contextInfo);

        if (!generateIdEmulation) {
            if (Literal.isEmptySequence(getLhsExpression()) || Literal.isEmptySequence(getRhsExpression())) {
                return Literal.makeEmptySequence();
            }
        }

        Supplier<RoleDiagnostic> role0 =
                () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, operator.toString(), 0);
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(false);
        setLhsExpression(tc.staticTypeCheck(getLhsExpression(), SequenceType.OPTIONAL_GNODE, role0, visitor));

        Supplier<RoleDiagnostic> role1 =
                () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, operator.toString(), 1);
        setRhsExpression(tc.staticTypeCheck(getRhsExpression(), SequenceType.OPTIONAL_GNODE, role1, visitor));

        if (!Cardinality.allowsZero(getLhsExpression().getCardinality()) && !Cardinality.allowsZero(getRhsExpression().getCardinality())) {
            generateIdEmulation = false;
            // because the flag only makes a difference if one of the operands evaluates to ()
        }
        return this;
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
        Expression r = super.optimize(visitor, contextItemType);
        if (r != this) {
            if (!generateIdEmulation) {
                if (Literal.isEmptySequence(getLhsExpression()) || Literal.isEmptySequence(getRhsExpression())) {
                    return Literal.makeEmptySequence();
                }
            }
        }
        return r;
    }

    @Override
    protected OperandRole getOperandRole(int arg) {
        return OperandRole.INSPECT;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings  variables that must be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        IdentityComparison ic = new IdentityComparison(getLhsExpression().copy(rebindings), operator, getRhsExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, ic);
        ic.generateIdEmulation = generateIdEmulation;
        return ic;
    }

    /**
     * Get the element name used to identify this expression in exported expression format
     *
     * @return the element name used to identify this expression
     */
    @Override
    protected String tag() {
        return switch (operator) {
            case IS -> "is";
            case IS_NOT -> "is-not";
            case PRECEDES -> "precedes";
            case FOLLOWS -> "follows";
            case PRECEDES_OR_IS -> "precedes-or-is";
            case FOLLOWS_OR_IS -> "follows-or-is";
            default -> "?";
        };
    }

    /**
     * Evaluate the expression
     */

    /*@Nullable*/
    @Override
    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        GNode node0 = getNode(getLhsExpression(), context);
        if (node0 == null) {
            if (generateIdEmulation) {
                return BooleanValue.get(getNode(getRhsExpression(), context) == null);
            }
            return null;
        }

        GNode node1 = getNode(getRhsExpression(), context);
        if (node1 == null) {
            if (generateIdEmulation) {
                return BooleanValue.FALSE;
            }
            return null;
        }

        return BooleanValue.get(compareIdentity(node0, node1));
    }

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        GNode node0 = getNode(getLhsExpression(), context);
        if (node0 == null) {
            return generateIdEmulation && getNode(getRhsExpression(), context) == null;
        }

        GNode node1 = getNode(getRhsExpression(), context);
        return node1 != null && compareIdentity(node0, node1);

    }

    private boolean compareIdentity(GNode node0, GNode node1) {

        return switch (operator) {
            case IS -> node0.equals(node1);
            case IS_NOT -> !node0.equals(node1);
            case PRECEDES -> GlobalOrderComparer.getInstance().compare(node0, node1) < 0;
            case FOLLOWS -> GlobalOrderComparer.getInstance().compare(node0, node1) > 0;
            case PRECEDES_OR_IS -> GlobalOrderComparer.getInstance().compare(node0, node1) <= 0;
            case FOLLOWS_OR_IS -> GlobalOrderComparer.getInstance().compare(node0, node1) >= 0;
            default -> throw new UnsupportedOperationException("Unknown node identity test");
        };
    }

    private static GNode getNode(Expression exp, XPathContext c) throws XPathException {
        return (GNode) exp.evaluateItem(c);
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

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.BOOLEAN;
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
        return "nodeComparison";
    }

    /**
     * Display the operator used by this binary expression
     *
     * @return String representation of the operator (for diagnostic display only)
     */
    @Override
    protected String displayOperator() {
        if (generateIdEmulation) {
            return "is-g";
        }
        return super.displayOperator();
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new IdentityComparisonElaborator();
    }

    /**
     * Elaborator for an identity comparison (operators {@code is}, {@code <<}, and {@code >>} )
     */

    public static class IdentityComparisonElaborator extends ItemElaborator {

        public BooleanEvaluator elaborateForBoolean() {
            final IdentityComparison exp = (IdentityComparison)getExpression();
            final ItemEvaluator p0 = exp.getLhsExpression().makeElaborator().elaborateForItem();
            final ItemEvaluator p1 = exp.getRhsExpression().makeElaborator().elaborateForItem();
            final boolean nullable0 = Cardinality.allowsZero(exp.getLhsExpression().getCardinality());
            final boolean nullable1 = Cardinality.allowsZero(exp.getRhsExpression().getCardinality());
            final OperatorSymbol operator = exp.getOperator();
            final boolean generateIdEmulation = exp.isGenerateIdEmulation();

            switch (operator) {
                case IS -> {
                    if (nullable0 || nullable1) {
                        if (generateIdEmulation) {
                            return context -> {
                                GNode v0 = (GNode) p0.eval(context);
                                GNode v1 = (GNode) p1.eval(context);
                                if (v0 == null) {
                                    return v1 == null;
                                }
                                return v0.equals(v1);
                            };
                        } else {
                            return context -> {
                                GNode v0 = (GNode) p0.eval(context);
                                GNode v1 = (GNode) p1.eval(context);
                                if (v0 == null || v1 == null) {
                                    return false;
                                }
                                return v0.equals(v1);
                            };
                        }
                    } else {
                        return context -> p0.eval(context).equals(p1.eval(context));
                    }
                }
                case IS_NOT -> {
                    if (nullable0 || nullable1) {
                        return context -> {
                            GNode v0 = (GNode) p0.eval(context);
                            GNode v1 = (GNode) p1.eval(context);
                            if (v0 == null || v1 == null) {
                                return false;
                            }
                            return !v0.equals(v1);
                        };
                    } else {
                        return context -> !p0.eval(context).equals(p1.eval(context));
                    }
                }
                case PRECEDES -> {
                    return context -> {
                        GNode v0 = (GNode) p0.eval(context);
                        GNode v1 = (GNode) p1.eval(context);
                        if (v0 == null || v1 == null) {
                            return false;
                        }
                        return GlobalOrderComparer.getInstance().compare(v0, v1) < 0;
                    };
                }
                case PRECEDES_OR_IS -> {
                    return context -> {
                        GNode v0 = (GNode) p0.eval(context);
                        GNode v1 = (GNode) p1.eval(context);
                        if (v0 == null || v1 == null) {
                            return false;
                        }
                        return GlobalOrderComparer.getInstance().compare(v0, v1) <= 0;
                    };
                }
                case FOLLOWS -> {
                    return context -> {
                        GNode v0 = (GNode) p0.eval(context);
                        GNode v1 = (GNode) p1.eval(context);
                        if (v0 == null || v1 == null) {
                            return false;
                        }
                        return GlobalOrderComparer.getInstance().compare(v0, v1) > 0;
                    };
                }
                case FOLLOWS_OR_IS -> {
                    return context -> {
                        GNode v0 = (GNode) p0.eval(context);
                        GNode v1 = (GNode) p1.eval(context);
                        if (v0 == null || v1 == null) {
                            return false;
                        }
                        return GlobalOrderComparer.getInstance().compare(v0, v1) >= 0;
                    };
                }
                default -> throw new IllegalStateException();
            }
        }

        public ItemEvaluator elaborateForItem() {
            final IdentityComparison exp = (IdentityComparison) getExpression();
            final ItemEvaluator p0 = exp.getLhsExpression().makeElaborator().elaborateForItem();
            final ItemEvaluator p1 = exp.getRhsExpression().makeElaborator().elaborateForItem();
            final boolean nullable0 = Cardinality.allowsZero(exp.getLhsExpression().getCardinality());
            final boolean nullable1 = Cardinality.allowsZero(exp.getRhsExpression().getCardinality());
            final OperatorSymbol operator = exp.getOperator();
            final boolean generateIdEmulation = exp.isGenerateIdEmulation();

            switch (operator) {
                case IS -> {
                    if (nullable0 || nullable1) {
                        if (generateIdEmulation) {
                            return context -> {
                                GNode v0 = (GNode) p0.eval(context);
                                GNode v1 = (GNode) p1.eval(context);
                                if (v0 == null) {
                                    if (v1 == null) {
                                        // generate-id(()) = generate-id(()) is true: bug 6279
                                        return BooleanValue.TRUE;
                                    } else {
                                        return BooleanValue.FALSE;
                                    }
                                }
                                if (v1 == null) {
                                    return BooleanValue.FALSE;
                                }
                                return BooleanValue.get(v0.equals(v1));
                            };
                        } else {
                            return context -> {
                                GNode v0 = (GNode) p0.eval(context);
                                if (v0 == null) {
                                    return null;
                                }
                                GNode v1 = (GNode) p1.eval(context);
                                if (v1 == null) {
                                    return null;
                                }
                                return BooleanValue.get(v0.equals(v1));
                            };
                        }
                    } else {
                        return context -> BooleanValue.get(p0.eval(context).equals(p1.eval(context)));
                    }
                }
                case IS_NOT -> {
                    if (nullable0 || nullable1) {
                        return context -> {
                            GNode v0 = (GNode) p0.eval(context);
                            if (v0 == null) {
                                return null;
                            }
                            GNode v1 = (GNode) p1.eval(context);
                            if (v1 == null) {
                                return null;
                            }
                            return BooleanValue.get(!v0.equals(v1));
                        };
                    } else {
                        return context -> BooleanValue.get(!p0.eval(context).equals(p1.eval(context)));
                    }
                }
                case PRECEDES -> {
                    return context -> {
                        GNode v0 = (GNode) p0.eval(context);
                        if (v0 == null) {
                            return null;
                        }
                        GNode v1 = (GNode) p1.eval(context);
                        if (v1 == null) {
                            return null;
                        }
                        return BooleanValue.get(GlobalOrderComparer.getInstance().compare(v0, v1) < 0);
                    };
                }
                case FOLLOWS -> {
                    return context -> {
                        GNode v0 = (GNode) p0.eval(context);
                        if (v0 == null) {
                            return null;
                        }
                        GNode v1 = (GNode) p1.eval(context);
                        if (v1 == null) {
                            return null;
                        }
                        return BooleanValue.get(GlobalOrderComparer.getInstance().compare(v0, v1) > 0);
                    };
                }
                case PRECEDES_OR_IS -> {
                    return context -> {
                        GNode v0 = (GNode) p0.eval(context);
                        if (v0 == null) {
                            return null;
                        }
                        GNode v1 = (GNode) p1.eval(context);
                        if (v1 == null) {
                            return null;
                        }
                        return BooleanValue.get(GlobalOrderComparer.getInstance().compare(v0, v1) <= 0);
                    };
                }
                case FOLLOWS_OR_IS -> {
                    return context -> {
                        GNode v0 = (GNode) p0.eval(context);
                        if (v0 == null) {
                            return null;
                        }
                        GNode v1 = (GNode) p1.eval(context);
                        if (v1 == null) {
                            return null;
                        }
                        return BooleanValue.get(GlobalOrderComparer.getInstance().compare(v0, v1) >= 0);
                    };
                }
                default -> throw new IllegalStateException();
            }

        }


    }
}

