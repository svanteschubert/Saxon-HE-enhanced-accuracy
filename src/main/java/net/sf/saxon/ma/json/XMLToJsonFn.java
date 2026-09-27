////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
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
import net.sf.saxon.ma.map.EmptyMap;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.UniStringConsumer;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.trans.XPathException;
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

    public static OptionsParameter makeOptionsParameter(int version) {
        OptionsParameter xmlToJsonOptions = new OptionsParameter(version);
        xmlToJsonOptions.addAllowedOption("indent", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        xmlToJsonOptions.addAllowedOption("escape-solidus", SequenceType.SINGLE_BOOLEAN, BooleanValue.TRUE);
        return xmlToJsonOptions;
    }

    private static class Options {
        public boolean indent;
        public boolean escapeSolidus = true;
        public boolean retainNumberFormat;
    }

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo xml = (NodeInfo) arguments[0].head();
        if (xml == null) {
            return EmptySequence.INSTANCE;
        }

        Options options = getOptions(context, arguments);

        PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
        pipe.setXPathContext(context);
        UnicodeBuilder uniBuffer = new UnicodeBuilder();
        convertToJson(xml, uniBuffer, options, context);
        return new StringValue(uniBuffer.toUnicodeString());
    }

    private Options getOptions(XPathContext context, Sequence[] arguments) throws XPathException {
        Options o = new Options();
        o.retainNumberFormat = getRetainedStaticContext().getPackageData().getHostLanguageVersion() >= 40;
        if (getArity() > 1) {
            MapItem suppliedOptions = (MapItem) arguments[1].head();
            if (suppliedOptions == null) {
                suppliedOptions = EmptyMap.INSTANCE_40;
            }
            int version = getRetainedStaticContext().getPackageData().getHostLanguageVersion();
            Map<String, GroundedValue> options =
                    getDetails().optionDetails.processSuppliedOptions(suppliedOptions, context, version);
            o.indent = ((BooleanValue) options.get("indent").head()).getBooleanValue();
            o.escapeSolidus = ((BooleanValue) options.get("escape-solidus").head()).getBooleanValue();
            return o;
        } else {
            return o;
        }
    }

    @Override
    public void process(Outputter destination, XPathContext context, Sequence[] arguments) throws XPathException {
        NodeInfo xml = (NodeInfo) arguments[0].head();
        if (xml != null) {
            Options options = getOptions(context, arguments);
            PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
            pipe.setXPathContext(context);
            convertToJson(xml, destination.getStringReceiver(false, Loc.NONE), options, context);
        }
    }

    private void convertToJson(NodeInfo xml, UniStringConsumer output, Options options, XPathContext context) throws XPathException {
        PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
        pipe.setXPathContext(context);
        JsonReceiver receiver = new JsonReceiver(pipe, context, output);
        receiver.setIndenting(options.indent);
        receiver.setRetainNumberFormat(options.retainNumberFormat);
        receiver.setEscapeSolidus(options.escapeSolidus);
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
