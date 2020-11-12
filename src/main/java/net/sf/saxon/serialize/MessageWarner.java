////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XmlProcessingIncident;
import net.sf.saxon.trans.XPathException;

import java.io.StringWriter;


/**
 * MessageWarner is a user-selectable receiver for XSLT xsl:message output. It causes xsl:message output
 * to be notified to the warning() method of the JAXP ErrorListener, or to the error() method if
 * terminate="yes" is specified. This behaviour is specified in recent versions of the JAXP interface
 * specifications, but it is not the default behaviour, for backwards compatibility reasons.
 * <p>The text of the message that is sent to the ErrorListener is an XML serialization of the actual
 * message content.</p>
 */

public class MessageWarner extends XMLEmitter {

    private boolean abort = false;
    private String errorCode = null;

    @Override
    public void startDocument(int properties) throws XPathException {
        setWriter(new StringWriter());
        abort = ReceiverOption.contains(properties, ReceiverOption.TERMINATE);
        super.startDocument(properties);
    }

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (target.equals("error-code")) {
            errorCode = data.toString();
        } else {
            super.processingInstruction(target, data, locationId, properties);
        }
    }

    @Override
    public void endDocument() throws XPathException {
        ErrorReporter reporter = getPipelineConfiguration().getErrorReporter();
        XmlProcessingIncident de = new XmlProcessingIncident(getWriter().toString(), errorCode==null ? "XTMM9000" : errorCode);
        if (!abort) {
            de = de.asWarning();
        }
        reporter.report(de);
    }

    @Override
    public void close() {
        // do nothing
    }

}

