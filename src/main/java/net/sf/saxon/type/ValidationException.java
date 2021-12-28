////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.AbsolutePath;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;

/**
 * This exception indicates a failure when validating an instance against a type
 * defined in a schema. It may also be used when validating against a built-in type.
 */

public class ValidationException extends XPathException {

    private ValidationFailure failure;

    /**
     * Creates a new ValidationException with the given nested
     * exception.
     *
     * @param exception the nested exception
     */
    public ValidationException(Exception exception) {
        super(exception);
        /*setIsTypeError(true);*/
    }

    /**
     * Creates a new ValidationException with the given message
     * and nested exception.
     *
     * @param message   the detail message for this exception
     * @param exception the nested exception
     */
    public ValidationException(String message, Exception exception) {
        super(message, exception);
        /*setIsTypeError(true);*/
    }

    /**
     * Create a new ValidationException from a message and a Locator.
     *
     * @param message The error or warning message.
     * @param locator The locator object for the error or warning.
     */
    public ValidationException(String message, Location locator) {
        super(message, null, locator);
        /*setIsTypeError(true);*/
        // With Xerces, it's enough to store the locator as part of the exception. But with Crimson,
        // the locator is destroyed when the parse terminates, which means the location information
        // will not be available in the final error message. So we copy the location information now,
        // as part of the exception object itself.
        //setSourceLocator(locator);
    }

    /**
     * Create a new ValidationException that wraps a ValidationFailure
     * @param failure the ValidationFailure to be wrapped
     */

    public ValidationException(ValidationFailure failure) {
        super(failure.getMessage(), failure.getErrorCode(), failure.getLocator());
        this.failure = failure;
    }

    /**
     * Returns the detail message string of this throwable.
     *
     * @return the detail message string of this {@code Throwable} instance
     * (which may be {@code null}).
     */
    @Override
    public String getMessage() {
        // The message held in the ValidationFailure is sometimes updated, so we use that one in preference.
        // The message in the exception can't be updated, it can only be set from the constructor.
        if (failure != null) {
            return failure.getMessage();
        } else {
            return super.getMessage();
        }
    }

    /**
     * Get a ValidationFailure object containing information from this ValidationException
     * @return a ValidationFailure object
     */

    public ValidationFailure getValidationFailure() {
        if (failure != null) {
            return failure;
        } else {
            ValidationFailure failure = new ValidationFailure(getMessage());
            failure.setErrorCodeQName(getErrorCodeQName());
            failure.setLocator(getLocator());
            return failure;
        }
    }

    /**
     * Returns the String representation of this Exception
     *
     * @return the String representation of this Exception
     */
    public String toString() {
        StringBuilder sb = new StringBuilder("ValidationException: ");
        String message = getMessage();
        if (message != null) {
            sb.append(message);
        }
        return sb.toString();
    }

    /*@Nullable*/
    public NodeInfo getNode() {
        if (failure != null) {
            return failure.getInvalidNode();
        } else {
            return null;
        }
    }

    /**
     * Get the location of the error in terms of a path expressed as a string
     *
     * @return the location, as a path. The result format is similar to that of the fn:path() function
     */

    /*@Nullable*/
    public String getPath() {
        AbsolutePath ap = getAbsolutePath();
        if (ap == null) {
            NodeInfo node = getNode();
            if (node != null) {
                return Navigator.getPath(node);
            } else {
                return null;
            }
        } else {
            return ap.getPathUsingAbbreviatedUris();
        }
    }

    /**
     * Get the location of the error as a structured path object
     *
     * @return the location, as a structured path object indicating the position of the error within the containing document
     */

    public AbsolutePath getAbsolutePath() {
        if (failure != null) {
            return failure.getPath();
        } else {
            return null;
        }
    }

}

