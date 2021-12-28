////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.CombinedNodeTest;
import net.sf.saxon.pattern.ContentTypeTest;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;

import java.util.HashSet;
import java.util.function.Supplier;

/**
 * A filter on the push pipeline that performs type checking, both of the item type and the
 * cardinality.
 * <p>Note that the TypeCheckingFilter cannot currently check document node tests of the form
 * document-node(element(X,Y)), so it is not invoked in such cases. This isn't a big problem, because most
 * instructions that return document nodes materialize them anyway.</p>
 */

public class TypeCheckingFilter extends ProxyOutputter {

    private ItemType itemType;
    private int cardinality;
    private RoleDiagnostic role;
    private Location locator;
    private int count = 0;
    private int level = 0;
    private HashSet<Long> checkedElements = new HashSet<>(10);
    // used to avoid repeated checking when a template creates large numbers of elements of the same type
    // The key is a (namecode, typecode) pair, packed into a single long
    private TypeHierarchy typeHierarchy;

    public TypeCheckingFilter(Outputter next) {
        super(next);
        typeHierarchy = getConfiguration().getTypeHierarchy();
    }

    public void setRequiredType(ItemType type, int cardinality, RoleDiagnostic role, Location locator) {
        itemType = type;
        this.cardinality = cardinality;
        this.role = role;
        this.locator = locator;
    }

