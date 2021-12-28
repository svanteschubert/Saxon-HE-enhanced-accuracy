////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.util;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SimpleType;
import org.xml.sax.Attributes;

import java.util.Arrays;


/**
 * AttributeCollectionImpl is an implementation of the SAX2 interface Attributes.
 * <p>As well as providing the information required by the SAX2 interface, an
 * AttributeCollection can hold type information (as needed to support the JAXP 1.3
 * {@link javax.xml.validation.ValidatorHandler} interface), and location information
 * for debugging. The location information is used in the case of attributes on a result
 * tree to identify the location in the query or stylesheet from which they were
 * generated.</p>
 */

public class AttributeCollectionImpl implements Attributes {

    private Configuration config;
    // Following fields can be null ONLY if used==0. We avoid allocating the arrays for the common
    // case of an empty attribute collection.
    private NodeName[] names = null;
    private String[] values = null;
    private Location[] locations = null;
    private int[] props = null;
    private int used = 0;
    // the types array can be null even if used>0; this indicates that all attributes are untyped
    private SimpleType[] types = null;


    public AttributeCollectionImpl(Configuration config, int initialSize) {
        this.config = config;
        names = new NodeName[initialSize];
        values = new String[initialSize];
        props = new int[initialSize];
        locations = new Location[initialSize];
        used = 0;
    }


    /**
     * Add an attribute to an attribute list. The parameters correspond
     * to the parameters of the {@link Outputter#attribute(NodeName, SimpleType, CharSequence, Location, int)}
     * method. There is no check that the name of the attribute is distinct from other attributes
     * already in the collection: this check must be made by the caller.
     *
     * @param nodeName   Object representing the attribute name.
     * @param type       The attribute type
     * @param value      The attribute value (must not be null)
     * @param locationId Identifies the attribute location.
     * @param properties Attribute properties
     */

    public void addAttribute(NodeName nodeName, SimpleType type, String value, Location locationId, int properties) {
        if (values == null) {
            names = new NodeName[5];
            values = new String[5];
            props = new int[5];
            locations = new Location[5];
            if (!type.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                types = new SimpleType[5];
            }
            used = 0;
        }
        if (values.length == used) {
            int newsize = used == 0 ? 5 : used * 2;
            names = Arrays.copyOf(names, newsize);
            values = Arrays.copyOf(values, newsize);
            props = Arrays.copyOf(props, newsize);
            locations = Arrays.copyOf(locations, newsize);
            if (types != null) {
                types = Arrays.copyOf(types, newsize);
            }
        }
        int n = used;
        names[n] = nodeName;
        props[n] = properties;
        locations[n] = locationId.saveLocation();
        setTypeAnnotation(n, type);
        values[used++] = value;
    }

    /**
     * Set (overwrite) an attribute in the attribute list. The parameters correspond
     * to the parameters of the {@link Outputter#attribute(NodeName, SimpleType, CharSequence, Location, int)}
     * method.
     *  @param index      Identifies the entry to be replaced. Must be in range (nasty things happen if not)
     * @param nodeName   representing the attribute name.
     * @param type       The attribute type code
     * @param value      The attribute value (must not be null)
     * @param locationId Identifies the attribtue location.
     * @param properties Attribute properties
     */

    public void setAttribute(int index, NodeName nodeName, SimpleType type, String value, Location locationId, int properties) {
        names[index] = nodeName;
        props[index] = properties;
        locations[index] = locationId;
        setTypeAnnotation(index, type);
        values[index] = value;
    }

    /**
     * Return the number of attributes in the list.
     *
     * @return The number of attributes that have been created in this attribute collection. This is the number
     *         of slots used in the list, including any slots allocated to attributes that have since been deleted.
     *         Such slots are not reused, to preserve attribute identity.
     */

    @Override
    public int getLength() {
        return values == null ? 0 : used;
    }


    /**
     * Get the type of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The type annotation
     */

