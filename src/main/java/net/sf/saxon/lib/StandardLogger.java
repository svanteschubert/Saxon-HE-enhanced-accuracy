////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.DayTimeDurationValue;
import net.sf.saxon.value.NumericValue;

import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.math.BigDecimal;

/**
 * The default Logger used by Saxon on the Java platform. All messages are written by default
 * to System.err. The logger can be configured by setting a different output destination, and
 * by setting a minimum threshold for the severity of messages to be output.
 */
public class StandardLogger extends Logger {

    private PrintWriter writer = new PrintWriter(System.err);
    private int threshold = Logger.INFO;
    private boolean mustClose = false;

    /**
     * Create a Logger that wraps the System.err output stream
     */

    public StandardLogger() {
    }

    /**
     * Create a Logger that wraps the specified stream. Closing the Logger will
     * not close the underlying stream; this remains the caller's responsibility
     *
     * @param stream the stream to which the Logger's output should be written
     */

    public StandardLogger(PrintStream stream) {
        setPrintStream(stream);
    }

    /**
     * Create a Logger that wraps the specified writer. Closing the Logger will
     * not close the underlying stream; this remains the caller's responsibility
     *
     * @param writer the writer to which the Logger's output should be written
     */

    public StandardLogger(Writer writer) {
        setPrintWriter(new PrintWriter(writer));
    }

    /**
     * Create a Logger that writes to a specified file
     *
     * @param fileName the file to which output should be written. When the logger is closed,
     *                 output will be flushed to the file.
     * @throws FileNotFoundException if the file is not accessible
     */

    public StandardLogger(File fileName) throws FileNotFoundException {
        setPrintStream(new PrintStream(fileName));
        mustClose = true;
    }


    /**
     * Set the output destination for messages
     *
     * @param stream the stream to which messages will be written. Defaults to System.err. The caller
     *               is responsible for closing the stream after use (it will not be closed by the
     *               close() method on the Logger)
     */

    public void setPrintStream(PrintStream stream) {
        this.writer = new PrintWriter(stream);
    }

    /**
     * Set the output destination for messages
     *
     * @param writer the stream to which messages will be written. Defaults to System.err. The caller
     *               is responsible for closing the stream after use (it will not be closed by the
     *               close() method on the Logger)
     */

    public void setPrintWriter(PrintWriter writer) {
        this.writer = writer;
    }

    /**
     * Get the output destination used for messages
     *
     * @return the stream to which messages are written
     */

    public PrintWriter getPrintWriter() {
        return writer;
    }

    /**
     * Set the minimum threshold for the severity of messages to be output. Defaults to
     * {@link Logger#INFO}. Messages whose severity is below this threshold will be ignored
     *
     * @param threshold the minimum severity of messages to be output.
     */

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    /**
     * Get the minimum threshold for the severity of messages to be output. Defaults to
     * {@link Logger#INFO}. Messages whose severity is below this threshold will be ignored
     *
     * @return the minimum severity of messages to be output.
     */

    public int getThreshold() {
        return threshold;
    }

    /**
     * Get a JAXP Result object allowing serialized XML to be written to
     * the output destination of this Logger
     *
     * @return a Result that serializes XML to this Logger
     */
    @Override
    public StreamResult asStreamResult() {
        return new StreamResult(writer);
    }


    /**
     * Output a message with a specified severity.
     *
     * @param message  The message to be output
     * @param severity The severity: one of {@link Logger#INFO}, {@link Logger#WARNING}, {@link Logger#ERROR},
     *                 {@link Logger#DISASTER}
     */

    @Override
    public void println(String message, int severity) {
        if (severity >= threshold) {
            writer.write(message + "\n");
            writer.flush();
        }
    }

    /**
     * Close the logger, indicating that no further messages will be written
     */
    @Override
    public void close() {
        if (mustClose) {
            writer.close();
        }
    }
}