    /**
     * Notify a namespace binding.
     */
    @Override
    public void namespace(String prefix, String namespaceUri, int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(Loc.NONE);
            }
            checkItemType(NodeKindTest.NAMESPACE, null, Loc.NONE);
        }
        getNextOutputter().namespace(prefix, namespaceUri, properties);
    }

    /**
     * Notify an attribute.
     */
    @Override
    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location location, int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(location);
            }
            ItemType type = new CombinedNodeTest(
                    new NameTest(Type.ATTRIBUTE, attName, getConfiguration().getNamePool()),
                    Token.INTERSECT,
                    new ContentTypeTest(Type.ATTRIBUTE, typeCode, getConfiguration(), false));
            checkItemType(type, nodeSupplier(Type.ATTRIBUTE, attName, typeCode, value), location);
        }
        getNextOutputter().attribute(attName, typeCode, value, location, properties);
    }

    /**
     * Character data
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(locationId);
            }
            checkItemType(NodeKindTest.TEXT, nodeSupplier(Type.TEXT, null, null, chars), locationId);
        }
        getNextOutputter().characters(chars, locationId, properties);
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(locationId);
            }
            checkItemType(NodeKindTest.COMMENT, nodeSupplier(Type.COMMENT, null, null, chars), locationId);
        }
        getNextOutputter().comment(chars, locationId, properties);
    }

    /**
     * Processing Instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(locationId);
            }
            checkItemType(NodeKindTest.PROCESSING_INSTRUCTION,
                          nodeSupplier(Type.PROCESSING_INSTRUCTION, new NoNamespaceName(target), null, data), locationId);
        }
        getNextOutputter().processingInstruction(target, data, locationId, properties);
    }

    /**
     * Start of a document node.
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(Loc.NONE);
            }
            checkItemType(NodeKindTest.DOCUMENT,
                          nodeSupplier(Type.DOCUMENT, null, null, ""), Loc.NONE);
        }
        level++;
        getNextOutputter().startDocument(properties);
    }

    /**
     * Notify the start of an element
     */

    @Override
    public void startElement(NodeName elemName, SchemaType elemType,
                             Location location, int properties) throws XPathException {
        checkElementStart(elemName, elemType, location);
        getNextOutputter().startElement(elemName, elemType, location, properties);
    }

    /**
     * Notify the start of an element, supplying all attributes and namespaces
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
        checkElementStart(elemName, type, location);
        getNextOutputter().startElement(elemName, type, attributes, namespaces, location, properties);
    }

    private void checkElementStart(NodeName elemName, SchemaType elemType, Location location) throws XPathException {
        Configuration config = getConfiguration();
        NamePool namePool = config.getNamePool();
        if (level == 0) {
            if (++count == 1) {
                // don't bother with any caching on the first item, it will often be the only one
                ItemType type = new CombinedNodeTest(
                        new NameTest(Type.ELEMENT, elemName, namePool),
                        Token.INTERSECT,
                        new ContentTypeTest(Type.ELEMENT, elemType, config, false));
                checkItemType(type, nodeSupplier(Type.ELEMENT, elemName, elemType, ""), location);
            } else {
                if (count == 2) {
                    checkAllowsMany(location);
                }
                long key = (long) elemName.obtainFingerprint(namePool) << 32 | (long) elemType.getFingerprint();
                if (!checkedElements.contains(key)) {
                    ItemType type = new CombinedNodeTest(
                            new NameTest(Type.ELEMENT, elemName, namePool),
                            Token.INTERSECT,
                            new ContentTypeTest(Type.ELEMENT, elemType, config, false));
                    checkItemType(type, nodeSupplier(Type.ELEMENT, elemName, elemType, ""), location);
                    checkedElements.add(key);
                }
            }
        }
        level++;
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        level--;
        getNextOutputter().endDocument();
    }

    /**
     * End of element
     */

    @Override
    public void endElement() throws XPathException {
        level--;
        getNextOutputter().endElement();
    }

    /**
     * End of event stream
     */

    @Override
    public void close() throws XPathException {
        finalCheck();
        super.close();
    }

    public void finalCheck() throws XPathException {
        if (count == 0 && !Cardinality.allowsZero(cardinality)) {
            XPathException err = new XPathException("An empty sequence is not allowed as the " +
                                                            role.getMessage());
            String errorCode = role.getErrorCode();
            err.setErrorCode(errorCode);
            if (!"XPDY0050".equals(errorCode)) {
                err.setIsTypeError(true);
            }
            throw err;
        }
    }

    private Supplier<NodeInfo> nodeSupplier(short nodeKind, NodeName name, SchemaType type, CharSequence value) {
        return () -> {
            Orphan o = new Orphan(getPipelineConfiguration().getConfiguration());
            o.setNodeKind(nodeKind);
            if (name != null) {
                o.setNodeName(name);
            }
            o.setTypeAnnotation(type);
            o.setStringValue(value);
            return o;
        };
    }

    /**
     * Output an item (atomic value or node) to the sequence
     */

    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(locationId);
            }
            checkItem(item, locationId);
        }
        getNextOutputter().append(item, locationId, copyNamespaces);
    }

    /**
     * Output an item (atomic value or node) to the sequence
     */

    @Override
    public void append(Item item) throws XPathException {
        if (level == 0) {
            if (++count == 2) {
                checkAllowsMany(Loc.NONE);
            }
            checkItem(item, Loc.NONE);
        }
        getNextOutputter().append(item);
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

    private void checkItemType(ItemType type, Supplier<? extends Item> itemSupplier, Location locationId) throws XPathException {
        if (!typeHierarchy.isSubType(type, itemType)) {
            throwTypeError(type, itemSupplier == null ? null : itemSupplier.get(), locationId);
        }
    }

    private void checkItem(Item item, Location locationId) throws XPathException {
        if (!itemType.matches(item, typeHierarchy)) {
            throwTypeError(null, item, locationId);
        }
    }


    private void throwTypeError(ItemType suppliedType, Item item, Location locationId) throws XPathException {
        String message;
        if (item == null) {
            message = role.composeErrorMessage(itemType, suppliedType);
        } else {
            message = role.composeErrorMessage(itemType, item, typeHierarchy);
        }
        String errorCode = role.getErrorCode();
        XPathException err = new XPathException(message);
        err.setErrorCode(errorCode);
        if (!"XPDY0050".equals(errorCode)) {
            err.setIsTypeError(true);
        }
        if (locationId == null) {
            err.setLocation(locator);
        } else {
            err.setLocation(locationId.saveLocation());
        }
        throw err;
    }

    private void checkAllowsMany(Location locationId) throws XPathException {
        if (!Cardinality.allowsMany(cardinality)) {
            XPathException err = new XPathException("A sequence of more than one item is not allowed as the " +
                    role.getMessage());
            String errorCode = role.getErrorCode();
            err.setErrorCode(errorCode);
            if (!"XPDY0050".equals(errorCode)) {
                err.setIsTypeError(true);
            }
            if (locationId == null || locationId == Loc.NONE) {
                err.setLocator(locator);
            } else {
                err.setLocator(locationId);
            }
            throw err;
        }
    }


}

