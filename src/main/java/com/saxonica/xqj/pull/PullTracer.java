package com.saxonica.xqj.pull;

import net.sf.saxon.om.NamePool;
import net.sf.saxon.pull.PullFilter;
import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;

/**
 * PullTracer is a PullFilter that can be inserted into a pull pipeline for diagnostic purposes. It traces
 * all the events as they are read, writing details to System.err
 */

public class PullTracer extends PullFilter {

    private NamePool pool;

    /**
     * Create a PullTracer
     *
     * @param base the PullProvider to which requests are to be passed
     */

    public PullTracer(PullProvider base) {
        super(base);
    }

    /**
     * Get the next event. This implementation gets the next event from the underlying PullProvider,
     * copies it to the branch Receiver, and then returns the event to the caller.
     *
     * @return an integer code indicating the type of event. The code
     *         {@link net.sf.saxon.pull.PullProvider.Event#END_OF_INPUT} is returned at the end of the sequence.
     */

    @Override
    public Event next() throws XPathException {
        currentEvent = super.next();
        traceEvent(currentEvent);
        return currentEvent;
    }


    /**
     * Copy a pull event to a Receiver
     */

    private void traceEvent(PullProvider.Event event) {
        if (pool == null) {
            pool = getPipelineConfiguration().getConfiguration().getNamePool();
        }
        PullProvider in = getUnderlyingProvider();
        switch (event) {
            case START_DOCUMENT:
                System.err.println("START_DOCUMENT");
                break;

            case START_ELEMENT:
                System.err.println("START_ELEMENT " + in.getNodeName().getDisplayName());
                break;

            case TEXT:
                System.err.println("TEXT");
                try {
                    CharSequence cs = this.getStringValue();
                    FastStringBuffer sb = new FastStringBuffer(cs.length() * 5);
                    sb.cat('(');
                    for (int i = 0; i < cs.length(); i++) {
                        sb.append((int) cs.charAt(i) + " ");
                    }
                    sb.cat(')');
                    System.err.println(sb);
                } catch (XPathException err) {
                    // no-op
                }
                break;

            case COMMENT:
                System.err.println("COMMENT");
                break;

            case PROCESSING_INSTRUCTION:
                System.err.println("PROCESSING_INSTRUCTION");
                break;

            case END_ELEMENT:
                System.err.println("END_ELEMENT " + (in.getNodeName() == null ? "" : in.getNodeName().getDisplayName()));
                break;

            case END_DOCUMENT:
                System.err.println("END_DOCUMENT");
                break;

            case END_OF_INPUT:
                System.err.println("END_OF_INPUT");
                break;

            case ATOMIC_VALUE:
                try {
                    System.err.println("ATOMIC VALUE: " + in.getStringValue());
                } catch (XPathException e) {
                    //
                }
        }
    }
}

// Copyright (c) 2009-2020 Saxonica Limited
