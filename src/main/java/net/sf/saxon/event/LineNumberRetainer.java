// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import java.util.Objects;

/**
 * This class is used to adjust the line and column positions reported by a SAX parser. SAX
 * reports the line/column at the end of each event (for example, the position at the end
 * of a text node), whereas what the user wants is typically the position at the start of
 * the event. This class therefore remembers the position reported with one event, and reports
 * it onwards as the position of the next event. Unfortunately it doesn't solve the problem
 * that we get no information about the position of attributes.
 *
 * <p>There's one complication: the {@code Receiver} pipeline doesn't pass location
 * information for an {@code endElement} event. Rather than introduce an interface
 * change to pass an extra argument to {@code endElement}, we instead register the
 * original SAX {@code Location} object in the {@link PipelineConfiguration}, and
 * pick the value up from there.</p>
 *
 * <p><b>DROPPED from the Saxon 13 build because in edge cases with multiple-entity files,
 * it gets the base URI of nodes wrong.</b></p>
 */
public class LineNumberRetainer extends ProxyReceiver {

    Location lastLocation = Loc.NONE;
    Location originalLocator;

    public LineNumberRetainer(Receiver out) {
        super(out);
    }

    /**
     * Start of a document node.
     */
    @Override
    public void startDocument(int properties) throws XPathException {
        super.startDocument(properties);
        originalLocator = (Location)pipelineConfiguration.getComponent("LocalLocator");
        lastLocation = new Loc(getSystemId(), 1, 1);
    }

    public void resetLocation(Location loc) {
        lastLocation = loc.saveLocation();
    }

    /**
     * Notify the end of a document node
     */
    @Override
    public void endDocument() throws XPathException {
        super.endDocument();
    }

    /**
     * Notify the start of an element
     *
     * @param elemName   the name of the element.
     * @param type       the type annotation of the element.
     * @param attributes the attributes of this element
     * @param namespaces the in-scope namespaces of this element: generally this is all the in-scope
     *                   namespaces, without relying on inheriting namespaces from parent elements
     * @param location   an object providing information about the module, line, and column where the node originated
     * @param properties bit-significant properties of the element node. If there are no relevant
     *                   properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */
    @Override
    public void startElement(NodeName elemName, SchemaType type, AttributeMap attributes, NamespaceMap namespaces, Location location, int properties) throws XPathException {
        super.startElement(elemName, type, attributes, namespaces, adjustLocation(location), properties);
        lastLocation = location.saveLocation();
    }

    /**
     * Find the best available location information to use for an event
     * @param loc the location reported for the event by the SAX parser
     * @return the saved location from the previous SAX event (this is normally the
     * position after the previous event, which is useful as the location before
     * the current event.) But if the previous SAX event was in a different
     * XML entity, then we use the current location reported by SAX, because
     * it is most important to get the base URI correct.
     */
    private Location adjustLocation(Location loc) {
        if (Objects.equals(loc.getSystemId(), lastLocation.getSystemId())) {
            return lastLocation;
        } else {
            return loc;
        }
    }

    /**
     * End of element
     */
    @Override
    public void endElement() throws XPathException {
        super.endElement();
        if (originalLocator == null) {
            originalLocator = (Location)getPipelineConfiguration().getComponent("LocalLocator");
        }
        if (originalLocator != null) {
            lastLocation = originalLocator.saveLocation();
        }
    }

    /**
     * Character data
     */
    @Override
    public void characters(UnicodeString chars, Location location, int properties) throws XPathException {
        super.characters(chars, adjustLocation(location), properties);
        lastLocation = location.saveLocation();
    }

    /**
     * Processing Instruction
     */
    @Override
    public void processingInstruction(String target, UnicodeString data, Location location, int properties) throws XPathException {
        super.processingInstruction(target, data, lastLocation, properties);
        lastLocation = location.saveLocation();
    }

    /**
     * Output a comment
     */
    @Override
    public void comment(UnicodeString chars, Location location, int properties) throws XPathException {
        super.comment(chars, lastLocation, properties);
        lastLocation = location.saveLocation();
    }
}

