////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntIterator;
import net.sf.saxon.z.IntSet;

import java.util.*;


/**
 * An AxisExpression is always obtained by simplifying a PathExpression.
 * It represents a PathExpression that starts at the context node, and uses
 * a simple node-test with no filters. For example "*", "title", "./item",
 * "@*", or "ancestor::chapter*".
 * <p>An AxisExpression delivers nodes in axis order (not in document order).
 * To get nodes in document order, in the case of a reverse axis, the expression
 * should be wrapped in a call on reverse().</p>
 */

public final class AxisExpression extends Expression {

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
        if ((test == null || test == AnyNodeTest.getInstance()) &&
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
        ItemType contextItemType = contextInfo.getItemType();
        boolean noWarnings = doneOptimize || (doneTypeCheck && this.staticInfo.getItemType().equals(contextItemType));
        doneTypeCheck = true;
        if (contextItemType == ErrorType.getInstance()) {
            // There is no context item. In principle we could raise XPTY0020 ("Context item is not a node"),
            // which is a type error and therefore can be thrown statically. But many test cases expect
            // XPDY0002 ("Context item absent") which for inexplicable reasons is a dynamic error rather than
            // a type error, and therefore cannot be raised until execution time.
            XPathException err = new XPathException("Axis step " + this +
                                                            " cannot be used here: the context item is absent");
            //err.setIsTypeError(true);
            err.setErrorCode("XPDY0002");
            err.setLocation(getLocation());
            throw err;
        } else {
            staticInfo = contextInfo;
        }
        Configuration config = visitor.getConfiguration();

        if (contextItemType.getGenre() != Genre.NODE) {
            TypeHierarchy th = config.getTypeHierarchy();
            Affinity relation = th.relationship(contextItemType, AnyNodeTest.getInstance());
            if (relation == Affinity.DISJOINT) {
                XPathException err = new XPathException("Axis step " + this +
                                                                " cannot be used here: the context item is not a node");
                err.setIsTypeError(true);
                err.setErrorCode("XPTY0020");
                err.setLocation(getLocation());
                throw err;
            } else if (relation == Affinity.OVERLAPS || relation == Affinity.SUBSUMES) {
                // need to insert a dynamic check of the context item type
                Expression thisExp = checkPlausibility(visitor, contextInfo, !noWarnings);
                if (Literal.isEmptySequence(thisExp)) {
                    return thisExp;
                }
                ContextItemExpression exp = new ContextItemExpression();
                ExpressionTool.copyLocationInfo(this, exp);
                RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.AXIS_STEP, "", axis);
                role.setErrorCode("XPTY0020");
                ItemChecker checker = new ItemChecker(exp, AnyNodeTest.getInstance(), role);
                ExpressionTool.copyLocationInfo(this, checker);
                SimpleStepExpression step = new SimpleStepExpression(checker, thisExp);
                ExpressionTool.copyLocationInfo(this, step);
                return step;
            }
        }

