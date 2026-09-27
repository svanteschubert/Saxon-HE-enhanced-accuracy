////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.event.*;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.expr.instruct.TailCall;
import net.sf.saxon.functions.ConstantFunction;
import net.sf.saxon.ma.arrays.ArrayFunctionSet;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.ma.map.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * The built-in shallow-copy-all rule set proposed for XSLT 4.0, which is the same as
 * shallow-copy, except for maps and arrays.
 */
public class ShallowCopyAllRuleSet extends ShallowCopyRuleSet {

    private static final ShallowCopyAllRuleSet INSTANCE_WITH_COPY_NS = new ShallowCopyAllRuleSet(true);
    private static final ShallowCopyAllRuleSet INSTANCE_WITH_NO_COPY_NS = new ShallowCopyAllRuleSet(false);

    /**
     * Get the singleton instance of this class
     *
     * @return the singleton instance
     */

    public static ShallowCopyRuleSet getInstance(boolean copyNamespaces) {
        return copyNamespaces ? INSTANCE_WITH_COPY_NS : INSTANCE_WITH_NO_COPY_NS;
    }

    private ShallowCopyAllRuleSet(boolean copyNamespaces) {
        super(copyNamespaces);
    }

    /**
     * Perform the built-in template action for a given node.
     * @param item the item to be processed by this built-in rule
     * @param parameters   the parameters supplied to apply-templates
     * @param tunnelParams the tunnel parameters to be passed through
     * @param out  the destination for output
     * @param context      the dynamic evaluation context
     * @param locationId   location of the instruction (apply-templates, apply-imports etc) that caused
     */

    @Override
    public void process(Item item, ParameterSet parameters,
                        ParameterSet tunnelParams, Outputter out, XPathContext context,
                        Location locationId) throws XPathException {
        if (item instanceof ArrayItem arrayItem) {
            SequenceCollector collector = context.getController().allocateSequenceOutputter();
            ComplexContentOutputter cco = new ComplexContentOutputter(collector);
            ProxyOutputter checker = new ShallowCopyProxyOutputterForArrays(cco, locationId);
  
            collector.setSystemId(out.getSystemId());
            GroundedValue inputMembers = ArrayFunctionSet.arrayMembers(arrayItem);
            SequenceIterator iter = inputMembers.iterate();

            PipelineConfiguration pipe = out.getPipelineConfiguration();
            XPathContextMajor c2 = context.newContext();
            c2.setOrigin(this);
            c2.trackFocus(iter);
            c2.setCurrentComponent(c2.getCurrentMode());
            pipe.setXPathContext(c2);
            TailCall tc = context.getCurrentMode().getActor().applyTemplates(
                    parameters, tunnelParams, null, checker, c2, locationId);
            while (tc != null) {
                tc = tc.processLeavingTail();
            }
            pipe.setXPathContext(context);

            List<GroundedValue> members = new ArrayList<>();
            for (Item it : collector.getList()) {
                members.add(((MapItem)it).get(new StringValue("value")));
            }
            SimpleArrayItem newArray = new SimpleArrayItem(members);
            out.append(newArray, locationId, 0);

        } else if (item instanceof MapItem) {

            int size = ((MapItem)item).size();
            if (size == 1) {
                // If it's a singleton map, we can't break it down any further
                AtomicValue key = null;
                GroundedValue singletonValue = null;
                for (KeyValuePair pair : ((MapItem)item).keyValuePairs()) {
                    key = pair.key();
                    singletonValue = pair.value();
                    break;
                }
                SequenceCollector collector = context.getController().allocateSequenceOutputter();
                ComplexContentOutputter cco = new ComplexContentOutputter(collector);

                collector.setSystemId(out.getSystemId());
                SequenceIterator iter = singletonValue.iterate();

                PipelineConfiguration pipe = out.getPipelineConfiguration();
                XPathContextMajor c2 = context.newContext();
                c2.setOrigin(this);
                c2.trackFocus(iter);
                c2.setCurrentComponent(c2.getCurrentMode());
                pipe.setXPathContext(c2);
                TailCall tc = context.getCurrentMode().getActor().applyTemplates(
                        parameters, tunnelParams, null, cco, c2, locationId);
                while (tc != null) {
                    tc = tc.processLeavingTail();
                }
                pipe.setXPathContext(context);
                MapItem singletonMap = new SingleEntryMap(key, collector.getSequence(), 40);
                out.append(singletonMap, locationId, 0);

            } else if (size > 1) {

                SequenceCollector collector = context.getController().allocateSequenceOutputter();
                ComplexContentOutputter cco = new ComplexContentOutputter(collector);
                ProxyOutputter checker = new ShallowCopyProxyOutputterForMaps(cco, locationId);

                collector.setSystemId(out.getSystemId());
                SequenceIterator iter = ((MapItem) item).entries();

                PipelineConfiguration pipe = out.getPipelineConfiguration();
                XPathContextMajor c2 = context.newContext();
                c2.setOrigin(this);
                c2.trackFocus(iter);
                c2.setCurrentComponent(c2.getCurrentMode());
                pipe.setXPathContext(c2);
                TailCall tc = context.getCurrentMode().getActor().applyTemplates(
                        parameters, tunnelParams, null, checker, c2, locationId);
                while (tc != null) {
                    tc = tc.processLeavingTail();
                }
                pipe.setXPathContext(context);

                MapItem mergedMap = MapFunctionSet.MapMerge.mergeMaps(
                        collector.iterate(), context, null, 40);
                out.append(mergedMap, locationId, 0);
            }


        } else {
            SequenceCollector collector = context.getController().allocateSequenceOutputter();
            ComplexContentOutputter cco = new ComplexContentOutputter(collector);
            collector.setSystemId(out.getSystemId());
            super.process(item, parameters, tunnelParams, cco, context, locationId);
            SequenceIterator resultIter = collector.iterate();
            Item resultItem;
            while ((resultItem = resultIter.next()) != null) {
                out.append(resultItem);
            }
        }

    }

