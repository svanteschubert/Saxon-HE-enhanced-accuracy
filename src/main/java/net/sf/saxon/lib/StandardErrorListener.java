////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.expr.EarlyEvaluationContext;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.type.ValidationFailure;
import org.xml.sax.SAXException;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import java.util.HashSet;
import java.util.Set;

/**
 * <B>StandardErrorListener</B> is the standard error handler for XSLT and XQuery processing
 * errors, used if no other ErrorListener is nominated.
 */

public class StandardErrorListener extends StandardDiagnostics implements ErrorListener {

    private int warningCount = 0;
    private int maximumNumberOfWarnings = 25;
    private int maxOrdinaryCharacter = 255;
    private int stackTraceDetail = 2;
    private Set<String> warningsIssued = new HashSet<>();
    protected transient Logger logger = new StandardLogger();

    /**
     * Create a Standard Error Listener
     */

    public StandardErrorListener() {
    }

    /**
     * Make a clean copy of this ErrorListener. This is necessary because the
     * standard error listener is stateful (it remembers how many errors there have been)
     *
     * @param hostLanguage the host language (not used by this implementation)
     * @return a copy of this error listener
     */

    public StandardErrorListener makeAnother(HostLanguage hostLanguage) {
        StandardErrorListener sel;
        try {
            sel = this.getClass().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            sel = new StandardErrorListener();
        }
        sel.logger = logger;
        return sel;
    }

    // Note, when the standard error listener is used, a new
    // one is created for each transformation, because it holds
    // the recovery policy and the warning count.

    /**
     * Set output destination for error messages (default is System.err)
     *
     * @param logger The Logger to use for error messages
     */

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Get the error output stream
     *
     * @return the error output stream
     */

    public Logger getLogger() {
        return logger;
    }


    /**
     * Set the maximum number of warnings that are reported; further warnings after this limit
     * are silently ignored
     *
     * @param max the maximum number of warnings output
     */

    public void setMaximumNumberOfWarnings(int max) {
        this.maximumNumberOfWarnings = max;
    }

    /**
     * Get the maximum number of warnings that are reported; further warnings after this limit
     * are silently ignored
     *
     * @return the maximum number of warnings output
     */

    public int getMaximumNumberOfWarnings() {
        return this.maximumNumberOfWarnings;
    }

    /**
     * Set the level of information to be included in a stack trace when a dynamic
     * error occurs.
     * @param level set to 0 (zero) for no stack trace; 1 (one) for a stack trace
     *              showing the templates and functions being executed; 2 (two) to
     *              add values of local variables and parameters (available in Saxon-EE only)
     *              Default is the maximum level available.
     */

    public void setStackTraceDetail(int level) {
        stackTraceDetail = level;
    }

    /**
     * Get the level of information to be included in a stack trace when a dynamic
     * error occurs.
     *
     * @return 0 (zero) for no stack trace; 1 (one) for a stack trace
     *              showing the templates and functions being executed; 2 (two) to
     *              add values of local variables and parameters (available in Saxon-EE only)
     *              Default is the maximum level available.
     */

    public int getStackTraceDetail() {
        return stackTraceDetail;
    }

    /**
     * Set the maximum codepoint value for a character to be considered non-special.
     * Special characters (codepoints above this value) will be expanded in hex for
     * extra clarity in the error message.
     * @param max the highest codepoint considered non-special (defaults to 255)
     */

    public void setMaxOrdinaryCharacter(int max) {
        maxOrdinaryCharacter = max;
    }

    /**
     * Set the maximum codepoint value for a character to be considered non-special.
     * Special characters (codepoints above this value) will be expanded in hex for
     * extra clarity in the error message.
     *
     * @param max the highest codepoint considered non-special (defaults to 255)
     */

    public int getMaxOrdinaryCharacter(int max) {
        return maxOrdinaryCharacter;
    }


    /**
     * Receive notification of a warning.
     * <p>Transformers can use this method to report conditions that
     * are not errors or fatal errors.  The default behaviour is to
     * take no action.</p>
     * <p>After invoking this method, the Transformer must continue with
     * the transformation. It should still be possible for the
     * application to process the document through to the end.</p>
     *
     * @param exception The warning information encapsulated in a
     *                  transformer exception.
     * @see javax.xml.transform.TransformerException
     */

