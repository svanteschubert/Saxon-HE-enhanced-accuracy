////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.instruct.Instruction;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.tree.AttributeLocation;

/**
 * The <b>XmlProcessingException</b> class is a concrete implementation of the <code>XmlProcessingError</code>
 * interface that wraps an {@link XPathException} object.
 */
public class XmlProcessingException implements XmlProcessingError {

    private XPathException exception;
    private boolean isWarning;
    private String fatalErrorMessage;

    /**
     * Create an XmlProcessingException wrapping an XPathException
     * @param exception the wrapped exception
     */
    public XmlProcessingException(XPathException exception) {
        this.exception = exception;
    }

    /**
     * Get the wrapped exception
     * @return the wrapped exception
     */

    public XPathException getXPathException() {
        return exception;
    }

    @Override
    public HostLanguage getHostLanguage() {
        Location loc = getLocation();
        if (loc instanceof Instruction || loc instanceof AttributeLocation) {
            return HostLanguage.XSLT;
        } else {
            return HostLanguage.XPATH;
        }
    }

    @Override
    public boolean isStaticError() {
        return exception.isStaticError();
    }

    @Override
    public boolean isTypeError() {
        return exception.isTypeError();
    }

    @Override
    public QName getErrorCode() {
        final StructuredQName errorCodeQName = exception.getErrorCodeQName();
        return errorCodeQName == null ? null : new QName(errorCodeQName);
    }

    @Override
    public String getMessage() {
        return exception.getMessage();
    }

    @Override
    public Location getLocation() {
        return exception.getLocator();
    }

    @Override
    public boolean isWarning() {
        return isWarning;
    }

    @Override
    public String getPath() {
        return null;
    }

    @Override
    public Throwable getCause() {
        return exception.getCause();
    }

    public void setWarning(boolean warning) {
        isWarning = warning;
    }

    @Override
    public XmlProcessingException asWarning() {
        XmlProcessingException e2 = new XmlProcessingException(exception);
        e2.setWarning(true);
        return e2;
    }


    /**
     * Indicate that this error is to be treated as fatal; that is, execution will be abandoned
     * after reporting this error. This method may be called by an {@link ErrorReporter}, for example
     * if the error is considered so severe that further processing is not worthwhile, or if
     * too many errors have been signalled. There is no absolute guarantee that setting this
     * property will cause execution to be abandoned. If a dynamic error is marked as fatal, it
     * will generally not be caught by any try/catch mechanism within the stylesheet or query.
     * @param message an error message giving the reason for the fatal error
     */

    @Override
    public void setFatal(String message) {
        this.fatalErrorMessage = message;
    }

    /**
     * Ask whether this error is to be treated as fatal, and if so, return the relevant messsage
     *
     * @return a non-null message if the error has been marked as a fatal error.
     */
    @Override
    public String getFatalErrorMessage() {
        return this.fatalErrorMessage;
    }

    /**
     * Ask whether this static error has already been reported
     *
     * @return true if the error has already been reported
     */

    @Override
    public boolean isAlreadyReported() {
        return exception.hasBeenReported();
    }

    /**
     * Say whether this error has already been reported
     *
     * @param reported true if the error has been reported
     */

    @Override
    public void setAlreadyReported(boolean reported) {
        exception.setHasBeenReported(reported);
    }
}
