////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
 * An Outputter that swallows (discards) all input supplied to it
 */

public class SinkOutputter extends Outputter {

    @Override
    public void startDocument(int properties) throws XPathException {

    }

    @Override
    public void endDocument() throws XPathException {

    }

    @Override
    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {

    }

    @Override
    public void namespace(String prefix, NamespaceUri namespaceUri, int properties) throws XPathException {

    }

    @Override
    public void attribute(NodeName attName, SimpleType typeCode, String value, Location location, int properties) throws XPathException {

    }

    @Override
    public void endElement() throws XPathException {

    }

    @Override
    public void characters(UnicodeString chars, Location location, int properties) throws XPathException {

    }

    @Override
    public void processingInstruction(String name, UnicodeString data, Location location, int properties) throws XPathException {

    }

    @Override
    public void comment(UnicodeString content, Location location, int properties) throws XPathException {

    }
}

// Copyright (c) 2009-2023 Saxonica Limited
