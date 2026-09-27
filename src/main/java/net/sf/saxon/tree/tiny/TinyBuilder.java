////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.*;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.CompressedWhitespace;
import net.sf.saxon.str.LargeTextBuffer;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpSimpleEnum;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;

import java.util.Arrays;
import java.util.Stack;


/**
 * The TinyBuilder class is responsible for taking a stream of SAX events and constructing
 * a Document tree, using the "TinyTree" implementation.
 */

public class TinyBuilder extends Builder {

    @CSharpSimpleEnum
    private enum Eligibility {INELIGIBLE, PRIMED, ELIGIBLE}

    private static final int PARENT_POINTER_INTERVAL = 10;
    // a lower value allocates more parent pointers which takes more space but reduces
    // the length of parent searches

    /*@Nullable*/ private TinyTree tree;
    private final Stack<NamespaceMap> namespaceStack = new Stack<>();

    private int currentDepth = 0;
    private int nodeNr = 0;             // this is the local sequence within this document
    private boolean ended = false;
    private boolean noNewNamespaces = true;
    private Statistics statistics;
    private final boolean markDefaultedAttributes;
    private Eligibility textualElementEligibilityState = Eligibility.INELIGIBLE;
    private final UnicodeBuilder commentBuilder = new UnicodeBuilder();

    /**
     * Create a TinyTree builder
     *
     * @param pipe information about the pipeline leading up to this Builder
     */

    public TinyBuilder(/*@NotNull*/ PipelineConfiguration pipe) {
        super(pipe);
        Configuration config = pipe.getConfiguration();
        statistics = config.getTreeStatistics().TEMPORARY_TREE_STATISTICS;
        markDefaultedAttributes = config.isExpandAttributeDefaults() && config.getBooleanProperty(Feature.MARK_DEFAULTED_ATTRIBUTES);
        //System.err.println("TinyBuilder " + this);
    }

    protected String getTreeKind() {
        return "tiny";
    }

    /**
     * Set the size parameters for the tree
     *
     * @param stats an object holding the expected number of non-attribute nodes, the expected
     *              number of attributes, the expected number of namespace declarations, and the expected total length of
     *              character data
     */

    public void setStatistics(Statistics stats) {
        statistics = stats;
    }

    /*@NotNull*/ private int[] prevAtDepth = new int[100];
    // this array is scaffolding used while constructing the tree, it is
    // not present in the final tree. For each level of the tree, it records the
    // node number of the most recent node at that level.

    /*@NotNull*/ private int[] siblingsAtDepth = new int[100];
    // more scaffolding. For each level of the tree, this array records the
    // number of siblings processed at that level. When this exceeds a threshold value,
    // a dummy node is inserted into the arrays to contain a parent pointer: this it to
    // prevent excessively long searches for a parent node, which is normally found by
    // scanning the siblings. The value is then reset to zero.

    private boolean isIDElement = false;

    /**
     * Get the tree being built by this builder
     *
     * @return the TinyTree
     */

    /*@Nullable*/
    public TinyTree getTree() {
        return tree;
    }

    /**
     * Get the current depth in the tree
     *
     * @return the current depth
     */

    public int getCurrentDepth() {
        return currentDepth;
    }

    /**
     * Open the event stream
     */

    @Override
    public void open() {
        //System.err.println("TinyBuilder " + this + " open; " + started);
        if (started) {
            // this happens when using an IdentityTransformer
            return;
        }
        if (tree == null) {
            tree = new TinyTree(config, statistics);
            currentDepth = 0;
            if (lineNumbering) {
                tree.setLineNumbering();
            }
            uniformBaseURI = true;
            tree.setUniformBaseUri(baseURI);
            tree.setDurability(getDurability());
        }
        if (useEventLocation) {
            Object copier = getPipelineConfiguration().getComponent(CopyInformee.class.getName());
            if (copier instanceof LocationCopier locCopier) {
                setSystemId(locCopier.getSystemId());
            }
        }
        super.open();
    }

