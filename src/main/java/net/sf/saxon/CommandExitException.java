// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

/**
 * An exception thrown by the {@link net.sf.saxon.Transform} and {@link net.sf.saxon.Query}
 * CLI classes when the command needs to exit and terminate the process immediately.
 *
 * @since 12.10
 */
public class CommandExitException extends RuntimeException {
    private final int code;

    /**
     *
     * @return the status code that should be passed to {@link System#exit}
     */
    public int getCode() {
        return code;
    }

    /**
     * Standard Exception constructor, defaults {@code code} to {@code 1}
     */
    public CommandExitException() {
        super();
        this.code = 1;
    }

    /**
     *
     * @param code The status code that should be passed to {@link System#exit}
     */
    public CommandExitException(int code) {
        super();
        this.code = code;
    }

    /**
     *
     * @param code The status code that should be passed to {@link System#exit}
     * @param message The error message
     */
    public CommandExitException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Standard Exception constructor, defaults {@code code} to {@code 1}
     * @param message the detail message. The detail message is saved for later retrieval by the
     *  {@link Throwable#getMessage} method.
     */
    public CommandExitException(String message) {
        super(message);
        this.code = 1;
    }

    /**
     * Standard Exception constructor, defaults {@code code} to {@code 1}
     *
     * @param message the detail message. The detail message is saved for later
     *                retrieval by the {@link Throwable#getMessage} method.
     * @param cause the cause (which is saved for later retrieval by the
     *              {@link Throwable#getCause} method). (A null value is
     *              permitted, and indicates that the cause is nonexistent
     *              or unknown.)
     */
    public CommandExitException(String message, Throwable cause) {
        super(message, cause);
        this.code = 1;
    }

    /**
     * Standard Exception constructor, defaults {@code code} to {@code 1}
     *
     * @param message the detail message. The detail message is saved for later
     *                retrieval by the {@link Throwable#getMessage} method.
     * @param cause the cause (which is saved for later retrieval by the
     *              {@link Throwable#getCause} method). (A null value is
     *              permitted, and indicates that the cause is nonexistent
     *              or unknown.)
     * @param enableSuppression whether or not suppression is enabled or disabled
     * @param writableStackTrace whether or not the stack trace should be writable
     */
    public CommandExitException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.code = 1;
    }
}
