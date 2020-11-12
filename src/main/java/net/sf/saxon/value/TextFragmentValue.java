////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingleNodeIterator;
import net.sf.saxon.tree.linked.DocumentImpl;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;

import javax.xml.transform.SourceLocator;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Predicate;

/**
 * This class represents a temporary tree whose root document node owns a single text node. <BR>
 */

public final class TextFragmentValue implements NodeInfo, SourceLocator {

    private CharSequence text;
    private String baseURI;
    private String documentURI;
    private GenericTreeInfo treeInfo;
    private TextFragmentTextNode textNode = null;   // created on demand


    /**
     * Constructor: create a result tree fragment containing a single text node
     * @param config the Saxon Configuration
     * @param value   a String containing the value
     * @param baseURI the base URI of the document node
     */

    public TextFragmentValue(Configuration config, CharSequence value, String baseURI) {
        this.text = value;
        this.baseURI = baseURI;
        this.treeInfo = new GenericTreeInfo(config);
        this.treeInfo.setRootNode(this);
    }

    /**
     * Static factory method: create a result tree fragment containing a single text node,
     * unless the text is zero length, in which case an empty document node is returned
     * @param config the Saxon Configuration
     * @param value   a String containing the value
     * @param baseURI the base URI of the document node
     */

    public static NodeInfo makeTextFragment(Configuration config, CharSequence value, String baseURI) {
        if (value.length() == 0) {
            // Create a childless document node: bug 4246
            DocumentImpl doc = new DocumentImpl();
            doc.setSystemId(baseURI);
            doc.setBaseURI(baseURI);
            doc.setConfiguration(config);
            return doc;
        } else {
            return new TextFragmentValue(config, value, baseURI);
        }
    }

    /**
     * Get information about the tree to which this NodeInfo belongs
     *
     * @return the TreeInfo
     * @since 9.7
     */
    @Override
    public TreeInfo getTreeInfo() {
        return treeInfo;
    }

    /**
     * Get the NodeInfo object representing the document node at the root of the tree
     *
     * @return the document node
     */

    public NodeInfo getRootNode() {
        return this;
    }

    /**
     * Ask whether the document contains any nodes whose type annotation is anything other than
     * UNTYPED
     *
     * @return true if the document contains elements whose type is other than UNTYPED
     */
    public boolean isTyped() {
        return false;
    }

    /**
     * Return the type of node.
     *
     * @return Type.DOCUMENT (always)
     */

    @Override
    public final int getNodeKind() {
        return Type.DOCUMENT;
    }

    /**
     * Get the String Value
     */

    @Override
    public String getStringValue() {
        return text.toString();
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public CharSequence getStringValueCS() {
        return text;
    }

    /**
     * Determine whether this is the same node as another node
     *
     * @return true if this Node object and the supplied Node object represent the
     *         same node in the tree.
     */

    public boolean equals(Object other) {
        return this == other;
    }

    /**
     * Ask whether this NodeInfo implementation holds a fingerprint identifying the name of the
     * node in the NamePool. If the answer is true, then the {@link #getFingerprint} method must
     * return the fingerprint of the node. If the answer is false, then the {@link #getFingerprint}
     * method should throw an {@code UnsupportedOperationException}. In the case of unnamed nodes
     * such as text nodes, the result can be either true (in which case getFingerprint() should
     * return -1) or false (in which case getFingerprint may throw an exception).
     *
     * @return true if the implementation of this node provides fingerprints.
     * @since 9.8; previously Saxon relied on using <code>FingerprintedNode</code> as a marker interface.
     */
    @Override
    public boolean hasFingerprint() {
        return true;
    }

    /**
     * Get a character string that uniquely identifies this node
     *
     * @param buffer the buffer to contain the generated ID
     */

    @Override
    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        buffer.append("tt");
        buffer.append(Long.toString(treeInfo.getDocumentNumber()));
    }

    /**
     * Set the system ID (that is, the document URI property) for the document node.
     *
     * @throws UnsupportedOperationException (always). This kind of tree does not have a document URI.
     */

    @Override
    public void setSystemId(String systemId) {
        documentURI = systemId;
    }

    /**
     * Get the system ID (the document URI) of the document node.
     */

    @Override
    public String getSystemId() {
        return documentURI;
    }

    /**
     * Get the base URI for the document node.
     */

    @Override
    public String getBaseURI() {
        return baseURI;
    }

