////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import java.util.Arrays;

/**
 * A TreeReceiver acts as a bridge between a SequenceReceiver, which can receive
 * events for constructing any kind of sequence, and an ordinary Receiver, which
 * only handles events relating to the building of trees. To do this, it has to
 * process any items added to the sequence using the append() interface; all other
 * events are passed through unchanged.
 * <p>If atomic items are appended to the sequence, then adjacent atomic items are
 * turned in to a text node by converting them to strings and adding a single space
 * as a separator.</p>
 * <p>If a document node is appended to the sequence, then the document node is ignored
 * and its children are appended to the sequence.</p>
 * <p>If any other node is appended to the sequence, then it is pushed to the result
 * as a sequence of Receiver events, which may involve walking recursively through the
 * contents of a tree.</p>
 */

public class TreeReceiver extends SequenceReceiver {
    private Receiver nextReceiver;
    private int level = 0;
    private boolean[] isDocumentLevel = new boolean[20];
    // The sequence of events can include startElement/endElement pairs or startDocument/endDocument
    // pairs at any level. A startDocument/endDocument pair is essentially ignored except at the
    // outermost level, except that a namespace or attribute node cannot be sent when we're at a
    // document level. See for example schema90963-err.xsl

    /**
     * Create a TreeReceiver
     *
     * @param nextInChain the receiver to which events will be directed, after
     *                    expanding append events into more primitive tree-based events
     */

    public TreeReceiver(Receiver nextInChain) {
        super(nextInChain.getPipelineConfiguration());
        nextReceiver = nextInChain;
        previousAtomic = false;
        setPipelineConfiguration(nextInChain.getPipelineConfiguration());
    }

    @Override
    public void setSystemId(String systemId) {
        if (systemId != null && !systemId.equals(this.systemId)) {
            this.systemId = systemId;
            if (nextReceiver != null) {
                nextReceiver.setSystemId(systemId);
            }
        }
    }

    @Override
    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        if (pipelineConfiguration != pipe) {
            pipelineConfiguration = pipe;
            if (nextReceiver != null) {
                nextReceiver.setPipelineConfiguration(pipe);
            }
        }
    }

    /**
     * Get the underlying Receiver (that is, the next one in the pipeline)
     *
     * @return the underlying Receiver
     */

    public Receiver getNextReceiver() {
        return nextReceiver;
    }

    /**
     * Start of event sequence
     */

    @Override
    public void open() throws XPathException {
        if (nextReceiver == null) {
            throw new IllegalStateException("TreeReceiver.open(): no underlying receiver provided");
        }
        nextReceiver.open();
        previousAtomic = false;
    }

    /**
     * End of event sequence
     */

    @Override
    public void close() throws XPathException {
        if (nextReceiver != null) {
            nextReceiver.close();
        }
        previousAtomic = false;
    }

    /**
     * Start of a document node.
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        if (level == 0) {
            nextReceiver.startDocument(properties);
        }
        if (isDocumentLevel.length - 1 < level) {
            isDocumentLevel = Arrays.copyOf(isDocumentLevel, level * 2);
        }
        isDocumentLevel[level++] = true;
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        level--;
        if (level == 0) {
            nextReceiver.endDocument();
        }
    }

    /**
     * Notify the start of an element
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties)
            throws XPathException {
        nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
        previousAtomic = false;
        if (isDocumentLevel.length - 1 < level) {
            isDocumentLevel = Arrays.copyOf(isDocumentLevel, level * 2);
        }
        isDocumentLevel[level++] = false;
    }



    /**
     * End of element
     */

    @Override
    public void endElement() throws XPathException {
        nextReceiver.endElement();
        previousAtomic = false;
        level--;
    }

    /**
     * Character data
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (chars.length() > 0) {
            nextReceiver.characters(chars, locationId, properties);
        }
        previousAtomic = false;
    }


    /**
     * Processing Instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        nextReceiver.processingInstruction(target, data, locationId, properties);
        previousAtomic = false;
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        nextReceiver.comment(chars, locationId, properties);
        previousAtomic = false;
    }


    /**
     * Set the URI for an unparsed entity in the document.
     */

    @Override
    public void setUnparsedEntity(String name, String uri, String publicId) throws XPathException {
        nextReceiver.setUnparsedEntity(name, uri, publicId);
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     */

    @Override
    public void append(/*@Nullable*/ Item item, Location locationId, int copyNamespaces) throws XPathException {
        decompose(item, locationId, copyNamespaces);
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    @Override
    public boolean usesTypeAnnotations() {
        return nextReceiver.usesTypeAnnotations();
    }
}

