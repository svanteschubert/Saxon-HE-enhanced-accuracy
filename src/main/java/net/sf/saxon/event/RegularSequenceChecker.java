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
import net.sf.saxon.type.Type;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * A <tt>RegularSequenceChecker</tt> is a filter that can be inserted into a Receiver pipeline
 * to check that the sequence of events passed in is a <b>regular event sequence</b>. Many
 * (though not all) implementations of {@link Outputter} require the sequence of events to
 * be regular according to this definition.
 * <p>A sequence of {@code Receiver} events is <b>regular</b> if the following conditions
 * are satisfied:</p>
 * <ol>
 *     <li>Calls to {@link Outputter#startElement(NodeName, SchemaType, Location, int)}, {@link #endElement()},
 *     {@link Outputter#startDocument(int)}, and {@link #endDocument()} are properly paired and nested.</li>
 *     <li>Events must only occur in a state where they are permitted; the states and transitions
 *     between states are defined by the table below. The initial state is <b>initial</b>,
 *     and the final state must be <b>final</b>.</li>
 * </ol>
 * <table>
 *     <caption>Permitted state transitions</caption>
 *     <thead>
 *         <tr><td>State</td><td>Events</td><td>Next State</td></tr>
 *     </thead>
 *     <tbody>
 *         <tr><td>initial</td><td>{@link #open()}</td><td>open</td></tr>
 *         <tr><td>open</td><td>{@link #open()}</td><td>open</td></tr>
 *         <tr><td>open</td><td>{@link Outputter#append(Item, Location, int)}, {@link #append(Item)},
 *         {@link Outputter#characters(CharSequence, Location, int)}, {@link Outputter#comment(CharSequence, Location, int)},
 *         {@link Outputter#processingInstruction(String, CharSequence, Location, int)}</td><td>open</td></tr>
 *         <tr><td>open</td><td>{@link Outputter#startDocument(int)}</td><td>content</td></tr>
 *         <tr><td>open</td><td>{@link Outputter#startElement(NodeName, SchemaType, Location, int)}</td><td>content</td></tr>
 *         <tr><td>content</td><td>{@link Outputter#characters(CharSequence, Location, int)}, {@link Outputter#comment(CharSequence, Location, int)},
 *         {@link Outputter#processingInstruction(String, CharSequence, Location, int)}</td><td>content</td></tr>
 *         <tr><td>content</td><td>{@link Outputter#startElement(NodeName, SchemaType, Location, int)}</td><td>startTag</td></tr>
 *         <tr><td>content</td><td>{@link #endDocument()}, {@link #endElement()}</td><td>if the stack is empty, then content, otherwise open</td></tr>
 *         <tr><td>(any)</td><td>close</td><td>final</td></tr>
 *         <tr><td>final</td><td>close</td><td>final</td></tr>
 *     </tbody>
 * </table>
 * <p>This class is not normally used in production within Saxon, but is available for diagnostics when needed.</p>
 * <p>Some implementations of {@code Receiver} accept sequences of events that are not regular; indeed, some
 * implementations are explicitly designed to produce a regular sequence from an irregular sequence.
 * Examples of such irregularities are <b>append</b> or <b>startDocument</b> events appearing within
 * element content, or <b>attribute</b> events being followed by <b>text</b> events with no intervening
 * <b>startContent</b>.</p>
 * <p>The rules for a <b>regular sequence</b> imply that the top level events (any events not surrounded
 * by startElement-endElement or startDocument-endDocument) can represent any sequence of items, including
 * for example multiple document nodes, free-standing attribute and namespace nodes, maps, arrays, and functions;
 * but within a startElement-endElement or startDocument-endDocument pair, the events represent content
 * that has been normalized and validated according to the XSLT rules for constructing complex content, or
 * the XQuery equivalent: for example, attributes and namespaces must appear before child nodes,
 * adjacent text nodes should
 * have been merged, zero-length text nodes should have been eliminated, all namespaces should be explicitly
 * declared, document nodes should be replaced by their children.</p>
 * <p>Element nodes in "composed form" (that is, existing as a tree in memory) may be passed through
 * the {@link #append(Item)} method at the top level, but within a startElement-endElement or
 * startDocument-endDocument pair, elements must be represented in "decomposed form" as a sequence
 * of events.</p>
 * <p>A call to {@link #close} is permitted in any state, but it should only be called in <code>Open</code>
 * state except on an error path; on error paths calling {@link #close} is recommended to ensure that
 * resources are released.</p>
 */
public class RegularSequenceChecker extends ProxyReceiver {

    private Stack<Short> stack = new Stack<>();

    public enum State {Initial, Open, StartTag, Content, Final, Failed}
    // StartTag is used only in an incremental Receiver where attributes and namespaces are notified separately
    private enum Transition {
        OPEN, APPEND, TEXT, COMMENT, PI, START_DOCUMENT,
        START_ELEMENT, END_ELEMENT, END_DOCUMENT, CLOSE}

    private State state;
    private boolean fullChecking = false;
    private static Map<State, Map<Transition, State>> machine = new HashMap<>();

    private static void edge(State from, Transition event, State to) {
        Map<Transition, State> edges = machine.computeIfAbsent(from, s -> new HashMap<>());
        edges.put(event, to);
    }

    static {
        edge(State.Initial, Transition.OPEN, State.Open);
        edge(State.Open, Transition.APPEND, State.Open);
        edge(State.Open, Transition.TEXT, State.Open);
        edge(State.Open, Transition.COMMENT, State.Open);
        edge(State.Open, Transition.PI, State.Open);
        edge(State.Open, Transition.START_DOCUMENT, State.Content);
        edge(State.Open, Transition.START_ELEMENT, State.Content);
        edge(State.Content, Transition.TEXT, State.Content);
        edge(State.Content, Transition.COMMENT, State.Content);
        edge(State.Content, Transition.PI, State.Content);
        edge(State.Content, Transition.START_ELEMENT, State.Content);
        edge(State.Content, Transition.END_ELEMENT, State.Content); // or Open if the stack is empty
        edge(State.Content, Transition.END_DOCUMENT, State.Open);
        edge(State.Open, Transition.CLOSE, State.Final);
        edge(State.Failed, Transition.CLOSE, State.Failed);
        //edge(State.Final, "close", State.Final);  // This was a concession to poor practice, but apparently no longer needed
    }

    private void transition(Transition event) {
        final Map<Transition, State> map = machine.get(state);
        State newState = map==null ? null : map.get(event);
        if (newState == null) {
            //assert false;
            throw new IllegalStateException("Event " + event + " is not permitted in state " + state);
        } else {
            state = newState;
        }
    }


    /**
     * Create a RegularSequenceChecker and allocate a unique Id.
     *
     * @param nextReceiver the underlying receiver to which the events will be sent (without change)
     * @param fullChecking requests full validation of the content passed across the interface. If false,
     *                     the only checking is that the sequence of events is correct. If true, more thorough
     *                     validation is carried out (though this does not necessarily mean that every violation
     *                     is detected).
     */

    public RegularSequenceChecker(Receiver nextReceiver, boolean fullChecking) {
        super(nextReceiver);
        state = State.Initial;
        this.fullChecking = fullChecking;
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output. In a regular sequence, append
     * events occur only at the top level, that is, when the document / element stack is empty.
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
*                       need to be copied. Values are {@link ReceiverOption#ALL_NAMESPACES}; the default (0) means
     */

    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        try {
            transition(Transition.APPEND);
            nextReceiver.append(item, locationId, copyNamespaces);
        } catch (XPathException e) {
            state = State.Failed;
            throw e;
        }
    }

    /**
     * Character data (corresponding to a text node). For character data within content (that is, events occurring
     * when the startDocument / startElement stack is non-empty), character data events will never be consecutive
     * and will never be zero-length.
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        transition(Transition.TEXT);
        if (chars.length() == 0 && !stack.isEmpty()) {
            throw new IllegalStateException("Zero-length text nodes not allowed within document/element content");
        }
        try {
            nextReceiver.characters(chars, locationId, properties);
        } catch (XPathException e) {
            state = State.Failed;
            throw e;
        }
    }

    /**
     * End of sequence
     */

    @Override
    public void close() throws XPathException {
        if (state != State.Final && state != State.Failed) {
            if (!stack.isEmpty()) {
                throw new IllegalStateException("Unclosed element or document nodes at end of stream");
            }
            nextReceiver.close();
            state = State.Final;
        }
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        transition(Transition.COMMENT);
        try {
            nextReceiver.comment(chars, locationId, properties);
        } catch (XPathException e) {
            state = State.Failed;
            throw e;
        }
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        transition(Transition.END_DOCUMENT);
        if (stack.isEmpty() || stack.pop() != Type.DOCUMENT) {
            throw new IllegalStateException("Unmatched endDocument() call");
        }
        try {
            nextReceiver.endDocument();
        } catch (XPathException e) {
            state = State.Failed;
            throw e;
        }
    }

    /**
     * End of element
     */

    @Override
    public void endElement() throws XPathException {
        transition(Transition.END_ELEMENT);
        if (stack.isEmpty() || stack.pop() != Type.ELEMENT) {
            throw new IllegalStateException("Unmatched endElement() call");
        }
        if (stack.isEmpty()) {
            state = State.Open;
        }
        try {
            nextReceiver.endElement();
        } catch (XPathException e) {
            state = State.Failed;
            throw e;
        }
    }

    /**
     * Start of event stream
     */

    @Override
    public void open() throws XPathException {
        transition(Transition.OPEN);
        try {
            nextReceiver.open();
        } catch (XPathException e) {
            state = State.Failed;
            throw e;
        }
    }

    /**
     * Processing Instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        transition(Transition.PI);
        try {
            nextReceiver.processingInstruction(target, data, locationId, properties);
        } catch (XPathException e) {
            state = State.Failed;
            throw e;
        }
    }

    /**
     * Start of a document node.
     * @param properties properties of the document node.
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        transition(Transition.START_DOCUMENT);
        stack.push(Type.DOCUMENT);
        try {
            nextReceiver.startDocument(properties);
        } catch (XPathException e) {
            state = State.Failed;
            throw e;
        }
    }

    /**
     * Notify the start of an element.
     *
     * <p>
     * All attributes must satisfy the following constraints:
     * <ol>
     *     <li>The namespace prefix and URI must either both be present (non-zero-length) or both absent</li>
     *     <li>The prefix "xml" and the URI "http://www.w3.org/XML/1998/namespace"
     *     are allowed only in combination.</li>
     *     <li>The namespace URI "http://www.w3.org/2000/xmlns/" is not allowed.</li>
     *     <li>The namespace prefix "xmlns" is not allowed.</li>
     *     <li>The local name "xmlns" is not allowed in the absence of a namespace prefix and URI.</li>
     * </ol>
     * <p>
     * The following additional constraints apply to the set of attributes as a whole:
     * <ol>
     *     <li>No two attributes may have the same (local-name, namespace URI) combination.</li>
     *     <li>No namespace prefix may be used in conjunction with more than one namespace URI.</li>
     *     <li>Every (namespace prefix, namespace URI) combination must correspond to an in-scope namespace:
     *     that is, unless the (prefix, URI) pair is ("", "") or ("xml", "http://www.w3.org/XML/1998/namespace"),
     *     it must be present in the in-scope namespaces.</li>
     * </ol>
     * <p>
     * These constraints are not all enforced by this class.
     * </p>
     *  @param elemName  the name of the element. If the name is in a namespace (non-empty namespace URI)
     *                  then the {@link Outputter#namespace(String, String, int)} event must include
     *                  a binding for the relevant prefix (or absence of a prefix) to the relevant URI.
     * @param type the type annotation of the element.
     * @param attributes the attributes of the element
     * @param namespaces the in-scope namespaces of the element
     * @param location  provides information such as line number and system ID.
     * @param properties properties of the element node
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        transition(Transition.START_ELEMENT);
        stack.push(Type.ELEMENT);
        if (fullChecking) {
            attributes.verify();

            String prefix = elemName.getPrefix();
            if (prefix.isEmpty()) {
                String declaredDefaultUri = namespaces.getDefaultNamespace();
                if (!declaredDefaultUri.equals(elemName.getURI())) {
                    throw new IllegalStateException("URI of element Q{" + elemName.getURI() +
                                                            "}" + elemName.getLocalPart() +
                                                            " does not match declared default namespace {"
                                                            + declaredDefaultUri + "}");
                }
            } else {
                String declaredUri = namespaces.getURI(prefix);
                if (declaredUri == null) {
                    throw new IllegalStateException("Prefix " + prefix + " has not been declared");
                } else if (!declaredUri.equals(elemName.getURI())) {
                    throw new IllegalStateException("Prefix " + prefix + " is bound to the wrong namespace");
                }
            }
            for (AttributeInfo att : attributes) {
                NodeName name = att.getNodeName();
                if (!name.getURI().isEmpty()) {
                    String attPrefix = name.getPrefix();
                    String declaredUri = namespaces.getURI(attPrefix);
                    if (declaredUri == null) {
                        throw new IllegalStateException("Prefix " + attPrefix + " has not been declared for attribute " + att.getNodeName().getDisplayName());
                    } else if (!declaredUri.equals(name.getURI())) {
                        throw new IllegalStateException("Prefix " + prefix + " is bound to the wrong namespace {" + declaredUri + "}");
                    }
                }
            }
        }
        try {
            nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
        } catch (XPathException e) {
            state = State.Failed;
            throw e;
        }


    }
}

