////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.transpile.CSharpModifiers;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;

/**
 * XPathException is used to indicate an error (static or dynamic) in an XPath expression,
 * or in a query or stylesheet.
 */

public class XPathException extends TransformerException {

    private boolean _isTypeError = false;
    private boolean _isSyntaxError = false;
    private boolean _isStaticError = false;
    private boolean _isGlobalError = false;
    private String hostLanguage = null;
    private StructuredQName errorCode;
    private Sequence errorObject;
    private Expression failingExpression;
    private boolean _hasBeenReported = false;
    transient XPathContext context;
    // declared transient because a compiled stylesheet might contain a "deferred action" dynamic error
    // and the EarlyEvaluationContext links back to the source stylesheet.

    /**
     * Create an XPathException with an error message
     *
     * @param message the message explaining what is wrong. This should not include location information.
     */

    public XPathException(String message) {
        super(message);
        breakPoint();
    }

    /**
     * Create an XPathException that wraps another exception
     *
     * @param err the wrapped error or exception
     */

    public XPathException(Throwable err) {
        super(err);
        breakPoint();
    }

    /**
     * Create an XPathException that supplies an error message and wraps an underlying exception
     *
     * @param message the error message (which should generally explain what Saxon was doing when the
     *                underlying exception occurred)
     * @param err     the underlying exception (the cause of this exception)
     */

    public XPathException(String message, Throwable err) {
        super(message, err);
        breakPoint();
    }

    /**
     * Create an XPathException that supplies an error message and supplies location information
     *
     * @param message the error message
     * @param loc     indicates where in the user-written query or stylesheet (or sometimes in a source
     *                document) the error occurred
     */

    public XPathException(String message, String errorCode, Location loc) {
        this(message, errorCode);
        setLocator(loc);
        breakPoint();
    }

    /**
     * Create an XPathException that supplies an error message and an error code
     *
     * @param message   the error message
     * @param errorCode the error code - an eight-character code, which is taken to be in the standard
     *                  system error code namespace
     */

    public XPathException(String message, String errorCode) {
        super(message);
        setErrorCode(errorCode);
        breakPoint();
    }

    /**
     * Create an XPathException that supplies an error message and an error code and provides the
     * dynamic context
     *
     * @param message   the error message
     * @param errorCode the error code - an eight-character code, which is taken to be in the standard
     *                  system error code namespace
     * @param context   the dynamic evaluation context
     */

    public XPathException(String message, String errorCode, XPathContext context) {
        super(message);
        setErrorCode(errorCode);
        setXPathContext(context);
        breakPoint();
    }

    /**
     * Breakpoint for debugging when we need to catch an error. Change to "assert false" to get
     * a stack trace when an exception is created.
     */

    private static void breakPoint() {
        //System.err.println("Error!!");
        assert true;
    }

    /**
     * Create an XPathException from an exception of another kind. If the supplied Exception is an XPathException;
     * if its cause is an XPathException, that XPathException is returned unchanged; otherwise the
     * supplied Exception is wrapped.
     *
     * @param err the supplied Exception
     * @return an XPathException obtained from the supplied TransformerException
     */

    /*@NotNull*/
    public static XPathException makeXPathException(Exception err) {
        if (err instanceof XPathException) {
            return (XPathException) err;
        } else if (err.getCause() instanceof XPathException) {
            return (XPathException) err.getCause();
        } else if (err instanceof TransformerException) {
            XPathException xe = new XPathException(err.getMessage(), err);
            xe.setLocator(((TransformerException) err).getLocator());
            return xe;
        } else {
            return new XPathException(err);
        }
    }

    public static XPathException fromXmlProcessingError(XmlProcessingError error) {
        if (error instanceof XmlProcessingException) {
            return ((XmlProcessingException)error).getXPathException();
        } else {
            XPathException e = new XPathException(error.getMessage());
            e.setLocation(error.getLocation());
            e.setHostLanguage(error.getHostLanguage());
            e.setIsStaticError(error.isStaticError());
            e.setIsTypeError(error.isTypeError());
            QName code = error.getErrorCode();
            if (code != null) {
                e.setErrorCodeQName(code.getStructuredQName());
            }
            return e;
        }
    }


