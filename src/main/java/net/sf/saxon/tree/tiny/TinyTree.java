////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.SystemIdMap;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.z.*;

import java.util.*;


/**
 * A data structure to hold the contents of a tree. As the name implies, this implementation
 * of the data model is optimized for size, and for speed of creation: it minimizes the number
 * of Java objects used.
 * <p>
 * <p>It can be used to represent a tree that is rooted at a document node, or one that is rooted
 * at an element node.</p>
 * <p>
 * <p>From Saxon 9.7, as a consequence of bug 2220, it is used only to hold a single tree, whose
 * root is always node number zero.</p>
 */

public final class TinyTree extends GenericTreeInfo implements NodeVectorTree {

    /*@NotNull*/
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    // the contents of the document

    protected AppendableCharSequence charBuffer;
    /*@Nullable*/
    protected FastStringBuffer commentBuffer = null; // created when needed

    protected int numberOfNodes = 0;    // excluding attributes and namespaces

    // The following arrays contain one entry for each node other than attribute
    // and namespace nodes, arranged in document order.

    // nodeKind indicates the kind of node, e.g. element, text, or comment
    public byte[] nodeKind;

    // depth is the depth of the node in the hierarchy, i.e. the number of ancestors
    protected short[] depth;

    // next is the node number of the next sibling
    // - unless it points backwards, in which case it is the node number of the parent
    protected int[] next;

    // alpha holds a value that depends on the node kind. For text nodes, it is the offset
    // into the text buffer. For comments and processing instructions, it is the offset into
    // the comment buffer. For elements, it is the index of the first attribute node, or -1
    // if this element has no attributes.
    protected int[] alpha;

    // beta holds a value that depends on the node kind. For text nodes, it is the length
    // of the text. For comments and processing instructions, it is the length of the text.
    // For elements, it is the index of the first namespace node, or -1
    // if this element has no namespaces.
    protected int[] beta;

    // nameCode holds the name of the node, as an identifier resolved using the name pool
    protected int[] nameCode;

    // the prior array indexes preceding-siblings; it is constructed only when required
    /*@Nullable*/
    protected int[] prior = null;

    // the typeCode array holds type codes for element nodes; it is constructed only
    // if at least one element has a type other than untyped, or has an IDREF property.
    // The array holds a reference to the schema type.
    /*@Nullable*/
    protected SchemaType[] typeArray = null;

    // the typedValue array holds the typed values of element nodes if the typed value is anything
    // other than string, untypedAtomic, or anyURI. This means it is only used for schema-validated
    // documents. It is created lazily when the typed value of a node is first accessed.
    /*@Nullable*/
    protected AtomicSequence[] typedValueArray = null;

    // idRefElements is a set holding the node numbers of element nodes having the IDREF property
    protected IntSet idRefElements = null;

    // idRefAttributes is a set holding the node numbers of attribute nodes having the IDREF property
    protected IntSet idRefAttributes = null;

    // nilledElements is a set holding the node numbers of elements having the NILLED property
    protected IntSet nilledElements = null;

    // defaultedAttribute is a set holding the attribute node numbers of attributes that resulted from expansion of schema defaults
    protected IntSet defaultedAttributes = null;

    // topWithinEntity is a set holding the node numbers of elements that do not have a parent within
    // the same external entity
    protected IntSet topWithinEntity = null;

    // boolean switch to disable the typed value caching
    private boolean allowTypedValueCache = true;

    // index from local names to fingerprints, built only if a search by local name is done
    private Map<String, IntSet> localNameIndex = null;


    public static final int TYPECODE_IDREF = 1 << 29;

    // the owner array gives fast access from a node to its parent; it is constructed
    // only when required
    // protected int[] parentIndex = null;

    // the following arrays have one entry for each attribute.
    protected int numberOfAttributes = 0;

    // attParent is the index of the parent element node
    protected int[] attParent;

    // attCode is the nameCode representing the attribute name
    protected int[] attCode;

    // attValue is the string value of the attribute
    protected CharSequence[] attValue;

    // attTypedValue is the typed vlaue of the attribute, maintained only if the attribute type is
    // something other than string, untypedAtomic, or anyURI. It is maintained lazily on first reference
    // to the typed value
    protected AtomicSequence[] attTypedValue;

    // attTypeCode holds type annotations. The array is created only if any nodes have a type annotation

    /*@Nullable*/
    protected SimpleType[] attType;

    // The following arrays have one entry for each distinct namespace map
    protected int numberOfNamespaces = 0;
    protected NamespaceMap[] namespaceMaps;

    /*@Nullable*/
    private int[] lineNumbers = null;
    /*@Nullable*/
    private int[] columnNumbers = null;
    /*@Nullable*/
    private SystemIdMap systemIdMap = null;

    // a boolean that is set to true if the document declares a namespace other than the XML namespace
    protected boolean usesNamespaces = false;

    protected PrefixPool prefixPool = new PrefixPool();

    //private TinyDocumentImpl root;
    private HashMap<String, NodeInfo> idTable;
    protected HashMap<String, String[]> entityTable;

    private NodeInfo copiedFrom;

    protected IntHashMap<String> knownBaseUris;

    // uniformBaseUri is set if all nodes in the tree have the same Base URI; otherwise it is null.
    private String uniformBaseUri = null;

    /**
     * Create a tree with a specified initial size
     *
     * @param config     the Saxon configuration
     * @param statistics the size parameters for the tree
     */

    public TinyTree(/*@NotNull*/ Configuration config, Statistics statistics) {
        super(config);
        //Instrumentation.count("TinyTree instances");

        int nodes = (int) statistics.getAverageNodes() + 1;
        int attributes = (int) statistics.getAverageAttributes() + 1;
        int namespaces = (int) statistics.getAverageNamespaces() + 1;
        int characters = (int) statistics.getAverageCharacters() + 1;

        nodeKind = new byte[nodes];
        depth = new short[nodes];
        next = new int[nodes];
        alpha = new int[nodes];
        beta = new int[nodes];
        nameCode = new int[nodes];

        numberOfAttributes = 0;
        attParent = new int[attributes];
        attCode = new int[attributes];
        attValue = new String[attributes];

        numberOfNamespaces = 0;
        namespaceMaps = new NamespaceMap[namespaces];

        charBuffer = characters > 65000 ? new LargeStringBuffer() : new FastStringBuffer(characters);

        setConfiguration(config);
    }

    /**
     * Set the Configuration that contains this document
     *
     * @param config the Saxon configuration
     */

