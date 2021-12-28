////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Action;
import net.sf.saxon.s9api.Destination;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import java.util.ArrayList;
import java.util.List;

/**
 * Implement the "sequence normalization" logic as defined in the XSLT 3.0/XQuery 3.0
 * serialization spec.
 * <p>
 * There are two subclasses, which handle the different logic for the case where an
 * {@code item-separator} is provided, and the case where whitespace-separation is used.
 * Note that the whitespace case behaves differently from the case where the item-separator
 * is set to a single space.
 * <p>Because this Receiver is often used as the entry point to the pipeline for a
 * {@link Destination}, it includes logic allowing {@code onClose} action for the
 * Destination to be triggered when the Receiver is closed.</p>
 */

public abstract class SequenceNormalizer extends ProxyReceiver {

    protected int level = 0;
    private List<Action> actionList;
    private boolean failed = false;

    public SequenceNormalizer(Receiver next) {
        super(next);
    }

    /**
     * Start of event stream
     */
    @Override
    public void open() throws XPathException {
        level = 0;
        previousAtomic = false;
        super.open();
        getNextReceiver().startDocument(ReceiverOption.NONE);
    }

    /**
     * Start of a document node.
     */
    @Override
    public void startDocument(int properties) throws XPathException {
        level++;
        previousAtomic = false;
    }

    /**
     * Notify the end of a document node
     */
    @Override
    public void endDocument() throws XPathException {
        level--;
        previousAtomic = false;
    }

    /**
     * Notify the start of an element
     */
    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        try {
            level++;
            super.startElement(elemName, type, attributes, namespaces, location, properties);
            previousAtomic = false;
        } catch (XPathException e) {
            failed = true;
            throw e;
        }
    }

    /**
     * Character data
     */
    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        try {
            super.characters(chars, locationId, properties);
            previousAtomic = false;
        } catch (XPathException e) {
            failed = true;
            throw e;
        }
    }

    /**
     * Processing Instruction
     */
    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        try {
            super.processingInstruction(target, data, locationId, properties);
            previousAtomic = false;
        } catch (XPathException e) {
            failed = true;
            throw e;
        }
    }

    /**
     * Output a comment
     */
    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        try {
            super.comment(chars, locationId, properties);
            previousAtomic = false;
        } catch (XPathException e) {
            failed = true;
            throw e;
        }
    }

    /**
     * End of element
     */
    @Override
    public void endElement() throws XPathException {
        try {
            level--;
            super.endElement();
            previousAtomic = false;
        } catch (XPathException e) {
            failed = true;
            throw e;
        }
    }

    /**
     * End of output. Note that closing this receiver also closes the rest of the
     * pipeline.
     */
    @Override
    public void close() throws XPathException {
        if (failed) {
            super.close();
        } else {
            getNextReceiver().endDocument();
            super.close();
            try {
                if (actionList != null) {
                    for (Action action : actionList) {
                        action.act();
                    }
                }
            } catch (SaxonApiException e) {
                throw XPathException.makeXPathException(e);
            }
        }
    }

    /**
     * Set actions to be performed when this {@code Receiver} is closed
     * @param actionList a list of actions to be performed
     */

    public void onClose(List<Action> actionList) {
        this.actionList = actionList;
    }

    public void onClose(Action action) {
        if (actionList == null) {
            actionList = new ArrayList<>();
        }
        actionList.add(action);
    }

}

