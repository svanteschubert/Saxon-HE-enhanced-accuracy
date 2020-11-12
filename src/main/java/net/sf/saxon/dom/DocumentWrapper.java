////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.GenericTreeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.tree.iter.AxisIterator;
import org.w3c.dom.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * The tree info object for a tree implemented as a wrapper around a DOM Document.
 * <p>Because the DOM is not thread-safe even when reading, and because Saxon-EE can spawn multiple
 * threads that access the same input tree, all methods that invoke DOM methods are synchronized
 * on the Document node</p>
 */

public class DocumentWrapper extends GenericTreeInfo {

    protected boolean domLevel3;
    public final Node docNode;
    private Map<String, NodeInfo> idIndex = null;

    /**
     * Wrap a DOM Document or DocumentFragment node
     *
     * @param doc     a DOM Document or DocumentFragment node
     * @param baseURI the base URI of the document
     * @param config  the Saxon configuration
     */

    public DocumentWrapper(Node doc, String baseURI, Configuration config) {
        super(config);
        if (doc.getNodeType() != Node.DOCUMENT_NODE && doc.getNodeType() != Node.DOCUMENT_FRAGMENT_NODE) {
            throw new IllegalArgumentException("Node must be a DOM Document or DocumentFragment");
        }
        if (config.getExternalObjectModel(doc.getClass()) == null) {
            throw new IllegalArgumentException(
                    "Node class " + doc.getClass().getName() + " is not recognized in this Saxon configuration");
        }
        domLevel3 = true;
        this.docNode = doc;
        setRootNode(wrap(doc));
        setSystemId(baseURI);
    }

    /**
     * Create a wrapper for a node in this document
     *
     * @param node the DOM node to be wrapped. This must be a node within the document wrapped by this
     *             DocumentWrapper
     * @return the wrapped node
     * @throws IllegalArgumentException if the node is not a descendant of the Document node wrapped by
     *                                  this DocumentWrapper
     */

    public DOMNodeWrapper wrap(Node node) {
        return DOMNodeWrapper.makeWrapper(node, this);
//        Document doc = node.getOwnerDocument();
//        if (doc == this.node || (domLevel3 && doc != null && doc.isSameNode(this.node))) {
//
//        } else {
//            throw new IllegalArgumentException(
//                    "DocumentWrapper#wrap: supplied node does not belong to the wrapped DOM document");
//        }
    }

    /**
     * Set the level of DOM interface to be used
     *
     * @param level the DOM level. Must be 2 or 3. By default Saxon assumes that DOM level 3 is available;
     *              this parameter can be set to the value 2 to indicate that Saxon should not use methods unless they
     *              are available in DOM level 2. From Saxon 9.2, this switch remains available, but the use of
     *              DOM level 2 is untested and unsupported.
     */

    public void setDOMLevel(int level) {
        if (!(level == 2 || level == 3)) {
            throw new IllegalArgumentException("DOM Level must be 2 or 3");
        }
        domLevel3 = level == 3;
    }

    /**
     * Get the level of DOM interface to be used
     *
     * @return the DOM level. Always 2 or 3.
     */

    public int getDOMLevel() {
        return domLevel3 ? 3 : 2;
    }

    /**
     * Get the element with a given ID, if any
     *
     * @param id        the required ID value
     * @param getParent true if the parent of the element having the given ID value is required
     * @return a NodeInfo representing the element with the given ID, or null if there
     *         is no such element. This implementation does not necessarily conform to the
     *         rule that if an invalid document contains two elements with the same ID, the one
     *         that comes last should be returned.
     */

    @Override
    public NodeInfo selectID(String id, boolean getParent) {
        synchronized (docNode) {
            Node node = ((DOMNodeWrapper)getRootNode()).node;
            if (node instanceof Document) {
                Node el = ((Document) node).getElementById(id);
                if (el != null) {
                    return wrap(el);
                }
            }
            // Search for xml:id attributes (which the DOM does not necessarily expose as ID values)
            if (idIndex != null) {
                return idIndex.get(id);
            } else {
                idIndex = new HashMap<>();
                AxisIterator iter = getRootNode().iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
                NodeInfo e;
                while ((e = iter.next()) != null) {
                    String xmlId = e.getAttributeValue(NamespaceConstant.XML, "id");
                    if (xmlId != null) {
                        idIndex.put(xmlId, e);
                    }
                }
                return idIndex.get(id);
            }
        }
    }


    /**
     * Get the list of unparsed entities defined in this document
     *
     * @return an Iterator, whose items are of type String, containing the names of all
     *         unparsed entities defined in this document. If there are no unparsed entities or if the
     *         information is not available then an empty iterator is returned
     * @since 9.1 (implemented for this subclass since 9.2)
     */

    @Override
    public Iterator<String> getUnparsedEntityNames() {
        synchronized (docNode) {
            Node node = ((DOMNodeWrapper) getRootNode()).node;
            if (node instanceof Document) {
                DocumentType docType = ((Document) node).getDoctype();
                if (docType == null) {
                    List<String> ls = Collections.emptyList();
                    return ls.iterator();
                }
                NamedNodeMap map = docType.getEntities();
                if (map == null) {
                    List<String> ls = Collections.emptyList();
                    return ls.iterator();
                }
                List<String> names = new ArrayList<>(map.getLength());
                for (int i = 0; i < map.getLength(); i++) {
                    Entity e = (Entity) map.item(i);
                    if (e.getNotationName() != null) {
                        // it is an unparsed entity
                        names.add(e.getLocalName());
                    }
                }
                return names.iterator();
            } else {
                return null;
            }
        }
    }

    /**
     * Get the unparsed entity with a given name
     *
     * @param name the name of the entity
     * @return if the entity exists, return an array of two Strings, the first
     *         holding the system ID of the entity (as an absolute URI if possible),
     *         the second holding the public ID if there is one, or null if not.
     *         If the entity does not exist, the method returns null.
     *         Applications should be written on the assumption that this array may
     *         be extended in the future to provide additional information.
     * @since 8.4 (implemented for this subclass since 9.2)
     */

    @Override
    public String[] getUnparsedEntity(String name) {
        synchronized (docNode) {
            Node node = ((DOMNodeWrapper) getRootNode()).node;
            if (node instanceof Document) {
                DocumentType docType = ((Document) node).getDoctype();
                if (docType == null) {
                    return null;
                }
                NamedNodeMap map = docType.getEntities();
                if (map == null) {
                    return null;
                }
                Entity entity = (Entity) map.getNamedItem(name);
                if (entity == null || entity.getNotationName() == null) {
                    // In the first case, no entity found. In the second case, it's a parsed entity.
                    return null;
                }
                String systemId = entity.getSystemId();
                try {
                    URI systemIdURI = new URI(systemId);
                    if (!systemIdURI.isAbsolute()) {
                        String base = getRootNode().getBaseURI();
                        if (base != null) {
                            systemId = ResolveURI.makeAbsolute(systemId, base).toString();
                        } else {
                            // base URI unknown: return the relative URI as written
                        }
                    }
                } catch (URISyntaxException err) {
                    // invalid URI: no action - return the "URI" as written
                }
                return new String[]{systemId, entity.getPublicId()};
            } else {
                return null;
            }
        }
    }


}

