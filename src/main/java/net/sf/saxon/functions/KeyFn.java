////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.expr.sort.LocalOrderComparer;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeSetPattern;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.KeyDefinition;
import net.sf.saxon.trans.KeyDefinitionSet;
import net.sf.saxon.trans.KeyManager;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;

import java.util.ArrayList;
import java.util.List;


public class KeyFn extends SystemFunction implements StatefulSystemFunction {

    private KeyDefinitionSet staticKeySet = null;

    public KeyManager getKeyManager() {
        return getRetainedStaticContext().getPackageData().getKeyManager();
    }

    public NamespaceResolver getNamespaceResolver() {
        return getRetainedStaticContext();
    }

    /**
     * Factory method to create an internal call on key() with a known key definition
     *
     * @param keySet the set of KeyDefinitions (always a single KeyDefinition)
     * @param name   the name allocated to the key (first argument of the function)
     * @param value  the value being searched for (second argument of the function)
     * @param doc    the document being searched (third argument)
     * @return a call on the key() function
     */

    public static Expression internalKeyCall(KeyManager keyManager, KeyDefinitionSet keySet,
                                             String name, Expression value, Expression doc,
                                             RetainedStaticContext rsc) {
        KeyFn fn = (KeyFn) SystemFunction.makeFunction("key", rsc, 3);
        assert fn != null;
        fn.staticKeySet = keySet;
        try {
            fn.fixArguments(new StringLiteral(name), value, doc);
        } catch (XPathException e) {
            // ignore the failure
        }
        return fn.makeFunctionCall(new StringLiteral(name), value, doc);
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * a property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @param arguments expressions supplied as arguments in the function call
     */

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        int prop = StaticProperty.ORDERED_NODESET |
                StaticProperty.SINGLE_DOCUMENT_NODESET |
                StaticProperty.NO_NODES_NEWLY_CREATED;
        if ((getArity() == 2) ||
                (arguments[2].getSpecialProperties() & StaticProperty.CONTEXT_DOCUMENT_NODESET) != 0) {
            prop |= StaticProperty.CONTEXT_DOCUMENT_NODESET;
        }
        return prop;
    }

    @Override
    public SystemFunction copy() {
        KeyFn k2 = (KeyFn) SystemFunction.makeFunction(getFunctionName().getLocalPart(), getRetainedStaticContext(), getArity());
        k2.staticKeySet = staticKeySet;
        return k2;
    }

    /**
     * Mapping class to filter nodes that have the origin node as an ancestor-or-self
     */

    public static class SubtreeFilter implements ItemMappingFunction {

        private NodeInfo origin;

        public SubtreeFilter(NodeInfo origin) {
            this.origin = origin;
        }

        @Override
        public NodeInfo mapItem(Item item) {
            if (Navigator.isAncestorOrSelf(origin, (NodeInfo)item)) {
                return (NodeInfo)item;
            } else {
                return null;
            }
        }

    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments.
     * This binds the key definition in the common case where the key name is defined statically
     *
     * @param arguments the supplied arguments to the function call. Note: modifying the contents
     *                  of this array should not be attempted, it is likely to have no effect.
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws XPathException if an error is detected
     */
    @Override
    public Expression fixArguments(final Expression... arguments) throws XPathException {
        if (arguments[0] instanceof StringLiteral && staticKeySet == null) {
            KeyManager keyManager = getKeyManager();
            String keyName = ((StringLiteral) arguments[0]).getStringValue();
            staticKeySet = getKeyDefinitionSet(keyManager, keyName);
        }
        return null;
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
     * expression, and that represent possible results of this expression. For an expression that does
     * navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     * expressions, it is the same as the input pathMapNode.
     */

    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        if (staticKeySet != null) {
            PathMap.PathMapNodeSet result = new PathMap.PathMapNodeSet();
            for (KeyDefinition kd : staticKeySet.getKeyDefinitions()) {
                Pattern pat = kd.getMatch();
                if (pat instanceof NodeSetPattern) {
                    Expression selector = ((NodeSetPattern) pat).getSelectionExpression();
                    PathMap.PathMapNodeSet selected = selector.addToPathMap(pathMap, pathMapNodeSet);
                    Expression use = kd.getUse();
                    PathMap.PathMapNodeSet used = use.addToPathMap(pathMap, selected);
                    result.addNodeSet(selected);
                } else {
                    throw new IllegalStateException("Can't add key() call to pathmap");
                }
            }
            return result;
        } else {
            throw new IllegalStateException("Can't add dynamic key() call to pathmap");
        }
    }


    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo origin;
        if (arguments.length == 3) {
            origin = (NodeInfo) getOrigin(context, arguments[2]);
        } else {
            origin = getContextRoot(context);
        }
        if (origin.getRoot().getNodeKind() != Type.DOCUMENT) {
            throw new XPathException("In the key() function," +
                                             " the node supplied in the third argument (or the context node if absent)" +
                                             " must be in a tree whose root is a document node", "XTDE1270", context);
        }