    @Override
    public void warning(TransformerException exception) {

        if (logger == null) {
            // can happen after deserialization
            logger = new StandardLogger();
        }
        XPathException xe = XPathException.makeXPathException(exception);
        String message = constructMessage(exception, xe, "", "Warning ");
        if (!warningsIssued.contains(message)) {
            if (exception instanceof ValidationException) {
                logger.error(message);

            } else {
                logger.warning(message);
                warningCount++;
                if (warningCount > getMaximumNumberOfWarnings()) {
                    logger.info("No more warnings will be displayed");
                    warningCount = 0;
                }
            }
            warningsIssued.add(message);
        }
    }

    /**
     * Ask whether the error listener is reporting warnings. (If it isn't, the caller
     * can save the effort of constructing one; which is significant because it's represented
     * by an exception, and constructing exceptions is expensive).
     * @return true if the error listener is ignoring warnings, perhaps because the threshold
     * on the number of warnings has been exceeded.
     */

    public boolean isReportingWarnings() {
        return true;
    }

    /**
     * Receive notification of a recoverable error.
     * <p>The transformer must continue to provide normal parsing events
     * after invoking this method.  It should still be possible for the
     * application to process the document through to the end.</p>
     * <p>The action of the standard error listener depends on the
     * recovery policy that has been set, which may be one of RECOVER_SILENTLY,
     * RECOVER_WITH_WARNING, or DO_NOT_RECOVER</p>
     *
     * @param exception The error information encapsulated in a
     *                  transformer exception.
     * @see TransformerException
     */

    @Override
    public void error(TransformerException exception) {

        if (logger == null) {
            // can happen after deserialization
            logger = new StandardLogger();
        }
        String message;
        if (exception instanceof ValidationException) {
            String explanation = getExpandedMessage(exception);
            ValidationFailure failure = ((ValidationException) exception).getValidationFailure();
            String constraintReference = failure.getConstraintReferenceMessage();
            String validationLocation = failure.getValidationLocationText();
            String contextLocation = failure.getContextLocationText();
            message = "Validation error " +
                    getLocationMessage(exception) +
                    "\n  " +
                    wordWrap(explanation) +
                    wordWrap(validationLocation.isEmpty() ? "" : "\n  " + validationLocation) +
                    wordWrap(contextLocation.isEmpty() ? "" : "\n  " + contextLocation) +
                    wordWrap(constraintReference == null ? "" : "\n  " + constraintReference) +
                    formatListOfOffendingNodes(failure);
        } else {
            String prefix = "Error ";
            message = constructMessage(exception, XPathException.makeXPathException(exception), "", prefix);
        }

        if (exception instanceof ValidationException) {
            logger.error(message);

        } else {
            logger.error(message);
            logger.info("Processing terminated because error recovery is disabled");
            //throw XPathException.makeXPathException(exception);
        }
    }

    /**
     * Receive notification of a non-recoverable error.
     * <p>The application must assume that the transformation cannot
     * continue after the Transformer has invoked this method,
     * and should continue (if at all) only to collect
     * addition error messages. In fact, Transformers are free
     * to stop reporting events once this method has been invoked.</p>
     *
     * @param exception The error information encapsulated in a
     *                  transformer exception.
     */

    @Override
    public void fatalError(TransformerException exception) {
        XPathException xe = XPathException.makeXPathException(exception);
        if (xe.hasBeenReported()) {
            // don't report the same error twice
            return;
        }
        if (logger == null) {
            // can happen after deserialization
            logger = new StandardLogger();
        }
        String message;

        String lang = xe.getHostLanguage();
        String langText = "";
        if ("XPath".equals(lang)) {
            langText = "in expression ";
        } else if ("XQuery".equals(lang)) {
            langText = "in query ";
        } else if ("XSLT Pattern".equals(lang)) {
            langText = "in pattern ";
        }
        String kind = "Error ";
        if (xe.isSyntaxError()) {
            kind = "Syntax error ";
        } else if (xe.isStaticError()) {
            kind = "Static error ";
        } else if (xe.isTypeError()) {
            kind = "Type error ";
        }

        message = constructMessage(exception, xe, langText, kind);

        logger.error(message);
        if (exception instanceof XPathException) {
            ((XPathException) exception).setHasBeenReported(true);
            // probably redundant. It's the caller's job to set this flag, because there might be
            // a non-standard error listener in use.
        }

        if (exception instanceof XPathException) {
            XPathContext context = ((XPathException) exception).getXPathContext();
            if (context != null
                    && !(context instanceof EarlyEvaluationContext)) {
                outputStackTrace(logger, context);
            }
        }
    }

