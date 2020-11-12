////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.event.CopyInformee;
import net.sf.saxon.event.CopyNamespaceSensitiveException;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.*;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.UntypedAtomicValue;
import net.sf.saxon.z.IntValuePredicate;

import javax.xml.transform.SourceLocator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * An element node in the TinyTree that has no attributes or namespace declarations and that
 * has a single text node child. The element-and-text-node pair are represented by a single
 * entry in the node arrays, but materialize as two separate objects when turned into node
 * objects.
 */

public class TinyTextualElement extends TinyElementImpl {

    private TinyTextualElementText textNode = null;

    public TinyTextualElement(TinyTree tree, int nodeNr) {
        super(tree, nodeNr);
    }

    @Override
    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        return NamespaceBinding.EMPTY_ARRAY;
    }

    @Override
    public NamespaceMap getAllNamespaces() {
        TinyNodeImpl parent = getParent();
        if (parent instanceof TinyElementImpl) {
            return parent.getAllNamespaces();
        } else {
            return NamespaceMap.emptyMap();
        }
    }

    @Override
    public String getAttributeValue(String uri, String local) {
        return null;
    }

    @Override
    public String getAttributeValue(int fp) {
        return null;
    }

    @Override
    public void copy(Receiver receiver, int copyOptions, Location location) throws XPathException {
        boolean typed = CopyOptions.includes(copyOptions, CopyOptions.TYPE_ANNOTATIONS);
        SchemaType type = typed ? getSchemaType() : Untyped.getInstance();
        boolean disallowNamespaceSensitiveContent =
                ((copyOptions & CopyOptions.TYPE_ANNOTATIONS) != 0) &&
                        ((copyOptions & CopyOptions.ALL_NAMESPACES) == 0);
        if (disallowNamespaceSensitiveContent) {
            try {
                checkNotNamespaceSensitiveElement(type, nodeNr);
            } catch (CopyNamespaceSensitiveException e) {
                e.setErrorCode(receiver.getPipelineConfiguration().isXSLT() ? "XTTE0950" : "XQTY0086");
                throw e;
            }
        }

        CopyInformee informee = (CopyInformee) receiver.getPipelineConfiguration().getComponent(CopyInformee.class.getName());
        if (informee != null) {
            Object o = informee.notifyElementNode(this);
            if (o instanceof Location) {
                location = (Location) o;
            }
        }

        NamespaceMap namespaces;
        if ((copyOptions & CopyOptions.ALL_NAMESPACES) != 0) {
            // Don't bother with LOCAL_NAMESPACES because there aren't any
            namespaces = getAllNamespaces();
        } else {
            namespaces = NamespaceMap.emptyMap();
        }
        receiver.startElement(NameOfNode.makeName(this), type, EmptyAttributeMap.getInstance(),
                              namespaces, location, ReceiverOption.NONE);
        receiver.characters(getStringValueCS(), location, ReceiverOption.NONE);
        receiver.endElement();
    }

    @Override
    public boolean hasChildNodes() {
        return true;
    }

    @Override
    public CharSequence getStringValueCS() {
        return TinyTextImpl.getStringValue(tree, nodeNr);
    }

    @Override
    public String getStringValue() {
        return TinyTextImpl.getStringValue(tree, nodeNr).toString();
    }

    @Override
    public AxisIterator iterateAxis(int axisNumber) {
        switch (axisNumber) {
            case AxisInfo.ATTRIBUTE:
                return EmptyIterator.ofNodes();

            case AxisInfo.CHILD:
            case AxisInfo.DESCENDANT:
                return SingleNodeIterator.makeIterator(getTextNode());

            case AxisInfo.DESCENDANT_OR_SELF:
                List<NodeInfo> list = new ArrayList<>(2);
                list.add(this);
                list.add(getTextNode());
                return new ListIterator.OfNodes(list);

            default:
                return super.iterateAxis(axisNumber);
        }
    }

    @Override
    public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {
        switch (axisNumber) {
            case AxisInfo.ATTRIBUTE:
                return EmptyIterator.ofNodes();

            case AxisInfo.CHILD:
            case AxisInfo.DESCENDANT:
                return Navigator.filteredSingleton(getTextNode(), nodeTest);

            case AxisInfo.DESCENDANT_OR_SELF:
                List<NodeInfo> list = new ArrayList<>(2);
                if (nodeTest.test(this)) {
                    list.add(this);
                }
                if (nodeTest.test(getTextNode())) {
                    list.add(getTextNode());
                }
                return new ListIterator.OfNodes(list);

            default:
                return super.iterateAxis(axisNumber, nodeTest);
        }
    }

    @Override
    public boolean isAncestorOrSelf(TinyNodeImpl d) {
        return this.equals(d);
    }

    /**
     * Make an instance of the text node
     *
     * @return the new or existing instance
     */

    /*@Nullable*/
    public TinyTextualElementText getTextNode() {
        if (textNode == null) {
            textNode = new TinyTextualElementText();
        }
        return textNode;
    }

    /**
     * Inner class representing the text node; this is created on demand
     */

    public class TinyTextualElementText implements NodeInfo, SourceLocator {

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
            return TinyTextualElement.this.getTreeInfo();
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
            return getStringValueCS().toString();
        }

        /**
         * Get the value of the item as a CharSequence. This is in some cases more efficient than
         * the version of the method that returns a String.
         */

        @Override
        public CharSequence getStringValueCS() {
            return TinyTextualElement.this.getStringValueCS();
        }

        /**
         * Determine whether this is the same node as another node
         *
         * @return true if this Node object and the supplied Node object represent the
         * same node in the tree.
         */

        public boolean equals(Object other) {
            return other instanceof TinyTextualElementText &&
                    getParent().equals(((TinyTextualElementText)other).getParent());
        }

        /**
         * Get a character string that uniquely identifies this node
         */

        @Override
        public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
            TinyTextualElement.this.generateId(buffer);
            buffer.append("T");
        }

        /**
         * Get the system ID for the entity containing the node.
         */

        /*@Nullable*/
        @Override
        public String getSystemId() {
            return TinyTextualElement.this.getSystemId();
        }

        /**
         * Get the base URI for the node. Default implementation for child nodes gets
         * the base URI of the parent node.
         */

        @Override
        public String getBaseURI() {
            return TinyTextualElement.this.getBaseURI();
        }

        /**
         * Determine the relative position of this node and another node, in document order.
         * The other node will always be in the same document.
         *
         * @param other The other node, whose position is to be compared with this node
         * @return -1 if this node precedes the other node, +1 if it follows the other
         * node, or 0 if they are the same node. (In this case, isSameNode() will always
         * return true, and the two nodes will produce the same result for generateId())
         */

        @Override
        public int compareOrder(NodeInfo other) {
            if (other.equals(this)) {
                return 0;
            } else if (other.equals(getParent())) {
                return 1;
            } else {
                return getParent().compareOrder(other);
            }
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
         * an element or attribute in the default namespace, return an empty string.
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
         * For a node with no name, return an empty string.
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
         * For a node with no name, return "".
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
         * <code>false</code> otherwise.
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
         * if this node is not an element.
         * @since 9.4
         */
        @Override
        public String getAttributeValue(/*@NotNull*/ String uri, /*@NotNull*/ String local) {
            return null;
        }

        /**
         * Get line number
         *
         * @return the line number of the node in its original source document; or
         * -1 if not available
         */

        @Override
        public int getLineNumber() {
            return getParent().getLineNumber();
        }

        private IntPredicate isNewline = new IntValuePredicate(10);

        /**
         * Return the character position where the current document event ends.
         * <p><strong>Warning:</strong> The return value from the method
         * is intended only as an approximation for the sake of error
         * reporting; it is not intended to provide sufficient information
         * to edit the character content of the original XML document.</p>
         * <p>The return value is an approximation of the column number
         * in the document entity or external parsed entity where the
         * markup that triggered the event appears.</p>
         *
         * @return The column number, or -1 if none is available.
         * @see #getLineNumber
         */
        @Override
        public int getColumnNumber() {
            return getParent().getColumnNumber();
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
         * validation (defaulting to xs:untyped and xs:untypedAtomic in the absence of schema
         * validation). For comments, text nodes, processing instructions, and namespaces: null.
         * For document nodes, either xs:untyped if the document has not been validated, or
         * xs:anyType if it has.
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
         * this element. For a node other than an element, return null. Otherwise, the returned array is a
         * sequence of namespace codes, whose meaning may be interpreted by reference to the name pool. The
         * top half word of each namespace code represents the prefix, the bottom half represents the URI.
         * If the bottom half is zero, then this is a namespace undeclaration rather than a declaration.
         * The XML namespace is never included in the list. If the supplied array is larger than required,
         * then the first unused entry will be set to -1.
         * <p>For a node other than an element, the method returns null.</p>
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
         * AtomicValue. In other cases it may be a Value representing a sequence whose items are atomic
         * values.
         * @since 8.5
         */

        /*@NotNull*/
        @Override
        public AtomicSequence atomize() throws XPathException {
            return new UntypedAtomicValue(getStringValueCS());
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
                    return TinyTextualElement.this.iterateAxis(AxisInfo.ANCESTOR_OR_SELF);

                case AxisInfo.PRECEDING_OR_ANCESTOR:
                    return new Navigator.PrecedingEnumeration(this, true);

                case AxisInfo.ANCESTOR_OR_SELF:
                    return new PrependAxisIterator(this, getParent().iterateAxis(AxisInfo.ANCESTOR_OR_SELF));

                case AxisInfo.FOLLOWING:
                    return new Navigator.FollowingEnumeration(this);

                case AxisInfo.PRECEDING:
                    return new Navigator.PrecedingEnumeration(this, false);

                case AxisInfo.PARENT:
                    return SingleNodeIterator.makeIterator(getParent());

                case AxisInfo.ATTRIBUTE:
                case AxisInfo.CHILD:
                case AxisInfo.DESCENDANT:
                case AxisInfo.FOLLOWING_SIBLING:
                case AxisInfo.NAMESPACE:
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
         * @param nodeTest   A pattern to be matched by the returned nodes
         * @return a AxisIterator that scans the nodes reached by the axis in turn.
         */

        /*@NotNull*/
        @Override
        public AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest) {
            switch (axisNumber) {
                case AxisInfo.ANCESTOR:
                    return getParent().iterateAxis(AxisInfo.ANCESTOR_OR_SELF, nodeTest);

                case AxisInfo.PRECEDING_OR_ANCESTOR:
                    return new Navigator.AxisFilter(
                            new Navigator.PrecedingEnumeration(this, true),
                            nodeTest);

                case AxisInfo.ANCESTOR_OR_SELF:
                    return new Navigator.AxisFilter(
                            new PrependAxisIterator(this, getParent().iterateAxis(AxisInfo.ANCESTOR_OR_SELF)),
                            nodeTest);

                case AxisInfo.FOLLOWING:
                    return new Navigator.AxisFilter(
                            new Navigator.FollowingEnumeration(this),
                            nodeTest);

                case AxisInfo.PRECEDING:
                    return new Navigator.AxisFilter(
                            new Navigator.PrecedingEnumeration(this, false),
                            nodeTest);

                case AxisInfo.PARENT:
                    return Navigator.filteredSingleton(getParent(), nodeTest);

                case AxisInfo.ATTRIBUTE:
                case AxisInfo.CHILD:
                case AxisInfo.DESCENDANT:
                case AxisInfo.FOLLOWING_SIBLING:
                case AxisInfo.NAMESPACE:
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
            return TinyTextualElement.this;
        }

        /**
         * Get the root node
         *
         * @return the NodeInfo representing the root of this tree
         */

        /*@NotNull*/
        @Override
        public NodeInfo getRoot() {
            return getParent().getRoot();
        }

        /**
         * Copy the node to a given Outputter
         */

        @Override
        public void copy(/*@NotNull*/ Receiver out, int copyOptions, Location locationId)
                throws XPathException {
            out.characters(getStringValueCS(), locationId, ReceiverOption.NONE);
        }

    }


}

