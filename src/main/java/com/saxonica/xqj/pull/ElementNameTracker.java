package com.saxonica.xqj.pull;

import net.sf.saxon.om.NodeName;
import net.sf.saxon.pull.PullFilter;
import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.trans.XPathException;

import java.util.Arrays;

/**
 * This is a filter that can be added to a pull pipeline to remember element names so that
 * they are available immediately after the END_ELEMENT event is notified
 */
public class ElementNameTracker extends PullFilter {

    private NodeName[] namestack = new NodeName[20];
    int used = 0;
    NodeName elementJustEnded = null;

    public ElementNameTracker(PullProvider base) {
        super(base);
    }

    /**
     * Get the next event.
     * <p>Note that a subclass that overrides this method is responsible for ensuring
     * that current() works properly. This can be achieved by setting the field
     * currentEvent to the event returned by any call on next().</p>
     *
     * @return an integer code indicating the type of event. The code
     *         {@link net.sf.saxon.pull.PullProvider.Event#END_OF_INPUT} is returned at the end of the sequence.
     */

    @Override
    public Event next() throws XPathException {
        currentEvent = super.next();
        if (currentEvent == Event.START_ELEMENT) {
            NodeName nc = getNodeName();
            if (used >= namestack.length) {
                namestack = Arrays.copyOf(namestack, used*2);
            }
            namestack[used++] = nc;
        } else if (currentEvent == Event.END_ELEMENT) {
            elementJustEnded = namestack[--used];
        }
        return currentEvent;
    }

    /**
     * Get the node name identifying the name of the current node. This method
     * can be used after the {@link net.sf.saxon.pull.PullProvider.Event#START_ELEMENT}, {@link net.sf.saxon.pull.PullProvider.Event#PROCESSING_INSTRUCTION},
     * {@link net.sf.saxon.pull.PullProvider.Event#ATTRIBUTE}, or {@link net.sf.saxon.pull.PullProvider.Event#NAMESPACE} events. With some PullProvider implementations,
     * including this one, it can also be used after {@link net.sf.saxon.pull.PullProvider.Event#END_ELEMENT}: in fact, that is the
     * main purpose of this class.
     * If called at other times, the result is undefined and may result in an IllegalStateException.
     * If called when the current node is an unnamed namespace node (a node representing the default namespace)
     * the returned value is -1.
     *
     * @return the node name. This can be used to obtain the prefix, local name,
     *         and namespace URI from the name pool.
     */

    @Override
    public NodeName getNodeName() {
        if (currentEvent == Event.END_ELEMENT) {
            return elementJustEnded;
        } else {
            return super.getNodeName();
        }
    }
}

// Copyright (c) 2009-2020 Saxonica Limited
