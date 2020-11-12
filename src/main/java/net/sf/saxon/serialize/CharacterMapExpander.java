////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import java.util.ArrayList;
import java.util.List;

/**
 * CharacterMapExpander: This ProxyReceiver expands characters occurring in a character map,
 * as specified by the XSLT 2.0 xsl:character-map declaration
 *
 * @author Michael Kay
 */


public class CharacterMapExpander extends ProxyReceiver {

    private CharacterMap charMap;
    private boolean useNullMarkers = true;

    public CharacterMapExpander(Receiver next) {
        super(next);
    }

    /**
     * Set the character map to be used by this CharacterMapExpander.
     * They will have been merged into a single character map if there is more than one.
     * @param map the character map
     */

    public void setCharacterMap(CharacterMap map) {
        charMap = map;
    }

    /**
     * Get the character map used by this CharacterMapExpander
     * @return the character map
     */

    public CharacterMap getCharacterMap() {
        return charMap;
    }

    /**
     * Indicate whether the result of character mapping should be marked using NUL
     * characters to prevent subsequent XML or HTML character escaping. The default value
     * is true (used for the XML and HTML output methods); the value false is used by the text
     * output method.
     */

    public void setUseNullMarkers(boolean use) {
        useNullMarkers = use;
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
        List<AttributeInfo> atts2 = new ArrayList<>(attributes.size());
        for (AttributeInfo att : attributes) {
            String oldValue = att.getValue();
            if (!ReceiverOption.contains(att.getProperties(), ReceiverOption.DISABLE_CHARACTER_MAPS)) {
                CharSequence mapped = charMap.map(oldValue, useNullMarkers);
                if (mapped != oldValue) {
                    // mapping was done
                    int p2 = (att.getProperties() | ReceiverOption.USE_NULL_MARKERS)
                            & ~ReceiverOption.NO_SPECIAL_CHARS;
                    atts2.add(new AttributeInfo(
                            att.getNodeName(),
                            att.getType(),
                            mapped.toString(),
                            att.getLocation(),
                            p2));
                } else {
                    atts2.add(att);
                }
            } else {
                atts2.add(att);
            }

        }
        nextReceiver.startElement(elemName, type, AttributeMap.fromList(atts2), namespaces, location, properties);
    }

    /**
     * Output character data
     */

    @Override
    public void characters(/*@NotNull*/ CharSequence chars, Location locationId, int properties) throws XPathException {

        if (!ReceiverOption.contains(properties, ReceiverOption.DISABLE_CHARACTER_MAPS)) {
            CharSequence mapped = charMap.map(chars, useNullMarkers);
            if (mapped != chars) {
                properties = (properties | ReceiverOption.USE_NULL_MARKERS)
                         &~ ReceiverOption.NO_SPECIAL_CHARS;
            }
            nextReceiver.characters(mapped, locationId, properties);
        } else {
            // if the user requests disable-output-escaping, this overrides the character
            // mapping
            nextReceiver.characters(chars, locationId, properties);
        }

    }


}

