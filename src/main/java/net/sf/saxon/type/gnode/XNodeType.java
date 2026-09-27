// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.gnode;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.Genre;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.type.AnyType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.UType;
import net.sf.saxon.type.coercion.CoercionPlan;
import net.sf.saxon.type.coercion.XNodeCoercionPlan;

/**
 * Abstract superclass for all item types that match XNodes only. There
 * are several subtypes:
 *
 * <ul>
 *     <li>{@link AnyXNodeType}: matches any XNode</li>
 *     <li>{@link NodeKindType}: matches any XNode of a given kind</li>
 *     <li>{@link NamedXNodeType}: matches any XNode of a given kind,
 *     whose name matches a {@link QNameTest} and whose type annotation
 *     matches a given schema type</li>
 *     <li>{@link SchemaNodeTest}: matches the types <code>schema-element(E)</code>
 *     and <code>schema-attribute(A)</code></li>
 *     <li>{@link DocumentNodeType}: matches the type <code>document-node(E)</code>
 *  *     and <code>schema-attribute(A)</code></li>
 * </ul>
 */
public abstract class XNodeType extends GNodeType {

    /**
     * Determine the Genre (top-level classification) of this type
     *
     * @return the Genre to which this type belongs, for example node or atomic value
     */
    @Override
    public Genre getGenre() {
        return Genre.XNODE;
    }

    @Override
    public NodeTest asXNodeTest(Configuration config) {
        return this;
    }

    /**
     * Get the set of allowed node names that this type if capable
     * of matching
     * @return the allowed node names
     */
    public abstract QNameTest getAllowedNodeNames();

    /**
     * For an {@code XNodeType} that can only match one kind of node
     * and one node name, return that node name, as an integer
     * fingerprint. In other cases, return -1.
     * @param nodeKind the kind of node required. If the {@code XNodeType}
     *                 does not match this node kind, return -1.
     * @return an integer fingerprint in the case of a node type that
     * only matches one node kind and one qualified name. In other
     * cases return -1.
     */

    public int getRequiredFingerprint(int nodeKind) {
        return -1;
    }

    /**
     * Get the corresponding {@link UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.XNODE;
    }


    /**
     * Get the allowed content type permitted by this item type. Defaults
     * to allowing any content
     * @return the allowed content type
     */

    public SchemaType getContentType() {
        return AnyType.INSTANCE;
    }

    /**
     * Ask whether nodes that are nilled can match this type.
     * Defaults to true
     */

    public boolean isNillable() {
        return true;
    }

    /**
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     *
     * @param version the XPath language version (40 or 31)
     */
    @Override
    public CoercionPlan getCoercionPlan(int version) {
        return XNodeCoercionPlan.getInstance();
    }
}

