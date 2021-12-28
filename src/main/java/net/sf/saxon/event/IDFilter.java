////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

import java.util.HashSet;


/**
 * IDFilter is a ProxyReceiver that extracts the subtree of a document rooted at the
 * element with a given ID value. Namespace declarations outside this subtree are
 * treated as if they were present on the identified element.
 *
 * <p>Note, this class only looks for ID attributes, not for ID elements.</p>
 */

public class IDFilter extends ProxyReceiver {

    private String requiredId;
    private int activeDepth = 0;

    private boolean matched = false;
    private HashSet<SimpleType> nonIDs;

    public IDFilter(Receiver next, String id) {
        // System.err.println("IDFilter, looking for " + id);
        super(next);
        this.requiredId = id;
    }

    /**
     * startElement
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties)
            throws XPathException {
        matched = false;
        if (activeDepth == 0) {
            for (AttributeInfo att : attributes) {
                if ((att.getNodeName().equals(StandardNames.XML_ID_NAME)) ||
                        ReceiverOption.contains(att.getProperties(), ReceiverOption.IS_ID)) {
                    if (att.getValue().equals(requiredId)) {
                        matched = true;
                    }
                }
            }
            if (matched) {
                activeDepth = 1;
                super.startElement(elemName, type, attributes, namespaces, location, properties);  // this remembers the details
            }
        } else {
            activeDepth++;
            super.startElement(elemName, type, attributes, namespaces, location, properties);  // this remembers the details
        }
    }

    /**
     * endElement:
     */

    @Override
    public void endElement() throws XPathException {
        if (activeDepth > 0) {
            nextReceiver.endElement();
            activeDepth--;
        }
    }

    /**
     * Character data
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (activeDepth > 0) {
            super.characters(chars, locationId, properties);
        }
    }

    /**
     * Processing Instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (activeDepth > 0) {
            super.processingInstruction(target, data, locationId, properties);
        }
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (activeDepth > 0) {
            super.comment(chars, locationId, properties);
        }
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
        return true;
    }

//    /**
//     * Test whether a type annotation code represents the type xs:ID or one of its subtypes
//     *
//     * @param typeCode the fingerprint of the type name
//     * @return true if the type is an ID type; false if it is not (or if the type code does not
//     *         resolve to a known type)
//     */
//
//    private boolean isIDCode(SimpleType typeCode) {
//        if (typeCode == BuiltInAtomicType.ID) {
//            return true;
//        }
//        if (typeCode instanceof BuiltInAtomicType) {
//            return false; // No other built-in type is an ID
//        }
//
//        if (nonIDs == null) {
//            nonIDs = new HashSet<>(20);
//        }
//        if (nonIDs.contains(typeCode)) {
//            return false;
//        }
//        if (typeCode.isAtomicType()) {
//            if (getConfiguration().getTypeHierarchy().isSubType((AtomicType) typeCode, BuiltInAtomicType.ID)) {
//                return true;
//            } else {
//                nonIDs.add(typeCode);
//                return false;
//            }
//        } else {
//            return false;
//        }
//    }

}

