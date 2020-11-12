package net.sf.saxon.trans;

import net.sf.saxon.lib.ErrorReporter;

/**
 * An unchecked exception, triggered when a user-supplied {@link ErrorReporter} requests
 * that processing should be aborted
 */

public class XmlProcessingAbort extends RuntimeException {

    public XmlProcessingAbort(String message) {
        super(message);
    }
}

