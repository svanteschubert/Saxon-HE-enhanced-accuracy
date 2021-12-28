////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.*;
import org.xml.sax.Locator;

import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;
import java.util.Collections;
import java.util.function.Predicate;

/**
 * The NodeInfo interface represents a node in Saxon's implementation of the XPath 2.0 data model.
 * <p>Note that several NodeInfo objects may represent the same node. To test node identity, the
 * method {@link #equals(Object)} should be used. An exception to this rule applies for
 * document nodes, where the correspondence between document nodes and DocumentInfo objects is one to
 * one. NodeInfo objects are never reused: a given NodeInfo object represents the same node for its entire
 * lifetime.</p>
 * <p>This is the primary interface for accessing trees in Saxon, and it forms part of the public
 * Saxon API. Methods that form part of the public API are (since Saxon 8.4)
 * labelled with a JavaDoc "since" tag: classes and methods that have no such label should not be
 * regarded as stable interfaces.</p>
 * <p>The interface represented by this class is at a slightly higher level than the abstraction described
 * in the W3C data model specification, in that it includes support for the XPath axes, rather than exposing
 * the lower-level properties (such as "parent" and "children") directly. All navigation within trees,
 * except for a few convenience methods, is done by following the axes using the {@link #iterateAxis} method.
 * This allows different implementations of the XPath tree model to implement axis navigation in different ways.
 * Some implementations may choose to use the helper methods provided in class {@link net.sf.saxon.tree.util.Navigator}.</p>
 * <p>Note that the stability of this interface applies to classes that use the interface,
 * not to classes that implement it. The interface may be extended in future to add new methods.</p>
 * <p>New implementations of NodeInfo are advised also to implement the methods in interface
 * ExtendedNodeInfo, which will be moved into this interface at some time in the future.</p>
 *
 * @since 8.4. Extended with three extra methods, previously in ExtendedNodeInfo, in 9.1.
 * In 9.7, DocumentInfo is no longer defined as a sub-interface; it is replaced with TreeInfo,
 * which contains information about any XML node tree, whether or not it is rooted at a document node.
 * In 9.8, default implementations are provided for some of the methods, making it easier to create
 * a new implementation of this interface.
 */

public interface NodeInfo extends Source, Item, Location {

    /**
     * Get information about the tree to which this NodeInfo belongs
     * @return the TreeInfo
     * @since 9.7
     */

    TreeInfo getTreeInfo();

    /**
     * Convenience method to get the Configuration. Always returns the same result as {@code getTreeInfo().getConfiguration()}
     * @return the Configuration to which the tree belongs. The default implementation invokes
     * {@code getTreeInfo().getConfiguration()}.
     * @since 8.4
     */

    default Configuration getConfiguration() {
        return getTreeInfo().getConfiguration();
    }

    /**
     * Get the kind of node. This will be a value such as {@link net.sf.saxon.type.Type#ELEMENT}
     * or {@link net.sf.saxon.type.Type#ATTRIBUTE}. There are seven kinds of node: documents, elements, attributes,
     * text, comments, processing-instructions, and namespaces.
     *
     * @return an integer identifying the kind of node. These integer values are the
     *         same as those used in the DOM
     * @see net.sf.saxon.type.Type
     * @since 8.4
     */

    int getNodeKind();

    /**
     * Determine whether this is the same node as another node.
     * <p>Note that two different NodeInfo instances can represent the same conceptual node.
     * Therefore the "==" operator should not be used to test node identity. The equals()
     * method should give the same result as isSameNodeInfo(), but since this rule was introduced
     * late it might not apply to all implementations.</p>
     * <p>Note: a.isSameNodeInfo(b) if and only if generateId(a)==generateId(b).</p>
     * <p>This method has the same semantics as isSameNode() in DOM Level 3, but
     * works on Saxon NodeInfo objects rather than DOM Node objects.</p>
     * <p>From Saxon 9.9 this method has a default implementation that invokes {@code equals()},
     * and all Saxon implementations of {@code NodeInfo} rely on this default implementation.
     * The method is retained for backwards compatibility, but applications are advised to
     * use the {@code equals()} method in preference, and user-defined implementations of
     * {@code NodeInfo} are advised to rely on the default implementation. In particular,
     * implementations of {@code equals()} should not invoke {@code isSameNodeInfo}.</p>
     *
     * @param other the node to be compared with this node
     * @return true if this NodeInfo object and the supplied NodeInfo object represent
     *         the same node in the tree. The default implementation returns
     *         {@code equals(other)}
     */

