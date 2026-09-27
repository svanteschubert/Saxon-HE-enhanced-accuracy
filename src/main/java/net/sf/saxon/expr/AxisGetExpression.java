////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.EagerPullEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.ma.MapOrArray;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.jnode.*;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.type.gnode.AnyGNodeType;
import net.sf.saxon.type.gnode.AnyXNodeType;
import net.sf.saxon.type.gnode.XNodeType;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntSet;

import java.util.*;
import java.util.function.Supplier;


/**
 * An AxisGetExpression represents a step such as child::get(XYZ)
 * or descendant::get(XYZ), where XYZ is an expression that evaluates
 * to a selector
 */

public final class AxisGetExpression extends UnaryExpression implements GeneralizedAxisExpression {

    private final int axis;
    private ItemType itemType = AnyGNodeType.getInstance();

    /**
     * Constructor for an AxisExpression whose origin is the context item
     *
     * @param axis     The axis to be used in this AxisExpression: relevant constants are defined
     *                 in class {@link AxisInfo}.
     * @param selector The expression within the "get()"
     * @see AxisInfo
     */

    public AxisGetExpression(int axis, Expression selector) {
        super(selector);
        this.axis = axis;
    }

    /**
     * Get the usage (in terms of streamability analysis) of the single operand
     *
     * @return the operand usage
     */
    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.ATOMIC_SEQUENCE;
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
        return "axisGet";
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        if (contextInfo.getItemType() instanceof JNodeType) {
            itemType = AnyJNodeType.getInstance();
        } else if (contextInfo.getItemType() instanceof XNodeType) {
            itemType = AnyXNodeType.getInstance();
        }
        ContextItemStaticInfo noFocus = new ContextItemStaticInfo(ErrorType.getInstance(), Optionality.PROHIBITED);
        getOperand().typeCheck(visitor, noFocus);
        Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(RoleDiagnostic.BINARY_EXPR, "get()", 1);
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(false);
        SequenceType req = BuiltInAtomicType.ANY_ATOMIC.zeroOrMore();
        setBaseExpression(tc.staticTypeCheck(getBaseExpression(), req, role, visitor));
        return this;
    }

    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        getOperand().optimize(visitor, contextInfo);