    /**
     * Write a document node to the tree
     * @param properties properties of the document node
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        if ((started && !ended) || currentDepth > 0) {
            // this happens when using an IdentityTransformer, or when copying a document node to form
            // the content of an element
            return;
        }

        started = true;
        ended = false;

        TinyTree tt = tree;
        assert tt != null;
        currentRoot = new TinyDocumentImpl(tt);
        TinyDocumentImpl doc = (TinyDocumentImpl) currentRoot;
        doc.setSystemId(getSystemId());
        doc.setBaseURI(getBaseURI());

        currentDepth = 0;

        int nodeNr = tt.addDocumentNode((TinyDocumentImpl) currentRoot);
        prevAtDepth[0] = nodeNr;
        prevAtDepth[1] = -1;
        siblingsAtDepth[0] = 0;
        siblingsAtDepth[1] = 0;
        tt.next[nodeNr] = -1;

        currentDepth++;
    }

    /**
     * Callback interface for SAX: not for application use
     */

    @Override
    public void endDocument() throws XPathException {
//        System.err.println("TinyBuilder: " + this + " End document");

        tree.commentBuffer = commentBuilder.toUnicodeString();

        // Add a stopper node to ensure no-one walks off the end of the array; but
        // decrement numberOfNodes so the next node will overwrite it
        tree.addNode(Type.STOPPER, 0, 0, 0, -1);
        tree.numberOfNodes--;

        if (currentDepth > 1) {
            return;
        }
        // happens when copying a document node as the child of an element

        if (ended) {
            return;  // happens when using an IdentityTransformer
        }
        ended = true;

        prevAtDepth[currentDepth] = -1;
        currentDepth--;

    }

    @Override
    public void reset() {
        super.reset();
        tree = null;
        currentDepth = 0;
        nodeNr = 0;
        ended = false;
        statistics = config.getTreeStatistics().TEMPORARY_TREE_STATISTICS;
    }

    @Override
    public void close() throws XPathException {
        //System.err.println("TinyBuilder.close() " + this);
        TinyTree tt = tree;
        if (tt != null) {
            tree.commentBuffer = commentBuilder.toUnicodeString();
            tree.textBuffer.close();
            tt.addNode(Type.STOPPER, 0, 0, 0, -1);
            tt.condense(statistics);
        }
        super.close();
    }

