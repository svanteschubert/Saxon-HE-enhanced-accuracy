////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sapling;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.ma.trie.ImmutableHashTrieMap;
import net.sf.saxon.ma.trie.ImmutableList;
import net.sf.saxon.ma.trie.ImmutableMap;
import net.sf.saxon.ma.trie.Tuple2;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;

import java.util.Objects;

/**
 * An element node on a sapling tree. The node has the following properties:
 *
 * <ul>
 * <li>A name. This is a QName, potentially containing a prefix, URI, and local-name.</li>
 *
 * <li>An ordered list of children. The children may be elements, text nodes, comments, or processing instructions.</li>
 *
 * <li>A set of attributes. An attribute has a name (which is a QName), and a string value; attribute names must
 * be distinct. The prefix and URI of an attribute must either both be empty or both non-empty. Attributes in this
 * model are not nodes, they are simply properties of an element node. Attributes for an element are unordered.</li>
 *
 * <li>A set of namespace bindings (prefix/URI pairs, where the prefix may be empty, and the URI may be empty
 * if and only if the prefix is empty). The namespace bindings implicitly include the prefix/URI combination
 * for the element name, and the prefix/URI combinations for all attributes. They may also include additional
 * namespace bindings. As far as the sapling tree is concerned, namespace bindings are not inherited from parent
 * nodes; each element must contain all the namespace bindings that it needs. When a sapling tree is converted
 * to a full tree, however, namespace bindings on a parent node will propagate to its children.</li>
 * </ul>
 *
 * <p>Like all sapling nodes, a {@code SaplingElement} is immutable. All operations such as adding children
 * or attributes deliver a new element node. A sapling node generally exists only transiently during tree
 * construction; to make use of the constructed tree, it will usually be converted to a regular tree when
 * construction is complete, using {@link #toXdmNode(Processor)} or {@link #toNodeInfo(Configuration)}.</p>
 */

public class SaplingElement extends SaplingNode {

    private StructuredQName nodeName;
    private ImmutableList<SaplingNode> reversedChildren = ImmutableList.empty();
    private ImmutableMap<StructuredQName, String> attributes = ImmutableHashTrieMap.empty();
    private NamespaceMap namespaces = NamespaceMap.emptyMap();

    /**
     * Create an empty element, in no namespace
     * @param name the name of the element. This should take the form of an NCName, but the
     *             current implementation does not check this.
     */

    public SaplingElement(String name) {
        Objects.requireNonNull(name);
        nodeName = StructuredQName.fromEQName(name);
    }

    /**
     * Create an empty element, with a name supplied as a QName.
     *
     * @param name the name of the element, as a QName. If the prefix of the QName is non-empty,
     *             then the URI part must also be non-empty.
     * @throws IllegalArgumentException if the name contains a prefix but no URI
     */

    public SaplingElement(QName name) {
        Objects.requireNonNull(name);
        nodeName = name.getStructuredQName();
        if (!nodeName.getPrefix().isEmpty() && nodeName.getURI().isEmpty()) {
            throw new IllegalArgumentException("No namespace URI for prefixed element name: " + name);
        }
        namespaces = NamespaceMap.of(nodeName.getPrefix(), nodeName.getURI());
    }

    private SaplingElement(StructuredQName name) {
        this.nodeName = name;
    }

    @Override
    public int getNodeKind() {
        return Type.ELEMENT;
    }

    private SaplingElement copy() {
        SaplingElement e2 = new SaplingElement(nodeName);
        e2.reversedChildren = reversedChildren;
        e2.attributes = attributes;
        e2.namespaces = namespaces;
        return e2;
    }

    /**
     * Add a number of child nodes to a document node, returning a new document node
     * with additional children beyond those already present. The target document is not modified,
     * neither are the added children.
     * <p>Note: because adding a child always creates a new parent element, it is impossible
     * to create cycles by adding a node to itself, directly or indirectly.</p>
     * @param children The nodes to be added as children.
     * The supplied nodes are added in order after any existing children.
     *
     * @return the new parent element node
     * @throws IllegalArgumentException if any of the nodes supplied as an argument is
     *                                  a document node.
     */

    public SaplingElement withChild(SaplingNode... children) {
        SaplingElement e2 = copy();
        for (SaplingNode child : children) {
            switch (child.getNodeKind()) {
                case Type.DOCUMENT:
                    throw new IllegalArgumentException("Cannot add document node as a child of an element node");
                case Type.ELEMENT:
                case Type.TEXT:
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                    e2.reversedChildren = e2.reversedChildren.prepend(child);
                    break;
            }
        }
        return e2;
    }

    /**
     * Add a text node as a child to this element, returning a new sapling element node.
     * This is a convenience method: <code>e.withText("value")</code> is equavalent to
     * <code>e.withChild(text("value"))</code>
     * @param value the string value of a new text node, which will be added as a child
     *              to this element after any existing children
     * @return a new element node, identical to this element but with the new text node as
     * an additional child
     */

    public SaplingElement withText(String value) {
        return withChild(new SaplingText(value));
    }

    private SaplingElement withAttribute(StructuredQName name, String value) {
        SaplingElement e2 = copy();
        e2.attributes = e2.attributes.put(name, value);
        return e2;
    }