    public SimpleType getTypeAnnotation(int index) {
        if (types == null) {
            return BuiltInAtomicType.UNTYPED_ATOMIC;
        }
        if (index < 0 || index >= used) {
            return BuiltInAtomicType.UNTYPED_ATOMIC;
        }

        return types[index];
    }

    /**
     * Get the location of an attribute (by position)
     *
     * @param index The position of the attribute in the list.
     * @return The location of the attribute. This can be used to obtain the
     *         actual system identifier and line number of the relevant location
     */

    public Location getLocation(int index) {
        if (locations == null) {
            return Loc.NONE;
        }
        if (index < 0 || index >= used) {
            return Loc.NONE;
        }

        return locations[index];
    }


    /**
     * Get the properties of an attribute (by position)
     *
     * @param index The position of the attribute in the list.
     * @return The properties of the attribute. This is a set
     *         of bit-settings defined in class {@link net.sf.saxon.event.ReceiverOption}. The
     *         most interesting of these is {{@link net.sf.saxon.event.ReceiverOption#DEFAULTED_VALUE},
     *         which indicates an attribute that was added to an element as a result of schema validation.
     */

    public int getProperties(int index) {
        if (props == null) {
            return ReceiverOption.NONE;
        }
        if (index < 0 || index >= used) {
            return ReceiverOption.NONE;
        }

        return props[index];
    }

    /**
     * Get the lexical QName of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The lexical QName of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    /*@Nullable*/
    @Override
    public String getQName(int index) {
        if (names == null) {
            return null;
        }
        if (index < 0 || index >= used) {
            return null;
        }
        return names[index].getDisplayName();
    }

    /**
     * Get the local name of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The local name of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    /*@Nullable*/
    @Override
    public String getLocalName(int index) {
        if (names == null) {
            return null;
        }
        if (index < 0 || index >= used) {
            return null;
        }
        return names[index].getLocalPart();
    }

    /**
     * Get the namespace URI of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The local name of the attribute as a string, or null if there
     *         is no attribute at that position.
     */

    /*@Nullable*/
    @Override
    public String getURI(int index) {
        if (names == null) {
            return null;
        }
        if (index < 0 || index >= used) {
            return null;
        }
        return names[index].getURI();
    }


    /**
     * Get the type of an attribute (by position). This is a SAX2 method,
     * so it gets the type name as a DTD attribute type, mapped from the
     * schema type code.
     *
     * @param index The position of the attribute in the list.
     * @return The attribute type as a string ("NMTOKEN" for an
     *         enumeration, and "CDATA" if no declaration was
     *         read), or null if there is no attribute at
     *         that position.
     */

    /*@NotNull*/
    @Override
    public String getType(int index) {
        int typeCode = getTypeAnnotation(index).getFingerprint();
        switch (typeCode) {
            case StandardNames.XS_ID:
                return "ID";
            case StandardNames.XS_IDREF:
                return "IDREF";
            case StandardNames.XS_NMTOKEN:
                return "NMTOKEN";
            case StandardNames.XS_ENTITY:
                return "ENTITY";
            case StandardNames.XS_IDREFS:
                return "IDREFS";
            case StandardNames.XS_NMTOKENS:
                return "NMTOKENS";
            case StandardNames.XS_ENTITIES:
                return "ENTITIES";
            default:
                return "CDATA";
        }
    }

    /**
     * Get the type of an attribute (by name).
     *
     * @param uri       The namespace uri of the attribute.
     * @param localname The local name of the attribute.
     * @return The index position of the attribute
     */

    /*@Nullable*/
    @Override
    public String getType(String uri, String localname) {
        int index = findByName(uri, localname);
        return (index < 0 ? null : getType(index));
    }

    /**
     * Get the value of an attribute (by position).
     *
     * @param index The position of the attribute in the list.
     * @return The attribute value as a string, or null if
     *         there is no attribute at that position.
     */

    /*@Nullable*/
    @Override
    public String getValue(int index) {
        if (values == null) {
            return null;
        }
        if (index < 0 || index >= used) {
            return null;
        }
        return values[index];
    }

