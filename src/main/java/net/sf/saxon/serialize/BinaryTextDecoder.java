////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.value.Base64BinaryValue;
import net.sf.saxon.value.HexBinaryValue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * This class generates decodes processing instructions in text output that represent text encoded
 * in base64 binary or hexBinary
 *
 * @author Michael H. Kay
 */

public class BinaryTextDecoder extends ProxyReceiver {

    // TODO: is this tested or documented?

    String outputEncoding = "utf8";

    public BinaryTextDecoder(Receiver next, Properties details) throws XPathException {
        super(next);
        setOutputProperties(details);
    }

    /**
     * Set output properties
     *
     * @param details the output serialization properties
     */

    public void setOutputProperties(Properties details) throws XPathException {
        outputEncoding = details.getProperty("encoding", "utf8");
    }


    /**
     * Output a processing instruction. <br>
     * Does nothing with this output method, unless the saxon:recognize-binary option is set, and this is the
     * processing instructions hex or b64. The name of the processing instruction may be followed by an encoding
     * name, for example b64.ascii indicates base64-encoded ASCII strings; if no encoding is present, the encoding
     * of the output method is assumed.
     */

    @Override
    public void processingInstruction(String name, /*@NotNull*/ CharSequence value, Location locationId, int properties)
            throws XPathException {
        String encoding;
        byte[] bytes = null;
        int dot = name.indexOf('.');
        if (dot >= 0 && dot != name.length() - 1) {
            encoding = name.substring(dot + 1);
            name = name.substring(0, dot);
        } else {
            encoding = outputEncoding;
        }
        if (name.equals("hex")) {
            bytes = new HexBinaryValue(value).getBinaryValue();
        } else if (name.equals("b64")) {
            bytes = new Base64BinaryValue(value).getBinaryValue();
        }
        if (bytes != null) {
            try {
                ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
                InputStreamReader reader = new InputStreamReader(stream, encoding);
                char[] array = new char[bytes.length];
                int used = reader.read(array, 0, array.length);
                nextReceiver.characters(new CharSlice(array, 0, used), locationId, properties);
            } catch (IOException e) {
                throw new XPathException(
                        "Text output method: failed to decode binary data " + Err.wrap(value.toString(), Err.VALUE));
            }
        }
    }


}