    /**
     * Identify this built-in rule set
     *
     * @return "shallow-copy"
     */

    @Override
    public String getName() {
        return "shallow-copy-all";
    }

    private static class ShallowCopyProxyOutputterForMaps extends ProxyOutputter {

        private final Location locationId;

        public ShallowCopyProxyOutputterForMaps(ComplexContentOutputter cco, Location locationId) {
            super(cco);
            this.locationId = locationId;
        }

        @Override
        public void append(Item item) throws XPathException {
            if (item instanceof MapItem) {
                super.append(item);
            } else {
                mustBeParcel(locationId);
            }
        }

        @Override
        public void append(Item item, Location locationId, int properties) throws XPathException {
            if (item instanceof MapItem) {
                super.append(item, locationId, properties);
            } else {
                mustBeParcel(locationId);
            }
        }


        private void mustBeParcel(Location locationId) throws XPathException {
            throw new XPathException("Template rule invoked when processing a map using the "
                                             + "shallow-copy-all rule must return a sequence of maps", "XPTY0004", locationId);
        }
    }

    private static class ShallowCopyProxyOutputterForArrays extends ProxyOutputter {

        private final Location locationId;

        public ShallowCopyProxyOutputterForArrays(ComplexContentOutputter cco, Location locationId) {
            super(cco);
            this.locationId = locationId;
        }

        @Override
        public void append(Item item) throws XPathException {
            if (RecordType.VALUE_RECORD.matches(item)) {
                super.append(item);
            } else if (item instanceof ConstantFunction fn) {
                // Temporary while the 4.0 spec gets sorted out
                super.append(new ShapedMap(ArrayFunctionSet.VALUE_RECORD, fn.getConstantValue()));
            } else {
                mustBeValueRecord(locationId);
            }
        }

        @Override
        public void append(Item item, Location locationId, int properties) throws XPathException {
            if (RecordType.VALUE_RECORD.matches(item)) {
                super.append(item, locationId, properties);
            } else if (item instanceof ConstantFunction fn) {
                // Temporary while the 4.0 spec gets sorted out
                super.append(new ShapedMap(ArrayFunctionSet.VALUE_RECORD, fn.getConstantValue()));
            } else {
                mustBeValueRecord(locationId);
            }
        }


        private void mustBeValueRecord(Location locationId) throws XPathException {
            throw new XPathException("Template rule invoked when processing an array using the "
                                             + "shallow-copy-all rule must return a sequence of value records", "XPTY0004", locationId);
        }
    }


}
