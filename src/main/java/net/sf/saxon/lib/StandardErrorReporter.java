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
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingException;
import net.sf.saxon.tree.AttributeLocation;
import net.sf.saxon.type.ValidationException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * <B>StandardErrorReporter</B> is the standard error handler for processing XSLT and XQuery static
 * errors, used if no other error handler is nominated.
 */

public class StandardErrorReporter
        extends StandardDiagnostics
        implements ErrorReporter {

    //private int recoveryPolicy = Configuration.RECOVER_WITH_WARNINGS;
    //private int requestedRecoveryPolicy = Configuration.RECOVER_WITH_WARNINGS;
    private int warningCount = 0;
    private int maximumNumberOfWarnings = 25;
    private int errorCount = 0;
    private int maximumNumberOfErrors = 1000;
    private int maxOrdinaryCharacter = 255;
    private int stackTraceDetail = 2;
    private Set<String> warningsIssued = new HashSet<>();
    protected transient Logger logger = new StandardLogger();
    private XmlProcessingError latestError;
    private boolean outputErrorCodes = true;

    /**
     * Create a Standard Error Reporter
     */

    public StandardErrorReporter() {
    }

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
     * Set the maximum number of errors that are reported; further errors after this limit
     * result in the compilation being abandoned
     *
     * @param max the maximum number of errors output before the compilation is abandoned
     */

    public void setMaximumNumberOfErrors(int max) {
        this.maximumNumberOfErrors = max;
    }

    /**
     * Get the maximum number of errors that are reported; further errors after this limit
     * result in the compilation being abandoned
     *
     * @return the maximum number of errors output before the compilation is abandoned
     */

    public int getMaximumNumberOfErrors() {
        return this.maximumNumberOfErrors;
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
     * Set the level of information to be included in a stack trace when a dynamic
     * error occurs.
     *
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
     * showing the templates and functions being executed; 2 (two) to
     * add values of local variables and parameters (available in Saxon-EE only)
     * Default is the maximum level available.
     */

    public int getStackTraceDetail() {
        return stackTraceDetail;
    }

    /**
     * Say whether error codes should be included in the message. This is normally suppressed during
     * schema processing because the error codes (such as FORG0001) are generally useless.
     * @param include if true (the default), then error codes are included in the message
     */

    public void setOutputErrorCodes(boolean include) {
        this.outputErrorCodes = include;
    }

    /**
     * Report an error or warning
     *
     * @param error the error or warning being reported
     */

    @Override
    public void report(XmlProcessingError error) {
        if (error != latestError) {
            latestError = error;
            if (error.isWarning()) {
                warning(error);
            } else {
                error(error);
            }
        }
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
     * @param error The warning information encapsulated in a
     *                  transformer exception.
     * @see TransformerException
     */

    protected void warning(XmlProcessingError error) {
        if (logger == null) {
            logger = new StandardLogger();
        }
        String message = constructMessage(error, "", "Warning ");
        if (!warningsIssued.contains(message)) {
            logger.warning(message);
            warningCount++;
            if (warningCount > getMaximumNumberOfWarnings()) {
                logger.info("No more warnings will be displayed");
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
        return warningCount < getMaximumNumberOfWarnings();
    }

    /**
     * Receive notification of an error.
     *
     * <p>After calling this method to report a static error, the compiler will normally
     * continue to detect and report further errors, but the method can abort the
     * compilation by calling {@link StaticError#setFatal(String)}</p>
     *
     * @param error The error information.
     */

    protected void error(XmlProcessingError error) {
        if (errorCount++ > maximumNumberOfErrors) {
            error.setFatal("Too many errors reported");
        }
        if (logger == null) {
            logger = new StandardLogger();
        }
        String message;

        HostLanguage lang = error.getHostLanguage();
        String langText = "";
        if (lang != null) {
            switch (lang) {
                case XSLT:
                    break;
                case XQUERY:
                    langText = "in query ";
                    break;
                case XPATH:
                    langText = "in expression ";
                    break;
                case XML_SCHEMA:
                    langText = "in schema ";
                    break;
                case XSLT_PATTERN:
                    langText = "in pattern ";
                    break;

            }
        }

        String kind = "Error ";
        if (error.isTypeError()) {
            kind = "Type error ";
        } else if (error.isStaticError()) {
            kind = "Static error ";
        }

        message = constructMessage(error, langText, kind);

        logger.error(message);

        if (error instanceof XmlProcessingException) {
            XPathException exception = ((XmlProcessingException)error).getXPathException();
            XPathContext context = exception.getXPathContext();
            if (context != null && !(context instanceof EarlyEvaluationContext)) {
                outputStackTrace(logger, context);
            }
        }
    }

    /**
     * Construct an error or warning message.
     * <p>The default implementation outputs a two-line message: the first line is obtained
     * by calling {@link #constructFirstLine(XmlProcessingError, String, String)},
     * the second by calling {@link #constructSecondLine(XmlProcessingError)}; these are
     * concatenated with a newline and two spaces separating them.</p>
     * @param exception the exception originally reported to the ErrorListener
     * @param langText a string such as "in expression" or "in query" identifying the kind of
     *                 construct that is in error
     * @param kind the kind of error, for example "Syntax error", "Static error", "Type error"
     * @return the constructed message

     */

    public String constructMessage(XmlProcessingError exception, String langText, String kind) {
        return constructFirstLine(exception, langText, kind)
                + "\n  "
                + constructSecondLine(exception);
    }

    /**
     * Construct the first line of the error or warning message. This typically contains
     * information about the kind of error that occurred, and the location where it occurred
     * @param error the error originally reported to the ErrorReporter
     * @param langText a string such as "in expression" or "in query" identifying the kind of
     *                 construct that is in error
     * @param kind the kind of error, for example "Syntax error", "Static error", "Type error"
     * @return the constructed message
     */
    public String constructFirstLine(XmlProcessingError error, String langText, String kind) {
        Location locator = error.getLocation();
        if (locator instanceof AttributeLocation) {
            return kind + langText + getLocationMessageText(locator);
        } else if (locator instanceof XPathParser.NestedLocation) {

            XPathParser.NestedLocation nestedLoc = (XPathParser.NestedLocation)locator;
            Location outerLoc = nestedLoc.getContainingLocation();

            int line = nestedLoc.getLocalLineNumber();
            int column = nestedLoc.getColumnNumber();
            String lineInfo = line <= 0 ? "" : "on line " + line + ' ';
            String columnInfo = column < 0 ? "" : "at " + (line <= 0 ? "char " : "column ") + column + ' ';
            String nearBy = nestedLoc.getNearbyText();

            Expression failingExpression = null;
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

        } else {
            return kind + getLocationMessage(error);

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
     *     of indentation, followed by the result of calling {@link #getExpandedMessage(XmlProcessingError)}
     *     and then formatting the result using {@link #wordWrap(String)} and {@link #expandSpecialCharacters(CharSequence)}.</li>
     * </ul>
     * @param err the original reported exception
     * @return the string to be used as the second line of the error message
     */

    public String constructSecondLine(XmlProcessingError err) {
        return expandSpecialCharacters(wordWrap(getExpandedMessage(err))).toString();
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

    protected String getLocationMessage(XmlProcessingError err) {
        Location loc = err.getLocation();
        return getLocationMessageText(loc);
    }


    /**
     * Get a string containing the message for this exception and all contained exceptions.
     *
     * <p>The default implementation outputs the concatenation (with space-separation) of:</p>
     *
     * <ul>
     *     <li>The error code, formatted using {@link #formatErrorCode(XmlProcessingError)}</li>
     *     <li>The message associated with this exception, and with all nested exceptions,
     *     formatted using {@link #formatNestedMessages(XmlProcessingError, String)} </li>
     * </ul>
     *
     * @param err the exception containing the required information
     * @return a message that concatenates the message of this exception with its contained exceptions,
     *         also including information about the error code and location.
     */

    public String getExpandedMessage(XmlProcessingError err) {
        String message = formatErrorCode(err) + " " + err.getMessage();
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

    public String formatNestedMessages(XmlProcessingError err, String message) {
        if (err.getCause() == null) {
            return message;
        } else {
            StringBuilder sb = new StringBuilder(message);
            Throwable e = err.getCause();
            while (e != null) {
                if (!(e instanceof SAXParseException)) {
                    if (e instanceof RuntimeException) {
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        sb.append('\n').append(sw);
                    } else {
                        sb.append(". Caused by ").append(e.getClass().getName());
                    }
                } 
                String next = e.getMessage();
                if (next != null) {
                    sb.append(": ").append(next);
                }
                e = e.getCause();
            }
            return sb.toString();
        }
    }

    /**
     * Format the error code contained in the supplied exception object
     * @param err the exception object. The default implementation looks for an error
     *            code both in this object (if it is an {@link XPathException}) or in
     *            any wrapped {@code XPathException}. The default implementation ignores
     *            the namespace part of the error code (which is in general a QName) if it
     *            is the standard namespace {@link NamespaceConstant#ERR}; otherwise it displays
     *            the code in the format {@code prefix:localName}.
     * @return a string representation of the error code, followed by a trailing space if non-empty;
     * returns a zero-length string if outputErrorCodes is false.
     */

    public String formatErrorCode(XmlProcessingError err) {
        if (outputErrorCodes) {
            QName qCode = err.getErrorCode();
            if (qCode != null) {
                if (qCode.getNamespaceURI().equals(NamespaceConstant.ERR)) {
                    return qCode.getLocalName() + " ";
                } else {
                    return qCode.toString() + " ";
                }
            }
        }
        return "";
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

    /**
     * Generate a stack trace. This method is protected so it can be overridden in a subclass.
     *
     * @param out     the destination for the stack trace
     * @param context the context (which holds the information to be output)
     */

    protected void outputStackTrace(Logger out, XPathContext context) {
        printStackTrace(context, out, stackTraceDetail);
    }


}