//        // if the operand value is known, pre-evaluate the expression
//        Expression base = getBaseExpression();
//        try {
//            if (base instanceof Literal) {
//                return Literal.makeLiteral(
//                        SequenceTool.toGroundedValue(iterate(visitor.getStaticContext().makeEarlyEvaluationContext())), this);
//            }
//        } catch (XPathException | UncheckedXPathException err) {
//            // if early evaluation fails, suppress the error: the value might
//            // not be needed at run-time
//        }
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
        switch (axis) {
            case AxisInfo.SELF:
            case AxisInfo.PARENT:
            case AxisInfo.ATTRIBUTE:
                return 1;
            case AxisInfo.CHILD:
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.PRECEDING_SIBLING:
            case AxisInfo.ANCESTOR:
            case AxisInfo.ANCESTOR_OR_SELF:
                return 5;
            default:
                return 20;
        }
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return other instanceof AxisGetExpression &&
                axis == ((AxisGetExpression) other).axis &&
                Objects.equals(getBaseExpression(), ((AxisGetExpression) other).getBaseExpression());
    }

    /**
     * get HashCode for comparing two expressions
     */

    @Override
    protected int computeHashCode() {
        // generate an arbitrary hash code that depends on the axis and the node test
        return 9375162 + axis << 20 ^ getBaseExpression().hashCode();
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
        return new AxisGetExpression(axis, getBaseExpression().copy(rebindings));
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
                (AxisInfo.isPeerAxis[axis] ? StaticProperty.PEER_NODESET : 0) |
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
        return itemType;
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
        if (contextItemType == UType.JNODE) {
            return contextItemType;
        } else {
            return UType.XNODE;
        }
    }


    /**
     * Determine the intrinsic dependencies of an expression, that is, those which are not derived
     * from the dependencies of its subexpressions. For example, position() has an intrinsic dependency
     * on the context position, while (position()+1) does not. The default implementation
     * of the method returns 0, indicating "no dependencies".
     *
     * @return a set of bit-significant flags identifying the "intrinsic"
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
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
//        ItemType originNodeType = staticInfo.getItemType();
//        if (!(originNodeType instanceof GNodeType)) {
//            // context item not a node - we'll report a type error somewhere along the line
//            return StaticProperty.ALLOWS_ZERO_OR_MORE;
//        }
//        if (test == null) {
//            return StaticProperty.ALLOWS_ZERO_OR_MORE;
//        }
//        GNodeType gNodeType = (GNodeType)originNodeType;
//        int attFingerprint = test.getRequiredFingerprint(Type.ATTRIBUTE);
//        int elemFingerprint = test.getRequiredFingerprint(Type.ELEMENT);
//        if (axis == AxisInfo.ATTRIBUTE && attFingerprint != -1) {
//            StructuredQName attName = getConfiguration().getNamePool().getStructuredQName(attFingerprint);
//            Schema schema = getRetainedStaticContext().getImportedSchema();
//            SchemaType contentType = gNodeType.getContentType();
//            if (contentType instanceof ComplexType) {
//                try {
//                    return ((ComplexType) contentType).getAttributeUseCardinality(attName, schema);
//                } catch (SchemaException err) {
//                    // shouldn't happen; play safe
//                    return StaticProperty.ALLOWS_ZERO_OR_ONE;
//                }
//            } else if (contentType instanceof SimpleType) {
//                return StaticProperty.EMPTY;
//            }
//            return StaticProperty.ALLOWS_ZERO_OR_ONE;
//        } else if (axis == AxisInfo.CHILD && elemFingerprint != -1) {
//            Schema schema = getRetainedStaticContext().getImportedSchema();
//            SchemaType contentType = gNodeType.getContentType();
//            if (contentType instanceof ComplexType) {
//                return ((ComplexType) contentType).getElementParticleCardinality(elemFingerprint, schema, true);
//            } else {
//                return StaticProperty.EMPTY;
//            }
//        } else if (axis == AxisInfo.DESCENDANT && elemFingerprint != -1) {
//            Schema schema = getRetainedStaticContext().getImportedSchema();
//            SchemaType contentType = gNodeType.getContentType();
//            if (contentType instanceof ComplexType) {
//                try {
//                    return ((ComplexType) contentType).getDescendantElementCardinality(schema, elemFingerprint);
//                } catch (SchemaException err) {
//                    // shouldn't happen; play safe
//                    return StaticProperty.ALLOWS_ZERO_OR_MORE;
//                }
//            } else {
//                return StaticProperty.EMPTY;
//            }
//
//        } else if (axis == AxisInfo.SELF) {
//            return StaticProperty.ALLOWS_ZERO_OR_ONE;
//        } else {
//            return StaticProperty.ALLOWS_ZERO_OR_MORE;
//        }
//        // the parent axis isn't handled by this class
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
     * @return the axis number, for example {@link AxisInfo#CHILD}
     */

    public int getAxis() {
        return axis;
    }

//    /**
//     * Get the NodeTest. Returns null if the AxisExpression can return any node.
//     *
//     * @return the node test, or null if all nodes are returned
//     */
//
//    public NodeTest getNodeTest() {
//        return test;
//    }


//    /**
//     * Ask whether there is a possibility that the context item will be undefined
//     *
//     * @return true if this is a possibility
//     */
//
//    public boolean isContextPossiblyUndefined() {
//        return staticInfo.getOptionality() != Optionality.REQUIRED;
//    }
//
//    public ContextItemStaticInfo getContextItemStaticInfo() {
//        return staticInfo;
//    }


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
        return makeElaborator().elaborateForPull().iterate(context);
