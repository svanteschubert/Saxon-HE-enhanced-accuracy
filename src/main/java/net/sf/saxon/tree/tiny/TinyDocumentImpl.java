////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.CopyOptions;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AnyType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.UntypedAtomicValue;
import net.sf.saxon.z.IntHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * A node in the XML parse tree representing the Document itself (or equivalently, the root
 * node of the Document).
 */

public final class TinyDocumentImpl extends TinyParentNodeImpl {


    private IntHashMap<List<NodeInfo>> elementList;
    private String baseURI;


    public TinyDocumentImpl(/*@NotNull*/ TinyTree tree) {
        this.tree = tree;
        nodeNr = 0;
    }

    /**
     * Get the tree containing this node
     */

    @Override
    public TinyTree getTree() {
        return tree;
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
     * Get the configuration previously set using setConfiguration
     */

    @Override
    public Configuration getConfiguration() {
        return tree.getConfiguration();
    }

    /**
     * Set the system id of this node
     */

    @Override
    public void setSystemId(String uri) {
        tree.setSystemId(nodeNr, uri);
    }

    /**
     * Get the system id of this root node
     */

    @Override
    public String getSystemId() {
        return tree.getSystemId(nodeNr);
    }

    /**
     * Set the base URI of this document node
     *
     * @param uri the base URI
     */

    public void setBaseURI(String uri) {
        baseURI = uri;
    }

    /**
     * Get the base URI of this root node.
     */

    @Override
    public String getBaseURI() {
        if (baseURI != null) {
            return baseURI;
        }
        return getSystemId();
    }

    /**
     * Get the line number of this root node.
     *
     * @return 0 always
     */

    @Override
    public int getLineNumber() {
        return 0;
    }

    /**
     * Ask whether the document contains any nodes whose type annotation is anything other than
     * UNTYPED
     *
     * @return true if the document contains elements whose type is other than UNTYPED
     */
    public boolean isTyped() {
        return tree.getTypeArray() != null;
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
     * Find the parent node of this node.
     *
     * @return The Node object describing the containing element or root node.
     */

    /*@Nullable*/
    @Override
    public TinyNodeImpl getParent() {
        return null;
    }

    /**
     * Get the root node
     *
     * @return the NodeInfo that is the root of the tree - not necessarily a document node
     */

    /*@NotNull*/
    @Override
    public NodeInfo getRoot() {
        return this;
    }

    /**
     * Get a character string that uniquely identifies this node
     *
     * @param buffer to contain an identifier based on the document number
     */

    @Override
    public void generateId(/*@NotNull*/ FastStringBuffer buffer) {
        buffer.cat('d');
        buffer.append(Long.toString(getTreeInfo().getDocumentNumber()));
    }

    /**
     * Get the typed value.
     *
     * @return the typed value. This will either be a single AtomicValue or a Value whose items are
     *         atomic values.
     */

    /*@NotNull*/
    @Override
    public AtomicSequence atomize() throws XPathException {
        return new UntypedAtomicValue(getStringValueCS());
    }

    /**
     * Get a list of all elements with a given name. This is implemented
     * as a memo function: the first time it is called for a particular
     * element type, it remembers the result for next time.
     *
     * @param fingerprint the fingerprint identifying the required element name
     * @return an iterator over all elements with this name
     */

    /*@NotNull*/ AxisIterator getAllElements(int fingerprint) {
        if (elementList == null) {
            elementList = new IntHashMap<List<NodeInfo>>(20);
        }
        List<NodeInfo> list = elementList.get(fingerprint);
        if (list == null) {
            list = makeElementList(fingerprint);
            elementList.put(fingerprint, list);
        }
        return new net.sf.saxon.tree.iter.ListIterator.OfNodes(list);
    }

    /**
     * Make a list containing all the elements with a given element name, in document order
     *
     * @param fingerprint the fingerprint of the element name
     * @return list a List containing the TinyElementImpl objects
     */

    List<NodeInfo> makeElementList(int fingerprint) {
        int size = tree.getNumberOfNodes() / 20;
        if (size > 100) {
            size = 100;
        }
        if (size < 20) {
            size = 20;
        }
        ArrayList<NodeInfo> list = new ArrayList<>(size);
        int i = nodeNr + 1;
        try {
            while (tree.depth[i] != 0) {
                byte kind = tree.nodeKind[i];
                if ((kind & 0x0f) == Type.ELEMENT &&
                        (tree.nameCode[i] & 0xfffff) == fingerprint) {
                    list.add(tree.getNode(i));
                }
                i++;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // this shouldn't happen. If it does happen, it means the tree wasn't properly closed
            // during construction (there is no stopper node at the end). In this case, we'll recover
            return list;
        }
        list.trimToSize();
        return list;
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
        AxisIterator children = iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        NodeInfo node = children.next();
        if (node == null || node.getSchemaType() == Untyped.getInstance()) {
            return Untyped.getInstance();
        } else {
            return AnyType.getInstance();
        }
    }

    /**
     * Copy this node to a given outputter
     */

    @Override
    public void copy(/*@NotNull*/ Receiver out, int copyOptions, Location locationId) throws XPathException {

        out.startDocument(CopyOptions.getStartDocumentProperties(copyOptions));

        // copy any unparsed entities

        if (tree.entityTable != null) {
            for (Map.Entry<String, String[]> entry : tree.entityTable.entrySet()) {
                String name = entry.getKey();
                String[] details = entry.getValue();
                String systemId = details[0];
                String publicId = details[1];
                out.setUnparsedEntity(name, systemId, publicId);
            }
        }

        // output the children

        for (NodeInfo child : children()) {
            child.copy(out, copyOptions, locationId);
        }

        out.endDocument();
    }

    public void showSize() {
        tree.showSize();
    }

    /**
     * The hashCode() method obeys the contract for hashCode(): that is, if two objects are equal
     * (represent the same node) then they must have the same hashCode()
     *
     * @since 8.7 Previously, the effect of the equals() and hashCode() methods was not defined. Callers
     *        should therefore be aware that third party implementations of the NodeInfo interface may
     *        not implement the correct semantics.
     */

    public int hashCode() {
        // Chosen to give a hashcode that is likely (a) to be distinct from other documents, and (b) to
        // be distinct from other nodes in the same document
        return (int) tree.getDocumentNumber();
    }


}

