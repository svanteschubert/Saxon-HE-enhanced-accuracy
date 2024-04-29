////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.Version;
import net.sf.saxon.event.PushToReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.TransformerReceiver;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.StandardLogger;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.push.Document;
import net.sf.saxon.s9api.push.Element;
import net.sf.saxon.s9api.push.Push;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.trans.CompilerInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.StringValue;

import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * A trace listener that records timing information for templates and functions, outputting this
 * information as an HTML report to a specified destination when the transformation completes.
 */

public class TimingTraceListener implements TraceListener {

    private URL stylesheet = null;
    private PreparedStylesheet reportingStylesheet;
    // The transformStack allows for the fact that multiple transformations can be initiated using fn:transform(),
    // and all are reported to the same TimingTraceListener
    private final Stack<TimingRun> transformStack = new Stack<>();
    private Configuration config;

    private static class ComponentMetrics {
        TraceableComponent component;
        Map<String, Object> properties;
        long gross;
        long net;
        long count;
    }


    private Logger out = new StandardLogger();

    /**
     * Set the PrintStream to which the output will be written.
     *
     * @param stream the PrintStream to be used for output. By default, the output is written
     *               to System.err.
     */

    @Override
    public void setOutputDestination(Logger stream) {
        out = stream;
    }

    /**
     * Set the URI of the stylesheet to be used for formatting the results
     * @param stylesheet the URI of the stylesheet
     */

    public void setStylesheet(URL stylesheet) {
        this.stylesheet = stylesheet;
    }

    /**
     * Called at start
     */

    @Override
    public void open(/*@NotNull*/ Controller controller) {
        config = controller.getConfiguration();
        int depth = transformStack.size();
        transformStack.push(new TimingRun(controller, depth));
    }

    /**
     * Called at end. This method builds the XML out and analyzed html output
     */

    @Override
    public void close() {
        try {
            if (reportingStylesheet == null) {
                reportingStylesheet = getStyleSheet();
            }
            TimingRun run = transformStack.pop();
            run.complete(reportingStylesheet, out);
        } catch (XPathException e) {
            // no action
        }

    }

    /**
     * Called when an instruction in the stylesheet gets processed
     */

    @Override
    public void enter(/*@NotNull*/ Traceable instruction, Map<String, Object> properties, XPathContext context) {
        transformStack.peek().enter(instruction, properties, context);
    }

    /**
     * Called after an instruction of the stylesheet got processed
     * @param instruction the instruction or other construct that has now finished execution
     */

    @Override
    public void leave(/*@NotNull*/ Traceable instruction) {
        transformStack.peek().leave(instruction);
    }

    /**
     * Called when an item becomes current
     */

    @Override
    public void startCurrentItem(Item item) {
    }

    /**
     * Called after a node of the source tree got processed
     */

    @Override
    public void endCurrentItem(Item item) {
    }

    /**
     * Prepare Stylesheet to render the analyzed XML data out.
     * This method can be overridden in a subclass to produce the output in a different format.
     */
    /*@NotNull*/
    protected PreparedStylesheet getStyleSheet() throws XPathException {
        InputStream in = getStylesheetInputStream();
        StreamSource ss = new StreamSource(in, "profile.xsl");

        CompilerInfo info = config.getDefaultXsltCompilerInfo();
        PreparedStylesheet pss = Compilation.compileSingletonPackage(config, info, ss);
        try {
            in.close();
        } catch (IOException err) {
            out.warning("Preparation of profiling stylesheet failed: " + err.getMessage());
        }
        return pss;
    }


    /**
     * Get an input stream containing the stylesheet used for formatting results.
     * The method is protected so that a user-written stylesheet can be supplied in a subclass.
     * @return the input stream containing the stylesheet for processing the results.
     */

    protected InputStream getStylesheetInputStream() {
        if (stylesheet == null) {
            return Version.platform.locateResource("profile.xsl", new ArrayList<>());
        } else {
            try {
                return stylesheet.openConnection().getInputStream();
            } catch (IOException e) {
                System.err.println("Unable to read " + stylesheet + "; using default stylesheet for -TP output");
                return Version.platform.locateResource("profile.xsl", new ArrayList<>());
            }
        }
    }

    private static class TimingRun {

        private long t_total;
        private final Stack<ComponentMetrics> metrics = new Stack<>();
        private final HashMap<Traceable, ComponentMetrics> instructMap = new HashMap<>();
        private Configuration config;
        private DateTimeValue startTime;
        private String executableUri = null;
        private int depth;

        private final Map<Traceable, Integer> recursionDepth = new HashMap<>();
        private HostLanguage lang = HostLanguage.XSLT;

        public TimingRun(Controller controller, int depth) {
            config = controller.getConfiguration();
            lang = controller.getExecutable().getHostLanguage();
            t_total = System.nanoTime();
            startTime = controller.getCurrentDateTime();
            this.depth = depth;
        }

