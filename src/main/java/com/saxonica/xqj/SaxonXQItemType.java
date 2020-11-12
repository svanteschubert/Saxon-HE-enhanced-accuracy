////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.DocumentNodeTest;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.type.*;

import javax.xml.namespace.QName;
import javax.xml.xquery.XQException;
import javax.xml.xquery.XQItemType;
import javax.xml.xquery.XQSequenceType;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Saxon implementation of the XQJ XQItemType interface
 */
public class SaxonXQItemType implements XQItemType {

    /*@NotNull*/ private ItemType itemType;
    /*@NotNull*/ private Configuration config;

    protected SaxonXQItemType(/*@NotNull*/ ItemType itemType, /*@NotNull*/ Configuration config) {
        this.itemType = itemType;
        this.config = config;
    }

    protected SaxonXQItemType(/*@NotNull*/ NodeInfo node) {
        config = node.getConfiguration();
        itemType = Type.getItemType(node, config.getTypeHierarchy());
    }

    @Override
    public int getBaseType() throws XQException {
        if (itemType instanceof AtomicType) {
            AtomicType at = (AtomicType) itemType;
            while (!at.isBuiltInType()) {
                at = (AtomicType) at.getBaseType();
            }
            return SaxonXQDataFactory.mapSaxonTypeToXQJ(at.getFingerprint());
        } else if (itemType instanceof NodeTest) {
            NodeTest it = (NodeTest) itemType;
            if (it instanceof DocumentNodeTest) {
                it = ((DocumentNodeTest) it).getElementTest();
            }
            if (it.getUType().overlaps(UType.TEXT.union(UType.DOCUMENT).union(UType.COMMENT).union(UType.PI).union(UType.NAMESPACE))) {
                throw new XQException("Wrong node kind for getBaseType()");
            }
            SchemaType contentType = it.getContentType();
            if (contentType.isAtomicType()) {
                AtomicType at = (AtomicType) contentType;
                while (!at.isBuiltInType()) {
                    at = (AtomicType) at.getBaseType();
                }
                return SaxonXQDataFactory.mapSaxonTypeToXQJ(at.getFingerprint());
            } else if (contentType.isSimpleType()) {
                if (((SimpleType) contentType).isListType()) {
                    int fp = contentType.getFingerprint();
                    if (fp == StandardNames.XS_NMTOKENS) {
                        return XQBASETYPE_NMTOKENS;
                    } else if (fp == StandardNames.XS_ENTITIES) {
                        return XQBASETYPE_ENTITIES;
                    } else if (fp == StandardNames.XS_IDREFS) {
                        return XQBASETYPE_IDREFS;
                    }
                }
                return XQBASETYPE_ANYSIMPLETYPE;
            } else if (contentType == Untyped.getInstance()) {
                return XQBASETYPE_UNTYPED;
            } else {
                return XQBASETYPE_ANYTYPE;
            }

        } else {
            throw new XQException("Wrong item type for getBaseType()");
        }
    }

    @Override
    public int getItemKind() {
        if (itemType instanceof AtomicType) {
            return XQITEMKIND_ATOMIC;
        } else if (itemType instanceof NodeTest) {
            if (itemType instanceof DocumentNodeTest) {
                return XQITEMKIND_DOCUMENT_ELEMENT;
            }
            int x = itemType.getPrimitiveType();
            switch (x) {
                case Type.DOCUMENT:
                    return XQITEMKIND_DOCUMENT;
                case Type.ELEMENT:
                    return XQITEMKIND_ELEMENT;
                case Type.ATTRIBUTE:
                    return XQITEMKIND_ATTRIBUTE;
                case Type.TEXT:
                    return XQITEMKIND_TEXT;
                case Type.COMMENT:
                    return XQITEMKIND_COMMENT;
                case Type.PROCESSING_INSTRUCTION:
                    return XQITEMKIND_PI;
                case Type.NODE:
                    return XQITEMKIND_NODE;
            }
        }
        return XQITEMKIND_ITEM;
    }

    @Override
    public int getItemOccurrence() {
        return XQSequenceType.OCC_EXACTLY_ONE;
    }