    /**
     * Construct an error or warning message.
     * <p>The default implementation outputs a two-line message: the first line is obtained
     * by calling {@link #constructFirstLine(TransformerException, XPathException, String, String)},
     * the second by calling {@link #constructSecondLine(TransformerException, XPathException)}; these are
     * concatenated with a newline and two spaces separating them.</p>
     * @param exception the exception originally reported to the ErrorListener
     * @param xe the result of converting the exception to an XPathException (possibly null).
     *           This may be the original exception, or an XPathException nested within it.
     * @param langText a string such as "in expression" or "in query" identifying the kind of
     *                 construct that is in error
     * @param kind the kind of error, for example "Syntax error", "Static error", "Type error"
     * @return the constructed message

     */

    public String constructMessage(TransformerException exception, XPathException xe, String langText, String kind) {
        return constructFirstLine(exception, xe, langText, kind)
                + "\n  "
                + constructSecondLine(exception, xe);
    }

    /**
     * Construct the first line of the error or warning message. This typically contains
     * information about the kind of error that occurred, and the location where it occurred
     * @param exception the exception originally reported to the ErrorListener
     * @param xe the result of converting the exception to an XPathException (possibly null).
     *           This may be the original exception, or an XPathException nested within it.
     * @param langText a string such as "in expression" or "in query" identifying the kind of
     *                 construct that is in error
     * @param kind the kind of error, for example "Syntax error", "Static error", "Type error"
     * @return the constructed message
     */
    public String constructFirstLine(TransformerException exception, XPathException xe, String langText, String kind) {
        Expression failingExpression = null;
        if (exception instanceof XPathException) {
            failingExpression = ((XPathException)exception).getFailingExpression();
        }
        if (xe.getLocator() instanceof AttributeLocation) {
            return kind + langText + getLocationMessageText(xe.getLocator());
        } else if (xe.getLocator() instanceof XPathParser.NestedLocation) {

            XPathParser.NestedLocation nestedLoc = (XPathParser.NestedLocation)xe.getLocator();
            Location outerLoc = nestedLoc.getContainingLocation();

            int line = nestedLoc.getLocalLineNumber();
            int column = nestedLoc.getColumnNumber();
            String lineInfo = line <= 0 ? "" : "on line " + line + ' ';
            String columnInfo = column < 0 ? "" : "at " + (line <= 0 ? "char " : "column ") + column + ' ';
            String nearBy = nestedLoc.getNearbyText();

            String extraContext = formatExtraContext(failingExpression, nearBy);

            if (outerLoc instanceof AttributeLocation) {
                // Typical XSLT case

                String innerLoc = lineInfo + extraContext + columnInfo;
                return kind + innerLoc + langText + getLocationMessageText(outerLoc);

            } else {
                // Typical XQuery case; no extra information available from the outer location

                String innerLoc = lineInfo + columnInfo;
                
                if (outerLoc.getLineNumber() > 1) {
                    innerLoc += "(" + langText + "on line " + outerLoc.getLineNumber() + ") ";
                }
                if (outerLoc.getSystemId() != null) {
                    innerLoc += "of " + outerLoc.getSystemId() + " ";
                }
                return kind + extraContext + innerLoc;
            }

        } else if (xe instanceof ValidationException) {
            return "Validation error " + getLocationMessage(exception);

        } else {
            return kind +
                    (failingExpression != null ? "evaluating (" + failingExpression.toShortString() + ") " : "") +
                    getLocationMessage(exception);

        }
    }

    /**
     * Create extra context information for locating the error from knowledge of the
     * containing expression or the nearby text retained by the tokenizer
     * @param failingExpression the subexpression in which the failure occurs (possibly null)
     * @param nearBy text from the input buffer near the error, retained by the tokenizer
     *               (possible null)
     * @return a message with extra context information, or an empty string if nothing is
     * available.
     */

