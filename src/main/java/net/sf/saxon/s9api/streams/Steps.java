////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.s9api.streams;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.Converter;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static net.sf.saxon.s9api.streams.Predicates.*;

/**
 * This non-instantiable class provides a number of useful implementations of the {@link Step}
 * interface, used to navigate XDM trees, typically as an argument to {@link XdmValue#select}.
 */

public class Steps {
    /**
     * Obtain a {@link Step} that selects the root node of the containing document (which may or may not
     * be a document node)
     */

    public static Step<XdmNode> root() {
        return new Step<XdmNode>() {
            @Override
            public Stream<XdmNode> apply(XdmItem origin) {
                return origin instanceof XdmNode
                        ? Stream.of(((XdmNode)origin).getRoot())
                        : Stream.empty();
            }
        };
    }
    /**
     * Obtain a {@link Step} that atomizes an item to produce a stream of atomic values. (Atomizing a node will
     * usually produce a single atomic value, but in the case of schema-typed nodes using a list
     * type, there may be more than one atomic value. Atomizing an array also returns multiple
     * atomic values)
     */

    public static Step<XdmAtomicValue> atomize() {
        return new Step<XdmAtomicValue>() {
            @Override
            public Stream<? extends XdmAtomicValue> apply(XdmItem item) {
                if (item instanceof XdmAtomicValue) {
                    return Stream.of((XdmAtomicValue)item);
                } else if (item instanceof XdmNode) {
                    try {
                        return (XdmStream<XdmAtomicValue>) ((XdmNode) item).getTypedValue().stream();
                    } catch (SaxonApiException e) {
                        throw new SaxonApiUncheckedException(e);
                    }
                } else if (item instanceof XdmArray) {
                    try {
                        ArrayItem arrayItem = ((XdmArray)item).getUnderlyingValue();
                        AtomicSequence data = arrayItem.atomize();
                        return (XdmStream<XdmAtomicValue>)XdmValue.wrap(data).stream();
                    } catch (XPathException e) {
                        throw new SaxonApiUncheckedException(new SaxonApiException(e));
                    }
                } else {
                    throw new SaxonApiUncheckedException(new SaxonApiException("Cannot atomize supplied value"));
                }
            }
        };
    }

    /**
     * A step equivalent to the XPath "cast as" operator: the supplied item is atomized
     * if necessary, and the resulting atomic values are cast to the required type
     */

    public static Step<XdmAtomicValue> castAs(ItemType type) {
        if (!ItemType.ANY_ATOMIC_VALUE.subsumes(type)) {
            throw new IllegalArgumentException("Target of castAs must be an atomic type");
        }
        final net.sf.saxon.type.ItemType tType = type.getUnderlyingItemType().getPrimitiveItemType();
        final ConversionRules rules = type.getConversionRules();

        return atomize().then(new Step<XdmAtomicValue>() {
            @Override
            public Stream<? extends XdmAtomicValue> apply(XdmItem xdmItem) {
                try {
                    AtomicValue source = ((XdmAtomicValue)xdmItem).getUnderlyingValue();
                    Converter converter = rules.getConverter(source.getItemType(), (AtomicType)tType);
                    AtomicValue result = converter.convert(source).asAtomic();
                    return Stream.of((XdmAtomicValue) XdmValue.wrap(result));
                } catch (ValidationException e) {
                    throw new SaxonApiUncheckedException(new SaxonApiException(e));
                }
            }
        });
    }

    /**
     * A {@code Step} to navigate from a node to its ancestors, in reverse document
     * order (that is, nearest ancestor first, root node last)
     */

    private final static Step<XdmNode> ANCESTOR = new AxisStep(Axis.ANCESTOR);
    /**
     * A {@code Step} to navigate from a node to its ancestors, in reverse document
     * order, with the node itself returned at the start of the sequence (that is, origin node first, root node last)
     */

    private final static Step<XdmNode> ANCESTOR_OR_SELF = new AxisStep(Axis.ANCESTOR_OR_SELF);
    /**
     * A {@code Step} to navigate from a node to its attributes
     */

    private final static Step<XdmNode> ATTRIBUTE = new AxisStep(Axis.ATTRIBUTE);
    /**
     * A Step to navigate from a node to its children
     */

    private final static Step<XdmNode> CHILD = new AxisStep(Axis.CHILD);
    /**
     * A Step to navigate from a node to its descendants, which are returned in document order
     */

    private final static Step<XdmNode> DESCENDANT = new AxisStep(Axis.DESCENDANT);
    /**
     * A Step to navigate from a node to its descendants, which are returned in document order,
     * preceded by the origin node itself
     */

