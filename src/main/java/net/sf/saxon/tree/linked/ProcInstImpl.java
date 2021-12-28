////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.NoNamespaceName;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;

/**
 * ProcInstImpl is an implementation of ProcInstInfo used by the Propagator to construct
 * its trees.
 *
 * @author Michael H. Kay
 */


public class ProcInstImpl extends NodeImpl {

    String content;
    String name;
    String systemId;
    int lineNumber = -1;
    int columnNumber = -1;

    public ProcInstImpl(String name, String content) {
        this.name = name;
        this.content = content;
    }

    /**
     * Get the name of the node. Returns null for an unnamed node
     *
     * @return the name of the node
     */
    @Override
    public NodeName getNodeName() {
        return new NoNamespaceName(name);
    }



    @Override
    public String getStringValue() {
        return content;
    }

    /**
     * Get the typed value of this node.
     * Returns the string value, as an instance of xs:string
     */

    /*@NotNull*/
    @Override
    public AtomicSequence atomize() {
        return new StringValue(getStringValue());
    }

    @Override
    public final int getNodeKind() {
        return Type.PROCESSING_INSTRUCTION;
    }

    /**
     * Set the system ID and line number
     *
     * @param uri        the system identifier
     * @param lineNumber the line number
     */

    public void setLocation(String uri, int lineNumber, int columnNumber) {
        this.systemId = uri;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    /**
     * Get the system ID for the entity containing this node.
     *
     * @return the system identifier
     */

    @Override
    public String getSystemId() {
        return systemId;
    }

    /**
     * Get the line number of the node within its source entity
     */

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Get the column number of the node within its source entity
     */

    @Override
    public int getColumnNumber() {
        return columnNumber;
    }


    /**
     * Copy this node to a given outputter
     */

    @Override
    public void copy(/*@NotNull*/ Receiver out, int copyOptions, Location locationId) throws XPathException {
        out.processingInstruction(getLocalPart(), content, locationId, ReceiverOption.NONE);
    }

    /**
     * Rename this node
     *
     * @param newNameCode the new name
     */

    @Override
    public void rename(NodeName newNameCode) {
        name = newNameCode.getLocalPart();
    }


    /**
     * Replace the string-value of this node
     *
     * @param stringValue the new string value
     */

    @Override
    public void replaceStringValue(/*@NotNull*/ CharSequence stringValue) {
        content = stringValue.toString();
    }
}

