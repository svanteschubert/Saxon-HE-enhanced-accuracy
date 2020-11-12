////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;

/**
 * The <tt>SequenceWriter</tt> is used when writing a sequence of items, for
 * example, when {@code xsl:variable} is used with content and an "as" attribute. The {@code SequenceWriter}
 * builds the sequence; the concrete subclass is responsible for deciding what to do with the
 * resulting items.
 * <p>
 * Items may be supplied in either "composed" form (by calling the {@link #append(Item)} method,
 * or in "decomposed" form (by a sequence of calls representing XML push events: {@link Outputter#startDocument(int)},
 * {@link Outputter#startElement(NodeName, SchemaType, Location, int)}, and so on. When items are supplied
 * in decomposed form, a tree will be built, and the resulting document or element node is then
 * written to the sequence in the same way as if it were supplied directly as a {@link NodeInfo} item.
 */

public abstract class SequenceWriter extends SequenceReceiver {
    private TreeModel treeModel = null;
    private Builder builder = null;
    private int level = 0;

    public SequenceWriter(/*@NotNull*/ PipelineConfiguration pipe) {
        super(pipe);
        //System.err.println("SequenceWriter init");
    }

    /**
     * Abstract method to be supplied by subclasses: output one item in the sequence.
     *
     * @param item the item to be written to the sequence
     * @throws XPathException
     *          if any failure occurs while writing the item
     */

    public abstract void write(Item item) throws XPathException;

    @Override
    public void startDocument(int properties) throws XPathException {
        if (builder == null) {
            createTree(ReceiverOption.contains(properties, ReceiverOption.MUTABLE_TREE));
        }
        if (level++ == 0) {
            builder.startDocument(properties);
        }
    }

    @Override
    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
        if (builder != null) {
            builder.setUnparsedEntity(name, systemID, publicID);
        }
    }

    /**
     * Create a (skeletal) tree to hold a document or element node.
     *
     * @param mutable set to true if the tree is required to support in-situ updates (other that the initial
     *                sequential writing of nodes to construct the tree)
     * @throws XPathException
     *          if any error occurs while creating the tree
     */
    private void createTree(boolean mutable) throws XPathException {
        PipelineConfiguration pipe = getPipelineConfiguration();

        if (treeModel != null) {
            builder = treeModel.makeBuilder(pipe);
        } else if (pipe.getController() != null) {
            if (mutable) {
                TreeModel model = pipe.getController().getModel();
                if (model.isMutable()) {
                    builder = pipe.getController().makeBuilder();
                } else {
                    builder = new LinkedTreeBuilder(pipe);
                }
            } else {
                builder = pipe.getController().makeBuilder();
            }
        } else {
            TreeModel model = getConfiguration().getParseOptions().getModel();
            builder = model.makeBuilder(pipe);
        }

        builder.setPipelineConfiguration(pipe);
        builder.setSystemId(systemId);
        builder.setBaseURI(systemId);
        builder.setTiming(false);
        builder.setUseEventLocation(false);
        builder.open();
    }

    /**
     * Get the tree model that will be used for creating trees when events are written to the sequence
     * @return the tree model, if one has been set using setTreeModel(); otherwise null
     */

    public TreeModel getTreeModel() {
        return treeModel;
    }

    /**
     * Set the tree model that will be used for creating trees when events are written to the sequence
     * @param treeModel the tree model to be used. If none has been set, the default tree model for the configuration
     * is used, unless a mutable tree is required and the default tree model is not mutable, in which case a linked
     * tree is used.
     */

    public void setTreeModel(TreeModel treeModel) {
        this.treeModel = treeModel;
    }

    @Override
    public void endDocument() throws XPathException {
        if (--level == 0) {
            builder.endDocument();
            NodeInfo doc = builder.getCurrentRoot();
            // add the constructed document to the result sequence
            append(doc, Loc.NONE, ReceiverOption.ALL_NAMESPACES);
            builder = null;
            systemId = null;
        }
        previousAtomic = false;
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {

        if (builder == null) {
            createTree(ReceiverOption.contains(properties, ReceiverOption.MUTABLE_TREE));
        }
        //System.err.println("SEQUENCE_WRITER startElement " + this);
        builder.startElement(elemName, type, attributes, namespaces, location, properties);
        level++;
        previousAtomic = false;
    }

    @Override
    public void endElement() throws XPathException {
        //System.err.println("SEQUENCE_WRITER endElement " + this);
        builder.endElement();
        if (--level == 0) {
            builder.close();
            NodeInfo element = builder.getCurrentRoot();
            append(element, Loc.NONE, ReceiverOption.ALL_NAMESPACES);
            builder = null;
            systemId = null;
        }
        previousAtomic = false;
    }


    @Override
    public void characters(CharSequence s, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeKind(Type.TEXT);
            o.setStringValue(s.toString());
            write(o);
        } else {
            if (s.length() > 0) {
                builder.characters(s, locationId, properties);
            }
        }
        previousAtomic = false;
    }

    @Override
    public void comment(CharSequence comment, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeKind(Type.COMMENT);
            o.setStringValue(comment);
            write(o);
        } else {
            builder.comment(comment, locationId, properties);
        }
        previousAtomic = false;
    }

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            Orphan o = new Orphan(getConfiguration());
            o.setNodeName(new NoNamespaceName(target));
            o.setNodeKind(Type.PROCESSING_INSTRUCTION);
            o.setStringValue(data);
            write(o);
        } else {
            builder.processingInstruction(target, data, locationId, properties);
        }
        previousAtomic = false;
    }

    @Override
    public void close() throws XPathException {
        previousAtomic = false;
        if (builder != null) {
            builder.close();
        }
    }

    @Override
    public void append(/*@Nullable*/ Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (item != null) {
            if (level == 0) {
                write(item);
                previousAtomic = false;
            } else {
                decompose(item, locationId, copyNamespaces);
            }
        }
    }

    @Override
    public boolean usesTypeAnnotations() {
        return builder == null || builder.usesTypeAnnotations();
    }

}

