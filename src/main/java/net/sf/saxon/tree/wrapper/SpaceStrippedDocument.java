////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.type.ComplexType;
import net.sf.saxon.type.SchemaType;

import java.util.Iterator;

/**
 * A SpaceStrippedDocument represents a view of a real Document in which selected
 * whitespace text nodes are treated as having been stripped.
 */

public class SpaceStrippedDocument extends GenericTreeInfo {

    private SpaceStrippingRule strippingRule;
    private boolean preservesSpace;
    private boolean containsAssertions;
    private TreeInfo underlyingTree;

    /**
     * Create a space-stripped view of a document
     *
     * @param doc           the underlying document
     * @param strippingRule an object that contains the rules defining which whitespace
     *                      text nodes are to be absent from the view
     */

    public SpaceStrippedDocument(TreeInfo doc, SpaceStrippingRule strippingRule) {
        super(doc.getConfiguration());
        setRootNode(wrap(doc.getRootNode()));
        this.strippingRule = strippingRule;
        this.underlyingTree = doc;
        preservesSpace = findPreserveSpace(doc);
        containsAssertions = findAssertions(doc);
    }

    /**
     * Create a wrapped node within this document
     */

    public SpaceStrippedNode wrap(NodeInfo node) {
        return SpaceStrippedNode.makeWrapper(node, this, null);
    }

    /**
     * Ask whether the document contains any nodes whose type annotation is anything other than
     * UNTYPED
     *
     * @return true if the document contains elements whose type is other than UNTYPED
     */
    @Override
    public boolean isTyped() {
        return underlyingTree.isTyped();
    }

    /**
     * Get the document's strippingRule
     */

    public SpaceStrippingRule getStrippingRule() {
        return strippingRule;
    }

    /**
     * Get the element with a given ID, if any
     *
     * @param id        the required ID value
     * @param getParent true if we want the parent of an element with the ID value
     * @return the element with the given ID value, or null if there is none.
     */

    /*@Nullable*/
    @Override
    public NodeInfo selectID(String id, boolean getParent) {
        NodeInfo n = underlyingTree.selectID(id, false);
        if (n == null) {
            return null;
        } else {
            return wrap(n);
        }
    }

    /**
     * Get the list of unparsed entities defined in this document
     *
     * @return an Iterator, whose items are of type String, containing the names of all
     *         unparsed entities defined in this document. If there are no unparsed entities or if the
     *         information is not available then an empty iterator is returned
     */

    @Override
    public Iterator<String> getUnparsedEntityNames() {
        return underlyingTree.getUnparsedEntityNames();
    }

    /**
     * Get the unparsed entity with a given name
     *
     * @param name the name of the entity
     */

    @Override
    public String[] getUnparsedEntity(String name) {
        return underlyingTree.getUnparsedEntity(name);
    }

    /**
     * Determine whether the wrapped document contains any xml:space="preserve" attributes. If it
     * does, we will look for them when stripping individual nodes. It's more efficient to scan
     * the document in advance checking for xml:space attributes than to look for them every time
     * we hit a whitespace text node.
     *
     * @param doc the wrapper of the document node
     * @return true if any element in the document has an xml:space attribute with the value "preserve"
     */

    private static boolean findPreserveSpace(/*@NotNull*/ TreeInfo doc) {
        if (doc instanceof TinyTree) {
            // Optimisation - see bug 2929. Makes a vast difference especially if there are few attributes in the tree
            return ((TinyTree) doc).hasXmlSpacePreserveAttribute();
        } else {
            AxisIterator iter = doc.getRootNode().iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
            NodeInfo node;
            while ((node = iter.next()) != null) {
                String val = node.getAttributeValue(NamespaceConstant.XML, "space");
                if ("preserve".equals(val)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Determine whether the wrapped document contains any nodes annotated with complex types that
     * define assertions.
     *
     * @param doc the wrapper of the document node
     * @return true if any element in the document has an xml:space attribute with the value "preserve"
     */

    private static boolean findAssertions(/*@NotNull*/ TreeInfo doc) {
        if (doc.isTyped()) {
            AxisIterator iter = doc.getRootNode().iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
            while (true) {
                NodeInfo node = iter.next();
                if (node == null) {
                    return false;
                }
                SchemaType type = node.getSchemaType();
                if (type.isComplexType() && ((ComplexType) type).hasAssertions()) {
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    /**
     * Ask whether the stripped document contains any xml:space="preserve" attributes.
     *
     * @return true if any element in the document has an xml:space attribute with the value "preserve"
     */

    public boolean containsPreserveSpace() {
        return preservesSpace;
    }

    /**
     * Ask whether the stripped document contain any nodes annotated with types that carry assertions
     *
     * @return true if any element in the document has a type that has an assertion
     */

    public boolean containsAssertions() {
        return containsAssertions;
    }

}

