////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.PushToReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.StandardLogger;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.push.Document;
import net.sf.saxon.s9api.push.Element;
import net.sf.saxon.s9api.push.Push;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.rules.RuleTarget;

import java.net.URL;
import java.util.*;

/**
 * A trace listener that records coverage data (actually execution counts) for instructions in
 * an XSLT stylesheet, outputting the histogram at the end as XML.
 */

public class CoverageTraceListener implements TraceListener {
    private Configuration config;

    // We need to maintain separate histograms for each module. Earlier versions of this class
    // mapped traceable expressions. This version simplifies it to just track hits per line
    // number.
    private final Map<String, CoverageRecord> histograms = new HashMap<>();

    private Map<String, CoverageRecord> queryMap = null;

    private int openCount = 0;

    private Logger out = StandardLogger.makeLogger();

    public CoverageTraceListener() {}

    public CoverageTraceListener(Map<String, CoverageRecord> queryMap) {
        this.queryMap = queryMap;
    }

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
     * Set the URI of the stylesheet to be used for formatting the results.
     * Ignored by this listener.
     * @param stylesheet the URI of the stylesheet
     */
    public void setStylesheet(URL stylesheet) {
        // ignored
    }

    /**
     * Called at start
     */

    @Override
    public void open(/*@NotNull*/ Controller controller) {
        openCount++;
        config = controller.getConfiguration();
        if (queryMap != null) {
            histograms.putAll(queryMap);
        } else {
            getAllTracePoints(controller);
        }
    }

    /**
     * Called at end. This method builds the XML output.
     */
    @Override
    public void close() {
        openCount--;

        if (openCount > 0) {
            return;
        }

        try {
            SerializationProperties props = new SerializationProperties();
            props.setProperty("method", "xml");
            props.setProperty("indent", "yes");

            Receiver result = config.getSerializerFactory().getReceiver(
                    out.asStreamResult(), props, config.makePipelineConfiguration());
            
            Push push = new PushToReceiver(result);
            Document doc = push.document(true);
            Element root = doc.element("coverage");

            for (String module : histograms.keySet()) {
                Element group = root.element(new QName("module"))
                        .attribute(new QName("module"), module);
                CoverageRecord record = histograms.get(module);

                List<Integer> linenumbers = record.coveredLines();

                int index = 0;
                while (index < linenumbers.size()) {
                    int lineno = linenumbers.get(index);
                    group.element(new QName("T"))
                            .attribute(new QName("line"), ""+lineno)
                            .attribute(new QName("count"), ""+record.getCover(lineno));

                    index++;
                }
            }

            doc.close();
        } catch (SaxonApiException | XPathException e) {
            // no action
        }
    }

    /**
     * Called when an instruction in the stylesheet gets processed
     */

    @Override
    public void enter(Traceable instruction, Map<String, Object> properties, XPathContext context) {
        if (instruction instanceof ComponentTracer) {
            Expression body = ((ComponentTracer) instruction).getBody();
            if (body instanceof TraceExpression) {
                enter(((TraceExpression) body).getBody(), properties, context);
            } else {
                enter(body, properties, context);
            }
            return;
        }

        Location loc = instruction.getLocation();

        if (loc == null || loc.getSystemId() == null || loc.getLineNumber() <= 0) {
            return;
        }

        if (!histograms.containsKey(loc.getSystemId())) {
            histograms.put(loc.getSystemId(), new CoverageRecord());
        }
        CoverageRecord record = histograms.get(loc.getSystemId());
        record.addCover(loc.getLineNumber());
    }

    /**
     * Called after an instruction of the stylesheet got processed
     * @param instruction the instruction or other construct that has now finished execution
     */

    @Override
    public void leave(/*@NotNull*/ Traceable instruction) {
        // no action
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

    private void getAllTracePoints(Controller controller) {
        Executable exec = controller.getExecutable();
        for (PackageData pack : exec.getPackages()) {
            if (pack instanceof StylesheetPackage) {
                StylesheetPackage ssPack = ((StylesheetPackage) pack);
                for (Component component : ssPack.getComponentIndex().values()) {
                    if (component.getActor() instanceof Mode) {
                        try {
                            ((Mode)component.getActor()).processRules(rule -> {
                                RuleTarget t = rule.getAction();
                                if (t instanceof TemplateRule) {
                                    updateHistograms((TemplateRule) t);
                                    Expression templateBody = ((TemplateRule)t).getBody();
                                    gatherTracePoints(templateBody);
                                }
                            });
                        } catch (XPathException e) {
                            System.err.println("*** " + e.getMessage());
                            // ignore failures
                        }
                    } else if (component.getActor() instanceof Traceable) {
                        updateHistograms((Traceable) component.getActor());
                        Expression body = component.getActor().getBody();
                        gatherTracePoints(body);
                    }
                }
            }
        }
    }

    private void gatherTracePoints(Expression expr) {
        if (expr != null) {
            if (expr instanceof TraceExpression) {
                //System.err.println(XSLTTraceListener.tagName(expr));
                Expression traceExpression = ((TraceExpression) expr).getBody();
                if (traceExpression instanceof ComponentTracer) {
                    gatherTracePoints(((ComponentTracer) traceExpression).getBody());
                } else {
                    updateHistograms(((TraceExpression) expr).getBody());
                }
            }
            for (Operand o : expr.operands()) {
                gatherTracePoints(o.getChildExpression());
            }
        }
    }

    private void updateHistograms(Traceable traceable) {
        Location loc = traceable.getLocation();
        if (loc == null || loc.getSystemId() == null || loc.getLineNumber() <= 0) {
            return;
        }
        if (!histograms.containsKey(loc.getSystemId())) {
            histograms.put(loc.getSystemId(), new CoverageRecord());
        }
        CoverageRecord record = histograms.get(loc.getSystemId());
        record.addLocation(loc.getLineNumber());
    }
}

