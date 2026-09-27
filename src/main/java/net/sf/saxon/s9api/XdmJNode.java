////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.transpile.CSharpModifiers;

/**
 * This class represents a JNode in the XDM data model. An {@code XdmJNode} is an {@link XdmItem}, and is therefore an
 * {@link XdmValue} in its own right, and may also participate as one item within a sequence value.
 * <p>An {@code XdmJNode} is implemented as a wrapper around an object of type {@link JNode}.</p>
 * <p>The {@code XdmJNode} interface exposes basic properties of the JNode, such as its parent, its
 * selector, its position, and its contained value.
 *
 * @since 13.0
 */
@CSharpModifiers(code = {"internal"})
@CSharpInjectMembers(code={"public override bool IsAtomic() { return false; }"})
public class XdmJNode extends XdmItem {


    /**
     * Construct an {@code XdmJNode} as a wrapper around an existing JNode object. This constructor
     * is intended primarily for system use.
     *
     * @param node the {@link JNode} object to be wrapped. This can be retrieved using the
     *             {@link #getUnderlyingValue()} method.
     */

    public XdmJNode(JNode node) {
        super(node);
    }

    /**
     * Get the content property of the JNode.
     *
     * @return the value of the content property
     */

    public XdmValue getContent() {
        return new XdmValue(getUnderlyingValue().getContent());
    }

    /**
     * Get the underlying implementation object representing the JNode. This method allows
     * access to lower-level Saxon functionality, including classes and methods that offer
     * no guarantee of stability across releases.
     *
     * @return the underlying implementation object representing the JNode
     */
    @Override
    public JNode getUnderlyingValue() {
        return (JNode) super.getUnderlyingValue();
    }

    /**
     * Get the selector of the JNode, as an XdmAtomicValue
     *
     * @return the selector of the JNode. Returns null for a root JNode; otherwise the key value for a
     * JNode representing an entry in a map, or the 1-based index for a JNode representing a member of an
     * array.
     */

    /*@Nullable*/
    public XdmAtomicValue getSelector() {
        return new XdmAtomicValue(getUnderlyingValue().getSelector());
    }

    /**
     * Get the position of the JNode, as an integer
     *
     * @return the integer of the JNode. Returns -1 for a root JNode; otherwise the position of
     * the JNode within a sequence-valued parent (normally 1, since array members and map entry values
     * are usually singletons)
     */

    /*@Nullable*/
    public int getPosition() {
        return getUnderlyingValue().getPosition();
    }



}

