////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ExternalObject;

/**
 * SequenceReceiver: this extension of the Receiver interface is used when processing
 * a sequence constructor. It differs from the Receiver in allowing items (atomic values or
 * nodes) to be added to the sequence, not just tree-building events.
 */

public abstract class SequenceReceiver implements Receiver {

    protected boolean previousAtomic = false;
    /*@NotNull*/
    protected PipelineConfiguration pipelineConfiguration;
    /*@Nullable*/
    protected String systemId = null;

    /**
     * Create a SequenceReceiver
     *
     * @param pipe the pipeline configuration
     */

    public SequenceReceiver(/*@NotNull*/ PipelineConfiguration pipe) {
        this.pipelineConfiguration = pipe;
    }

    /*@NotNull*/
    @Override
    public final PipelineConfiguration getPipelineConfiguration() {
        return pipelineConfiguration;
    }

    @Override
    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipelineConfiguration) {
        this.pipelineConfiguration = pipelineConfiguration;
    }

    /**
     * Get the Saxon Configuration
     *
     * @return the Configuration
     */

    public final Configuration getConfiguration() {
        return pipelineConfiguration.getConfiguration();
    }

    /**
     * Set the system ID
     *
     * @param systemId the URI used to identify the tree being passed across this interface
     */

    @Override
    public void setSystemId(/*@Nullable*/ String systemId) {
        this.systemId = systemId;
    }

    /**
     * Get the system ID
     *
     * @return the system ID that was supplied using the setSystemId() method
     */

    /*@Nullable*/
    @Override
    public String getSystemId() {
        return systemId;
    }

    /**
     * Notify an unparsed entity URI.
     *
     * @param name     The name of the unparsed entity
     * @param systemID The system identifier of the unparsed entity
     * @param publicID The public identifier of the unparsed entity
     */

    @Override
    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
    }

    /**
     * Start the output process
     */

    @Override
    public void open() throws XPathException {
        previousAtomic = false;
    }

    /**
     * Append an arbitrary item (node, atomic value, or function) to the output
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param properties if the item is an element node, this indicates whether its namespaces
*                       need to be copied. Values are {@link ReceiverOption#ALL_NAMESPACES}; the default (0) means
     */

    @Override
    public abstract void append(Item item, Location locationId, int properties) throws XPathException;

    /**
     * Append an arbitrary item (node, atomic value, or function) to the output.
     * By default, if the item is an element
     * node, it is copied with all namespaces.
     *
     * @param item the item to be appended
     * @throws XPathException if the operation fails
     */

    @Override
    public void append(Item item) throws XPathException {
        append(item, Loc.NONE, ReceiverOption.ALL_NAMESPACES);
    }

    /**
     * Get the name pool
     *
     * @return the Name Pool that was supplied using the setConfiguration() method
     */

    public NamePool getNamePool() {
        return pipelineConfiguration.getConfiguration().getNamePool();
    }

    /**
     * Helper method for subclasses to invoke if required: flatten an array. The effect
     * is that each item in each member of the array is appended to this {@code SequenceReceiver}
     * by calling its {@link #append(Item) method}
     * @param array the array to be flattened
     * @param locationId the location of the instruction triggering this operation
     * @param copyNamespaces options for copying namespace nodes
     * @throws XPathException if things go wrong
     */

    protected void flatten(ArrayItem array, Location locationId, int copyNamespaces) throws XPathException {
        for (Sequence member : array.members()) {
            member.iterate().forEachOrFail(it -> append(it, locationId, copyNamespaces));
        }
    }

    /**
     * Helper method for subclasses to invoke if required: decompose an item into a sequence
     * of node events. Note that when this is used, methods such as characters(), comment(),
     * startElement(), and processingInstruction() are responsible for setting previousAtomic to false.
     * @param item the item to be decomposed (that is, to be delivered to this {@code SequenceReceiver}
     *             as a sequence of separate events
     * @param locationId the location of the originating instruction
     * @param copyNamespaces options for copying namespace nodes
     * @throws XPathException if things go wrong
     */

    protected void decompose(Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (item != null) {
            if (item instanceof AtomicValue || item instanceof ExternalObject) {
                if (previousAtomic) {
                    characters(" ", locationId, ReceiverOption.NONE);
                }
                characters(item.getStringValueCS(), locationId, ReceiverOption.NONE);
                previousAtomic = true;
            } else if (item instanceof ArrayItem) {
                flatten((ArrayItem)item, locationId, copyNamespaces);
            } else if (item instanceof Function) {
                String thing = item instanceof MapItem ? "map" : "function item";
                String errorCode = getErrorCodeForDecomposingFunctionItems();
                if (errorCode.startsWith("SENR")) {
                    throw new XPathException("Cannot serialize a " + thing + " using this output method", errorCode, locationId);
                } else {
                    throw new XPathException("Cannot add a " + thing + " to an XDM node tree", errorCode, locationId);
                }
            } else {
                NodeInfo node = (NodeInfo)item;
                int kind = node.getNodeKind();
                if (node instanceof Orphan && ((Orphan) node).isDisableOutputEscaping()) {
                    // see test case doe-0801, -2 -3 - needed for output buffered within try/catch, xsl:fork etc
                    characters(item.getStringValueCS(), locationId, ReceiverOption.DISABLE_ESCAPING);
                    previousAtomic = false;
                } else if (kind == Type.DOCUMENT) {
                    startDocument(ReceiverOption.NONE); // needed to ensure that illegal namespaces or attributes in the content are caught
                    for (NodeInfo child : node.children()) {
                        append(child, locationId, copyNamespaces);
                    }
                    previousAtomic = false;
                    endDocument();
                } else if (kind == Type.ATTRIBUTE || kind == Type.NAMESPACE) {
                    String thing = kind == Type.ATTRIBUTE ? "an attribute" : "a namespace";
                    throw new XPathException("Sequence normalization: Cannot process " + thing + " node", "SENR0001", locationId);

                } else {
                    int copyOptions = CopyOptions.TYPE_ANNOTATIONS;
                    if (ReceiverOption.contains(copyNamespaces, ReceiverOption.ALL_NAMESPACES)) {
                        copyOptions |= CopyOptions.ALL_NAMESPACES;
                    }
                    ((NodeInfo) item).copy(this, copyOptions, locationId);
                    previousAtomic = false;
                }
            }
        }
    }

    protected String getErrorCodeForDecomposingFunctionItems() {
        return getPipelineConfiguration().isXSLT() ? "XTDE0450" : "XQTY0105";
    }

    /**
     * Ask whether this Receiver can handle arbitrary items in its {@link Outputter#append} and
     * {@link Outputter#append(Item, Location, int)} methods. If it cannot, then calling
     * these methods will raise an exception (typically but not necessarily an
     * {@code UnsupportedOperationException}). This implementation returns true.
     *
     * @return true if the Receiver is able to handle items supplied to
     *      its {@link Outputter#append} and {@link Outputter#append(Item, Location, int)} methods. A
     *      receiver that returns true may still reject some kinds of item, for example
     *      it may reject function items.
     */

    @Override
    public boolean handlesAppend() {
        return true;
    }
}

