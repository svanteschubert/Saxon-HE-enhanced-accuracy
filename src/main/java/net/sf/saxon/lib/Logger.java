////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import org.jetbrains.annotations.NotNull;

import javax.xml.transform.stream.StreamResult;
import java.io.Writer;

/**
 * Interface to diagnostic event logging mechanism.
 * A Logger can be registered at the level of the Saxon Configuration.
 * The default implementation for the Java platform writes all messages to System.err
 */
public abstract class Logger {

    public static final int INFO = 0;
    public static final int WARNING = 1;
    public static final int ERROR = 2;
    public static final int DISASTER = 3;

    private boolean unicodeAware = false;

    /**
     * Log a message with level {@link Logger#INFO}
     * @param message the message to be logged
     */
    public void info(String message) {
        println(message, INFO);
    }

    /**
     * Log a message with level {@link Logger#WARNING}
     * @param message the message to be logged
     */
    public void warning(String message) {
        println(message, WARNING);
    }

    /**
     * Log a message with level {@link Logger#ERROR}
     * @param message the message to be logged
     */
    public void error(String message) {
        println(message, ERROR);
    }

    /**
     * Log a message with level {@link Logger#DISASTER}
     * @param message the message to be logged
     */
    public void disaster(String message) {
        println(message, DISASTER);
    }

    /**
     * Log a message. To be implemented in a concrete subclass
     * @param message The message to be output
     * @param severity The severity level. One of {@link Logger#INFO}, {@link Logger#WARNING}, {@link Logger#ERROR},
     * {@link Logger#DISASTER}
     */

    public abstract void println(String message, int severity);

    /**
     * Close the logger, indicating that no further messages will be written
     * and that underlying streams should be closed, if they were created by the Logger
     * itself rather than by the user.
     */

    public void close() {}

    /**
     * Ask whether the Logger is capable of handling the full Unicode character repertoire.
     * By default this is false, because the default destination of console output is sensitive
     * to the configured locale and is often limited to legacy character sets.
     * @return true if the Logger can handle the full Unicode character repertoire. With the
     * default value (false), the {@link StandardErrorListener} will output special characters
     * using the notation C[xHHHH] where C is the character, and HHHH is its hexadecimal codepoint.
     */

    public boolean isUnicodeAware() {
        return unicodeAware;
    }

    /**
     * Say whether the Logger is capable of handling the full Unicode character repertoire.
     * By default this is false, because the default destination of console output is sensitive
     * to the configured locale and is often limited to legacy character sets.
     *
     * @param aware true if the Logger can handle the full Unicode character repertoire. With the
     * default value (false), the {@link StandardErrorListener} will output special characters
     * using the notation C[xHHHH] where C is the character, and HHHH is its hexadecimal codepoint.
     */

    public void setUnicodeAware(boolean aware) {
        unicodeAware = aware;
    }

    /**
     * Get a {@link Writer} whose effect is to send each line of written output as
     * a separate INFO message to this Logger
     * @return a suitable {@code Writer}
     */

    public Writer asWriter() {
        return new Writer() {
            StringBuilder builder = new StringBuilder();

            @Override
            public void write(@NotNull char[] cbuf, int off, int len) {
                for (int i = 0; i < len; i++) {
                    char ch = cbuf[off + i];
                    if (ch == '\n') {
                        println(builder.toString(), INFO);
                        builder.setLength(0);
                    } else {
                        builder.append(ch);
                    }
                }
            }

            @Override
            public void flush() {
                if (builder.length() > 0) {
                    println(builder.toString(), INFO);
                    builder.setLength(0);
                }
            }

            @Override
            public void close() {
                flush();
            }
        };
    }

    /**
     * Get a JAXP {@link StreamResult} object allowing serialized XML to be written to the
     * output destination of this Logger. The default implementation returns a
     * {@code StreamResult} wrapping a custom {@link Writer} that writes the supplied
     * text line by line using the {@link #println(String, int)} method.
     *
     * @return a StreamResult that serializes XML to this Logger
     */

    public StreamResult asStreamResult() {
        return new StreamResult(asWriter());
    }
}

