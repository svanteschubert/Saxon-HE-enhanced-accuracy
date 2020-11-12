////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.event.PushToReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.TransformerReceiver;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.StandardLogger;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Push;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.trans.CompilerInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.value.StringValue;

import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.*;

/**
 * A trace listener that records timing information for templates and functions, outputting this
 * information as an HTML report to a specified destination when the transformation completes.
 */

public class TimingTraceListener implements TraceListener {

    private int runs = 0;

    private static class ComponentMetrics {
        TraceableComponent component;
        Map<String, Object> properties;
        long gross;
        long net;
        long count;
    }


    private Logger out = new StandardLogger();
    private long t_total;
    private Stack<ComponentMetrics> metrics = new Stack<>();
    private HashMap<Traceable, ComponentMetrics> instructMap = new HashMap<>();
    protected Configuration config = null;

    private Map<Traceable, Integer> recursionDepth = new HashMap<>();
    private HostLanguage lang = HostLanguage.XSLT;

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
     * Called at start
     */

    @Override
    public void open(/*@NotNull*/ Controller controller) {
        config = controller.getConfiguration();
        lang = controller.getExecutable().getHostLanguage();
        t_total = System.nanoTime();
    }

    /**
     * Called at end. This method builds the XML out and analyzed html output
     */

    @Override
    public void close() {
        t_total = System.nanoTime() - t_total;
        runs++;
        try {
            PreparedStylesheet sheet = this.getStyleSheet();
            XsltController controller = sheet.newController();

            SerializationProperties props = new SerializationProperties();
            props.setProperty("method", "html");
            props.setProperty("indent", "yes");
            controller.setTraceListener(null);
            TransformerReceiver tr = new TransformerReceiver(controller);
            controller.initializeController(new GlobalParameterSet());
            tr.open();
            Receiver result = config.getSerializerFactory().getReceiver(out.asStreamResult(), props, controller.makePipelineConfiguration());
            tr.setDestination(result);

            Push push = new PushToReceiver(tr);
            Push.Document doc = push.document(true);
            Push.Element trace = doc.element("trace")
                    .attribute("t-total", Double.toString((double) t_total / 1000000));
            for (ComponentMetrics ins : instructMap.values()) {
                Push.Element fn = trace.element("fn");
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
                        .attribute("count", Long.toString(ins.count/ runs))
                        .attribute("t-sum-net", Double.toString((double) ins.net / runs / 1000000))
                        .attribute("t-avg-net", Double.toString(ins.net / (double) ins.count / 1000000))
                        .attribute("t-sum", Double.toString((double) ins.gross / runs / 1000000))
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

    /**
     * Called when an instruction in the stylesheet gets processed
     */

    @Override
    public void enter(/*@NotNull*/ Traceable instruction, Map<String, Object> properties, XPathContext context) {
        if (isTarget(instruction)) {
            long start = System.nanoTime();
            ComponentMetrics metric = new ComponentMetrics();
            metric.component = (TraceableComponent)instruction;
            metric.properties = properties;
            metric.gross = start;
            metrics.add(metric);
            Integer depth = recursionDepth.get(instruction);
            if (depth == null) {
                recursionDepth.put(instruction, 0);
            } else {
                recursionDepth.put(instruction, depth+1);
            }
        }
    }

    private boolean isTarget(Traceable traceable) {
        return traceable instanceof UserFunction ||
            traceable instanceof GlobalVariable ||
            traceable instanceof NamedTemplate ||
            traceable instanceof TemplateRule;
    }

    /**
     * Called after an instruction of the stylesheet got processed
     * @param instruction the instruction or other construct that has now finished execution
     */

    @Override
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
    private PreparedStylesheet getStyleSheet() throws XPathException {
        InputStream in = getStylesheetInputStream();
        StreamSource ss = new StreamSource(in, "profile.xsl");
        CompilerInfo info = config.getDefaultXsltCompilerInfo();
        info.setParameter(new StructuredQName("", "", "lang"),
                          new StringValue(this.lang == HostLanguage.XSLT ? "XSLT" : "XQuery"));
        return Compilation.compileSingletonPackage(config, info, ss);
    }

    /**
     * Get an input stream containing the stylesheet used for formatting results
     * @return the input stream
     */

    private InputStream getStylesheetInputStream() {
        List<String> messages = new ArrayList<>();
        List<ClassLoader> classLoaders = new ArrayList<>();
        return Configuration.locateResource("profile.xsl", messages, classLoaders);
    }

}