        KeyDefinitionSet selectedKeySet = staticKeySet;
        KeyManager keyManager = getKeyManager();
        if (selectedKeySet == null) {
            selectedKeySet = getKeyDefinitionSet(keyManager, arguments[0].head().getStringValue());
        }
        return search(keyManager, context, arguments[1], origin, selectedKeySet);

    }

    private static NodeInfo getContextRoot(XPathContext context) throws XPathException {
        Item contextItem = context.getContextItem();
        if (contextItem == null) {
            throw new XPathException("Cannot call the key() function when there is no context item", "XTDE1270", context);
        } else if (!(contextItem instanceof NodeInfo)) {
            throw new XPathException("Cannot call the key() function when the context item is not a node", "XTDE1270", context);
        }
        return ((NodeInfo) contextItem).getRoot();
    }

    private static Item getOrigin(XPathContext context, Sequence argument2) throws XPathException {
        Item arg2;
        try {
            arg2 = argument2.head();
        } catch (XPathException e) {
            String code = e.getErrorCodeLocalPart();
            if ("XPDY0002".equals(code) && argument2 instanceof RootExpression) {
                throw new XPathException("Cannot call the key() function when there is no context node", "XTDE1270", context);
            } else if ("XPDY0050".equals(code)) {
                throw new XPathException("In the key() function," +
                                                 " the node supplied in the third argument (or the context node if absent)" +
                                                 " must be in a tree whose root is a document node", "XTDE1270", context);
            } else if ("XPTY0020".equals(code) || "XPTY0019".equals(code)) {
                throw new XPathException("Cannot call the key() function when the context item is an atomic value",
                                         "XTDE1270", context);
            }
            throw e;
        }
        return arg2;
    }

    private KeyDefinitionSet getKeyDefinitionSet(KeyManager keyManager, String keyName) throws XPathException {
        KeyDefinitionSet selectedKeySet;
        StructuredQName qName = null;
        try {
            qName = StructuredQName.fromLexicalQName(
                    keyName, false, true,
                    getNamespaceResolver());
        } catch (XPathException err) {
            throw new XPathException("Invalid key name: " + err.getMessage(), "XTDE1260");
        }
        selectedKeySet = keyManager.getKeyDefinitionSet(qName);
        if (selectedKeySet == null) {
            throw new XPathException("Key '" + keyName + "' has not been defined", "XTDE1260");
        }
        return selectedKeySet;
    }

    protected static Sequence search(
            final KeyManager keyManager, XPathContext context, Sequence sought, NodeInfo origin, KeyDefinitionSet selectedKeySet) throws XPathException {


//        if (internal) {
//            System.err.println("Using key " + fprint + " on doc " + doc);
//        }
        NodeInfo doc = origin.getRoot();
        if (selectedKeySet.isComposite()) {
            SequenceIterator soughtKey = sought.iterate();
            SequenceIterator all =
                    keyManager.selectByCompositeKey(selectedKeySet, doc.getTreeInfo(), soughtKey, context);
            if (origin.equals(doc)) {
                return new LazySequence(all);
            }
            return new LazySequence(new ItemMappingIterator(all, new SubtreeFilter(origin)));

        } else {
            // Changed by bug 2929 and bug 4656
            SequenceIterator allResults = null;
            SequenceIterator keys = sought.iterate();
            AtomicValue keyValue;
            List<SequenceIterator> allKeyIterators = new ArrayList<>();
            while ((keyValue = (AtomicValue)keys.next()) != null) {
                SequenceIterator someResults = keyManager.selectByKey(selectedKeySet, doc.getTreeInfo(), keyValue, context);
                allKeyIterators.add(someResults);
            }
            if (allKeyIterators.isEmpty()) {
                allResults = EmptyIterator.ofNodes();
            } else if (allKeyIterators.size() == 1) {
                allResults = allKeyIterators.get(0);
            } else {
                allResults = new UnionIterator(allKeyIterators, LocalOrderComparer.getInstance());
            }
            if (origin.equals(doc)) {
                return new LazySequence(allResults);
            }
            return new LazySequence(new ItemMappingIterator(allResults, new SubtreeFilter(origin)));
        }
    }

}


