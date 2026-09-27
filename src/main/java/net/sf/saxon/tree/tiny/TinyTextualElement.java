////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.event.CopyNamespaceSensitiveException;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntIterator;

import javax.xml.transform.SourceLocator;

/**
 * An element node in the TinyTree that has no attributes or namespace declarations and that
 * has a single text node child. The element-and-text-node pair are represented by a single
 * entry in the node arrays, but materialize as two separate objects when turned into node
 * objects. This object represents the element node; the nested class {@link TinyTextualElementText}
 * represents the child text node
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
    public String getAttributeValue(NamespaceUri uri, String local) {
        return null;
    }

    @Override
    public String getAttributeValue(int fp) {
        return null;
    }

    @Override
    public void copy(Receiver receiver, int copyOptions, Location location) throws XPathException {
        boolean typed = CopyOptions.includes(copyOptions, CopyOptions.TYPE_ANNOTATIONS);
        SchemaType type;
        type = typed ? getSchemaType() : Untyped.INSTANCE;
        boolean disallowNamespaceSensitiveContent =
                ((copyOptions & CopyOptions.TYPE_ANNOTATIONS) != 0) &&
                        ((copyOptions & CopyOptions.ALL_NAMESPACES) == 0);
        if (disallowNamespaceSensitiveContent) {
            try {
                checkNotNamespaceSensitiveElement(type, nodeNr);
            } catch (CopyNamespaceSensitiveException e) {
                throw e.withErrorCode(receiver.getPipelineConfiguration().isXSLT() ? "XTTE0950" : "XQTY0086");
            }
        }

        java.util.function.Function<NodeInfo, Object> informee = receiver.getPipelineConfiguration().getCopyInformee();
        if (informee != null) {
            Object o = informee.apply(this);
            if (o instanceof Location) {
                location = (Location) o;
            }
        }

        NamespaceMap namespaces;
        if ((copyOptions & CopyOptions.ALL_NAMESPACES) != 0) {
            // Don't bother with LOCAL_NAMESPACES because there aren't any
            namespaces = getAllNamespaces();
        } else if (!getNamespaceUri().isEmpty()) {  // Bug 5616
            namespaces = NamespaceMap.of(getPrefix(), getNamespaceUri());
        } else {
            namespaces = NamespaceMap.emptyMap();
        }
        receiver.startElement(NameOfNode.makeName(this), type, EmptyAttributeMap.INSTANCE,
                              namespaces, location, ReceiverOption.NONE);
        receiver.characters(getUnicodeStringValue(), location, ReceiverOption.NONE);
        receiver.endElement();
    }

    @Override
    public boolean hasChildNodes() {
        return true;
    }

    @Override
    public UnicodeString getUnicodeStringValue() {
        return TinyTextImpl.getStringValue(tree, nodeNr);
    }

    @Override
    public SequenceIterator iterateChildAxis(NodePredicate nodeTest) {
        return Navigator.filteredSingleton(getTextNode(), nodeTest);
    }

    @Override
    public SequenceIterator iterateDescendantAxis(NodePredicate nodeTest) {
        return Navigator.filteredSingleton(getTextNode(), nodeTest);
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
            textNode = new TinyTextualElementText(this);
        }
        return textNode;
    }

    /**
     * Inner class representing the text node; this is created on demand
     */

    public static class TinyTextualElementText implements NodeInfo, SourceLocator {

        private final TinyTextualElement element;

        public TinyTextualElementText(TinyTextualElement element) {
            this.element = element;
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
         * Get information about the tree to which this NodeInfo belongs
         *
         * @return the TreeInfo
         * @since 9.7
         */
        @Override
        public TreeInfo getTreeInfo() {
            return element.getTreeInfo();
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
         * Get the value of the item as a UnicodeString.
         * @return the string value of the text node
         */

        @Override
        public UnicodeString getUnicodeStringValue() {
            return element.getUnicodeStringValue();
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

        @Override
        public int hashCode() {
            return getParent().hashCode() ^ 0x01010101;
        }

        /**
         * Get a character string that uniquely identifies this node
         */

        @Override
        public void generateId(/*@NotNull*/ StringBuilder buffer) {
            element.generateId(buffer);
            buffer.append("T");
        }

        /**
         * Get the system ID for the entity containing the node.
         */

        /*@Nullable*/
        @Override
        public String getSystemId() {
            return element.getSystemId();
        }

        /**
         * Get the base URI for the node. Default implementation for child nodes gets
         * the base URI of the parent node.
         */

        @Override
        public String getBaseURI() {
            return element.getBaseURI();
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
        public int compareOrder(GNode other) {
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
        public NamespaceUri getNamespaceUri() {
            return NamespaceUri.NULL;
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
        public String getAttributeValue(NamespaceUri uri, String local) {
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
            // The line number is that of the END of the text node
            // TODO (bug 6405): this calculation is only correct if using
            //  the position data returned by a SAX parser, it will give wrong
            //  answer if the position data comes from a different parser, for example
            //  the Microsoft parser used in SaxonCS.
            int line = getParent().getLineNumber();
            UnicodeString value = getUnicodeStringValue();
            IntIterator codepoints = value.codePoints();
            while (codepoints.hasNext()) {
                int cp = codepoints.next();
                if (cp == 10) {
                    line++;
                }
            }
            return line;
        }


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
            // The column number is that of the END of the text node
            // TODO: as per getLineNumber() above
            int col = getParent().getColumnNumber() + 1; // The end of the element's start tag
            UnicodeString value = getUnicodeStringValue();
            IntIterator codepoints = value.codePoints();
            while (codepoints.hasNext()) {
                int cp = codepoints.next();
                if (cp == 10) {
                    col = 1;
                }
            }
            return col;
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
         * Get all namespace declarations and undeclarations defined on this element.
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
            return StringValue.makeUntypedAtomic(getUnicodeStringValue());
        }


        @Override
        public SequenceIterator iterateAncestorAxis(NodePredicate predicate) {
            return element.iterateAncestorOrSelfAxis(predicate);
        }

        /**
         * Get an iterator over the child axis, starting at this node; the nodes will
         * be in document order.
         *
         * @param predicate a condition that the nodes must satisfy, or null
         * @return the required iterator
         */
        @Override
        public SequenceIterator iterateChildAxis(NodePredicate predicate) {
            return EmptyIterator.INSTANCE;
        }

        /**
         * Get an iterator over the descendant axis, starting at this node; the nodes will
         * be in document order.
         *
         * @param predicate a condition that the nodes must satisfy, or null
         * @return the required iterator
         */
        @Override
        public SequenceIterator iterateDescendantAxis(NodePredicate predicate) {
            return EmptyIterator.INSTANCE;
        }

        /**
         * Get an iterator over the following-sibling axis, starting at this node; the nodes will
         * be in document order.
         *
         * @param predicate a condition that the nodes must satisfy, or null
         * @return the required iterator
         */
        @Override
        public SequenceIterator iterateFollowingSiblingAxis(NodePredicate predicate) {
            return EmptyIterator.INSTANCE;
        }

        /**
         * Get an iterator over the preceding-sibling axis, starting at this node; the nodes will
         * be in reverse document order.
         *
         * @param predicate a condition that the nodes must satisfy, or null
         * @return the required iterator
         */
        @Override
        public SequenceIterator iteratePrecedingSiblingAxis(NodePredicate predicate) {
            return EmptyIterator.INSTANCE;
        }


        /**
         * Find the parent node of this node.
         *
         * @return The Node object describing the containing element or root node.
         */

        /*@NotNull*/
        @Override
        public NodeInfo getParent() {
            return element;
        }

        /**
         * Get the root node
         *
         * @return the NodeInfo representing the root of this tree
         */

        /*@NotNull*/
        @Override
        public NodeInfo getRoot() {
            return element.getRoot();
        }

        /**
         * Copy the node to a given Outputter
         */

        @Override
        public void copy(/*@NotNull*/ Receiver out, int copyOptions, Location locationId)
                throws XPathException {
            out.characters(getUnicodeStringValue(), locationId, ReceiverOption.NONE);
        }

    }


}