    public String formatExtraContext(Expression failingExpression, String nearBy) {
        if (failingExpression != null) {
            if (failingExpression.isCallOn(net.sf.saxon.functions.Error.class)) {
                return "signaled by call to error() ";
            } else {
                return "evaluating (" + failingExpression.toShortString() + ") ";
            }
        } else if (nearBy != null && !nearBy.isEmpty()) {
            return (nearBy.startsWith("...") ? "near" : "in") +
                    ' ' + Err.wrap(nearBy) + " ";
        } else {
            return "";
        }
    }

    /**
     * Construct the second line of the error message. This contains the error code,
     * error object (if applicable) and the textual message.
     * <p>The default implementation depends on the kind of exception:</p>
     * <ul>
     *     <li>For a {@link ValidationException}, it constructs a message containing
     *     the error message together with explanations of which XSD Schema constraint
     *     was violated;</li>
     *     <li>For other exceptions, it returns a string comprising two characters
     *     of indentation, followed by the result of calling {@link #getExpandedMessage(TransformerException)}
     *     and then formatting the result using {@link #wordWrap(String)} and {@link #expandSpecialCharacters(CharSequence)}.</li>
     * </ul>
     * @param err the original reported exception
     * @return the string to be used as the second line of the error message
     */

    public String constructSecondLine(TransformerException err, XPathException xe) {
        if (xe instanceof ValidationException) {
            String explanation = getExpandedMessage(err);
            ValidationFailure failure = ((ValidationException) xe).getValidationFailure();
            String constraintReference = failure.getConstraintReferenceMessage();
            if (constraintReference != null) {
                explanation += " (" + constraintReference + ')';
            }
            return wordWrap(explanation + formatListOfOffendingNodes(failure));
        } else {
            return expandSpecialCharacters(wordWrap(getExpandedMessage(err))).toString();
        }
    }

    /**
     * Generate a stack trace for a dynamic error. The default implementation
     * calls {@link #printStackTrace(XPathContext, Logger, int)} supplying as
     * the third argument the current setting of {@link #setStackTraceDetail(int)},
     * which defaults to 2.
     *
     * @param out     the destination for the stack trace
     * @param context the context (which holds the information to be output)
     */

    protected void outputStackTrace(Logger out, XPathContext context) {
        printStackTrace(context, out, stackTraceDetail);
    }

    /**
     * Get a string identifying the location of an error.
     *
     * The default implementation first tries to get a {@link SourceLocator}
     * object from this exception or from any nested exception, and then,
     * if a {@code SourceLocator} is found, calls {@link #getLocationMessageText(SourceLocator)}
     * to format the location information.
     *
     * @param err the exception containing the location information
     * @return a message string describing the location
     */

    public String getLocationMessage(TransformerException err) {
        SourceLocator loc = err.getLocator();
        while (loc == null) {
            if (err.getException() instanceof TransformerException) {
                err = (TransformerException) err.getException();
                loc = err.getLocator();
            } else if (err.getCause() instanceof TransformerException) {
                err = (TransformerException) err.getCause();
                loc = err.getLocator();
            } else {
                return "";
            }
        }
        return getLocationMessageText(loc);
    }


    /**
     * Get a string containing the message for this exception and all contained exceptions.
     *
     * <p>The default implementation outputs the concatenation (with space-separation) of:</p>
     *
     * <ul>
     *     <li>The error code, formatted using {@link #formatErrorCode(TransformerException)}</li>
     *     <li>The error object, formatted using {@link #formatErrorObject(Sequence)} (whose
     *     default implementation outputs nothing)</li>
     *     <li>The message associated with this exception, and with all nested exceptions,
     *     formatted using {@link #formatNestedMessages(TransformerException, String)} </li>
     * </ul>
     *
     * @param err the exception containing the required information
     * @return a message that concatenates the message of this exception with its contained exceptions,
     *         also including information about the error code and location.
     */

    public String getExpandedMessage(TransformerException err) {

        String message = formatErrorCode(err);

        if (err instanceof XPathException) {
            Sequence errorObject = ((XPathException) err).getErrorObject();
            if (errorObject != null) {
                String errorObjectDesc = formatErrorObject(errorObject);
                if (errorObjectDesc != null) {
                    message += " " + errorObjectDesc;
                }
            }
        }

        message = formatNestedMessages(err, message);
        return message;
    }

