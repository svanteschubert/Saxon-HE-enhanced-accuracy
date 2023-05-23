////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api.streams;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.s9api.*;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * This non-instantiable class provides a number of useful implementations of the {@link Predicate}
 * interface, designed for use when navigating streams of XDM items.
 */

public class Predicates {
    /**
     * A predicate to test whether an item is a node
     * @return a predicate that returns true if given an item that is a node
     */

    public static Predicate<XdmItem> isNode() {
        return item -> item instanceof XdmNode;
    }

    /**
     * A predicate to test whether an item is an element node
     * @return a predicate that returns true if given an item that is an element node
     */

    public static Predicate<XdmItem> isElement() {
        return nodeKindPredicate(XdmNodeKind.ELEMENT);
    }

    /**
     * A predicate to test whether an item is an attribute node
     * @return a predicate that returns true if given an item that is an attribute node
     */

    public static Predicate<XdmItem> isAttribute() {
        return nodeKindPredicate(XdmNodeKind.ATTRIBUTE);
    }
    /**
     * A predicate to test whether an item is a text node
     * @return a predicate that returns true if given an item that is a text node
     */

    public static Predicate<XdmItem> isText() {
        return nodeKindPredicate(XdmNodeKind.TEXT);
    }

    /**
     * A predicate to test whether an item is a comment node
     * @return a predicate that returns true if given an item that is a comment node
     */

    public static Predicate<XdmItem> isComment() {
        return nodeKindPredicate(XdmNodeKind.COMMENT);
    }

    /**
     * A predicate to test whether an item is a processing instruction node
     * @return a predicate that returns true if given an item that is a processing instruction node
     */

    public static Predicate<XdmItem> isProcessingInstruction() {
        return nodeKindPredicate(XdmNodeKind.PROCESSING_INSTRUCTION);
    }

    /**
     * A predicate to test whether an item is a document node
     * @return a predicate that returns true if given an item that is a document node
     */

    public static Predicate<XdmItem> isDocument() {
        return nodeKindPredicate(XdmNodeKind.DOCUMENT);
    }

    /**
     * A predicate to test whether an item is a namespace node
     * @return a predicate that returns true if given an item that is a namespace node
     */

    public static Predicate<XdmItem> isNamespace() {
        return nodeKindPredicate(XdmNodeKind.NAMESPACE);
    }

    /**
     * A predicate to test whether an item is an atomic value
     * <p>Note: to test for a specific type of atomic value, use a predicate such as
     * {@code hasType(ItemType.XS_INTEGER)}</p>
     * @return a predicate that returns true if given an item that is an atomic value.
     */

    public static Predicate<XdmItem> isAtomic() {
        return item -> item instanceof XdmAtomicValue;
    }

    /**
     * A predicate to test whether an item is a function value (this includes maps and arrays)
     * @return a predicate that returns true if given an item that is a function, including
     * maps and arrays
     */

    public static Predicate<XdmItem> isFunction() {
        return item -> item instanceof XdmFunctionItem;
    }

    /**
     * A predicate to test whether an item is an XDM map
     * @return a predicate that returns true if given an item that is a map
     */

    public static Predicate<XdmItem> isMap() {
        return item -> item instanceof XdmMap;
    }

    /**
     * A predicate to test whether an item is an XDM array
     * @return a predicate that returns true if given an item that is an array
     */

    public static Predicate<XdmItem> isArray() {
        return item -> item instanceof XdmArray;
    }

    /**
     * Obtain a predicate that tests whether a supplied {@link Step} delivers an empty result
     *
     * @param step a step to be applied to the item being tested
     * @return a predicate that returns true if the supplied step returns an empty result.
     * For example {@code empty(attribute("id")} is a predicate that returns true for a node that
     * has no "id" attribute. Similarly {@code CHILD.where(empty(child(IS_ELEMENT)))} is a step
     * that selects child elements having no element children.
     */

    public static <T extends XdmItem> Predicate<XdmItem> empty(Step<T> step) {
        return item -> !step.apply(item).findFirst().isPresent();
    }

