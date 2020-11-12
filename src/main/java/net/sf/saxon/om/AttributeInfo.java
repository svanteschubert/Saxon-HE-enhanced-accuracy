////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.om;

import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.type.MissingComponentException;
import net.sf.saxon.type.SimpleType;

/**
 * This class contains immutable information about an attribute. An {@code AttributeInfo} is not a node:
 * it has no identity and no navigation capability to the parent element or to any other nodes on the tree.
 */

public class AttributeInfo {

    private NodeName nodeName;
    private SimpleType type;
    private String value;
    private Location location;
    private int properties;

    /**
     * Create an immutable AttributeInfo object
     * @param nodeName the name of the attribute
     * @param type the type annotation of the attribute
     * @param value the string value of the attribute
     * @param location the location of the attribute within a source document (if known)
     * @param properties additional bitwise properties of the attribute, enumerated in class
     *                   {@link ReceiverOption}
     */

    public AttributeInfo(NodeName nodeName,
                         SimpleType type,
                         String value,
                         Location location,
                         int properties) {
        this.nodeName = nodeName;
        this.type = type;
        this.value = value;
        this.location = location;
        this.properties = properties;
    }

    /**
     * Get the name of the attribute
     * @return the name of the attribute
     */

    public NodeName getNodeName() {
        return nodeName;
    }

    /**
     * Get the type annotation of the attribute
     * @return the type annotation
     */

    public SimpleType getType() {
        return type;
    }

    /**
     * Get the string value of the attribute
     * @return the string value
     */

    public String getValue() {
        return value;
    }

    /**
     * Get the location of the attribute, if known
     * @return the location of the attribute (or {@link Loc#NONE} if unknown)
     */

    public Location getLocation() {
        return location;
    }

    /**
     * Get properties of the attribute
     * @return properties of the attribute, identified by constants in {@link ReceiverOption}
     */

    public int getProperties() {
        return properties;
    }

    /**
     * Ask whether this is an ID attribute
     * @return true if the name is xml:id, or the properties include the IS_ID property, or the
     * type annotation is an ID type.
     */

    public boolean isId() {
        try {
            return StandardNames.XML_ID_NAME.equals(nodeName) ||
                    ReceiverOption.contains(getProperties(), ReceiverOption.IS_ID) ||
                    getType().isIdType();
        } catch (MissingComponentException e) {
            return false;
        }
    }

    public AttributeInfo withNodeName(NodeName newName) {
        return new AttributeInfo(newName, type, value, location, properties);
    }

    /**
     * AttributeInfo.Deleted is a subclass used to mark a deleted attribute (in XQuery Update)
     */

    public static class Deleted extends AttributeInfo {
        public Deleted(AttributeInfo att) {
            super(att.getNodeName(), att.getType(), att.getValue(), att.getLocation(), att.getProperties());
        }
    }

}

