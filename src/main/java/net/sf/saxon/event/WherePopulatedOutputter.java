////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.expr.instruct.WherePopulated;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;

/**
 * This class acts as a filter on a push pipeline, allowing through only those items
 * that are deemed non-empty according to the rules of the xsl:where-populated
 * instruction.
 *
 * <p>The items that are deemed empty, and therefore excluded, are:</p>
 *
 * <ul>
 *     <li>Document and element nodes having no children</li>
 *     <li>Nodes other than document and element nodes whose string value is zero-length</li>
 *     <li>Atomic values such that string(A) is zero-length</li>
 *     <li>Maps containing no entries</li>
 *     <li>Arrays which, when flattened, are either empty, or consist entirely of deemed-empty items</li>
 * </ul>
 *
 * <p>Note that these rules apply only to top-level items in the sequence passed through this
 * Outputter. Events representing items nested within a top-level document or element node
 * are always passed through; they are examined only to determine the emptiness or otherwise
 * of their contained.</p>
 */

public class WherePopulatedOutputter extends ProxyOutputter {

    private int level = 0;
    private boolean pendingStartTag = false;
    private NodeName pendingElemName;
    private SchemaType pendingSchemaType;
    private Location pendingLocationId;
    private int pendingProperties;
    private AttributeMap pendingAttributes;
    private NamespaceMap pendingNamespaces;

    public WherePopulatedOutputter(Outputter next) {
        super(next);
    }

    /**
     * Start of a document node.
     */
    @Override
    public void startDocument(int properties) throws XPathException {
        if (level++ == 0) {
            pendingStartTag = true;
            pendingElemName = null;
            pendingProperties = properties;
        } else {
            super.startDocument(properties);
        }
    }