    /**
     * Construct an exception that differs from a supplied exception only
     * by changing the error message
     * @param message the new message
     * @return a new exception, copying all the properties of this exception except
     * for the message
     */
    public XPathException withMessage(String message) {
        XPathException e2 = new XPathException(message);
        e2.setErrorCodeQName(getErrorCodeQName());
        e2.setLocation(getLocator());
        e2.setIsSyntaxError(isSyntaxError());
        e2.setIsTypeError(isTypeError());
        e2.setHostLanguage(getHostLanguage());
        e2.setXPathContext(getXPathContext());
        return e2;
    }

    /**
     * Set dynamic context information in the exception object
     *
     * @param context the dynamic context at the time the exception occurred
     */

    public void setXPathContext(XPathContext context) {
        this.context = context;
    }

    /**
     * Set dynamic context information in the exception object
     *
     * @param context the dynamic context at the time the exception occurred
     * @return this XPathException object in a modified state
     */
    public XPathException withXPathContext(XPathContext context) {
        this.context = context;
        return this;
    }

    public void setLocation(Location loc) {
        if (loc != null) {
            setLocator(loc.saveLocation());
        }
    }

    /**
     * Set location information in the exception object
     *
     * @param loc indicating where the exception occurred
     * @return this XPathException object in a modified state
     */

    public XPathException withLocation(Location loc) {
        setLocation(loc);
        return this;
    }


    public Expression getFailingExpression() {
        return failingExpression;
    }

    public XPathException withFailingExpression(Expression failingExpression) {
        if (failingExpression != null) {
            this.failingExpression = failingExpression;
            maybeSetLocation(failingExpression.getLocation());
        }
        return this;
    }


    public XPathException maybeWithFailingExpression(Expression failingExpression) {
        if (failingExpression != null) {
            if (this.failingExpression == null) {
                this.failingExpression = failingExpression;
            }
            maybeSetLocation(failingExpression.getLocation());
        }
        return this;
    }


    /**
     * Method getLocator retrieves an instance of a SourceLocator
     * object that specifies where an error occured.
     *
     * @return A SourceLocator object, or null if none was specified.
     */
    @Override
    @CSharpModifiers(code={"public", "override"})
    public Location getLocator() {
        SourceLocator locator = super.getLocator();
        if (locator == null) {
            return null;
        } else if (locator instanceof Location) {
            return (Location)locator;
        } else {
            return new Loc(locator);
        }
    }

    /**
     * Get the dynamic context at the time the exception occurred
     *
     * @return the dynamic context if known; otherwise null
     */

    public XPathContext getXPathContext() {
        return context;
    }

    /**
     * Mark this exception to indicate that it represents (or does not represent) a static error
     *
     * @param is true if this exception is a static error
     */

    public void setIsStaticError(boolean is) {
        _isStaticError = is;
    }

    public XPathException asStaticError() {
        setIsStaticError(true);
        return this;
    }

    /**
     * Ask whether this exception represents a static error
     *
     * @return true if this exception is a static error
     */

    public boolean isStaticError() {
        return _isStaticError;
    }

    /**
     * Mark this exception to indicate that it represents (or does not represent) a syntax error
     *
     * @param is true if this exception is a syntax error
     */

    public void setIsSyntaxError(boolean is) {
        if (is) {
            _isStaticError = true;
        }
        _isSyntaxError = is;
    }

    /**
     * Ask whether this exception represents a syntax error
     *
     * @return true if this exception is a syntax error
     */

    public boolean isSyntaxError() {
        return _isSyntaxError;
    }


    /**
     * Mark this exception to indicate that it represents (or does not represent) a type error
     *
     * @param is true if this exception is a type error
     */

