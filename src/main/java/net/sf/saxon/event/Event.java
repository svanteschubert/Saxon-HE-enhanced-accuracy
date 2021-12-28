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

/**
 * An event is an object representing one of the events that can be passed to a receiver: for example, a startElement,
 * endElement, characters, or comment event. Sufficient information is retained in order to enable a stored event to
 * be "replayed" later.
 */
public abstract class Event {

    /**
     * Send the event to a receiver
     *
     * @param out the receiver to which the event is to be sent
     * @throws XPathException the the receiver reports an error
     */

    public void replay(Receiver out) throws XPathException {
    }


    /**
     * Event representing start of document
     */

    public static class StartDocument extends Event {
        int properties;
        public StartDocument(int properties) {
            this.properties = properties;
        }
        @Override
        public void replay(Receiver out) throws XPathException {
            out.startDocument(properties);
        }
    }

    /**
     * Event representing end of document
     */

    public static class EndDocument extends Event {
        public EndDocument() {
        }

        @Override
        public void replay(Receiver out) throws XPathException {
            out.endDocument();
        }
    }

    /**
     * Event representing the start of an element (including attributes or namespaces)
     */

    public static class StartElement extends Event {
        NodeName name;
        SchemaType type;
        AttributeMap attributes;
        NamespaceMap namespaces;
        Location location;
        int properties;

        public StartElement(NodeName name, SchemaType type, AttributeMap attributes, NamespaceMap namespaces, Location location, int properties) {
            this.name = name;
            this.type = type;
            this.attributes = attributes;
            this.namespaces = namespaces;
            this.location = location;
            this.properties = properties;
        }

        @Override
        public void replay(Receiver out) throws XPathException {
            out.startElement(name, type, attributes, namespaces, location, properties);
        }

        public void replay(Receiver out, int newProps) throws XPathException {
            out.startElement(name, type, attributes, namespaces, location, newProps);
        }

        public int getProperties() {
            return properties;
        }
    }

    /**
     * Event representing the end of an element
     */

    public static class EndElement extends Event {
        public EndElement() {
        }

        @Override
        public void replay(Receiver out) throws XPathException {
            out.endElement();
        }
    }

    /**
     * Event representing a text node
     */

    public static class Text extends Event {

        String content;
        Location location;
        int properties;

        public Text(CharSequence content, Location location, int properties) {
            this.content = content.toString();
            this.location = location;
            this.properties = properties;
        }

        @Override
        public void replay(Receiver out) throws XPathException {
            out.characters(content, location, properties);
        }
    }

    /**
     * Event representing a comment node
     */

    public static class Comment extends Event {

        String content;
        Location location;
        int properties;

        public Comment(CharSequence content, Location location, int properties) {
            this.content = content.toString();
            this.location = location;
            this.properties = properties;
        }

        @Override
        public void replay(Receiver out) throws XPathException {
            out.comment(content, location, properties);
        }
    }

    /**
     * Event representing a processing instruction node
     */

    public static class ProcessingInstruction extends Event {
        String target;
        String content;
        Location location;
        int properties;

        public ProcessingInstruction(String target, CharSequence content, Location location, int properties) {
            this.target = target;
            this.content = content.toString();
            this.location = location;
            this.properties = properties;
        }

        @Override
        public void replay(Receiver out) throws XPathException {
            out.processingInstruction(target, content, location, properties);
        }
    }

    /**
     * Event representing an arbitrary item being sent to the event stream in composed form. Perhaps
     * an atomic value, perhaps an entire element or document in composed form.
     */

    public static class Append extends Event {
        Item item;
        Location location;
        int properties;

        public Append(Item item, Location location, int properties) {
            this.item = item;
            this.location = location;
            this.properties = properties;
        }

        @Override
        public void replay(Receiver out) throws XPathException {
            out.append(item, location, properties);
        }
    }
}