    default boolean isSameNodeInfo(NodeInfo other) {
        return equals(other);
    }

    /**
     * The equals() method compares nodes for identity. It is defined to give the same result
     * as isSameNodeInfo().
     *
     * @param other the node to be compared with this node
     * @return true if this NodeInfo object and the supplied NodeInfo object represent
     *         the same node in the tree.
     * @since 8.7 Previously, the effect of the equals() method was not defined. Callers
     *        should therefore be aware that third party implementations of the NodeInfo interface may
     *        not implement the correct semantics. It is safer to use isSameNodeInfo() for this reason.
     *        The equals() method has been defined because it is useful in contexts such as a Java Set or HashMap.
     */

    boolean equals(Object other);

    /**
     * The hashCode() method obeys the contract for hashCode(): that is, if two objects are equal
     * (represent the same node) then they must have the same hashCode()
     *
     * @since 8.7 Previously, the effect of the equals() and hashCode() methods was not defined. Callers
     *        should therefore be aware that third party implementations of the NodeInfo interface may
     *        not implement the correct semantics.
     */

    int hashCode();

    /**
     * Get the System ID for the node. Note this is not the
     * same as the base URI: the base URI can be modified by xml:base, but
     * the system ID cannot. The base URI is used primarily for resolving
     * relative URIs within the content of the document. The system ID is
     * used primarily in conjunction with a line number, for identifying the
     * location of elements within the source XML, in particular when errors
     * are found. For a document node, the System ID represents the value of
     * the document-uri property as defined in the XDM data model.
     *
     * @return the System Identifier of the entity in the source document
     *         containing the node, or null if not known or not applicable.
     * @since 8.4
     */

    /*@Nullable*/
    @Override
    String getSystemId();

    /**
     * Get the Public ID of the entity containing the node. This method
     * is provided largely so that NodeInfo can act as a {@link Locator}
     * or {@link SourceLocator}, and many implementations return null.
     * @return the Public Identifier of the entity in the source document
     * containing the node, or null if not known or not applicable.
     * The default implementation returns null.
     * @since 9.7
     */

    @Override
    default String getPublicId() {
        return null;
    }

    /**
     * Get the Base URI for the node, that is, the URI used for resolving a relative URI contained
     * in the node. This will be the same as the System ID unless xml:base has been used. Where the
     * node does not have a base URI of its own, the base URI of its parent node is returned.
     *
     * @return the base URI of the node. This may be null if the base URI is unknown, including the case
     *         where the node has no parent.
     * @since 8.4
     */

    String getBaseURI();

    /**
     * Get line number. Line numbers are not maintained by default, except for
     * stylesheets and schema documents. Line numbering can be requested using the
     * -l option on the command line, or by setting options on the TransformerFactory
     * or the Configuration before the source document is built.
     * <p>The granularity of line numbering is normally the element level: for other nodes
     * such as text nodes and attributes, the line number of the parent element will normally be returned.</p>
     * <p>In the case of a tree constructed by taking input from a SAX parser, the line number will reflect the
     * SAX rules: that is, the line number of an element is the line number where the start tag ends. This
     * may be a little confusing where elements have many attributes spread over multiple lines, or where
     * single attributes (as can easily happen with XSLT 2.0 stylesheets) occupy several lines.</p>
     * <p>In the case of a tree constructed by a stylesheet or query, the line number may reflect the line in
     * the stylesheet or query that caused the node to be constructed.</p>
     * <p>The line number can be read from within an XPath expression using the Saxon extension function
     * saxon:line-number()</p>
     *
     * @return the line number of the node in its original source document; or
     *         -1 if not available. The default implementation returns -1.
     * @since 8.4
     */

