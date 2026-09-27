////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

//import com.saxonica.ee.validate.SkipValidator;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.CopyNamespaceSensitiveException;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodePredicateLambda;
import net.sf.saxon.pattern.nodetest.NamedXNodePredicate;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.CompressedWhitespace;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.*;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.z.IntHashMap;


/**
 * A node in the XML parse tree representing an XML element.
 * <p>This class is an implementation of NodeInfo. The object is a wrapper around
 * one entry in the arrays maintained by the TinyTree. Note that the same node
 * might be represented by different TinyElementImpl objects at different times.</p>
 */

public class TinyElementImpl extends TinyParentNodeImpl {

    /**
     * Constructor - create a tiny element node
     *
     * @param tree   the Tinytree containing the node
     * @param nodeNr the node number
     */

    public TinyElementImpl(TinyTree tree, int nodeNr) {
        super(tree, nodeNr);
    }

    /**
     * Return the type of node.
     *
     * @return Type.ELEMENT
     */

    @Override
    public final int getNodeKind() {
        return Type.ELEMENT;
    }

    /**
     * Get the base URI of this element node. This will be the same as the System ID unless
     * xml:base has been used.
     */

    @Override
    public String getBaseURI() {
        if (tree.getUniformBaseUri() != null) {
            return tree.getUniformBaseUri();
        }
        synchronized (tree) {
            if (tree.knownBaseUris == null) {
                tree.knownBaseUris = new IntHashMap<>();
            }
            String uri = tree.knownBaseUris.get(nodeNr);
            if (uri == null) {
                uri = Navigator.getBaseURI(this,
                                           n -> tree.isTopWithinEntity(((TinyElementImpl) n).getNodeNumber()));
                tree.knownBaseUris.put(nodeNr, uri);
            }
            return uri;
        }
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
        return tree.getSchemaType(nodeNr);
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. It will be a Value representing a sequence whose items are atomic
     *         values.
     */

    @Override
    public AtomicSequence atomize() throws XPathException {
        return tree.getTypedValueOfElement(this);
    }

    /**
     * Get all namespace undeclarations and undeclarations defined on this element.
     *
     * @param buffer If this is non-null, and the result array fits in this buffer, then the result
     *               may overwrite the contents of this array, to avoid the cost of allocating a new array on the heap.
     * @return An array of objects representing the namespace declarations and undeclarations present on
     *         this element. For a node other than an element, return null. Otherwise, the returned array is a
     *         sequence of namespace binding objects (essentially prefix/uri pairs)
     *         If the URI is null, then this is a namespace undeclaration rather than a declaration.
     *         The XML namespace is never included in the list. If the supplied array is larger than required,
     *         then the first unused entry will be set to null.
     */

    /*@Nullable*/
    @Override
    public NamespaceBinding[] getDeclaredNamespaces(NamespaceBinding[] buffer) {
        TinyNodeImpl parent = getParent();
        if (parent != null && parent.getNodeKind() == Type.ELEMENT) {
            return getAllNamespaces().getDifferences(parent.getAllNamespaces(), false);
        } else {
            return getAllNamespaces().getNamespaceBindings();
        }
    }

    @Override
    public NamespaceMap getAllNamespaces() {
        return tree.namespaceMaps[tree.beta[nodeNr]];
    }

    /**
     * Ask whether the element is the root of a subtree in which no descendant element
     * has any local namespace declarations or undeclarations; that is, all elements
     * in the subtree have the same in-scope namespace bindings.
     * @return true if it is known that no descendant elements have in-scope namespaces
     * different from those of this element.
     */

    public boolean hasUniformNamespaces() {
        return false; // TODO: temporarily
//        int nr = nodeNr;
//        int ns = tree.beta[nr];
//        TinyElementImpl anc = this;
//        while (ns == -1) {
//            TinyNodeImpl parent = anc.getParent();
//            if (parent instanceof TinyDocumentImpl) {
//                return !tree.usesNamespaces;
//            } else {
//                nr = parent.nodeNr;
//                ns = tree.beta[nr];
//                anc = (TinyElementImpl)parent;
//            }
//        }
//        // Find the first namespace binding with a different parent
//        while (ns < tree.numberOfNamespaces && tree.namespaceParent[ns] == nr) {
//            ns++;
//        }
//        // Return true if none is found, or if the element owning this namespace binding
//        // is outside the subtree
//        return ns >= tree.numberOfNamespaces || !isAncestorOrSelf(tree.getNode(tree.namespaceParent[ns]));
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
    public String getAttributeValue(/*@NotNull*/ NamespaceUri uri, /*@NotNull*/ String local) {
        int a = tree.alpha[nodeNr];
        if (a < 0) {
            return null;
        }
        NamePool pool = getNamePool();
        while (a < tree.numberOfAttributes && tree.attParent[a] == nodeNr) {
            int nc = tree.attCode[a];
            // Avoid allocating a name code for an ad-hoc request
            StructuredQName name = pool.getUnprefixedQName(nc);
            if (name.getLocalPart().equals(local) && name.hasURI(uri)) {
                return tree.attValue[a];
            }
            a++;
        }
        return null;
    }

    /**
     * Get the value of the attribute with a given fingerprint.
     *
     * @param fp the fingerprint of the required attribute
     * @return the string value of the attribute if present, or null if absent
     */

    public String getAttributeValue(int fp) {
        int a = tree.alpha[nodeNr];
        if (a < 0) {
            return null;
        }
        while (a < tree.numberOfAttributes && tree.attParent[a] == nodeNr) {
            if (fp == (tree.attCode[a] & NamePool.FP_MASK)) {
                return tree.attValue[a];
            }
            a++;
        }
        return null;
    }

    /**
     * Get the attribute node with a given name, if it exists, or null otherwise
     */

    public TinyAttributeImpl getAttributeNode(int fp) {
        int index = tree.alpha[nodeNr];
        if (index < 0) {
            return null;
        }
        int[] attNames = tree.getAttributeNameCodeArray();
        while (index < tree.numberOfAttributes && tree.attParent[index] == nodeNr) {
            if (fp == (attNames[index] & NamePool.FP_MASK)) {
                return tree.getAttributeNode(index);
            }
            index++;
        }
        return null;
    }

    /**
     * Get an iterator over the attribute axis, starting at this node; the nodes will
     * be in document order.
     *
     * @param predicate a condition that the nodes must satisfy, or null
     * @return the required iterator
     */
    @Override
    public SequenceIterator iterateAttributeAxis(NodePredicate predicate) {
        if (tree.alpha[nodeNr] < 0) {
            return EmptyIterator.INSTANCE;
        }
        if (predicate instanceof NamedXNodePredicate fpTest) {
            int fp = fpTest.getRequiredFingerprint();
            if (fp != -1 && fpTest.getNodeKind() == Type.ATTRIBUTE) {
                int index = tree.alpha[nodeNr];
                int[] attNames = tree.getAttributeNameCodeArray();
                while (index < tree.numberOfAttributes && tree.attParent[index] == nodeNr) {
                    if (fp == (attNames[index] & NamePool.FP_MASK)) {
                        TinyAttributeImpl att = tree.getAttributeNode(index);
                        if (fpTest.isFingerprintSufficient() || predicate.test(att)) {
                            return new SingletonIterator(att);
                        } else {
                            return EmptyIterator.INSTANCE;
                        }
                    }
                    index++;
                }
                return EmptyIterator.INSTANCE;
            }
        }
        if (predicate == NodeKindType.ATTRIBUTE) {
            predicate = NodePredicateLambda.of(node -> true);
        }
        return new AttributeIterator(tree, nodeNr, predicate);
    }

    /**
     * Copy this node to a given receiver
     *
     * @param receiver the destination of the copy
     * @param copyOptions determines handling of namespaces, etc
     * @param location location information associated with the event
     */

    @Override
    public void copy(/*@NotNull*/ Receiver receiver, int copyOptions, Location location) throws XPathException {

        boolean copyTypes = CopyOptions.includes(copyOptions, CopyOptions.TYPE_ANNOTATIONS);
        final boolean copyForUpdate = CopyOptions.includes(copyOptions, CopyOptions.FOR_UPDATE);

        short level = -1;
        boolean closePending = false;
        short startLevel = tree.depth[nodeNr];

        boolean disallowNamespaceSensitiveContent =
                ((copyOptions & CopyOptions.TYPE_ANNOTATIONS) != 0) &&
                        ((copyOptions & CopyOptions.ALL_NAMESPACES) == 0);
        Configuration config = tree.getConfiguration();
        NamePool pool = config.getNamePool();
        int next = nodeNr;
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        java.util.function.Function<NodeInfo, Object> informee = pipe.getCopyInformee();
        SchemaType elementType = Untyped.INSTANCE;
        SimpleType attributeType = BuiltInAtomicType.UNTYPED_ATOMIC;
        AttributeMap emptyAttributes = EmptyAttributeMap.INSTANCE;


        do {

            // determine node depth
            short nodeLevel = tree.depth[next];

            // extra close required?
            if (closePending) {
                level++;
            }

            // close former elements
            for (; level > nodeLevel; level--) {
                receiver.endElement();
            }

            // new node level
            level = nodeLevel;

            // output depends on node kind
            int kind = tree.nodeKind[next];
            switch (kind) {
                case Type.ELEMENT, Type.TEXTUAL_ELEMENT -> {

                    boolean isTextualElement = kind == Type.TEXTUAL_ELEMENT;
                    int receiverOptions = ReceiverOption.BEQUEATH_INHERITED_NAMESPACES_ONLY;
                    if (copyForUpdate) {
                        receiverOptions |= ReceiverOption.MUTABLE_TREE;
                    }
                    // start element
                    if (copyTypes) {
                        elementType = tree.getSchemaType(next);
                        if (disallowNamespaceSensitiveContent) {
                            try {
                                checkNotNamespaceSensitiveElement(elementType, next);
                            } catch (CopyNamespaceSensitiveException e) {
                                throw e.withErrorCode(pipe.isXSLT() ? "XTTE0950" : "XQTY0086");
                            }
                        }
                    }
                    if (informee != null) {
                        Location loc = (Location) informee.apply(tree.getNode(next));
                        if (loc != null) {
                            location = loc;
                        }
                    }
                    int nameCode = tree.nameCode[next];
                    int fp = nameCode & NamePool.FP_MASK;
                    String prefix = tree.getPrefix(next);
                    if (location.getLineNumber() < tree.getLineNumber(next)) {
                        String systemId = location.getSystemId() == null ? getSystemId() : location.getSystemId();
                        location = new Loc(systemId, tree.getLineNumber(next), tree.getColumnNumber(next));
                    }

                    // decide on namespaces
                    NamespaceMap namespaces = NamespaceMap.emptyMap();
                    boolean addAttributeNamespaces = false;
                    if (tree.usesNamespaces) {
                        if ((copyOptions & CopyOptions.ALL_NAMESPACES) != 0) {
                            if (isTextualElement) {
                                int parent = getParentNodeNr(tree, next);
                                namespaces = tree.namespaceMaps[tree.beta[parent]];
                            } else {
                                namespaces = tree.namespaceMaps[tree.beta[next]];
                            }
                        } else {
                            addAttributeNamespaces = true;
                            NamespaceUri uri = pool.getURI(nameCode);
                            if (!uri.isEmpty()) {
                                namespaces = NamespaceMap.of(prefix, uri);
                            }
                        }
                    }


                    if (isTextualElement) {
                        closePending = false;
                        receiver.startElement(new CodedName(fp, prefix, pool),
                                              elementType,
                                              emptyAttributes,
                                              namespaces,
                                              location,
                                              receiverOptions);
                        // output characters
                        final UnicodeString value = TinyTextImpl.getStringValue(tree, next);
                        receiver.characters(value, location, ReceiverOption.WHOLE_TEXT_NODE);
                        receiver.endElement();
                    } else {
                        // there is an element to close
                        closePending = true;


                        // output attributes
                        //AttributeMap attributes = tree.alpha[nodeNr] < 0 ? EmptyAttributeMap.getInstance() : new LocalAttributeMap(copyTypes);

                        AttributeMap attributes;

                        int att = tree.alpha[next];
                        if (att >= 0) {
                            attributes = EmptyAttributeMap.INSTANCE;
                            //List<AttributeInfo> attributeInfoList = new ArrayList<>(8);
                            int[] attParent = tree.attParent;
                            while (att < tree.numberOfAttributes && attParent[att] == next) {
                                int attCode = tree.attCode[att];
                                int attfp = attCode & NamePool.FP_MASK;
                                if (copyTypes) {
                                    attributeType = tree.getAttributeType(att);
                                    if (disallowNamespaceSensitiveContent) {
                                        try {
                                            checkNotNamespaceSensitiveAttribute(attributeType, att);
                                        } catch (CopyNamespaceSensitiveException e) {
                                            throw e.withErrorCode(pipe.isXSLT() ? "XTTE0950" : "XQTY0086");
                                        }
                                    }
                                }
                                String attPrefix = tree.prefixPool.getPrefix(attCode >> 20);
                                int attProps = ReceiverOption.NOT_A_DUPLICATE;
                                if (tree.isIdAttribute(att)) {
                                    attProps |= ReceiverOption.IS_ID;
                                }
                                if (tree.isIdrefAttribute(att)) {
                                    attProps |= ReceiverOption.IS_IDREF;
                                }
                                attributes = attributes.put(new AttributeInfo(new CodedName(attfp, attPrefix, pool),
                                                                        attributeType, tree.attValue[att],
                                                                        location, attProps));
                                if (addAttributeNamespaces && !attPrefix.isEmpty()) {
                                    namespaces = namespaces.put(attPrefix, pool.getURI(attCode));
                                }
                                att++;
                            }
                            //attributes = SequenceTool.attributeMapFromList(attributeInfoList);
                        } else {
                            attributes = emptyAttributes;
                        }

                        // see bug 2209
                        receiver.startElement(new CodedName(fp, prefix, pool),
                                              elementType,
                                              attributes, namespaces,
                                              location,
                                              receiverOptions);

                    }
                }
                case Type.TEXT -> {

                    // don't close text nodes
                    closePending = false;

                    // output characters
                    final UnicodeString value = TinyTextImpl.getStringValue(tree, next);
                    receiver.characters(value, location, ReceiverOption.WHOLE_TEXT_NODE);
                }
                case Type.WHITESPACE_TEXT -> {

                    // don't close text nodes
                    closePending = false;

                    // output characters
                    long compressedValue = ((long) tree.alpha[next] << 32) | ((long) tree.beta[next] & 0xffffffffL);
                    final UnicodeString value = new CompressedWhitespace(compressedValue);
                    receiver.characters(value, location, ReceiverOption.WHOLE_TEXT_NODE);
                }
                case Type.COMMENT -> {

                    // don't close text nodes
                    closePending = false;

                    // output copy of comment
                    int start = tree.alpha[next];
                    int len = tree.beta[next];
                    if (len > 0) {
                        receiver.comment(tree.commentBuffer.substring(start, start + len), location, ReceiverOption.NONE);
                    } else {
                        receiver.comment(EmptyUnicodeString.getInstance(), Loc.NONE, ReceiverOption.NONE);
                    }
                }
                case Type.PROCESSING_INSTRUCTION -> {

                    // don't close text nodes
                    closePending = false;

                    // output copy of PI
                    NodeInfo pi = tree.getNode(next);
                    receiver.processingInstruction(pi.getLocalPart(), pi.getUnicodeStringValue(), location, ReceiverOption.NONE);
                }
                case Type.PARENT_POINTER -> {
                    closePending = false;
                }
            }

            next++;

        } while (next < tree.numberOfNodes && tree.depth[next] > startLevel);

        // close all remaining elements
        if (closePending) {
            level++;
        }
        for (; level > startLevel; level--) {
            receiver.endElement();
        }
    }

    // Alternative algorithm for the copy() method, using recursion. Included so that we
    // can study the performance. It's currently not handling all edge cases, for example
    // detection of the disallowNamespaceSensitiveContent condition.

//    public void copy_12(Receiver receiver, int copyOptions, Location location) throws XPathException {
//
//        String prefix = getPrefix();
//        NamePool pool = tree.getNamePool();
//        NodeName elemName = new CodedName(tree.nameCode[nodeNr] & 0xfffff, prefix, pool);
//        boolean typed = tree.isTyped() && CopyOptions.includes(copyOptions, CopyOptions.TYPE_ANNOTATIONS);
//        SchemaType type = typed ? getSchemaType() : Untyped.getInstance();
//        AttributeMap atts = tree.alpha[nodeNr] < 0 ? EmptyAttributeMap.getInstance() : new LocalAttributeMap(typed);
//
//        // decide on namespaces
//        NamespaceMap namespaces = NamespaceMap.emptyMap();
//        if (tree.usesNamespaces) {
//            if ((copyOptions & CopyOptions.ALL_NAMESPACES) != 0) {
//                // copy all the namespaces
//                namespaces = getAllNamespaces();
//            } else {
//                // copy only the namespace bindings actually used in the element and attribute names
//                NamespaceUri uri = getNamespaceUri();
//                if (!uri.isEmpty()) {
//                    namespaces = NamespaceMap.of(prefix, uri);
//                }
//                int att = tree.alpha[nodeNr];
//                if (att >= 0) {
//                    while (att < tree.numberOfAttributes && tree.attParent[att] == nodeNr) {
//                        int attCode = tree.attCode[att++];
//                        if (NamePool.isPrefixed(attCode)) {
//                            String attPrefix = tree.prefixPool.getPrefix(attCode >> 20);
//                            namespaces = namespaces.put(attPrefix, pool.getURI(attCode));
//                        }
//                    }
//                }
//            }
//        }
//
//
//        receiver.startElement(elemName, type, atts, namespaces, location, 0);
//
//        // output the children
//
//        for (NodeInfo child : children()) {
//            child.copy(receiver, copyOptions, location);
//        }
//
//        receiver.endElement();
//    }


    /**
     * Check whether the content of an element is namespace-sensitive
     *
     *
     * @param type   the type annotation of the node
     * @param nodeNr the node number of the element
     * @throws XPathException if an error occurs
     */

    protected void checkNotNamespaceSensitiveElement(SchemaType type, int nodeNr) throws XPathException {
        if (type instanceof SimpleType && ((SimpleType) type).isNamespaceSensitive()) {
            if (type.isAtomicType()) {
                throw new CopyNamespaceSensitiveException(
                        "Cannot copy QName or NOTATION values without copying namespaces");
            } else {
                // For a union or list type, we need to check whether the actual value is namespace-sensitive
                AtomicSequence value = tree.getTypedValueOfElement(nodeNr);
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
     *
     * @param type   the type annotation of the node
     * @param nodeNr the node number of the element
     * @throws XPathException  if an error occurs
     */

    private void checkNotNamespaceSensitiveAttribute(SimpleType type, int nodeNr) throws XPathException {
        if (type.isNamespaceSensitive()) {
            if (type.isAtomicType()) {
                throw new CopyNamespaceSensitiveException(
                        "Cannot copy QName or NOTATION values without copying namespaces");
            } else {
                // For a union or list type, we need to check whether the actual value is namespace-sensitive
                AtomicSequence value = tree.getTypedValueOfAttribute(null, nodeNr);
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
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix. May be the zero-length string, indicating
     *                   that there is no prefix. This indicates either the default namespace or the
     *                   null namespace, depending on the value of useDefault.
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is "". If false, the method returns "" when the prefix is "".
     * @return the uri for the namespace, or null if the prefix is not in scope.
     *         The "null namespace" is represented by the pseudo-URI "".
     */

    /*@Nullable*/
//    public NamespaceUri getURIForPrefix(/*@Nullable*/ String prefix, boolean useDefault) {
//        if (!useDefault && (prefix == null || prefix.isEmpty())) {
//            return "";
//        }
//        int ns = tree.beta[nodeNr]; // by convention
//        NamespaceMap map = tree.namespaceMaps[ns];
//        return map.getURIForPrefix(prefix, useDefault);
//    }

    /**
     * Determine whether this node has the is-id property
     *
     * @return true if the node is an ID
     */

    @Override
    public boolean isId() {
        // this looks very inefficient, but the method isn't actually used...
        return tree.isIdElement(nodeNr);
    }

    /**
     * Determine whether this node has the is-idref property
     *
     * @return true if the node is an IDREF or IDREFS element or attribute
     */

    @Override
    public boolean isIdref() {
        return tree.isIdrefElement(nodeNr);
    }

    private boolean isSkipValidator(Receiver r) {
        return false;
    }

//    private class LocalAttributeMap implements AttributeMap {
//
//        private final boolean typed;
//
//        public LocalAttributeMap(boolean typed) {
//            this.typed = typed;
//        }
//
//        /**
//         * Return the number of attributes in the map.
//         *
//         * @return The number of attributes in the map.
//         */
//        @Override
//        public int size() {
//            int count = 0;
//            int att = tree.alpha[nodeNr];
//            if (att >= 0) {
//                while (att < tree.numberOfAttributes && tree.attParent[att] == nodeNr) {
//                    count++;
//                    att++;
//                }
//            }
//            return count;
//        }
//
//        /**
//         * Returns an iterator over elements of type {@code T}.
//         *
//         * @return an Iterator.
//         */
//        @Override
//        public Iterator<AttributeInfo> iterator() {
//            return new AttributeInfoIterator(typed);
//        }
//    }
//
//    private class AttributeInfoIterator implements Iterator<AttributeInfo> {
//        private int att = tree.alpha[nodeNr];
//        private final NamePool pool = tree.getNamePool();
//        private final boolean typed;
//        private final Location location = TinyElementImpl.this;
//
//        public AttributeInfoIterator(boolean typed) {
//            this.typed = typed && tree.attType != null;
//        }
//
//        @Override
//        public boolean hasNext() {
//            return att < tree.numberOfAttributes && tree.attParent[att] == nodeNr;
//        }
//
//        @Override
//        public AttributeInfo next() {
//            int code = tree.attCode[att];
//            String prefix = NamePool.isPrefixed(code) ? tree.prefixPool.getPrefix(code >> 20) : "";
//            AttributeInfo info = new AttributeInfo(
//                    new CodedName(code & 0xfffff, prefix, pool),
//                    typed ? tree.attType[att] : BuiltInAtomicType.UNTYPED_ATOMIC,
//                    tree.attValue[att],
//                    location,
//                    0);
//            att++;
//            return info;
//        }
//    }


}

