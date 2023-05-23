////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.CopyInformee;
import net.sf.saxon.event.CopyNamespaceSensitiveException;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * ElementImpl implements an element node in the Linked tree model. Subclasses of ElementImpl,
 * for holding particular kinds of element, can be defined by using a {@link NodeFactory}
 * registered with the {@link LinkedTreeBuilder} used to construct the tree.
 */


public class ElementImpl extends ParentNodeImpl implements NamespaceResolver {

    private NodeName nodeName;
    private SchemaType type = Untyped.getInstance();
    private AttributeMap attributeMap;      // this excludes namespace attributes
    private NamespaceMap namespaceMap = NamespaceMap.emptyMap();

    /**
     * Construct an empty ElementImpl
     */

    public ElementImpl() {
        this.attributeMap = EmptyAttributeMap.getInstance();
    }

    /**
     * Set the attribute list
     *
     * @param atts the list of attributes of this element (not including namespace attributes)
     */

    @Override
    public void setAttributes(AttributeMap atts) {
        this.attributeMap = atts;
    }

    /**
     * Set the node name
     *
     * @param name the node name
     */

    public void setNodeName(NodeName name) {
        this.nodeName = name;
    }

    /**
     * Initialise a new ElementImpl with an element name
     *
     * @param elemName       Integer representing the element name, with namespaces resolved
     * @param elementType    the schema type of the element node
     * @param atts           The attribute list: always null
     * @param parent         The parent node
     * @param sequenceNumber Integer identifying this element within the document
     */

    public void initialise(/*@NotNull*/ NodeName elemName, SchemaType elementType, AttributeMap atts, /*@NotNull*/ NodeInfo parent,
                                        int sequenceNumber) {
        this.nodeName = elemName;
        this.type = elementType;
        setRawParent((ParentNodeImpl) parent);
        setRawSequenceNumber(sequenceNumber);
        attributeMap = atts;
    }

    /**
     * Get the name of the node. Returns null for an unnamed node
     *
     * @return the name of the node
     */
    @Override
    public NodeName getNodeName() {
        return nodeName;
    }

    /**
     * Set location information for this node
     *
     * @param systemId the base URI
     * @param line     the line number if known
     * @param column   the column number if known
     */

    public void setLocation(String systemId, int line, int column) {
        DocumentImpl root = getRawParent().getPhysicalRoot();
        root.setLineAndColumn(getRawSequenceNumber(), line, column);
        root.setSystemId(getRawSequenceNumber(), systemId);
    }

    /**
     * Set the system ID of this node. This method is provided so that a NodeInfo
     * implements the javax.xml.transform.Source interface, allowing a node to be
     * used directly as the Source of a transformation
     */

    @Override
    public void setSystemId(String uri) {
        getPhysicalRoot().setSystemId(getRawSequenceNumber(), uri);
    }

    /**
     * Get the root node
     */

    @Override
    public NodeInfo getRoot() {
        ParentNodeImpl up = getRawParent();
        if (up == null || (up instanceof DocumentImpl && ((DocumentImpl) up).isImaginary())) {
            return this;
        } else {
            return up.getRoot();
        }
    }

    /**
     * Get the system ID of the entity containing this element node.
     */

    /*@Nullable*/
    @Override
    public final String getSystemId() {
        DocumentImpl root = getPhysicalRoot();
        return root == null ? null : root.getSystemId(getRawSequenceNumber());
    }

    /**
     * Get the base URI of this element node. This will be the same as the System ID unless
     * xml:base has been used.
     */

    @Override
    public String getBaseURI() {
        return Navigator.getBaseURI(this, n -> getPhysicalRoot().isTopWithinEntity((ElementImpl) n));
    }

    /**
     * Determine whether the node has the is-nilled property
     *
     * @return true if the node has the is-nilled property
     */

    @Override
    public boolean isNilled() {
        return getPhysicalRoot().isNilledElement(this);
    }

    /**
     * Set the type annotation on a node. This must only be called when the caller has verified (by validation)
     * that the node is a valid instance of the specified type. The call is ignored if the node is not an element
     * or attribute node.
     *
     * @param type the type annotation
     */

    @Override
    public void setTypeAnnotation(SchemaType type) {
        this.type = type;
    }

    /**
     * Say that the element has the nilled property
     */

    public void setNilled() {
        getPhysicalRoot().addNilledElement(this);
    }

    /**
     * Get the type annotation
     *
     * @return the type annotation of the node
     */

    @Override
    public SchemaType getSchemaType() {
        return type;
    }

