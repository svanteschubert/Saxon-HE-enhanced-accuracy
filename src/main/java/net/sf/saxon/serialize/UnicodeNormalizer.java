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
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.serialize.codenorm.Normalizer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;

/**
 * UnicodeNormalizer: This ProxyReceiver performs unicode normalization on the contents
 * of attribute and text nodes.
 */


public class UnicodeNormalizer extends ProxyReceiver {

    private Normalizer normalizer;

    public UnicodeNormalizer(String form, Receiver next) throws XPathException {
        super(next);
        byte fb;
        switch (form) {
            case "NFC":
                fb = Normalizer.C;
                break;
            case "NFD":
                fb = Normalizer.D;
                break;
            case "NFKC":
                fb = Normalizer.KC;
                break;
            case "NFKD":
                fb = Normalizer.KD;
                break;
            default:
                XPathException err = new XPathException("Unknown normalization form " + form);
                err.setErrorCode("SESU0011");
                throw err;
        }
        normalizer = Normalizer.make(fb, getConfiguration());
    }

    /**
     * Get the underlying normalizer
     * @return the underlying Normalizer
     */

    public Normalizer getNormalizer() {
        return normalizer;
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
        AttributeMap am2 = attributes.apply(attInfo -> {
            String newValue = normalize(attInfo.getValue(),
                                        ReceiverOption.contains(attInfo.getProperties(), ReceiverOption.USE_NULL_MARKERS)).toString();

            return new AttributeInfo(
                        attInfo.getNodeName(),
                        attInfo.getType(),
                        newValue,
                        attInfo.getLocation(),
                        attInfo.getProperties());
        });
        nextReceiver.startElement(elemName, type, am2, namespaces, location, properties);
    }

    /**
     * Output character data
     */

    @Override
    public void characters(/*@NotNull*/ CharSequence chars, Location locationId, int properties) throws XPathException {
        if (Whitespace.isWhite(chars)) {
            nextReceiver.characters(chars, locationId, properties);
        } else {
            nextReceiver.characters(normalize(chars, ReceiverOption.contains(properties, ReceiverOption.USE_NULL_MARKERS)),
                                    locationId, properties);
        }
    }

    public CharSequence normalize(CharSequence in, boolean containsNullMarkers) {
        if (containsNullMarkers) {
            FastStringBuffer out = new FastStringBuffer(in.length());
            String s = in.toString();
            int start = 0;
            int nextNull = s.indexOf((char)0);
            while (nextNull >= 0) {
                out.cat(normalizer.normalize(s.substring(start, nextNull)));
                out.cat((char) 0);
                start = nextNull + 1;
                nextNull = s.indexOf((char) 0, start);
                out.append(s.substring(start, nextNull));
                out.cat((char) 0);
                start = nextNull + 1;
                nextNull = s.indexOf((char) 0, start);
            }
            out.cat(normalizer.normalize(s.substring(start)));
            return out.condense();
        } else {
            return normalizer.normalize(in);
        }
    }

}