    /**
     * Return a {@link Predicate} that is the negation of a supplied {@link Predicate}
     *
     * @param condition the supplied predicate
     * @param <T> The type of object to which the predicate is applicable
     * @return a Predicate that matches an item if and only if the supplied Predicate does
     * not match the item.
     */

    public static <T> Predicate<T> not(Predicate<T> condition) {
        return condition.negate();
    }

    /**
     * Obtain a predicate that tests whether a supplied Step delivers a non-empty result
     *
     * @param step a step to be applied to the item being tested
     * @return a predicate that returns true if the
     * step returns a non-empty result. For example {@code exists(attribute("id")} is a
     * predicate that returns true for a node that has an "id" attribute. So
     * {@code CHILD.where(exists(attribute("id"))} is a step that selects child elements
     * having an "id" attribute.
     */

    public static <T extends XdmItem> Predicate<XdmItem> exists(Step<T> step) {
        return item -> step.apply(item).findFirst().isPresent();
    }

    /**
     * Obtain a predicate that tests whether an item is a node with a given namespace URI and local name
     *
     * @param uri       the required namespace URI: supply a zero-length string to indicate the null namespace
     * @param localName the required local name
     * @return a predicate that returns true if and only if the supplied item is a node with the given
     * namespace URI and local name
     */

    public static Predicate<? super XdmNode> hasName(String uri, String localName) {
        return item -> {
            QName name = item.getNodeName();
            return name != null &&
                    name.getLocalName().equals(localName) &&
                    name.getNamespaceURI().equals(uri);
        };
    }

    /**
     * Obtain a predicate that tests whether an item is a node with a given local name,
     * irrespective of the namespace
     *
     * @param localName the required local name. If a zero-length string is supplied, the predicate
     *                  will match nodes having no name (for example, comments and text nodes)
     * @return a predicate that returns true if and only if the supplied item is a node with the given
     * local name, irrespective of the namespace
     */

    public static Predicate<XdmNode> hasLocalName(String localName) {
        Objects.requireNonNull(localName);
        if (localName.isEmpty()) {
            return item -> item.getNodeName() == null;
        }
        return item -> {
            QName name = item.getNodeName();
            return name != null &&
                    name.getLocalName().equals(localName);
        };
    }

    /**
     * Obtain a predicate that tests whether an item is a node with a given namespace URI
     *
     * @param uri the required namespace URI: supply a zero-length string to identify the null namespace
     * @return a predicate that returns true if and only if the supplied item is a node with the given
     * namespace URI. If a zero-length string is supplied, the predicate will also match nodes having no name,
     * such as text and comment nodes, and nodes having a local name only, such as namespace and processing-instruction
     * nodes.
     */

    public static Predicate<XdmNode> hasNamespace(String uri) {
        return item -> {
            QName name = item.getNodeName();
            return name != null &&
                    name.getNamespaceURI().equals(uri);
        };
    }

    /**
     * Obtain a predicate that tests whether an item is an element node with a given attribute (whose
     * name is in no namespace)
     *
     * @param local the required attribute name
     * @return a predicate that returns true if and only if the supplied item is an element having an attribute
     * with the given local name, in no namespace
     */

    public static Predicate<XdmNode> hasAttribute(String local) {
        return item -> item.attribute(local) != null;
    }

    /**
     * Obtain a predicate that tests whether an item is an element node with a given attribute (whose
     * name is in no namespace) whose string value is equal to a given value
     *
     * @param local the required attribute name
     * @param value the required attribute value
     * @return a predicate that returns true if and only if the supplied item is an element having an attribute
     * with the given local name, in no namespace, whose string value is equal to the given value
     */

    public static Predicate<XdmNode> attributeEq(String local, String value) {
        return item -> value.equals(item.attribute(local));
    }

