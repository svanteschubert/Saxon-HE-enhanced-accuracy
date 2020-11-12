package com.saxonica.xqj.pull;

import net.sf.saxon.pull.PullFilter;
import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.trans.XPathException;

/**
 * Returns the events provided by an underlying PullProvider, with the addition of a START_DOCUMENT
 * event immediately after the START_OF_INPUT, and an END_DOCUMENT event immediately before the
 * END_OF_INPUT
 */
public class DocumentWrappingPullProvider extends PullFilter {

    boolean atStart = true;
    boolean atEnd = false;

    public DocumentWrappingPullProvider(PullProvider base) {
        super(base);
    }

    @Override
    public Event next() throws XPathException {
        if (atStart) {
            atStart = false;
            return Event.START_DOCUMENT;
        } else if (atEnd) {
            return Event.END_OF_INPUT;
        } else {
            Event event = getUnderlyingProvider().next();
            if (event == Event.END_OF_INPUT) {
                atEnd = true;
                return Event.END_DOCUMENT;
            } else {
                return event;
            }
        }
    }
}

// Copyright (c) 2018-2020 Saxonica Limited