    /**
     * Notify the start of an element
     *
     * @param elemName   the name of the element.
     * @param type       the type annotation of the element.
     * @param attributes the attributes of this element
     * @param namespaces the in-scope namespaces of this element: generally this is all the in-scope
     *                   namespaces, without relying on inheriting namespaces from parent elements
     * @param location   an object providing information about the module, line, and column where the node originated
     * @param properties bit-significant properties of the element node. If there are no relevant
     *                   properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties)
            throws XPathException {
        // if the number of siblings exceeds a certain threshold, add a parent pointer, in the form
        // of a pseudo-node

        //System.err.println(this + " startElement " + elemName + " " + currentDepth);
        assert tree != null;
        textualElementEligibilityState = Eligibility.INELIGIBLE;

        startElementSetupNamespaces(namespaces);

        startElementSetupSiblings();

        startElementAddNode(elemName, type, properties);

        startElementCalculateDepth();

        startElementLocalSystemId(location);

        if (lineNumbering) {
            tree.setLineNumber(nodeNr, location.getLineNumber(), location.getColumnNumber());
        }
        if ((properties & ReceiverOption.TOP_IN_ENTITY) != 0 && currentDepth >= 1) {
            tree.markTopWithinEntity(nodeNr);
        }
//        //#if CSHARP==false
//        if (location instanceof ReceivingContentHandler.LocalLocator &&
//                ((ReceivingContentHandler.LocalLocator)location).levelInEntity == 0 && currentDepth >= 1) {
//            tree.markTopWithinEntity(nodeNr);
//        }
//        //#endif
        if (attributes instanceof SingletonAttributeMap) {
            // Common enough to handle specially
            AttributeInfo att = (AttributeInfo)attributes;
            addAttributeToTree(att.getNodeName(), att.getType(),
                               getAttValue(att), att.getProperties());
        } else {
            for (AttributeInfo att : attributes) {
                addAttributeToTree(att.getNodeName(), att.getType(),
                                   getAttValue(att), att.getProperties());
            }
        }
        textualElementEligibilityState = (noNewNamespaces && !lineNumbering) ? Eligibility.PRIMED : Eligibility.INELIGIBLE;
        tree.addNamespaces(nodeNr, namespaceStack.peek());
        nodeNr++;
    }

    private void startElementSetupNamespaces(NamespaceMap namespaces) {
        noNewNamespaces = !namespaceStack.isEmpty() && namespaces == namespaceStack.peek();
        namespaceStack.push(namespaces);
    }

    private void startElementSetupSiblings() {
        if (siblingsAtDepth[currentDepth] > PARENT_POINTER_INTERVAL) {
            nodeNr = tree.addNode(Type.PARENT_POINTER, currentDepth, prevAtDepth[currentDepth - 1], 0, 0);
            int prev = prevAtDepth[currentDepth];
            if (prev > 0) {
                tree.next[prev] = nodeNr;
            }
            tree.next[nodeNr] = prevAtDepth[currentDepth - 1];
            prevAtDepth[currentDepth] = nodeNr;
            siblingsAtDepth[currentDepth] = 0;
        }
    }

    private void startElementAddNode(NodeName elemName, SchemaType type, int properties)
    {
        int nameCode;
        if (elemName instanceof NoNamespaceName) {
            nameCode = elemName.obtainFingerprint(namePool);
        } else {
            int fp = elemName.obtainFingerprint(namePool);
            int prefixCode = tree.prefixPool.obtainPrefixCode(elemName.getPrefix());
            nameCode = (prefixCode << 20) | fp;
        }
        nodeNr = tree.addNode(Type.ELEMENT, currentDepth, -1, -1, nameCode);

        isIDElement = ReceiverOption.contains(properties, ReceiverOption.IS_ID);
        if (type != Untyped.INSTANCE) {
            tree.setElementAnnotation(nodeNr, type);
            if (ReceiverOption.contains(properties, ReceiverOption.NILLED_ELEMENT)) {
                tree.setNilled(nodeNr);
            }
            if (!isIDElement && type.isIdType()) {
                isIDElement = true;
            }
        }
    }

    private void startElementCalculateDepth() {
        if (currentDepth == 0) {
            prevAtDepth[0] = nodeNr;
            prevAtDepth[1] = -1;
            currentRoot = tree.getNode(nodeNr);
        } else {
            int prev = prevAtDepth[currentDepth];
            if (prev > 0) {
                tree.next[prev] = nodeNr;
            }
            tree.next[nodeNr] = prevAtDepth[currentDepth - 1];   // *O* owner pointer in last sibling
            prevAtDepth[currentDepth] = nodeNr;
            siblingsAtDepth[currentDepth]++;
        }
        currentDepth++;

        if (currentDepth == prevAtDepth.length) {
            prevAtDepth = Arrays.copyOf(prevAtDepth, currentDepth * 2);
            siblingsAtDepth = Arrays.copyOf(siblingsAtDepth, currentDepth * 2);
        }
        prevAtDepth[currentDepth] = -1;
        siblingsAtDepth[currentDepth] = 0;
    }

    private void startElementLocalSystemId(Location location) {
        String localSystemId = location.getSystemId();
        if (isUseEventLocation() && localSystemId != null) {
            tree.setSystemId(nodeNr, localSystemId);
        } else if (currentDepth == 1) {
            tree.setSystemId(nodeNr, systemId);
        }
        if (uniformBaseURI && localSystemId != null && !localSystemId.equals(baseURI)) {
            uniformBaseURI = false;
            tree.setUniformBaseUri(null);
        }
    }


    protected String getAttValue(AttributeInfo att) {
        return att.getValue();
    }


    private void addAttributeToTree(NodeName attName, SimpleType type, String value, int properties) {
        // System.err.println("attribute " + nameCode + "=" + value);
        int fp = attName.obtainFingerprint(namePool);
        String prefix = attName.getPrefix();
        int nameCode = prefix.isEmpty() ? fp : (tree.prefixPool.obtainPrefixCode(prefix) << 20) | fp;
        assert tree != null;
        assert currentRoot != null;
        tree.addAttribute(currentRoot, nodeNr, nameCode, type, value, properties);
        if (markDefaultedAttributes && ReceiverOption.contains(properties, ReceiverOption.DEFAULTED_VALUE)) {
            tree.markDefaultedAttribute(tree.numberOfAttributes - 1);
        }
        if (fp == StandardNames.XML_BASE) {
            uniformBaseURI = false;
            tree.setUniformBaseUri(null);
        }
    }

    /**
     * Notify the end of an element node
     */