    /**
     * Obtain a predicate that tests whether an item matches a given item type
     *
     * @param type the required item type
     * @return a predicate that returns true if and only if the supplied item matches the supplied
     * item type. For example, {@code hasType(ItemType.DATE_TIME)} matches atomic values of type
     * <code>xs:dateTime</code>.
     */

    public static Predicate<XdmItem> hasType(ItemType type) {
        return type::matches;
    }

    /**
     * Obtain a predicate that tests whether there is some item in the result of applying a step that
     * satisfies the supplied condition.
     * <p>For example, {@code some(CHILD, exists(attribute("foo"))} matches an element if it has a child
     * element with an attribute whose local name is "foo".</p>
     * <p>If the step returns an empty sequence the result will always be false.</p>
     *
     * @param step      the step to be evaluated
     * @param condition the predicate to be applied to the items returned by the step
     */

    public static <T extends XdmItem> Predicate<XdmItem> some(Step<T> step, Predicate<? super T> condition) {
        return item -> step.apply(item).anyMatch(condition);
    }

    /**
     * Obtain a predicate that tests whether every item in the result of applying a step
     * satisfies the supplied condition.
     * <p>For example, {@code every(CHILD, exists(attribute("foo"))} matches an element if each of its child
     * elements has an attribute whose local name is "foo".</p>
     * <p>If the step returns an empty sequence the result will always be true.</p>
     *
     * @param step      the step to be evaluated
     * @param condition the predicate to be applied to the items returned by the step
     */

    public static <T extends XdmItem> Predicate<XdmItem> every(Step<T> step, Predicate<? super XdmItem> condition) {
        return item -> step.apply(item).allMatch(condition);
    }

    /**
     * Obtain a predicate that tests whether an atomic value compares equal to a supplied atomic value of
     * a comparable type
     *
     * @param value the atomic value to be compared with
     * @return a Predicate which returns true when applied to a value that is equal to the supplied
     * value under the "is-same-key" comparison rules. (These are the rules used to compare key values
     * in an XDM map. The rules are chosen to be context-free, error-free, and transitive.)
     */

    public static Predicate<XdmAtomicValue> eq(XdmAtomicValue value) {
        AtomicMatchKey k2 = value.getUnderlyingValue().asMapKey();
        return item -> item.getUnderlyingValue().asMapKey().equals(k2);
    }

    /**
     * Obtain a predicate that tests whether the result of applying the XPath string() function to an item
     * is equal to a given string
     *
     * @param value the string being tested
     * @return a Predicate which returns true if the string value of the item being tested
     * is equal to the given string under Java comparison rules for comparing strings.
     */

    public static Predicate<XdmItem> eq(String value) {
        return item -> item.getStringValue().equals(value);
    }

    /**
     * Obtain a predicate that tests whether the result of applying the XPath string() function to an item
     * matches a given regular expression
     *
     * @param regex the regular expression (this is a Java regular expression, not an XPath regular expression)
     * @return a Predicate which returns true if the string value of the item being tested
     * contains a substring that matches the given regular expression. To test the string in its entirety,
     * use anchors "^" and "$" in the regular expression.
     */

    public static Predicate<XdmItem> matchesRegex(String regex) {
        Pattern re = Pattern.compile(regex);
        return item -> re.matcher(item.getStringValue()).find();
    }

    /**
     * Obtain a predicate that tests whether there is some item in the result of applying a step,
     * whose string value is equal to a given string.
     * <p>For example, {@code eq(attribute("id"), "foo")} matches an element if it has an "id"
     * attribute whose value is "foo".</p>
     *
     * @param step  the step to be evaluated
     * @param value the string to be compared against the items returned by the step
     * @return a Predicate which returns true if some item selected by the step has as string value
     * equal to the given string
     */

    public static <T extends XdmItem> Predicate<XdmItem> eq(Step<T> step, String value) {
        return some(step, eq(value));
    }



    private static Predicate<XdmItem> nodeKindPredicate(XdmNodeKind kind) {
        return item -> item instanceof XdmNode && ((XdmNode) item).getNodeKind() == kind;
    }


}