    /**
     * Add or replace an attribute of the element node, returning a new element node with a modified
     * set of attributes
     * @param name the name of the attribute to be added or replaced; this represents the local part of
     *             a no-namespace QName. The name must be in the form of an NCName, but the current implementation
     *             does not check this.
     * @param value the (new) value of the attribute
     * @return a new sapling element node, identical to the target node except for the added or replaced attribute
     */

    public SaplingElement withAttr(String name, String value) {
        return withAttribute(new StructuredQName("", "", name), value);
    }

    /**
     * Add or replace an attribute of the element node, returning a new element node with a modified
     * set of attributes
     *
     * @param name  the name of the attribute to be added or replaced, as a QName
     * @param value the (new) value of the attribute
     * @return a new sapling element node, identical to the target node except for the added or replaced attribute
     * @throws IllegalArgumentException if the prefix of the attribute name is empty and the URI is not, or
     * if the URI is empty and the prefix is not
     * @throws IllegalStateException if the prefix/uri binding is incompatible with the existing prefix/uri
     * bindings on the element
     */

    public SaplingElement withAttr(QName name, String value) {
        StructuredQName attName = name.getStructuredQName();
        if (attName.getPrefix().isEmpty() && !attName.getURI().isEmpty()) {
            throw new IllegalArgumentException("An attribute whose name is in a namespace must have a prefix");
        }
        withNamespace(attName.getPrefix(), attName.getURI());
        return withAttribute(attName, value);
    }

    /**
     * Add a namespace binding for a prefix/URI pair. Namespace bindings are added automatically for
     * prefixes used in element and attribute names; they only need to be added explicitly if the binding
     * is additional to those in element and attribute names. A namespace binding for the XML namespace
     * is implicitly present on every element.
     * @param prefix the namespace prefix. This must either be a zero length string, or it must take the
     *               form of an NCName, but this constraint is not currently enforced.
     * @param uri the namespace URI. If this is the empty string, then (a) if the prefix is empty, the
     *            namespace binding is ignored; (b) otherwise, an exception is raised (namespace undeclarations
     *            are not permitted).
     * @return a new element node, identical to the original except for the additional namespace binding
     * @throws IllegalArgumentException if the URI is empty and the prefix is not
     * @throws IllegalStateException if the element already has a namespace binding for this prefix, with
     * a different URI.
     */

    public SaplingElement withNamespace(String prefix, String uri) {
        if (uri.isEmpty()) {
            if (prefix.isEmpty()) {
                return this;
            } else {
                throw new IllegalArgumentException("Cannot bind non-empty prefix to empty URI");
            }
        }
        String existingURI = namespaces.getURI(prefix);
        if (existingURI != null) {
            if (existingURI.equals(uri)) {
                return this;
            } else {
                throw new IllegalStateException("Inconsistent namespace bindings for prefix '" + prefix + "'");
            }
        }
        SaplingElement e2 = copy();
        e2.namespaces = namespaces.put(prefix, uri);
        return e2;
    }

    @Override
    protected void sendTo(Receiver receiver) throws XPathException {
        final Configuration config = receiver.getPipelineConfiguration().getConfiguration();
        final NamePool namePool = config.getNamePool();
        NamespaceMap ns = namespaces;
        if (!nodeName.getURI().isEmpty()) {
            ns = ns.put(nodeName.getPrefix(), nodeName.getURI());
        }
        AttributeMap atts = EmptyAttributeMap.getInstance();
        for (Tuple2<StructuredQName, String> attribute : attributes) {
            atts = atts.put(new AttributeInfo(new FingerprintedQName(attribute._1, namePool), BuiltInAtomicType.UNTYPED_ATOMIC,
                              attribute._2, Loc.NONE, ReceiverOption.NONE));
            if (!attribute._1.getURI().isEmpty()) {
                ns = ns.put(attribute._1.getPrefix(), attribute._1.getURI());
            }
        }
        receiver.startElement(new FingerprintedQName(nodeName, namePool), Untyped.getInstance(),
                              atts, ns,
                              Loc.NONE, ReceiverOption.NONE);

        ImmutableList<SaplingNode> children = reversedChildren.reverse();
        for (SaplingNode node : children) {
            node.sendTo(receiver);
        }
        receiver.endElement();
    }

    /**
     * Convert the sapling element to a regular element node, returning the {@link NodeInfo} object
     * representing the parentless element node at the root of the resulting tree
     *
     * @param config the Saxon Configuration
     * @return the parentless element node at the root of the constructed tree. The implementation model for the
     * tree will be the default tree model of the Configuration
     * @throws XPathException if construction fails; this could happen if constraints have been violated
     */

    public NodeInfo toNodeInfo(Configuration config) throws XPathException {
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        TreeModel treeModel = config.getParseOptions().getModel();
        Builder builder = treeModel.makeBuilder(pipe);
        builder.open();
        sendTo(builder);
        builder.close();
        return builder.getCurrentRoot();
    }

    /**
     * Convert the sapling element to a regular element node, returning the {@link XdmNode} object
     * representing the parentless element node at the root of the resulting tree
     *
     * @param processor the s9api Processor object that is to own the resulting tree
     * @return the element node at the root of the constructed tree. The implementation model for the
     * tree will be the default tree model of the Processor's Configuration
     * @throws SaxonApiException if construction fails; this could happen if constraints have been violated
     */

    public XdmNode toXdmNode(Processor processor) throws SaxonApiException {
        try {
            return (XdmNode) XdmValue.wrap(toNodeInfo(processor.getUnderlyingConfiguration()));
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

}

