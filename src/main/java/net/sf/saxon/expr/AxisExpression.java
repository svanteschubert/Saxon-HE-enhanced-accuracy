////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.DocumentSorter;
import net.sf.saxon.ma.jnode.AnyJNodeType;
import net.sf.saxon.ma.jnode.JNodeType;
import net.sf.saxon.ma.jnode.RootJNodeType;
import net.sf.saxon.ma.jnode.SpecificJNodeType;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.pattern.nodetest.NamedXNodePredicate;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.pattern.nodetest.NodeTestStar;
import net.sf.saxon.pattern.qname.AnyQNameTest;
import net.sf.saxon.pattern.qname.NamespaceQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.pattern.qname.SpecificQNameTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyElementImpl;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.*;
import net.sf.saxon.type.gnode.AnyGNodeType;
import net.sf.saxon.type.gnode.AnyXNodeType;
import net.sf.saxon.type.gnode.GNodeType;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;


/**
 * An AxisExpression is always obtained by simplifying a PathExpression.
 * It represents a PathExpression that starts at the context node, and uses
 * a simple node-test with no filters. For example "*", "title", "./item",
 * "@*", or "ancestor::chapter*".
 * <p>An AxisExpression delivers nodes in axis order (not in document order).
 * To get nodes in document order, in the case of a reverse axis, the expression
 * should be wrapped in a call on reverse().</p>
 */

public final class AxisExpression extends Expression implements GeneralizedAxisExpression {

    private int axis;
    /*@Nullable*/
    private NodeTest test;
    /*@Nullable*/
    private ItemType itemType = null;
    private ContextItemStaticInfo staticInfo = ContextItemStaticInfo.DEFAULT;
    private boolean doneTypeCheck = false;
    private boolean doneOptimize = false;

    /**
     * Constructor for an AxisExpression whose origin is the context item
     *
     * @param axis     The axis to be used in this AxisExpression: relevant constants are defined
     *                 in class {@link net.sf.saxon.om.AxisInfo}.
     * @param nodeTest The conditions to be satisfied by selected nodes. May be null,
     *                 indicating that any node on the axis is acceptable
     * @see net.sf.saxon.om.AxisInfo
     */

    public AxisExpression(int axis, /*@Nullable*/ NodeTest nodeTest) {
        this.axis = axis;
        this.test = nodeTest;
    }

    /**
     * Set the axis
     *
     * @param axis the new axis
     */

    public void setAxis(int axis) {
        this.axis = axis;
    }

    /**
     * Set the node test
     * @param test the node test
     */

    public void setNodeTest(NodeTest test) {
        this.test = test;
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
        return "axisStep";
    }

