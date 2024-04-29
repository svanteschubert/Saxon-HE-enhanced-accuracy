////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.event.ReceiverWithOutputProperties;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.serialize.charcode.CharacterSet;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.serialize.charcode.UTF8CharacterSet;
import net.sf.saxon.str.UnicodeWriter;
import net.sf.saxon.trans.XPathException;

import java.io.IOException;
import java.util.Properties;


/**
 * Emitter: This abstract class defines methods that must be implemented by
 * components that format SAXON output. There is one emitter for XML,
 * one for HTML, and so on. Additional methods are concerned with
 * setting options and providing a Writer.
 * <p>The interface is deliberately designed to be as close as possible to the
 * standard SAX2 ContentHandler interface, however, it allows additional
 * information to be made available.</p>
 * <p>An Emitter is a Receiver, specifically it is a Receiver that can direct output
 * to a Writer or OutputStream, using serialization properties defined in a Properties
 * object.</p>
 * <p>The Emitter (from 11.0 onwards) writes to a UnicodeWriter, which may itself
 * bridge to a Writer or OutputStream.</p>
 */

public abstract class Emitter extends SequenceReceiver implements ReceiverWithOutputProperties {
    protected UnicodeWriter writer;
    protected Properties outputProperties;
    protected CharacterSet characterSet;
    protected boolean allCharactersEncodable = false;
    private boolean mustClose = false;

    public Emitter() {
        super(null);
    }

    /**
     * Set output properties
     *
     * @param details the output serialization properties
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs finding the encoding property
     */

    public void setOutputProperties(Properties details) throws XPathException {
        if (characterSet == null) {
            characterSet = getConfiguration().getCharacterSetFactory().getCharacterSet(details);
            allCharactersEncodable = (characterSet instanceof UTF8CharacterSet ||
                    characterSet instanceof UTF16CharacterSet);
        }
        outputProperties = details;
    }

    /**
     * Get the output properties
     *
     * @return the output serialization properties. The returned value will be null if setOutputProperties()
     *         has not been called
     */

    @Override
    public Properties getOutputProperties() {
        return outputProperties;
    }

    /**
     * Set the output destination to which the emitter's output will be written
     * @param unicodeWriter the output destination
     */
    public void setUnicodeWriter(UnicodeWriter unicodeWriter) {
        this.writer = unicodeWriter;
    }

    /**
     * Set a flag indicating that the unicode writer must be closed after use. This will
     * generally be true if the writer was created by Saxon, and false if it was supplied
     * by the user
     * @param mustClose true if the unicode writer is to be closed after use.
     */
    public void setMustClose(boolean mustClose) {
        this.mustClose = mustClose;
    }

    /**
     * Set unparsed entity URI. Needed to satisfy the Receiver interface, but not used,
     * because unparsed entities can occur only in input documents, not in output documents.
     *
     * @param name     the entity name
     * @param uri      the entity system ID
     * @param publicId the entity public ID
     */

    @Override
    public void setUnparsedEntity(String name, String uri, String publicId) throws XPathException {
    }

    /**
     * Notify the end of the event stream
     */

    @Override
    public void close() throws XPathException {
        if (mustClose && writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                throw new XPathException("Failed to close output stream");
            }
        }
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation (or conversely, it may
     *         avoid stripping unwanted type annotations)
     */
    @Override
    public boolean usesTypeAnnotations() {
        return false;
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     */

    @Override
    public void append(/*@Nullable*/ Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (item instanceof NodeInfo) {
            decompose(item, locationId, copyNamespaces);
        } else {
            characters(item.getUnicodeStringValue(), locationId, ReceiverOption.NONE);
        }
    }

}