    /**
     * Determine the relative position of this node and another node, in document order.
     * The other node will always be in the same document.
     *
     * @param other The other node, whose position is to be compared with this node
     * @return -1 if this node precedes the other node, +1 if it follows the other
     *         node, or 0 if they are the same node. (In this case, isSameNode() will always
     *         return true, and the two nodes will produce the same result for generateId())
     */

    @Override
    public int compareOrder(NodeInfo other) {
        if (this == other) {
            return 0;
        }
        return -1;
    }

    /**
     * Get the fingerprint of the node, used for matching names
     */

    @Override
    public int getFingerprint() {
        return -1;
    }

    /**
     * Get the prefix part of the name of this node. This is the name before the ":" if any.
     *
     * @return the prefix part of the name. For an unnamed node, return "".
     */

    /*@NotNull*/
    @Override
    public String getPrefix() {
        return "";
    }

    /**
     * Get the URI part of the name of this node. This is the URI corresponding to the
     * prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node, or for
     *         an element or attribute in the default namespace, return an empty string.
     */

    /*@NotNull*/
    @Override
    public String getURI() {
        return "";
    }

    /**
     * Get the display name of this node. For elements and attributes this is [prefix:]localname.
     * For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node.
     *         For a node with no name, return an empty string.
     */

    /*@NotNull*/
    @Override
    public String getDisplayName() {
        return "";
    }

    /**
     * Get the local name of this node.
     *
     * @return The local name of this node.
     *         For a node with no name, return "".
     */

    /*@NotNull*/
    @Override
    public String getLocalPart() {
        return "";
    }

    /**
     * Determine whether the node has any children.
     *
     * @return <code>true</code> if this node has any attributes,
     *         <code>false</code> otherwise.
     */

    @Override
    public boolean hasChildNodes() {
        return text.length() != 0;
    }

    /**
     * Get an immutable copy of this Location object. By default Location objects may be mutable, so they
     * should not be saved for later use. The result of this operation holds the same location information,
     * but in an immutable form.
     */
    @Override
    public Location saveLocation() {
        return this;
    }

    /**
     * Get the type annotation of this node, if any. The type annotation is represented as
     * SchemaType object.
     * <p>Types derived from a DTD are not reflected in the result of this method.</p>
     *
     * @return For element and attribute nodes: the type annotation derived from schema
     *         validation (defaulting to xs:untyped and xs:untypedAtomic in the absence of schema
     *         validation). For comments, text nodes, processing instructions, and namespaces: null.
     *         For document nodes, either xs:untyped if the document has not been validated, or
     *         xs:anyType if it has.
     * @since 9.4
     */
    @Override
    public SchemaType getSchemaType() {
        return Untyped.getInstance();
    }

    /**
     * Get all namespace undeclarations and undeclarations defined on this element.
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of integers representing the namespace declarations and undeclarations present on
     *         this element. For a node other than an element, return null. Otherwise, the returned array is a
     *         sequence of namespace codes, whose meaning may be interpreted by reference to the name pool. The
     *         top half word of each namespace code represents the prefix, the bottom half represents the URI.
     *         If the bottom half is zero, then this is a namespace undeclaration rather than a declaration.
     *         The XML namespace is never included in the list. If the supplied array is larger than required,
     *         then the first unused entry will be set to -1.
     *         <p>For a node other than an element, the method returns null.</p>
     */

