////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.str.UnicodeWriter;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Result;

/**
 * An UnicodeWriterResult encapsulates a UnicodeWriter to act as the destination for a transformation
 * or serialization
 */
public class UnicodeWriterResult implements Result {

    private final UnicodeWriter unicodeWriter;
    private String systemId;

    public UnicodeWriterResult(UnicodeWriter unicodeWriter, String systemId) throws XPathException {
        this.systemId = systemId;
        this.unicodeWriter = unicodeWriter;
    }

    @Override
    public String getSystemId() {
        return systemId;
    }

    @Override
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public UnicodeWriter getUnicodeWriter() {
        return unicodeWriter;
    }

    public String toString() {
        return super.toString();
    }
}