    public void setIsTypeError(boolean is) {
        _isTypeError = is;
    }

    /**
     * Mark this exception to indicate that it represents a type error
     *
     * @return this XPathException in a modified state
     */

    public XPathException asTypeError() {
        setIsTypeError(true);
        return this;
    }

    public XPathException asTypeErrorIf(boolean condition) {
        setIsTypeError(condition);
        return this;
    }

    /**
     * Ask whether this exception represents a type error
     *
     * @return true if this exception is a type error
     */

    public boolean isTypeError() {
        return _isTypeError;
    }

    /**
     * Mark this exception to indicate that it originated while evaluating a global
     * variable reference, and is therefore to be reported regardless of the try/catch
     * context surrounding the variable reference
     *
     * @param is true if this exception is a global variable error
     */

    public void setIsGlobalError(boolean is) {
        _isGlobalError = is;
    }

    /**
     * Ask whether this exception originated while evaluating a global
     * variable reference, and is therefore to be reported regardless of the try/catch
     * context surrounding the variable reference
     *
     * @return true if this exception is a global variable error
     */

    public boolean isGlobalError() {
        return _isGlobalError;
    }

    /**
     * Set the host language code
     *
     * @param language a value such as "XPath", "XQuery", "XSLT Pattern"
     */

    public void setHostLanguage(String language) {
        this.hostLanguage = language;
    }

    /**
     * Set the host language code
     *
     * @param language a value such as "XPath", "XQuery", "XSLT Pattern"
     */

    public void setHostLanguage(HostLanguage language) {
        this.hostLanguage = language == HostLanguage.UNKNOWN ? null : language.toString();
    }

    /**
     * Get the host language code
     *
     * @return a value such as "XPath", "XQuery", "XSLT Pattern", or null
     */

    public String getHostLanguage() {
        return hostLanguage;
    }

    /**
     * Set the error code. The error code is a QName; this method sets the local part of the name,
     * setting the namespace of the error code to the standard system namespace {@link net.sf.saxon.lib.NamespaceConstant#ERR}
     *
     * @param code The local part of the name of the error code
     */

    public void setErrorCode(/*@Nullable*/ String code) {
        if (code != null) {
            errorCode = new StructuredQName("err", NamespaceUri.ERR, code);
        }
    }

    /**
     * Set the error code. The error code is a QName; this method sets the local part of the name,
     * setting the namespace of the error code to the standard system namespace {@link net.sf.saxon.lib.NamespaceConstant#ERR}
     *
     * @param code The local part of the name of the error code
     * @return this XPathException object in a modified state
     */
    public XPathException withErrorCode(String code) {
        setErrorCode(code);
        return this;
    }

    public XPathException withErrorCode(StructuredQName code) {
        setErrorCodeQName(code);
        return this;
    }

    public XPathException replacingErrorCode(String oldCode, String newCode) {
        if (hasErrorCode(oldCode)) {
            setErrorCode(newCode);
        }
        return this;
    }

    /**
     * Set the error code, provided it has not already been set.
     * The error code is a QName; this method sets the local part of the name,
     * setting the namespace of the error code to the standard system namespace {@link NamespaceConstant#ERR}
     *
     * @param code The local part of the name of the error code
     */

    public void maybeSetErrorCode(/*@Nullable*/ String code) {
        if (errorCode == null && code != null) {
            errorCode = new StructuredQName("err", NamespaceUri.ERR, code);
        }
    }

    public XPathException maybeWithErrorCode(String code) {
        maybeSetErrorCode(code);
        return this;
    }

    /**
     * Set the error code. The error code is a QName; this method sets both parts of the name.
     *
     * @param code The error code as a QName
     */

    public void setErrorCodeQName(/*@Nullable*/ StructuredQName code) {
        errorCode = code;
    }

    /**
     * Get the error code as a QName
     *
     * @return the error code as a QName
     */

    /*@Nullable*/
    public StructuredQName getErrorCodeQName() {
        return errorCode;
    }

