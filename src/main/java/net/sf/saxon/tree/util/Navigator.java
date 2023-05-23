////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.Controller;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.*;
import net.sf.saxon.tree.tiny.TinyElementImpl;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import net.sf.saxon.tree.tiny.TinyTextualElement;
import net.sf.saxon.tree.wrapper.SiblingCountingNode;
import net.sf.saxon.tree.wrapper.VirtualCopy;
import net.sf.saxon.type.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;


/**
 * The Navigator class provides helper classes for navigating a tree, irrespective
 * of its implementation
 *
 * @author Michael H. Kay
 */


public final class Navigator {

    // Class is never instantiated
    private Navigator() {
    }

    /**
     * Get the string value of an attribute of a given element, given the URI and
     * local part of the attribute name.
     *
     * @param element   the element on which the required attribute appears
     * @param uri       The namespace URI of the attribute name.
     *                  The "no namespace" case is represented as an empty string.
     * @param localName The local part of the attribute name.
     * @return the attribute value, or null if the attribute is not present
     * @since 9.0
     */

    public static String getAttributeValue(/*@NotNull*/ NodeInfo element, /*@NotNull*/ String uri, /*@NotNull*/ String localName) {
        return element.getAttributeValue(uri, localName);
    }

    /**
     * Get the string value of an inherited attribute of a given element, given the URI and
     * local part of the attribute name. If the element does not have such an attribute, go up
     * the ancestor axis to find such an attribute
     *
     * @param element   the element where the search should start
     * @param uri       The namespace URI of the attribute name.
     *                  The "no namespace" case is represented as an empty string.
     * @param localName The local part of the attribute name.
     * @return the attribute value, or null if the attribute is not present on any ancestor
     * @since 9.9
     */

    public static String getInheritedAttributeValue(NodeInfo element, String uri, String localName) {
        NodeInfo node = element;
        while (node != null) {
            String value = node.getAttributeValue(uri, localName);
            if (value == null) {
                node = node.getParent();
            } else {
                return value;
            }
        }
        return null;
    }

    /**
     * Helper method to get the name of a node as a structuredQName. Used from bytecode
     * @param node the node
     * @return the name of the node if it has a name, or null otherwise
     */

    public static StructuredQName getNodeName(NodeInfo node) {
        if (node.getLocalPart() != null) {
            return new StructuredQName(node.getPrefix(), node.getURI(), node.getLocalPart());
        } else {
            return null;
        }
    }

    /**
     * Helper method to get the outermost element of a document, given the document node
     *
     * @param doc the document node at the root of the document
     * @return the first element child of the document node, if there is one, else null. This
     *         is often referred to as the "root element" or "document element". No error is reported
     *         if the document node has multiple element children, which can happen in a document
     *         constructed using XSLT or XQuery.
     * @since 9.3
     */

