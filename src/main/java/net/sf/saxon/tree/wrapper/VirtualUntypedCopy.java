////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.CopyOptions;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.UntypedAtomicValue;

/**
 * This class represents a virtual copy of a node with type annotations stripped
 */
public class VirtualUntypedCopy extends VirtualCopy {

    /**
     * Public factory method: create a new untyped virtual tree as a copy of a node
     *
     * @param original the node (in the original tree) to be copied
     * @param root     the node in the original tree corresponding to the root node of the virtual copy
     * @return the virtual copy.
     */

    public static VirtualCopy makeVirtualUntypedTree(NodeInfo original, NodeInfo root) {
        VirtualCopy vc;
        // Don't allow copies of copies of copies: define the new copy in terms of the original
        while (original instanceof VirtualUntypedCopy && original.getParent() == null) {
            original = ((VirtualUntypedCopy) original).original;
            root = ((VirtualUntypedCopy) root).original;
        }

        vc = new VirtualUntypedCopy(original, root);

        Configuration config = original.getConfiguration();
        VirtualTreeInfo doc = new VirtualTreeInfo(config, vc);
        vc.tree = doc;

        return vc;
    }

    /**
     * Protected constructor: create a virtual copy of a node
     *
     * @param base the node to be copied
     */

    protected VirtualUntypedCopy(NodeInfo base, NodeInfo root) {
        super(base, root);
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
        switch (getNodeKind()) {
            case Type.ELEMENT:
                return Untyped.getInstance();
            case Type.ATTRIBUTE:
                return BuiltInAtomicType.UNTYPED_ATOMIC;
            default:
                return super.getSchemaType();
        }
    }

    /**
     * Get the typed value.
     *
     * @return the typed value.
     * @since 8.5
     */

    @Override
    public AtomicSequence atomize() throws XPathException {
        switch (getNodeKind()) {
            case Type.ELEMENT:
            case Type.ATTRIBUTE:
                return new UntypedAtomicValue(getStringValueCS());
            default:
                return super.atomize();
        }
    }


    @Override
    public void copy(Receiver out, int copyOptions, Location locationId) throws XPathException {
        super.copy(out, copyOptions & ~CopyOptions.TYPE_ANNOTATIONS, locationId);
    }

    /**
     * Method to create the virtual copy of a node encountered when navigating. This method
     * is separated out so that it can be overridden in a subclass.
     */

    @Override
    protected VirtualCopy wrap(NodeInfo node) {
        VirtualUntypedCopy vc = new VirtualUntypedCopy(node, root);
        vc.tree = tree;
        return vc;
    }

    /**
     * Determine whether the node has the is-nilled property
     *
     * @return true if the node has the is-nilled property
     */

    @Override
    public boolean isNilled() {
        return false;
    }
}