    @Override
    default int getLineNumber() {
        return -1;
    }

    /**
     * Get column number. Column numbers are not maintained by default. Column numbering
     * can be requested in the same way as line numbering; but a tree implementation can ignore
     * the request.
     * <p>The granularity of column numbering is normally the element level: for other nodes
     * such as text nodes and attributes, the line number of the parent element will normally be returned.</p>
     * <p>In the case of a tree constructed by taking input from a SAX parser, the column number will reflect the
     * SAX rules: that is, the column number of an element is the column number where the start tag ends. This
     * may be a little confusing where elements have many attributes spread over multiple lines, or where
     * single attributes (as can easily happen with XSLT 2.0 stylesheets) occupy several lines.</p>
     * <p>In the case of a tree constructed by a stylesheet or query, the column number may reflect the line in
     * the stylesheet or query that caused the node to be constructed.</p>
     * <p>The column number can be read from within an XPath expression using the Saxon extension function
     * saxon:column-number()</p>
     *
     * @return the column number of the node in its original source document; or
     *         -1 if not available. The default implementation returns -1.
     * @since 9.1
     */

    @Override
    default int getColumnNumber() {
        return -1;
    }

    /**
     * Determine the relative position of this node and another node, in document order.
     * <p>The other node must always be in the same tree; the effect of calling this method
     * when the two nodes are in different trees is undefined. To obtain a global ordering
     * of nodes, the application should first compare the result of getDocumentNumber(),
     * and only if the document number is the same should compareOrder() be called.</p>
     *
     * @param other The other node, whose position is to be compared with this
     *              node
     * @return -1 if this node precedes the other node, +1 if it follows the
     *         other node, or 0 if they are the same node. (In this case,
     *         isSameNode() will always return true, and the two nodes will
     *         produce the same result for generateId())
     * @since 8.4
     */

    int compareOrder(NodeInfo other);

    /**
     * Return the string value of the node as defined in the XPath data model.
     * <p>The interpretation of this depends on the type
     * of node. For an element it is the accumulated character content of the element,
     * including descendant elements.</p>
     * <p>This method returns the string value as if the node were untyped. Unlike the string value
     * accessor in the XPath 2.0 data model, it does not report an error if the element has a complex
     * type, instead it returns the concatenation of the descendant text nodes as it would if the element
     * were untyped.</p>
     *
     * @return the string value of the node
     * @since 8.4
     */

    @Override
    String getStringValue();

    /**
     * Ask whether this NodeInfo implementation holds a fingerprint identifying the name of the
     * node in the NamePool. If the answer is true, then the {@link #getFingerprint} method must
     * return the fingerprint of the node. If the answer is false, then the {@link #getFingerprint}
     * method should throw an {@code UnsupportedOperationException}. In the case of unnamed nodes
     * such as text nodes, the result can be either true (in which case getFingerprint() should
     * return -1) or false (in which case getFingerprint may throw an exception).
     * @return true if the implementation of this node provides fingerprints.
     * @since 9.8; previously Saxon relied on using <code>FingerprintedNode</code> as a marker interface.
     */

    boolean hasFingerprint();

    /**
     * Get fingerprint. The fingerprint is a coded form of the expanded name
     * of the node: two nodes
     * with the same name code have the same namespace URI and the same local name.
     * The fingerprint contains no information about the namespace prefix. For a name
     * in the null namespace, the fingerprint is the same as the name code.
     *
     * @return an integer fingerprint; two nodes with the same fingerprint have
     * the same expanded QName. For unnamed nodes (text nodes, comments, document nodes,
     * and namespace nodes for the default namespace), returns -1.
     * @throws UnsupportedOperationException if this kind of node does not hold
     * namepool fingerprints (specifically, if {@link #hasFingerprint()} returns false).
     * @since 8.4 (moved into FingerprintedNode at 9.7; then back into NodeInfo at 9.8).
     */

    int getFingerprint();

    /**
     * Get the local part of the name of this node. This is the name after the ":" if any.
     *
     * @return the local part of the name. For an unnamed node, returns "". Unlike the DOM
     *         interface, this returns the full name in the case of a non-namespaced name.
     * @since 8.4
     */