    @Override
    public void endElement() throws XPathException {
        assert tree != null;
        //System.err.println(this + " end element " + currentDepth);

        boolean eligibleAsTextualElement = textualElementEligibilityState == Eligibility.ELIGIBLE;
        textualElementEligibilityState = Eligibility.INELIGIBLE;
        prevAtDepth[currentDepth] = -1;
        siblingsAtDepth[currentDepth] = 0;
        currentDepth--;
        namespaceStack.pop();
        if (isIDElement) {
            // we're relying on the fact that an ID element has no element children!
            tree.indexIDElement(currentRoot, prevAtDepth[currentDepth]);
            isIDElement = false;
        } else if (noNewNamespaces &&
                eligibleAsTextualElement &&
                tree.nodeKind[nodeNr] == Type.TEXT &&
                tree.nodeKind[nodeNr-1] == Type.ELEMENT &&
                tree.alpha[nodeNr-1] == -1) {
            // Collapse a simple element with text content and no attributes or namespaces into a single node
            // of type TRIVIAL_ELEMENT
            //System.err.println("Created textual element");
            tree.nodeKind[nodeNr-1] = (byte)Type.TEXTUAL_ELEMENT;
            tree.alpha[nodeNr-1] = tree.alpha[nodeNr];
            tree.beta[nodeNr-1] = tree.beta[nodeNr];
            nodeNr--;
            tree.numberOfNodes--;
            if (currentDepth == 0) {
                currentRoot = tree.getNode(nodeNr);
            }
        }
    }

    /**
     * Get the last completed element node. This is used during checking of schema assertions,
     * which happens while the tree is still under construction. It is also used when copying
     * accumulator values to the new tree from a streamed input. This method is called immediately after
     * a call on endElement(), and it returns the element that has just ended.
     *
     * @return the last completed element node, that is, the element whose endElement event is the most recent
     *         endElement event to be reported, or null if there is no such element
     */

    public TinyNodeImpl getLastCompletedElement() {
        if (tree == null) {
            return null;
        }
        //noinspection RedundantCast
        return (TinyNodeImpl)tree.getNode(currentDepth >= 0 ? prevAtDepth[currentDepth] : 0);
        // Note: reading an incomplete tree needs care if it constructs a prior index, etc.
    }

    /**
     * Notify a text node
     */

    @Override
    public void characters(UnicodeString chars, Location locationId, int properties) throws XPathException {
        //System.err.println("Adding text node length " + chars.length());
        if (chars instanceof CompressedWhitespace &&
                ReceiverOption.contains(properties, ReceiverOption.WHOLE_TEXT_NODE)) {
            TinyTree tt = tree;
            assert tt != null;
            long lvalue = ((CompressedWhitespace) chars).getCompressedValue();
            nodeNr = tt.addNode(Type.WHITESPACE_TEXT, currentDepth, (int)(lvalue >> 32), (int)lvalue, -1);

            int prev = prevAtDepth[currentDepth];
            if (prev > 0) {
                tt.next[prev] = nodeNr;
            }
            tt.next[nodeNr] = prevAtDepth[currentDepth - 1];   // *O* owner pointer in last sibling
            prevAtDepth[currentDepth] = nodeNr;
            siblingsAtDepth[currentDepth]++;
            if (lineNumbering) {
                tt.setLineNumber(nodeNr, locationId.getLineNumber(), locationId.getColumnNumber());
            }
            return;
        }

        if (!chars.isEmpty()) {
            nodeNr = makeTextNode(chars.tidy());
            if (lineNumbering) {
                tree.setLineNumber(nodeNr, locationId.getLineNumber(), locationId.getColumnNumber());
            }
            textualElementEligibilityState = textualElementEligibilityState == Eligibility.PRIMED
                    ? Eligibility.ELIGIBLE
                    : Eligibility.INELIGIBLE;
        }
    }

    /**
     * Create a text node. Separate method so it can be overridden. If the current node
     * on the tree is already a text node, the new text will be appended to it.
     *
     * @param chars the contents of the text node
     * @return the node number of the created text node, or the text node to which
     *         this text has been appended.
     */

