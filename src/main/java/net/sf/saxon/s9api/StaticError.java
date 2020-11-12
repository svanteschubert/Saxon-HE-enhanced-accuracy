////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;


import net.sf.saxon.lib.ErrorReporter;

import java.util.List;

/**
 * The <b>StaticError</b> interface is retained in Saxon 10.0 as a marker interface so that
 * the methods {@link XsltCompiler#setErrorList(List)} and {@link XQueryCompiler#setErrorList(List)}
 * continue to function.
 *
 * <p>The name is misleading, because all errors including dynamic errors implement this interface.</p>
 */

public interface StaticError {


    /**
     * The error code, as a QName. May be null if no error code has been assigned
     *
     * @return QName
     */
    QName getErrorCode();
    /**
     * Return the error message  associated with this error
     *
     * @return String
     */
    String getMessage();

    /**
     * Get the location information associated with the error
     *
     * @return the location of the error. The result is never null, though it may
     * be a location with little useful information.
     */

    Location getLocation();

    /**
     * The URI of the query or stylesheet module in which the error was detected (as a string)
     * May be null if the location of the error is unknown, or if the error is not localized
     * to a specific module, or if the module in question has no known URI (for example, if
     * it was supplied as an anonymous Stream)
     *
     * @return String
     */
    default String getModuleUri() {
        return getLocation().getSystemId();
    }
    /**
     * The coloumn number locating the error within a query or stylesheet module
     *
     * @return int
     */
    default int getColumnNumber() {
        return getLocation().getColumnNumber();
    }

    /**
     * The line number locating the error within a query or stylesheet module
     *
     * @return int
     */
    default int getLineNumber() {
        return getLocation().getLineNumber();
    }

    /**
     * Get a name identifying the kind of instruction, in terms meaningful to a user. This method
     * is not used in the case where the instruction name code is a standard name (&lt;1024).
     *
     * @return a name identifying the kind of instruction, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the instruction.
     */
    default String getInstructionName() {
        return null;
    }
    /**
     * Indicate whether this error is being reported as a warning condition.
     * If so, applications may ignore the condition, though the results may not be as intended.
     *
     * @return boolean
     */
    boolean isWarning();

    /**
     * Indicate whether this condition is a type error.
     *
     * @return boolean
     */
    boolean isTypeError();
    /**
     * Get the absolute XPath expression that identifies the node within its document
     * where the error occurred, if available
     *
     * @return String - a path expression
     */
    default String getPath() {
        return null;
    }

    /**
     * Indicate that this error is to be treated as fatal; that is, execution will be abandoned
     * after reporting this error. This method may be called by an {@link ErrorReporter}, for example
     * if the error is considered so severe that further processing is not worthwhile, or if
     * too many errors have been signalled. There is no absolute guarantee that setting this
     * property will cause execution to be abandoned. If a dynamic error is marked as fatal, it
     * will generally not be caught by any try/catch mechanism within the stylesheet or query.
     */

    void setFatal(String message);

    /**
     * Ask whether this error is to be treated as fatal, and if so, return the relevant messsage
     * @return a non-null message if the error has been marked as a fatal error.
     */

    String getFatalErrorMessage();

}

