////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.*;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ExternalObject;
import net.sf.saxon.value.ObjectValue;

import java.util.Map;

/**
 * This class is used for creating ItemType objects.
 * <p>The <code>ItemTypeFactory</code> class is thread-safe.</p>
 */
public class ItemTypeFactory {

    private Processor processor;

    /**
     * Create an ItemTypeFactory
     *
     * @param processor the processor used by this ItemTypeFactory. This must be supplied
     *                  in the case of user-defined types or types that reference element or attribute names;
     *                  for built-in types it can be omitted.
     */

    public ItemTypeFactory(Processor processor) {
        this.processor = processor;
    }

    /**
     * Get an item type representing an atomic type. This may be a built-in type in the
     * XML Schema namespace, or a user-defined atomic type. The resulting type will use
     * the conversion rules configured for the supplied <code>Processor</code>.
     * <p>It is undefined whether two calls supplying the same QName will return the same ItemType
     * object.</p>
     *
     * @param name the name of the built-in or user-defined atomic type required
     * @return an ItemType object representing this built-in  or user-defined atomic type
     * @throws SaxonApiException if the type name is not known, or if the type identified by the
     *                           name is not an atomic type.
     */

    public ItemType getAtomicType(QName name) throws SaxonApiException {
        return getAtomicType(name.getStructuredQName());
    }

    private ItemType getAtomicType(StructuredQName name) throws SaxonApiException {
        String uri = name.getURI();
        String local = name.getLocalPart();
        if (NamespaceConstant.SCHEMA.equals(uri)) {
            int fp = StandardNames.getFingerprint(uri, local);
            Configuration config = processor.getUnderlyingConfiguration();
            if (config.getXsdVersion() == Configuration.XSD10 && config.getXMLVersion() == Configuration.XML10) {
                return getBuiltInAtomicType(fp);
            } else {
                return ItemType.BuiltInAtomicItemType.makeVariant(
                        (ItemType.BuiltInAtomicItemType) getBuiltInAtomicType(fp), config.getConversionRules());
            }
        } else {
            Configuration config = processor.getUnderlyingConfiguration();
            SchemaType type = config.getSchemaType(new StructuredQName("", uri, local));
            if (type == null || !type.isAtomicType()) {
                throw new SaxonApiException("Unknown atomic type " + name.getClarkName());
            }
            return new ConstructedItemType((AtomicType) type, processor);
        }
    }