        public void enter(/*@NotNull*/ Traceable instruction, Map<String, Object> properties, XPathContext context) {
            if (executableUri == null && instruction.getLocation().getSystemId() != null) {
                executableUri = instruction.getLocation().getSystemId();
            }
            if (isTarget(instruction)) {
                long start = System.nanoTime();
                ComponentMetrics metric = new ComponentMetrics();
                metric.component = (TraceableComponent) instruction;
                metric.properties = properties;
                metric.gross = start;
                metrics.add(metric);
                Integer depth = recursionDepth.get(instruction);
                if (depth == null) {
                    recursionDepth.put(instruction, 0);
                } else {
                    recursionDepth.put(instruction, depth + 1);
                }
            }
        }

        public void leave(/*@NotNull*/ Traceable instruction) {
            if (isTarget(instruction)) {
                ComponentMetrics metric = metrics.peek();
                long duration = System.nanoTime() - metric.gross;
                metric.net = duration - metric.net;
                metric.gross = duration;
                ComponentMetrics foundInstructDetails = instructMap.get(instruction);
                if (foundInstructDetails == null) {
                    metric.count = 1;
                    instructMap.put(instruction, metric);
                } else {
                    foundInstructDetails.count++;
                    Integer depth = recursionDepth.get(instruction);
                    recursionDepth.put(instruction, --depth);
                    if (depth == 0) {
                        foundInstructDetails.gross = foundInstructDetails.gross + metric.gross;
                    }
                    foundInstructDetails.net = foundInstructDetails.net + metric.net;
                }
                metrics.pop();
                if (!metrics.isEmpty()) {
                    ComponentMetrics parentInstruct = metrics.peek();
                    parentInstruct.net = parentInstruct.net + duration;
                }
            }
        }

        private boolean isTarget(Traceable traceable) {
            return traceable instanceof UserFunction ||
                    traceable instanceof GlobalVariable ||
                    traceable instanceof NamedTemplate ||
                    traceable instanceof TemplateRule;
        }

        public void complete(PreparedStylesheet sheet, Logger out) {
            t_total = System.nanoTime() - t_total;
            try {
                XsltController controller = sheet.newController();

                SerializationProperties props = new SerializationProperties();
                props.setProperty("method", "html");
                props.setProperty("indent", "yes");
                controller.setTraceListener(null);
                TransformerReceiver tr = new TransformerReceiver(controller);
                final GlobalParameterSet params = new GlobalParameterSet();
                params.put(NamespaceUri.NULL.qName("lang"),
                           StringValue.bmp(this.lang == HostLanguage.XSLT ? "XSLT" : "XQuery"));
                params.put(NamespaceUri.NULL.qName("startTime"),
                           startTime);
                params.put(NamespaceUri.NULL.qName("executableUri"),
                           StringValue.bmp(executableUri == null ? "" : executableUri));
                params.put(NamespaceUri.NULL.qName("nested"),
                           BooleanValue.get(depth > 0));
                controller.initializeController(params);
                tr.open();
                Receiver result = config.getSerializerFactory().getReceiver(out.asStreamResult(), props, controller.makePipelineConfiguration());
                tr.setDestination(result);

                Push push = new PushToReceiver(tr);
                Document doc = push.document(true);
                Element trace = doc.element("trace")
                        .attribute("t-total", Double.toString((double) t_total / 1000000));
                for (ComponentMetrics ins : instructMap.values()) {
                    Element fn = trace.element("fn");
                    String name;
                    if (ins.component.getObjectName() != null) {
                        name = ins.component.getObjectName().getDisplayName();
                        fn.attribute("name", name);
                    } else {
                        if (ins.properties.get("name") != null) {
                            name = ins.properties.get("name").toString();
                            fn.attribute("name", name);
                        }
                    }
                    if (ins.properties.get("match") != null) {
                        name = ins.properties.get("match").toString();
                        fn.attribute("match", name);
                    }
                    if (ins.properties.get("mode") != null) {
                        name = ins.properties.get("mode").toString();
                        fn.attribute("mode", name);
                    }
                    fn.attribute("construct", ins.component.getTracingTag())
                            .attribute("file", ins.component.getLocation().getSystemId())
                            .attribute("count", Long.toString(ins.count))
                            .attribute("t-sum-net", Double.toString((double) ins.net / 1000000))
                            .attribute("t-avg-net", Double.toString(ins.net / (double) ins.count / 1000000))
                            .attribute("t-sum", Double.toString((double) ins.gross / 1000000))
                            .attribute("t-avg", Double.toString(ins.gross / (double) ins.count / 1000000))
                            .attribute("line", Long.toString(ins.component.getLocation().getLineNumber()))
                            .close();
                }
                doc.close();
            } catch (TransformerException e) {
                System.err.println("Unable to transform timing profile information: " + e.getMessage());
            } catch (SaxonApiException e) {
                System.err.println("Unable to generate timing profile information: " + e.getMessage());
            }
        }

    }

}