    /**
     * Simplify an expression
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        Expression e2 = super.simplify();
        if (e2 != this) {
            return e2;
        }
        if ((test == null || test == AnyGNodeType.getInstance()) &&
                (axis == AxisInfo.PARENT || axis == AxisInfo.ANCESTOR)) {
            // get more precise type information for parent/ancestor nodes
            test = MultipleNodeKindTest.PARENT_NODE;
        }
        return this;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Configuration config = visitor.getConfiguration();
        TypeHierarchy th = config.getTypeHierarchy();
        ItemType contextItemType = contextInfo.getItemType();
        boolean noWarnings = doneOptimize || (doneTypeCheck && this.staticInfo.getItemType().equals(contextItemType));
        doneTypeCheck = true;
        if (contextItemType == ErrorType.getInstance()) {
            // There is no context item. In principle we could raise XPTY0020 ("Context item is not a node"),
            // which is a type error and therefore can be thrown statically. But many test cases expect
            // XPDY0002 ("Context item absent") which for inexplicable reasons is a dynamic error rather than
             // a type error, and therefore cannot be raised until execution time.
           throw new XPathException("Axis step " + this + " cannot be used here: the context item is absent")
                    .withErrorCode("XPDY0002")
                    .withLocation(getLocation());
        }
        if (visitor.getStaticContext().getXPathVersion() >= 40) {
            if (!th.isSubType(contextItemType, AnyGNodeType.getInstance())) {
                Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(
                        RoleDiagnostic.AXIS_STEP, "", axis);
                //Expression coercer = SequenceCoercer.makeSequenceCoercer(new ContextItemExpression(), AnyGNodeType.getInstance().zeroOrMore(), role, true);
                //Expression coercer = new ItemChecker(new ContextItemExpression(), AnyGNodeType.getInstance(), role);
                Expression coercer = new net.sf.saxon.type.coercion.GNodeSequenceConverter(new ContextItemExpression(), role);
                return new SimpleStepExpression(coercer, this).typeCheck(visitor, contextInfo);
            }
        }
        if (contextInfo.getCardinality() != StaticProperty.ALLOWS_ONE) {
            // XPath 4.0 generalizes the context item to context value
            SlashExpression slash = new SlashExpression(new ContextValueExpression(), this);
            Expression sortedSlash = new DocumentSorter(slash);
            ExpressionTool.copyLocationInfo(this, sortedSlash);
            return sortedSlash;
        } else {
            staticInfo = contextInfo;
        }

        if (!UType.GNODE.subsumes(contextItemType.getUType())) {
            Affinity relation = th.relationship(contextItemType, AnyXNodeType.getInstance());
            if (relation == Affinity.DISJOINT) {
                throw new XPathException("Axis step "
                                                 + this + " cannot be used here: the context item is not a node")
                        .asTypeError()
                        .withErrorCode("XPTY0020")
                        .withLocation(getLocation());
            } else if (relation == Affinity.OVERLAPS || relation == Affinity.SUBSUMES) {
                // need to insert a dynamic check of the context item type
                Expression thisExp = checkPlausibility(visitor, contextInfo, !noWarnings);
                if (Literal.isEmptySequence(thisExp)) {
                    return thisExp;
                }
                ContextItemExpression exp = new ContextItemExpression();
                ExpressionTool.copyLocationInfo(this, exp);
                Supplier<RoleDiagnostic> role =
                        () -> new RoleDiagnostic(RoleDiagnostic.AXIS_STEP, "", axis, "XPTY0020");
                ItemChecker checker = new ItemChecker(exp, AnyXNodeType.getInstance(), role);
                ExpressionTool.copyLocationInfo(this, checker);
                SimpleStepExpression step = new SimpleStepExpression(checker, thisExp);
                ExpressionTool.copyLocationInfo(this, step);
                return step;
            }
        }

        if (th.isSubType(contextItemType, AnyXNodeType.getInstance())) {
            // An axis step starting at an XNode will always return XNodes
            if (test == null) {
                return new AxisExpression(axis, AnyXNodeType.getInstance());
            } else if (test instanceof NodeTestStar) {
                NodeKindType kind = NodeKindType.of(((NodeTestStar)test).getDefaultNodeKind());
                return new AxisExpression(axis, kind);
            } else if (test instanceof SelectorTest) {
                return new AxisExpression(axis, ((SelectorTest)test).asXNodeTest(config));
            } else if (test == MultipleNodeKindTest.PARENT_NODE) {
                return new AxisExpression(axis, MultipleNodeKindTest.PARENT_XNODE);
            }
        } else if (contextItemType instanceof JNodeType) {
            // An axis step starting at a JNode will always return JNodes
            if (test == null || test instanceof NodeTestStar) {
                return new AxisExpression(axis, AnyJNodeType.getInstance());
            }
        }

        if (visitor.getStaticContext().getOptimizerOptions().isSet(OptimizerOptions.VOID_EXPRESSIONS)) {
            return checkPlausibility(visitor, contextInfo, !noWarnings);
        } else {
            return this;
        }
    }

    private Expression checkPlausibility(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, boolean warnings)
            throws XPathException {
        ItemType contextType = contextInfo.getItemType();

        if (!(contextType instanceof NodeTest)) {
            contextType = AnyGNodeType.getInstance();
        }

        if (contextType instanceof JNodeType) {
            return this;
        }

        // Test whether the requested nodetest is consistent with the requested axis
        if (test != null && !AxisInfo.getTargetUType(UType.GNODE, axis).overlaps(test.getUType())) {
            if (warnings) {
                visitor.issueWarning("The " + AxisInfo.axisName[axis] + " axis will never select " +
                                             test.getUType().toStringWithIndefiniteArticle(),
                                     SaxonErrorCode.SXWN9037, getLocation());
            }
            return Literal.makeEmptySequence();
        }

        if (test != null && axis == AxisInfo.NAMESPACE) {
            QNameTest qNameTest = test.getQNameTest();
            if (qNameTest instanceof NamespaceQNameTest nqt && nqt.getNamespace() == NamespaceUri.NULL) {
                qNameTest = AnyQNameTest.getInstance();
            }
            if (qNameTest instanceof NamespaceQNameTest ||
                    (qNameTest instanceof SpecificQNameTest && !((SpecificQNameTest) qNameTest).getStructuredQName().getNamespaceUri().isEmpty())) {
                if (warnings) {
                    visitor.issueWarning("The names of namespace nodes are never prefixed, so this axis step will never select anything",
                                         SaxonErrorCode.SXWN9037, getLocation());
                }
                return Literal.makeEmptySequence();
            }
        }

        // Test whether the axis ever selects anything, when starting at this context node
        UType originUType = contextType.getUType();

        if (originUType == UType.JNODE) {
            itemType = AnyJNodeType.getInstance();
            return this;
        }
        UType targetUType = AxisInfo.getTargetUType(originUType, axis);
        UType testUType = test == null ? UType.XNODE : test.getUType();
        if (targetUType.equals(UType.VOID)) {
            if (warnings) {
                visitor.issueWarning("The " + AxisInfo.axisName[axis] + " axis starting at " +
                                             originUType.toStringWithIndefiniteArticle() + " will never select anything",
                                     SaxonErrorCode.SXWN9037, getLocation());
            }
            return Literal.makeEmptySequence();
        }

        if (contextInfo.isParentless() && (axis == AxisInfo.PARENT || axis == AxisInfo.ANCESTOR)) {
            if (warnings) {
                visitor.issueWarning("The " + AxisInfo.axisName[axis] + " axis will never select anything because the context item is parentless",
                                     SaxonErrorCode.SXWN9037, getLocation());
            }
            return Literal.makeEmptySequence();
        }

        // Test whether the axis ever selects a node of the right kind, when starting at this context node
        if (!targetUType.overlaps(testUType)) {
            if (warnings) {
                visitor.issueWarning("The " + AxisInfo.axisName[axis] + " axis starting at " +
                                             originUType.toStringWithIndefiniteArticle() + " will never select " +
                                             test.getUType().toStringWithIndefiniteArticle(),
                                     SaxonErrorCode.SXWN9037, getLocation());
            }
            return Literal.makeEmptySequence();
        }

        // For an X-or-self axis, if X never selects anything, then substitute the self axis.
        int nonSelf = AxisInfo.excludeSelfAxis[axis];
        if (axis != nonSelf) {
            UType nonSelfTarget = AxisInfo.getTargetUType(originUType, nonSelf);
            if (!nonSelfTarget.overlaps(testUType)) {
                axis = AxisInfo.SELF;
                targetUType = AxisInfo.getTargetUType(originUType, axis);
            }
        }

        if (targetUType.overlaps(UType.XNODE) && testUType.overlaps(UType.JNODE)) {
            // mixed XNodes and JNodes, give up unless we can simplify the test some other way
            return this;
        }

        ItemType target = targetUType.toItemType();
        if (test == null || test instanceof AnyXNodeType) {
            itemType = target;
        } else if (target instanceof AnyXNodeType || targetUType.subsumes(test.getUType())) {
            itemType = test.getItemType();
        } else if (target instanceof NodeTest) {
            itemType = new CombinedNodeTest((NodeTest) target, OperatorSymbol.INTERSECT, test).getItemType();
        } else {
            itemType = target;
        }

        if (test != null) {

            // If the content type of the context item is known, see whether the node test can select anything

//            if (contextType instanceof DocumentNodeType && kind.equals(UType.ELEMENT)) {
//                NodeTest elementTest = ((DocumentNodeType) contextType).getElementTest();
//                Optional<IntSet> outermostElementNames = elementTest.getRequiredNodeNames();
//                if (outermostElementNames.isPresent()) {
//                    Optional<IntSet> selectedElementNames = test.getRequiredNodeNames();
//                    if (selectedElementNames.isPresent()) {
//                        if (axis == AxisInfo.CHILD) {
//                            // check that the name appearing in the step is one of the names allowed by the nodetest
//
//                            if (selectedElementNames.get().intersect(outermostElementNames.get()).isEmpty()) {
//                                if (warnings) {
//                                    visitor.issueWarning(
//                                            "Starting at a document node, the step is selecting an element whose name " +
//                                                    "is not among the names of child elements permitted for this document node type", SaxonErrorCode.SXWN9037, getLocation());
//                                }
//
//                                return Literal.makeEmptySequence();
//                            }
//
//                            if (env.getPackageData().isSchemaAware() &&
//                                    elementTest instanceof SchemaNodeTest &&
//                                    outermostElementNames.get().size() == 1) {
//                                IntIterator oeni = outermostElementNames.get().iterator();
//                                int outermostElementName = oeni.hasNext() ? oeni.next() : -1;
//                                IElementDecl decl = env.getImportedSchema().getElementDecl(outermostElementName);
//                                if (decl == null) {
//                                    if (warnings) {
//                                        visitor.issueWarning("Element " + config.getNamePool().getEQName(outermostElementName) +
//                                                                     " is not declared in the schema", SaxonErrorCode.SXWN9037, getLocation());
//                                    }
//                                    itemType = elementTest;
//                                } else {
//                                    itemType = new NamedNodeKindType(Type.ELEMENT, elementTest.getQNameTest(), decl.getType(), true, config);
//                                }
//                            } else {
//                                itemType = elementTest;
//                            }
//                            return this;
//
//                        } else if (axis == AxisInfo.DESCENDANT) {
//                            // check that the name appearing in the step is one of the names allowed by the nodetest
//                            boolean canMatchOutermost = !selectedElementNames.get().intersect(outermostElementNames.get()).isEmpty();
//                            if (!canMatchOutermost) {
//                                // The expression /descendant::x starting at the document node doesn't match the outermost
//                                // element, so replace it by child::*/descendant::x, and check that
//                                Expression path = ExpressionTool.makePathExpression(new AxisExpression(AxisInfo.CHILD, elementTest), new AxisExpression(AxisInfo.DESCENDANT, test));
//                                ExpressionTool.copyLocationInfo(this, path);
//                                return path.typeCheck(visitor, contextInfo);
//                            }
//                        }
//                    }
//                }
//            }
//
//            SchemaType contentType = ((NodeTest) contextType).getContentType();
//            if (contentType == AnyType.getInstance()) {
//                // fast exit in non-schema-aware case
//                return this;
//            }

