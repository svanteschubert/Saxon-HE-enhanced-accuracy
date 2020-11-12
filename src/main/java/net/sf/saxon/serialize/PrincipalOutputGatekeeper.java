////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.Destination;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.type.SchemaType;

/**
 * This class is added to the output pipeline for the principal result destination for a transformation
 * if the transformation uses xsl:result-document instructions. It is there to deal with the possibility
 * that an xsl:result-document instruction will use the same output URI (or no output URI) in which case
 * it is defined to write to the principal output destination, but potentially with different serialization
 * parameters.
 * <p>If actual output is produced to the principal result destination, then this {@code PrincipalOutputGatekeeper}
 * will be marked as being "used by primary", which will cause any attempt to open a secondary result destination on the
 * same URI (either explicitly, or by omitting the {@code href} attribute, or by setting {@code href} to
 * a zero length string) as an error. Apart from this marker, the {@code PrincipalOutputGatekeeper} acts as
 * a pass-through filter for the output events.</p>
 * <p>If a secondary result destination is opened using the principal output URI (either explicitly or implicitly),
 * then the {@code PrincipalOutputGatekeeper} is marked as being "used by secondary", which will cause any
 * attempts to write events to the {@code PrincipalOutputGatekeeper} to fail. Secondary result output is not
 * actually directed through the gatekeeper; the gatekeeper is merely notified of its existence.</p>
 */

public class PrincipalOutputGatekeeper extends ProxyReceiver {

    private XsltController controller;
    private boolean usedAsPrimaryResult = false;
    private boolean usedAsSecondaryResult = false;
    private boolean open = false;
    private boolean closed = false;


    public PrincipalOutputGatekeeper(XsltController controller, Receiver next) {
        super(next);
        this.controller = controller;
    }

    @Override
    public void open() throws XPathException {
        if (closed) {
            String uri = getSystemId().equals(XsltController.ANONYMOUS_PRINCIPAL_OUTPUT_URI) ? "(no URI supplied)" : getSystemId();
            XPathException err = new XPathException(
                        "Cannot write more than one result document to the principal output destination: " + uri);
            err.setErrorCode("XTDE1490");
            throw err;
        }
        super.open();
        open = true;
    }

    @Override
    public synchronized void startDocument(int properties) throws XPathException {
        if (!open) {
            open();
        }
        //checkNotClosed();
        nextReceiver.startDocument(properties);
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        useAsPrimary();
        nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
    }

    @Override
    public synchronized void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        useAsPrimary();
        nextReceiver.characters(chars, locationId, properties);
    }

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        useAsPrimary();
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        useAsPrimary();
        nextReceiver.comment(chars, locationId, properties);
    }

    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        useAsPrimary();
        nextReceiver.append(item, locationId, copyNamespaces);
    }

    private synchronized void useAsPrimary() throws XPathException {
        if (closed) {
            XPathException err = new XPathException(
                    "Cannot write to the principal output destination as it has already been closed: " + identifySystemId());
            err.setErrorCode("XTDE1490");
            throw err;
        }
        if (usedAsSecondaryResult) {
            XPathException err = new XPathException(
                    "Cannot write to the principal output destination as it has already been used by xsl:result-document: " + identifySystemId());
            err.setErrorCode("XTDE1490");
            throw err;
        }
        usedAsPrimaryResult = true;
    }

    public synchronized void useAsSecondary() throws XPathException {
        if (usedAsPrimaryResult) {
            XPathException err = new XPathException(
                    "Cannot use xsl:result-document to write to a destination already used for the principal output: " + identifySystemId());
            err.setErrorCode("XTDE1490");
            throw err;
        }
        if (usedAsSecondaryResult) {
            XPathException err = new XPathException(
                    "Cannot write more than one xsl:result-document to the principal output destination: " + identifySystemId());
            err.setErrorCode("XTDE1490");
            throw err;
        }
        usedAsSecondaryResult = true;
    }

    public Receiver makeReceiver(SerializationProperties params) {
        try {
            Destination dest = controller.getPrincipalDestination();
            if (dest != null) {
                return dest.getReceiver(controller.makePipelineConfiguration(), params);
            }
        } catch (SaxonApiException e) {
            return null;
        }
        return null;
    }

    private String identifySystemId() {
        String uri = getSystemId();
        return uri==null ? "(no URI supplied)" : uri;
    }


    @Override
    public void close() throws XPathException {
        closed = true;
        if (usedAsPrimaryResult) {
            nextReceiver.close();
        }
    }

}

