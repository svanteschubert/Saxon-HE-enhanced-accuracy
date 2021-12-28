////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.s9api.StaticError;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.trans.UncheckedXPathException;
import org.xml.sax.ErrorHandler;

import javax.xml.transform.ErrorListener;

/**
 * The ErrorReporter is a generic functional interface for reporting errors and
 * warnings.
 *
 * <p>The error or warning is reported in the form of an {@link XmlProcessingError};
 * unlike the JAXP {@link ErrorListener} and {@link ErrorHandler} it is possible
 * to report errors without expensively construction an exception object.</p>
 *
 * <p>The {@code ErrorReporter}'s {@code accept()} method does not throw any checked
 * exceptions. It may however, throw an {@link UncheckedXPathException} indicating
 * that processing is to be terminated without looking for further errors.</p>
 *
 * <p>A warning condition is notified using an <code>XmlProcessingError</code>
 * object whose {@code isWarning()} property is true.</p>
 *
 * @since 10.0
 */

@FunctionalInterface
public interface ErrorReporter {
    /**
     * Report an error. This method is called by Saxon when an error needs to be
     * reported to the calling application.
     *
     * <p>The application can safely ignore the error if {@link XmlProcessingError#isWarning()}
     * returns true.</p>
     *
     * <p>The application can indicate to Saxon that the error should be considered fatal
     * by calling {@link StaticError#setFatal(String)}. The precise effect of marking
     * an error as fatal is not defined, and may depend on the circumstances; in some cases
     * it may have no effect. If a dynamic error is marked as fatal then an attempt to
     * catch the error using a try/catch construct in XSLT or XQuery will generally be
     * unsuccessful.</p>
     *
     * @param error details of the error to be reported
     */

    void report(XmlProcessingError error);
}