            Optimizer opt = visitor.obtainOptimizer();
            return opt.checkAxisExprAgainstSchema(this, visitor, contextInfo, warnings);
        }
        return this;
    }



    public void setItemType(ItemType type) {
        itemType = type;
    }





    /**
     * Get the static type of the context item for this AxisExpression. May be null if not known.
     *
     * @return the statically-inferred type, or null if not known
     */

    public ItemType getContextItemType() {
        return staticInfo.getItemType();
    }


    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                    The parameter is set to null if it is known statically that the context item will be undefined.
     *                    If the type of the context item is not known statically, the argument is set to
     *                    {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     */

    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) {
        doneOptimize = true; // This ensures no more warnings about empty axes, because (a) we've probably output the
        // warning already, and (b) we're now looking at a different expression from what the user
        // wrote. In particular, prevent spurious warnings after function inlining.
        staticInfo = contextInfo;
        return this;
    }

    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     * @return the estimated cost
     */
    @Override
    public double getCost() {
        return switch (axis) {
            case AxisInfo.SELF, AxisInfo.PARENT, AxisInfo.ATTRIBUTE -> 1;
            case AxisInfo.CHILD, AxisInfo.FOLLOWING_SIBLING, AxisInfo.PRECEDING_SIBLING, AxisInfo.ANCESTOR,
                 AxisInfo.ANCESTOR_OR_SELF -> 5;
            default -> 20;
        };
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return other instanceof AxisExpression &&
                axis == ((AxisExpression) other).axis &&
                Objects.equals(test, ((AxisExpression) other).test);
    }

    /**
     * get HashCode for comparing two expressions
     */

    @Override
    protected int computeHashCode() {
        // generate an arbitrary hash code that depends on the axis and the node test
        int h = 9375162 + axis << 20;
        if (test != null) {
            h ^= test.hashCode();
        }
        return h;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *                   that is used to update the bindings held in any
     *                   local variable references that are copied.
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        AxisExpression a2 = new AxisExpression(axis, test);
        a2.itemType = itemType;
        a2.staticInfo = staticInfo;
        a2.doneTypeCheck = doneTypeCheck;
        a2.doneOptimize = doneOptimize;
        ExpressionTool.copyLocationInfo(this, a2);
        return a2;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    @Override
    protected int computeSpecialProperties() {
        return StaticProperty.CONTEXT_DOCUMENT_NODESET |
                StaticProperty.SINGLE_DOCUMENT_NODESET |
                StaticProperty.NO_NODES_NEWLY_CREATED |
                (AxisInfo.isForwards[axis] ? StaticProperty.ORDERED_NODESET : StaticProperty.REVERSE_DOCUMENT_ORDER) |
                (AxisInfo.isPeerAxis[axis] || isPeerNodeTest(test) ? StaticProperty.PEER_NODESET : 0) |
                (AxisInfo.isSubtreeAxis[axis] ? StaticProperty.SUBTREE_NODESET : 0) |
                (axis == AxisInfo.ATTRIBUTE || axis == AxisInfo.NAMESPACE ? StaticProperty.ATTRIBUTE_NS_NODESET : 0);
    }

    /**
     * Determine whether a node test is a peer node test. A peer node test is one that, if it
     * matches a node, cannot match any of its descendants. For example, text() is a peer node-test.
     *
     * @param test the node test
     * @return true if nodes selected by this node-test will never contain each other as descendants
     */

    private static boolean isPeerNodeTest(NodeTest test) {
        if (test == null) {
            return false;
        }
        UType uType = test.getUType();
        if (uType.overlaps(UType.ELEMENT)) {
            // can match elements; for the moment, assume these can contain each other
            return false;
        } else if (uType.overlaps(UType.DOCUMENT)) {
            // can match documents; return false if we can also match non-documents
            return uType.equals(UType.DOCUMENT);
        } else {
            return true;
        }
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @return Type.NODE or a subtype, based on the NodeTest in the axis step, plus
     * information about the content type if this is known from schema analysis
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        if (itemType != null) {
            return itemType;
        }
        int p = AxisInfo.principalNodeType[axis];
        switch (p) {
            case Type.ATTRIBUTE:
            case Type.NAMESPACE:
                return NodeKindType.makeNodeKindTest(p);
            default:
                if (test == null) {
                    return AnyGNodeType.getInstance();
                } else {
                    return test.getItemType();
                }
        }
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @param contextItemType the static type of the context item for the expression evaluation
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        // See W3C bug 30032
        UType reachable = AxisInfo.getTargetUType(contextItemType, axis);
        if (test == null) {
            return reachable;
        } else {
            return reachable.intersection(test.getUType());
        }
    }


    /**
     * Determine the intrinsic dependencies of an expression, that is, those which are not derived
     * from the dependencies of its subexpressions. For example, position() has an intrinsic dependency
     * on the context position, while (position()+1) does not. The default implementation
     * of the method returns 0, indicating "no dependencies".
     *
     * @return an integer comprising bit-significant flags identifying the "intrinsic"
     * dependencies. The flags are documented in class net.sf.saxon.value.StaticProperty
     */
    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CONTEXT_ITEM;
    }

    /**
     * Determine the cardinality of the result of this expression
     */

    @Override
    protected int computeCardinality() {
        ItemType originNodeType = staticInfo.getItemType();
        if (!(originNodeType instanceof GNodeType gNodeType)) {
            // context item not a node - we'll report a type error somewhere along the line
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        }
        if (test == null) {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        }

        if (axis == AxisInfo.ATTRIBUTE && test instanceof NamedXNodePredicate attFpTest) {
            int attFingerprint = attFpTest.getRequiredFingerprint();
            if (attFpTest.getNodeKind() == Type.ATTRIBUTE && attFingerprint != -1) {
                StructuredQName attName = getConfiguration().getNamePool().getStructuredQName(attFingerprint);
                Schema schema = getRetainedStaticContext().getImportedSchema();
                SchemaType contentType = gNodeType.getContentType();
                if (contentType instanceof ComplexType) {
                    try {
                        int card = ((ComplexType) contentType).getAttributeUseCardinality(attName, schema);
                        if (!attFpTest.isFingerprintSufficient()) {
                            card = Cardinality.union(card, StaticProperty.ALLOWS_ZERO);
                        }
                        return card;
                    } catch (SchemaException err) {
                        // shouldn't happen; play safe
                        return StaticProperty.ALLOWS_ZERO_OR_ONE;
                    }
                } else if (contentType instanceof SimpleType) {
                    return StaticProperty.EMPTY;
                }
                return StaticProperty.ALLOWS_ZERO_OR_ONE;
            }
        }
        if (axis == AxisInfo.CHILD && test instanceof NamedXNodePredicate childFpTest) {
            int elemFingerprint = childFpTest.getRequiredFingerprint();
            if (childFpTest.getNodeKind() == Type.ELEMENT && elemFingerprint != -1) {
                Schema schema = getRetainedStaticContext().getImportedSchema();
                SchemaType contentType = gNodeType.getContentType();
                if (contentType instanceof ComplexType) {
                    int card = ((ComplexType) contentType).getElementParticleCardinality(elemFingerprint, schema, true);
                    if (!childFpTest.isFingerprintSufficient()) {
                        card = Cardinality.union(card, StaticProperty.ALLOWS_ZERO);
                    }
                    return card;
                } else {
                    return StaticProperty.EMPTY;
                }
            }
        }
        if (axis == AxisInfo.DESCENDANT && test instanceof NamedXNodePredicate descFpTest) {
            int elemFingerprint = descFpTest.getRequiredFingerprint();
            if (descFpTest.getNodeKind() == Type.ELEMENT && elemFingerprint != -1) {
                Schema schema = getRetainedStaticContext().getImportedSchema();
                SchemaType contentType = gNodeType.getContentType();
                if (contentType instanceof ComplexType) {
                    try {
                        int card = ((ComplexType) contentType).getDescendantElementCardinality(schema, elemFingerprint);
                        if (!descFpTest.isFingerprintSufficient()) {
                            card = Cardinality.union(card, StaticProperty.ALLOWS_ZERO);
                        }
                        return card;
                    } catch (SchemaException err) {
                        // shouldn't happen; play safe
                        return StaticProperty.ALLOWS_ZERO_OR_MORE;
                    }
                } else {
                    return StaticProperty.EMPTY;
                }
            }
        }
        if (axis == AxisInfo.SELF) {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Determine whether the expression can be evaluated without reference to the part of the context
     * document outside the subtree rooted at the context node.
     *
     * @return true if the expression has no dependencies on the context node, or if the only dependencies
     * on the context node are downward selections using the self, child, descendant, attribute, and namespace
     * axes.
     */

    @Override
    public boolean isSubtreeExpression() {
        return AxisInfo.isSubtreeAxis[axis];
    }

    /**
     * Get the axis
     *
     * @return the axis number, for example {@link net.sf.saxon.om.AxisInfo#CHILD}
     */

    public int getAxis() {
        return axis;
    }

    /**
     * Get the NodeTest. Returns null if the AxisExpression can return any node.
     *
     * @return the node test, or null if all nodes are returned
     */

    public NodeTest getNodeTest() {
        return test;
    }


    /**
     * Ask whether there is a possibility that the context item will be undefined
     *
     * @return true if this is a possibility
     */

    public boolean isContextPossiblyUndefined() {
        return staticInfo.getOptionality() != Optionality.REQUIRED;
    }

    public ContextItemStaticInfo getContextItemStaticInfo() {
        return staticInfo;
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config      the Saxon configuration
     * @param firstInPath true if this is the first or only step in a relative path
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config, boolean firstInPath) throws XPathException {

        NodeTest test = getNodeTest();
        Pattern pat;

        if (test == null) {
            test = AnyGNodeType.getInstance();
        }
        if (test instanceof SpecificJNodeType sjt) {
            pat = new JNodePattern(sjt.getSelector(), sjt.getValueType());
        } else if (test instanceof RootJNodeType rjt) {
            pat = new JNodePattern(null, rjt.getValueType());
        } else if (test instanceof AnyJNodeType ajt) {
            pat = new JNodePattern(null, SequenceType.ANY_SEQUENCE);
        } else {
            test = test.asXNodeTest(config);
            if (test instanceof AnyXNodeType
                    && (axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT || axis == AxisInfo.SELF)) {
                test = MultipleNodeKindTest.CHILD_NODE;
            }
            int kind = test.getItemType().getPrimitiveType();
            if (axis == AxisInfo.SELF) {
                pat = new NodeTestPattern(test);
            } else if (axis == AxisInfo.ATTRIBUTE) {
                if (kind == Type.XNODE || kind == Type.GNODE) {
                    // attribute::node() matches any attribute, and only an attribute
                    pat = new NodeTestPattern(NodeKindType.ATTRIBUTE);
                } else if (!AxisInfo.containsNodeKind(axis, kind)) {
                    // for example, attribute::comment()
                    pat = new ItemTypePattern(ErrorType.getInstance());
                } else {
                    // TODO: a NodeTestPattern only matches XNodes, but this turns out to be OK with
                    //  template rules because the code for compiling template rules expands it to a union
                    //  pattern. But it might not be OK for patterns in other contexts.
                    pat = new NodeTestPattern(test);
                }
            } else if (axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT || axis == AxisInfo.DESCENDANT_OR_SELF) {
                if (!AxisInfo.containsNodeKind(axis, kind)) {
                    pat = new ItemTypePattern(ErrorType.getInstance());
                } else {
                    pat = new NodeTestPattern(test);
                }
            } else if (axis == AxisInfo.NAMESPACE) {
                if (kind == Type.XNODE) {
                    // namespace::node() matches any namespace, and only a namespace
                    pat = new NodeTestPattern(NodeKindType.NAMESPACE);
                } else if (!AxisInfo.containsNodeKind(axis, kind)) {
                    // for example, namespace::comment()
                    pat = new ItemTypePattern(ErrorType.getInstance());
                } else {
                    pat = new NodeTestPattern(test);
                }
            } else {
                throw new XPathException("Only downwards axes are allowed in a pattern", "XTSE0340");
            }
        }
        ExpressionTool.copyLocationInfo(this, pat);
        return pat;
    }

    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD;
    }

    /**
     * Evaluate the path-expression in a given context to return a NodeSet
     *
     * @param context the evaluation context
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        Item item = context.getContextItem();
        if (item == null) {
            // Might as well do the test anyway, whether or not contextMaybeUndefined is set
            throw new XPathException("The context item for axis step " + this + " is absent")
                    .withErrorCode("XPDY0002")
                    .withXPathContext(context)
                    .withLocation(getLocation())
                    .asTypeError();
        }
        try {
            return Navigator.iterateAxis(((NodeInfo)item), axis, test);
        } catch (ClassCastException cce) {
            throw new XPathException("The context item for axis step " + this + " is not a node")
                    .withErrorCode("XPTY0020")
                    .withXPathContext(context)
                    .withLocation(getLocation())
                    .asTypeError();
        } catch (UnsupportedOperationException err) {
            if (err.getCause() instanceof XPathException) {
                throw ((XPathException) err.getCause())
                    .maybeWithLocation(getLocation())
                    .maybeWithContext(context);
            } else {
                // the namespace axis is not supported for all tree implementations
                dynamicError(err.getMessage(), "XPST0010", context);
                return null;
            }
        }
    }

    /**
     * Iterate the axis from a given starting node, without regard to context
     *
     * @param origin the starting node
     * @return the iterator over the axis
     */

    public SequenceIterator iterate(GNode origin) {
        return Navigator.iterateAxis(origin, axis, test);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("axis", this);
        destination.emitAttribute("name", AxisInfo.axisName[axis]);
        if (test == null) {
            destination.emitAttribute("nodeTest", AlphaCode.fromItemType(AnyGNodeType.getInstance()));
        } else if (test instanceof ItemType it) {
            destination.emitAttribute("nodeTest", AlphaCode.fromItemType(it));
        } else {
            destination.emitAttribute("nodeTest", test.export());
        }
        String schemaRole = getRetainedStaticContext().getImportedSchemaRoleName();
        if (!schemaRole.isEmpty()) {
            destination.emitAttribute("schemaRole", schemaRole);
        }
        destination.endElement();
    }

    /**
     * Represent the expression as a string. The resulting string will be a valid XPath 3.0 expression
     * with no dependencies on namespace bindings other than the binding of the prefix "xs" to the XML Schema
     * namespace.
     *
     * @return the expression as a string in XPath 3.0 syntax
     */

    public String toString() {
        return AxisInfo.axisName[axis]
                + "::"
                + (test == null ? "gnode()" : test.toString());
    }

    @Override
    public String toShortString() {
        StringBuilder fsb = new StringBuilder(16);
        if (axis == AxisInfo.CHILD) {
            // no action
        } else if (axis == AxisInfo.ATTRIBUTE) {
            fsb.append("@");
        } else {
            fsb.append(AxisInfo.axisName[axis]);
            fsb.append("::");
        }
        if (test == null) {
            fsb.append("gnode()");
        } else {
            fsb.append(test);
        }
        return fsb.toString();
    }

    @Override
    public String getStreamerName() {
        return "AxisExpression";
    }

    /**
     * Find any necessary preconditions for the satisfaction of this expression
     * as a set of boolean expressions to be evaluated on the context node
     *
     * @return A set of conditions, or null if none have been computed
     */
    public Set<Expression> getPreconditions() {
        HashSet<Expression> pre = new HashSet<>(1);
        /*Expression args[] = new Expression[1];
        args[0] = this.copy();
        pre.add(SystemFunctionCall.makeSystemFunction(
                "exists", args));*/
        Expression a = this.copy(new RebindingMap());
        a.setRetainedStaticContext(getRetainedStaticContext());
        pre.add(a);
        return pre;
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new AxisExpressionElaborator();
    }

    /**
     * Elaborator for an AxisExpression
     */

    public static class AxisExpressionElaborator extends PullElaborator {

        private void reportDoesNotExist(Expression expression, XPathContext context) {
            throw new UncheckedXPathException(new XPathException("The context item for axis step " +
                                                            expression + " is absent")
                    .withErrorCode("XPDY0002")
                    .withXPathContext(context)
                    .withLocation(expression.getLocation())
                    .asTypeError());
        }

        private void reportIsNotNode(Expression expression, XPathContext context) {
            throw new UncheckedXPathException(new XPathException("The context item for axis step " +
                                                            expression + " is not a node")
                    .withErrorCode("XPTY0020")
                    .withXPathContext(context)
                    .withLocation(expression.getLocation())
                    .asTypeError());
        }

        @SuppressWarnings("DuplicatedCode")
        @Override
        public PullEvaluator elaborateForPull() {
            AxisExpression axisExpression = (AxisExpression) getExpression();
            NodeTest test = axisExpression.getNodeTest();
            NodePredicate predicate =
                    (test == AnyXNodeType.getInstance()
                    || test == AnyGNodeType.getInstance()
                    || test == AnyJNodeType.getInstance()) ? null : test;
            int axis = axisExpression.getAxis();
            // These variables are computed in the hope that the optimizer will remove runtime error tests
            // that aren't needed because the condition cannot occur
            boolean checkContextItemExists = axisExpression.isContextPossiblyUndefined();
            boolean checkContextItemIsNode = axisExpression.getContextItemType().getGenre() != Genre.XNODE;
            BiConsumer<Item, XPathContext> checkOrigin = (origin, cxt) -> {
                if (checkContextItemExists && origin == null) {
                    reportDoesNotExist(axisExpression, cxt);
                }
                if (checkContextItemIsNode && !(origin instanceof GNode)) {
                    reportIsNotNode(axisExpression, cxt);
                }
            };
            return switch (axis) {
                case AxisInfo.ANCESTOR -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateAncestorAxis(predicate);
                };
                case AxisInfo.ANCESTOR_OR_SELF -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateAncestorOrSelfAxis(predicate);
                };
                case AxisInfo.ATTRIBUTE -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateAttributeAxis(predicate);
                };
                case AxisInfo.CHILD -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateChildAxis(predicate);
                };
                case AxisInfo.DESCENDANT -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateDescendantAxis(predicate);
                };
                case AxisInfo.DESCENDANT_OR_SELF -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateDescendantOrSelfAxis(predicate);
                };
                case AxisInfo.FOLLOWING -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateFollowingAxis(predicate);
                };
                case AxisInfo.FOLLOWING_OR_SELF -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateFollowingOrSelfAxis(predicate);
                };
                case AxisInfo.FOLLOWING_SIBLING -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateFollowingSiblingAxis(predicate);
                };
                case AxisInfo.FOLLOWING_SIBLING_OR_SELF -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateFollowingSiblingOrSelfAxis(predicate);
                };
                case AxisInfo.NAMESPACE -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateNamespaceAxis(predicate);
                };
                case AxisInfo.PARENT -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateParentAxis(predicate);
                };
                case AxisInfo.PRECEDING -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iteratePrecedingAxis(predicate);
                };
                case AxisInfo.PRECEDING_OR_SELF -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iteratePrecedingOrSelfAxis(predicate);
                };
                case AxisInfo.PRECEDING_SIBLING -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iteratePrecedingSiblingAxis(predicate);
                };
                case AxisInfo.PRECEDING_SIBLING_OR_SELF -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iteratePrecedingSiblingOrSelfAxis(predicate);
                };
                case AxisInfo.SELF -> context -> {
                    Item origin = context.getContextItem();
                    checkOrigin.accept(origin, context);
                    return ((GNode) origin).iterateSelfAxis(predicate);
                };
                default -> throw new IllegalArgumentException("Unknown axis number " + axis);
            };

        }

        @Override
        public ItemEvaluator elaborateForItem() {

            // Handle axis expressions known to return zero or one items, specifically @name, parent::x, self::x
            AxisExpression axisExpression = (AxisExpression) getExpression();
            NodeTest test = axisExpression.getNodeTest();
            int axis = axisExpression.getAxis();
            // These variables are computed in the hope that the optimizer will remove runtime error tests
            // that aren't needed because the condition cannot occur
            boolean checkContextItemExists = axisExpression.isContextPossiblyUndefined();
            boolean checkContextItemIsNode = axisExpression.getContextItemType().getGenre() != Genre.XNODE;
            BiConsumer<Item, XPathContext> checkOrigin = (origin, cxt) -> {
                if (checkContextItemExists && origin == null) {
                    reportDoesNotExist(axisExpression, cxt);
                }
                if (checkContextItemIsNode && !(origin instanceof GNode)) {
                    reportIsNotNode(axisExpression, cxt);
                }
            };
            switch (axis) {
                case AxisInfo.ATTRIBUTE -> {
                    if (test instanceof NamedXNodePredicate predicate && predicate.isFingerprintSufficient()) {
                        return context -> {
                            Item origin = context.getContextItem();
                            checkOrigin.accept(origin, context);
                            if (origin instanceof TinyElementImpl) {
                                return ((TinyElementImpl) origin).getAttributeNode(predicate.getRequiredFingerprint());
                            } else {
                                SequenceIterator iter = ((NodeInfo) origin).iterateAttributeAxis(predicate);
                                return iter.next();
                            }
                        };
                    } else {
                        return super.elaborateForItem();
                    }
                }
                case AxisInfo.PARENT -> {
                    return context -> {
                        Item origin = context.getContextItem();
                        checkOrigin.accept(origin, context);
                        GNode parent = ((GNode) origin).getParent();
                        return parent != null && test.test(parent) ? parent : null;
                    };
                }
                case AxisInfo.SELF -> {
                    return context -> {
                        Item origin = context.getContextItem();
                        checkOrigin.accept(origin, context);
                        return test.test((GNode) origin) ? origin : null;
                    };
                }
                default -> {
                    return super.elaborateForItem();
                }
            }
        }

    }
}

