////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.s9api.Message;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.serialize.UnicodeWriterResult;
import net.sf.saxon.str.UnicodeWriter;
import net.sf.saxon.str.UnicodeWriterToWriter;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.OutputKeys;
import java.io.Writer;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * This is the default implementation of the new Saxon 11 interface
 * for user-defined handling of {@code xsl:message} and {@code xsl:assert}
 * output.
 */

public class StandardMessageHandler implements Consumer<Message> {

    private final Configuration config;
    private UnicodeWriter writer;
    private final SerializationProperties serializationProperties;

    public StandardMessageHandler(Configuration config) {
        this.config = config;
        serializationProperties = getSerializationProperties();
    }

    /**
     * Get the serialization properties to be used. This is a public method so that
     * it can be overridden in a user-written subclass.
     * @return the serialization properties. By default the properties returned
     * are method="yes", indent="yes", omit-xml-declaration="yes".
     */

    public SerializationProperties getSerializationProperties() {
        Properties props = new Properties();
        props.setProperty(OutputKeys.METHOD, "xml");
        props.setProperty(OutputKeys.INDENT, "yes");
        props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        return new SerializationProperties(props);
    }

    /**
     * Set the destination to which serialized messages will be written
     * @param writer the destination for serialized messages
     */

    public void setUnicodeWriter(UnicodeWriter writer) {
        this.writer = writer;
    }

    /**
     * Get the destination to which serialized messages will be written
     * @return the destination for serialized messages
     */

    public UnicodeWriter getUnicodeWriter() {
        return this.writer;
    }

    public synchronized void accept(Message message) {
        try {
            XdmNode node = message.getContent();
            assert node.getNodeKind() == XdmNodeKind.DOCUMENT;
            if (writer == null) {
                Writer w = config.getLogger().asWriter();
                writer = new UnicodeWriterToWriter(w);
            }
            UnicodeWriterResult result = new UnicodeWriterResult(writer, null);
            Receiver out = config.getSerializerFactory().getReceiver(result, serializationProperties);
            out.open();
            out.append(node.getUnderlyingNode());
            out.close();
        } catch (XPathException e) {
            // No action if xsl:message fails
        }
    }
}