    /**
     * Get the value of an attribute (by name).
     *
     * @param uri       The namespace uri of the attribute.
     * @param localname The local name of the attribute.
     * @return The index position of the attribute
     */

    /*@Nullable*/
    @Override
    public String getValue(String uri, String localname) {
        int index = findByName(uri, localname);
        return (index < 0 ? null : getValue(index));
    }

    /**
     * Get the index of an attribute, from its lexical QName
     *
     * @param qname The lexical QName of the attribute. The prefix must match.
     * @return The index position of the attribute
     */

    @Override
    public int getIndex(/*@NotNull*/ String qname) {
        if (names == null) {
            return -1;
        }
        if (qname.indexOf(':') < 0) {
            return findByName("", qname);
        }
        // Searching using prefix+localname is not recommended, but SAX allows it...
        String[] parts;
        try {
            parts = NameChecker.getQNameParts(qname);
        } catch (QNameException err) {
            return -1;
        }
        String prefix = parts[0];
        if (prefix.isEmpty()) {
            return findByName("", qname);
        } else {
            String localName = parts[1];
            for (int i = 0; i < used; i++) {
                if (names[i] != null) {
                    String lname = names[i].getLocalPart();
                    String ppref = names[i].getPrefix();
                    if (localName.equals(lname) && prefix.equals(ppref)) {
                        return i;
                    }
                }
            }
            return -1;
        }
    }

    /**
     * Get the index of an attribute (by name).
     *
     * @param uri       The namespace uri of the attribute.
     * @param localname The local name of the attribute.
     * @return The index position of the attribute, or -1 if absent
     */

    @Override
    public int getIndex(String uri, String localname) {
        return findByName(uri, localname);
    }

    /**
     * Get the type of an attribute (by lexical QName).
     *
     * @param name The lexical QName of the attribute.
     * @return The attribute type as a string (e.g. "NMTOKEN", or
     *         "CDATA" if no declaration was read).
     */

    /*@NotNull*/
    @Override
    public String getType(/*@NotNull*/ String name) {
        int index = getIndex(name);
        return getType(index);
    }


    /**
     * Get the value of an attribute (by lexnical QName).
     *
     * @param name The attribute name (a lexical QName).
     *             The prefix must match the prefix originally used. This method is defined in SAX, but is
     *             not recommended except where the prefix is null.
     */

    /*@Nullable*/
    @Override
    public String getValue(/*@NotNull*/ String name) {
        int index = getIndex(name);
        return getValue(index);
    }

    /**
     * Find an attribute by expanded name
     *
     * @param uri       the namespace uri
     * @param localName the local name
     * @return the index of the attribute, or -1 if absent
     */

    private int findByName(String uri, String localName) {
        if (names == null || config == null) {
            return -1;        // indicates an empty attribute set
        }
        for (int i = 0; i < used; i++) {
            if (names[i] != null && names[i].hasURI(uri) && localName.equals(names[i].getLocalPart())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Set the type annotation of an attribute
     *
     * @param index the index position of the attribute node
     * @param type  the new type for the attribute
     */

    public void setTypeAnnotation(int index, SimpleType type) {
        if (type.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            if (types != null) {
                types[index] = type;
            }
        } else {
            if (types == null) {
                types = new SimpleType[names.length];
                Arrays.fill(types, BuiltInAtomicType.UNTYPED_ATOMIC);
                types[index] = type;
            } else {
                types[index] = type;
            }
        }
    }

    /**
     * Add an attribute to the collection, replacing any existing attribute
     * with the same name
     *
     * @param attribute the attribute to be added to the collection
     */

    public void setAttribute(AttributeInfo attribute) {
        NodeName name = attribute.getNodeName();
        int index = getIndex(name.getURI(), name.getLocalPart());
        if (index < 0) {
            addAttribute(name, attribute.getType(), attribute.getValue(),
                         attribute.getLocation(), attribute.getProperties());
        } else {
            setAttribute(index, name, attribute.getType(), attribute.getValue(),
                         attribute.getLocation(), attribute.getProperties());
        }
    }

}

