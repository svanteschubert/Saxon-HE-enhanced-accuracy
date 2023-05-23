////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.xom;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.*;
import net.sf.saxon.type.Type;
import nu.xom.Attribute;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Node;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

/**
 * The root node of an XPath tree. (Or equivalently, the tree itself).
 * <p>This class is used not only for a document, but also for the root
 * of a document-less tree fragment.</p>
 *
 * @author Michael H. Kay
 * @author Wolfgang Hoschek (ported net.sf.saxon.jdom to XOM)
 */

public class XOMDocumentWrapper extends XOMNodeWrapper implements TreeInfo {

    protected Configuration config;
    protected long documentNumber;
    private HashMap<String, NodeInfo> idIndex;
    private SpaceStrippingRule spaceStrippingRule = NoElementsSpaceStrippingRule.getInstance();
    private HashMap<String, Object> userData;

    /**
     * Create a Saxon wrapper for a XOM root node
     *
     * @param root    The XOM root node
     * @param config  The configuration which defines the name pool used for all
     *                names in this tree
     */
    public XOMDocumentWrapper(Node root, Configuration config) {
        super(root, null, 0);
        if (!config.isLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
            config.requireProfessionalLicense("XOM");
        }
        if (root.getParent() != null) {
            throw new IllegalArgumentException("root node must not have a parent node");
        }
        docWrapper = this;
        treeInfo = this;
        setConfiguration(config);
    }

    /**
     * Get the NodeInfo object representing the document node at the root of the tree
     *
     * @return the document node
     */

    @Override
    public NodeInfo getRootNode() {
        return this;
    }

    /**
     * Wrap a node in the XOM document.
     *
     * @param node The node to be wrapped. This must be a node in the same
     *             document (the system does not check for this).
     * @return the wrapping NodeInfo object
     */

    public NodeInfo wrap(Node node) {
        if (node == this.node) {
            return this;
        }
        return makeWrapper(node, this);
    }

    /**
     * Set the configuration, which defines the name pool used for all names in
     * this document. This is always called after a new document has been
     * created. The implementation must register the name pool with the
     * document, so that it can be retrieved using getNamePool(). It must also
     * call NamePool.allocateDocumentNumber(), and return the relevant document
     * number when getDocumentNumber() is subsequently called.
     *
     * @param config The configuration to be used
     */

    public void setConfiguration(Configuration config) {
        this.config = config;
        documentNumber = config.getDocumentNumberAllocator().allocateDocumentNumber();
    }

    /**
     * Get the configuration previously set using setConfiguration
     */

    @Override
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the name pool used for the names in this document
     *
     * @return the name pool in which all the names used in this document are
     *         registered
     */

    @Override
    public NamePool getNamePool() {
        return config.getNamePool();
    }


    @Override
    public void setSystemId(String uri) {
        ((Document)node).setBaseURI(uri);
    }

    /**
     * Get the unique document number for this document (the number is unique
     * for all documents within a NamePool)
     *
     * @return the unique number identifying this document within the name pool
     */

    @Override
    public long getDocumentNumber() {
        return documentNumber;
    }

    /**
     * Get the element with a given ID, if any
     *
     * @param id        the required ID value
     * @param getParent true if the parent of the element of type ID is requird
     * @return the element with the given ID, or null if there is no such ID
     *         present (or if the parser has not notified attributes as being of
     *         type ID).
     */

    /*@Nullable*/
    @Override
    public NodeInfo selectID(String id, boolean getParent) {
        if (idIndex == null) {
            Element elem;
            switch (nodeKind) {
                case Type.DOCUMENT:
                    elem = ((Document) node).getRootElement();
                    break;
                case Type.ELEMENT:
                    elem = (Element) node;
                    break;
                default:
                    return null;
            }
            idIndex = new HashMap<>(50);
            buildIDIndex(elem);
        }
        return idIndex.get(id);
    }


    private void buildIDIndex(Element elem) {
        // walk the tree in reverse document order, to satisfy the XPath 1.0 rule
        // that says if an ID appears twice, the first one wins
        for (int i = elem.getChildCount(); --i >= 0; ) {
            Node child = elem.getChild(i);
            if (child instanceof Element) {
                buildIDIndex((Element) child);
            }
        }
        for (int i = elem.getAttributeCount(); --i >= 0; ) {
            Attribute att = elem.getAttribute(i);
            if (att.getType() == Attribute.Type.ID) {
                idIndex.put(att.getValue(), wrap(elem));
            }
        }
    }

    /**
     * Get the list of unparsed entities defined in this document
     *
     * @return an Iterator, whose items are of type String, containing the names of all
     *         unparsed entities defined in this document. If there are no unparsed entities or if the
     *         information is not available then an empty iterator is returned
     */

    @Override
    public Iterator<String> getUnparsedEntityNames() {
        return Collections.emptyIterator();
    }

    /**
     * Get the unparsed entity with a given name
     *
     * @param name the name of the entity
     * @return null: XOM does not provide access to unparsed entities
     */

    @Override
    public String[] getUnparsedEntity(String name) {
        return null;
    }


    /**
     * Set details of space stripping action that was applied to this document during
     * construction. This ensures that space stripping is not applied twice to the same
     * document.
     *
     * @param rule details of the space stripping rules that have been applied to this
     *             document during its construction.
     * @since 9.9
     */
    @Override
    public void setSpaceStrippingRule(SpaceStrippingRule rule) {
        this.spaceStrippingRule = rule;
    }

    /**
     * Get details of space stripping action that was applied to this document during
     * construction. This ensures that space stripping is not applied twice to the same
     * document.
     *
     * @return details of the space stripping rules that have been applied to this
     * document during its construction. By default, returns {@link NoElementsSpaceStrippingRule},
     * indicating that no space stripping has been applied
     * @since 9.9
     */
    @Override
    public SpaceStrippingRule getSpaceStrippingRule() {
        return spaceStrippingRule;
    }

    /**
     * Set user data on the document node. The user data can be retrieved subsequently
     * using {@link #getUserData}
     *
     * @param key   A string giving the name of the property to be set. Clients are responsible
     *              for choosing a key that is likely to be unique. Must not be null. Keys used internally
     *              by Saxon are prefixed "saxon:".
     * @param value The value to be set for the property. May be null, which effectively
     *              removes the existing value for the property.
     */

    @Override
    public void setUserData(String key, Object value) {
        if (userData == null) {
            userData = new HashMap<>(4);
        }
        if (value == null) {
            userData.remove(key);
        } else {
            userData.put(key, value);
        }
    }

    /**
     * Get user data held in the document node. This retrieves properties previously set using
     * {@link #setUserData}
     *
     * @param key A string giving the name of the property to be retrieved.
     * @return the value of the property, or null if the property has not been defined.
     */

    @Override
    public Object getUserData(String key) {
        if (userData == null) {
            return null;
        } else {
            return userData.get(key);
        }
    }

}