    /*@Nullable*/
    @Override
    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        return null;
    }

    /**
     * Get all the namespace bindings that are in-scope for this element.
     * <p>For an element return all the prefix-to-uri bindings that are in scope. This may include
     * a binding to the default namespace (represented by a prefix of ""). It will never include
     * "undeclarations" - that is, the namespace URI will never be empty; the effect of an undeclaration
     * is to remove a binding from the in-scope namespaces, not to add anything.</p>
     * <p>For a node other than an element, returns null.</p>
     *
     * @return the in-scope namespaces for an element, or null for any other kind of node.
     */
    @Override
    public NamespaceMap getAllNamespaces() {
        return null;
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. If requireSingleton is set to true, the result will always be an
     *         AtomicValue. In other cases it may be a Value representing a sequence whose items are atomic
     *         values.
     * @since 8.5
     */

    /*@NotNull*/
    @Override
    public AtomicSequence atomize() {
        return new UntypedAtomicValue(text);
    }

    /**
     * Get the string value of a given attribute of this node
     *
     * @param uri   the namespace URI of the attribute name. Supply the empty string for an attribute
     *              that is in no namespace
     * @param local the local part of the attribute name.
     * @return the attribute value if it exists, or null if it does not exist. Always returns null
     *         if this node is not an element.
     * @since 9.4
     */
    @Override
    public String getAttributeValue(/*@NotNull*/ String uri, /*@NotNull*/ String local) {
        return null;
    }

    /**
     * Return an iteration over the nodes reached by the given axis from this node
     *
     * @param axisNumber The axis to be iterated over
     * @return a AxisIterator that scans the nodes reached by the axis in turn.
     * @see net.sf.saxon.om.AxisInfo
     */

    /*@NotNull*/
    @Override
    public AxisIterator iterateAxis(int axisNumber) {
        switch (axisNumber) {
            case AxisInfo.ANCESTOR:
            case AxisInfo.ATTRIBUTE:
            case AxisInfo.FOLLOWING:
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.NAMESPACE:
            case AxisInfo.PARENT:
            case AxisInfo.PRECEDING:
            case AxisInfo.PRECEDING_SIBLING:
            case AxisInfo.PRECEDING_OR_ANCESTOR:
                return EmptyIterator.ofNodes();

            case AxisInfo.SELF:
            case AxisInfo.ANCESTOR_OR_SELF:
                return SingleNodeIterator.makeIterator(this);

            case AxisInfo.CHILD:
            case AxisInfo.DESCENDANT:
                return SingleNodeIterator.makeIterator(getTextNode());

            case AxisInfo.DESCENDANT_OR_SELF:
                NodeInfo[] nodes = {this, getTextNode()};
                return new ArrayIterator.OfNodes(nodes);

            default:
                throw new IllegalArgumentException("Unknown axis number " + axisNumber);
        }
    }

    /**
     * Return an enumeration over the nodes reached by the given axis from this node
     *
     * @param axisNumber The axis to be iterated over
     * @param nodeTest   A pattern to be matched by the returned nodes
     * @return a AxisIterator that scans the nodes reached by the axis in turn.
     * @see net.sf.saxon.om.AxisInfo
     */

    /*@NotNull*/
    @Override
    public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {
        switch (axisNumber) {
            case AxisInfo.ANCESTOR:
            case AxisInfo.ATTRIBUTE:
            case AxisInfo.FOLLOWING:
            case AxisInfo.FOLLOWING_SIBLING:
            case AxisInfo.NAMESPACE:
            case AxisInfo.PARENT:
            case AxisInfo.PRECEDING:
            case AxisInfo.PRECEDING_SIBLING:
            case AxisInfo.PRECEDING_OR_ANCESTOR:
                return EmptyIterator.ofNodes();

            case AxisInfo.SELF:
            case AxisInfo.ANCESTOR_OR_SELF:
                return Navigator.filteredSingleton(this, nodeTest);

            case AxisInfo.CHILD:
            case AxisInfo.DESCENDANT:
                return Navigator.filteredSingleton(getTextNode(), nodeTest);

            case AxisInfo.DESCENDANT_OR_SELF:
                boolean b1 = nodeTest.test(this);
                NodeInfo textNode2 = getTextNode();
                boolean b2 = nodeTest.test(textNode2);
                if (b1) {
                    if (b2) {
                        NodeInfo[] pair = {this, textNode2};
                        return new ArrayIterator.OfNodes(pair);
                    } else {
                        return SingleNodeIterator.makeIterator(this);
                    }
                } else {
                    if (b2) {
                        return SingleNodeIterator.makeIterator(textNode2);
                    } else {
                        return EmptyIterator.ofNodes();
                    }
                }

            default:
                throw new IllegalArgumentException("Unknown axis number " + axisNumber);
        }
    }

    /**
     * Find the parent node of this node.
     *
     * @return The Node object describing the containing element or root node.
     */

    /*@Nullable*/
    @Override
    public NodeInfo getParent() {
        return null;
    }

    /**
     * Get the root node
     *
     * @return the NodeInfo representing the root of this tree
     */

    /*@NotNull*/
    @Override
    public NodeInfo getRoot() {
        return this;
    }

    /**
     * Copy the result tree fragment value to a given Outputter
     */

    @Override
    public void copy(/*@NotNull*/ Receiver out, int copyOptions, Location locationId)
            throws XPathException {
        out.characters(text, locationId, ReceiverOption.NONE);
    }

    /**
     * Get the element with a given ID.
     *
     * @param id        The unique ID of the required element
     * @param getParent True if, in the case of an element of type xs:ID, we want its parent
     * @return null (this kind of tree contains no elements)
     */

    /*@Nullable*/
    public NodeInfo selectID(String id, boolean getParent) {
        return null;
    }

    /**
     * Get the list of unparsed entities defined in this document
     *
     * @return an Iterator, whose items are of type String, containing the names of all
     *         unparsed entities defined in this document. If there are no unparsed entities or if the
     *         information is not available then an empty iterator is returned
     */

    public Iterator<String> getUnparsedEntityNames() {
        return Collections.emptyIterator();
    }

    /**
     * Get the unparsed entity with a given name
     *
     * @param name the name of the entity
     * @return the URI and public ID of the entity if there is one, or null if not
     */

    /*@Nullable*/
    public String[] getUnparsedEntity(String name) {
        return null;
    }

    /**
     * Make an instance of the text node
     * @return the new or existing instance
     */

    /*@Nullable*/
    private TextFragmentTextNode getTextNode() {
        if (textNode == null) {
            textNode = new TextFragmentTextNode();
        }
        return textNode;
    }

    /**
     * Inner class representing the text node; this is created on demand
     */

    private class TextFragmentTextNode implements NodeInfo, SourceLocator {

        /**
         * Ask whether this NodeInfo implementation holds a fingerprint identifying the name of the
         * node in the NamePool. If the answer is true, then the {@link #getFingerprint} method must
         * return the fingerprint of the node. If the answer is false, then the {@link #getFingerprint}
         * method should throw an {@code UnsupportedOperationException}. In the case of unnamed nodes
         * such as text nodes, the result can be either true (in which case getFingerprint() should
         * return -1) or false (in which case getFingerprint may throw an exception).
         *
         * @return true if the implementation of this node provides fingerprints.
         * @since 9.8; previously Saxon relied on using <code>FingerprintedNode</code> as a marker interface.
         */
        @Override
        public boolean hasFingerprint() {
            return true;
        }

        /**
         * Get information about the tree to which this NodeInfo belongs
         *
         * @return the TreeInfo
         * @since 9.7
         */
        @Override
        public TreeInfo getTreeInfo() {
            return treeInfo;
        }

        /**
         * Set the system ID for the entity containing the node.
         */

        @Override
        public void setSystemId(String systemId) {
        }

        /**
         * Return the type of node.
         *
         * @return Type.TEXT (always)
         */

        @Override
        public final int getNodeKind() {
            return Type.TEXT;
        }

        /**
         * Get the String Value
         */

        @Override
        public String getStringValue() {
            return text.toString();
        }

        /**
         * Get the value of the item as a CharSequence. This is in some cases more efficient than
         * the version of the method that returns a String.
         */

        @Override
        public CharSequence getStringValueCS() {
            return text;
        }

        /**
         * Determine whether this is the same node as another node
         *
         * @return true if this Node object and the supplied Node object represent the
         *         same node in the tree.
         */

        public boolean equals(NodeInfo other) {
            return this == other;
        }

        /**
         * Get a character string that uniquely identifies this node
         */

        @Override
        public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
            buffer.append("tt");
            buffer.append(Long.toString(treeInfo.getDocumentNumber()));
            buffer.append("t1");
        }

        /**
         * Get the system ID for the entity containing the node.
         */

        /*@Nullable*/
        @Override
        public String getSystemId() {
            return null;
        }

        /**
         * Get the base URI for the node. Default implementation for child nodes gets
         * the base URI of the parent node.
         */

        @Override
        public String getBaseURI() {
            return baseURI;
        }

        /**
         * Determine the relative position of this node and another node, in document order.
         * The other node will always be in the same document.
         *
         * @param other The other node, whose position is to be compared with this node
         * @return -1 if this node precedes the other node, +1 if it follows the other
         *         node, or 0 if they are the same node. (In this case, isSameNode() will always
         *         return true, and the two nodes will produce the same result for generateId())
         */

        @Override
        public int compareOrder(NodeInfo other) {
            if (this == other) {
                return 0;
            }
            return +1;
        }

        /**
         * Get the fingerprint of the node, used for matching names
         */

        @Override
        public int getFingerprint() {
            return -1;
        }


        /**
         * Get the prefix part of the name of this node. This is the name before the ":" if any.
         *
         * @return the prefix part of the name. For an unnamed node, return "".
         */

        /*@NotNull*/
        @Override
        public String getPrefix() {
            return "";
        }

        /**
         * Get the URI part of the name of this node. This is the URI corresponding to the
         * prefix, or the URI of the default namespace if appropriate.
         *
         * @return The URI of the namespace of this node. For an unnamed node, or for
         *         an element or attribute in the default namespace, return an empty string.
         */

        /*@NotNull*/
        @Override
        public String getURI() {
            return "";
        }

        /**
         * Get the display name of this node. For elements and attributes this is [prefix:]localname.
         * For unnamed nodes, it is an empty string.
         *
         * @return The display name of this node.
         *         For a node with no name, return an empty string.
         */

        /*@NotNull*/
        @Override
        public String getDisplayName() {
            return "";
        }

        /**
         * Get the local name of this node.
         *
         * @return The local name of this node.
         *         For a node with no name, return "".
         */

        /*@NotNull*/
        @Override
        public String getLocalPart() {
            return "";
        }

        /**
         * Determine whether the node has any children.
         *
         * @return <code>true</code> if this node has any attributes,
         *         <code>false</code> otherwise.
         */

        @Override
        public boolean hasChildNodes() {
            return false;
        }

        /**
         * Get the string value of a given attribute of this node
         *
         * @param uri   the namespace URI of the attribute name. Supply the empty string for an attribute
         *              that is in no namespace
         * @param local the local part of the attribute name.
         * @return the attribute value if it exists, or null if it does not exist. Always returns null
         *         if this node is not an element.
         * @since 9.4
         */
        @Override
        public String getAttributeValue(/*@NotNull*/ String uri, /*@NotNull*/ String local) {
            return null;
        }

        /**
         * Get an immutable copy of this Location object. By default Location objects may be mutable, so they
         * should not be saved for later use. The result of this operation holds the same location information,
         * but in an immutable form.
         */
        @Override
        public Location saveLocation() {
            return this;
        }

        /**
         * Get the type annotation of this node, if any. The type annotation is represented as
         * SchemaType object.
         * <p>Types derived from a DTD are not reflected in the result of this method.</p>
         *
         * @return For element and attribute nodes: the type annotation derived from schema
         *         validation (defaulting to xs:untyped and xs:untypedAtomic in the absence of schema
         *         validation). For comments, text nodes, processing instructions, and namespaces: null.
         *         For document nodes, either xs:untyped if the document has not been validated, or
         *         xs:anyType if it has.
         * @since 9.4
         */
        @Override
        public SchemaType getSchemaType() {
            return null;
        }

        /**
         * Get all namespace undeclarations and undeclarations defined on this element.
         *
         * @param buffer If this is non-null, and the result array fits in this buffer, then the result
         *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
         * @return An array of integers representing the namespace declarations and undeclarations present on
         *         this element. For a node other than an element, return null. Otherwise, the returned array is a
         *         sequence of namespace codes, whose meaning may be interpreted by reference to the name pool. The
         *         top half word of each namespace code represents the prefix, the bottom half represents the URI.
         *         If the bottom half is zero, then this is a namespace undeclaration rather than a declaration.
         *         The XML namespace is never included in the list. If the supplied array is larger than required,
         *         then the first unused entry will be set to -1.
         *         <p>For a node other than an element, the method returns null.</p>
         */

        /*@Nullable*/
        @Override
        public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
            return null;
        }

        /**
         * Get all the namespace bindings that are in-scope for this element.
         * <p>For an element return all the prefix-to-uri bindings that are in scope. This may include
         * a binding to the default namespace (represented by a prefix of ""). It will never include
         * "undeclarations" - that is, the namespace URI will never be empty; the effect of an undeclaration
         * is to remove a binding from the in-scope namespaces, not to add anything.</p>
         * <p>For a node other than an element, returns null.</p>
         *
         * @return the in-scope namespaces for an element, or null for any other kind of node.
         */
        @Override
        public NamespaceMap getAllNamespaces() {
            return null;
        }

        /**
         * Get the typed value.
         *
         * @return the typed value. If requireSingleton is set to true, the result will always be an
         *         AtomicValue. In other cases it may be a Value representing a sequence whose items are atomic
         *         values.
         * @since 8.5
         */

        /*@NotNull*/
        @Override
        public AtomicSequence atomize() throws XPathException {
            return new UntypedAtomicValue(text);
        }

        /**
         * Return an enumeration over the nodes reached by the given axis from this node
         *
         * @param axisNumber the axis to be iterated over
         * @return a AxisIterator that scans the nodes reached by the axis in turn.
         */

        /*@NotNull*/
        @Override
        public AxisIterator iterateAxis(int axisNumber) {
            switch (axisNumber) {
                case AxisInfo.ANCESTOR:
                case AxisInfo.PARENT:
                case AxisInfo.PRECEDING_OR_ANCESTOR:
                    return SingleNodeIterator.makeIterator(TextFragmentValue.this);

                case AxisInfo.ANCESTOR_OR_SELF:
                    NodeInfo[] nodes = {this, TextFragmentValue.this};
                    return new ArrayIterator.OfNodes(nodes);

                case AxisInfo.ATTRIBUTE:
                case AxisInfo.CHILD:
                case AxisInfo.DESCENDANT:
                case AxisInfo.FOLLOWING:
                case AxisInfo.FOLLOWING_SIBLING:
                case AxisInfo.NAMESPACE:
                case AxisInfo.PRECEDING:
                case AxisInfo.PRECEDING_SIBLING:
                    return EmptyIterator.ofNodes();

                case AxisInfo.SELF:
                case AxisInfo.DESCENDANT_OR_SELF:
                    return SingleNodeIterator.makeIterator(this);

                default:
                    throw new IllegalArgumentException("Unknown axis number " + axisNumber);
            }
        }


        /**
         * Return an enumeration over the nodes reached by the given axis from this node
         *
         * @param axisNumber the axis to be iterated over
         * @param nodeTest   A condition to be matched by the returned nodes
         * @return a AxisIterator that scans the nodes reached by the axis in turn.
         */

        /*@NotNull*/
        @Override
        public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {
            switch (axisNumber) {
                case AxisInfo.ANCESTOR:
                case AxisInfo.PARENT:
                case AxisInfo.PRECEDING_OR_ANCESTOR:
                    return Navigator.filteredSingleton(TextFragmentValue.this, nodeTest);

                case AxisInfo.ANCESTOR_OR_SELF:
                    boolean matchesDoc = nodeTest.test(TextFragmentValue.this);
                    boolean matchesText = nodeTest.test(this);
                    if (matchesDoc && matchesText) {
                        NodeInfo[] nodes = {this, TextFragmentValue.this};
                        return new ArrayIterator.OfNodes(nodes);
                    } else if (matchesDoc /* && !matchesText */) {
                        return SingleNodeIterator.makeIterator(TextFragmentValue.this);
                    } else if (matchesText /* && !matchesDoc */) {
                        return SingleNodeIterator.makeIterator(this);
                    } else {
                        return EmptyIterator.ofNodes();
                    }

                case AxisInfo.ATTRIBUTE:
                case AxisInfo.CHILD:
                case AxisInfo.DESCENDANT:
                case AxisInfo.FOLLOWING:
                case AxisInfo.FOLLOWING_SIBLING:
                case AxisInfo.NAMESPACE:
                case AxisInfo.PRECEDING:
                case AxisInfo.PRECEDING_SIBLING:
                    return EmptyIterator.ofNodes();

                case AxisInfo.SELF:
                case AxisInfo.DESCENDANT_OR_SELF:
                    return Navigator.filteredSingleton(this, nodeTest);

                default:
                    throw new IllegalArgumentException("Unknown axis number " + axisNumber);
            }
        }

        /**
         * Find the parent node of this node.
         *
         * @return The Node object describing the containing element or root node.
         */

        /*@NotNull*/
        @Override
        public NodeInfo getParent() {
            return TextFragmentValue.this;
        }

        /**
         * Get the root node
         *
         * @return the NodeInfo representing the root of this tree
         */

        /*@NotNull*/
        @Override
        public NodeInfo getRoot() {
            return TextFragmentValue.this;
        }

        /**
         * Copy the node to a given Outputter
         */

        @Override
        public void copy(/*@NotNull*/ Receiver out, int copyOptions, Location locationId)
                throws XPathException {
            out.characters(text, locationId, ReceiverOption.NONE);
        }
    }

}

