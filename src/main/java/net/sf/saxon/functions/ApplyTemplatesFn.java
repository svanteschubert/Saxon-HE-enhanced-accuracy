////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Controller;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.SequenceCollector;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.ma.map.EmptyMap;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.FocusTrackingIterator;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ChoiceItemType;
import net.sf.saxon.type.EnumerationUnionType;
import net.sf.saxon.value.*;

import java.util.Collections;
import java.util.Map;

/**
 * Implements the XSLT 4.0 function apply-templates()
 */

public class ApplyTemplatesFn extends SystemFunction {


    public static OptionsParameter makeOptionsParameter() {
        EnumerationUnionType specialModes =
                EnumerationUnionType.of("#current", "#unnamed", "#default");
        ChoiceItemType modeType = ChoiceItemType.of(BuiltInAtomicType.QNAME, specialModes);
        SequenceType paramsType = SequenceType.optional(
                new MapType(BuiltInAtomicType.QNAME, SequenceType.ANY_SEQUENCE));
        OptionsParameter options = new OptionsParameter(40);
        options.addAllowedOption("mode", SequenceType.optional(modeType), EmptySequence.INSTANCE);
        options.addAllowedOption("params", paramsType, EmptyMap.INSTANCE_40);
        options.addAllowedOption("tunnel-params", paramsType, EmptyMap.INSTANCE_40);
        return options;
    }


    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        Controller controller = context.getController();
        if (!(controller instanceof XsltController)) {
            throw new XPathException("fn:apply-templates() function is only available in XSLT");
        }
        RuleManager ruleManager = controller.getRuleManager();
        Mode mode;
        Map<String, GroundedValue> checkedOptions = Collections.emptyMap();
        if (getArity() >= 2) {
            MapItem options = (MapItem) arguments[1].head();
            if (options != null) {
                checkedOptions = getDetails().optionDetails.processSuppliedOptions(options, context, 40);
            }
        }
        AtomicValue modeVal = (AtomicValue) checkedOptions.get("mode");
        if (modeVal instanceof QNameValue) {
            StructuredQName modeName = ((QNameValue) modeVal).getStructuredQName();
            mode = ruleManager.obtainMode(modeName, false);
            if (mode == null) {
                throw new XPathException("Unknown mode " + modeName.getEQName());
            }
        } else if (modeVal instanceof StringValue) {
            String modeToken = modeVal.getStringValue();
            switch (modeToken) {
                case "#default":
                    throw new XPathException("Mode=#default not yet implemented");
                case "#unnamed":
                    mode = ruleManager.obtainMode(Mode.UNNAMED_MODE_NAME, false);
                    break;
                case "#current":
                    mode = context.getCurrentMode().getActor();
                    break;
                default:
                    throw new XPathException("Invalid mode requested: " + modeToken);
            }
        } else {
            mode = context.getCurrentMode().getActor();
            //throw new XPathException("Mode=#default not yet implemented");
        }

        ParameterSet params = makeParameterSet(null, checkedOptions,"params");
        ParameterSet existingTunnelParams = context.getTunnelParameters();
        ParameterSet tunnels = makeParameterSet(existingTunnelParams, checkedOptions, "tunnel-params");

        XPathContextMajor newContext = context.newContext();
        newContext.setCurrentIterator(new FocusTrackingIterator(arguments[0].iterate()));
        SequenceCollector seq = new SequenceCollector(controller.makePipelineConfiguration());
        ComplexContentOutputter cco = new ComplexContentOutputter(seq);
        mode.applyTemplates(params, tunnels, null, cco, newContext, Loc.NONE);
        return seq.getSequence();

    }

    private ParameterSet makeParameterSet(ParameterSet existing,
                                          Map<String, GroundedValue> checkedOptions,
                                          String optionName) {
        if (checkedOptions == null) {
            return existing;
        }
        MapItem supplied = (MapItem)checkedOptions.get(optionName);
        if (supplied == null) {
            return existing;
        }
        ParameterSet params = existing == null
                ? new ParameterSet(supplied.size())
                : new ParameterSet(existing, supplied.size());
        for (KeyValuePair pair : supplied.keyValuePairs()) {
            params.put(((QNameValue)pair.key()).getStructuredQName(), pair.value(), false);
        }
        return params;
    }
};


