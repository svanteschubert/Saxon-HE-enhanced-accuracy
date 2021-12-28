////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;
import java.util.Objects;

/**
 * A bridging class that allows errors in an XSLT Transformation, or in a Query,
 * to be reported to a JAXP {@link ErrorListener}.
 *
 * <p>Warning conditions that do not prevent execution of the stylesheet or query are
 * reported to the {@link ErrorListener#warning(TransformerException)} method.</p>
 *
 * <p>Error conditions that do prevent execution of the stylesheet or query are
 * reported to the {@link ErrorListener#fatalError(TransformerException)} method.</p>
 *
 * <p>The {@link ErrorListener#error(TransformerException)} method is never called,
 * because the concept of "recoverable errors" is no longer present in the XSLT
 * specification.</p>
 *
 * @since 10.0
 */

public class ErrorReporterToListener implements ErrorReporter {

    private ErrorListener listener;

    /**
     * Create an error reporter that wraps a JAXP {@code ErrorListener}
     * @param listener the wrapped {@code ErrorListener}. Must not be null.
     */
    public ErrorReporterToListener(ErrorListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    /**
     * Get the JAXP {@code ErrorListener} wrapped by this error reporter
     * @return the wrapped {@code ErrorListener}
     */

    public ErrorListener getErrorListener() {
        return listener;
    }

    /**
     * Receive notification of a error or warning
     * @param error information about the error or warning
     */

    @Override
    public void report(XmlProcessingError error) {
        if (!error.isAlreadyReported()) {
            try {
                XPathException err = XPathException.fromXmlProcessingError(error);
                if (error.isWarning()) {
                    listener.warning(err);
                } else {
                    listener.fatalError(err);
                }
                error.setAlreadyReported(true);
            } catch (TransformerException e) {
                error.setFatal(e.getMessage());
            }
        }
    }
}
