////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.Type;

import java.util.List;
import java.util.Stack;

/**
 * PullPushTee is a pass-through filter class that links one PullProvider to another PullProvider
 * in a pipeline, copying all events that are read into a push pipeline, supplied in the form
 * of a Receiver.
 * <p>This class can be used to insert a schema validator into a pull pipeline, since Saxon's schema
 * validation is push-based. It could also be used to insert a serializer into the pipeline, allowing
 * the XML document being "pulled" to be displayed for diagnostic purposes.</p>
 */

public class PullPushTee extends PullFilter {

    private Receiver branch;
    boolean previousAtomic = false;
    private Stack<NamespaceMap> nsStack = new Stack<>();

    /**
     * Create a PullPushTee
     *
     * @param base   the PullProvider to which requests are to be passed
     * @param branch the Receiver to which all events are to be copied, as "push" events.
     *               This Receiver must already be open before use
     */

    public PullPushTee(/*@NotNull*/ PullProvider base, Receiver branch) {
        super(base);
        this.branch = branch;
    }

    /**
     * Get the Receiver to which events are being tee'd.
     *
     * @return the Receiver
     */

    public Receiver getReceiver() {
        return branch;
    }

    /**
     * Get the next event. This implementation gets the next event from the underlying PullProvider,
     * copies it to the branch Receiver, and then returns the event to the caller.
     *
     * @return an integer code indicating the type of event. The code
     *         {@link net.sf.saxon.pull.PullProvider.Event#END_OF_INPUT} is returned at the end of the sequence.
     */

    @Override
    public Event next() throws XPathException {
        currentEvent = super.next();
        copyEvent(currentEvent);
        return currentEvent;
    }


    /**
     * Copy a pull event to a Receiver
     *
     * @param event the pull event to be copied
     */

    private void copyEvent(Event event) throws XPathException {
        PullProvider in = getUnderlyingProvider();
        Location loc = in.getSourceLocator();
        if (loc == null) {
            loc = Loc.NONE;
        }
        Receiver out = branch;
        switch (event) {
            case START_DOCUMENT:
                out.startDocument(ReceiverOption.NONE);
                break;

            case START_ELEMENT:
                NamespaceBinding[] bindings = in.getNamespaceDeclarations();
                NamespaceMap nsMap = nsStack.isEmpty() ? NamespaceMap.emptyMap() : nsStack.peek();
                for (NamespaceBinding binding : bindings) {
                    if (binding == null) {
                        break;
                    }
                    nsMap = nsMap.put(binding.getPrefix(), binding.getURI());
                }
                nsStack.push(nsMap);
                out.startElement(in.getNodeName(), in.getSchemaType(),
                                 in.getAttributes(), nsMap,
                                 loc, ReceiverOption.NAMESPACE_OK);

                break;

            case TEXT:

                out.characters(in.getStringValue(), loc, ReceiverOption.WHOLE_TEXT_NODE);
                break;

            case COMMENT:

                out.comment(in.getStringValue(), loc, ReceiverOption.NONE);
                break;

            case PROCESSING_INSTRUCTION:

                out.processingInstruction(
                        in.getNodeName().getLocalPart(),
                        in.getStringValue(), loc, ReceiverOption.NONE);
                break;

            case END_ELEMENT:

                out.endElement();
                nsStack.pop();
                break;

            case END_DOCUMENT:
                List<UnparsedEntity> entities = in.getUnparsedEntities();
                if (entities != null) {
                    for (Object entity : entities) {
                        UnparsedEntity ue = (UnparsedEntity) entity;
                        out.setUnparsedEntity(ue.getName(), ue.getSystemId(), ue.getPublicId());
                    }
                }
                out.endDocument();
                break;

            case END_OF_INPUT:
                in.close();
                break;

            case ATOMIC_VALUE:
                if (out instanceof SequenceReceiver) {
                    out.append(super.getAtomicValue(), loc, ReceiverOption.NONE);
                } else {
                    if (previousAtomic) {
                        out.characters(" ", loc, ReceiverOption.NONE);
                    }
                    CharSequence chars = in.getStringValue();
                    out.characters(chars, loc, ReceiverOption.NONE);
                }
                break;

            case ATTRIBUTE:
                if (out instanceof SequenceReceiver) {
                    Orphan o = new Orphan(in.getPipelineConfiguration().getConfiguration());
                    o.setNodeName(getNodeName());
                    o.setNodeKind(Type.ATTRIBUTE);
                    o.setStringValue(getStringValue());
                    out.append(o, loc, ReceiverOption.NONE);
                }
                break;

            case NAMESPACE:
                if (out instanceof SequenceReceiver) {
                    Orphan o = new Orphan(in.getPipelineConfiguration().getConfiguration());
                    o.setNodeName(getNodeName());
                    o.setNodeKind(Type.NAMESPACE);
                    o.setStringValue(getStringValue());
                    out.append(o, loc, ReceiverOption.NONE);
                }
                break;

            default:
                throw new UnsupportedOperationException("" + event);

        }
        previousAtomic = event == Event.ATOMIC_VALUE;
    }
}

