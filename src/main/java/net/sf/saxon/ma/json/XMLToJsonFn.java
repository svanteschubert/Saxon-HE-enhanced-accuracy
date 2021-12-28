////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.event.DocumentValidator;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.functions.OptionsParameter;
import net.sf.saxon.functions.PushableFunction;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.CharSequenceConsumer;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.Map;

/**
 * Implement the XML to JSON conversion as a built-in function - fn:xml-to-json()
 */
public class XMLToJsonFn extends SystemFunction implements PushableFunction {

    public static OptionsParameter makeOptionsParameter() {
        OptionsParameter xmlToJsonOptions = new OptionsParameter();
        xmlToJsonOptions.addAllowedOption("indent", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        return xmlToJsonOptions;
    }

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo xml = (NodeInfo) arguments[0].head();
        if (xml == null) {
            return EmptySequence.getInstance();
        }

        boolean indent = isindentingRequested(context, arguments);

        PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
        pipe.setXPathContext(context);
        FastStringBuffer stringBuffer = new FastStringBuffer(2048);
        convertToJson(xml, stringBuffer, indent, context);
        return new StringValue(stringBuffer.condense());
    }

    private boolean isindentingRequested(XPathContext context, Sequence[] arguments) throws XPathException {
        if (getArity() > 1) {
            MapItem suppliedOptions = (MapItem) arguments[1].head();
            Map<String, Sequence> options = getDetails().optionDetails.processSuppliedOptions(suppliedOptions, context);
            return ((BooleanValue) options.get("indent").head()).getBooleanValue();
        }
        return false;
    }

    @Override
    public void process(Outputter destination, XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo xml = (NodeInfo) arguments[0].head();
        if (xml != null) {
            boolean indent = isindentingRequested(context, arguments);
            PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
            pipe.setXPathContext(context);
            convertToJson(xml, destination.getStringReceiver(false), indent, context);
        }
    }

    private void convertToJson(NodeInfo xml, CharSequenceConsumer output, boolean indent, XPathContext context) throws XPathException {
        PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
        pipe.setXPathContext(context);
        JsonReceiver receiver = new JsonReceiver(pipe, output);
        receiver.setIndenting(indent);
        Receiver r = receiver;
        if (xml.getNodeKind() == Type.DOCUMENT) {
            r = new DocumentValidator(r, "FOJS0006");
        }

        r.open();
        xml.copy(r, 0, Loc.NONE);
        r.close();
    }

    @Override
    public String getStreamerName() {
        return "XmlToJsonFn";
    }

}
