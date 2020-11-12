////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.*;
import net.sf.saxon.functions.Doc;
import net.sf.saxon.functions.DocumentFn;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * A PathMap is a description of all the paths followed by an expression.
 * It is a set of trees. Each tree contains as its root an expression that selects
 * nodes without any dependency on the context. The arcs in the tree are axis steps.
 * So the expression doc('a.xml')/a[b=2]/c has a single root (the call on doc()), with
 * a single arc representing child::a, this leads to a node which has two further arcs
 * representing child::b and child::c. Because element b is atomized, there will also be
 * an arc for the step descendant::text() indicating the requirement to access the text
 * nodes of the element.
 * <p>The current implementation works only for XPath 2.0 expressions (for example, constructs
 * like xsl:for-each-group are not handled.)</p>
 * <p>This class, together with the overloaded method
 * {@link net.sf.saxon.expr.Expression#addToPathMap(PathMap, PathMap.PathMapNodeSet)} can be
 * seen as an implementation of the static path analysis algorithm given in section 4 of
 * <a href="http://www-db.research.bell-labs.com/user/simeon/xml_projection.pdf">A. Marian and J. Simeon,
 * Projecting XML Documents, VLDB 2003</a>.</p>
 */

public class PathMap {

    /*@NotNull*/ private List<PathMapRoot> pathMapRoots = new ArrayList<PathMapRoot>();
    /*@NotNull*/ private HashMap<Binding, PathMapNodeSet> pathsForVariables =
            new HashMap<Binding, PathMapNodeSet>();  // a map from a variable Binding to a PathMapNodeSet

    /**
     * A node in the path map. A node holds a set of arcs, each representing a link to another
     * node in the path map.
     */

    public static class PathMapNode {
        List<PathMapArc> arcs;
        private boolean returnable;
        private boolean atomized;
        private boolean hasUnknownDependencies;

        /**
         * Create a node in the PathMap (initially with no arcs)
         */

        private PathMapNode() {
            arcs = new ArrayList<PathMapArc>();
        }

        /**
         * Create a new arc
         *
         * @param axis the axis of this step
         * @param test the node test of this step
         * @return the newly-constructed target of the new arc
         */

        public PathMapNode createArc(int axis, /*@NotNull*/ NodeTest test) {
            for (PathMapArc a : arcs) {
                if (a.getAxis() == axis && a.getNodeTest().equals(test)) {
                    return a.getTarget();
                }
            }
            PathMapNode target = new PathMapNode();
            PathMapArc arc = new PathMapArc(axis, test, target);
            arcs.add(arc);
            return target;
        }

        /**
         * Create a new arc to an existing target
         *
         * @param axis   the axis of this step
         * @param test   the node test of this step
         * @param target the target node of the new arc
         */

        public void createArc(int axis, /*@NotNull*/ NodeTest test, /*@NotNull*/ PathMapNode target) {
            for (PathMapArc a : arcs) {
                if (a.getAxis() == axis && a.getNodeTest().equals(test) && a.getTarget() == target) {
                    // TODO: if it's a different target, then merge the two targets into one. XMark Q8
                    a.getTarget().setReturnable(a.getTarget().isReturnable() || target.isReturnable());
                    if (target.isAtomized()) {
                        a.getTarget().setAtomized();
                    }
                    return;
                }
            }
            PathMapArc arc = new PathMapArc(axis, test, target);
            arcs.add(arc);
        }

        /**
         * Get the arcs emanating from this node in the PathMap
         *
         * @return the arcs, each representing an AxisStep. The order of arcs in the array is undefined.
         */

        public PathMapArc[] getArcs() {
            return arcs.toArray(new PathMapArc[arcs.size()]);
        }

        /**
         * Indicate that the node represents a value that is returnable as the result of the
         * supplied expression, rather than merely a node that is visited en route
         *
         * @param returnable true if the node represents a final result of the expression
         */

        public void setReturnable(boolean returnable) {
            this.returnable = returnable;
        }

        /**
         * Ask whether the node represents a value that is returnable as the result of the
         * supplied expression, rather than merely a node that is visited en route
         *
         * @return true if the node represents a final result of the expression
         */

        public boolean isReturnable() {
            return returnable;
        }

        /**
         * Test whether there are any returnable nodes reachable from this node by
         * zero or more arcs
         *
         * @return true if any arcs lead to a pathmap node representing a returnable XDM node
         */

        public boolean hasReachableReturnables() {
            if (isReturnable()) {
                return true;
            }
            for (PathMapArc arc : arcs) {
                if (arc.getTarget().hasReachableReturnables()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Indicate that the typed value or string value of the node reached by this path
         * will be used. Note that because this is being used only to determine navigation paths,
         * the property does not need to be set when nodes other than element or document nodes
         * are atomized.
         */

        public void setAtomized() {
            this.atomized = true;
        }

        /**
         * Ask whether the typed value (or string value) of the node reached by this path
         * will be required.
         *
         * @return true if the typed value or string value of the node is required
         */

        public boolean isAtomized() {
            return atomized;
        }

        /**
         * Indicate that the path has unknown dependencies, typically because a node reached
         * by the path is supplied as an argument to a user-defined function
         */

        public void setHasUnknownDependencies() {
            hasUnknownDependencies = true;
        }

        /**
         * Ask whether the path has unknown dependencies, typically because a node reached
         * by the path is supplied as an argument to a user-defined function
         *
         * @return true if the path has unknown dependencies
         */

        public boolean hasUnknownDependencies() {
            return hasUnknownDependencies;
        }

        /**
         * Determine whether the path is entirely within a streamable snapshot of a streamed document:
         * that is, it must only navigate to ancestors and to attributes of ancestors
         *
         * @return true if this path performs navigation other than to ancestors and their attributes
         */

        public boolean allPathsAreWithinStreamableSnapshot() {
            if (hasUnknownDependencies() || isReturnable() || isAtomized()) {
                return false;
            }
            for (PathMapArc arc : arcs) {
                int axis = arc.getAxis();
                if (axis == AxisInfo.ATTRIBUTE) {
                    PathMapNode next = arc.getTarget();
                    if (next.isReturnable()) {
                        return false;
                    }
                    if (next.getArcs().length != 0 && !next.allPathsAreWithinStreamableSnapshot()) {
                        return false;
                    }
                } else if (axis == AxisInfo.SELF || axis == AxisInfo.ANCESTOR || axis == AxisInfo.ANCESTOR_OR_SELF ||
                        axis == AxisInfo.PARENT) {
                    PathMapNode next = arc.getTarget();
                    if (next.isAtomized()) {
                        return false;
                    }
                    if (!next.allPathsAreWithinStreamableSnapshot()) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * A root node in the path map. A root node represents either (a) a subexpression that is the first step in
     * a path expression, or (b) a subexpression that is not the first step in a path, but which returns nodes
     * (for example, a call on the doc() function).
     */

    public static class PathMapRoot extends PathMapNode {

        private Expression rootExpression;
        private boolean isDownwardsOnly;

        /**
         * Create a PathMapRoot
         *
         * @param root the expression at the root of a path
         */
        private PathMapRoot(Expression root) {
            this.rootExpression = root;
        }

        /**
         * Get the root expression
         *
         * @return the expression at the root of the path
         */
        public Expression getRootExpression() {
            return rootExpression;
        }

    }

    /**
     * An arc joining two nodes in the path map. The arc has a target (destination) node, and is
     * labelled with an AxisExpression representing a step in a path expression
     */

    public static class PathMapArc {
        private PathMapNode target;
        private int axis;
        private NodeTest test;

        /**
         * Create a PathMapArc
         *
         * @param axis   the axis (a constant from class {@link net.sf.saxon.om.AxisInfo}
         * @param test   the node test
         * @param target the node reached by following this arc
         */
        private PathMapArc(int axis, /*@NotNull*/ NodeTest test, /*@NotNull*/ PathMapNode target) {
            this.axis = axis;
            this.test = test;
            this.target = target;
        }

        /**
         * Get the Axis associated with this arc
         *
         * @return the axis, a constant from class {@link net.sf.saxon.om.AxisInfo}
         */

        public int getAxis() {
            return axis;
        }

        /**
         * Get the NodeTest associated with this arc
         *
         * @return the NodeTest
         */

        public NodeTest getNodeTest() {
            return test;
        }

        /**
         * Get the target node representing the destination of this arc
         *
         * @return the target node
         */

        public PathMapNode getTarget() {
            return target;
        }
    }

    /**
     * A (mutable) set of nodes in the path map
     */

    public static class PathMapNodeSet extends HashSet<PathMapNode> {

        /**
         * Create an initially-empty set of path map nodes
         */

        public PathMapNodeSet() {
        }

        /**
         * Create a set of path map nodes that initially contains a single node
         *
         * @param singleton the single node to act as the initial content
         */

        public PathMapNodeSet(PathMapNode singleton) {
            add(singleton);
        }

        /**
         * Create an arc from each node in this node set to a corresponding newly-created
         * target node
         *
         * @param axis the axis of the step defining the transition
         * @param test the node test of the step defining the transition
         * @return the set of new target nodes
         */

        /*@NotNull*/
        public PathMapNodeSet createArc(int axis, /*@NotNull*/ NodeTest test) {
            PathMapNodeSet targetSet = new PathMapNodeSet();
            for (PathMapNode node : this) {
                targetSet.add(node.createArc(axis, test));
            }
            return targetSet;
        }

        /**
         * Combine two node sets into one
         *
         * @param nodes the set of nodes to be added to this set
         */

        public void addNodeSet(/*@Nullable*/ PathMapNodeSet nodes) {
            if (nodes != null) {
                for (PathMapNode node : nodes) {
                    add(node);
                }
            }
        }

        /**
         * Set the atomized property on all nodes in this nodeset
         */

        public void setAtomized() {
            for (PathMapNode node : this) {
                node.setAtomized();
            }
        }

        /**
         * Set the returnable property on all nodes in this nodeset
         */

        public void setReturnable(boolean isReturned) {
            for (PathMapNode node : this) {
                node.setReturnable(isReturned);
            }
        }

        /**
         * Test whether there are any returnable nodes reachable from nodes in this nodeset
         */

        public boolean hasReachableReturnables() {
            for (PathMapNode node : this) {
                if (node.hasReachableReturnables()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Determine whether the path is entirely within a streamable snapshot of a streamed document:
         * that is, it must only navigate to ancestors and to attributes of ancestors
         */

        public boolean allPathsAreWithinStreamableSnapshot() {
            for (PathMapNode node : this) {
                if (!node.allPathsAreWithinStreamableSnapshot()) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Indicate that all the descendants of the nodes in this nodeset are required
         */

        public void addDescendants() {
            for (PathMapNode node : this) {
                node.createArc(AxisInfo.DESCENDANT, AnyNodeTest.getInstance());
            }
        }

        /**
         * Indicate that all the nodes have unknown dependencies
         */

        public void setHasUnknownDependencies() {
            for (PathMapNode node : this) {
                node.setHasUnknownDependencies();
            }
        }

    }

    /**
     * Create the PathMap for an expression
     *
     * @param exp the expression whose PathMap is required
     */

    public PathMap(/*@NotNull*/ Expression exp) {
        PathMapNodeSet finalNodes = exp.addToPathMap(this, null);
        if (finalNodes != null) {
            for (PathMapNode node : finalNodes) {
                node.setReturnable(true);
            }
        }
    }

    /**
     * Make a new root node in the path map. However, if there is already a root for the same
     * expression, the existing root for that expression is returned.
     *
     * @param exp the expression represented by this root node
     * @return the new root node
     */

    public PathMapRoot makeNewRoot(/*@NotNull*/ Expression exp) {
        for (PathMapRoot r : pathMapRoots) {
            if (exp.isEqual(r.getRootExpression())) {
                return r;
            }
        }
        PathMapRoot root = new PathMapRoot(exp);
        pathMapRoots.add(root);
        return root;
    }

    /**
     * Get all the root expressions from the path map
     *
     * @return an array containing the root expressions
     */

    public PathMapRoot[] getPathMapRoots() {
        return pathMapRoots.toArray(new PathMapRoot[pathMapRoots.size()]);
    }

    /**
     * Register the path used when evaluating a given variable binding
     *
     * @param binding the variable binding
     * @param nodeset the set of PathMap nodes reachable when evaluating that variable
     */

    public void registerPathForVariable(Binding binding, PathMapNodeSet nodeset) {
        pathsForVariables.put(binding, nodeset);
    }

    /**
     * Get the path used when evaluating a given variable binding
     *
     * @param binding the variable binding
     * @return the set of PathMap nodes reachable when evaluating that variable
     */

    public PathMapNodeSet getPathForVariable(Binding binding) {
        return pathsForVariables.get(binding);
    }

    /**
     * Get the path map root for the context document
     *
     * @return the path map root for the context document if there is one, or null if none is found.
     * @throws IllegalStateException if there is more than one path map root for the context document
     */

    /*@Nullable*/
    public PathMapRoot getContextDocumentRoot() {
        //System.err.println("BEFORE REDUCTION:");
        //map.diagnosticDump(System.err);
        PathMap.PathMapRoot[] roots = getPathMapRoots();
        PathMapRoot contextRoot = null;
        for (PathMapRoot root : roots) {
            PathMapRoot newRoot = reduceToDownwardsAxes(root);
            if (newRoot.getRootExpression() instanceof RootExpression) {
                if (contextRoot != null) {
                    throw new IllegalStateException("More than one context document root found in path map");
                } else {
                    contextRoot = newRoot;
                }
            }
        }
        //System.err.println("AFTER REDUCTION:");
        //map.diagnosticDump(System.err);
        return contextRoot;
    }

    /**
     * Get the path map root for the context item
     *
     * @return the path map root for the context item if there is one, or null if none is found.
     * @throws IllegalStateException if there is more than one path map root for the context item
     */

    /*@Nullable*/
    public PathMapRoot getContextItemRoot() {
        //System.err.println("BEFORE REDUCTION:");
        //map.diagnosticDump(System.err);
        PathMap.PathMapRoot[] roots = getPathMapRoots();
        PathMapRoot contextRoot = null;
        for (PathMapRoot root : roots) {
            if (root.getRootExpression() instanceof ContextItemExpression) {
                if (contextRoot != null) {
                    throw new IllegalStateException("More than one context document root found in path map");
                } else {
                    contextRoot = root;
                }
            }
        }
        return contextRoot;
    }

    /**
     * Get the path map root for a call on the doc() or document() function with a given literal argument
     *
     * @param requiredUri the literal argument we are looking for
     * @return the path map root for the specified document if there is one, or null if none is found.
     * @throws IllegalStateException if there is more than one path map root for the specified document
     */

    /*@Nullable*/
    public PathMapRoot getRootForDocument(/*@NotNull*/ String requiredUri) {
        //System.err.println("BEFORE REDUCTION:");
        //map.diagnosticDump(System.err);
        PathMap.PathMapRoot[] roots = getPathMapRoots();
        PathMapRoot requiredRoot = null;
        for (PathMapRoot root : roots) {
            PathMapRoot newRoot = reduceToDownwardsAxes(root);
            Expression exp = newRoot.getRootExpression();
            String baseUri;
            if (exp.isCallOn(Doc.class)) {
                baseUri = exp.getStaticBaseURIString();
            } else if (exp.isCallOn(DocumentFn.class)) {
                baseUri = exp.getStaticBaseURIString();
            } else {
                continue;
            }
            Expression arg = ((SystemFunctionCall) exp).getArg(0);
            String suppliedUri = null;
            if (arg instanceof Literal) {
                try {
                    String argValue = ((Literal) arg).getValue().getStringValue();
                    if (baseUri == null) {
                        if (new URI(argValue).isAbsolute()) {
                            suppliedUri = argValue;
                        } else {
                            suppliedUri = null;
                        }
                    } else {
                        suppliedUri = ResolveURI.makeAbsolute(argValue, baseUri).toString();
                    }
                } catch (URISyntaxException err) {
                    suppliedUri = null;
                } catch (XPathException err) {
                    suppliedUri = null;
                }
            }
            if (requiredUri.equals(suppliedUri)) {
                if (requiredRoot != null) {
                    throw new IllegalStateException("More than one document root found in path map for " + requiredUri);
                } else {
                    requiredRoot = newRoot;
                }
            }
        }
        //System.err.println("AFTER REDUCTION:");
        //map.diagnosticDump(System.err);
        return requiredRoot;
    }

    /**
     * Given a PathMapRoot, simplify the tree rooted at this node so that
     * it only contains downwards selections: specifically, so that the only axes
     * used are child, attribute, namespace, and descendant. If the root expression
     * is a ContextItemExpression (that is, the path can start at any node) then it is rebased
     * to start at a root node, which means in effect that a path such as a/b/c is treated
     * as //a/b/c.
     *
     * @param root the root of the path to be simplified
     * @return the path map root after converting the tree to use downwards axes only
     */

    public PathMapRoot reduceToDownwardsAxes(/*@NotNull*/ PathMapRoot root) {
        // If the path is rooted at an arbitrary context node, we rebase it to be rooted at the
        // document root. This involves changing the root to a RootExpression, and changing the axis
        // for initial steps from child to descendant where necessary
        if (root.isDownwardsOnly) {
            return root;
        }
        PathMapRoot newRoot = root;
        if (root.getRootExpression() instanceof ContextItemExpression) {
            RootExpression slash = new RootExpression();
            //slash.setContainer(root.getRootExpression().getContainer());
            //root.setRootExpression(slash);
            newRoot = makeNewRoot(slash);
            for (int i = root.arcs.size() - 1; i >= 0; i--) {
                PathMapArc arc = root.arcs.get(i);
                int axis = arc.getAxis();
                switch (axis) {
                    case AxisInfo.ATTRIBUTE:
                    case AxisInfo.NAMESPACE: {
                        PathMapNode newTarget = new PathMapNode();
                        newTarget.arcs.add(arc);
                        newRoot.createArc(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT, newTarget);
                        break;
                    }
                    default: {
                        newRoot.createArc(AxisInfo.DESCENDANT_OR_SELF, arc.getNodeTest(), arc.getTarget());
                        break;
                    }
                }
            }
            for (int i = 0; i < pathMapRoots.size(); i++) {
                if (pathMapRoots.get(i) == root) {
                    pathMapRoots.remove(i);
                    break;
                }
            }
        }
        // Now process the tree of paths recursively, rewriting all axes in terms of downwards
        // selections, if necessary as downward selections from the root
        Stack<PathMapNode> nodeStack = new Stack<PathMapNode>();
        nodeStack.push(newRoot);
        reduceToDownwardsAxes(newRoot, nodeStack);
        newRoot.isDownwardsOnly = true;
        return newRoot;
    }

    /**
     * Supporting method for {@link #reduceToDownwardsAxes(PathMap.PathMapRoot)}
     *
     * @param root      the root of the path being simplified
     * @param nodeStack the sequence of nodes by which the current node in the path map was reached.
     *                  The node at the bottom of the stack is the root.
     */

    private void reduceToDownwardsAxes(/*@NotNull*/ PathMapRoot root, /*@NotNull*/ Stack<PathMapNode> nodeStack) {
        //PathMapArc lastArc = (PathMapArc)arcStack.peek();
        //byte lastAxis = lastArc.getStep().getAxis();
        PathMapNode node = nodeStack.peek();
        if (node.hasUnknownDependencies()) {
            root.setHasUnknownDependencies();
        }

        for (int i = 0; i < node.arcs.size(); i++) {
            nodeStack.push((node.arcs.get(i)).getTarget());
            reduceToDownwardsAxes(root, nodeStack);
            nodeStack.pop();
        }

        for (int i = node.arcs.size() - 1; i >= 0; i--) {
            PathMapArc thisArc = node.arcs.get(i);
            //AxisExpression axisStep = thisArc.getStep();
            PathMapNode grandParent =
                    (nodeStack.size() < 2 ? null : nodeStack.get(nodeStack.size() - 2));
            int lastAxis = -1;
            if (grandParent != null) {
                for (PathMapArc arc1 : grandParent.arcs) {
                    PathMapArc arc = (arc1);
                    if (arc.getTarget() == node) {
                        lastAxis = arc.getAxis();
                    }
                }
            }
            switch (thisArc.getAxis()) {

                case AxisInfo.ANCESTOR_OR_SELF:
                case AxisInfo.DESCENDANT_OR_SELF:
                    if (thisArc.getNodeTest() == NodeKindTest.DOCUMENT) {
                        // This is typically an absolute path expression appearing within a predicate
                        node.arcs.remove(i);
                        for (PathMapArc arc : thisArc.getTarget().arcs) {
                            root.arcs.add(arc);
                        }
                        break;
                    } else {
                        // fall through
                    }

                case AxisInfo.ANCESTOR:
                case AxisInfo.FOLLOWING:
                case AxisInfo.PRECEDING: {
                    // replace the axis by a downwards axis from the root
                    if (thisArc.getAxis() != AxisInfo.DESCENDANT_OR_SELF) {
                        root.createArc(AxisInfo.DESCENDANT_OR_SELF, thisArc.getNodeTest(), thisArc.getTarget());
                        node.arcs.remove(i);
                    }
                    break;
                }

                case AxisInfo.ATTRIBUTE:
                case AxisInfo.CHILD:
                case AxisInfo.DESCENDANT:
                case AxisInfo.NAMESPACE:
                    // no action
                    break;

                case AxisInfo.FOLLOWING_SIBLING:
                case AxisInfo.PRECEDING_SIBLING: {
                    if (grandParent != null) {
                        grandParent.createArc(lastAxis, thisArc.getNodeTest(), thisArc.getTarget());
                        node.arcs.remove(i);
                        break;
                    } else {
                        root.createArc(AxisInfo.CHILD, thisArc.getNodeTest(), thisArc.getTarget());
                        node.arcs.remove(i);
                        break;
                    }
                }
                case AxisInfo.PARENT: {

                    if (lastAxis == AxisInfo.CHILD || lastAxis == AxisInfo.ATTRIBUTE || lastAxis == AxisInfo.NAMESPACE) {
                        // ignore the parent step - it leads to somewhere we have already been.
                        // But it might become a returned node
                        if (node.isReturnable()) {
                            grandParent.setReturnable(true);
                        }
                        // any paths after the parent step need to be attached to the grandparent

                        PathMapNode target = thisArc.getTarget();
                        for (int a = 0; a < target.arcs.size(); a++) {
                            grandParent.arcs.add(target.arcs.get(a));
                        }
                        node.arcs.remove(i);
                    } else if (lastAxis == AxisInfo.DESCENDANT) {
                        if (thisArc.getTarget().arcs.isEmpty()) {
                            grandParent.createArc(AxisInfo.DESCENDANT_OR_SELF, thisArc.getNodeTest());
                        } else {
                            grandParent.createArc(AxisInfo.DESCENDANT_OR_SELF, thisArc.getNodeTest(), thisArc.getTarget());
                        }
                        node.arcs.remove(i);
                    } else {
                        // don't try to be precise about a/b/../../c
                        if (thisArc.getTarget().arcs.isEmpty()) {
                            root.createArc(AxisInfo.DESCENDANT_OR_SELF, thisArc.getNodeTest());
                        } else {
                            root.createArc(AxisInfo.DESCENDANT_OR_SELF, thisArc.getNodeTest(), thisArc.getTarget());
                        }
                        node.arcs.remove(i);
                    }
                    break;
                }
                case AxisInfo.SELF: {
                    // This step can't take us anywhere we haven't been, so delete it
                    node.arcs.remove(i);
                    break;
                }
            }
        }

    }

    /**
     * Display a printed representation of the path map
     *
     * @param out the output stream to which the output will be written
     */

    public void diagnosticDump(/*@NotNull*/ Logger out) {
        for (int i = 0; i < pathMapRoots.size(); i++) {
            out.info("\nROOT EXPRESSION " + i);
            PathMapRoot mapRoot = pathMapRoots.get(i);
            if (mapRoot.hasUnknownDependencies()) {
                out.info("  -- has unknown dependencies --");
            }
            Expression exp = mapRoot.rootExpression;
            exp.explain(out);
            out.info("\nTREE FOR EXPRESSION " + i);
            showArcs(out, mapRoot, 2);
        }
    }

    /**
     * Internal helper method called by diagnosticDump, to show the arcs emanating from a node.
     * Each arc is shown as a representation of the axis step, followed optionally by "@" if the
     * node reached by the arc is atomized, followed optionally by "#" if the
     * node reached by the arc is a final returnable node.
     *
     * @param out    the output stream
     * @param node   the node in the path map whose arcs are to be displayed
     * @param indent the indentation level in the output
     */

    private void showArcs(Logger out, PathMapNode node, int indent) {
        String pad = "                                           ".substring(0, indent);
        List<PathMapArc> arcs = node.arcs;
        for (PathMapArc arc : arcs) {
            out.info(pad + AxisInfo.axisName[arc.axis] +
                    "::" +
                    arc.test.toString() +
                    (arc.target.isAtomized() ? " @" : "") +
                    (arc.target.isReturnable() ? " #" : "") +
                    (arc.target.hasUnknownDependencies() ? " ...??" : ""));
            showArcs(out, arc.target, indent + 2);
        }
    }

}

