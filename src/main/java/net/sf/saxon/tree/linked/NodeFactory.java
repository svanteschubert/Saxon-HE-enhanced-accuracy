////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.type.SchemaType;


/**
 * Interface NodeFactory. <br>
 * A Factory for nodes used to build a tree. <br>
 * Currently only allows Element nodes to be user-constructed.
 *
 * @author Michael H. Kay
 * @version 25 February 2000
 */

public interface NodeFactory {

    /**
     * Create an Element node
     * @param parent         The parent element
     * @param nameCode       The element name
     * @param elementType    The type annotation of the element
     * @param isNilled       true if the element is to be marked as nilled
     * @param attlist        The attribute collection, excluding any namespace attributes
     * @param namespaces     in-scope namespace declarations for this element
     * @param pipe           The pipeline configuration (provides access to the error listener and the
*                       location provider)
     * @param locationId     Indicates the source document and line number containing the node
     * @param sequenceNumber Sequence number to be assigned to represent document order.
     * @return the element node
     */

    /*@Nullable*/
    ElementImpl makeElementNode(
            NodeInfo parent,
            NodeName nameCode,
            SchemaType elementType,
            boolean isNilled,
            AttributeMap attlist,
            NamespaceMap namespaces,
            PipelineConfiguration pipe,
            Location locationId,
            int sequenceNumber);

    /**
     * Make a text node
     *
     * @param parent  the parent element
     * @param content the content of the text node
     * @return the constructed text node
     */

    public TextImpl makeTextNode(NodeInfo parent, CharSequence content);

}