    private final static Step<XdmNode> DESCENDANT_OR_SELF = new AxisStep(Axis.DESCENDANT_OR_SELF);
    /**
     * A Step to navigate from a node to its following nodes (excluding descendants), which are returned in document order
     */

    private final static Step<XdmNode> FOLLOWING = new AxisStep(Axis.FOLLOWING);
    /**
     * A Step to navigate from a node to its following siblings, which are returned in document order
     */

    private final static Step<XdmNode> FOLLOWING_SIBLING = new AxisStep(Axis.FOLLOWING_SIBLING);
    /**
     * A {@code Step} to navigate from a node to its namespace nodes
     */

    private final static Step<XdmNode> NAMESPACE = new AxisStep(Axis.NAMESPACE);
    /**
     * A Step to navigate from a node to its parent
     */

    private final static Step<XdmNode> PARENT = new AxisStep(Axis.PARENT);
    /**
     * A Step to navigate from a node to its preceding siblings, which are returned in reverse document order
     */

    private final static Step<XdmNode> PRECEDING_SIBLING = new AxisStep(Axis.PRECEDING_SIBLING);
    /**
     * A Step to navigate from a node to its preceding nodes (excluding ancestors), which are returned in reverse document order
     */

    private final static Step<XdmNode> PRECEDING = new AxisStep(Axis.PRECEDING);
    /**
     * A Step to navigate from a node to itself (useful only if applying a predicate)
     */

    private final static Step<XdmNode> SELF = new AxisStep(Axis.SELF);

    /**
     * Obtain a Step that always returns an empty sequence, whatever the input
     * @return a Step that always returns an empty sequence
     */

    public static <U extends XdmItem> Step<U> nothing() {
        return new Step<U>() {
            @Override
            public Stream<U> apply(XdmItem xdmItem) {
                return Stream.empty();
            }
        };
    }

    private static Predicate<XdmNode> nodeTestPredicate (NodeTest test) {
        return item -> test.test(item.getUnderlyingNode());
    }

    private static Predicate<? super XdmNode> localNamePredicate(String given) {
        if ("*".equals(given)) {
            return isElement();
        } else {
            return item -> {
                NodeInfo node = item.getUnderlyingNode();
                return node.getNodeKind() == Type.ELEMENT
                        && node.getLocalPart().equals(given);
            };
        }
    }

    private static Predicate<? super XdmNode> expandedNamePredicate(String ns, String local) {
        return item -> {
            NodeInfo node = item.getUnderlyingNode();
            return node.getNodeKind() == Type.ELEMENT
                    && node.getLocalPart().equals(local) && node.getURI().equals(ns);
        };
    }

