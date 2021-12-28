////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.Builder;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.StreamWriterToReceiver;
import net.sf.saxon.trans.XPathException;

/**
 * This class is an implementation of {@link javax.xml.stream.XMLStreamWriter}, allowing
 * a document to be constructed by means of a series of XMLStreamWriter method calls such
 * as writeStartElement(), writeAttribute(), writeCharacters(), and writeEndElement().
 * <p>The detailed way in which this class is packaged was carefully designed to ensure that
 * if the functionality is not used, the <code>DocumentBuilder</code> would still be usable under
 * JDK 1.5 (which does not include javax.xml.stream interfaces).</p>
 */

public class BuildingStreamWriterImpl extends StreamWriterToReceiver implements BuildingStreamWriter {

    Builder builder;

    public BuildingStreamWriterImpl(Receiver receiver, Builder builder) {
        super(receiver);
        this.builder = builder;
        builder.open();
    }

    /*@Nullable*/
    @Override
    public XdmNode getDocumentNode() throws SaxonApiException {
        try {
            builder.close();
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
        return new XdmNode(builder.getCurrentRoot());
    }
}
