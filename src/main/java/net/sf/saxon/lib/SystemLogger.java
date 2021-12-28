////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import java.util.logging.Level;

/**
 * An implementation of Saxon's {@code Logger} interface that writes all messages through to
 * a supplied {@code java.util.logging.Logger}.
 */
public class SystemLogger extends Logger {

    private java.util.logging.Logger log;

    /**
     * Create a {@code Logger} that wraps a Java {@code java.util.logging.Logger}
     */

    public SystemLogger(java.util.logging.Logger log) {
        this.log = log;
    }

    private Level getLevel(int severity) {
        switch (severity) {
            case INFO:
                return Level.INFO;
            case WARNING:
                return Level.WARNING;
            case ERROR:
            case DISASTER:
            default:
                return Level.SEVERE;
        }
    }

    /**
     * Log a message.
     *
     * @param message  The message to be output
     * @param severity The severity level. One of {@link Logger#INFO}, {@link Logger#WARNING}, {@link Logger#ERROR},
     *                 {@link Logger#DISASTER}
     */
    @Override
    public void println(String message, int severity) {
        log.log(getLevel(severity), message);
    }

}