    public String showErrorCode() {
        if (errorCode == null) {
            return "no_error_code";
        } else if (errorCode.hasURI(NamespaceUri.ERR)) {
            return errorCode.getLocalPart();
        } else {
            return errorCode.getEQName();
        }
    }

    /**
     * Ask whether the error code is a specific system error code
     * @param codes the local part of the error(s) expected
     * @return true if the error code matches one of the required codes,
     * treated as local names in the standard error namespace
     */

    public boolean hasErrorCode(String... codes) {
        if (errorCode != null && errorCode.hasURI(NamespaceUri.ERR)) {
            for (String code : codes) {
                if (errorCode.getLocalPart().equals(code)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Set the error object associated with this error. This is used by the standard XPath fn:error() function
     *
     * @param value the error object, as supplied to the fn:error() function
     */

    public void setErrorObject(Sequence value) {
        errorObject = value;
    }

    /**
     * Get the error object associated with this error. This is used by the standard XPath fn:error() function
     *
     * @return the error object, as supplied to the fn:error() function
     */

    public Sequence getErrorObject() {
        return errorObject;
    }

    /**
     * Mark this error to indicate that it has already been reported to the error listener, and should not be
     * reported again
     *
     * @param reported true if the error has been reported to the error listener
     */

    public void setHasBeenReported(boolean reported) {
        _hasBeenReported = reported;
    }

    /**
     * Ask whether this error is marked to indicate that it has already been reported to the error listener,
     * and should not be reported again
     *
     * @return true if this error has already been reported
     */

    public boolean hasBeenReported() {
        return _hasBeenReported;
    }

    /**
     * Set the location of a message, only if it is not already set
     *
     * @param here the current location (or null)
     */

    public void maybeSetLocation(Location here) {
        if (here != null) {
            if (getLocator() == null) {
                setLocator(here.saveLocation());
            } else if (getLocator().getLineNumber() == -1
                    && !(getLocator().getSystemId() != null && here.getSystemId() != null && !getLocator().getSystemId().equals(here.getSystemId()))) {
                setLocator(here.saveLocation());
            }
        }
    }

    public XPathException maybeWithLocation(Location here) {
        maybeSetLocation(here);
        return this;
    }

    /**
     * Set the context of a message, only if it is not already set
     *
     * @param context the current XPath context (or null)
     */

    public void maybeSetContext(XPathContext context) {
        if (getXPathContext() == null) {
            setXPathContext(context);
        }
    }

    public XPathException maybeWithContext(XPathContext context) {
        if (getXPathContext() == null) {
            setXPathContext(context);
        }
        return this;
    }

    /**
     * Tests whether this is a dynamic error that may be reported statically if it is detected statically
     *
     * @return true if the error can be reported statically
     */

    public boolean isReportableStatically() {
        if (isStaticError() || isTypeError()) {
            return true;
        }
        StructuredQName err = errorCode;
        if (err != null && err.hasURI(NamespaceUri.ERR)) {
            String local = err.getLocalPart();
            return local.equals("XTDE1260") ||
                    local.equals("XTDE1280") ||
                    local.equals("XTDE1390") ||
                    local.equals("XTDE1400") ||
                    local.equals("XTDE1428") ||
                    local.equals("XTDE1440") ||
                    local.equals("XTDE1460");
        }
        return false;
    }

    /**
     * Subclass of XPathException used to report circularities
     */

    public static class Circularity extends XPathException {

        /**
         * Create an exception indicating that a circularity was detected
         *
         * @param message the error message
         */
        public Circularity(String message) {
            super(message);
        }
    }

    /**
     * Subclass of XPathException used to report stack overflow
     */

    public static class StackOverflow extends XPathException {

        /**
         * Create an exception indicating that a circularity was detected
         *
         * @param message the error message
         */
        public StackOverflow(String message, String errorCode, Location location) {
            super(message, errorCode, location);
        }
    }

}

