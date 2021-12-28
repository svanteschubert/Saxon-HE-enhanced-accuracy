////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.BuilderMonitor;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;

/**
 * Monitor construction of a document tree. This allows a marker to be set during tree construction, in such a way
 * that the node corresponding to the marker can be retrieved at the end of tree construction. This is used in the
 * implementation of the XSLT 3.0 snapshot function.
 */
public class LinkedBuilderMonitor extends BuilderMonitor {

    private LinkedTreeBuilder builder;
    private int mark = -1;
    /*@Nullable*/ private NodeInfo markedNode;

    public LinkedBuilderMonitor(/*@NotNull*/ LinkedTreeBuilder builder) {
        super(builder);
        this.builder = builder;
    }

    @Override
    public void markNextNode(int nodeKind) {
        mark = nodeKind;
    }

    @Override
    public void startDocument(int properties) throws XPathException {
        super.startDocument(properties);
        if (mark == Type.DOCUMENT) {
            markedNode = builder.getCurrentParentNode();
        }
        mark = -1;
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type, AttributeMap attributes, NamespaceMap namespaces, Location location, int properties) throws XPathException {
        super.startElement(elemName, type, attributes, namespaces, location, properties);
        if (mark == Type.ELEMENT) {
            markedNode = builder.getCurrentParentNode();
        }
        mark = -1;
    }

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        super.characters(chars, locationId, properties);
        if (mark == Type.TEXT) {
            markedNode = builder.getCurrentLeafNode();
        }
        mark = -1;
    }

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        super.comment(chars, locationId, properties);
        if (mark == Type.COMMENT) {
            markedNode = builder.getCurrentLeafNode();
        }
        mark = -1;
    }

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        super.processingInstruction(target, data, locationId, properties);
        if (mark == Type.PROCESSING_INSTRUCTION) {
            markedNode = builder.getCurrentLeafNode();
        }
        mark = -1;
    }

    /*@Nullable*/
    @Override
    public NodeInfo getMarkedNode() {
        return markedNode;
    }
}