    protected int makeTextNode(UnicodeString chars) {
//        if (Configuration.isAssertionsEnabled()) {
//            chars.verifyCharacters();
//            //System.err.println("make text node length " + chars.length());
//        }
        TinyTree tt = tree;
        assert tt != null;

        LargeTextBuffer buffer = tt.textBuffer;
        int bufferStart = buffer.length();
        tt.appendChars(chars);
        int bufferEnd = buffer.length();
        int len = bufferEnd - bufferStart;
        int n = tt.numberOfNodes - 1;
        if (tt.nodeKind[n] == Type.TEXT && tt.depth[n] == currentDepth) {
            // merge this text node with the previous text node
            tt.beta[n] += len;
        } else {
            //System.err.println("Adding text node (" + bufferStart + " - " + bufferEnd + ") - " /*+ Err.wrap(chars)*/);
            nodeNr = tt.addNode(Type.TEXT, currentDepth, bufferStart, len, -1);
            //nodeNr = tt.addNode(Type.TEXT, currentDepth, tt.textChunksUsed, -1, -1);
            //tt.appendChars(chars);

            int prev = prevAtDepth[currentDepth];
            if (prev > 0) {
                tt.next[prev] = nodeNr;
            }
            tt.next[nodeNr] = prevAtDepth[currentDepth - 1];
            prevAtDepth[currentDepth] = nodeNr;
            siblingsAtDepth[currentDepth]++;
        }
        return nodeNr;
    }

    /**
     * Callback interface for SAX: not for application use
     */

    @Override
    public void processingInstruction(String piname, /*@NotNull*/ UnicodeString remainder, Location locationId, int properties) throws XPathException {
        TinyTree tt = tree;
        assert tt != null;

        textualElementEligibilityState = Eligibility.INELIGIBLE;
        int s = (int)commentBuilder.length();
        commentBuilder.accept(remainder);
        int nameCode = namePool.allocateFingerprint(NamespaceUri.NULL, piname);

        nodeNr = tt.addNode(Type.PROCESSING_INSTRUCTION, currentDepth, s, remainder.length32(),
                nameCode);

        int prev = prevAtDepth[currentDepth];
        if (prev > 0) {
            tt.next[prev] = nodeNr;
        }
        tt.next[nodeNr] = prevAtDepth[currentDepth - 1];   // *O* owner pointer in last sibling
        prevAtDepth[currentDepth] = nodeNr;
        siblingsAtDepth[currentDepth]++;

        String localLocation = locationId.getSystemId();
        if (isUseEventLocation() && localLocation != null) {
            tt.setSystemId(nodeNr, localLocation);
        } else if (currentDepth == 1) {
            tt.setSystemId(nodeNr, systemId);
        }
        if (localLocation != null && !localLocation.equals(baseURI)) {
            uniformBaseURI = false;
            tree.setUniformBaseUri(null);
        }
        if (lineNumbering) {
            tt.setLineNumber(nodeNr, locationId.getLineNumber(), locationId.getColumnNumber());
        }
    }

    /**
     * Callback interface for SAX: not for application use
     */

    @Override
    public void comment(/*@NotNull*/ UnicodeString chars, Location locationId, int properties) throws XPathException {
        TinyTree tt = tree;
        assert tt != null;

        textualElementEligibilityState = Eligibility.INELIGIBLE;
        int s = (int)commentBuilder.length();
        commentBuilder.accept(chars);
        nodeNr = tt.addNode(Type.COMMENT, currentDepth, s, chars.tidy().length32(), -1);

        int prev = prevAtDepth[currentDepth];
        if (prev > 0) {
            tt.next[prev] = nodeNr;
        }
        tt.next[nodeNr] = prevAtDepth[currentDepth - 1];   // *O* owner pointer in last sibling
        prevAtDepth[currentDepth] = nodeNr;
        siblingsAtDepth[currentDepth]++;

        if (lineNumbering) {
            tt.setLineNumber(nodeNr, locationId.getLineNumber(), locationId.getColumnNumber());
        }

    }

    /**
     * Set an unparsed entity in the document
     */

    @Override
    public void setUnparsedEntity(String name, String uri, String publicId) {
        if (tree.getUnparsedEntity(name) == null) {
            // bug 2187
            tree.setUnparsedEntity(name, uri, publicId);
        }
    }

    /*@NotNull*/
    @Override
    public BuilderMonitor getBuilderMonitor() {
        return new TinyBuilderMonitor(this);
    }


}