    /*@Nullable*/
    @Override
    public QName getNodeName() throws XQException {
        ItemType type = itemType;
        if (type instanceof DocumentNodeTest) {
            type = ((DocumentNodeTest) type).getElementTest();
        }
        if (type instanceof NodeTest) {
            if (type.getUType().overlaps(
                    UType.TEXT.union(UType.DOCUMENT).union(UType.COMMENT).union(UType.PI).union(UType.NAMESPACE))) {
                throw new XQException("Wrong node kind for getNodeName()");
            }
            StructuredQName match = ((NodeTest) type).getMatchingNodeName();
            if (match != null) {
                return new QName(match.getURI(), match.getLocalPart());
            } else {
                return null;
            }
        }
        throw new XQException("getNodeName() is not defined for this kind of item type");
    }

    /*@Nullable*/
    @Override
    public String getPIName() throws XQException {
        if (itemType instanceof NameTest && itemType.getPrimitiveType() == Type.PROCESSING_INSTRUCTION) {
            StructuredQName match = ((NameTest) itemType).getMatchingNodeName();
            return match.getLocalPart();
        } else if (itemType instanceof NodeKindTest && itemType.getPrimitiveType() == Type.PROCESSING_INSTRUCTION) {
            return null;
        } else {
            throw new XQException("Item kind is not a processing instruction");
        }
    }

    /*@Nullable*/
    @Override
    public URI getSchemaURI() {
        try {
            if (itemType instanceof NodeTest) {
                SchemaType content = ((NodeTest) itemType).getContentType();
                if (content == null) {
                    return null;
                }
                String systemId = content.getSystemId();
                if (systemId == null) {
                    return null;
                }
                return new URI(systemId);
            } else if (itemType instanceof AtomicType) {
                String systemId = ((AtomicType) itemType).getSystemId();
                return systemId == null ? null : new URI(systemId);
            } else {
                return null;
            }
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /*@Nullable*/
    public String toString() {
        return itemType.toString();
    }

    /*@NotNull*/
    @Override
    public QName getTypeName() throws XQException {
        ItemType type = itemType;
        if (type instanceof AtomicType) {
            StructuredQName name = ((AtomicType) type).getStructuredQName();
            return new QName(name.getURI(), name.getLocalPart());
        }
        if (type instanceof DocumentNodeTest) {
            type = ((DocumentNodeTest) type).getElementTest();
        }
        if (type instanceof NodeTest) {
            if (type.getUType().overlaps(UType.TEXT.union(UType.DOCUMENT).union(UType.COMMENT).union(UType.PI).union(UType.NAMESPACE))) {
                throw new XQException("getTypeName() failed: itemType is not a document, element, or attribute test");
            }
            SchemaType t = ((NodeTest) type).getContentType();
            if (t != null) {
                StructuredQName name = ((NodeTest) type).getContentType().getStructuredQName();
                return new QName(name.getURI(), name.getLocalPart());
            }
        }
        throw new XQException("getTypeName() failed: itemType is not a document, element, or attribute test");
    }

    @Override
    public boolean isAnonymousType() {
        ItemType type = itemType;
        if (type instanceof DocumentNodeTest) {
            type = ((DocumentNodeTest) type).getElementTest();
        }
        if (type instanceof NodeTest) {
            SchemaType t = ((NodeTest) type).getContentType();
            if (t != null) {
                return t.isAnonymousType();
            }
        }
        return false;
    }

    @Override
    public boolean isElementNillable() {
        return (itemType instanceof NodeTest) && itemType.getUType() == UType.ELEMENT && ((NodeTest) itemType).isNillable();
    }


    /*@NotNull*/
    @Override
    public XQItemType getItemType() {
        return this;
    }

    /*@Nullable*/ AtomicType getAtomicType() {
        if (itemType instanceof AtomicType) {
            return (AtomicType) itemType;
        } else {
            return null;
        }
    }

    /*@Nullable*/ ItemType getSaxonItemType() {
        return itemType;
    }

    public boolean equals(/*@NotNull*/ Object obj) {
        return obj instanceof SaxonXQItemType &&
                itemType.equals(((SaxonXQItemType) obj).itemType);
    }

    public int hashCode() {
        return itemType.hashCode();
    }
}
