////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.OutputKeys;
import java.util.Properties;


/**
 * MessageEmitter is the default Receiver for xsl:message output.
 * It is the same as XMLEmitter except that (a) it adds an extra newline at the end of the message, and
 * (b) it recognizes a processing-instruction with name "error-code" specially; this is assumed to contain
 * the error-code requested on the {@code xsl:message} instruction. These changes can be overridden
 * in a user-supplied subclass.
 */

public class MessageEmitter extends XMLEmitter {

    public MessageEmitter() {

    }

    @Override
    public void setPipelineConfiguration(PipelineConfiguration pipelineConfiguration) {
        super.setPipelineConfiguration(pipelineConfiguration);
        if (writer == null && outputStream == null) {
            try {
                setWriter(getConfiguration().getLogger().asWriter());
            } catch (XPathException e) {
                throw new AssertionError(e);
            }
        }
        try {
            Properties props = new Properties();
            props.setProperty(OutputKeys.METHOD, "xml");
            props.setProperty(OutputKeys.INDENT, "yes");
            props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            setOutputProperties(props);
        } catch (XPathException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (!suppressProcessingInstruction(target, data, locationId, properties)) {
            super.processingInstruction(target, data, locationId, properties);
        }
    }

    /**
     * Method to decide whether a processing instruction in the message should be suppressed. The default
     * implementation suppresses a processing instruction whose target is "error-code"; this processing
     * instruction is inserted into the message; its data part is an EQName containing the error code.
     * @param target the processing instruction target (that is, name)
     * @param data the data part of the processing instruction
     * @param locationId the location, which in the case of the error-code processing instruction, holds
     *                   the location of the originating xsl:message instruction
     * @param properties currently 0.
     * @return true if the processing instruction is to be suppressed from the displayed message.
     */

    protected boolean suppressProcessingInstruction(String target, CharSequence data, Location locationId, int properties) {
        return target.equals("error-code");
    }

    @Override
    public void endDocument() throws XPathException {
        try {
            if (writer != null) {
                writer.write('\n');
                writer.flush();
            }
        } catch (java.io.IOException err) {
            throw new XPathException(err);
        }
        super.endDocument();
    }

    @Override
    public void close() throws XPathException {
        try {
            if (writer != null) {
                writer.flush();
            }
        } catch (java.io.IOException err) {
            throw new XPathException(err);
        }
        super.close();
    }

}