    @Override
    public void setConfiguration(/*@NotNull*/ Configuration config) {
        super.setConfiguration(config);
        allowTypedValueCache = config.isLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION) &&
                config.getBooleanProperty(Feature.USE_TYPED_VALUE_CACHE);
        //addNamespace(0, NamespaceBinding.XML);
    }

    private void ensureNodeCapacity(short kind, int needed) {
        if (nodeKind.length < numberOfNodes + needed) {
            //System.err.println("Number of nodes = " + numberOfNodes);
            int k = kind == Type.STOPPER ? numberOfNodes + 1 : Math.max(numberOfNodes * 2, numberOfNodes + needed);

            nodeKind = Arrays.copyOf(nodeKind, k);
            next = Arrays.copyOf(next, k);
            depth = Arrays.copyOf(depth, k);
            alpha = Arrays.copyOf(alpha, k);
            beta = Arrays.copyOf(beta, k);
            nameCode = Arrays.copyOf(nameCode, k);

            if (typeArray != null) {
                typeArray = Arrays.copyOf(typeArray, k);
            }

            if (typedValueArray != null) {
                typedValueArray = Arrays.copyOf(typedValueArray, k);
            }

            if (lineNumbers != null) {
                lineNumbers = Arrays.copyOf(lineNumbers, k);
                columnNumbers = Arrays.copyOf(columnNumbers, k);
            }
        }
    }

    private void ensureAttributeCapacity(int needed) {
        if (attParent.length < numberOfAttributes + needed) {
            int k = Math.max(numberOfAttributes + needed, numberOfAttributes * 2);
            if (k == 0) {
                k = 10 + needed;
            }

            attParent = Arrays.copyOf(attParent, k);
            attCode = Arrays.copyOf(attCode, k);
            attValue = Arrays.copyOf(attValue, k);

            if (attType != null) {
                attType = Arrays.copyOf(attType, k);
            }

            if (attTypedValue != null) {
                attTypedValue = Arrays.copyOf(attTypedValue, k);
            }
        }
    }

    private void ensureNamespaceCapacity(int needed) {
        if (namespaceMaps.length < numberOfNamespaces + needed) {
            int k = Math.max(numberOfNamespaces * 2, numberOfNamespaces + needed);
            if (k == 0) {
                k = 10;
            }
            namespaceMaps = Arrays.copyOf(namespaceMaps, k);
        }
    }

    /**
     * Get the prefix pool
     *
     * @return the prefix pool
     */

    public PrefixPool getPrefixPool() {
        return prefixPool;
    }

    /**
     * Declare that this tree was produced as a copy of another tree, and identify
     * the root node of that tree
     *
     * @param copiedFrom the root of the tree from which this one was copied
     */

    public void setCopiedFrom(NodeInfo copiedFrom) {
        this.copiedFrom = copiedFrom;
    }

    /**
     * Declare that this tree was produced as a copy of another tree, and identify
     * the root node of that tree
     *
     * @return the root of the tree from which this one was copied, or null if this
     * does not apply
     */

    public NodeInfo getCopiedFrom() {
        return this.copiedFrom;
    }

    /**
     * Add a document node to the tree. The data structure since 9.7 can only hold one tree, therefore at
     * most one document node, which must be node zero.
     *
     * @param doc the document node to be added
     * @return the number of the node that was added
     */

    int addDocumentNode(TinyDocumentImpl doc) {
        setRootNode(doc);
        return addNode(Type.DOCUMENT, 0, 0, 0, -1);
    }

    /**
     * Add a node to the tree
     *
     * @param kind     The kind of the node. This must be a document, element, text, comment,
     *                 or processing-instruction node (not an attribute or namespace)
     * @param depth    The depth in the tree
     * @param alpha    Pointer to attributes or text
     * @param beta     Pointer to namespaces or text
     * @param nameCode The name of the node
     * @return the node number of the node that was added
     */
    int addNode(short kind, int depth, int alpha, int beta, int nameCode) {

        ensureNodeCapacity(kind, 1);
        nodeKind[numberOfNodes] = (byte) kind;
        this.depth[numberOfNodes] = (short) depth;
        this.alpha[numberOfNodes] = alpha;
        this.beta[numberOfNodes] = beta;
        this.nameCode[numberOfNodes] = nameCode;
        next[numberOfNodes] = -1;      // safety precaution

        if (typeArray != null) {
            typeArray[numberOfNodes] = Untyped.getInstance();
        }

        if (numberOfNodes == 0) {
            setDocumentNumber(getConfiguration().getDocumentNumberAllocator().allocateDocumentNumber());
        }

//        if (depth == 0 && kind != Type.STOPPER) {
//            if (rootIndexUsed == rootIndex.length) {
//                int[] r2 = new int[rootIndexUsed * 2];
//                System.arraycopy(rootIndex, 0, r2, 0, rootIndexUsed);
//                rootIndex = r2;
//            }
//            rootIndex[rootIndexUsed++] = numberOfNodes;
//        }
        return numberOfNodes++;
    }

    /**
     * Append character data to the current text node
     *
     * @param chars the character data to be appended
     */

    void appendChars(CharSequence chars) {
        if (charBuffer instanceof FastStringBuffer && charBuffer.length() > 65000) {
            LargeStringBuffer lsb = new LargeStringBuffer();
            charBuffer = lsb.cat(charBuffer);
        }
        charBuffer.cat(chars);
    }

    /**
     * Create a new text node that is a copy of an existing text node
     *
     * @param depth          the depth of the new node
     * @param existingNodeNr the node to be copied
     * @return the node number of the new node
     */

    public int addTextNodeCopy(int depth, int existingNodeNr) {
        return addNode(Type.TEXT, depth, alpha[existingNodeNr], beta[existingNodeNr], -1);
    }

    /**
     * Condense the tree: release unused memory. This is done after the full tree has been built.
     * The method makes a pragmatic judgement as to whether it is worth reclaiming space; this is
     * only done when the constructed tree is very small compared with the space allocated.
     *
     * @param statistics represents the family of trees to which this tree belongs; statistics for the
     *                   size of the tree are recorded in this Statistics object
     */

    void condense(Statistics statistics) {
        //System.err.println("TinyTree.condense() " + this + " roots " + rootIndexUsed + " nodes " + numberOfNodes + " capacity " + nodeKind.length);

        if (numberOfNodes * 3 < nodeKind.length ||
                (nodeKind.length - numberOfNodes > 20000)) {

            //System.err.println("-- copying node arrays");

            nodeKind = Arrays.copyOf(nodeKind, numberOfNodes);
            next = Arrays.copyOf(next, numberOfNodes);
            depth = Arrays.copyOf(depth, numberOfNodes);
            alpha = Arrays.copyOf(alpha, numberOfNodes);
            beta = Arrays.copyOf(beta, numberOfNodes);
            nameCode = Arrays.copyOf(nameCode, numberOfNodes);

            if (typeArray != null) {
                typeArray = Arrays.copyOf(typeArray, numberOfNodes);
            }
            if (lineNumbers != null) {
                lineNumbers = Arrays.copyOf(lineNumbers, numberOfNodes);
                columnNumbers = Arrays.copyOf(columnNumbers, numberOfNodes);
            }

        }

        if ((numberOfAttributes * 3 < attParent.length) ||
                (attParent.length - numberOfAttributes > 1000)) {
            int k = numberOfAttributes;

            //System.err.println("-- copying attribute arrays");

            if (k == 0) {
                attParent = IntArraySet.EMPTY_INT_ARRAY;
                attCode = IntArraySet.EMPTY_INT_ARRAY;
                attValue = EMPTY_STRING_ARRAY;
                attType = null;
            } else {
                attParent = Arrays.copyOf(attParent, numberOfAttributes);
                attCode = Arrays.copyOf(attCode, numberOfAttributes);
                attValue = Arrays.copyOf(attValue, numberOfAttributes);
            }

            if (attType != null) {
                attType = Arrays.copyOf(attType, numberOfAttributes);
            }
        }

        if (numberOfNamespaces * 3 < namespaceMaps.length) {
            namespaceMaps = Arrays.copyOf(namespaceMaps, numberOfNamespaces);
        }

        prefixPool.condense();

        statistics.updateStatistics(numberOfNodes, numberOfAttributes, numberOfNamespaces, charBuffer.length());
//        System.err.println("STATS: " + averageNodes + ", " + averageAttributes + ", "
//                + averageNamespaces + ", " + averageCharacters);

//        if (charBufferLength * 3 < charBuffer.length ||
//                charBuffer.length - charBufferLength > 10000) {
//            char[] c2 = new char[charBufferLength];
//            System.arraycopy(charBuffer,  0, c2, 0, charBufferLength);
//            charBuffer = c2;
//        }
    }

    /**
     * Set the type annotation of an element node
     *
     * @param nodeNr the node whose type annotation is to be set
     * @param type   the type annotation
     */

    void setElementAnnotation(int nodeNr, SchemaType type) {
        if (!type.equals(Untyped.getInstance())) {
            if (typeArray == null) {
                typeArray = new SchemaType[nodeKind.length];
                Arrays.fill(typeArray, 0, nodeKind.length, Untyped.getInstance());
            }
            assert typeArray != null;
            typeArray[nodeNr] = type;
        }
    }

    /**
     * Get the type annotation of a node. Applies only to document, element, text,
     * processing instruction, and comment nodes.
     *
     * @param nodeNr the node whose type annotation is required
     * @return the fingerprint of the type annotation for elements and attributes, otherwise undefined.
     */

    public int getTypeAnnotation(int nodeNr) {
        if (typeArray == null) {
            return StandardNames.XS_UNTYPED;
        }
        return typeArray[nodeNr].getFingerprint();
    }

    /**
     * Get the type annotation of a node. Applies only to document, element, text,
     * processing instruction, and comment nodes.
     *
     * @param nodeNr the node whose type annotation is required
     * @return the fingerprint of the type annotation for elements and attributes, otherwise undefined.
     */

    public SchemaType getSchemaType(int nodeNr) {
        if (typeArray == null) {
            return Untyped.getInstance();
        }
        return typeArray[nodeNr];
    }

    /**
     * Get the typed value of an element node.
     *
     * @param element the element node
     * @return the typed value of the node (a Value whose items are AtomicValue instances)
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs, for example if the node is
     *                                           an element annotated with a type that has element-only content
     */

    /*@Nullable*/
    public AtomicSequence getTypedValueOfElement(/*@NotNull*/ TinyElementImpl element) throws XPathException {
        int nodeNr = element.nodeNr;
        if (typedValueArray == null || typedValueArray[nodeNr] == null) {
            SchemaType stype = getSchemaType(nodeNr);
            int annotation = stype.getFingerprint();
            if (annotation == StandardNames.XS_UNTYPED || annotation == StandardNames.XS_UNTYPED_ATOMIC ||
                    annotation == StandardNames.XS_ANY_TYPE) {
                CharSequence stringValue = TinyParentNodeImpl.getStringValueCS(this, nodeNr);
                return new UntypedAtomicValue(stringValue);
            } else if (annotation == StandardNames.XS_STRING) {
                CharSequence stringValue = TinyParentNodeImpl.getStringValueCS(this, nodeNr);
                return new StringValue(stringValue);
            } else if (annotation == StandardNames.XS_ANY_URI) {
                CharSequence stringValue = TinyParentNodeImpl.getStringValueCS(this, nodeNr);
                return new AnyURIValue(stringValue);
            } else {
                AtomicSequence value = stype.atomize(element);
                if (allowTypedValueCache) {
                    if (typedValueArray == null) {
                        //noinspection unchecked
                        typedValueArray = new AtomicSequence[nodeKind.length];
                    }
                    typedValueArray[nodeNr] = value;
                }
                return value;
            }
        } else {
            return typedValueArray[nodeNr];
        }
    }

    /**
     * Get the type value of an element node, given only the node number
     *
     * @param nodeNr the node number of the element node
     * @return the typed value of the node
     * @throws net.sf.saxon.trans.XPathException if the eement has no typed value
     */

    /*@Nullable*/
    public AtomicSequence getTypedValueOfElement(int nodeNr) throws XPathException {
        if (typedValueArray == null || typedValueArray[nodeNr] == null) {
            SchemaType stype = getSchemaType(nodeNr);
            int annotation = stype.getFingerprint();
            if (annotation == StandardNames.XS_UNTYPED_ATOMIC || annotation == StandardNames.XS_UNTYPED) {
                CharSequence stringValue = TinyParentNodeImpl.getStringValueCS(this, nodeNr);
                return new UntypedAtomicValue(stringValue);
            } else if (annotation == StandardNames.XS_STRING) {
                CharSequence stringValue = TinyParentNodeImpl.getStringValueCS(this, nodeNr);
                return new StringValue(stringValue);
            } else if (annotation == StandardNames.XS_ANY_URI) {
                CharSequence stringValue = TinyParentNodeImpl.getStringValueCS(this, nodeNr);
                return new AnyURIValue(stringValue);
            } else if (annotation == StandardNames.XS_ID) {
                CharSequence stringValue = TinyParentNodeImpl.getStringValueCS(this, nodeNr);
                return new StringValue(stringValue, BuiltInAtomicType.ID);
            } else {
                TinyNodeImpl element = getNode(nodeNr);
                AtomicSequence value = stype.atomize(element);
                if (allowTypedValueCache) {
                    if (typedValueArray == null) {
                        //noinspection unchecked
                        typedValueArray = new AtomicSequence[nodeKind.length];
                    }
                    typedValueArray[nodeNr] = value;
                }
                return value;
            }
        } else {
            return typedValueArray[nodeNr];
        }
    }

    /**
     * Get the typed value of an attribute node. This method avoids
     * materializing the attribute node if possible, but uses the attribute node
     * supplied if it already exists.
     *
     * @param att    the attribute node if available. If null is supplied, the attribute node
     *               will be materialized only if it is needed.
     * @param nodeNr the node number of the attribute node
     * @return the typed value of the node
     * @throws net.sf.saxon.trans.XPathException if an error is found
     */

    public AtomicSequence getTypedValueOfAttribute(/*@Nullable*/ TinyAttributeImpl att, int nodeNr) throws XPathException {
        if (attType == null) {
            // it's an untyped tree
            return new UntypedAtomicValue(attValue[nodeNr]);
        }
        if (attTypedValue == null || attTypedValue[nodeNr] == null) {
            SimpleType type = getAttributeType(nodeNr);
            if (type.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                return new UntypedAtomicValue(attValue[nodeNr]);
            } else if (type.equals(BuiltInAtomicType.STRING)) {
                return new StringValue(attValue[nodeNr]);
            } else if (type.equals(BuiltInAtomicType.ANY_URI)) {
                return new AnyURIValue(attValue[nodeNr]);
            } else {
                if (att == null) {
                    att = new TinyAttributeImpl(this, nodeNr);
                }
                AtomicSequence value = type.atomize(att);
                if (allowTypedValueCache) {
                    if (attTypedValue == null) {
                        //noinspection unchecked
                        attTypedValue = new AtomicSequence[attParent.length];
                    }
                    attTypedValue[nodeNr] = value;
                }
                return value;

            }
        } else {
            return attTypedValue[nodeNr];
        }
    }


    /**
     * Get the node kind of a given node, which must be a document, element,
     * text, comment, or processing instruction node
     *
     * @param nodeNr the node number
     * @return the node kind
     */

    @Override
    public int getNodeKind(int nodeNr) {
        int kind = nodeKind[nodeNr];
        return kind == Type.WHITESPACE_TEXT ? Type.TEXT : kind;
    }

    /**
     * Get the nameCode for a given node, which must be a document, element,
     * text, comment, or processing instruction node
     *
     * @param nodeNr the node number
     * @return the name code
     */

    public int getNameCode(int nodeNr) {
        return nameCode[nodeNr];
    }

    /**
     * Get the fingerprint for a given node, which must be a document, element,
     * text, comment, or processing instruction node
     *
     * @param nodeNr the node number
     * @return the name code
     */

    @Override
    public int getFingerprint(int nodeNr) {
        int nc = nameCode[nodeNr];
        return nc == -1 ? -1 : nc & NamePool.FP_MASK;
    }


    /**
     * Get the prefix for a given element node
     *
     * @param nodeNr the node number
     * @return the prefix. Return "" for an unprefixed element node. Result is
     * undefined for a non-element node.
     */

    public String getPrefix(int nodeNr) {
        int code = nameCode[nodeNr] >> 20;
        if (code <= 0) {
            return code == 0 ? "" : null;
        }
        return prefixPool.getPrefix(code);
    }

    /**
     * On demand, make an index for quick access to preceding-sibling nodes
     */

    void ensurePriorIndex() {
        if (prior == null || prior.length < numberOfNodes) { // bug 3665
            makePriorIndex();
        }
    }

    private synchronized void makePriorIndex() {
        int[] p = new int[numberOfNodes];
        Arrays.fill(p, 0, numberOfNodes, -1);
        for (int i = 0; i < numberOfNodes; i++) {
            int nextNode = next[i];
            if (nextNode > i) {
                p[nextNode] = i;
            }
        }
        prior = p;
    }

    /**
     * Add an attribute node to the tree
     *
     * @param root       the root of the tree to contain the attribute
     * @param parent     the parent element of the new attribute
     * @param nameCode   the name code of the attribute
     * @param type       the type annotation of the attribute
     * @param attValue   the string value of the attribute
     * @param properties any special properties of the attribute (bit-significant)
     */


    void addAttribute(/*@NotNull*/ NodeInfo root, int parent, int nameCode, SimpleType type, CharSequence attValue, int properties) {
        ensureAttributeCapacity(1);
        attParent[numberOfAttributes] = parent;
        attCode[numberOfAttributes] = nameCode;
        this.attValue[numberOfAttributes] = attValue.toString();

        if (!type.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            initializeAttributeTypeCodes();
        }

        if (attType != null) {
            attType[numberOfAttributes] = type;
        }

        if (alpha[parent] == -1) {
            alpha[parent] = numberOfAttributes;
        }

        if (root instanceof TinyDocumentImpl) {
            boolean isID = false;
            try {
                if (ReceiverOption.contains(properties, ReceiverOption.IS_ID)) {
                    isID = true;
                } else if ((nameCode & NamePool.FP_MASK) == StandardNames.XML_ID) {
                    isID = true;
                } else if (type.isIdType()) {
                    isID = true;
                }
            } catch (MissingComponentException e) {
                // isID = false;
            }
            if (isID) {

                // The attribute is marked as being an ID. But we don't trust it - it
                // might come from a non-validating parser. Before adding it to the index, we
                // check that it really is an ID.

                String id = Whitespace.trim(attValue);

                // Make an exception to our usual policy of storing the original string value.
                // This is because xml:id processing applies whitespace trimming at an earlier stage
                this.attValue[numberOfAttributes] = id;

                if (NameChecker.isValidNCName(id)) {
                    NodeInfo e = getNode(parent);
                    registerID(e, id);
                } else if (attType != null) {
                    attType[numberOfAttributes] = BuiltInAtomicType.UNTYPED_ATOMIC;
                }
            }
            boolean isIDREF = false;
            try {
                if (ReceiverOption.contains(properties, ReceiverOption.IS_IDREF)) {
                    isIDREF = true;
                } else if (type == BuiltInAtomicType.IDREF || type == BuiltInListType.IDREFS) {
                    isIDREF = true;
                } else if (type.isIdRefType()) {
                    // The attribute has the idref property only if at least one item in its typed value
                    // is an IDREF: see Saxon bug 2331
                    try {
                        AtomicSequence as = type.getTypedValue(attValue, null, getConfiguration().getConversionRules());
                        for (AtomicValue v : as) {
                            if (v.getItemType().isIdRefType()) {
                                isIDREF = true;
                                break;
                            }
                        }
                    } catch (ValidationException ve) {
                        // isIDREF = false
                    }
                }
            } catch (MissingComponentException e) {
                // isIDREF = false
            }
            if (isIDREF) {
                if (idRefAttributes == null) {
                    idRefAttributes = new IntHashSet();
                }
                idRefAttributes.add(numberOfAttributes);
            }
        }

        // Note: IDREF attributes are not indexed at this stage; that happens only if and when
        // the idref() function is called.

        // Note that an attTypes array will be created for all attributes if any IDREF value is reported.

        numberOfAttributes++;
    }

    private void initializeAttributeTypeCodes() {
        if (attType == null) {
            // this is the first typed attribute;
            // create an array for the types, and set all previous attributes to untyped
            attType = new SimpleType[attParent.length];
            Arrays.fill(attType, 0, numberOfAttributes, BuiltInAtomicType.UNTYPED_ATOMIC);
//            for (int i=0; i<numberOfAttributes; i++) {
//                attTypeCode[i] = StandardNames.XDT_UNTYPED_ATOMIC;
//            }
        }
    }

    /**
     * Mark an attribute as resulting from expansion of attribute defaults
     *
     * @param attNr the attribute number
     */

    public void markDefaultedAttribute(int attNr) {
        if (defaultedAttributes == null) {
            defaultedAttributes = new IntHashSet();
        }
        defaultedAttributes.add(attNr);
    }

    /**
     * Ask whether an attribute results from expansion of attribute defaults
     *
     * @param attNr the attribute number
     * @return true if this attribute resulted from expansion of default or fixed values defined
     * in a schema. Note that this property will only be set if both the configuration properties
     * {@link FeatureKeys#EXPAND_ATTRIBUTE_DEFAULTS} and {@link FeatureKeys#MARK_DEFAULTED_ATTRIBUTES}
     * are set.
     */

    public boolean isDefaultedAttribute(int attNr) {
        return defaultedAttributes != null && defaultedAttributes.contains(attNr);
    }

    /**
     * Index an element of type xs:ID
     *
     * @param root   the root node of the document
     * @param nodeNr the element of type xs:ID
     */

    public void indexIDElement(/*@NotNull*/ NodeInfo root, int nodeNr) {
        String id = Whitespace.trim(TinyParentNodeImpl.getStringValueCS(this, nodeNr));
        if (root.getNodeKind() == Type.DOCUMENT && NameChecker.isValidNCName(id)) {
            NodeInfo e = getNode(nodeNr);
            registerID(e, id);
        }
    }

    /**
     * Ask whether, somewhere in the tree, there is an attribute xml:space="preserve"
     *
     * @return true if some element in the tree has an xml:space attribute with the value preserve
     */

    public boolean hasXmlSpacePreserveAttribute() {
        for (int i = 0; i < numberOfAttributes; i++) {
            if ((attCode[i] & NamePool.FP_MASK) == StandardNames.XML_SPACE && "preserve".equals(attValue[i].toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a set of namespace bindings to the current element
     *
     * @param parent  the node number of the element
     * @param nsMap namespace map identifying the prefix and uri
     */
    void addNamespaces(int parent, /*@NotNull*/ NamespaceMap nsMap) {
        usesNamespaces = true;
        // reuse existing entry if possible
        for (int i=0; i<numberOfNamespaces; i++) {
            if (namespaceMaps[i].equals(nsMap)) {
                beta[parent] = i;
                return;
            }
        }
        ensureNamespaceCapacity(1);
        namespaceMaps[numberOfNamespaces] = nsMap;
        beta[parent] = numberOfNamespaces;
        numberOfNamespaces++;
    }

    /**
     * Get the node at a given position in the tree
     *
     * @param nr the node number
     * @return the node at the given position
     */

    @Override
    public final TinyNodeImpl getNode(int nr) {
        switch (nodeKind[nr]) {
            case Type.DOCUMENT:
                return (TinyDocumentImpl) getRootNode();
            case Type.ELEMENT:
                return new TinyElementImpl(this, nr);
            case Type.TEXTUAL_ELEMENT:
                return new TinyTextualElement(this, nr);
            case Type.TEXT:
                return new TinyTextImpl(this, nr);
            case Type.WHITESPACE_TEXT:
                return new WhitespaceTextImpl(this, nr);
            case Type.COMMENT:
                return new TinyCommentImpl(this, nr);
            case Type.PROCESSING_INSTRUCTION:
                return new TinyProcInstImpl(this, nr);
            case Type.PARENT_POINTER:
                throw new IllegalArgumentException("Attempting to treat a parent pointer as a node");
            case Type.STOPPER:
                throw new IllegalArgumentException("Attempting to treat a stopper entry as a node");
            default:
                throw new IllegalStateException("Unknown node kind " + nodeKind[nr]);
        }
    }

    /**
     * Get the typed value of a node whose type is known to be untypedAtomic.
     * The node must be a document, element, text,
     * comment, or processing-instruction node, and it must have no type annotation.
     * This method gets the typed value
     * of a numbered node without actually instantiating the NodeInfo object, as
     * a performance optimization.
     *
     * @param nodeNr the node whose typed value is required
     * @return the atomic value of the node
     */

    AtomicValue getAtomizedValueOfUntypedNode(int nodeNr) {
        switch (nodeKind[nodeNr]) {
            case Type.ELEMENT:
            case Type.DOCUMENT:
                int level = depth[nodeNr];
                int next = nodeNr + 1;

                // we optimize two special cases: firstly, where the node has no children, and secondly,
                // where it has a single text node as a child.

                if (depth[next] <= level) {
                    return UntypedAtomicValue.ZERO_LENGTH_UNTYPED;
                } else if (nodeKind[next] == Type.TEXT && depth[next + 1] <= level) {
                    int length = beta[next];
                    int start = alpha[next];
                    return new UntypedAtomicValue(charBuffer.subSequence(start, start + length));
                } else if (nodeKind[next] == Type.WHITESPACE_TEXT && depth[next + 1] <= level) {
                    return new UntypedAtomicValue(WhitespaceTextImpl.getStringValueCS(this, next));
                }

                // Now handle the general case

                FastStringBuffer sb = null;
                while (next < numberOfNodes && depth[next] > level) {
                    if (nodeKind[next] == Type.TEXT) {
                        if (sb == null) {
                            sb = new FastStringBuffer(FastStringBuffer.C256);
                        }
                        sb.cat(TinyTextImpl.getStringValue(this, next));
                    } else if (nodeKind[next] == Type.WHITESPACE_TEXT) {
                        if (sb == null) {
                            sb = new FastStringBuffer(FastStringBuffer.C256);
                        }
                        WhitespaceTextImpl.appendStringValue(this, next, sb);
                    }
                    next++;
                }
                if (sb == null) {
                    return UntypedAtomicValue.ZERO_LENGTH_UNTYPED;
                } else {
                    return new UntypedAtomicValue(sb.condense());
                }

            case Type.TEXT:
                return new UntypedAtomicValue(TinyTextImpl.getStringValue(this, nodeNr));
            case Type.WHITESPACE_TEXT:
                return new UntypedAtomicValue(WhitespaceTextImpl.getStringValueCS(this, nodeNr));
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
                int start2 = alpha[nodeNr];
                int len2 = beta[nodeNr];
                if (len2 == 0) {
                    return UntypedAtomicValue.ZERO_LENGTH_UNTYPED;
                }
                char[] dest = new char[len2];
                assert commentBuffer != null;
                commentBuffer.getChars(start2, start2 + len2, dest, 0);
                return new StringValue(new CharSlice(dest, 0, len2));
            default:
                throw new IllegalStateException("Unknown node kind");
        }
    }

    /**
     * Make a (transient) attribute node from the array of attributes
     *
     * @param nr the node number of the attribute
     * @return an attribute node
     */

    /*@NotNull*/ TinyAttributeImpl getAttributeNode(int nr) {
        return new TinyAttributeImpl(this, nr);
    }

    /**
     * Get the type annotation of an attribute node.
     *
     * @param nr the node number of the attribute
     * @return the fingerprint of the type annotation, or Type.UNTYPED_ATOMIC if there is no annotation
     */

    int getAttributeAnnotation(int nr) {
        if (attType == null) {
            return StandardNames.XS_UNTYPED_ATOMIC;
        } else {
            return attType[nr].getFingerprint();
        }
    }

    /**
     * Get the type annotation of an attribute node.
     *
     * @param nr the node number of the attribute
     * @return the fingerprint of the type annotation, or Type.UNTYPED_ATOMIC if there is no annotation
     */

    SimpleType getAttributeType(int nr) {
        if (attType == null) {
            return BuiltInAtomicType.UNTYPED_ATOMIC;
        } else {
            return attType[nr];
        }
    }

    /**
     * Determine whether an attribute is an ID attribute. (The represents the
     * is-id property in the data model)
     *
     * @param nr the node number of the attribute
     * @return true if this is an ID attribute
     */

    public boolean isIdAttribute(int nr) {
        try {
            return attType != null && getAttributeType(nr).isIdType();
        } catch (MissingComponentException e) {
            return false;
        }
    }


    /**
     * Determine whether an attribute is an IDREF/IDREFS attribute. (The represents the
     * is-idref property in the data model)
     *
     * @param nr the node number of the attribute
     * @return true if this is an IDREF/IDREFS attribute
     */

    public boolean isIdrefAttribute(int nr) {
        return idRefAttributes != null && idRefAttributes.contains(nr);
    }

    /**
     * Ask whether an element is an ID element. (The represents the
     * is-id property in the data model)
     *
     * @param nr the element node whose is-id property is required
     * @return true if the node has the is-id property
     */

    public boolean isIdElement(int nr) {
        try {
            return getSchemaType(nr).isIdType() && getTypedValueOfElement(nr).getLength() == 1;
        } catch (XPathException e) {
            return false;
        }
    }

    /**
     * Ask whether an element is an IDREF/IDREFS element. (The represents the
     * is-idref property in the data model)
     *
     * @param nr the element node whose is-idref property is required
     * @return true if the node has the is-idref property
     */

    public boolean isIdrefElement(int nr) {
        SchemaType type = getSchemaType(nr);
        try {
            if (type.isIdRefType()) {
                if (type == BuiltInAtomicType.IDREF || type == BuiltInListType.IDREFS) {
                    return true;
                }
                try {
                    for (AtomicValue av : getTypedValueOfElement(nr)) {
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


    /**
     * Set the system id of an element in the document. This identifies the external entity containing
     * the node - this is not necessarily the same as the base URI.
     *
     * @param seq the node number
     * @param uri the system ID
     */

    void setSystemId(int seq, /*@Nullable*/ String uri) {
        if (uri == null) {
            uri = "";
        }
        if (systemIdMap == null) {
            systemIdMap = new SystemIdMap();
        }
        systemIdMap.setSystemId(seq, uri);
        if (getSystemId(0) == null) {
            setSystemId(0, uri);
        }
    }

    void setUniformBaseUri(String base) {
        this.uniformBaseUri = base;
    }

    String getUniformBaseUri() {
        return this.uniformBaseUri;
    }
    /**
     * Get the system id of an element in the document
     *
     * @param seq the node number of the element node
     * @return the system id (base URI) of the element
     */

    /*@Nullable*/
    public String getSystemId(int seq) {
        if (systemIdMap == null) {
            return null;
        }
        return systemIdMap.getSystemId(seq);
    }


    @Override
    public NodeInfo getRootNode() {
        if (getNodeKind(0) == Type.DOCUMENT) {
            if (root != null) {
                return root;
            } else {
                root = new TinyDocumentImpl(this);
                return root;
            }
        } else {
            return getNode(0);
        }
    }

    /**
     * Set line numbering on
     */

    public void setLineNumbering() {
        lineNumbers = new int[nodeKind.length];
        Arrays.fill(lineNumbers, -1);
        columnNumbers = new int[nodeKind.length];
        Arrays.fill(columnNumbers, -1);
    }

    /**
     * Set the line number for a node. Ignored if line numbering is off.
     *
     * @param sequence the node number
     * @param line     the line number to be set for the  node
     * @param column   the column number for the node
     */

    void setLineNumber(int sequence, int line, int column) {
        if (lineNumbers != null) {
            assert columnNumbers != null;
            lineNumbers[sequence] = line;
            columnNumbers[sequence] = column;
        }
    }

    /**
     * Get the line number for a node.
     *
     * @param sequence the node number
     * @return the line number of the node. Return -1 if line numbering is off.
     */

    public int getLineNumber(int sequence) {
        if (lineNumbers != null) {
            // find the nearest preceding node that has a known line number, and return it
            for (int i = sequence; i >= 0; i--) {
                int c = lineNumbers[i];
                if (c > 0) {
                    return c;
                }
            }
        }
        return -1;
    }

    /**
     * Get the column number for a node.
     *
     * @param sequence the node number
     * @return the line number of the node. Return -1 if line numbering is off.
     */

    public int getColumnNumber(int sequence) {
        if (columnNumbers != null) {
            // find the nearest preceding node that has a known column number, and return it
            for (int i = sequence; i >= 0; i--) {
                int c = columnNumbers[i];
                if (c > 0) {
                    return c;
                }
            }
        }
        return -1;
    }

    /**
     * Set an element node to be marked as nilled
     *
     * @param nodeNr the node number to be marked as nilled
     */

    public void setNilled(int nodeNr) {
        if (nilledElements == null) {
            nilledElements = new IntHashSet();
        }
        nilledElements.add(nodeNr);
    }

    /**
     * Ask whether a given node is nilled
     *
     * @param nodeNr the node in question (which must be an element node)
     * @return true if the node has the nilled property
     */

    public boolean isNilled(int nodeNr) {
        return nilledElements != null && nilledElements.contains(nodeNr);
    }

    /**
     * Register a unique element ID. Fails if there is already an element with that ID.
     *
     * @param e  The NodeInfo (always an element) having a particular unique ID value
     * @param id The unique ID value. The caller is responsible for checking that this
     *           is a valid NCName.
     */

    void registerID(NodeInfo e, String id) {
        if (idTable == null) {
            idTable = new HashMap<>(256);
        }

        // the XPath spec (5.2.1) says ignore the second ID if it's not unique
        idTable.putIfAbsent(id, e);

    }

    /**
     * Get the element with a given ID.
     *
     * @param id        The unique ID of the required element, previously registered using registerID()
     * @param getParent true if the required element is the parent of the element of type ID
     * @return The NodeInfo (always an Element) for the given ID if one has been registered,
     * otherwise null.
     */

    /*@Nullable*/
    @Override
    public NodeInfo selectID(String id, boolean getParent) {
        if (idTable == null) {
            return null;            // no ID values found
        }
        NodeInfo node = idTable.get(id);
        if (node != null && getParent && node.isId() && node.getStringValue().equals(id)) {
            node = node.getParent();
        }
        return node;
    }


    /**
     * Set an unparsed entity URI associated with this document. For system use only, while
     * building the document.
     *
     * @param name     the name of the unparsed entity
     * @param uri      the system identifier of the unparsed entity
     * @param publicId the public identifier of the unparsed entity
     */

    void setUnparsedEntity(String name, String uri, String publicId) {
        if (entityTable == null) {
            entityTable = new HashMap<>(20);
        }
        String[] ids = new String[2];
        ids[0] = uri;
        ids[1] = publicId;
        entityTable.put(name, ids);
    }

    /**
     * Get the list of unparsed entities defined in this document
     *
     * @return an Iterator, whose items are of type String, containing the names of all
     * unparsed entities defined in this document. If there are no unparsed entities or if the
     * information is not available then an empty iterator is returned
     */

    @Override
    public Iterator<String> getUnparsedEntityNames() {
        if (entityTable == null) {
            List<String> emptyList = Collections.emptyList();
            return emptyList.iterator();
        } else {
            return entityTable.keySet().iterator();
        }
    }

    /**
     * Get the unparsed entity with a given nameID if there is one, or null if not. If the entity
     * does not exist, return null.
     *
     * @param name the name of the entity
     * @return if the entity exists, return an array of two Strings, the first holding the system ID
     * of the entity, the second holding the public
     */

    /*@Nullable*/
    @Override
    public String[] getUnparsedEntity(String name) {
        if (entityTable == null) {
            return null;
        }
        return entityTable.get(name);
    }

    public NamePool getNamePool() {
        return getConfiguration().getNamePool();
    }

    public void markTopWithinEntity(int nodeNr) {
        if (topWithinEntity == null) {
            topWithinEntity = new IntHashSet();
        }
        topWithinEntity.add(nodeNr);
    }

    public boolean isTopWithinEntity(int nodeNr) {
        return topWithinEntity != null && topWithinEntity.contains(nodeNr);
    }


    /**
     * Produce diagnostic print of main tree arrays
     */

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public void diagnosticDump() {
        NamePool pool = getNamePool();
        System.err.println("    node    kind   depth    next   alpha    beta    name    type");
        for (int i = 0; i < numberOfNodes; i++) {
            String eqName = "";
            if (nameCode[i] != -1) {
                try {
                    eqName = pool.getEQName(nameCode[i]);
                } catch (Exception err) {
                    eqName = "#" + nameCode[1];
                }
            }
            System.err.println(n8(i) + n8(nodeKind[i]) + n8(depth[i]) + n8(next[i]) +
                                       n8(alpha[i]) + n8(beta[i]) + n8(nameCode[i]) +
                                       n8(getTypeAnnotation(i)) + " " + eqName);
        }
        System.err.println("    attr  parent    name    value");
        for (int i = 0; i < numberOfAttributes; i++) {
            System.err.println(n8(i) + n8(attParent[i]) + n8(attCode[i]) + "    " + attValue[i]);
        }
        System.err.println("      ns  parent  prefix     uri");
        for (int i = 0; i < numberOfNamespaces; i++) {
            System.err.println(n8(i) + "  " + namespaceMaps[i]);
        }
    }

    /**
     * Create diagnostic dump of the tree containing a particular node.
     * Designed to be called as an extension function for diagnostics.
     *
     * @param node the node in question
     */

    public static synchronized void diagnosticDump(/*@NotNull*/ NodeInfo node) {
        if (node instanceof TinyNodeImpl) {
            TinyTree tree = ((TinyNodeImpl) node).tree;
            System.err.println("Tree containing node " + ((TinyNodeImpl) node).nodeNr);
            tree.diagnosticDump();
        } else {
            System.err.println("Node is not in a TinyTree");
        }
    }

    /**
     * Output a number as a string of 8 characters
     *
     * @param val the number
     * @return the string representation of the number, aligned in a fixed width field
     */

    private static String n8(int val) {
        String s = "        " + val;
        return s.substring(s.length() - 8);
    }

    /**
     * Output a statistical summary to System.err
     */

    public void showSize() {
        System.err.println("Tree size: " + numberOfNodes + " nodes, " + charBuffer.length() + " characters, " +
                                   numberOfAttributes + " attributes");
    }

    /**
     * Ask whether the document contains any nodes whose type annotation is anything other than
     * UNTYPED
     *
     * @return true if the document contains elements whose type is other than UNTYPED
     */
    @Override
    public boolean isTyped() {
        return typeArray != null;
    }

    /**
     * Get the number of nodes in the tree, excluding attributes and namespace nodes
     *
     * @return the number of nodes.
     */

    public int getNumberOfNodes() {
        return numberOfNodes;
    }

    /**
     * Get the number of attributes in the tree
     *
     * @return the number of attributes
     */

    public int getNumberOfAttributes() {
        return numberOfAttributes;
    }

    /**
     * Get the number of namespace declarations in the tree
     *
     * @return the number of namespace declarations
     */

    public int getNumberOfNamespaces() {
        return numberOfNamespaces;
    }

    /**
     * Get the array holding node kind information
     *
     * @return an array of bytes, byte N is the node kind of node number N
     */

    @Override
    public byte[] getNodeKindArray() {
        return nodeKind;
    }

    /**
     * Get the array holding node depth information
     *
     * @return an array of shorts, byte N is the node depth of node number N
     */

    public short[] getNodeDepthArray() {
        return depth;
    }

    /**
     * Get the array holding node name information
     *
     * @return an array of integers, integer N is the name code of node number N
     */

    @Override
    public int[] getNameCodeArray() {
        return nameCode;
    }

    /**
     * Get the array holding node type information
     *
     * @return an array of integers, integer N is the type code of node number N
     */

    /*@Nullable*/
    public SchemaType[] getTypeArray() {
        return typeArray;
    }

    /**
     * Get the array holding next-sibling pointers
     *
     * @return an array of integers, integer N is the next-sibling pointer for node number N
     */

    public int[] getNextPointerArray() {
        return next;
    }

    /**
     * Get the array holding alpha information
     *
     * @return an array of integers, whose meaning depends on the node kind. For elements it is a pointer
     * to the first attribute, for text, comment, and processing instruction nodes it is a pointer to the content
     */

    public int[] getAlphaArray() {
        return alpha;
    }

    /**
     * Get the array holding beta information
     *
     * @return an array of integers, whose meaning depends on the node kind. For elements it is a pointer
     * to the first namespace declaration
     */

    public int[] getBetaArray() {
        return beta;
    }

    /**
     * Get the character buffer used to hold all the text data of the document
     *
     * @return the character buffer
     */

    public AppendableCharSequence getCharacterBuffer() {
        //return new CharSlice(charBuffer, 0, charBufferLength);
        return charBuffer;
    }

    /**
     * Get the character buffer used to hold all the comment data of the document
     *
     * @return the character buffer used for comments
     */

    /*@Nullable*/
    public CharSequence getCommentBuffer() {
        return commentBuffer;
    }

    /**
     * Get the array used to hold the name codes of all attributes
     *
     * @return an integer array; the Nth integer holds the attribute name code of attribute N
     */

    public int[] getAttributeNameCodeArray() {
        return attCode;
    }

    /**
     * Get the array used to hold the type codes of all attributes
     *
     * @return an integer array; the Nth integer holds the attribute type code of attribute N
     */

    /*@Nullable*/
    public SimpleType[] getAttributeTypeArray() {
        return attType;
    }

    /**
     * Get the array used to hold the parent pointers of all attributes
     *
     * @return an integer array; the Nth integer holds the pointer to the parent element of attribute N
     */

    public int[] getAttributeParentArray() {
        return attParent;
    }

    /**
     * Get the array used to hold the name codes of all attributes
     *
     * @return an array of strings; the Nth string holds the string value of attribute N
     */

    public CharSequence[] getAttributeValueArray() {
        return attValue;
    }

    /**
     * Get the array used to hold the namespace declarations
     *
     * @return an array of namespace bindings
     */

    public NamespaceBinding[] getNamespaceBindings() {
        throw new UnsupportedOperationException();
    }  // TODO

    /**
     * Get the array used to hold the in-scope namespaces
     *
     * @return an array of namespace bindings
     */

    public NamespaceMap[] getNamespaceMaps() {
        return namespaceMaps;
    }

    /**
     * Get the array used to hold the parent pointers of all namespace declarations
     *
     * @return an integer array; the Nth integer holds the pointer to the parent element of namespace N
     */

    public int[] getNamespaceParentArray() {
        throw new UnsupportedOperationException();
    }   // TODO

    /**
     * Ask whether the tree contains any namespace declarations
     *
     * @return true if there is a namespace declaration (other than the XML namespace) anywhere
     * in the tree
     */

    public boolean isUsesNamespaces() {
        return usesNamespaces;
    }

    /**
     * Bulk copy an element node from another TinyTree. Type annotations will always be
     * stripped.
     * @param source        the source tree
     * @param nodeNr        the element node in the source tree to be deep-copied
     * @param currentDepth  the current depth in this tree
     * @param parentNodeNr  the node number in this tree of the parent which the copied node
     *                      will be attached to
     */

    public void bulkCopy(TinyTree source, int nodeNr, int currentDepth, int parentNodeNr) {
        //System.err.println(" **** doing bulk copy **** ");
        int end = source.next[nodeNr];
        while (end < nodeNr && end >= 0) {
            end = source.next[end];
        }
        if (end == -1) {
            end = source.numberOfNodes;
            if (end - 1 < source.nodeKind.length && source.nodeKind[end - 1] == Type.STOPPER) {
                end--;
            }
        }
        int length = end - nodeNr;
        assert length > 0;         // Bug 4089 bites here
        ensureNodeCapacity(Type.ELEMENT, length);
        System.arraycopy(source.nodeKind, nodeNr, nodeKind, numberOfNodes, length);
        int depthDiff = currentDepth - source.depth[nodeNr];

        NamespaceMap subtreeRoot = source.namespaceMaps[source.beta[nodeNr]];
        NamespaceMap inherited = namespaceMaps[beta[parentNodeNr]];
        boolean sameNamespaces = subtreeRoot == inherited || inherited.isEmpty();
            // TODO: or more generally, if inherited is a subset of subtreeRoot

        for (int i = 0; i < length; i++) {
            int from = nodeNr + i;
            int to = numberOfNodes + i;
            depth[to] = (short) (source.depth[from] + depthDiff);
            next[to] = source.next[from] + (to - from);
            switch (source.nodeKind[from]) {
                case Type.ELEMENT: {
                    nameCode[to] = (source.nameCode[from] & NamePool.FP_MASK) |
                            (prefixPool.obtainPrefixCode(source.getPrefix(from)) << 20);
                    int firstAtt = source.alpha[from];
                    if (firstAtt >= 0) {
                        int lastAtt = firstAtt;
                        while (lastAtt < source.numberOfAttributes && source.attParent[lastAtt] == from) {
                            lastAtt++;
                        }
                        int atts = lastAtt - firstAtt;
                        ensureAttributeCapacity(atts);
                        int aFrom = firstAtt;
                        int aTo = numberOfAttributes;
                        alpha[to] = aTo;
                        System.arraycopy(source.attValue, firstAtt, attValue, aTo, atts);
                        Arrays.fill(attParent, aTo, aTo + atts, to);
                        for (int a = 0; a < atts; a++, aFrom++, aTo++) {
                            int attNameCode = attCode[aTo] = source.attCode[aFrom];
                            if (NamePool.isPrefixed(attNameCode)) {
                                String prefix = source.prefixPool.getPrefix(attNameCode >> 20);
                                attCode[aTo] = (attNameCode & 0xfffff) | (prefixPool.obtainPrefixCode(prefix) << 20);
                            } else {
                                attCode[aTo] = attNameCode;
                            }
                            if (source.isIdAttribute(aFrom)) {
                                registerID(getNode(to), source.attValue[aFrom].toString());
                            }
                            if (source.isIdrefAttribute(aFrom)) {
                                if (idRefAttributes == null) {
                                    idRefAttributes = new IntHashSet();
                                }
                                idRefAttributes.add(aTo);
                            }
                        }
                        numberOfAttributes += atts;
                    } else {
                        alpha[to] = -1;
                    }
                    // Copy the namespaces. The namespaces present on nodes in the copied
                    // subtree need to be augmented with namespaces inherited from the destination
                    // tree
                    if (sameNamespaces) {
                        // The namespace map from the source tree can be copied unchanged
                        if (source.beta[from] == source.beta[nodeNr]) {
                            beta[to] = beta[parentNodeNr];
                        } else {
                            ensureNamespaceCapacity(1);
                            namespaceMaps[numberOfNamespaces] = source.namespaceMaps[source.beta[nodeNr]];
                            beta[to] = numberOfNamespaces++;
                        }
                    } else {
                        if (i > 0 && source.beta[from] == source.beta[nodeNr]) {
                            beta[to] = beta[parentNodeNr];
                        } else {
                            ensureNamespaceCapacity(1);
                            NamespaceMap in = source.namespaceMaps[source.beta[from]];
                            NamespaceMap out = inherited.putAll(in);
                            namespaceMaps[numberOfNamespaces] = out;
                            beta[to] = numberOfNamespaces++;
                        }
//                        ensureNamespaceCapacity(1);
//                        namespaceMaps[numberOfNamespaces] = source.namespaceMaps[source.beta[nodeNr]];
//                        beta[to] = numberOfNamespaces++;
                    }
                    break;
                }
                case Type.TEXTUAL_ELEMENT: {
                    int start = source.alpha[from];
                    int len = source.beta[from];
                    nameCode[to] = (source.nameCode[from] & NamePool.FP_MASK) |
                            (prefixPool.obtainPrefixCode(source.getPrefix(from)) << 20);
                    alpha[to] = charBuffer.length();
                    appendChars(source.charBuffer.subSequence(start, start + len));
                    beta[to] = len;
                    break;
                }
                case Type.TEXT: {
                    int start = source.alpha[from];
                    int len = source.beta[from];
                    nameCode[to] = -1;
                    alpha[to] = charBuffer.length();
                    appendChars(source.charBuffer.subSequence(start, start + len));
                    beta[to] = len;
                    break;
                }
                case Type.WHITESPACE_TEXT: {
                    nameCode[to] = -1;
                    alpha[to] = source.alpha[from];
                    beta[to] = source.beta[from];
                    break;
                }
                case Type.COMMENT: {
                    int start = source.alpha[from];
                    int len = source.beta[from];
                    nameCode[to] = -1;
                    CharSequence text = source.commentBuffer.subSequence(start, start+len);
                    if (commentBuffer == null) {
                        commentBuffer = new FastStringBuffer(FastStringBuffer.C256);
                    }
                    alpha[to] = commentBuffer.length();
                    commentBuffer.cat(text);
                    beta[to] = len;
                    break;
                }
                case Type.PROCESSING_INSTRUCTION:
                    int start = source.alpha[from];
                    int len = source.beta[from];
                    nameCode[to] = source.nameCode[from];
                    CharSequence text = source.commentBuffer.subSequence(start, start + len);
                    if (commentBuffer == null) {
                        commentBuffer = new FastStringBuffer(FastStringBuffer.C256);
                    }
                    alpha[to] = commentBuffer.length();
                    commentBuffer.cat(text);
                    beta[to] = len;
                    break;

                case Type.PARENT_POINTER:
                    nameCode[to] = -1;
                    alpha[to] = source.alpha[from] + (to - from);
                    beta[to] = -1;
                    break;
                default:
                    break;
            }
        }
        numberOfNodes += length;
    }

    /**
     * Get (and build if necessary) an index from local names to fingerprints
     * @return a Map whose keys are local names and whose values are sets of
     * fingerprints (usually singleton sets!) of names with that local name.
     * The index is for element names only.
     */

    public synchronized Map<String, IntSet> getLocalNameIndex() {
        if (localNameIndex == null) {
            localNameIndex = new HashMap<>();
            IntHashSet indexed = new IntHashSet();
            for (int i=0; i<numberOfNodes; i++) {
                if ((nodeKind[i] & 0xf) == Type.ELEMENT) {
                    int fp = nameCode[i] & NamePool.FP_MASK;
                    if (!indexed.contains(fp)) {
                        String local = getNamePool().getLocalName(fp);
                        indexed.add(fp);
                        IntSet existing = localNameIndex.get(local);
                        if (existing == null) {
                            localNameIndex.put(local, new IntSingletonSet(fp));
                        } else {
                            IntSet copy = existing.isMutable() ? existing : existing.mutableCopy();
                            copy.add(fp);
                            localNameIndex.put(local, copy);
                        }
                    }
                }
            }
        }
        return localNameIndex;
    }

}