        if (visitor.getStaticContext().getOptimizerOptions().isSet(OptimizerOptions.VOID_EXPRESSIONS)) {
            return checkPlausibility(visitor, contextInfo, !noWarnings);
        } else {
            return this;
        }
    }

    private Expression checkPlausibility(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, boolean warnings)
            throws XPathException  {
        StaticContext env = visitor.getStaticContext();
        Configuration config = env.getConfiguration();
        ItemType contextType = contextInfo.getItemType();

        if (!(contextType instanceof NodeTest)) {
            contextType = AnyNodeTest.getInstance();
        }

        // New code in terms of UTypes

        // Test whether the requested nodetest is consistent with the requested axis
        if (test != null && !AxisInfo.getTargetUType(UType.ANY_NODE, axis).overlaps(test.getUType())) {
            if (warnings) {
                visitor.issueWarning("The " + AxisInfo.axisName[axis] + " axis will never select " +
                                             test.getUType().toStringWithIndefiniteArticle(),
                                     getLocation());
            }
            return Literal.makeEmptySequence();
        }

        if (test instanceof NameTest && axis == AxisInfo.NAMESPACE && !((NameTest) test).getNamespaceURI().isEmpty()) {
            if (warnings) {
                visitor.issueWarning("The names of namespace nodes are never prefixed, so this axis step will never select anything",
                                     getLocation());
            }
            return Literal.makeEmptySequence();
        }

        // Test whether the axis ever selects anything, when starting at this context node
        UType originUType = contextType.getUType();
        UType targetUType = AxisInfo.getTargetUType(originUType, axis);
        UType testUType = test == null ? UType.ANY_NODE : test.getUType();
        if (targetUType.equals(UType.VOID)) {
            if (warnings) {
                visitor.issueWarning("The " + AxisInfo.axisName[axis] + " axis starting at " +
                                             originUType.toStringWithIndefiniteArticle() + " will never select anything",
                                     getLocation());
            }
            return Literal.makeEmptySequence();
        }

        if (contextInfo.isParentless() && (axis == AxisInfo.PARENT || axis == AxisInfo.ANCESTOR)) {
            if (warnings) {
                visitor.issueWarning("The " + AxisInfo.axisName[axis] + " axis will never select anything because the context item is parentless",
                                     getLocation());
            }
            return Literal.makeEmptySequence();
        }

        // Test whether the axis ever selects a node of the right kind, when starting at this context node
        if (!targetUType.overlaps(testUType)) {
            if (warnings) {
                visitor.issueWarning("The " + AxisInfo.axisName[axis] + " axis starting at " +
                                             originUType.toStringWithIndefiniteArticle() + " will never select " +
                                             test.getUType().toStringWithIndefiniteArticle(),
                                     getLocation());
            }
            return Literal.makeEmptySequence();
        }

        // For an X-or-self axis, if X never selects anything, then substitute the self axis.
        int nonSelf = AxisInfo.excludeSelfAxis[axis];
        UType kind = test == null ? UType.ANY_NODE : test.getUType();
        if (axis != nonSelf) {
            UType nonSelfTarget = AxisInfo.getTargetUType(originUType, nonSelf);
            if (!nonSelfTarget.overlaps(testUType)) {
                axis = AxisInfo.SELF;
                targetUType = AxisInfo.getTargetUType(originUType, axis);
            }
        }

        ItemType target = targetUType.toItemType();
        if (test == null || test instanceof AnyNodeTest) {
            itemType = target;
        } else if (target instanceof AnyNodeTest || targetUType.subsumes(test.getUType())) {
            itemType = test;
        } else {
            itemType = new CombinedNodeTest((NodeTest) target, Token.INTERSECT, test);
        }

        int origin = contextType.getPrimitiveType();

        if (test != null) {

            // If the content type of the context item is known, see whether the node test can select anything

            if (contextType instanceof DocumentNodeTest && kind.equals(UType.ELEMENT)) {
                NodeTest elementTest = ((DocumentNodeTest) contextType).getElementTest();
                Optional<IntSet> outermostElementNames = elementTest.getRequiredNodeNames();
                if (outermostElementNames.isPresent()) {
                    Optional<IntSet> selectedElementNames = test.getRequiredNodeNames();
                    if (selectedElementNames.isPresent()) {
                        if (axis == AxisInfo.CHILD) {
                            // check that the name appearing in the step is one of the names allowed by the nodetest

                            if (selectedElementNames.get().intersect(outermostElementNames.get()).isEmpty()) {
                                if (warnings) {
                                    visitor.issueWarning(
                                            "Starting at a document node, the step is selecting an element whose name " +
                                                    "is not among the names of child elements permitted for this document node type", getLocation());
                                }

                                return Literal.makeEmptySequence();
                            }

                            if (env.getPackageData().isSchemaAware() &&
                                    elementTest instanceof SchemaNodeTest &&
                                    outermostElementNames.get().size() == 1) {
                                IntIterator oeni = outermostElementNames.get().iterator();
                                int outermostElementName = oeni.hasNext() ? oeni.next() : -1;
                                SchemaDeclaration decl = config.getElementDeclaration(outermostElementName);
                                if (decl == null) {
                                    if (warnings) {
                                        visitor.issueWarning("Element " + config.getNamePool().getEQName(outermostElementName) +
                                                                     " is not declared in the schema", getLocation());
                                    }
                                    itemType = elementTest;
                                } else {
                                    SchemaType contentType = decl.getType();
                                    itemType = new CombinedNodeTest(
                                            elementTest, Token.INTERSECT,
                                            new ContentTypeTest(Type.ELEMENT, contentType, config, true));
                                }
                            } else {
                                itemType = elementTest;
                            }
                            return this;

                        } else if (axis == AxisInfo.DESCENDANT) {
                            // check that the name appearing in the step is one of the names allowed by the nodetest
                            boolean canMatchOutermost = !selectedElementNames.get().intersect(outermostElementNames.get()).isEmpty();
                            if (!canMatchOutermost) {
                                // The expression /descendant::x starting at the document node doesn't match the outermost
                                // element, so replace it by child::*/descendant::x, and check that
                                Expression path = ExpressionTool.makePathExpression(new AxisExpression(AxisInfo.CHILD, elementTest), new AxisExpression(AxisInfo.DESCENDANT, test));
                                ExpressionTool.copyLocationInfo(this, path);
                                return path.typeCheck(visitor, contextInfo);
                            }
                        }
                    }
                }
            }

            SchemaType contentType = ((NodeTest) contextType).getContentType();
            if (contentType == AnyType.getInstance()) {
                // fast exit in non-schema-aware case
                return this;
            }

            if (!env.getPackageData().isSchemaAware()) {
                SchemaType ct = test.getContentType();
                if (!(ct == AnyType.getInstance() || ct == Untyped.getInstance() || ct == AnySimpleType.getInstance() ||
                        ct == BuiltInAtomicType.ANY_ATOMIC || ct == BuiltInAtomicType.UNTYPED_ATOMIC ||
                        ct == BuiltInAtomicType.STRING)) {
                    if (warnings) {
                        visitor.issueWarning(
                                "The " + AxisInfo.axisName[axis] + " axis will never select any typed nodes, " +
                                        "because the expression is being compiled in an environment that is not schema-aware",
                                getLocation());
                    }
                    return Literal.makeEmptySequence();
                }
            }

            int targetfp = test.getFingerprint();
            StructuredQName targetName = test.getMatchingNodeName();

            if (contentType.isSimpleType()) {
                if (warnings) {
                    if ((axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT || axis == AxisInfo.DESCENDANT_OR_SELF) &&
                            UType.PARENT_NODE_KINDS.union(UType.ATTRIBUTE).subsumes(kind)) {
                        visitor.issueWarning(
                                "The " + AxisInfo.axisName[axis] + " axis will never select any " +
                                        kind + " nodes when starting at " +
                                        (origin == Type.ATTRIBUTE ? "an attribute node" : getStartingNodeDescription(contentType)),
                                getLocation());
                    } else if (axis == AxisInfo.CHILD && kind.equals(UType.TEXT) &&
                            (getParentExpression() instanceof Atomizer)) {
                        visitor.issueWarning(
                                "Selecting the text nodes of an element with simple content may give the " +
                                        "wrong answer in the presence of comments or processing instructions. It is usually " +
                                        "better to omit the '/text()' step",
                                getLocation());
                    } else if (axis == AxisInfo.ATTRIBUTE) {
                        Iterator extensions = config.getExtensionsOfType(contentType);
                        boolean found = false;
                        if (targetfp == -1) {
                            while (extensions.hasNext()) {
                                ComplexType extension = (ComplexType) extensions.next();
                                if (extension.allowsAttributes()) {
                                    found = true;
                                    break;
                                }
                            }
                        } else {
                            while (extensions.hasNext()) {
                                ComplexType extension = (ComplexType) extensions.next();
                                try {
                                    if (extension.getAttributeUseType(targetName) != null) {
                                        found = true;
                                        break;
                                    }
                                } catch (SchemaException e) {
                                    // ignore the error
                                }
                            }
                        }
                        if (!found) {
                            visitor.issueWarning(
                                    "The " + AxisInfo.axisName[axis] + " axis will never select " +
                                            (targetName == null ?
                                                     "any attribute nodes" :
                                                     "an attribute node named " + getDiagnosticName(targetName, env)) +
                                            " when starting at " + getStartingNodeDescription(contentType), getLocation());
                            // Despite the warning, leave the expression unchanged. This is because
                            // we don't necessarily know about all extended types at compile time:
                            // in particular, we don't seal the XML Schema namespace to block extensions
                            // of built-in types
                        }
                    }
                }
            } else if (((ComplexType) contentType).isSimpleContent() &&
                    (axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT || axis == AxisInfo.DESCENDANT_OR_SELF) &&
                    UType.PARENT_NODE_KINDS.subsumes(kind)) {
                // We don't need to consider extended types here, because a type with complex content
                // can never be defined as an extension of a type with simple content
                if (warnings) {
                    visitor.issueWarning("The " + AxisInfo.axisName[axis] + " axis will never select any " +
                                                 kind +
                                                 " nodes when starting at " +
                                                 getStartingNodeDescription(contentType) +
                                                 ", as this type requires simple content", getLocation());
                }
                return Literal.makeEmptySequence();
            } else if (((ComplexType) contentType).isEmptyContent() &&
                    (axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT || axis == AxisInfo.DESCENDANT_OR_SELF)) {
                for (Iterator iter = config.getExtensionsOfType(contentType); iter.hasNext(); ) {
                    ComplexType extension = (ComplexType) iter.next();
                    if (!extension.isEmptyContent()) {
                        return this;
                    }
                }
                if (warnings) {
                    visitor.issueWarning("The " + AxisInfo.axisName[axis] + " axis will never select any" +
                                                 " nodes when starting at " +
                                                 getStartingNodeDescription(contentType) +
                                                 ", as this type requires empty content", getLocation());
                }
                return Literal.makeEmptySequence();
            } else if (axis == AxisInfo.ATTRIBUTE) {
                if (targetfp == -1) {
                    if (warnings) {
                        if (!((ComplexType) contentType).allowsAttributes()) {
                            visitor.issueWarning(
                                    "The complex type " + contentType.getDescription() +
                                            " allows no attributes other than the standard attributes in the xsi namespace",
                                    getLocation());
                        }
                    }
                } else {
                    try {
                        SchemaType schemaType;
                        if (targetfp == StandardNames.XSI_TYPE) {
                            schemaType = BuiltInAtomicType.QNAME;
                        } else if (targetfp == StandardNames.XSI_SCHEMA_LOCATION) {
                            schemaType = BuiltInListType.ANY_URIS;
                        } else if (targetfp == StandardNames.XSI_NO_NAMESPACE_SCHEMA_LOCATION) {
                            schemaType = BuiltInAtomicType.ANY_URI;
                        } else if (targetfp == StandardNames.XSI_NIL) {
                            schemaType = BuiltInAtomicType.BOOLEAN;
                        } else {
                            schemaType = ((ComplexType) contentType).getAttributeUseType(targetName);
                        }
                        if (schemaType == null) {
                            if (warnings) {
                                visitor.issueWarning(
                                        "The complex type " + contentType.getDescription() +
                                                " does not allow an attribute named " + getDiagnosticName(targetName, env),
                                        getLocation());
                                return Literal.makeEmptySequence();
                            }
                        } else {
                            itemType = new CombinedNodeTest(
                                    test,
                                    Token.INTERSECT,
                                    new ContentTypeTest(Type.ATTRIBUTE, schemaType, config, false));
                        }
                    } catch (SchemaException e) {
                        // ignore the exception
                    }
                }
            } else if (axis == AxisInfo.CHILD && kind.equals(UType.ELEMENT)) {
                try {
                    int childfp = targetfp;
                    if (targetName == null) {
                        // select="child::*"
                        if (((ComplexType) contentType).containsElementWildcard()) {
                            return this;
                        }
                        IntHashSet children = new IntHashSet();
                        ((ComplexType) contentType).gatherAllPermittedChildren(children, false);
                        if (children.isEmpty()) {
                            if (warnings) {
                                visitor.issueWarning(
                                        "The complex type " + contentType.getDescription() +
                                                " does not allow children",
                                        getLocation());
                            }
                            return Literal.makeEmptySequence();
                        }
//                            if (children.contains(-1)) {
//                                return this;
//                            }
                        if (children.size() == 1) {
                            IntIterator iter = children.iterator();
                            if (iter.hasNext()) {
                                childfp = iter.next();
                            }
                        } else {
                            return this;
                        }
                    }
                    SchemaType schemaType = ((ComplexType) contentType).getElementParticleType(childfp, true);
                    if (schemaType == null) {
                        if (warnings) {
                            StructuredQName childElement = getConfiguration().getNamePool().getStructuredQName(childfp);
                            String message = "The complex type " + contentType.getDescription() +
                                    " does not allow a child element named " + getDiagnosticName(childElement, env);
                            IntHashSet permitted = new IntHashSet();
                            ((ComplexType) contentType).gatherAllPermittedChildren(permitted, false);
                            if (!permitted.contains(-1)) {
                                IntIterator kids = permitted.iterator();
                                while (kids.hasNext()) {
                                    int kid = kids.next();
                                    StructuredQName sq = getConfiguration().getNamePool().getStructuredQName(kid);
                                    if (sq.getLocalPart().equals(childElement.getLocalPart()) && kid != childfp) {
                                        message += ". Perhaps the namespace is " +
                                                (childElement.hasURI("") ? "missing" : "wrong") +
                                                ", and " + sq.getEQName() + " was intended?";
                                        break;
                                    }
                                }
                            }
                            visitor.issueWarning(message, getLocation());
                        }
                        return Literal.makeEmptySequence();
                    } else {
                        itemType = new CombinedNodeTest(
                                test,
                                Token.INTERSECT,
                                new ContentTypeTest(Type.ELEMENT, schemaType, config, true));
                        int computedCardinality = ((ComplexType) contentType).getElementParticleCardinality(childfp, true);
                        ExpressionTool.resetStaticProperties(this);
                        if (computedCardinality == StaticProperty.ALLOWS_ZERO) {
                            // this shouldn't happen, because we've already checked for this a different way.
                            // but it's worth being safe (there was a bug involving an incorrect inference here)
                            StructuredQName childElement = getConfiguration().getNamePool().getStructuredQName(childfp);
                            visitor.issueWarning(
                                    "The complex type " + contentType.getDescription() +
                                            " appears not to allow a child element named " +
                                            getDiagnosticName(childElement, env),
                                    getLocation());
                            return Literal.makeEmptySequence();
                        }
                        if (!Cardinality.allowsMany(computedCardinality) &&
                                !(getParentExpression() instanceof FirstItemExpression) &&
                                !visitor.isOptimizeForPatternMatching()) {
                            // if there can be at most one child of this name, create a FirstItemExpression
                            // to stop the search after the first one is found
                            return FirstItemExpression.makeFirstItemExpression(this);
                        }
                    }
                } catch (SchemaException e) {
                    // ignore the exception
                }
            } else if (axis == AxisInfo.DESCENDANT && kind.equals(UType.ELEMENT) && targetfp != -1) {
                // when searching for a specific element on the descendant axis, try to produce a more
                // specific path that avoids searching branches of the tree where the element cannot occur
                try {
                    IntHashSet descendants = new IntHashSet();
                    ((ComplexType) contentType).gatherAllPermittedDescendants(descendants);
                    if (descendants.contains(-1)) {
                        return this;
                    }
                    if (descendants.contains(targetfp)) {
                        IntHashSet children = new IntHashSet();
                        ((ComplexType) contentType).gatherAllPermittedChildren(children, false);
                        IntHashSet usefulChildren = new IntHashSet();
                        boolean considerSelf = false;
                        boolean considerDescendants = false;
                        IntIterator kids = children.iterator();
                        while (kids.hasNext()) {
                            int c = kids.next();
                            if (c == targetfp) {
                                usefulChildren.add(c);
                                considerSelf = true;
                            }
                            SchemaType st = ((ComplexType) contentType).getElementParticleType(c, true);
                            if (st == null) {
                                throw new AssertionError("Can't find type for child element " + c);
                            }
                            if (st instanceof ComplexType) {
                                IntHashSet subDescendants = new IntHashSet();
                                ((ComplexType) st).gatherAllPermittedDescendants(subDescendants);
                                if (subDescendants.contains(targetfp)) {
                                    usefulChildren.add(c);
                                    considerDescendants = true;
                                }
                            }
                        }
                        itemType = test;
                        if (considerDescendants) {
                            SchemaType st = ((ComplexType) contentType).getDescendantElementType(targetfp);
                            if (st != AnyType.getInstance()) {
                                itemType = new CombinedNodeTest(
                                        test, Token.INTERSECT,
                                        new ContentTypeTest(Type.ELEMENT, st, config, true));
                            }
                            //return this;
                        }
                        if (usefulChildren.size() < children.size()) {
                            NodeTest childTest = makeUnionNodeTest(usefulChildren, config.getNamePool());
                            AxisExpression first = new AxisExpression(AxisInfo.CHILD, childTest);
                            ExpressionTool.copyLocationInfo(this, first);
                            int nextAxis;
                            if (considerSelf) {
                                nextAxis = considerDescendants ? AxisInfo.DESCENDANT_OR_SELF : AxisInfo.SELF;
                            } else {
                                nextAxis = AxisInfo.DESCENDANT;
                            }
                            AxisExpression next = new AxisExpression(nextAxis, (NodeTest) itemType);
                            ExpressionTool.copyLocationInfo(this, next);
                            Expression path = ExpressionTool.makePathExpression(first, next);
                            ExpressionTool.copyLocationInfo(this, path);
                            return path.typeCheck(visitor, contextInfo);
                        }
                    } else {
                        if (warnings) {
                            visitor.issueWarning(
                                    "The complex type " + contentType.getDescription() +
                                            " does not allow a descendant element named " +
                                            getDiagnosticName(targetName, env),
                                    getLocation());
                        }
                    }
                } catch (SchemaException e) {
                    throw new AssertionError(e);
                }


            }
        }

        return this;
    }

    /*
     * Get a string representation of a name to use in diagnostics
     */

    private static String getDiagnosticName(StructuredQName name, StaticContext env) {
        String uri = name.getURI();
        if (uri.equals("")) {
            return name.getLocalPart();
        } else {
            NamespaceResolver resolver = env.getNamespaceResolver();
            for (Iterator<String> it = resolver.iteratePrefixes(); it.hasNext(); ) {
                String prefix = it.next();
                if (uri.equals(resolver.getURIForPrefix(prefix, true))) {
                    if (prefix.isEmpty()) {
                        return "Q{" + uri + "}" + name.getLocalPart();
                    } else {
                        return prefix + ":" + name.getLocalPart();
                    }
                }
            }
        }
        return "Q{" + uri + "}" + name.getLocalPart();
    }

    private static String getStartingNodeDescription(SchemaType type) {
        String s = type.getDescription();
        if (s.startsWith("of element")) {
            return "a valid element named" + s.substring("of element".length());
        } else if (s.startsWith("of attribute")) {
            return "a valid attribute named" + s.substring("of attribute".length());
        } else {
            return "a node with " + (type.isSimpleType() ? "simple" : "complex") + " type " + s;
        }
    }

    /**
     * Make a union node test for a set of supplied element fingerprints
     *
     * @param elements the set of integer element fingerprints to be tested for. Must not
     *                 be empty.
     * @param pool     the name pool
     * @return a NodeTest that returns true if the node is an element whose name is one of the names
     * in this set
     */

    private NodeTest makeUnionNodeTest(IntHashSet elements, NamePool pool) {
        NodeTest test = null;
        IntIterator iter = elements.iterator();
        while (iter.hasNext()) {
            int fp = iter.next();
            NodeTest nextTest = new NameTest(Type.ELEMENT, fp, pool);
            if (test == null) {
                test = nextTest;
            } else {
                test = new CombinedNodeTest(test, Token.UNION, nextTest);
            }
        }
        return test;
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
        return other instanceof AxisExpression &&
                axis == ((AxisExpression) other).axis &&
                Objects.equals(test, ((AxisExpression) other).test);
    }

    /**
     * get HashCode for comparing two expressions
     */

    @Override
    public int computeHashCode() {
        // generate an arbitrary hash code that depends on the axis and the node test
        int h = 9375162 + axis << 20;
        if (test != null) {
            h ^= test.getPrimitiveType() << 16;
            h ^= test.getFingerprint();
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
    public int computeSpecialProperties() {
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
    public final ItemType getItemType() {
        if (itemType != null) {
            return itemType;
        }
        int p = AxisInfo.principalNodeType[axis];
        switch (p) {
            case Type.ATTRIBUTE:
            case Type.NAMESPACE:
                return NodeKindTest.makeNodeKindTest(p);
            default:
                if (test == null) {
                    return AnyNodeTest.getInstance();
                } else {
                    return test;
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
    public final int computeCardinality() {
        NodeTest originNodeType;
        NodeTest nodeTest = test;
        ItemType contextItemType = staticInfo.getItemType();
        if (contextItemType instanceof NodeTest) {
            originNodeType = (NodeTest) contextItemType;
        } else if (contextItemType instanceof AnyItemType) {
            originNodeType = AnyNodeTest.getInstance();
        } else {
            // context item not a node - we'll report a type error somewhere along the line
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        }
        if (axis == AxisInfo.ATTRIBUTE && nodeTest instanceof NameTest) {
            SchemaType contentType = originNodeType.getContentType();
            if (contentType instanceof ComplexType) {
                try {
                    return ((ComplexType) contentType).getAttributeUseCardinality(nodeTest.getMatchingNodeName());
                } catch (SchemaException err) {
                    // shouldn't happen; play safe
                    return StaticProperty.ALLOWS_ZERO_OR_ONE;
                }
            } else if (contentType instanceof SimpleType) {
                return StaticProperty.EMPTY;
            }
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        } else if (axis == AxisInfo.DESCENDANT && nodeTest instanceof NameTest && nodeTest.getPrimitiveType() == Type.ELEMENT) {
            SchemaType contentType = originNodeType.getContentType();
            if (contentType instanceof ComplexType) {
                try {
                    return ((ComplexType) contentType).getDescendantElementCardinality(nodeTest.getFingerprint());
                } catch (SchemaException err) {
                    // shouldn't happen; play safe
                    return StaticProperty.ALLOWS_ZERO_OR_MORE;
                }
            } else {
                return StaticProperty.EMPTY;
            }

        } else if (axis == AxisInfo.SELF) {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        } else {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        }
        // the parent axis isn't handled by this class
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
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     * expression is the first operand of a path expression or filter expression
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        if (pathMapNodeSet == null) {
            ContextItemExpression cie = new ContextItemExpression();
            //cie.setContainer(getContainer());
            pathMapNodeSet = new PathMap.PathMapNodeSet(pathMap.makeNewRoot(cie));
        }
        return pathMapNodeSet.createArc(axis, test == null ? AnyNodeTest.getInstance() : test);
    }

    /**
     * Ask whether there is a possibility that the context item will be undefined
     *
     * @return true if this is a possibility
     */

    public boolean isContextPossiblyUndefined() {
        return staticInfo.isPossiblyAbsent();
    }

    public ContextItemStaticInfo getContextItemStaticInfo() {
        return staticInfo;
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config) throws XPathException {

        NodeTest test = getNodeTest();
        Pattern pat;

        if (test == null) {
            test = AnyNodeTest.getInstance();
        }
        if (test instanceof AnyNodeTest && (axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT || axis == AxisInfo.SELF)) {
            test = MultipleNodeKindTest.CHILD_NODE;
        }
        int kind = test.getPrimitiveType();
        if (axis == AxisInfo.SELF) {
            pat = new NodeTestPattern(test);
        } else if (axis == AxisInfo.ATTRIBUTE) {
            if (kind == Type.NODE) {
                // attribute::node() matches any attribute, and only an attribute
                pat = new NodeTestPattern(NodeKindTest.ATTRIBUTE);
            } else if (!AxisInfo.containsNodeKind(axis, kind)) {
                // for example, attribute::comment()
                pat = new NodeTestPattern(ErrorType.getInstance());
            } else {
                pat = new NodeTestPattern(test);
            }
        } else if (axis == AxisInfo.CHILD || axis == AxisInfo.DESCENDANT || axis == AxisInfo.DESCENDANT_OR_SELF) {
            if (kind != Type.NODE && !AxisInfo.containsNodeKind(axis, kind)) {
                pat = new NodeTestPattern(ErrorType.getInstance());
            } else {
                pat = new NodeTestPattern(test);
            }
        } else if (axis == AxisInfo.NAMESPACE) {
            if (kind == Type.NODE) {
                // namespace::node() matches any attribute, and only an attribute
                pat = new NodeTestPattern(NodeKindTest.NAMESPACE);
            } else if (!AxisInfo.containsNodeKind(axis, kind)) {
                // for example, namespace::comment()
                pat = new NodeTestPattern(ErrorType.getInstance());
            } else {
                pat = new NodeTestPattern(test);
            }
        } else {
            throw new XPathException("Only downwards axes are allowed in a pattern", "XTSE0340");
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
            XPathException err = new XPathException("The context item for axis step " +
                                                            this + " is absent");
            err.setErrorCode("XPDY0002");
            err.setXPathContext(context);
            err.setLocation(getLocation());
            err.setIsTypeError(true);
            throw err;
        }
        try {
            if (test == null) {
                return ((NodeInfo) item).iterateAxis(axis);
            } else {
                return ((NodeInfo) item).iterateAxis(axis, test);
            }
        } catch (ClassCastException cce) {
            XPathException err = new XPathException("The context item for axis step " +
                                                            this + " is not a node");
            err.setErrorCode("XPTY0020");
            err.setXPathContext(context);
            err.setLocation(getLocation());
            err.setIsTypeError(true);
            throw err;
        } catch (UnsupportedOperationException err) {
            if (err.getCause() instanceof XPathException) {
                XPathException ec = (XPathException) err.getCause();
                ec.maybeSetLocation(getLocation());
                ec.maybeSetContext(context);
                throw ec;
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

    public AxisIterator iterate(NodeInfo origin) {
        if (test == null) {
            return origin.iterateAxis(axis);
        } else {
            return origin.iterateAxis(axis, test);
        }
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("axis", this);
        destination.emitAttribute("name", AxisInfo.axisName[axis]);
        destination.emitAttribute("nodeTest", AlphaCode.fromItemType(test == null ? AnyNodeTest.getInstance() : test));
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
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C16);
        fsb.append(AxisInfo.axisName[axis]);
        fsb.append("::");
        fsb.append(test == null ? "node()" : test.toString());
        return fsb.toString();
    }

    @Override
    public String toShortString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C16);
        if (axis == AxisInfo.CHILD) {
            // no action
        } else if (axis == AxisInfo.ATTRIBUTE) {
            fsb.append("@");
        } else {
            fsb.append(AxisInfo.axisName[axis]);
            fsb.append("::");
        }
        if (test == null) {
            fsb.append("node()");
        } else if (test instanceof NameTest) {
            if (((NameTest) test).getNodeKind() != AxisInfo.principalNodeType[axis]) {
                fsb.append(test.toString());
            } else {
                fsb.append(test.getMatchingNodeName().getDisplayName());
            }
        } else {
            fsb.append(test.toString());
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
}