    /*@Nullable*/
    public static NodeInfo getOutermostElement(/*@NotNull*/ TreeInfo doc) {
        return doc.getRootNode().iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT).next();
    }

    /**
     * Helper method to get the base URI of an element or processing instruction node
     *
     * @param node the node whose base URI is required
     * @return the base URI of the node
     * @since 8.7
     */

    /*@Nullable*/
    public static String getBaseURI(/*@NotNull*/ NodeInfo node) {
        return getBaseURI(node, n -> {
            NodeInfo parent = n.getParent();
            return parent == null || !parent.getSystemId().equals(n.getSystemId());
        });
    }

    /**
     * Helper method to get the base URI of an element or processing instruction node
     * @param node the node whose base URI is required
     * @param isTopElementWithinEntity a predicate applied to a node that can be tested to determine
     *                                 if the node is an element that originated as the outermost element within
     *                                 an external entity. If this is the case, the rules for determining
     *                                 the base URI are slightly different; in particular, any @xml:base
     *                                 attribute on such a node is interpreted relative to the system ID of
     *                                 the entity, not relative to the base URI of the parent node.
     * @return the base URI of the node
     * @since 9.9
     */

    /*@Nullable*/
    public static String getBaseURI(/*@NotNull*/ NodeInfo node, Predicate<NodeInfo> isTopElementWithinEntity) {
        String xmlBase = node instanceof TinyElementImpl ?
                ((TinyElementImpl)node).getAttributeValue(StandardNames.XML_BASE) :
                node.getAttributeValue(NamespaceConstant.XML, "base");
        if (xmlBase != null) {
            URI baseURI;
            try {
                baseURI = new URI(xmlBase);
                if (!baseURI.isAbsolute()) {
                    NodeInfo parent = node.getParent();
                    if (parent == null) {
                        // We have a parentless element with a relative xml:base attribute.
                        // See for example test XQTS fn-base-uri-10 and base-uri-27
                        URI base = new URI(node.getSystemId());
                        URI resolved = xmlBase.isEmpty() ? base : base.resolve(baseURI);
                        return resolved.toString();
                    }
                    String startSystemId = node.getSystemId();
                    if (startSystemId == null) {
                        return null;
                    }
                    String parentSystemId = parent.getSystemId();
                    boolean isTopWithinEntity = false; // TODO: variable is unused. What's going on here? - MHK 2020-07-04
                    if (node instanceof TinyElementImpl) {
                        isTopWithinEntity = ((TinyElementImpl)node).getTree().isTopWithinEntity(((TinyElementImpl) node).getNodeNumber());
                    } else {
                        isTopWithinEntity = !startSystemId.equals(parentSystemId);
                    }
                    URI base = new URI(isTopElementWithinEntity.test(node) ? startSystemId : parent.getBaseURI());
                    //URI base = new URI(parent.getBaseURI());  //bug 3530
                    baseURI = xmlBase.isEmpty() ? base : base.resolve(baseURI);
                }
            } catch (URISyntaxException e) {
                // xml:base is an invalid URI. Just return it as is: the operation that needs the base URI
                // will probably fail as a result.     \
                return xmlBase;
            }
            return baseURI.toString();
        }
        String startSystemId = node.getSystemId();
        if (startSystemId == null) {
            return null;
        }
        NodeInfo parent = node.getParent();
        if (parent == null) {
            return startSystemId;
        }
        String parentSystemId = parent.getSystemId();
        if (startSystemId.equals(parentSystemId) || parentSystemId.isEmpty()) {
            return parent.getBaseURI();
        } else {
            return startSystemId;
        }
    }

    /**
     * Get an absolute XPath expression that identifies a given node within its document
     *
     * @param node the node whose path is required. If null is supplied,
     *             an empty string is returned - this fact is used in making a recursive call
     *             for a parentless node.
     * @return a path expression that can be used to retrieve the node
     */

    public static String getPath(NodeInfo node) {
        return getPath(node, null);
    }

    /**
     * Get an absolute XPath expression that identifies a given node within its document. The
     * resulting path is intended for human readers, not for software evaluation. It uses lexical
     * QNames (prefix:localname) for element and attribute names, with the original prefix as it
     * appears in the source document. The sibling position of an element (a/b[2]) is included
     * unless the node is a streamed node.
     *
     * @param node    the node whose path is required. If null is supplied,
     *                an empty string is returned - this fact is used in making a recursive call
     *                for a parentless node.
     * @param context the XPath dynamic evaluation context. May be null if no context is known
     * @return a path expression that can be used to retrieve the node
     */

    public static String getPath(/*@Nullable*/ NodeInfo node, /*@Nullable*/ XPathContext context) {
        if (node == null) {
            return "";
        }
        String pre;
        boolean streamed = node.getConfiguration().isStreamedNode(node);
        NodeInfo parent = node.getParent();
        // System.err.println("node = " + node + " parent = " + parent);

        switch (node.getNodeKind()) {
            case Type.DOCUMENT:
                return "/";
            case Type.ELEMENT:
                if (parent == null) {
                    return node.getDisplayName();
                } else {
                    pre = getPath(parent, context);
                    if (pre.equals("/")) {
                        return '/' + node.getDisplayName();
                    } else {
                        return pre + '/' + node.getDisplayName() + (streamed ? "" : "[" + getNumberSimple(node, context) + "]");
                    }
                }
            case Type.ATTRIBUTE:
                return getPath(parent, context) + "/@" + node.getDisplayName();
            case Type.TEXT:
                pre = getPath(parent, context);
                return (pre.equals("/") ? "" : pre) +
                            "/text()" + (streamed ? "" : "[" + getNumberSimple(node, context) + "]");

            case Type.COMMENT:
                pre = getPath(parent, context);
                return (pre.equals("/") ? "" : pre) +
                            "/comment()" + (streamed ? "" : "[" + getNumberSimple(node, context) + "]");

            case Type.PROCESSING_INSTRUCTION:
                pre = getPath(parent, context);
                return (pre.equals("/") ? "" : pre) +
                            "/processing-instruction()" + (streamed ? "" : "[" + getNumberSimple(node, context) + "]");

            case Type.NAMESPACE:
                String test = node.getLocalPart();
                if (test.isEmpty()) {
                    // default namespace: need a node-test that selects unnamed nodes only
                    test = "*[not(local-name()]";
                }
                return getPath(parent, context) + "/namespace::" + test;
            default:
                return "";
        }
    }

    /**
     * Get the absolute path to a node
     *
     * @param node the node in question
     * @return an object representing the path to the node
     */

    public static AbsolutePath getAbsolutePath(NodeInfo node) {
        boolean streamed = node.getConfiguration().isStreamedNode(node);
        List<AbsolutePath.PathElement> path = new LinkedList<>();
        String sysId = node.getSystemId();
        while (node != null && node.getNodeKind() != Type.DOCUMENT) {
            path.add(0, new AbsolutePath.PathElement(node.getNodeKind(),
                                                     NameOfNode.makeName(node),
                                                     streamed ? -1 : getNumberSimple(node, null)));
            node = node.getParent();
        }
        AbsolutePath a = new AbsolutePath(path);
        a.setSystemId(sysId);
        return a;
    }

    /**
     * Ask whether two nodes have the same name
     * @param n1 the first node
     * @param n2 the second node
     * @return true if they have the same namespace and local part
     */

    public static boolean haveSameName(NodeInfo n1, NodeInfo n2) {
        if (n1.hasFingerprint() && n2.hasFingerprint()) {
            return n1.getFingerprint() == n2.getFingerprint();
        } else {
            return n1.getLocalPart().equals(n2.getLocalPart()) &&
                n1.getURI().equals(n2.getURI());
        }
    }

    /**
     * Get simple node number. This is defined as one plus the number of previous siblings of the
     * same node type and name. It is not accessible directly in XSL.
     *
     * @param node    The node whose number is required
     * @param context Used for remembering previous result, for
     *                performance. May be null.
     * @return the node number, as defined above
     */

    public static int getNumberSimple(/*@NotNull*/ NodeInfo node, /*@Nullable*/ XPathContext context) {

        //checkNumberable(node);

        NodeTest same;

        if (node.getLocalPart().isEmpty()) {
            same = NodeKindTest.makeNodeKindTest(node.getNodeKind());
        } else {
            same = new SameNameTest(node);
        }

        Controller controller = context == null ? null : context.getController();
        AxisIterator preceding = node.iterateAxis(AxisInfo.PRECEDING_SIBLING, same);

        int i = 1;
        while (true) {
            NodeInfo prev = preceding.next();
            if (prev == null) {
                break;
            }

            if (controller != null) {
                int memo = controller.getRememberedNumber(prev);
                if (memo > 0) {
                    memo += i;
                    controller.setRememberedNumber(node, memo);
                    return memo;
                }
            }

            i++;
        }

        if (controller != null) {
            controller.setRememberedNumber(node, i);
        }
        return i;
    }

    /**
     * Get node number (level="single"). If the current node matches the supplied pattern, the returned
     * number is one plus the number of previous siblings that match the pattern. Otherwise,
     * return the element number of the nearest ancestor that matches the supplied pattern.
     *
     * @param node    the current node, the one whose node number is required
     * @param count   Pattern that identifies which nodes should be
     *                counted. Default (null) is the element name if the current node is
     *                an element, or "node()" otherwise.
     * @param from    Pattern that specifies where counting starts from.
     *                Default (null) is the root node. (This parameter does not seem
     *                useful but is included for the sake of XSLT conformance.)
     * @param context the dynamic context of the transformation, used if
     *                the patterns reference context values (e.g. variables)
     * @return the node number established as follows: go to the nearest
     *         ancestor-or-self that matches the 'count' pattern and that is a
     *         descendant of the nearest ancestor that matches the 'from' pattern.
     *         Return one plus the nunber of preceding siblings of that ancestor
     *         that match the 'count' pattern. If there is no such ancestor,
     *         return 0.
     * @throws XPathException when any error occurs in processing
     */

    public static int getNumberSingle(/*@NotNull*/ NodeInfo node, /*@Nullable*/ Pattern count,
                                      /*@Nullable*/ Pattern from, XPathContext context) throws XPathException {

        if (count == null && from == null) {
            return getNumberSimple(node, context);
        }

        boolean knownToMatch = false;
        if (count == null) {
            if (node.getLocalPart().isEmpty()) {    // unnamed node
                count = new NodeTestPattern(NodeKindTest.makeNodeKindTest(node.getNodeKind()));
            } else {
                count = new NodeTestPattern(new SameNameTest(node));
            }
            knownToMatch = true;
        }

        NodeInfo target = node;
        // code changed in 9.5 to fix issue described in spec bug 9840
        if (!knownToMatch) {
            while (true) {
                if (count.matches(target, context)) {
                    if (from == null) {
                        break;
                    } else {
                        // see whether there is an ancestor node that matches the from pattern
                        NodeInfo anc = target;
                        while (!from.matches(anc, context)) {
                            anc = anc.getParent();
                            if (anc == null) {
                                // there's no ancestor that matches the "from" pattern
                                return 0;
                            }
                        }
                        // we've found the node to be counted
                        break;
                    }
                } else if (from != null && from.matches(target, context)) {
                    // if we find something that matches "from" before we find something that matches "count", exit
                    return 0;
                } else {
                    target = target.getParent();
                    if (target == null) {
                        // found the root before finding a match on either "count" or "from"
                        return 0;
                    }
                }
            }
        }

        // we've found the ancestor to count from

        SequenceIterator preceding =
                target.iterateAxis(AxisInfo.PRECEDING_SIBLING, getNodeTestForPattern(count));
        // pass the filter condition down to the axis enumeration where possible
        boolean alreadyChecked = count instanceof NodeTestPattern;
        int i = 1;
        while (true) {
            NodeInfo p = (NodeInfo) preceding.next();
            if (p == null) {
                return i;
            }
            if (alreadyChecked || count.matches(p, context)) {
                i++;
            }
        }
    }

    /**
     * Get node number (level="any").
     * Return one plus the number of previous nodes in the
     * document that match the supplied pattern
     *
     * @param inst                   Identifies the xsl:number expression; this is relevant
     *                               when the function is memoised to support repeated use of the same
     *                               instruction to number multiple nodes
     * @param node                   The node being numbered
     * @param count                  Pattern that identifies which nodes should be
     *                               counted. Default (null) is the element name if the current node is
     *                               an element, or "node()" otherwise.
     * @param from                   Pattern that specifies where counting starts from.
     *                               Default (null) is the root node. Only nodes at or after the first (most
     *                               recent) node that matches the 'from' pattern are counted.
     * @param context                The dynamic context for the transformation
     * @param hasVariablesInPatterns if the count or from patterns
     *                               contain variables, then it's not safe to get the answer by adding
     *                               one to the number of the most recent node that matches
     * @return one plus the number of nodes that precede the current node,
     *         that match the count pattern, and that follow the first node that
     *         matches the from pattern if specified.
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs
     */

    public static int getNumberAny(/*@NotNull*/ Expression inst, /*@NotNull*/ NodeInfo node, /*@Nullable*/ Pattern count,
                                   /*@Nullable*/ Pattern from, /*@NotNull*/ XPathContext context, boolean hasVariablesInPatterns) throws XPathException {

        NodeInfo memoNode = null;
        int memoNumber = 0;
        Controller controller = context.getController();
        assert controller != null;
        boolean memoise = !hasVariablesInPatterns && from == null;
        if (memoise) {
            Object[] memo = (Object[]) controller.getUserData(inst.getLocation(), "xsl:number");
            if (memo != null) {
                memoNode = (NodeInfo) memo[0];
                memoNumber = (Integer) memo[1];
            }
        }

        int num = 0;
        if (count == null) {
            if (node.getLocalPart().isEmpty()) {    // unnamed node
                count = new NodeTestPattern(NodeKindTest.makeNodeKindTest(node.getNodeKind()));
            } else {
                count = new NodeTestPattern(new SameNameTest(node));
            }
            num = 1;
        } else if (count.matches(node, context)) {
            num = 1;
        }

        // We use a special axis invented for the purpose: the union of the preceding and
        // ancestor axes, but in reverse document order

        // Pass part of the filtering down to the axis iterator if possible
        NodeTest filter;
        if (from == null) {
            filter = getNodeTestForPattern(count);
        } else if (from.getUType() == UType.ELEMENT && count.getUType() == UType.ELEMENT) {
            filter = NodeKindTest.ELEMENT;
        } else {
            filter = AnyNodeTest.getInstance();
        }

        if (from != null && from.matches(node, context)) {
            return num;
        }

        SequenceIterator preceding =
                node.iterateAxis(AxisInfo.PRECEDING_OR_ANCESTOR, filter);

        while (true) {
            NodeInfo prev = (NodeInfo) preceding.next();
            if (prev == null) {
                break;
            }

            if (count.matches(prev, context)) {
                if (num == 1 && prev.equals(memoNode)) {
                    num = memoNumber + 1;
                    break;
                }
                num++;
            }

            if (from != null && from.matches(prev, context)) {
                break;
            }
        }

        if (memoise) {
            Object[] memo = new Object[2];
            memo[0] = node;
            memo[1] = num;
            controller.setUserData(inst.getLocation(), "xsl:number", memo);
        }
        return num;
    }

    /**
     * Get node number (level="multiple").
     * Return a vector giving the hierarchic position of this node. See the XSLT spec for details.
     *
     * @param node    The node to be numbered
     * @param count   Pattern that identifies which nodes (ancestors and
     *                their previous siblings) should be counted. Default (null) is the
     *                element name if the current node is an element, or "node()"
     *                otherwise.
     * @param from    Pattern that specifies where counting starts from.
     *                Default (null) is the root node. Only nodes below the first (most
     *                recent) node that matches the 'from' pattern are counted.
     * @param context The dynamic context for the transformation
     * @return a vector containing for each ancestor-or-self that matches the
     *         count pattern and that is below the nearest node that matches the
     *         from pattern, an Integer which is one greater than the number of
     *         previous siblings that match the count pattern.
     * @throws XPathException if an error occurs
     */

    /*@NotNull*/
    public static List<Long> getNumberMulti(/*@NotNull*/ NodeInfo node, /*@Nullable*/ Pattern count,
                                            /*@Nullable*/ Pattern from, XPathContext context) throws XPathException {

        //checkNumberable(node);

        ArrayList<Long> v = new ArrayList<>(5);

        if (count == null) {
            if (node.getLocalPart().isEmpty()) {    // unnamed node
                count = new NodeTestPattern(NodeKindTest.makeNodeKindTest(node.getNodeKind()));
            } else {
                count = new NodeTestPattern(new SameNameTest(node));
            }
        }

        NodeInfo curr = node;

        while (true) {
            if (count.matches(curr, context)) {
                int num = getNumberSingle(curr, count, null, context);
                v.add(0, (long) num);
            }
            if (from != null && from.matches(curr, context)) {
                break;
            }
            curr = curr.getParent();
            if (curr == null) {
                break;
            }
        }

        return v;
    }

    /**
     * Get a NodeTest to use as a filter for nodes, given a pattern.
     */

    private static NodeTest getNodeTestForPattern(Pattern pattern) {
        ItemType type = pattern.getItemType();
        if (type instanceof NodeTest) {
            return (NodeTest) type;
        } else if (pattern.getUType().overlaps(UType.ANY_NODE)) {
            return AnyNodeTest.getInstance();
        } else {
            return ErrorType.getInstance();
        }
    }

    /**
     * Generic (model-independent) implementation of deep copy algorithm for nodes.
     * This is available for use by any node implementations that choose to use it.
     *
     * @param node        The node to be copied.
     * @param out         The receiver to which events will be sent
     * @param copyOptions Options for copying namespaces, type annotations, etc,
     *                    as defined in {@link net.sf.saxon.om.CopyOptions}
     * @param locationId  The location of the instruction invoking the copy
     * @throws XPathException on any failure reported by the Receiver
     * @throws IllegalArgumentException if the node is an attribute or namespace node
     */

    public static void copy(/*@NotNull*/ NodeInfo node,
                            /*@NotNull*/ Receiver out,
                            int copyOptions,
                            Location locationId) throws XPathException {

        switch (node.getNodeKind()) {
            case Type.DOCUMENT: {
                out.startDocument(CopyOptions.getStartDocumentProperties(copyOptions));
                for (NodeInfo child : node.children()) {
                    child.copy(out, copyOptions, locationId);
                }
                out.endDocument();
                break;
            }
            case Type.ELEMENT: {
                SchemaType annotation = (copyOptions & CopyOptions.TYPE_ANNOTATIONS) != 0 ?
                        node.getSchemaType() :
                        Untyped.getInstance();

                NodeName elementName = NameOfNode.makeName(node);
                NamespaceMap nsMap;
                if (CopyOptions.includes(copyOptions, CopyOptions.ALL_NAMESPACES)) {
                    nsMap = node.getAllNamespaces();
                } else {
                    // Bug #5861 - we need to ensure the namespaces used in element and attribute names are declared
                    nsMap = NamespaceMap.of(elementName.getPrefix(), elementName.getURI());
                    for (AttributeInfo att : node.attributes()) {
                        NodeName attName = att.getNodeName();
                        if (!attName.getPrefix().isEmpty()) {
                            nsMap = nsMap.put(attName.getPrefix(), attName.getURI());
                        }
                    }
                }
                out.startElement(elementName, annotation,
                                 node.attributes(), nsMap,
                                 locationId, ReceiverOption.BEQUEATH_INHERITED_NAMESPACES_ONLY | ReceiverOption.NAMESPACE_OK);


                // output the children

                for (NodeInfo child : node.children()) {
                    child.copy(out, copyOptions, locationId);
                }

                // finally the end tag

                out.endElement();
                return;
            }
            case Type.ATTRIBUTE: {
                throw new IllegalArgumentException("Cannot copy attribute to Receiver");
//                SimpleType annotation = (copyOptions & CopyOptions.TYPE_ANNOTATIONS) != 0 ?
//                        (SimpleType) node.getSchemaType() :
//                        BuiltInAtomicType.UNTYPED_ATOMIC;
//                ((ComplexContentOutputter)out).attribute(NameOfNode.makeName(node), annotation, node.getStringValueCS(), locationId, ReceiverOption.NONE);
//                return;
            }
            case Type.TEXT: {
                CharSequence value = node.getStringValueCS();
                if (value.length() != 0) {
                    // zero-length text nodes can arise from external model wrappers
                    out.characters(value, locationId, ReceiverOption.NONE);
                }
                return;
            }
            case Type.COMMENT: {
                out.comment(node.getStringValueCS(), locationId, ReceiverOption.NONE);
                return;
            }
            case Type.PROCESSING_INSTRUCTION: {
                out.processingInstruction(node.getLocalPart(), node.getStringValueCS(), locationId, ReceiverOption.NONE);
                return;
            }
            case Type.NAMESPACE: {
                throw new IllegalArgumentException("Cannot copy namespace to Receiver");
//                out.namespacesOLD(NamespaceMap.of(node.getLocalPart(), node.getStringValue()), ReceiverOption.NONE);
//                return;
            }
            default:

        }
    }

    /**
     * Generic (model-independent) implementation of deep copy algorithm for nodes.
     * This is available for use by any node implementations that choose to use it.
     *
     * @param node        The node to be copied.
     * @param out         The outputter to which events will be sent
     * @param copyOptions Options for copying namespaces, type annotations, etc,
     *                    as defined in {@link net.sf.saxon.om.CopyOptions}
     * @param locationId  The location of the instruction invoking the copy
     * @throws XPathException on any failure reported by the Receiver
     */

    public static void copy(NodeInfo node, Outputter out,
                            int copyOptions, Location locationId) throws XPathException {

        boolean keepTypes = (copyOptions & CopyOptions.TYPE_ANNOTATIONS) != 0;
        switch (node.getNodeKind()) {
            case Type.DOCUMENT: {
                out.startDocument(CopyOptions.getStartDocumentProperties(copyOptions));
                for (NodeInfo child : node.children()) {
                    copy(child, out, copyOptions, locationId);
                }
                out.endDocument();
                break;
            }
            case Type.ELEMENT: {
                SchemaType annotation = keepTypes ? node.getSchemaType() : Untyped.getInstance();

                out.startElement(NameOfNode.makeName(node), annotation,
                                 locationId, ReceiverOption.DISINHERIT_NAMESPACES | ReceiverOption.NAMESPACE_OK);

                if ((copyOptions & CopyOptions.ALL_NAMESPACES) != 0) {
                    for (NamespaceBinding ns : node.getAllNamespaces()) {
                        out.namespace(ns.getPrefix(), ns.getURI(), ReceiverOption.NONE);
                    }
                }

                for (AttributeInfo attr : node.attributes()) {
                    SimpleType attType = keepTypes ? attr.getType() : BuiltInAtomicType.UNTYPED_ATOMIC;
                    out.attribute(attr.getNodeName(), attType, attr.getValue(), attr.getLocation(), attr.getProperties());
                }

                // output the children

                for (NodeInfo child : node.children()) {
                    copy(child, out, copyOptions, locationId);
                }

                // finally the end tag

                out.endElement();
                return;
            }
            case Type.ATTRIBUTE: {
                SimpleType attType = keepTypes ? (SimpleType)node.getSchemaType() : BuiltInAtomicType.UNTYPED_ATOMIC;
                out.attribute(NameOfNode.makeName(node), attType, node.getStringValueCS(), locationId, ReceiverOption.NONE);
                return;
            }
            case Type.TEXT: {
                CharSequence value = node.getStringValueCS();
                if (value.length() != 0) {
                    // zero-length text nodes can arise from external model wrappers
                    out.characters(value, locationId, ReceiverOption.NONE);
                }
                return;
            }
            case Type.COMMENT: {
                out.comment(node.getStringValueCS(), locationId, ReceiverOption.NONE);
                return;
            }
            case Type.PROCESSING_INSTRUCTION: {
                out.processingInstruction(node.getLocalPart(), node.getStringValueCS(), locationId, ReceiverOption.NONE);
                return;
            }
            case Type.NAMESPACE: {
                out.namespace(node.getLocalPart(), node.getStringValue(), ReceiverOption.NONE);
                return;
            }
            default:

        }
    }

    /**
     * Generic (model-independent) method to determine the relative position of two
     * nodes in document order. The nodes must be in the same tree.
     *
     * @param first  The first node
     * @param second The second node, whose position is to be compared with the first node
     * @return -1 if this node precedes the other node, +1 if it follows the other
     *         node, or 0 if they are the same node. (In this case, isSameNode() will always
     *         return true, and the two nodes will produce the same result for generateId())
     */

    public static int compareOrder(/*@NotNull*/ SiblingCountingNode first, /*@NotNull*/ SiblingCountingNode second) {

        // are they the same node?
        if (first.equals(second)) {
            return 0;
        }

        NodeInfo firstParent = first.getParent();
        if (firstParent == null) {
            // first node is the root
            return -1;
        }

        NodeInfo secondParent = second.getParent();
        if (secondParent == null) {
            // second node is the root
            return +1;
        }

        // do they have the same parent (common case)?
        if (firstParent.equals(secondParent)) {
            int cat1 = nodeCategories[first.getNodeKind()];
            int cat2 = nodeCategories[second.getNodeKind()];
            if (cat1 == cat2) {
                return first.getSiblingPosition() - second.getSiblingPosition();
            } else {
                return cat1 - cat2;
            }
        }

        // find the depths of both nodes in the tree
        int depth1 = 0;
        int depth2 = 0;
        NodeInfo p1 = first;
        NodeInfo p2 = second;
        while (p1 != null) {
            depth1++;
            p1 = p1.getParent();
        }
        while (p2 != null) {
            depth2++;
            p2 = p2.getParent();
        }
        // move up one branch of the tree so we have two nodes on the same level

        p1 = first;
        while (depth1 > depth2) {
            p1 = p1.getParent();
            assert p1 != null;
            if (p1.equals(second)) {
                return +1;
            }
            depth1--;
        }

        p2 = second;
        while (depth2 > depth1) {
            p2 = p2.getParent();
            assert p2 != null;
            if (p2.equals(first)) {
                return -1;
            }
            depth2--;
        }

        // now move up both branches in sync until we find a common parent
        while (true) {
            NodeInfo par1 = p1.getParent();
            NodeInfo par2 = p2.getParent();
            if (par1 == null || par2 == null) {
                throw new NullPointerException("Node order comparison - internal error");
            }
            if (par1.equals(par2)) {
                if (p1.getNodeKind() == Type.ATTRIBUTE && p2.getNodeKind() != Type.ATTRIBUTE) {
                    return -1;  // attributes first
                }
                if (p1.getNodeKind() != Type.ATTRIBUTE && p2.getNodeKind() == Type.ATTRIBUTE) {
                    return +1;  // attributes first
                }
                return ((SiblingCountingNode) p1).getSiblingPosition() -
                        ((SiblingCountingNode) p2).getSiblingPosition();
            }
            p1 = par1;
            p2 = par2;
        }
    }

    /**
     * Generic (model-independent) method to determine the relative position of two
     * node in document order. The nodes must be in the same tree.
     *
     * @param first  The first node
     * @param second The second node, whose position is to be compared with the first node
     * @return {@link net.sf.saxon.om.AxisInfo#PRECEDING} if this node is on the preceding axis of the other node;
     *         {@link net.sf.saxon.om.AxisInfo#FOLLOWING} if it is on the following axis; {@link net.sf.saxon.om.AxisInfo#ANCESTOR} if the first node is an
     *         ancestor of the second; {@link net.sf.saxon.om.AxisInfo#DESCENDANT} if the first is a descendant of the second;
     *         {@link net.sf.saxon.om.AxisInfo#SELF} if they are the same node.
     * @throws UnsupportedOperationException if either node is an attribute or namespace
     * @since 9.5
     */

    public static int comparePosition(/*@NotNull*/ NodeInfo first, /*@NotNull*/ NodeInfo second) {

        if (first.getNodeKind() == Type.ATTRIBUTE || first.getNodeKind() == Type.NAMESPACE ||
                second.getNodeKind() == Type.ATTRIBUTE || second.getNodeKind() == Type.NAMESPACE) {
            throw new UnsupportedOperationException();
        }

        // are they the same node?
        if (first.equals(second)) {
            return AxisInfo.SELF;
        }

        NodeInfo firstParent = first.getParent();
        if (firstParent == null) {
            // first node is the root
            return AxisInfo.ANCESTOR;
        }

        NodeInfo secondParent = second.getParent();
        if (secondParent == null) {
            // second node is the root
            return AxisInfo.DESCENDANT;
        }

        // do they have the same parent (common case)?
        if (firstParent.equals(secondParent)) {
            if (first.compareOrder(second) < 0) {
                return AxisInfo.PRECEDING;
            } else {
                return AxisInfo.FOLLOWING;
            }
        }

        // find the depths of both nodes in the tree
        int depth1 = 0;
        int depth2 = 0;
        NodeInfo p1 = first;
        NodeInfo p2 = second;
        while (p1 != null) {
            depth1++;
            p1 = p1.getParent();
        }
        while (p2 != null) {
            depth2++;
            p2 = p2.getParent();
        }
        // Test if either node is an ancestor of the other

        p1 = first;
        while (depth1 > depth2) {
            p1 = p1.getParent();
            assert p1 != null;
            if (p1.equals(second)) {
                return AxisInfo.DESCENDANT;
            }
            depth1--;
        }

        p2 = second;
        while (depth2 > depth1) {
            p2 = p2.getParent();
            assert p2 != null;
            if (p2.equals(first)) {
                return AxisInfo.ANCESTOR;
            }
            depth2--;
        }

        // now delegate to compareOrder()
        if (first.compareOrder(second) < 0) {
            return AxisInfo.PRECEDING;
        } else {
            return AxisInfo.FOLLOWING;
        }
    }


    /**
     * Classify node kinds into categories for sorting into document order:
     * 0 = document, 1 = namespace, 2 = attribute, 3 = (element, text, comment, pi)
     */

    /*@NotNull*/ private static int[] nodeCategories = {
            -1, //0 = not used
            3, //1 = element
            2, //2 = attribute
            3, //3 = text
            -1, -1, -1, //4,5,6 = not used
            3, //7 = processing-instruction
            3, //8 = comment
            0, //9 = document
            -1, -1, -1, //10,11,12 = not used
            1   //13 = namespace
    };

    /**
     * Get a character string that uniquely identifies this node and that collates nodes
     * into document order
     *
     * @param node     the node whose unique identifier is reuqired
     * @param sb       a buffer to which the unique identifier will be appended
     * @param addDocNr true if a unique document number is to be included in the information
     */

    public static void appendSequentialKey(/*@NotNull*/ SiblingCountingNode node, /*@NotNull*/ FastStringBuffer sb, boolean addDocNr) {
        if (addDocNr) {
            sb.cat('w');
            sb.append(Long.toString(node.getTreeInfo().getDocumentNumber()));
        }
        if (node.getNodeKind() != Type.DOCUMENT) {
            NodeInfo parent = node.getParent();
            if (parent != null) {
                appendSequentialKey((SiblingCountingNode) parent, sb, false);
            }
            if (node.getNodeKind() == Type.ATTRIBUTE) {
                sb.cat('A');
            }
        }
        sb.append(alphaKey(node.getSiblingPosition()));
    }


    /**
     * Construct an alphabetic key from an positive integer; the key collates in the same sequence
     * as the integer
     *
     * @param value The positive integer key value (negative values are treated as zero).
     * @return the alphabetic key value
     */

    /*@NotNull*/
    public static String alphaKey(int value) {
        if (value < 1) {
            return "a";
        }
        if (value < 10) {
            return "b" + value;
        }
        if (value < 100) {
            return "c" + value;
        }
        if (value < 1000) {
            return "d" + value;
        }
        if (value < 10000) {
            return "e" + value;
        }
        if (value < 100000) {
            return "f" + value;
        }
        if (value < 1000000) {
            return "g" + value;
        }
        if (value < 10000000) {
            return "h" + value;
        }
        if (value < 100000000) {
            return "i" + value;
        }
        if (value < 1000000000) {
            return "j" + value;
        }
        return "k" + value;
    }

    /**
     * Test if one node is an ancestor-or-self of another
     *
     * @param a the putative ancestor-or-self node
     * @param d the putative descendant node
     * @return true if a is an ancestor-or-self of d
     */

    public static boolean isAncestorOrSelf(/*@NotNull*/ NodeInfo a, /*@NotNull*/ NodeInfo d) {
        int k = a.getNodeKind();
        if (k != Type.ELEMENT && k != Type.DOCUMENT) {
            return a.equals(d);
        }
        // Fast path for the TinyTree implementation
        if (a instanceof TinyNodeImpl) {
            if (d instanceof TinyNodeImpl) {
                return ((TinyNodeImpl) a).isAncestorOrSelf((TinyNodeImpl) d);
            } else if (d instanceof TinyTextualElement.TinyTextualElementText) {
                return a.equals(d) || isAncestorOrSelf(a, d.getParent());
            } else if (d.getNodeKind() == Type.NAMESPACE) {
                // fall through
            } else if (d instanceof VirtualCopy) {
                // fall through
            } else {
                return false;
            }
        }
        // Generic implementation
        NodeInfo p = d;
        while (p != null) {
            if (a.equals(p)) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }


    ///////////////////////////////////////////////////////////////////////////////
    // Helper classes to support axis iteration
    ///////////////////////////////////////////////////////////////////////////////

    /**
     * Create an iterator over a singleton node, if it exists and matches a nodetest;
     * otherwise return an empty iterator
     *
     * @param node     the singleton node, or null if the node does not exist
     * @param nodeTest the test to be applied
     * @return an iterator over the node if it exists and matches the test.
     */

    /*@NotNull*/
    public static AxisIterator filteredSingleton(NodeInfo node, Predicate<? super NodeInfo> nodeTest) {
        if (node != null && nodeTest.test(node)) {
            return SingleNodeIterator.makeIterator(node);
        } else {
            return EmptyIterator.ofNodes();
        }
    }

    /**
     * Get the sibling position of a node: specifically, count how many preceding siblings
     * of a node satisfy the nodetest.
     *
     * @param node     the starting node, which is assumed to satisfy the node test
     * @param nodeTest the node test
     * @param max      the maximum number of nodes to be counted
     * @return the number of preceding siblings that satisfy the node test, plus one, unless the
     *         number exceeds max, in which case return some number greater than or equal to max.
     */

    public static int getSiblingPosition(NodeInfo node, NodeTest nodeTest, int max) {
        if (node instanceof SiblingCountingNode && nodeTest instanceof AnyNodeTest) {
            return ((SiblingCountingNode)node).getSiblingPosition();
        }
        AxisIterator prev = node.iterateAxis(AxisInfo.PRECEDING_SIBLING, nodeTest);
        int count = 1;
        while (prev.next() != null) {
            if (++count > max) {
                return count;
            }
        }
        return count;
    }

    /**
     * AxisFilter is an iterator that applies a NodeTest filter to
     * the nodes returned by an underlying AxisIterator.
     */

    public static class AxisFilter implements AxisIterator {
        private AxisIterator base;
        private Predicate<? super NodeInfo> nodeTest;

        /**
         * Construct a AxisFilter
         *
         * @param base the underlying iterator that returns all the nodes on
         *             a required axis. This must not be an atomizing iterator!
         * @param test a NodeTest that is applied to each node returned by the
         *             underlying AxisIterator; only those nodes that pass the NodeTest are
         *             returned by the AxisFilter
         */

        public AxisFilter(AxisIterator base, Predicate<? super NodeInfo> test) {
            this.base = base;
            nodeTest = test;
        }

        /*@Nullable*/
        @Override
        public NodeInfo next() {
            while (true) {
                NodeInfo next = base.next();
                if (next == null) {
                    return null;
                }
                if (nodeTest.test(next)) {
                    return next;
                }
            }
        }

    }

    /**
     * EmptyTextFilter is an iterator that applies removes any zero-length text
     * nodes returned by an underlying AxisIterator.
     */

    public static class EmptyTextFilter implements AxisIterator {
        private AxisIterator base;

        /**
         * Construct an EmptyTextFilter
         *
         * @param base the underlying iterator that returns all the nodes on
         *             a required axis. This must not be an atomizing iterator
         */

        public EmptyTextFilter(AxisIterator base) {
            this.base = base;
        }

        /*@Nullable*/
        @Override
        public NodeInfo next() {
            while (true) {
                NodeInfo next = base.next();
                if (next == null) {
                    return null;
                }
                if (!(next.getNodeKind() == Type.TEXT && next.getStringValueCS().length() == 0)) {
                    return next;
                }
            }
        }

    }


    /**
     * General-purpose implementation of the ancestor and ancestor-or-self axes
     */

    public static final class AncestorEnumeration implements AxisIterator {

        private boolean includeSelf;
        private boolean atStart;
        private NodeInfo current;

        /**
         * Create an iterator over the ancestor or ancestor-or-self axis
         *
         * @param start       the initial context node
         * @param includeSelf true if the "self" node is to be included
         */

        public AncestorEnumeration(NodeInfo start, boolean includeSelf) {
            this.includeSelf = includeSelf;
            current = start;
            atStart = true;
        }

        @Override
        public final NodeInfo next() {
            if (atStart) {
                atStart = false;
                if (includeSelf) {
                    return current;
                }
            }
            return current = current == null ? null : current.getParent();
        }

    } // end of class AncestorEnumeration

    /**
     * General-purpose implementation of the descendant and descendant-or-self axes,
     * in terms of the child axis.
     * But it also has the option to return the descendants in reverse document order;
     * this is used when evaluating the preceding axis. Note that the includeSelf option
     * should not be used when scanning in reverse order, as the self node will always be
     * returned first.
     */

    public static final class DescendantEnumeration implements AxisIterator {

        /*@Nullable*/ private AxisIterator children = null;
        /*@Nullable*/ private AxisIterator descendants = null;
        private NodeInfo start;
        private boolean includeSelf;
        private boolean forwards;
        private boolean atEnd = false;

        /**
         * Create an iterator over the descendant or descendant-or-self axis
         *
         * @param start       the initial context node
         * @param includeSelf true if the "self" node is to be included
         * @param forwards    true for a forwards iteration, false for reverse order
         */

        public DescendantEnumeration(NodeInfo start,
                                     boolean includeSelf, boolean forwards) {
            this.start = start;
            this.includeSelf = includeSelf;
            this.forwards = forwards;
        }

         @Override
         public final NodeInfo next() {
            if (descendants != null) {
                NodeInfo nextd = descendants.next();
                if (nextd != null) {
                    return nextd;
                } else {
                    descendants = null;
                }
            }
            if (children != null) {
                NodeInfo n = children.next();
                if (n != null) {
                    if (n.hasChildNodes()) {
                        if (forwards) {
                            descendants = new DescendantEnumeration(n, false, true);
                            return n;
                        } else {
                            descendants = new DescendantEnumeration(n, true, false);
                            return next();
                        }
                    } else {
                        return n;
                    }
                } else {
                    if (forwards || !includeSelf) {
                        return null;
                    } else {
                        atEnd = true;
                        children = null;
                        return start;
                    }
                }
            } else if (atEnd) {
                // we're just finishing a backwards scan
                return null;
            } else {
                // we're just starting...
                if (start.hasChildNodes()) {
                    //children = new NodeWrapper.ChildEnumeration(start, true, forwards);
                    children = start.iterateAxis(AxisInfo.CHILD);
                    if (!forwards) {
                        if (children instanceof ReversibleIterator) {
                            children = (AxisIterator) ((ReversibleIterator) children).getReverseIterator();
                        } else {
                            LinkedList<NodeInfo> list = new LinkedList<>();
                            AxisIterator forwards = start.iterateAxis(AxisInfo.CHILD);
                            NodeInfo n;
                            while ((n = forwards.next()) != null) {
                                list.addFirst(n);
                            }
                            children = new ListIterator.OfNodes(list);
                        }
                    }
                } else {
                    children = EmptyIterator.ofNodes();
                }
                if (forwards && includeSelf) {
                    return start;
                } else {
                    return next();
                }
            }
        }

        public void advance() {

        }

    } // end of class DescendantEnumeration

    /**
     * General purpose implementation of the following axis, in terms of the
     * ancestor, child, and following-sibling axes
     */

    public static final class FollowingEnumeration implements AxisIterator {
        /*@NotNull*/ private AxisIterator ancestorEnum;
        /*@Nullable*/ private AxisIterator siblingEnum;
        /*@Nullable*/ private AxisIterator descendEnum = null;

        /**
         * Create an iterator over the "following" axis
         *
         * @param start the initial context node
         */

        public FollowingEnumeration(/*@NotNull*/ NodeInfo start) {
            ancestorEnum = new AncestorEnumeration(start, false);
            switch (start.getNodeKind()) {
                case Type.ELEMENT:
                case Type.TEXT:
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                    //siblingEnum = new NodeWrapper.ChildEnumeration(start, false, true);
                    // gets following siblings
                    siblingEnum = start.iterateAxis(AxisInfo.FOLLOWING_SIBLING);
                    break;
                case Type.ATTRIBUTE:
                case Type.NAMESPACE:
                    //siblingEnum = new NodeWrapper.ChildEnumeration((NodeWrapper)start.getParent(), true, true);
                    // gets children of the attribute's parent node
                    NodeInfo parent = start.getParent();
                    if (parent == null) {
                        siblingEnum = EmptyIterator.ofNodes();
                    } else {
                        siblingEnum = parent.iterateAxis(AxisInfo.CHILD);
                    }
                    break;
                default:
                    siblingEnum = EmptyIterator.ofNodes();
            }
        }

        @Override
        public final NodeInfo next() {
            if (descendEnum != null) {
                NodeInfo nextd = descendEnum.next();
                if (nextd != null) {
                    return nextd;
                } else {
                    descendEnum = null;
                }
            }
            if (siblingEnum != null) {
                NodeInfo nexts = siblingEnum.next();
                if (nexts != null) {
                    if (nexts.hasChildNodes()) {
                        descendEnum = new DescendantEnumeration(nexts, false, true);
                    } else {
                        descendEnum = null;
                    }
                    return nexts;
                } else {
                    descendEnum = null;
                    siblingEnum = null;
                }
            }
            NodeInfo nexta = ancestorEnum.next();
            if (nexta != null) {
                if (nexta.getNodeKind() == Type.DOCUMENT) {
                    siblingEnum = EmptyIterator.ofNodes();
                } else {
                    //siblingEnum = new NodeWrapper.ChildEnumeration(next, false, true);
                    siblingEnum = nexta.iterateAxis(AxisInfo.FOLLOWING_SIBLING);
                }
                return next();
            } else {
                return null;
            }
        }

    } // end of class FollowingEnumeration

    /**
     * Helper method to iterate over the preceding axis, or Saxon's internal
     * preceding-or-ancestor axis, by making use of the ancestor, descendant, and
     * preceding-sibling axes.
     */

    public static final class PrecedingEnumeration implements AxisIterator {

        /*@NotNull*/ private AxisIterator ancestorEnum;
        /*@Nullable*/ private AxisIterator siblingEnum;
        /*@Nullable*/ private AxisIterator descendEnum = null;
        private boolean includeAncestors;

        /**
         * Create an iterator for the preceding or "preceding-or-ancestor" axis (the latter being
         * used internall to support xsl:number)
         *
         * @param start            the initial context node
         * @param includeAncestors true if ancestors of the initial context node are to be included
         *                         in the result
         */

        public PrecedingEnumeration(/*@NotNull*/ NodeInfo start, boolean includeAncestors) {
            this.includeAncestors = includeAncestors;
            ancestorEnum = new AncestorEnumeration(start, false);
            switch (start.getNodeKind()) {
                case Type.ELEMENT:
                case Type.TEXT:
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                    // get preceding-sibling enumeration
                    siblingEnum = start.iterateAxis(AxisInfo.PRECEDING_SIBLING);
                    break;
                default:
                    siblingEnum = EmptyIterator.ofNodes();
            }
        }

        @Override
        public final NodeInfo next() {
            if (descendEnum != null) {
                NodeInfo nextd = descendEnum.next();
                if (nextd != null) {
                    return nextd;
                } else {
                    descendEnum = null;
                }
            }
            if (siblingEnum != null) {
                NodeInfo nexts = siblingEnum.next();
                if (nexts != null) {
                    if (nexts.hasChildNodes()) {
                        descendEnum = new DescendantEnumeration(nexts, true, false);
                        return next();
                    } else {
                        descendEnum = null;
                        return nexts;
                    }
                } else {
                    descendEnum = null;
                    siblingEnum = null;
                }
            }
            NodeInfo nexta = ancestorEnum.next();
            if (nexta != null) {
                if (nexta.getNodeKind() == Type.DOCUMENT) {
                    siblingEnum = EmptyIterator.ofNodes();
                } else {
                    siblingEnum = nexta.iterateAxis(AxisInfo.PRECEDING_SIBLING);
                }
                if (!includeAncestors) {
                    return next();
                } else {
                    return nexta;
                }
            } else {
                return null;
            }
        }


    } // end of class PrecedingEnumeration


}

