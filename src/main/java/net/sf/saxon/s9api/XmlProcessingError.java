////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;


import java.util.List;

/**
 * The <b>XmlProcessingError</b> class contains information about an error detected during
 * compilation or execution of a stylesheet, query, XPath expression, or schema
 *
 * <p>The interface extends {@link StaticError} so that
 *  the methods {@link XsltCompiler#setErrorList(List)} and {@link XQueryCompiler#setErrorList(List)}
 *  continue to function. It is <b>not</b> the case, however, that every {@code XmlProcessingError}
 *  is a static error.</p>
 */

public interface XmlProcessingError extends StaticError {


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

    @Override
    boolean isTypeError();

    /**
     * Get the error code, as a QName. This may be null if no error code has been assigned
     * @return QName
     */

    @Override
    QName getErrorCode();

    /**
     * Get the error message associated with this error
     *
     * @return String the error message
     */

    @Override
    String getMessage();

    /**
     * Get the location information associated with the error
     * @return the location of the error. The result is never null, though it may
     * be a location with little useful information.
     */

    @Override
    Location getLocation();

    /**
     * Get The URI of the query or stylesheet module in which the error was detected (as a string)
     * May be null if the location of the error is unknown, or if the error is not localized
     * to a specific module, or if the module in question has no known URI (for example, if
     * it was supplied as an anonymous Stream)
     *
     * @return the URI identifying the location of the stylesheet module or query module
     */

     @Override
     default String getModuleUri() {
         return getLocation().getSystemId();
     }

    /**
     * Ask whether this error is being reported as a warning condition.
     * If so, applications may ignore the condition, though the results may not be as intended.
     *
     * @return true if a condition is detected that is not an error according to the language
     * specification, but which may indicate that the query or stylesheet might behave in unexpected
     * ways
     */

    @Override
    boolean isWarning();

    /**
     * Get the absolute XPath expression that identifies the node within its document
     * where the error occurred, if available
     *
     * @return a path expression identifying the location of the error within an XML document,
     * or null if the information is not available
     */

    @Override
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
     */

    XmlProcessingError asWarning();

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

