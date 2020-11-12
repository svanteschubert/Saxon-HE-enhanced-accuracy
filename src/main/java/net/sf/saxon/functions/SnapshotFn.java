////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.event.BuilderMonitor;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.ItemMappingFunction;
import net.sf.saxon.expr.ItemMappingIterator;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.wrapper.SnapshotNode;
import net.sf.saxon.tree.wrapper.VirtualCopy;
import net.sf.saxon.type.AnyType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * XSLT 3.0 function snapshot(). This is like a deep-copy except that it also includes a shallow copy of all ancestors.
 */
public class SnapshotFn extends SystemFunction {

    @Override
    public int getCardinality(Expression[] arguments) {
        return arguments[0].getCardinality();
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        Sequence in = arguments.length == 0 ? context.getContextItem() : arguments[0];
        SequenceIterator iter = snapshotSequence(in.iterate(), context);
        return new LazySequence(iter);
    }

    public static SequenceIterator snapshotSequence(SequenceIterator nodes, final XPathContext context) {
        return new ItemMappingIterator(nodes, getMappingFunction());
    }

    /**
     * Get a mapping function that can be used to take a snapshot of every item in a sequence
     * @return a suitable mapping function
     */

    public static ItemMappingFunction getMappingFunction() {
        return SnapshotFn::snapshotSingle;
    }

    /**
     * Take a snapshot of a single item
     * @param origin the item in question
     * @return the snapshot
     */

    public static Item snapshotSingle(Item origin) {
        if (origin instanceof NodeInfo) {
            if (((NodeInfo)origin).getParent() == null) {
                VirtualCopy vc = VirtualCopy.makeVirtualCopy((NodeInfo) origin);
                vc.getTreeInfo().setCopyAccumulators(true);
                return vc;
            } else {
                return SnapshotNode.makeSnapshot((NodeInfo) origin);
            }
        } else {
            return origin;
        }

    }

    public static List<NodeInfo> makeAncestorList(NodeInfo origin) {
        List<NodeInfo> ancestors = new ArrayList<>(20);
        origin.iterateAxis(AxisInfo.ANCESTOR).forEachNode(ancestors::add);
        return ancestors;
    }

    public static BuilderMonitor openAncestors(NodeInfo origin, List<NodeInfo> ancestors, XPathContext context) throws XPathException {
        NodeInfo root = origin.getRoot();
        TinyBuilder builder = new TinyBuilder(context.getController().makePipelineConfiguration());
        builder.setStatistics(context.getConfiguration().getTreeStatistics().TEMPORARY_TREE_STATISTICS);
        builder.setSystemId(root.getSystemId());
        builder.setTiming(false);

        BuilderMonitor bm = builder.getBuilderMonitor();
        bm.open();

        TreeInfo source = root.getTreeInfo();
        Iterator<String> unparsedEntities = source.getUnparsedEntityNames();
        while (unparsedEntities.hasNext()) {
            String name = unparsedEntities.next();
            String[] properties = source.getUnparsedEntity(name);
            builder.setUnparsedEntity(name, properties[0], properties[1]);
        }

        SchemaType ancestorType = context.getController().getExecutable().isSchemaAware() ?
                AnyType.getInstance() : Untyped.getInstance();
        for (int i = ancestors.size() - 1; i >= 0; i--) {
            NodeInfo anc = ancestors.get(i);
            int kind = anc.getNodeKind();
            switch (kind) {

                case Type.ELEMENT: {
                    bm.startElement(NameOfNode.makeName(anc), ancestorType,
                                    anc.attributes(), anc.getAllNamespaces(),
                                    Loc.NONE, ReceiverOption.NONE);
                    break;
                }

                case Type.DOCUMENT: {
                    bm.startDocument(ReceiverOption.NONE);
                    break;
                }
                default: {
                    throw new IllegalStateException("Unknown ancestor node kind " + anc.getNodeKind());
                }
            }
        }
        return bm;
    }

    public static void closeAncestors(List<NodeInfo> ancestors, Receiver bm) throws XPathException {
        for (NodeInfo anc : ancestors) {
            switch (anc.getNodeKind()) {
                case Type.ELEMENT: {
                    bm.endElement();
                    break;
                }
                case Type.DOCUMENT: {
                    bm.endDocument();
                    break;
                }
                default: {
                    throw new IllegalStateException("Unknown ancestor node kind " + anc.getNodeKind());
                }
            }
        }
        bm.close();
    }

    @Override
    public String getStreamerName() {
        return "SnapshotFn";
    }
}

