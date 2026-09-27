////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.str.StringTool;
import net.sf.saxon.tree.util.Navigator;

import javax.xml.transform.TransformerException;
import java.util.Objects;

/**
 * The <b>XmlProcessingIncident</b> class is a concrete implementation of the <code>XmlProcessingError</code>
 * interface that holds all the required information internally. (That is, no exception objects
 * are involved.)
 */
public class XmlProcessingIncident implements XmlProcessingError {

    private final String message;
    private String errorCode;
    private Throwable cause;
    private Location locator = null;
    private boolean _isWarning;
    private boolean _isTypeError;
    private String fatalErrorMessage;
    private boolean _hasBeenReported = false;
    private HostLanguage hostLanguage = HostLanguage.UNKNOWN;
    private boolean _isStaticError;
    private Expression failingExpression;

    /**
     * Create an XmlProcessingIncident
     * @param message  the error message
     * @param errorCode  the error code, supplied either as a local name, or in <code>Q{uri}local</code>
     *                   format. If supplied as a local name, the standard error namespace is assumed.
     * @param location  the location of the error
     */

    public XmlProcessingIncident(String message, String errorCode, Location location) {
        Objects.requireNonNull(message);
        Objects.requireNonNull(errorCode);
        Objects.requireNonNull(location);
        this.message = message;
        setErrorCodeAsEQName(errorCode);
        this.locator = location;
        this._isWarning = false;
    }

    /**
     * Create an Incident
     *
     * @param message   the error message
     */

    public XmlProcessingIncident(String message) {
        this.message = message;
    }

    /**
     * Create an Incident
     *
     * @param message   the error message
     * @param errorCode the error code, supplied either as a local name, or in <code>Q{uri}local</code>
     *                  format. If supplied as a local name, the standard error namespace is assumed.
     */

    public XmlProcessingIncident(String message, String errorCode) {
        this.message = message;
        setErrorCodeAsEQName(errorCode);
    }

    public XmlProcessingIncident(TransformerException err, boolean isWarning) {
        XPathException exception = XPathException.makeXPathException(err);
        message = exception.getMessage();
        errorCode = exception.getErrorCodeQName().getEQName();
        locator = exception.getLocator();
        this._isWarning = isWarning;
    }

    public void setWarning(boolean warning) {
        _isWarning = warning;
    }

    @Override
    public XmlProcessingIncident asWarning() {
        _isWarning = true;
        return this;
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
    public void setTerminationMessage(String message) {
        fatalErrorMessage = message;
    }

    /**
     * Ask whether this error is to be treated as fatal, and return the associated message
     *
     * @return a non-null message if the error has been marked as a fatal error.
     */

    @Override
    public String getTerminationMessage() {
        return fatalErrorMessage;
    }

    /**
     * Ask whether this static error has already been reported
     * @return true if the error has already been reported
     */

    @Override
    public boolean isAlreadyReported() {
        return _hasBeenReported;
    }

    /**
     * Say whether this error has already been reported
     * @param reported true if the error has been reported
     */

    @Override
    public void setAlreadyReported(boolean reported) {
        this._hasBeenReported = reported;
    }

    @Override
    public HostLanguage getHostLanguage() {
        return hostLanguage;
    }

    public void setHostLanguage(HostLanguage language) {
        this.hostLanguage = language;
    }

    @Override
    public boolean isTypeError() {
        return _isTypeError;
    }

    public void setTypeError(boolean isTypeError) {
        this._isTypeError = isTypeError;
    }

    @Override
    public boolean isStaticError() {
        return _isStaticError;
    }

    public void setStaticError(boolean isStaticError) {
        this._isStaticError = isStaticError;
    }


    /**
     * The error code, as a QName. May be null if no error code has been assigned
     *
     * @return QName
     */
    @Override
    public QName getErrorCode() {
        if (errorCode == null) {
            return null;
        }
        return new QName(StructuredQName.fromEQName40((errorCode)));
    }

    public void setErrorCodeAsEQName(String code) {
        if (code.startsWith("Q{")) {
            this.errorCode = code;
        } else if (NameChecker.isValidNCName(StringTool.codePoints(code))) {
            this.errorCode = "Q{" + NamespaceConstant.ERR + "}" + code;
        } else {
            this.errorCode = "Q{" + NamespaceConstant.SAXON + "}invalid-error-code";
        }
    }


    /**
     * Return the error message  associated with this error
     *
     * @return String
     */
    @Override
    public String getMessage() {
        return message;
    }

    /**
     * The URI of the query or stylesheet module in which the error was detected (as a string)
     * May be null if the location of the error is unknown, or if the error is not localized
     * to a specific module, or if the module in question has no known URI (for example, if
     * it was supplied as an anonymous Stream)
     *
     * @return String
     */
    @Override
    public String getModuleUri() {
        return getLocation().getSystemId();
    }

    public Expression getFailingExpression() {
        return failingExpression;
    }

    public void setFailingExpression(Expression expr) {
        this.failingExpression = expr;
    }

    @Override
    public Location getLocation() {
        return locator == null ? Loc.NONE : locator;
    }

    public void setLocation(Location loc) {
        this.locator = loc;
    }

    /**
     * The column number locating the error within a query or stylesheet module
     *
     * @return int
     */

    public int getColumnNumber() {
        Location locator = getLocation();
        if (locator != null) {
            return locator.getColumnNumber();
        }
        return -1;
    }

    /**
     * The line number locating the error within a query or stylesheet module
     *
     * @return int
     */

    public int getLineNumber() {
        Location locator = getLocation();
        if (locator != null) {
            return locator.getLineNumber();
        }
        return -1;
    }


    /**
     * Get a name identifying the kind of instruction, in terms meaningful to a user. This method
     * is not used in the case where the instruction name code is a standard name (&lt;1024).
     *
     * @return a name identifying the kind of instruction, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the instruction.
     */

    public String getInstructionName() {
        return ((NodeInfo) locator).getDisplayName();
    }


    /**
     * Ask whether this error is being reported as a warning condition.
     * If so, applications may ignore the condition, though the results may not be as intended.
     *
     * @return boolean
     */
    @Override
    public boolean isWarning() {
        return _isWarning;
    }

    /**
     * Get the absolute XPath expression that identifies the node within its document
     * where the error occurred, if available
     *
     * @return String - a path expression
     */
    @Override
    public String getPath() {
        if (locator instanceof NodeInfo) {
            return Navigator.getPath((NodeInfo) locator);
        } else {
            return null;
        }
    }

    /**
     * Return the underlying exception. This method may not be stable across Saxon releases.
     *
     * @return the underlying exception if there was one, or null otherwise
     */
    @Override
    public Throwable getCause() {
        return cause;
    }

    public void setCause(Throwable cause) {
        this.cause = cause;
    }


    public static void maybeSetHostLanguage(XmlProcessingError error, HostLanguage lang) {
        if (error.getHostLanguage() == HostLanguage.UNKNOWN) {
            if (error instanceof XmlProcessingIncident) {
                ((XmlProcessingIncident) error).setHostLanguage(lang);
            } else if (error instanceof XmlProcessingException) {
                ((XmlProcessingException) error).getXPathException().setHostLanguage(lang);
            }
        }
    }

    public static void maybeSetLocation(XmlProcessingError error, Location loc) {
        if (error.getLocation() == null || error.getLocation() == Loc.NONE) {
            if (error instanceof XmlProcessingIncident) {
                ((XmlProcessingIncident) error).setLocation(loc);
            } else if (error instanceof XmlProcessingException) {
                ((XmlProcessingException) error).getXPathException().setLocation(loc);
            }
        }
    }


}