    String getLocalPart();

    /**
     * Get the URI part of the name of this node. This is the URI corresponding to the
     * prefix, or the URI of the default namespace if appropriate.
     *
     * @return The URI of the namespace of this node. For an unnamed node,
     *         or for an element or attribute that is not in a namespace, or for a processing
     *         instruction, returns an empty string.
     * @since 8.4
     */

    String getURI();

    /**
     * Get the display name of this node, in the form of a lexical QName.
     * For elements and attributes this is [prefix:]localname.
     * For unnamed nodes, it is an empty string.
     *
     * @return The display name of this node. For a node with no name, returns
     *         an empty string.
     * @since 8.4
     */

    String getDisplayName();

    /**
     * Get the prefix of the name of the node. This is defined only for elements and attributes.
     * If the node has no prefix, or for other kinds of node, returns a zero-length string.
     *
     * @return The prefix of the name of the node.
     * @since 8.4
     */

    String getPrefix();

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
     *         <p>The default implementation returns {@link BuiltInAtomicType#UNTYPED_ATOMIC} for attribute
     *         nodes, {@link Untyped#getInstance} for element and document nodes, and null otherwise</p>
     * @since 9.4
     */

    default SchemaType getSchemaType() {
        switch (getNodeKind()) {
            case Type.ATTRIBUTE:
                return BuiltInAtomicType.UNTYPED_ATOMIC;
            case Type.DOCUMENT:
            case Type.ELEMENT:
                return Untyped.getInstance();
            default:
                return null;
        }
    }

    /**
     * Bit setting in the returned type annotation indicating a DTD_derived type on an attribute node
     */

    int IS_DTD_TYPE = 1 << 30;

    /**
     * Bit setting for use alongside a type annotation indicating that the is-nilled property is set
     */

    int IS_NILLED = 1 << 29;


    /**
     * Get the typed value. This will either be a single AtomicValue or a value whose items are
     * atomic values.
     * @return the typed value of the node
     * @throws XPathException if the node has no typed value, for example if
     *                        it is an element node with element-only content
     * @since 8.5. Changed in 9.5 to return the new type AtomicSequence.
     */

    @Override
    AtomicSequence atomize() throws XPathException;

    /**
     * Get the NodeInfo object representing the parent of this node
     *
     * @return the parent of this node; null if this node has no parent
     * @since 8.4
     */

    /*@Nullable*/
    NodeInfo getParent();

    /**
     * Return an iteration over all the nodes reached by the given axis from this node
     *
     * @param axisNumber an integer identifying the axis; one of the constants
     *                   defined in class {@link AxisInfo}
     * @return an AxisIterator that delivers the nodes reached by the axis in
     *         turn. The nodes are returned in axis order (document order for a forwards
     *         axis, reverse document order for a reverse axis). The default implementation
     *         returns {@code iterateAxis(axisNumber, AnyNodeTest.getInstance()}.
     * @throws UnsupportedOperationException if the namespace axis is
     *                                       requested and this axis is not supported for this implementation.
     * @see AxisInfo
     * @since 8.4
     */

    default AxisIterator iterateAxis(int axisNumber) {
        return iterateAxis(axisNumber, AnyNodeTest.getInstance());
    }


    /**
     * Return an iteration over all the nodes reached by the given axis from this node
     * that match a given NodeTest
     *
     * @param axisNumber an integer identifying the axis; one of the constants
     *                   defined in class {@link AxisInfo}
     * @param nodeTest   A condition to be satisfied by the returned nodes; nodes
     *                   that do not satisfy this condition are not included in the result
     * @return an AxisIterator that delivers the nodes reached by the axis in
     *         turn.  The nodes are returned in axis order (document order for a forwards
     *         axis, reverse document order for a reverse axis).
     * @throws UnsupportedOperationException if the namespace axis is
     *                                       requested and this axis is not supported for this implementation.
     * @see AxisInfo
     * @since 8.4. Changed in 10.0 to accept any {@code Predicate<NodeInfo>} as the second argument. It is still
     * possible to supply an {@link NodeTest}, because {@code NodeTest} implements {@code Predicate<NodeInfo>}.
     */

    AxisIterator iterateAxis(int axisNumber, Predicate<? super NodeInfo> nodeTest);

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

    /*@Nullable*/
    String getAttributeValue(/*@NotNull*/ String uri, /*@NotNull*/ String local);

    /**
     * Get the root node of the tree containing this node
     *
     * @return the NodeInfo representing the top-level ancestor of this node.
     *         This will not necessarily be a document node. If this node has no parent,
     *         then the method returns this node.
     * @since 8.4
     */

    NodeInfo getRoot();

    /**
     * Determine whether the node has any children.
     * <p>Note: the result is equivalent to
     * <code>iterateAxis(Axis.CHILD).next() != null</code></p>
     *
     * @return True if the node has one or more children
     * @since 8.4
     */

    boolean hasChildNodes();

    /**
     * Return the sequence of children of this node, as an {@code Iterable}. This
     * method is designed to allow iteration over the children in a Java "for each" loop,
     * in the form <code>for (NodeInfo child : children()) {...}</code>
     * @return the children of the node, as an {@code Iterable}.
     */

    default Iterable<? extends NodeInfo> children() {
        if (hasChildNodes()) {
            NodeInfo parent = this;
            return () -> parent.iterateAxis(AxisInfo.CHILD).asIterator();
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Return the sequence of children of this node, filtered by a supplied predicate,
     * as an {@code Iterable}.
     * @param filter a condition that the selected children must satisfy
     * @return the children of the node, as an {@code Iterable}.
     */

    default Iterable<? extends NodeInfo> children(Predicate<? super NodeInfo> filter) {
        if (hasChildNodes()) {
            NodeInfo parent = this;
            return () -> parent.iterateAxis(AxisInfo.CHILD, filter).asIterator();
        } else {
            return Collections.emptyList();
        }
    }


    default AttributeMap attributes() {
        AttributeMap atts = EmptyAttributeMap.getInstance();
        if (getNodeKind() == Type.ELEMENT) {
            AxisIterator iter = iterateAxis(AxisInfo.ATTRIBUTE);
            NodeInfo attr;
            while ((attr = iter.next()) != null) {
                atts = atts.put(new AttributeInfo(NameOfNode.makeName(attr), (SimpleType) attr.getSchemaType(),
                                                  attr.getStringValue(),
                                                  Loc.NONE,
                                                  ReceiverOption.NONE));
            }
        }
        return atts;
    }

    /**
     * Construct a character string that uniquely identifies this node.
     * Note: a.isSameNode(b) if and only if generateId(a)==generateId(b)
     *
     * @param buffer a buffer which will be updated to hold a string
     *               that uniquely identifies this node, across all documents.
     * @since 8.7
     *        <p>Changed in Saxon 8.7 to generate the ID value in a client-supplied buffer</p>
     */

    void generateId(FastStringBuffer buffer);

    /**
     * Copy this node to a given Receiver.
     * <p>This method is primarily for internal use. It should not be considered a stable
     * part of the Saxon API.</p>
     * <p>The default implementation invokes {@code Navigator.copy(this, out, copyOptions, locationId);} which
     * is always adequate.</p>
     *
     * @param out         the Receiver to which the node should be copied. It is the caller's
     *                    responsibility to ensure that this Receiver is open before the method is called
     *                    (or that it is self-opening), and that it is closed after use.
     * @param copyOptions a selection of the options defined in {@link CopyOptions}
     * @param locationId  If non-null, identifies the location of the instruction
     *                    that requested this copy. If zero, indicates that the location information
     *                    is not available
     * @throws XPathException if any downstream error occurs
     * @throws IllegalArgumentException if the node is an attribute or namespace node
     */

    default void copy(Receiver out, int copyOptions, Location locationId) throws XPathException {
        Navigator.copy(this, out, copyOptions, locationId);
    }


    /**
     * Get all namespace declarations and undeclarations defined on this element.
     * <p>This method is intended primarily for internal use. User applications needing
     * information about the namespace context of a node should use <code>iterateAxis(Axis.NAMESPACE)</code>.
     * (However, not all implementations support the namespace axis, whereas all implementations are
     * required to support this method.)</p>
     *
     * <p>In many cases (and in particular with Saxon's TinyTree and LinkedTree implementations),
     * from 10.0 onwards it is more efficient to call {@link #getAllNamespaces()} to get all the in-scope
     * namespaces at once, rather than building up the list be calling {@code getDeclaredNamespaces} on
     * each ancestor node.</p>
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of namespace binding objects representing the namespace declarations and undeclarations present on
     *         this element. For a node other than an element, return null.
     *         If the uri part is "", then this is a namespace undeclaration rather than a declaration.
     *         The XML namespace is never included in the list. If the supplied array is larger than required,
     *         then the first unused entry will be set to null.
     *         <p>
     *         For a node other than an element, the method returns null.</p>
     */

    NamespaceBinding[] getDeclaredNamespaces(/*@Nullable*/ NamespaceBinding[] buffer);

    /**
     * Get all the namespace bindings that are in-scope for this element.
     * <p>For an element return all the prefix-to-uri bindings that are in scope. This may include
     * a binding to the default namespace (represented by a prefix of "").  The map does NOT include
     * the implicit binding of the XML namespace. It will never include
     * "undeclarations" - that is, the namespace URI will never be empty; the effect of an undeclaration
     * is to remove a binding from the in-scope namespaces, not to add anything.</p>
     * <p>For a node other than an element, returns null.</p>
     * @return the in-scope namespaces for an element, or null for any other kind of node.
     */

    NamespaceMap getAllNamespaces();

    /**
     * Ask whether this node has the is-id property
     *
     * @return true if the node is an ID.  The default
     * implementation returns false.
     */

    default boolean isId() {
        return false;
    }

    /**
     * Ask whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute. The default
     * implementation returns false.
     */

    default boolean isIdref() {
        return false;
    }

    /**
     * Ask whether the node has the is-nilled property
     *
     * @return true if the node has the is-nilled property. The default
     * implementation returns false.
     */

    default boolean isNilled() {
        return false;
    }

    /**
     * Ask whether this is a node in a streamed document
     * @return true if the node is in a document being processed using streaming.
     * The default implementation returns false.
     */

    @Override
    default boolean isStreamed() {
        return false;
    }

    /**
     * Provide a short string showing the contents of the item, suitable
     * for use in error messages
     *
     * @return a depiction of the item suitable for use in error messages.
     * The default implementation returns:
     * <ul>
     *     <li><code>document-node()</code> for a document node</li>
     *     <li><code>&lt;p:local/&gt;</code> for an element node</li>
     *     <li><code>@p:local</code> for an attribute node</li>
     *     <li><code>text("In a hole...")</code> for a text node</li>
     *     <li><code>&lt;--You are old, ...--&gt;</code> for a comment node</li>
     *     <li><code>&lt;?pi?&gt;</code> for a processing instruction node</li>
     *     <li><code>xmlns:p=uri</code> for a namespace node</li>
     * </ul>
     */

    @Override
    default String toShortString() {
        switch (getNodeKind()) {
            case Type.DOCUMENT:
                return "document-node()";
            case Type.ELEMENT:
                return "<" + getDisplayName() + "/>";
            case Type.ATTRIBUTE:
                return "@" + getDisplayName();
            case Type.TEXT:
                return "text(\"" + Err.truncate30(getStringValue()) + "\")";
            case Type.COMMENT:
                return "<!--" + Err.truncate30(getStringValue()) + "-->";
            case Type.PROCESSING_INSTRUCTION:
                return "<?" + getDisplayName() + "?>";
            case Type.NAMESPACE:
                String prefix = getLocalPart();
                return "xmlns" + (prefix.equals("") ? "" : ":" + prefix) + "=\"" + getStringValue() + '"';
            default:
                return "";
        }
    }

    /**
     * Get the genre of this item
     *
     * @return the genre: specifically, {@link Genre#NODE}. The default implementation (which should not
     * be overridden) returns {@link Genre#NODE}.
     */
    @Override
    default Genre getGenre() {
        return Genre.NODE;
    }


}

