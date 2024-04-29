////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;


import net.sf.saxon.expr.Expression;
import net.sf.saxon.lib.ErrorReporter;

/**
 * The <b>XmlProcessingError</b> class contains information about an error detected during
 * compilation or execution of a stylesheet, query, XPath expression, or schema
 * @since 10.0. In 11.0, the super-interface {@code StaticError} is dropped, as it had
 * become misleading.
 */

public interface XmlProcessingError  {


    HostLanguage getHostLanguage();

    /**
     * Ask whether this is a static error, defined as an error that can be detected during
     * static analysis of a stylesheet, query, schema, or XPath expression.
     * @return true if this is a static error
     */

    boolean isStaticError();

    /**
     * Ask whether this is a type error. Saxon reports type errors statically if it can establish
     * that a supplied value will never satisfy the required type
     * @return true if this is a type error
     */

    boolean isTypeError();

    /**
     * Get the error code, as a QName. This may be null if no error code has been assigned
     * @return QName
     */

    QName getErrorCode();

    /**
     * Get the error message associated with this error
     *
     * @return String the error message
     */

    String getMessage();

    /**
     * Get the location information associated with the error
     * @return the location of the error. The result is never null, though it may
     * be a location with little useful information.
     */

    Location getLocation();

    /**
     * Get The URI of the query or stylesheet module in which the error was detected (as a string)
     * May be null if the location of the error is unknown, or if the error is not localized
     * to a specific module, or if the module in question has no known URI (for example, if
     * it was supplied as an anonymous Stream)
     *
     * @return the URI identifying the location of the stylesheet module or query module
     */

     default String getModuleUri() {
         return getLocation().getSystemId();
     }

    /**
     * Get the Expression that failed, if known
     * @return the failing expression, or null
     */

    Expression getFailingExpression();

    /**
     * Ask whether this error is being reported as a warning condition.
     * If so, applications may ignore the condition, though the results may not be as intended.
     *
     * @return true if a condition is detected that is not an error according to the language
     * specification, but which may indicate that the query or stylesheet might behave in unexpected
     * ways
     */

    boolean isWarning();

    /**
     * Get the absolute XPath expression that identifies the node within its document
     * where the error occurred, if available
     *
     * @return a path expression identifying the location of the error within an XML document,
     * or null if the information is not available
     */

    String getPath();

    /**
     * Return an underlying exception. For example, if the static error was caused by failure
     * to retrieve another stylesheet or query module, this may contain the IO exception that
     * was reported; or if the XML parser was unable to parse a stylesheet module, it may
     * contain a SAXException reported by the parser.
     * @return the underlying exception if there was one, or null otherwise
     */

    Throwable getCause();

    /**
     * Return an XmlProcessingError containing the same information, but to be treated as
     * a warning condition
     * @return an XmlProcessingError to be treated as a warning
     */

    XmlProcessingError asWarning();

    /**
     * Indicate that this error is to be treated as fatal; that is, execution will be abandoned
     * after reporting this error. This method may be called by an {@link ErrorReporter}, for example
     * if the error is considered so severe that further processing is not worthwhile, or if
     * too many errors have been signalled. There is no absolute guarantee that setting this
     * property will cause execution to be abandoned. If a dynamic error is marked as fatal, it
     * will generally not be caught by any try/catch mechanism within the stylesheet or query.
     * @param message an error message giving the reason for the fatal error
     */

    void setTerminationMessage(String message);

    /**
     * Ask whether this error is to be treated as fatal, and return the associated message
     *
     * @return a non-null message if the error has been marked as a fatal error.
     */

    String getTerminationMessage();

    /**
     * Ask whether this static error has already been reported
     *
     * @return true if the error has already been reported
     */

    boolean isAlreadyReported();

    /**
     * Say whether this error has already been reported
     *
     * @param reported true if the error has been reported
     */

    void setAlreadyReported(boolean reported);

}

