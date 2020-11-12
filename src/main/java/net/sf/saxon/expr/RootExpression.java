////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTestPattern;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.*;


/**
 * An expression whose value is always a set of nodes containing a single node,
 * the document root. This corresponds to the XPath Expression "/", including the implicit
 * "/" at the start of a path expression with a leading "/".
 */

public class RootExpression extends Expression {


    private boolean contextMaybeUndefined = true;
    private boolean doneWarnings = false;

    public RootExpression() {
    }

    /**
     * Type-check the expression.
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, /*@Nullable*/ ContextItemStaticInfo contextInfo) throws XPathException {
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        if (contextInfo == null || contextInfo.getItemType() == null || contextInfo.getItemType().equals(ErrorType.getInstance())) {
            XPathException err = new XPathException(noContextMessage() + ": the context item is absent");
            err.setErrorCode("XPDY0002");
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            throw err;
        } else if (!doneWarnings && contextInfo.isParentless()
                && th.relationship(contextInfo.getItemType(),NodeKindTest.DOCUMENT) == Affinity.DISJOINT) {
            visitor.issueWarning(noContextMessage() + ": the context item is parentless and is not a document node", getLocation());
            doneWarnings = true;
        }
        contextMaybeUndefined = contextInfo.isPossiblyAbsent();
        if (th.isSubType(contextInfo.getItemType(), NodeKindTest.DOCUMENT)) {
            // this rewrite is important for streamability analysis
            ContextItemExpression cie = new ContextItemExpression();
            ExpressionTool.copyLocationInfo(this, cie);
            cie.setStaticInfo(contextInfo);
            return cie;
        }

        Affinity relation = th.relationship(contextInfo.getItemType(), AnyNodeTest.getInstance());
        if (relation == Affinity.DISJOINT) {
            XPathException err = new XPathException(noContextMessage() + ": the context item is not a node");
            err.setErrorCode("XPTY0020");
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            throw err;
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
     * @throws net.sf.saxon.trans.XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        // repeat the check: in XSLT insufficient information is available the first time
        return typeCheck(visitor, contextItemType);
    }

    @Override
    public int computeSpecialProperties() {
        return StaticProperty.ORDERED_NODESET |
                StaticProperty.CONTEXT_DOCUMENT_NODESET |
                StaticProperty.SINGLE_DOCUMENT_NODESET |
                StaticProperty.NO_NODES_NEWLY_CREATED;
    }

    /**
     * Ask whether there is a possibility that the context item will be undefined
     *
     * @return true if this is a possibility
     */

    public boolean isContextPossiblyUndefined() {
        return contextMaybeUndefined;
    }


    /**
     * Customize the error message on type checking
     */

    protected String noContextMessage() {
        return "Leading '/' selects nothing";
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return other instanceof RootExpression;
    }

    /**
     * Specify that the expression returns a singleton
     */

    @Override
    public final int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Determine the data type of the items returned by this expression
     *
     * @return Type.NODE
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return NodeKindTest.DOCUMENT;
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return UType.DOCUMENT;
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
        return EVALUATE_METHOD;
    }

    /**
     * get HashCode for comparing two expressions
     */

    @Override
    public int computeHashCode() {
        return "RootExpression".hashCode();
    }

    /**
     * Return the first element selected by this Expression
     *
     * @param context The evaluation context
     * @return the NodeInfo of the first selected element, or null if no element
     *         is selected
     */

    /*@Nullable*/
    public NodeInfo getNode(XPathContext context) throws XPathException {
        Item current = context.getContextItem();
        if (current == null) {
            dynamicError("Finding root of tree: the context item is absent", "XPDY0002", context);
        }
        if (current instanceof NodeInfo) {
            NodeInfo doc = ((NodeInfo) current).getRoot();
            if (doc.getNodeKind() != Type.DOCUMENT) {
                dynamicError("The root of the tree containing the context item is not a document node", "XPDY0050", context);
            }
            return doc;
        }
        typeError("Finding root of tree: the context item is not a node", "XPTY0020", context);
        // dummy return; we never get here
        return null;
    }

    /**
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as StaticProperty.VARIABLES and
     * StaticProperty.CURRENT_NODE
     */

    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT;
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
        RootExpression exp = new RootExpression();
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config the Saxon configuration
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException
     *          if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config) throws XPathException {
        return new NodeTestPattern(NodeKindTest.DOCUMENT);
    }

    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        if (pathMapNodeSet == null) {
            ContextItemExpression cie = new ContextItemExpression();
            ExpressionTool.copyLocationInfo(this, cie);
            pathMapNodeSet = new PathMap.PathMapNodeSet(pathMap.makeNewRoot(cie));
        }
        return pathMapNodeSet.createArc(AxisInfo.ANCESTOR_OR_SELF, NodeKindTest.DOCUMENT);
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        return "(/)";
    }

    @Override
    public String getExpressionName() {
        return "root";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("root", this);
        destination.endElement();
    }


    /**
     * Evaluate the expression in a given context to return an iterator
     *
     * @param context the evaluation context
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return SingletonIterator.makeIterator(getNode(context));
    }

    @Override
    public NodeInfo evaluateItem(XPathContext context) throws XPathException {
        return getNode(context);
    }

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        return getNode(context) != null;
    }


    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "RootExpression";
    }
}