    private ItemType getBuiltInAtomicType(int fp) throws SaxonApiException {
        switch (fp) {
            case StandardNames.XS_ANY_ATOMIC_TYPE:
                return ItemType.ANY_ATOMIC_VALUE;

//            case StandardNames.XS_NUMERIC:
//                return ItemType.NUMERIC;

            case StandardNames.XS_STRING:
                return ItemType.STRING;

            case StandardNames.XS_BOOLEAN:
                return ItemType.BOOLEAN;

            case StandardNames.XS_DURATION:
                return ItemType.DURATION;

            case StandardNames.XS_DATE_TIME:
                return ItemType.DATE_TIME;

            case StandardNames.XS_DATE:
                return ItemType.DATE;

            case StandardNames.XS_TIME:
                return ItemType.TIME;

            case StandardNames.XS_G_YEAR_MONTH:
                return ItemType.G_YEAR_MONTH;

            case StandardNames.XS_G_MONTH:
                return ItemType.G_MONTH;

            case StandardNames.XS_G_MONTH_DAY:
                return ItemType.G_MONTH_DAY;

            case StandardNames.XS_G_YEAR:
                return ItemType.G_YEAR;

            case StandardNames.XS_G_DAY:
                return ItemType.G_DAY;

            case StandardNames.XS_HEX_BINARY:
                return ItemType.HEX_BINARY;

            case StandardNames.XS_BASE64_BINARY:
                return ItemType.BASE64_BINARY;

            case StandardNames.XS_ANY_URI:
                return ItemType.ANY_URI;

            case StandardNames.XS_QNAME:
                return ItemType.QNAME;

            case StandardNames.XS_NOTATION:
                return ItemType.NOTATION;

            case StandardNames.XS_UNTYPED_ATOMIC:
                return ItemType.UNTYPED_ATOMIC;

            case StandardNames.XS_DECIMAL:
                return ItemType.DECIMAL;

            case StandardNames.XS_FLOAT:
                return ItemType.FLOAT;

            case StandardNames.XS_DOUBLE:
                return ItemType.DOUBLE;

            case StandardNames.XS_INTEGER:
                return ItemType.INTEGER;

            case StandardNames.XS_NON_POSITIVE_INTEGER:
                return ItemType.NON_POSITIVE_INTEGER;

            case StandardNames.XS_NEGATIVE_INTEGER:
                return ItemType.NEGATIVE_INTEGER;

            case StandardNames.XS_LONG:
                return ItemType.LONG;

            case StandardNames.XS_INT:
                return ItemType.INT;

            case StandardNames.XS_SHORT:
                return ItemType.SHORT;

            case StandardNames.XS_BYTE:
                return ItemType.BYTE;

            case StandardNames.XS_NON_NEGATIVE_INTEGER:
                return ItemType.NON_NEGATIVE_INTEGER;

            case StandardNames.XS_POSITIVE_INTEGER:
                return ItemType.POSITIVE_INTEGER;

            case StandardNames.XS_UNSIGNED_LONG:
                return ItemType.UNSIGNED_LONG;

            case StandardNames.XS_UNSIGNED_INT:
                return ItemType.UNSIGNED_INT;

            case StandardNames.XS_UNSIGNED_SHORT:
                return ItemType.UNSIGNED_SHORT;

            case StandardNames.XS_UNSIGNED_BYTE:
                return ItemType.UNSIGNED_BYTE;

            case StandardNames.XS_YEAR_MONTH_DURATION:
                return ItemType.YEAR_MONTH_DURATION;

            case StandardNames.XS_DAY_TIME_DURATION:
                return ItemType.DAY_TIME_DURATION;

            case StandardNames.XS_NORMALIZED_STRING:
                return ItemType.NORMALIZED_STRING;

            case StandardNames.XS_TOKEN:
                return ItemType.TOKEN;

            case StandardNames.XS_LANGUAGE:
                return ItemType.LANGUAGE;

            case StandardNames.XS_NAME:
                return ItemType.NAME;

            case StandardNames.XS_NMTOKEN:
                return ItemType.NMTOKEN;

            case StandardNames.XS_NCNAME:
                return ItemType.NCNAME;

            case StandardNames.XS_ID:
                return ItemType.ID;

            case StandardNames.XS_IDREF:
                return ItemType.IDREF;

            case StandardNames.XS_ENTITY:
                return ItemType.ENTITY;

            case StandardNames.XS_DATE_TIME_STAMP:
                return ItemType.DATE_TIME_STAMP;

            default:
                throw new SaxonApiException("Unknown atomic type " +
                        processor.getUnderlyingConfiguration().getNamePool().getClarkName(fp));
        }
    }

    /**
     * Get an item type that matches any node of a specified kind.
     * <p>This corresponds to the XPath syntactic forms element(), attribute(),
     * document-node(), text(), comment(), processing-instruction(). It also provides
     * an option, not available in the XPath syntax, that matches namespace nodes.</p>
     * <p>It is undefined whether two calls supplying the same argument value will
     * return the same ItemType object.</p>
     *
     * @param kind the kind of node for which a NodeTest is required
     * @return an item type corresponding to the specified kind of node
     */

    public ItemType getNodeKindTest(XdmNodeKind kind) {
        switch (kind) {
            case DOCUMENT:
                return ItemType.DOCUMENT_NODE;
            case ELEMENT:
                return ItemType.ELEMENT_NODE;
            case ATTRIBUTE:
                return ItemType.ATTRIBUTE_NODE;
            case TEXT:
                return ItemType.TEXT_NODE;
            case COMMENT:
                return ItemType.COMMENT_NODE;
            case PROCESSING_INSTRUCTION:
                return ItemType.PROCESSING_INSTRUCTION_NODE;
            case NAMESPACE:
                return ItemType.NAMESPACE_NODE;
            default:
                throw new IllegalArgumentException("XdmNodeKind");
        }
    }

    /**
     * Get an item type that matches nodes of a specified kind with a specified name.
     * <p>This corresponds to the XPath syntactic forms element(name), attribute(name),
     * and processing-instruction(name). In the case of processing-instruction, the supplied
     * QName must have no namespace.</p>
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param kind the kind of nodes that match
     * @param name the name of the nodes that match
     * @return an ItemType that matches nodes of a given node kind with a given name
     * @throws IllegalArgumentException if the node kind is other than element, attribute, or
     *                                  processing instruction, or if the node kind is processing instruction and the name is in a namespace.
     */