    /**
     * Obtain a {@code Step} to navigate from a node to its ancestors, in reverse document
     * order (that is, nearest ancestor first, root node last)
     * @return a Step that selects all nodes on the ancestor axis
     */
    public static Step<XdmNode> ancestor() {
        return ANCESTOR;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to its ancestor elements having a specified
     * local name, irrespective of the namespace. The nodes are returned in reverse document
     * order (that is, nearest ancestor first, root node last)
     *
     * @param localName the local name of the ancestors to be selected by the {@code Step},
     *                  or "*" to select all ancestors that are element nodes
     * @return a {@code Step}, which selects the ancestors of a supplied node that have the
     * required local name.
     */

    public static Step<XdmNode> ancestor(String localName) {
        return ancestor().where(localNamePredicate(localName));
    }

    /**
     * Return a {@code Step} that navigates from a node to its ancestors having a specified
     * namespace URI and local name, in reverse document order (that is, nearest ancestor first,
     * root node last)
     *
     * @param uri       the namespace URI of the ancestors to be selected by the {@code Step}
     * @param localName the local name of the ancestors to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @return a {@code Step}, which selects the ancestors (at most one) of a supplied node that have the
     * required local name and namespace URI.
     */

    public static Step<XdmNode> ancestor(String uri, String localName) {
        return ancestor().where(expandedNamePredicate(uri, localName));
    }

    /**
     * Obtain a {@code Step} that filters the nodes found on the ancestor axis using a supplied {@code Predicate}.
     * Nodes are returned in reverse document order (that is, nearest ancestor first, root node last)
     * <p>The function call {@code ancestor(predicate)} is equivalent to {@code ANCESTOR.where(predicate)}.</p>
     *
     * @param filter the predicate to be applied
     * @return a {@code Step} that filters the nodes found on the ancestor axis using a supplied {@code Predicate}.
     */

    public static Step<XdmNode> ancestor(Predicate<? super XdmNode> filter) {
        return ancestor().where(filter);
    }

    /**
     * Obtain a {@code Step} to navigate from a node to its ancestors, in reverse document
     * order, with the node itself returned at the start of the sequence (that is, origin node first,
     * root node last)
     * @return a Step that selects all nodes on the ancestor-or-self axis
     */
    public static Step<XdmNode> ancestorOrSelf() {
        return ANCESTOR_OR_SELF;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to its ancestor elements having a specified
     * local name, irrespective of the namespace. The nodes are returned in reverse document
     * order (that is, nearest ancestor first, root node last), and include the node itself
     *
     * @param localName the local name of the ancestors to be selected by the {@code Step},
     *                  or "*" to select all ancestor-or-self nodes that are element nodes
     * @return a {@code Step}, which selects the ancestors-or-self of a supplied node that have the
     * required local name.
     */

    public static Step<XdmNode> ancestorOrSelf(String localName) {
        return ancestorOrSelf().where(localNamePredicate(localName));
    }

    /**
     * Obtain a {@code Step} that navigates from a node to its ancestors-or-self having a specified
     * namespace URI and local name, in reverse document order (that is, nearest ancestor first,
     * root node last)
     *
     * @param uri       the namespace URI of the ancestors to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the ancestors to be selected by the {@code Step}
     * @return a {@code Step}, which selects the ancestors-or-self of a supplied node that have the
     * required local name and namespace URI.
     */

    public static Step<XdmNode> ancestorOrSelf(String uri, String localName) {
        return ancestorOrSelf().where(expandedNamePredicate(uri, localName));
    }

    /**
     * Obtain a {@code Step} that filters the nodes found on the ancestor-or-self axis using a supplied {@code Predicate}.
     * Nodes are returned in reverse document order (that is, origin node first,
     * root node last)
     * <p>The function call {@code ancestorOrSelf(predicate)} is equivalent to {@code ANCESTOR_OR_SELF.where(predicate)}.</p>
     *
     * @param filter the predicate to be applied
     * @return a {@code Step} that filters the nodes found on the ancestor-or-self axis using a supplied {@code Predicate}.
     */

    public static Step<XdmNode> ancestorOrSelf(Predicate<? super XdmNode> filter) {
        return ancestorOrSelf().where(filter);
    }

    /**
     * Obtain a {@code Step} to navigate from a node to its attributes
     * @return a Step that selects all nodes on the ancestor axis
     */
    public static Step<XdmNode> attribute() {
        return ATTRIBUTE;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to its attributes having a specified
     * local name, irrespective of the namespace
     *
     * @param localName the local name of the attributes to be selected by the {@code Step}, or
     *                  "*" to select all attributes
     * @return a {@code Step}, which selects the attributes of a supplied node that have the
     * required local name.
     */

    public static Step<XdmNode> attribute(String localName) {
        return "*".equals(localName) ? attribute() : attribute().where(hasLocalName(localName));
    }

    /**
     * Return a {@code Step} that navigates from a node to its attribute having a specified
     * namespace URI and local name
     *
     * @param uri       the namespace URI of the attributes to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the attributes to be selected by the {@code Step}
     * @return a {@code Step}, which selects the attributes (at most one) of a supplied node that have the
     * required local name and namespace URI.
     */

    public static Step<XdmNode> attribute(String uri, String localName) {
        return attribute().where(hasName(uri, localName));
    }

    /**
     * Obtain a {@code Step} that filters the nodes found on the attribute axis using a supplied {@code Predicate}.
     * The function call {@code attribute(predicate)} is equivalent to {@code ATTRIBUTE.where(predicate)}.
     *
     * @param filter the predicate to be applied
     * @return a {@code Step} that filters the nodes found on the attribute axis using a supplied {@code Predicate}.
     */

    public static Step<XdmNode> attribute(Predicate<? super XdmNode> filter) {
        return attribute().where(filter);
    }

    /**
     * Obtain a {@link Step} to navigate from a node to its children
     * @return a Step that selects all nodes on the child axis
     */
    public static Step<XdmNode> child() {
        return CHILD;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the element children having a specified
     * local name, irrespective of the namespace
     * @param localName the local name of the child elements to be selected by the Step,
     *                  or "*" to select all children that are element nodes
     * @return a {@code Step}, which selects the element children of a supplied node that have the
     * required local name.
     */

    public static Step<XdmNode> child(String localName) {
        return child().where(localNamePredicate(localName));
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the element children having a specified
     * namespace URI and local name
     * @param uri the namespace URI of the child elements to be selected by the {@code Step}:
     *            supply a zero-length string to indicate the null namespace
     * @param localName the local name of the child elements to be selected by the {@code Step}
     * @return a {@code Step}, which selects the element children of a supplied node that have the
     * required local name and namespace URI.
     */

    public static Step<XdmNode> child(String uri, String localName) {
        return child().where(expandedNamePredicate(uri, localName));
    }

    /**
     * Obtain a {@code Step} that filters the nodes found on the child axis using a supplied {@code Predicate}.
     * The function call {@code child(predicate)} is equivalent to {@code CHILD.where(predicate)}.
     * For example, {@code child(isElement())} returns a Step that selects the element node children
     * of a given node.
     * @param filter the predicate to be applied
     * @return a Step that filters the nodes found on the child axis using a supplied {@code Predicate}.
     */

    public static Step<XdmNode> child(Predicate<? super XdmNode> filter) {
        return child().where(filter);
    }

    /**
     * Obtain a {@link Step} to navigate from a node to its descendants, which are returned in document order
     * @return a Step that selects all nodes on the descendant axis
     */
    public static Step<XdmNode> descendant() {
        return DESCENDANT;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the descendant elements having a specified
     * local name, irrespective of the namespace. These are returned in document order.
     *
     * @param localName the local name of the descendant elements to be selected by the {@code Step},
     *                  or "*" to select all descendants that are element nodes
     * @return a {@code Step}, which selects the element descendants of a supplied node that have the
     * required local name.
     */

    public static Step<XdmNode> descendant(String localName) {
        return descendant().where(localNamePredicate(localName));
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the element descendants having a specified
     * namespace URI and local name. These are returned in document order.
     *
     * @param uri       the namespace URI of the descendant elements to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the descendant elements to be selected by the {@code Step}
     * @return a {@code Step}, which selects the element descendants of a supplied node that have the
     * required local name and namespace URI.
     */

    public static Step<XdmNode> descendant(String uri, String localName) {
        return descendant().where(expandedNamePredicate(uri, localName));
    }

    /**
     * Obtain a {@link Step} to navigate from a node to its descendants, which are returned in document order,
     * preceded by the origin node itself
     * @return a Step that selects all nodes on the descendant-or-self axis
     */
    public static Step<XdmNode> descendantOrSelf() {
        return DESCENDANT_OR_SELF;
    }

    /**
     * Obtain a Step that filters the nodes found on the descendant axis using a supplied {@code Predicate}.
     * The function call {@code descendant(predicate)} is equivalent to {@code DESCENDANT.where(predicate)}.
     * For example, {@code descendant(isElement())} returns a Step that selects the element node descendants
     * of a given node, while {@code descendant(exists(attribute("id")))} selects those that have an attribute
     * named "id". These are returned in document order.
     *
     * @param filter the predicate to be applied
     * @return a Step that filters the nodes found on the descendant axis using a supplied Predicate.
     */

    public static Step<XdmNode> descendant(Predicate<? super XdmNode> filter) {
        return descendant().where(filter);
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the descendant-or-self elements having a specified
     * local name, irrespective of the namespace. These are returned in document order, preceded by the origin node
     * itself if it matches the conditions.
     *
     * @param localName the local name of the descendant-or-self elements to be selected by the {@code Step},
     *                  or "*" to select all descendant-or-self nodes that are element nodes
     * @return a {@code Step}, which selects the element children of a supplied node that have the
     * required local name.
     */

    public static Step<XdmNode> descendantOrSelf(String localName) {
        return descendantOrSelf().where(localNamePredicate(localName));
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the descendant-or-self elements having a specified
     * namespace URI and local name. These are returned in document order, preceded by the origin node
     * itself if it matches the conditions.
     *
     * @param uri       the namespace URI of the descendant-or-self elements to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the descendant-or-self elements to be selected by the {@code Step}
     * @return a {@code Step}, which selects the element descendants-or-self of a supplied node that have a
     * given local name and namespace URI.
     */

    public static Step<XdmNode> descendantOrSelf(String uri, String localName) {
        return descendantOrSelf().where(expandedNamePredicate(uri, localName));
    }

    /**
     * Obtain a Step that filters the nodes found on the descendant-or-self axis using a supplied {@code Predicate}.
     * The function call {@code descendant(predicate)} is equivalent to {@code DESCENDANT.where(predicate)}.
     * For example, {@code descendant(isElement())} returns a Step that selects the element node descendants
     * of a given node, while {@code descendant(exists(attribute("id")))} selects those that have an attribute
     * named "id". These are returned in document order.
     *
     * @param filter the predicate to be applied
     * @return a Step that filters the nodes found on the descendant-or-self axis using a supplied Predicate.
     */

    public static Step<XdmNode> descendantOrSelf(Predicate<? super XdmNode> filter) {
        return descendantOrSelf().where(filter);
    }

    /**
     * Obtain a {@link Step} to navigate from a node to its following nodes
     * (excluding descendants), which are returned in document order
     * @return a Step that selects all nodes on the following axis
     */
    public static Step<XdmNode> following() {
        return FOLLOWING;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the following elements having a specified
     * local name, irrespective of the namespace. These are returned in document order.
     *
     * @param localName the local name of the following elements to be selected by the {@code Step},
     *                  or "*" to select all following nodes that are elements
     * @return a {@code Step}, which selects the following elements of a supplied node that have the
     * required local name.
     */

    public static Step<XdmNode> following(String localName) {
        return following().where(localNamePredicate(localName));
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the following elements having a specified
     * namespace URI and local name. These are returned in document order.
     *
     * @param uri       the namespace URI of the following elements to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the following elements to be selected by the {@code Step}
     * @return a {@code Step}, which selects the following elements of a supplied node that have the
     * required local name and namespace URI.
     */

    public static Step<XdmNode> following(String uri, String localName) {
        return following().where(expandedNamePredicate(uri, localName));
    }

    /**
     * Obtain a Step that filters the nodes found on the following axis using a supplied {@code Predicate}.
     * The function call {@code followingSibling(predicate)} is equivalent to {@code FOLLOWING_SIBLING.where(predicate)}.
     * For example, {@code followingSibling(isElement())} returns a {@code Step} that selects the following sibling elements
     * of a given node, while {@code followingSibling(exists(attribute("id")))} selects those that have an attribute
     * named "id". These are returned in document order.
     *
     * @param filter the predicate to be applied
     * @return a {@code Step} that filters the nodes found on the following axis using a supplied {@code Predicate}.
     */

    public static Step<XdmNode> following(Predicate<? super XdmNode> filter) {
        return following().where(filter);
    }

    /**
     * Obtain a {@link Step} to navigate from a node to its following siblings, which are returned
     * in document order
     * @return a Step that selects all nodes on the following-sibling axis
     */
    public static Step<XdmNode> followingSibling() {
        return FOLLOWING_SIBLING;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the following sibling elements having a specified
     * local name, irrespective of the namespace. These are returned in document order.
     *
     * @param localName the local name of the following sibling elements to be selected by the {@code Step},
     *                  or "*" to select all following siblings that are element nodes
     * @return a {@code Step}, which selects the following sibling elements of a supplied node that have the
     * required local name.
     */

    public static Step<XdmNode> followingSibling(String localName) {
        return followingSibling().where(localNamePredicate(localName));
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the following sibling elements having a specified
     * namespace URI and local name. These are returned in document order.
     *
     * @param uri       the namespace URI of the following sibling elements to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the following sibling elements to be selected by the {@code Step}
     * @return a {@code Step}, which selects the following sibling elements of a supplied node that have the
     * required local name and namespace URI.
     */

    public static Step<XdmNode> followingSibling(String uri, String localName) {
        return followingSibling().where(expandedNamePredicate(uri, localName));
    }

    /**
     * Obtain a Step that filters the nodes found on the following sibling axis using a supplied {@code Predicate}.
     * The function call {@code followingSibling(predicate)} is equivalent to {@code FOLLOWING_SIBLING.where(predicate)}.
     * For example, {@code followingSibling(isElement())} returns a {@code Step} that selects the following sibling elements
     * of a given node, while {@code followingSibling(exists(attribute("id")))} selects those that have an attribute
     * named "id". These are returned in document order.
     *
     * @param filter the predicate to be applied
     * @return a {@code Step} that filters the nodes found on the following sibling axis using a supplied {@code Predicate}.
     */

    public static Step<XdmNode> followingSibling(Predicate<? super XdmNode> filter) {
        return followingSibling().where(filter);
    }

    /**
     * Obtain a {@link Step} to navigate from a node to its namespace nodes
     * @return a Step that selects all nodes on the namespace axis
     */
    public static Step<XdmNode> namespace() {
        return NAMESPACE;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to its namespaces having a specified
     * local name. The local name of a namespace node corresponds to the prefix used in the
     * namespace binding.
     *
     * @param localName the local name (representing the namespace prefix) of the namespace nodes
     *                  to be selected by the {@code Step}, or "*" to select all namespaces
     * @return a {@code Step}, which selects the namespaces of a supplied node that have a
     * given local name (prefix).
     */

    public static Step<XdmNode> namespace(String localName) {
        return "*".equals(localName) ? namespace() : namespace().where(hasLocalName(localName));
    }

    /**
     * Obtain a {@code Step} that filters the nodes found on the namespace axis using a supplied {@code Predicate}.
     * The function call {@code namespace(predicate)} is equivalent to {@code namespace().where(predicate)}.
     * For example, {@code namespace(eq("http://www.w3.org/1999/XSL/Transform")} selects a namespace node
     * that binds a prefix to the XSLT namespace.
     *
     * @param filter the predicate to be applied
     * @return a {@code Step} that filters the nodes found on the namespace axis using a supplied
     * {@code Predicate}.
     */

    public static Step<XdmNode> namespace(Predicate<? super XdmNode> filter) {
        return namespace().where(filter);
    }

    /**
     * Obtain a {@link Step} to navigate from a node to its parent
     * @return a Step that selects all nodes on the parent axis (of which there is at most one)
     */
    public static Step<XdmNode> parent() {
        return PARENT;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the parent element provided it has a specified
     * local name, irrespective of the namespace
     *
     * @param localName the local name of the parent element to be selected by the Step,
     *                  or "*" to select the parent node provided it is an element
     * @return a {@code Step}, which selects the parent of a supplied node provided it is an element with the
     * required local name.
     */

    public static Step<XdmNode> parent(String localName) {
        return parent().where(isElement()).where(localNamePredicate(localName));
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the parent element provided it has a specified
     * namespace URI and local name
     *
     * @param uri       the namespace URI of the parent element to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the parent element to be selected by the {@code Step}
     * @return a {@code Step}, which selects the parent element of a supplied node provided it is an
     * element with the required local name and namespace URI.
     */

    public static Step<XdmNode> parent(String uri, String localName) {
        return parent().where(expandedNamePredicate(uri, localName));
    }

    /**
     * Obtain a {@code Step} that filters the node found on the parent axis using a supplied {@code Predicate}.
     * The function call {@code parent(predicate)} is equivalent to {@code parent().where(predicate)}.
     * For example, {@code parent(isElement())} returns a Step that selects the parent node provided
     * it is an element
     *
     * @param filter the predicate to be applied
     * @return a Step that filters the nodes found on the parent axis using a supplied {@code Predicate}.
     */

    public static Step<XdmNode> parent(Predicate<? super XdmNode> filter) {
        return parent().where(filter);
    }

    /**
     * Obtain a {@link Step} to navigate from a node to its preceding siblings, which are returned
     * in reverse document order
     * @return a Step that selects all nodes on the preceding-sibling axis
     */
    public static Step<XdmNode> precedingSibling() {
        return PRECEDING_SIBLING;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the preceding sibling elements having a specified
     * local name, irrespective of the namespace. These are returned in reverse document order.
     *
     * @param localName the local name of the preceding sibling elements to be selected by the {@code Step},
     *                  or "*" to select all descendants that are element nodes
     * @return a {@code Step}, which selects the preceding sibling elements of a supplied node that have the
     * required local name.
     */

    public static Step<XdmNode> precedingSibling(String localName) {
        return precedingSibling().where(localNamePredicate(localName));
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the preceding sibling elements having a specified
     * namespace URI and local name. These are returned in reverse document order.
     *
     * @param uri       the namespace URI of the preceding sibling elements to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the preceding sibling elements to be selected by the {@code Step}
     * @return a {@code Step}, which selects the preceding sibling elements of a supplied node that have the
     * required local name and namespace URI.
     */

    public static Step<XdmNode> precedingSibling(String uri, String localName) {
        return precedingSibling().where(expandedNamePredicate(uri, localName));
    }

    /**
     * Obtain a Step that filters the nodes found on the preceding sibling axis using a supplied {@code Predicate}.
     * The function call {@code precedingSibling(predicate)} is equivalent to {@code precedingSibling().where(predicate)}.
     * For example, {@code precedingSibling(isElement())} returns a {@code Step} that selects the preceding sibling elements
     * of a given node, while {@code precedingSibling(exists(attribute("id")))} selects those that have an attribute
     * named "id". These are returned in reverse document order.
     *
     * @param filter the predicate to be applied
     * @return a {@code Step} that filters the nodes found on the following sibling axis using a supplied {@code Predicate}.
     */

    public static Step<XdmNode> precedingSibling(Predicate<? super XdmNode> filter) {
        return precedingSibling().where(filter);
    }

    /**
     * Obtain a {@link Step} to navigate from a node to its preceding nodes (excluding ancestors),
     * which are returned in reverse document order
     * @return a Step that selects all nodes on the preceding axis
     */
    public static Step<XdmNode> preceding() {
        return PRECEDING;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the preceding elements having a specified
     * local name. These are returned in reverse document order.
     *
     * @param localName the local name of the preceding elements to be selected by the {@code Step},
     *                  or "*" to select all descendants that are element nodes
     * @return a {@code Step}, which selects the preceding elements of a supplied node that have the
     * required local name.
     */

    public static Step<XdmNode> preceding(String localName) {
        return preceding().where(localNamePredicate(localName));
    }

    /**
     * Obtain a {@code Step} that navigates from a node to the preceding elements having a specified
     * namespace URI and local name. These are returned in reverse document order.
     *
     * @param uri       the namespace URI of the preceding elements to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the preceding sibling elements to be selected by the {@code Step}
     * @return a {@code Step}, which selects the preceding sibling elements of a supplied node that have the
     * required local name and namespace URI.
     */

    public static Step<XdmNode> preceding(String uri, String localName) {
        return preceding().where(expandedNamePredicate(uri, localName));
    }

    /**
     * Obtain a Step that filters the nodes found on the preceding axis using a supplied {@code Predicate}.
     * The function call {@code preceding(predicate)} is equivalent to {@code PRECEDING.where(predicate)}.
     * For example, {@code preceding(isElement())} returns a {@code Step} that selects the preceding elements
     * of a given node, while {@code preceding(exists(attribute("id")))} selects those that have an attribute
     * named "id". These are returned in reverse document order.
     *
     * @param filter the predicate to be applied
     * @return a {@code Step} that filters the nodes found on the following sibling axis using a supplied {@code Predicate}.
     */

    public static Step<XdmNode> preceding(Predicate<? super XdmNode> filter) {
        return preceding().where(filter);
    }

    /**
     * Obtain a {@link Step} to navigate from a node to itself (useful only if applying a predicate)
     * @return a Step that selects all nodes on the self axis (that is, the node itself)
     */
    public static Step<XdmNode> self() {
        return SELF;
    }

    /**
     * Obtain a {@code Step} that navigates from a node to itself provided it is an element with a specified
     * local name, irrespective of the namespace
     *
     * @param localName the local name of the element to be selected by the Step,
     *                  or "*" to select the node provided that it is an element node
     * @return a {@code Step}, which selects the supplied node provided it has a
     * given local name.
     */

    public static Step<XdmNode> self(String localName) {
        return self().where(localNamePredicate(localName));
    }

    /**
     * Obtain a {@code Step} that navigates from a node to itself provided it has a specified
     * namespace URI and local name
     *
     * @param uri       the namespace URI of the element to be selected by the {@code Step}:
     *                  supply a zero-length string to indicate the null namespace
     * @param localName the local name of the element to be selected by the {@code Step}
     * @return a {@code Step}, which selects the supplied node provided it is an element with a
     * given local name and namespace URI.
     */

    public static Step<XdmNode> self(String uri, String localName) {
        return self().where(expandedNamePredicate(uri, localName));
    }

    /**
     * Obtain a {@code Step} that filters the node found on the self axis using a supplied {@code Predicate}.
     * The function call {@code self(predicate)} is equivalent to {@code SELF.where(predicate)}.
     * For example, {@code self(isElement())} returns a Step that selects the supplied node provided
     * it is an element
     *
     * @param filter the predicate to be applied
     * @return a Step that filters the nodes found on the parent axis using a supplied {@code Predicate}.
     */

    public static Step<XdmNode> self(Predicate<? super XdmNode> filter) {
        return self().where(filter);
    }

    /**
     * Obtain a {@code Step} that returns text nodes found on the child axis.
     * The function call {@code text()} is equivalent to {@code child().where(isText())}.
     * For example, {@code self(isElement())} returns a Step that selects the supplied node provided
     * it is an element
     * @return a Step that returns the text nodes found on the child axis.
     */

    public static Step<XdmNode> text() {
        return child().where(isText());
    }

    /**
     * Construct a path as a composite {@link Step} from a sequence of steps composed together
     * @param steps the constituent steps in the path
     * @return a composite step
     */

    @SafeVarargs
    public static Step<? extends XdmNode> path(Step<? extends XdmNode>... steps) {
        return pathFromList(Arrays.asList(steps));
    }

    private static Step<? extends XdmNode> pathFromList(List<Step<? extends XdmNode>> steps) {
        if (steps.isEmpty()) {
            return nothing();
        } else if (steps.size() == 1) {
            return steps.get(0);
        } else {
            return steps.get(0).then(pathFromList(steps.subList(1, steps.size())));
        }
    }

    /**
     * Construct a simple path consisting solely of simple child, attribute, descendant, root,
     * and parent steps. For example, {@code path("div3", "head", "@style")} selects
     * the same nodes as the XPath 2.0 expression {@code child::*:div3/child::*:head/attribute::*:style}.
     * @param steps a sequence of strings. Each string must be one of the following:
     *              <ul>
     *              <li>A plain NCName (for example "item") selects child nodes by matching
     *              local-name (the namespace is ignored)</li>
     *              <li>An NCName preceded by "@" (for example, "@code"), selects attribute nodes
     *              by matching local-name (again, ignoring any namespace)</li>
     *              <li>The string "*" selects all child elements, regardless of name</li>
     *              <li>The string "/" selects the root node of the tree, provided it
     *              is a document node</li>
     *              <li>The string ".." selects the parent node</li>
     *              <li>The string "//" selects all descendant-or-self nodes (note,
     *              this does not involve finding the root of the tree: it correspondings to a binary
     *              '//' operator in XPath, not to an initial '//')</li>
     *              </ul>.
     *              <p>For more complex paths, see {@link #path(Step...)}</p>
     * @throws IllegalArgumentException if any of the strings is invalid according
     *              to these rules.
     */

    public static Step<? extends XdmNode> path(String... steps) {
        List<Step<? extends XdmNode>> pathSteps = new ArrayList<>();
        for (String step : steps) {
            if (step.equals("/")) {
                pathSteps.add(root().where(isDocument()));
            } else if (step.equals("..")) {
                pathSteps.add(parent());
            } else if (step.equals("*")) {
                pathSteps.add(child(isElement()));
            } else if (step.equals("//")) {
                pathSteps.add(descendantOrSelf());
            } else if (step.startsWith("@")) {
                String name = step.substring(1);
                if (!NameChecker.isValidNCName(name)) {
                    throw new IllegalArgumentException("Invalid attribute name " + name);
                }
                pathSteps.add(attribute(name));
            } else {
                if (!NameChecker.isValidNCName(step)) {
                    throw new IllegalArgumentException("Invalid element name " + step);
                }
                pathSteps.add(child(step));
            }
        }
        return pathFromList(pathSteps);
    }

    /**
     * Obtain a Step whose effect is to tokenize the supplied item on whitespace
     * boundaries, returning a sequence of strings as {@code XdmAtomicValue} instances.
     *
     * <p>Note: the tokenize step, when applied to a string with leading and trailing whitespace,
     * has the effect of removing this whitespace. In addition to its primary role, the function
     * can therefore be useful for trimming the content of a single string. </p>
     *
     * <p>Usage example: {@code child().where(some(attribute("id").then(tokenize())).eq("a123"))}
     * selects child elements that have an attribute named "id" whose value contains the token
     * "a123".</p>
     *
     * @return a Step whose effect is to take a supplied item and split its string
     * value into a sequence of xs:string instances
     */

    public static Step<XdmAtomicValue> tokenize() {
        return new Step<XdmAtomicValue>() {
            @Override
            public Stream<XdmAtomicValue> apply(XdmItem item) {
                AtomicIterator<StringValue> iter = new Whitespace.Tokenizer(item.getStringValue());
                return XdmSequenceIterator.ofAtomicValues(iter).stream();
            }
        };
    }

    /**
     * Obtain a Step whose effect is to interpret the supplied item as an xs:ID value
     * and return the nodes (in a given document) that have that string as their ID.
     * @param doc the root node (document node) of the document within which the ID
     *            value should be sought
     * @return a Step whose effect is to take a supplied item and split its string
     * value into a sequence of xs:string instances
     */

    public static Step<XdmNode> id(XdmNode doc) {
        return new Step<XdmNode>() {
            @Override
            public Stream<XdmNode> apply(XdmItem item) {
                if (doc.getNodeKind() != XdmNodeKind.DOCUMENT) {
                    throw new IllegalArgumentException("id() - argument is not a document node");
                }
                NodeInfo target = doc.getUnderlyingNode().getTreeInfo().selectID(item.getStringValue(), true);
                return target==null
                        ? Stream.empty()
                        : Stream.of((XdmNode)XdmNode.wrap(target));
            }
        };
    }


}

// Copyright (c) 2018-2020 Saxonica Limited