    /**
     * Construct a message by combining the message from the top-level exception
     * plus any messages associated with nested exceptions
     * <p>The default implementation outputs the supplied message, followed by the
     * messages from any nested (contained) exceptions, colon-separated, with
     * some attempt to remove duplicated messages and certain redundant message prefixes
     * (such as "net.sf.saxon.trans.XPathException: " and "TRaX Transform Exception")</p>
     * @param err the original reported exception
     * @param message the message as constructed so far, containing the error code,
     *                error object, and the message from the original reported exception
     * @return an expanded message containing the supplied message followed by messages
     * from any contained exceptions.
     */

    public String formatNestedMessages(TransformerException err, String message) {
        Throwable e = err;
        while (true) {
            if (e == null) {
                break;
            }
            String next = e.getMessage();
            if (next == null) {
                next = "";
            }
            if (next.startsWith("net.sf.saxon.trans.XPathException: ")) {
                next = next.substring(next.indexOf(": ") + 2);
            }
            if (!("TRaX Transform Exception".equals(next) || message.endsWith(next))) {
                if (!"".equals(message) && !message.trim().endsWith(":")) {
                    message += ": ";
                }
                message += next;
            }
            if (e instanceof TransformerException) {
                e = ((TransformerException) e).getException();
            } else if (e instanceof SAXException) {
                e = ((SAXException) e).getException();
            } else {
                // e.printStackTrace();
                break;
            }
        }
        return message;
    }

    /**
     * Format the error code contained in the supplied exception object
     * @param err the exception object. The default implementation looks for an error
     *            code both in this object (if it is an {@link XPathException}) or in
     *            any wrapped {@code XPathException}. The default implementation ignores
     *            the namespace part of the error code (which is in general a QName) if it
     *            is the standard namespace {@link NamespaceConstant#ERR}; otherwise it displays
     *            the code in the format {@code prefix:localName}.
     * @return a string representation of the error code
     */

    public String formatErrorCode(TransformerException err) {
        StructuredQName qCode = null;
        if (err instanceof XPathException) {
            qCode = ((XPathException) err).getErrorCodeQName();
        }
        if (qCode == null && err.getException() instanceof XPathException) {
            qCode = ((XPathException) err.getException()).getErrorCodeQName();
        }
        String message = "";
        if (qCode != null) {
            if (qCode.hasURI(NamespaceConstant.ERR)) {
                message = qCode.getLocalPart();
            } else {
                message = qCode.getDisplayName();
            }
        }
        return message;
    }

    /**
     * Get a string representation of the error object associated with the exception (this represents
     * the final argument to fn:error, in the case of error triggered by calls on the fn:error function).
     * The standard implementation returns null, meaning that the error object is not displayed; but
     * the method can be overridden in a subclass to create a custom display of the error object, which
     * is then appended to the message text.
     *
     * @param errorObject the error object passed as the last argument to fn:error. Note: this method is
     *                    not called if the error object is absent/null
     * @return a string representation of the error object to be appended to the message, or null if no
     *         output of the error object is required
     */

    @SuppressWarnings({"UnusedParameters"})
    public String formatErrorObject(Sequence errorObject) {
        return null;
    }



    /**
     * Expand any special characters appearing in a message. Special characters
     * will be output as themselves, followed by a hex codepoint in the form [xHHHHH].
     * Characters are considered special if they have a codepoint above the value set
     * using {@link #setMaxOrdinaryCharacter(int)}. The default implementation returns
     * the message unchanged if the registered {@link Logger} is unicode-aware.
     *
     * <p>This method is provided because a number of operating systems cannot display
     * arbitrary Unicode characters on the system console, or cannot do so unless the console
     * has been specially configured. The simplest way to prevent messages being expanded
     * in this way is to mark the logger as being unicode-aware.</p>
     *
     * <p>If messages are expanded, then they will be expanded using the method
     * {@link #expandSpecialCharacters(CharSequence, int)}, which can be overridden
     * to define the actual format in which special characters are displayed.</p>
     *
     * @param in the message to be expanded
     * @return the expanded message
     */

    public CharSequence expandSpecialCharacters(CharSequence in) {
        if (logger.isUnicodeAware()) {
            return in;
        } else {
            return expandSpecialCharacters(in, maxOrdinaryCharacter);
        }
    }


}

