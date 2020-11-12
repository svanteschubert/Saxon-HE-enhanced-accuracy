package net.sf.saxon.event;

import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
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
    public void namespace(String prefix, String namespaceUri, int properties) throws XPathException {

    }

    @Override
    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location location, int properties) throws XPathException {

    }

    @Override
    public void endElement() throws XPathException {

    }

    @Override
    public void characters(CharSequence chars, Location location, int properties) throws XPathException {

    }

    @Override
    public void processingInstruction(String name, CharSequence data, Location location, int properties) throws XPathException {

    }

    @Override
    public void comment(CharSequence content, Location location, int properties) throws XPathException {

    }
}

// Copyright (c) 2009-2020 Saxonica Limited