//        Item item = context.getContextItem();
//        if (item == null) {
//            // Might as well do the test anyway, whether or not contextMaybeUndefined is set
//            throw new XPathException("The context item for axis step " + this + " is absent")
//                    .withErrorCode("XPDY0002")
//                    .withXPathContext(context)
//                    .withLocation(getLocation())
//                    .asTypeError();
//        }
//        try {
//            return Navigator.iterateAxis(((NodeInfo)item), axis, test);
//        } catch (ClassCastException cce) {
//            throw new XPathException("The context item for axis step " + this + " is not a node")
//                    .withErrorCode("XPTY0020")
//                    .withXPathContext(context)
//                    .withLocation(getLocation())
//                    .asTypeError();
//        } catch (UnsupportedOperationException err) {
//            if (err.getCause() instanceof XPathException) {
//                throw ((XPathException) err.getCause())
//                    .maybeWithLocation(getLocation())
//                    .maybeWithContext(context);
//            } else {
//                // the namespace axis is not supported for all tree implementations
//                dynamicError(err.getMessage(), "XPST0010", context);
//                return null;
//            }
//        }
    }

    /**
     * Iterate the axis from a given starting node, without regard to context
     *
     * @param origin the starting node
     * @return the iterator over the axis
     */

    public SequenceIterator iterate(GNode origin) {
        return Navigator.iterateAxis(origin, axis, AnyGNodeType.getInstance());
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("axisGet", this);
        destination.emitAttribute("name", AxisInfo.axisName[axis]);
        getBaseExpression().export(destination);
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
                + "::get(" + getBaseExpression().toString() + ")";
    }

    @Override
    public String toShortString() {
        return AxisInfo.axisName[axis]
                + "::get(" + getBaseExpression().toShortString() + ")";
    }

    @Override
    public String getStreamerName() {
        return "AxisGetExpression";
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
        return new AxisGetExpressionElaborator();
    }

    /**
     * Elaborator for an AxisExpression
     */

    public static class AxisGetExpressionElaborator extends PullElaborator {

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
            AxisGetExpression axisExpression = (AxisGetExpression) getExpression();
            Expression getter = axisExpression.getBaseExpression();
            int axis = axisExpression.getAxis();
            EagerPullEvaluator getEvaluator = new EagerPullEvaluator(getter.makeElaborator().elaborateForPull());
            if (axis == AxisInfo.CHILD) {
                return context -> {
                    Item origin = context.getContextItem();
                    GroundedValue selection = (GroundedValue) getEvaluator.evaluate(context);
                    if (origin instanceof MapOrArray) {
                        origin = RootJNode.obtainRootJNode((MapOrArray) origin);
                    }
                    if (origin instanceof JNode) {
                        GroundedValue content = ((JNode) origin).getContent();
                        if (content instanceof MapItem) {
                            if (selection.getLength() == 0) {
                                return EmptyIterator.INSTANCE;
                            }
                            if (selection.getLength() == 1) {
                                AtomicValue key = (AtomicValue) selection.head(); // for now
                                GroundedValue val = ((MapItem) content).get(key);
                                if (val == null) {
                                    return EmptyIterator.INSTANCE;
                                }
                                JNode j = new JNodeForMapEntry(((JNode) origin), 1, key, val, -1);
                                return new SingletonIterator(j);
                            }
                            int specVersion = ((MapItem)content).getSpecVersion();
                            Set<AtomicMatchKey> matchKeys = new HashSet<>();
                            for (Item it : selection.asIterable()) {
                                matchKeys.add(((AtomicValue) it).asMapKey(specVersion));
                            }
                            List<Item> result = new ArrayList<>();
                            int serialNr = 0;
                            for (KeyValuePair pair : ((MapItem) content).keyValuePairs()) {
                                if (matchKeys.contains(pair.key().asMapKey(specVersion))) {
                                    result.add(new JNodeForMapEntry((JNode) origin, 1, pair.key(), pair.value(), serialNr));
                                }
                                serialNr++;
                            }
                            return new ListIterator.Of<>(result);
                        } else if (content instanceof ArrayItem) {
                            if (selection.getLength() == 0) {
                                return EmptyIterator.INSTANCE;
                            }
                            if (selection.getLength() == 1) {
                                AtomicValue key = (AtomicValue)selection.head();
                                if (key instanceof NumericValue) {
                                    int subscript;
                                    if (key instanceof IntegerValue) {
                                        subscript = ((IntegerValue) key).asSubscript();
                                    } else if (((NumericValue) key).isWholeNumber()) {
                                        subscript = (int) ((NumericValue) key).longValue();
                                    } else {
                                        return EmptyIterator.INSTANCE;
                                    }
                                    if (subscript >= 1 && subscript <= ((ArrayItem) content).arrayLength()) {
                                        GroundedValue val = ((ArrayItem) content).get(subscript - 1);
                                        JNode j = new JNodeForArrayMember(((JNode) origin), 1, new Int64Value(subscript), val);
                                        return new SingletonIterator(j);
                                    }
                                }
                                return EmptyIterator.INSTANCE;
                            }
                            IntSet matchKeys = new IntHashSet(selection.getLength());
                            for (Item it : selection.asIterable()) {
                                if (it instanceof IntegerValue) {
                                    matchKeys.add(((IntegerValue) it).asSubscript());
                                } else if (it instanceof NumericValue && ((NumericValue) it).isWholeNumber()) {
                                    matchKeys.add((int) ((NumericValue) it).longValue());
                                }
                            }
                            List<Item> result = new ArrayList<>();
                            int index = 1;
                            for (GroundedValue member : ((ArrayItem) content).members()) {
                                if (matchKeys.contains(index)) {
                                    result.add(new JNodeForArrayMember((JNode) origin, 1, Int64Value.makeIntegerValue(index), member));
                                }
                                index++;
                            }
                            return new ListIterator.Of<>(result);
                        } else {
                            return EmptyIterator.INSTANCE;
                        }
                    } else if (origin instanceof NodeInfo) {
                        // TODO - for now
                        return EmptyIterator.INSTANCE;
                    } else {
                        // TODO - for now
                        return EmptyIterator.INSTANCE;
                    }
                };
            }
            return context -> {
                Item origin = context.getContextItem();
                GroundedValue selection = (GroundedValue) getEvaluator.evaluate(context);
                if (origin instanceof MapOrArray) {
                    origin = RootJNode.obtainRootJNode((MapOrArray) origin);
                }
                Set<AtomicMatchKey> matchKeys = new HashSet<>();
                for (Item it : selection.asIterable()) {
                    matchKeys.add(((AtomicValue) it).asMapKey(40));
                }
                if (origin instanceof JNode) {
                    SequenceIterator iter = Navigator.iterateAxis((JNode) origin, axis, AnyGNode.TEST);
                    return new ItemMappingIterator(iter, item -> {
                        JNode jnode = (JNode) item;
                        if (jnode instanceof ChildJNode && matchKeys.contains(jnode.getSelector().asMapKey(40))) {
                            return jnode;
                        } else {
                            return null;
                        }
                    });
                } else if (origin instanceof NodeInfo) {
                    SequenceIterator iter = Navigator.iterateAxis((NodeInfo) origin, axis, AnyGNode.TEST);
                    return new ItemMappingIterator(iter, item -> {
                        NodeInfo node = (NodeInfo) item;
                        if (matchKeys.contains(new QNameValue(node.getQName(), BuiltInAtomicType.QNAME))) {
                            return node;
                        } else {
                            return null;
                        }
                    });
                } else {
                    // TODO - for now
                    return EmptyIterator.INSTANCE;
                }
            };

        }


    }
}

