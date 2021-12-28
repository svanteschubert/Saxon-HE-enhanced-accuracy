////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.event.BuilderMonitor;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;

/**
 * Monitor construction of a TinyTree. This allows a marker to be set during tree construction, in such a way
 * that the node corresponding to the marker can be retrieved at the end of tree construction. This is used in the
 * implementation of the XSLT 3.0 snapshot function.
 */
public class TinyBuilderMonitor extends BuilderMonitor {

    private TinyBuilder builder;
    private int mark = -1;
    private int markedNodeNr = -1;
    private int markedAttribute = -1;
    private int markedNamespace = -1;

    public TinyBuilderMonitor(/*@NotNull*/ TinyBuilder builder) {
        super(builder);
        this.builder = builder;
    }

    @Override
    public void markNextNode(int nodeKind) {
        mark = nodeKind;
    }

    @Override
    public void startDocument(int properties) throws XPathException {
        if (mark == Type.DOCUMENT) {
            markedNodeNr = builder.getTree().getNumberOfNodes();
        }
        mark = -1;
        super.startDocument(properties);
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        if (mark == Type.ELEMENT) {
            markedNodeNr = builder.getTree().getNumberOfNodes();
        }
        mark = -1;
        super.startElement(elemName, type, attributes, namespaces, location, properties);
    }

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (mark == Type.TEXT) {
            markedNodeNr = builder.getTree().getNumberOfNodes();
        }
        mark = -1;
        super.characters(chars, locationId, properties);
    }

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (mark == Type.COMMENT) {
            markedNodeNr = builder.getTree().getNumberOfNodes();
        }
        mark = -1;
        super.comment(chars, locationId, properties);
    }

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (mark == Type.PROCESSING_INSTRUCTION) {
            markedNodeNr = builder.getTree().getNumberOfNodes();
        }
        mark = -1;
        super.processingInstruction(target, data, locationId, properties);
    }

    /*@Nullable*/
    @Override
    public NodeInfo getMarkedNode() {
        if (markedNodeNr != -1) {
            return builder.getTree().getNode(markedNodeNr);
        } else if (markedAttribute != -1) {
            return builder.getTree().getAttributeNode(markedAttribute);
            // TODO: reinstate
//        } else if (markedNamespace != -1) {
//            NamespaceBinding nscode = builder.getTree().namespaceBinding[markedNamespace];
//            NamePool pool = builder.getConfiguration().getNamePool();
//            String prefix = nscode.getPrefix();
//            NodeInfo parent = builder.getTree().getNode(builder.getTree().namespaceParent[markedNamespace]);
//            NameTest test = new NameTest(Type.NAMESPACE, "", prefix, pool);
//            AxisIterator iter = parent.iterateAxis(AxisInfo.NAMESPACE, test);
//            return iter.next();
        } else {
            return null;
        }
    }
}