    /**
     * Notify the start of an element
     */
    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             Location location, int properties) throws XPathException {
        releaseStartTag();
        if (level++ == 0) {
            pendingStartTag = true;
            pendingElemName = elemName;
            pendingSchemaType = type;
            pendingLocationId = location.saveLocation();
            pendingProperties = properties;
            pendingAttributes = EmptyAttributeMap.getInstance();
            pendingNamespaces = NamespaceMap.emptyMap();

        } else {
            super.startElement(elemName, type, location, properties);
        }
    }

    /**
     * Notify the start of an element
     */
    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        releaseStartTag();
        if (level++ == 0) {
            pendingStartTag = true;
            pendingElemName = elemName;
            pendingSchemaType = type;
            pendingLocationId = location.saveLocation();
            pendingProperties = properties;
            pendingAttributes = attributes;
            pendingNamespaces = namespaces;

        } else {
            super.startElement(elemName, type, attributes, namespaces, location, properties);
        }
    }

    /**
     * Notify a namespace binding.
     */
    @Override
    public void namespace(String prefix, String namespaceUri, int properties) throws XPathException {
        if (level == 1) {
            pendingNamespaces = pendingNamespaces.put(prefix, namespaceUri);
        } else {
            super.namespace(prefix, namespaceUri, properties);
        }
    }

    /**
     * Notify an attribute.
     */
    @Override
    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location location, int properties) throws XPathException {
        if (level == 1) {
            pendingAttributes = pendingAttributes.put(
                    new AttributeInfo(attName, typeCode, value.toString(), location, properties));
        } else if (!(level == 0 && value.length() == 0)) {
            super.attribute(attName, typeCode, value, location, properties);
        }
    }

    /**
     * Notify the end of a document node
     */
    @Override
    public void endDocument() throws XPathException {
        if (--level == 0) {
            if (!pendingStartTag) {
                super.endDocument();
            }
        } else {
            super.endDocument();
        }
        //pendingStartTag = false;
    }

    /**
     * End of element
     */
    @Override
    public void endElement() throws XPathException {
        if (--level == 0) {
            if (!pendingStartTag) {
                super.endElement();
            }
        } else {
            super.endElement();
        }
        pendingStartTag = false;
    }

    public void releaseStartTag() throws XPathException {
        if (level>=1 && pendingStartTag) {
            if (pendingElemName == null) {
                getNextOutputter().startDocument(pendingProperties);
            } else {
                getNextOutputter().startElement(pendingElemName, pendingSchemaType,
                                                pendingAttributes, pendingNamespaces,
                                                pendingLocationId, pendingProperties);
            }
            pendingStartTag = false;
        }
    }

    // Discard zero-length text nodes if level >= 1

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            if (chars.length() > 0) {
                super.characters(chars, locationId, properties);
            }
        } else if (level == 1) {
            if (chars.length() > 0) {
                releaseStartTag();
                super.characters(chars, locationId, properties);
            }
        } else {
            super.characters(chars, locationId, properties);
        }
    }

    /**
     * Output a processing instruction
     *
     * @param name       The PI name. This must be a legal name (it will not be checked).
     * @param data       The data portion of the processing instruction
     * @param location   provides information such as line number and system ID.
     * @param properties Additional information about the PI.
     * @throws IllegalArgumentException the content is invalid for an XML processing instruction
     * @throws XPathException            if an error occurs
     */
    @Override
    public void processingInstruction(String name, CharSequence data, Location location, int properties) throws XPathException {
        if (level == 0) {
            if (data.length() > 0) {
                super.processingInstruction(name, data, location, properties);
            }
        } else if (level == 1) {
            if (data.length() > 0) {
                releaseStartTag();
                super.processingInstruction(name,  data, location, properties);
            }
        } else {
            super.processingInstruction(name, data, location, properties);
        }
    }

    /**
     * Notify a comment. Comments are only notified if they are outside the DTD.
     *
     * @param content    The content of the comment
     * @param location   provides information such as line number and system ID.
     * @param properties Additional information about the comment.
     * @throws IllegalArgumentException the content is invalid for an XML comment
     * @throws XPathException            if an error occurs
     */
    @Override
    public void comment(CharSequence content, Location location, int properties) throws XPathException {
        if (level == 0) {
            if (content.length() > 0) {
                super.comment(content, location, properties);
            }
        } else if (level == 1) {
            if (content.length() > 0) {
                releaseStartTag();
                super.comment(content, location, properties);
            }
        } else {
            super.comment(content, location, properties);
        }
    }


    @Override
    public void append(Item item) throws XPathException {
        if (level == 0) {
            if (!WherePopulated.isDeemedEmpty(item)) {
                getNextOutputter().append(item);
            }
        } else if (level == 1 && pendingStartTag) {
            if (item instanceof NodeInfo) {
                NodeInfo node = (NodeInfo) item;
                switch (node.getNodeKind()) {
                    case Type.TEXT:
                        // ignore empty text nodes
                        if (node.getNodeKind() == Type.TEXT && node.getStringValueCS().length() == 0) {
                            return;
                        }
                        break;
                    case Type.DOCUMENT:
                        // ignore empty document nodes
                        if (node.getNodeKind() == Type.DOCUMENT && !node.hasChildNodes()) {
                            return;
                        }
                        break;
                    case Type.ATTRIBUTE:
                        attribute(NameOfNode.makeName(node), (SimpleType) node.getSchemaType(), node.getStringValue(), Loc.NONE, 0);
                        return;

                    case Type.NAMESPACE:
                        namespace(node.getLocalPart(), node.getStringValue(), 0);
                        return;

                    default:
                        break;
                }

            }
            releaseStartTag();
            getNextOutputter().append(item);
        } else {
            super.append(item);
        }
    }

    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (level == 0) {
            if (!WherePopulated.isDeemedEmpty(item)) {
                getNextOutputter().append(item, locationId, copyNamespaces);
            }
        } else if (level == 1 && pendingStartTag) {
            if (item instanceof NodeInfo) {
                NodeInfo node = (NodeInfo)item;
                switch (node.getNodeKind()) {
                    case Type.TEXT:
                        // ignore empty text nodes
                        if (node.getNodeKind() == Type.TEXT && node.getStringValueCS().length() == 0) {
                            return;
                        }
                        break;
                    case Type.DOCUMENT:
                        // ignore empty document nodes
                        if (node.getNodeKind() == Type.DOCUMENT && !node.hasChildNodes()) {
                            return;
                        }
                        break;
                    case Type.ATTRIBUTE:
                        attribute(NameOfNode.makeName(node), (SimpleType)node.getSchemaType(), node.getStringValue(), locationId, 0);
                        return;

                    case Type.NAMESPACE:
                        namespace(node.getLocalPart(), node.getStringValue(), 0);
                        return;

                    default:
                        break;
                }
            }
            releaseStartTag();
            getNextOutputter().append(item, locationId, copyNamespaces);
        } else {
            super.append(item, locationId, copyNamespaces);
        }
    }
}