    public ItemType getItemType(XdmNodeKind kind, QName name) {
        int k = kind.getNumber();
        if (k == Type.ELEMENT || k == Type.ATTRIBUTE || k == Type.PROCESSING_INSTRUCTION) {
            if (k == Type.PROCESSING_INSTRUCTION && name.getNamespaceURI().isEmpty()) {
                throw new IllegalArgumentException("The name of a processing instruction must not be in a namespace");
            }
            NameTest type = new NameTest(k,
                    name.getNamespaceURI(), name.getLocalName(), processor.getUnderlyingConfiguration().getNamePool());
            return new ConstructedItemType(type, processor);
        } else {
            throw new IllegalArgumentException("Node kind must be element, attribute, or processing-instruction");
        }
    }

    /**
     * Make an ItemType representing an element declaration in the schema. This is the
     * equivalent of the XPath syntax <code>schema-element(element-name)</code>
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param name the element name
     * @return the ItemType
     * @throws SaxonApiException if the schema does not contain a global element declaration
     *                           for the given name, or if the element declaration contains
     *                           dangling references to other schema components
     */

    public ItemType getSchemaElementTest(QName name) throws SaxonApiException {
        Configuration config = processor.getUnderlyingConfiguration();
        SchemaDeclaration decl = config.getElementDeclaration(name.getStructuredQName());
        if (decl == null) {
            throw new SaxonApiException("No global declaration found for element " + name.getClarkName());
        }
        try {
            NodeTest test = decl.makeSchemaNodeTest();
            return new ConstructedItemType(test, processor);
        } catch (MissingComponentException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Make an ItemType that tests an element name and/or schema type. This is the
     * equivalent of the XPath syntax <code>element(element-name, type)</code>
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param name       the element name, or null if there is no constraint on the name (equivalent to
     *                   specifying <code>element(*, type)</code>)
     * @param schemaType the name of the required schema type, or null if there is no constraint
     *                   on the type (equivalent to specifying <code>element(name)</code>)
     * @param nillable   if a nilled element is allowed to match the type (equivalent to specifying
     *                   "?" after the type name). The value is ignored if schemaType is null.
     * @return the constructed ItemType
     * @throws SaxonApiException if the schema does not contain a global element declaration
     *                           for the given name
     */

    public ItemType getElementTest(/*@Nullable*/ QName name, QName schemaType, boolean nillable) throws SaxonApiException {
        Configuration config = processor.getUnderlyingConfiguration();
        NameTest nameTest = null;
        ContentTypeTest contentTest = null;
        if (name != null) {
            int elementFP = config.getNamePool().allocateFingerprint(name.getNamespaceURI(), name.getLocalName());
            nameTest = new NameTest(Type.ELEMENT, elementFP, config.getNamePool());
        }
        if (schemaType != null) {
            SchemaType type = config.getSchemaType(
                new StructuredQName("", schemaType.getNamespaceURI(), schemaType.getLocalName()));
            if (type == null) {
                throw new SaxonApiException("Unknown schema type " + schemaType.getClarkName());
            }
            contentTest = new ContentTypeTest(Type.ELEMENT, type, config, nillable);
        }
        if (contentTest == null) {
            if (nameTest == null) {
                return getNodeKindTest(XdmNodeKind.ELEMENT);
            } else {
                return new ConstructedItemType(nameTest, processor);
            }
        } else {
            if (nameTest == null) {
                return new ConstructedItemType(contentTest, processor);
            } else {
                CombinedNodeTest combo = new CombinedNodeTest(
                        nameTest,
                        Token.INTERSECT,
                        contentTest);
                return new ConstructedItemType(combo, processor);
            }
        }
    }

    /**
     * Get an ItemType representing an attribute declaration in the schema. This is the
     * equivalent of the XPath syntax <code>schema-attribute(attribute-name)</code>
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param name the attribute name
     * @return the ItemType
     * @throws SaxonApiException if the schema does not contain a global attribute declaration
     *                           for the given name, or if the attribute declaration contains
     *                           dangling references to other schema components
     */

    public ItemType getSchemaAttributeTest(QName name) throws SaxonApiException {
        Configuration config = processor.getUnderlyingConfiguration();
        StructuredQName nn = new StructuredQName("", name.getNamespaceURI(), name.getLocalName());
        SchemaDeclaration decl = config.getAttributeDeclaration(nn);
        if (decl == null) {
            throw new SaxonApiException("No global declaration found for attribute " + name.getClarkName());
        }
        try {
            NodeTest test = decl.makeSchemaNodeTest();
            return new ConstructedItemType(test, processor);
        } catch (MissingComponentException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get an ItemType that tests an attribute name and/or schema type. This is the
     * equivalent of the XPath syntax <code>element(element-name, type)</code>
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param name       the element name, or null if there is no constraint on the name (equivalent to
     *                   specifying <code>element(*, type)</code>)
     * @param schemaType the name of the required schema type, or null of there is no constraint
     *                   on the type (equivalent to specifying <code>element(name)</code>)
     * @return the constructed ItemType
     * @throws SaxonApiException if the schema does not contain a global element declaration
     *                           for the given name
     */

    public ItemType getAttributeTest(QName name, QName schemaType) throws SaxonApiException {
        NameTest nameTest = null;
        ContentTypeTest contentTest = null;
        Configuration config = processor.getUnderlyingConfiguration();
        if (name != null) {
            int attributeFP = config.getNamePool().allocateFingerprint(name.getNamespaceURI(), name.getLocalName());
            nameTest = new NameTest(Type.ATTRIBUTE, attributeFP, config.getNamePool());
        }
        if (schemaType != null) {
            SchemaType type = config.getSchemaType(
                new StructuredQName("", schemaType.getNamespaceURI(), schemaType.getLocalName()));
            if (type == null) {
                throw new SaxonApiException("Unknown schema type " + schemaType.getClarkName());
            }
            contentTest = new ContentTypeTest(Type.ATTRIBUTE, type, config, false);
        }
        if (contentTest == null) {
            if (nameTest == null) {
                return getNodeKindTest(XdmNodeKind.ATTRIBUTE);
            } else {
                return new ConstructedItemType(nameTest, processor);
            }
        } else {
            if (nameTest == null) {
                return new ConstructedItemType(contentTest, processor);
            } else {
                CombinedNodeTest combo = new CombinedNodeTest(
                        nameTest,
                        Token.INTERSECT,
                        contentTest);
                return new ConstructedItemType(combo, processor);
            }
        }
    }

    /**
     * Make an ItemType representing a document-node() test with an embedded element test.
     * This reflects the XPath syntax <code>document-node(element(N, T))</code> or
     * <code>document-node(schema-element(N))</code>.
     * <p>It is undefined whether two calls supplying the same argument values will
     * return the same ItemType object.</p>
     *
     * @param elementTest the elementTest. An IllegalArgumentException is thrown if the supplied
     *                    ItemTest is not an elementTest or schemaElementTest.
     * @return a new ItemType representing the document test
     */

    public ItemType getDocumentTest(ItemType elementTest) {
        net.sf.saxon.type.ItemType test = elementTest.getUnderlyingItemType();
        if (test.getPrimitiveType() != Type.ELEMENT) {
            throw new IllegalArgumentException("Supplied itemType is not an element test");
        }
        DocumentNodeTest docTest = new DocumentNodeTest((NodeTest) test);
        return new ConstructedItemType(docTest, processor);
    }

    /**
     * Get an ItemType representing the type of a Java object when used as an external object
     * for use in conjunction with calls on extension/external functions.
     *
     * @param externalClass a Java class
     * @return the ItemType representing the type of external objects of this class
     */

    public ItemType getExternalObjectType(Class externalClass) {
        JavaExternalObjectType type = processor.getUnderlyingConfiguration().getJavaExternalObjectType(externalClass);
        return new ConstructedItemType(type, processor);
    }

    /**
     * Factory method to construct an "external object". This is an XDM value that wraps a Java
     * object. Such values can be passed as parameters to stylesheets or queries, for use in conjunction
     * with external (extension) functions.
     * <p>Changed in Saxon 9.5 to return XdmItem rather than XdmAtomicValue, since wrapped
     * external objects are now modelled as a separate type of item, rather than as an atomic value.</p>
     *
     * @param object the value to be wrapped as an external object. Must not be null.
     * @return the object, wrapped as an XdmItem
     */

    public XdmItem getExternalObject(Object object) {
        return (XdmItem) XdmItem.wrap(new ObjectValue<>(object));
    }

    /**
     * Obtain a map type, that is a type for XDM maps with a given key type and value type
     * @param keyType the type of the keys in the map
     * @param valueType the type of the values in the map
     * @return the required map type
     */

    public ItemType getMapType(ItemType keyType, SequenceType valueType) {
        if (!(keyType.getUnderlyingItemType() instanceof AtomicType)) {
            throw new IllegalArgumentException("Map key must be atomic");
        }
        return new ConstructedItemType(
                new MapType((AtomicType)keyType.getUnderlyingItemType(), valueType.getUnderlyingSequenceType()), processor);
    }

    /**
     * Obtain an array type, that is a type for XDM arrays with a given member type
     *
     * @param memberType the type of the members in the array
     * @return the required array type
     */

    public ItemType getArrayType(SequenceType memberType) {
        return new ConstructedItemType(new ArrayItemType(memberType.getUnderlyingSequenceType()), processor);
    }

    /**
     * Factory method to construct a map item from a Java map
     * @param map the input map. The contents of this map are shallow-copied, so subsequent changes to the
     * Java map do not affect the XDM map. The keys in the map must be convertible to atomic values,
     * and the values in the map must be convertible to XDM values.
     * @return an XDM item representing the contents of the map. This is of type function item
     * because XDM maps are functions (from the key to the value).
     * @throws SaxonApiException if there are values that cannot be converted.
     * @since 9.6
     */

    public XdmMap newMap(Map<?, ?> map) throws SaxonApiException {
        try {
            return XdmMap.makeMap(map);
        } catch (IllegalArgumentException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get an ItemType representing the type of a supplied XdmItem.
     *
     * <p>If the supplied item is
     * an atomic value, the returned ItemType will reflect the most specific atomic type of the
     * item.</p>
     *
     * <p>If the supplied item is a node, the returned item type will reflect the node kind,
     * and if the node has a name, then its name. It will not reflect the type annotation.</p>
     *
     * <p>For a map, the result is {@link net.sf.saxon.s9api.ItemType#ANY_MAP}. For an array, the result is
     * {@link net.sf.saxon.s9api.ItemType#ANY_ARRAY}. For any other function, it is {@link net.sf.saxon.s9api.ItemType#ANY_FUNCTION}.</p>
     *
     * <p>If the item is an external object, a suitable item type object is constructed.</p>
     *
     * <p>Future versions of Saxon may return a more precise type.</p>
     *
     * @param item the supplied item whose type is required
     * @return the type of the supplied item
     */

    public ItemType getItemType(XdmItem item) {
        if (item.isAtomicValue()) {
            AtomicValue value = (AtomicValue) item.getUnderlyingValue();
            AtomicType type = value.getItemType();
            return new ConstructedItemType(type, processor);
        } else if (item.isNode()) {
            NodeInfo node = (NodeInfo) item.getUnderlyingValue();
            int kind = node.getNodeKind();
            if (node.getLocalPart().isEmpty()) {
                return new ConstructedItemType(NodeKindTest.makeNodeKindTest(kind), processor);
            } else {
                return new ConstructedItemType(new SameNameTest(node), processor);
            }
        } else {
            Item it = item.getUnderlyingValue();
            if (it instanceof MapItem) {
                return ItemType.ANY_MAP;
            } else if (it instanceof ArrayItem) {
                return ItemType.ANY_ARRAY;
            } else if (it instanceof ExternalObject) {
                return new ConstructedItemType(ExternalObjectType.THE_INSTANCE, processor);
            } else {
                return ItemType.ANY_FUNCTION;
            }
        }
    }

    /**
     * Obtain a s9api {@code ItemType} corresponding to an underlying instance of
     * {@link net.sf.saxon.type.ItemType}
     * @param it the supplied instance of {@link net.sf.saxon.type.ItemType}
     * @return a corresponding instance of {@link ItemType}
     * @since 10.0
     */

    public ItemType exposeItemType(net.sf.saxon.type.ItemType it) {
        return new ConstructedItemType(it, processor);
    }

}

