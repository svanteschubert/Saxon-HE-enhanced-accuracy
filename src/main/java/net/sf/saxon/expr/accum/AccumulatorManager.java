////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.accum;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.tree.wrapper.VirtualCopy;
import net.sf.saxon.tree.wrapper.VirtualTreeInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Manager for accumulator functions (XSLT 3.0).
 *
 * <p>In principle there is a dataset containing accumulator values for every accumulator/document pair.
 * In addition, if the same stylesheet is run concurrently in multiple transformations, and a document
 * is shared between these transformations, the data values for the accumulator can differ between the
 * two transformations, because the accumulator rules can depend on values of stylesheet parameters
 * or on context variables such as current-dateTime.</p>
 *
 * <p>It is important that the accumulator data for a tree does not cause the tree to be locked in
 * memory for the duration of a transformation. We therefore keep a weak reference to the tree. For
 * each tree, until it is garbage collected, there is a map from accumulator names to accumulator data.</p>
 */
public class AccumulatorManager {


    private transient WeakHashMap<TreeInfo, Map<Accumulator, IAccumulatorData>> accumulatorDataIndex =
        new WeakHashMap<TreeInfo, Map<Accumulator, IAccumulatorData>>();

    private transient WeakHashMap<TreeInfo, Set<? extends Accumulator>> applicableAccumulators =
            new WeakHashMap<TreeInfo, Set<? extends Accumulator>>();

    public AccumulatorManager() {
    }

    /**
     * By default, all accumulators are applicable to any given tree. If this method is called,
     * a specific set of accumulators are registered as applicable. This set may be empty.
     * @param tree the document tree in question
     * @param accumulators the set of accumulators that are appicable
     */

    public void setApplicableAccumulators(TreeInfo tree, Set<? extends Accumulator> accumulators) {
        applicableAccumulators.put(tree, accumulators);
    }

    /**
     * Ask whether a particular accumulator is applicable to a particular tree
     * @param tree the tree in question
     * @param accumulator the accumulator in question
     * @return true if the accumulator is applicable to this tree, otherwise false
     */

    public boolean isApplicable(TreeInfo tree, Accumulator accumulator) {
        Set<? extends Accumulator> accSet = applicableAccumulators.get(tree);
        return accSet == null || accSet.contains(accumulator);
    }

    private static AccumulatorData MARKER = new AccumulatorData(null);

    /**
     * Get the data relating to a particular accumulator for a particular unstreamed document tree
     *
     * @param doc     the source document tree in question
     * @param acc     the required accumulator
     * @param context the XPath dynamic evaluation context
     * @return a data structure holding the evaluated values of the accumulator for this source document
     * @throws XPathException if any dynamic error occurs
     */

    public synchronized IAccumulatorData getAccumulatorData(TreeInfo doc, Accumulator acc, XPathContext context)
            throws XPathException {
        Map<Accumulator, IAccumulatorData> map = accumulatorDataIndex.get(doc);
        if (map != null) {
            IAccumulatorData data = map.get(acc);
            if (data != null) {
                if (data == MARKER) {
                    throw new XPathException("Accumulator " + acc.getAccumulatorName().getDisplayName() +
                        " requires access to its own value", "XTDE3400");
                }
                return data;
            }
        } else {
            map = new HashMap<Accumulator, IAccumulatorData>();
            map.put(acc, MARKER);
            accumulatorDataIndex.put(doc, map);
        }
        if (doc instanceof VirtualTreeInfo && ((VirtualTreeInfo)doc).isCopyAccumulators()) {
            NodeInfo original = ((VirtualCopy) doc.getRootNode()).getOriginalNode();
            IAccumulatorData originalData = getAccumulatorData(original.getTreeInfo(), acc, context);
            VirtualAccumulatorData vad = new VirtualAccumulatorData(originalData);
            map.put(acc, vad);
            return vad;
        } else if (doc instanceof TinyTree && ((TinyTree)doc).getCopiedFrom() != null) {
            IAccumulatorData original = getAccumulatorData(((TinyTree) doc).getCopiedFrom().getTreeInfo(), acc, context);
            return new PathMappedAccumulatorData(original, ((TinyTree) doc).getCopiedFrom());
        } else {
            AccumulatorData d = new AccumulatorData(acc);
            XPathContextMajor c2 = context.newCleanContext();
            c2.setCurrentComponent(acc.getDeclaringComponent());
            try {
                d.buildIndex(doc.getRootNode(), c2);
                map.put(acc, d);
                return d;
            } catch (XPathException err) {
                IAccumulatorData failed = new FailedAccumulatorData(acc, err);
                map.put(acc, failed);
                return failed;
            }
        }
    }

    /**
     * Add the accumulator data for a particular accumulator, if it does not already exist
     * @param doc the document/tree whose accumulator data is being added
     * @param acc the accumulator in question
     * @param accData the data holding the values of the accumulator for the nodes in this tree
     */

    public synchronized void addAccumulatorData(TreeInfo doc, Accumulator acc, IAccumulatorData accData) {
        Map<Accumulator, IAccumulatorData> map = accumulatorDataIndex.get(doc);
        if (map != null) {
            IAccumulatorData data = map.get(acc);
            if (data != null) {
                return;
            }
        } else {
            map = new HashMap<Accumulator, IAccumulatorData>();
            accumulatorDataIndex.put(doc, map);
        }
        map.put(acc, accData);
    }


}
