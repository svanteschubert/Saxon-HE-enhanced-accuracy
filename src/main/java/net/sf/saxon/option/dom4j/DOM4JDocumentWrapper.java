////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.dom4j;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.GenericTreeInfo;
import net.sf.saxon.om.NodeInfo;
import org.dom4j.Branch;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;

import java.util.HashMap;
import java.util.List;

/**
 * TreeInfo class for a virtual tree that wraps a DOM4J tree
 */

public class DOM4JDocumentWrapper extends GenericTreeInfo {

    /**
     * Create a Saxon wrapper for a dom4j document
     *
     * @param doc     The dom4j document
     * @param baseURI The base URI for all the nodes in the document
     * @param config  The Saxon configuration
     */

    public DOM4JDocumentWrapper(Document doc, String baseURI, Configuration config) {
        super(config);
        setRootNode(wrap(doc));
        setSystemId(baseURI);
    }

    /**
     * Wrap a node in the dom4j document.
     *
     * @param node The node to be wrapped. This must be a node in the same document
     *             (the system does not check for this).
     * @return the wrapping NodeInfo object
     */

    public NodeInfo wrap(Node node) {
        return DOM4JNodeWrapper.makeWrapper(node, this);
    }

    /**
     * Get the element with a given ID, if any
     *
     * @param id        the required ID value
     * @param getParent true if the parent of the element having ID type is required
     * @return null: dom4j does not provide any information about attribute types.
     */


    /*@Nullable*/
    @Override
    public NodeInfo selectID(String id, boolean getParent) {
        HashMap<String, Element> idIndex = (HashMap<String, Element>)getUserData("saxon-id-index");
        if (idIndex != null) {
            Element e = idIndex.get(id);
            return e==null ? null : wrap(e);
        }
        return null;
    }

    /**
     * DOM4J does not guarantee to provide the parent of a node, but XDM requires this. In extremis,
     * if we need the parent of a node and it is not known, we search the document looking for it.
     * This is (fortunately!) very rarely necessary. One situation where we know it is needed is to find the parent of a
     * processing instruction whose parent is in fact the document node.
     *
     * @param subtree the root of a subtree to be searched
     * @param node    a node to be sought within this subtree
     * @return the parent of the sought node if it is found; otherwise null
     */

    public static Branch searchForParent(Branch subtree, Node node) {
        List content = subtree.content();
        for (Object o : content) {
            Node child = (Node) o;
            if (child == node) {
                return subtree;
            } else if (child.hasContent()) {
                Branch b = searchForParent((Branch) child, node);
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }

}