    /**
     * Get the line number of the node within its source document entity
     */

    @Override
    public int getLineNumber() {
        DocumentImpl root = getPhysicalRoot();
        if (root == null) {
            return -1;
        } else {
            return root.getLineNumber(getRawSequenceNumber());
        }
    }

    /**
     * Get the line number of the node within its source document entity
     */

    @Override
    public int getColumnNumber() {
        DocumentImpl root = getPhysicalRoot();
        if (root == null) {
            return -1;
        } else {
            return root.getColumnNumber(getRawSequenceNumber());
        }
    }

    /**
     * Get a character string that uniquely identifies this node
     *
     * @param buffer to contain the generated ID
     */

    @Override
    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        int sequence = getRawSequenceNumber();
        if (sequence >= 0) {
            getPhysicalRoot().generateId(buffer);
            buffer.append("e");
            buffer.append(Integer.toString(sequence));
        } else {
            getRawParent().generateId(buffer);
            buffer.append("f");
            buffer.append(Integer.toString(getSiblingPosition()));
        }
    }

    /**
     * Return the kind of node.
     *
     * @return Type.ELEMENT
     */

    @Override
    public final int getNodeKind() {
        return Type.ELEMENT;
    }

    /**
     * Get the attributes of the element
     *
     * @return an attribute map containing the attributes of the element
     */

    @Override
    public AttributeMap attributes() {
        return attributeMap;
    }

    AxisIterator iterateAttributes(Predicate<? super NodeInfo> test) {
        if (attributeMap instanceof AttributeMapWithIdentity) {
            // this case needs special care because of the possibility of deleted attribute nodes
            return new Navigator.AxisFilter(((AttributeMapWithIdentity) attributeMap).iterateAttributes(this), test);
        } else {
            return new AttributeAxisIterator(this, test);
        }
    }

    /**
     * Copy this node to a given Receiver.
     * <p>This method is primarily for internal use. It should not be considered a stable
     * part of the Saxon API.</p>
     *
     * @param out         the Receiver to which the node should be copied. It is the caller's
     *                    responsibility to ensure that this Receiver is open before the method is called
     *                    (or that it is self-opening), and that it is closed after use.
     * @param copyOptions a selection of the options defined in {@link CopyOptions}
     * @param location    If non-null, identifies the location of the instruction
     *                    that requested this copy. If zero, indicates that the location information
     *                    is not available
     * @throws XPathException if any downstream error occurs
     */

    @Override
    public void copy(Receiver out, int copyOptions, Location location) throws XPathException {

        boolean copyTypes = CopyOptions.includes(copyOptions, CopyOptions.TYPE_ANNOTATIONS);
        SchemaType typeCode = copyTypes ? getSchemaType() : Untyped.getInstance();
        CopyInformee informee = (CopyInformee) out.getPipelineConfiguration().getComponent(CopyInformee.class.getName());
        if (informee != null) {
            Object o = informee.notifyElementNode(this);
            if (o instanceof Location) {
                location = (Location) o;
            }
        }

        NamespaceMap ns = CopyOptions.includes(copyOptions, CopyOptions.ALL_NAMESPACES) ? getAllNamespaces() : NamespaceMap.emptyMap();

        boolean disallowNamespaceSensitiveContent =
                ((copyOptions & CopyOptions.TYPE_ANNOTATIONS) != 0) &&
                        ((copyOptions & CopyOptions.ALL_NAMESPACES) == 0);
        if (copyTypes && disallowNamespaceSensitiveContent) {
            try {
                checkNotNamespaceSensitiveElement(getSchemaType());
            } catch (CopyNamespaceSensitiveException e) {
                e.setErrorCode(out.getPipelineConfiguration().isXSLT() ? "XTTE0950" : "XQTY0086");
                throw e;
            }
        }

        List<AttributeInfo> atts = new ArrayList<>(attributes().size());
        for (AttributeInfo att : attributes()) {
            SimpleType attributeType = BuiltInAtomicType.UNTYPED_ATOMIC;
            if (copyTypes) {
                attributeType = att.getType();
                if (disallowNamespaceSensitiveContent) {
                    try {
                        checkNotNamespaceSensitiveAttribute(attributeType, att);
                    } catch (CopyNamespaceSensitiveException e) {
                        e.setErrorCode(out.getPipelineConfiguration().isXSLT() ? "XTTE0950" : "XQTY0086");
                        throw e;
                    }
                }
            }
            atts.add(new AttributeInfo(att.getNodeName(), attributeType, att.getValue(), att.getLocation(), 0));
        }

        out.startElement(NameOfNode.makeName(this), typeCode, AttributeMap.fromList(atts),
                         ns, location,
                         ReceiverOption.BEQUEATH_INHERITED_NAMESPACES_ONLY | ReceiverOption.NAMESPACE_OK);

        // output the children

        NodeImpl next = getFirstChild();
        while (next != null) {
            next.copy(out, copyOptions, location);
            next = next.getNextSibling();
        }

        out.endElement();
    }

    /**
     * Check whether the content of this element is namespace-sensitive
     *
     * @param type the type annotation of the node
     * @throws XPathException if an error occurs
     */

    protected void checkNotNamespaceSensitiveElement(SchemaType type) throws XPathException {
        if (type instanceof SimpleType && ((SimpleType) type).isNamespaceSensitive()) {
            if (type.isAtomicType()) {
                throw new CopyNamespaceSensitiveException(
                        "Cannot copy QName or NOTATION values without copying namespaces");
            } else {
                // For a union or list type, we need to check whether the actual value is namespace-sensitive
                AtomicSequence value = atomize();
                for (AtomicValue val : value) {
                    if (val.getPrimitiveType().isNamespaceSensitive()) {
                        throw new CopyNamespaceSensitiveException(
                                "Cannot copy QName or NOTATION values without copying namespaces");
                    }
                }
            }
        }
    }

    /**
     * Check whether the content of an attribute is namespace-sensitive
     *
     * @param type the type annotation of the attribute node
     * @param att  the attribute
     * @throws XPathException if the content is namespace sensitive and cannot be copied
     */

    private void checkNotNamespaceSensitiveAttribute(SimpleType type, AttributeInfo att) throws XPathException {
        if (type.isNamespaceSensitive()) {
            if (type.isAtomicType()) {
                throw new CopyNamespaceSensitiveException(
                        "Cannot copy QName or NOTATION values without copying namespaces");
            } else {
                // For a union or list type, we need to check whether the actual value is namespace-sensitive
                AtomicSequence value = type.getTypedValue(att.getValue(), namespaceMap, getConfiguration().getConversionRules());
                for (AtomicValue val : value) {
                    if (val.getPrimitiveType().isNamespaceSensitive()) {
                        throw new CopyNamespaceSensitiveException(
                                "Cannot copy QName or NOTATION values without copying namespaces");
                    }
                }
            }
        }
    }


    /**
     * Delete this node (that is, detach it from its parent)
     */

    @Override
    public void delete() {
        DocumentImpl root = getPhysicalRoot();
        super.delete();
        if (root != null) {
            AxisIterator iter = iterateAxis(AxisInfo.DESCENDANT_OR_SELF, NodeKindTest.ELEMENT);
            while (true) {
                ElementImpl n = (ElementImpl) iter.next();
                for (AttributeInfo att : attributeMap) {
                    if (att.isId()) {
                        root.deregisterID(att.getValue());
                    }
                }
                if (n == null) {
                    break;
                }
                root.deIndex(n);
            }
        }
    }

    /**
     * Rename this node
     *
     * @param newName the new name
     * @param inheritNamespaces
     */

    @Override
    public void rename(NodeName newName, boolean inheritNamespaces) {
        String prefix = newName.getPrefix();
        String uri = newName.getURI();
        NamespaceBinding ns = new NamespaceBinding(prefix, uri);
        String uc = getURIForPrefix(prefix, true);
        if (uc == null) {
            uc = "";
        }
        if (!uc.equals(uri)) {
            if (uc.isEmpty()) {
                addNamespace(ns, inheritNamespaces);
            } else {
                throw new IllegalArgumentException(
                        "Namespace binding of new name conflicts with existing namespace binding");
            }
        }
        nodeName = newName;
    }

    /**
     * Add a namespace binding (that is, a namespace node) to this element. This call has no effect if applied
     * to a node other than an element.
     *
     * @param binding The namespace binding to be
     *                added. If the target element already has a namespace binding with this (prefix, uri) pair, the call has
     *                no effect. If the target element currently has a namespace binding with this prefix and a different URI, an
     *                exception is raised.
     */

    @Override
    public void addNamespace(/*@NotNull*/ NamespaceBinding binding, boolean inheritNamespaces) {
        if (binding.getURI().isEmpty()) {
            throw new IllegalArgumentException("Cannot add a namespace undeclaration");
        }
        String existing = namespaceMap.getURI(binding.getPrefix());
        if (existing != null) {
            if (!existing.equals(binding.getURI())) {
                throw new IllegalArgumentException("New namespace conflicts with existing namespace binding");
            }
        } else {
            namespaceMap = namespaceMap.put(binding.getPrefix(), binding.getURI());
        }
    }


    /**
     * Replace the string-value of this node
     *
     * @param stringValue the new string value
     */

    @Override
    public void replaceStringValue(/*@NotNull*/ CharSequence stringValue) {
        if (stringValue.length() == 0) {
            setChildren(null);
        } else {
            TextImpl text = new TextImpl(stringValue.toString());
            text.setRawParent(this);
            setChildren(text);
        }
    }

    /**
     * Change details of an attribute of this element
     *
     * @param index   the index position of the attribute to be changed
     * @param attInfo new details of the attribute
     */

    public void setAttributeInfo(int index, AttributeInfo attInfo) {
        AttributeMapWithIdentity attMap = prepareAttributesForUpdate();
        attMap = attMap.set(index, attInfo);
        setAttributes(attMap);
    }

    private AttributeMapWithIdentity prepareAttributesForUpdate() {
        if (attributes() instanceof AttributeMapWithIdentity) {
            return (AttributeMapWithIdentity) attributes();
        } else {
            AttributeMapWithIdentity newAtts = new AttributeMapWithIdentity(attributes().asList());
            setAttributes(newAtts);
            return newAtts;
        }
    }

    /**
     * Add an attribute to this element node.
     * <p>If this node is not an element, or if the supplied node is not an attribute, the method
     * takes no action. If the element already has an attribute with this name, the method
     * throws an exception.</p>
     * <p>This method does not perform any namespace fixup. It is the caller's responsibility
     * to ensure that any namespace prefix used in the name of the attribute (or in its value
     * if it has a namespace-sensitive type) is declared on this element.</p>
     *
     * @param nodeName   the name of the new attribute
     * @param attType    the type annotation of the new attribute
     * @param value      the string value of the new attribute
     * @param properties properties including IS_ID and IS_IDREF properties
     * @param inheritNamespaces
     * @throws IllegalStateException if the element already has an attribute with the given name.
     */

    @Override
    public void addAttribute(/*@NotNull*/ NodeName nodeName, SimpleType attType, /*@NotNull*/ CharSequence value, int properties, boolean inheritNamespaces) {
        AttributeMapWithIdentity atts = prepareAttributesForUpdate();
        atts = atts.add(new AttributeInfo(nodeName, attType, value.toString(), Loc.NONE, ReceiverOption.NONE));
        setAttributes(atts);
        if (!nodeName.hasURI("")) {
            // The new attribute name is in a namespace
            NamespaceBinding binding = nodeName.getNamespaceBinding();
            String prefix = binding.getPrefix();
            String uc = getURIForPrefix(prefix, false);
            if (uc == null) {
                // The namespace is not already declared on the element
                addNamespace(binding, inheritNamespaces);
            } else if (!uc.equals(binding.getURI())) {
                throw new IllegalStateException(
                        "Namespace binding of new name conflicts with existing namespace binding");
            }
        }
        if (ReceiverOption.contains(properties, ReceiverOption.IS_ID)) {
            DocumentImpl root = getPhysicalRoot();
            if (root != null) {
                root.registerID(this, Whitespace.trim(value));
            }
        }
    }

    /**
     * Remove an attribute from this element node
     * <p>If this node is not an element, or if the specified node is not an attribute
     * of this element, this method takes no action.</p>
     * <p>The attribute object itself becomes unusable; any attempt to use this attribute object,
     * or any other object representing the same node, is likely to result in an exception.</p>
     *
     * @param attribute the attribute node to be removed
     */

    @Override
    public void removeAttribute(/*@NotNull*/ NodeInfo attribute) {
        if (!(attribute instanceof AttributeImpl)) {
            return; // no action
        }
        int index = ((AttributeImpl) attribute).getSiblingPosition();
        AttributeInfo info = attributes().itemAt(index);
        AttributeMapWithIdentity atts = prepareAttributesForUpdate();
        atts = atts.remove(index);
        setAttributes(atts);

        if (index >= 0 && info.isId()) {
            DocumentImpl root = getPhysicalRoot();
            root.deregisterID(info.getValue());
        }

        ((AttributeImpl) attribute).setRawParent(null);
    }

    /**
     * Remove a namespace node from this node. The namespaces of its descendant nodes are unaffected.
     * The method has no effect on non-element nodes, nor on elements if there is no in-scope namespace
     * with the required prefix, nor if the element name or one of its attributes uses this namespace
     * prefix
     *
     * @param prefix the namespace prefix.
     */
    @Override
    public void removeNamespace(String prefix) {
        Objects.requireNonNull(prefix);
        if (prefix.equals(getPrefix())) {
            throw new IllegalStateException("Cannot remove binding of namespace prefix used on the element name");
        }
        for (AttributeInfo att : attributeMap) {
            if (att.getNodeName().getPrefix().equals(prefix)) {
                throw new IllegalStateException("Cannot remove binding of namespace prefix used on an existing attribute name");
            }
        }
        namespaceMap = namespaceMap.remove(prefix);
    }

    /**
     * Add a namespace node from this node. The namespaces of its descendant nodes are unaffected.
     * The method has no effect on non-element nodes. If there is an existing namespace using this
     * prefix, the method throws an exception.
     *  @param prefix the namespace prefix. Empty string for the default namespace.
     * @param uri    The namespace URI.
     * @param inherit
     */
    @Override
    public void addNamespace(String prefix, String uri, boolean inherit) {
        NamespaceBinding binding = new NamespaceBinding(prefix, uri);
        if (binding.getURI().isEmpty()) {
            throw new IllegalArgumentException("Cannot add a namespace undeclaration");
        }
        String existing = namespaceMap.getURI(binding.getPrefix());
        if (existing != null) {
            if (!existing.equals(binding.getURI())) {
                throw new IllegalArgumentException("New namespace conflicts with existing namespace binding");
            }
        } else {
            NamespaceMap oldMap = namespaceMap;
            namespaceMap = namespaceMap.put(binding.getPrefix(), binding.getURI());
            if (inherit && namespaceMap != oldMap) {
                for (NodeInfo child : children(NodeKindTest.ELEMENT)) {
                    ((ElementImpl) child).inheritParentNamespaces(binding, oldMap, namespaceMap);
                }
            }
        }
    }

    private void inheritParentNamespaces(NamespaceBinding binding, NamespaceMap oldParentMap, NamespaceMap newParentMap) {
        NamespaceMap oldMap = namespaceMap;
        if (oldMap.getURIForPrefix(binding.getPrefix(), false) == null) {
            if (namespaceMap == oldParentMap) {
                namespaceMap = newParentMap;
            } else {
                namespaceMap = namespaceMap.put(binding.getPrefix(), binding.getURI());
            }
            for (NodeInfo child : children(NodeKindTest.ELEMENT)) {
                ((ElementImpl) child).inheritParentNamespaces(binding, oldMap, namespaceMap);
            }
        }
    }


    /**
     * Remove type information from this node (and its ancestors, recursively).
     * This method implements the upd:removeType() primitive defined in the XQuery Update specification
     */

    @Override
    public void removeTypeAnnotation() {
        if (getSchemaType() != Untyped.getInstance()) {
            type = AnyType.getInstance();
            getRawParent().removeTypeAnnotation();
        }
    }

    public void setNamespaceMap(NamespaceMap map) {
        namespaceMap = map;
    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix. May be the zero-length string, indicating
     *                   that there is no prefix. This indicates either the default namespace or the
     *                   null namespace, depending on the value of useDefault.
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is "". If false, the method returns "" when the prefix is "".
     * @return the uri for the namespace, or null if the prefix is not in scope.
     * The "null namespace" is represented by the pseudo-URI "".
     */

    /*@Nullable*/
    @Override
    public String getURIForPrefix(/*@NotNull*/ String prefix, boolean useDefault) {

        if (prefix.isEmpty()) {
            if (useDefault) {
                return namespaceMap.getDefaultNamespace();
            } else {
                return NamespaceConstant.NULL;
            }
        } else {
            return namespaceMap.getURI(prefix);
        }
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    /*@Nullable*/
    @Override
    public Iterator<String> iteratePrefixes() {
        return namespaceMap.iteratePrefixes();
    }

    /**
     * Search the in-scope namespaces to see whether a given namespace is in scope.
     *
     * @param uri The URI to be matched.
     * @return true if the namespace is in scope
     */

    /*@Nullable*/
    public boolean isInScopeNamespace(/*@NotNull*/ String uri) {
        for (NamespaceBinding b : namespaceMap) {
            if (b.getURI().equals(uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all namespace undeclarations and undeclarations defined on this element.
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of NamespaceBinding objects representing the namespace declarations and undeclarations present on
     * this element. For a node other than an element, return null.
     * The XML namespace is never included in the list. If the supplied array is larger than required,
     * then the first unused entry will be set to null.
     * <p>For a node other than an element, the method returns null.</p>
     */

    @Override
    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        List<NamespaceBinding> bindings = new ArrayList<>();
        for (NamespaceBinding nb : namespaceMap) {
            bindings.add(nb);
        }
        return bindings.toArray(NamespaceBinding.EMPTY_ARRAY);
    }

    /**
     * Ensure that a child element being inserted into a tree has the right namespace declarations.
     * Redundant declarations should be removed. If the child is in the null namespace but the parent has a default
     * namespace, xmlns="" should be added. If inherit is false, namespace undeclarations should be added for all
     * namespaces that are declared on the parent but not on the child.
     *
     * @param inherit true if the child is to inherit the inscope namespaces of its new parent
     */

    protected void fixupInsertedNamespaces(boolean inherit) {
        if (getRawParent().getNodeKind() == Type.DOCUMENT) {
            return;
        }

        ElementImpl parent = (ElementImpl) getRawParent();

        NamespaceMap parentNamespaces = parent.namespaceMap;

        // Namespaces present on the parent but not on the child should be undeclared (if requested)

        if (inherit) {
            deepAddNamespaces(parentNamespaces);
        } else {
//            Iterator it = inscope.iteratePrefixes();
//            while (it.hasNext()) {
//                String prefix = (String) it.next();
//                if (!prefix.equals("xml")) {
//                    boolean found = false;
//                    if (namespaceList != null) {
//                        for (NamespaceBinding aNamespaceList : namespaceList) {
//                            if (aNamespaceList.getPrefix().equals(prefix)) {
//                                found = true;
//                                break;
//                            }
//                        }
//                    }
//                    if (!found) {
//                        childNamespaces.add(new NamespaceBinding(prefix, ""));
//                    }
//                }
//            }
        }

    }

    private void deepAddNamespaces(NamespaceMap inheritedNamespaces) {
        NamespaceMap childNamespaces = namespaceMap;
        for (NamespaceBinding binding : inheritedNamespaces) {
            if (childNamespaces.getURI(binding.getPrefix()) == null) {
                childNamespaces = childNamespaces.put(binding.getPrefix(), binding.getURI());
            } else {
                inheritedNamespaces = inheritedNamespaces.remove(binding.getPrefix());
            }
        }
        namespaceMap = childNamespaces;
        for (NodeInfo child : children(ElementImpl.class::isInstance)) {
            ((ElementImpl) child).deepAddNamespaces(inheritedNamespaces);
        }
    }

    /**
     * Get the namespace list for this element.
     *
     * @return The full set of in-scope namespaces
     */

    /*@Nullable*/
    @Override
    public NamespaceMap getAllNamespaces() {
        return namespaceMap;
    }


    /**
     * Get the value of a given attribute of this node
     *
     * @param uri       the namespace URI of the attribute name, or "" if the attribute is not in a namepsace
     * @param localName the local part of the attribute name
     * @return the attribute value if it exists or null if not
     */

    /*@Nullable*/
    @Override
    public String getAttributeValue(/*@NotNull*/ String uri, /*@NotNull*/ String localName) {
        return attributeMap == null ? null : attributeMap.getValue(uri, localName);
    }

    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    @Override
    public boolean isId() {
        // This is an approximation. For a union type, we check that the actual value is a valid NCName,
        // but we don't check that it was validated against the member type of the union that is an ID type.
        try {
            SchemaType type = getSchemaType();
            return type.getFingerprint() == StandardNames.XS_ID ||
                    type.isIdType() && NameChecker.isValidNCName(getStringValueCS());
        } catch (MissingComponentException e) {
            return false;
        }
    }

    /**
     * Ask whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element.
     */

    @Override
    public boolean isIdref() {
        return isIdRefNode(this);
    }

    static boolean isIdRefNode(NodeImpl node) {
        SchemaType type = node.getSchemaType();
        try {
            if (type.isIdRefType()) {
                if (type == BuiltInAtomicType.IDREF || type == BuiltInListType.IDREFS) {
                    return true;
                }
                try {
                    for (AtomicValue av : node.atomize()) {
                        if (av.getItemType().isIdRefType()) {
                            return true;
                        }
                    }
                } catch (XPathException err) {
                    // no action
                }
            }
        } catch (MissingComponentException e) {
            return false;
        }
        return false;
    }

}

