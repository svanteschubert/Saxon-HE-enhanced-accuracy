////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ValidationFailure;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

/**
 * Interface for reporting validation errors found during validation of an instance document
 * against a schema.
 */
public class InvalidityHandlerWrappingErrorListener implements InvalidityHandler {

    private ErrorListener errorListener;

    public InvalidityHandlerWrappingErrorListener(ErrorListener errorListener) {
        this.errorListener = errorListener;
    }

    /**
     * At the start of a validation episode, initialize the handler
     *
     * @param systemId optional; may be used to represent the destination of any report produced
     * @throws XPathException if initialization of the invalidity handler fails for any reason
     */
    @Override
    public void startReporting(String systemId) throws XPathException {
        // no action
    }

    @Override
    public void reportInvalidity(Invalidity failure) throws XPathException {
        try {
            errorListener.error((((ValidationFailure) failure).makeException()));
        } catch (TransformerException e) {
            throw XPathException.makeXPathException(e);
        }
    }

    public ErrorListener getErrorListener() {
        return errorListener;
    }

    /**
     * Get the value to be associated with a validation exception. May return null.
     * In the case of the InvalidityReportGenerator, this returns the XML document
     * containing the validation report
     *
     * @return a value (or null). This will be the value returned as the value of
     * the variable $err:value during try/catch processing
     */
    @Override
    public Sequence endReporting() {
        return null;
    }
}

