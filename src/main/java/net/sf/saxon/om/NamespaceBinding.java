////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.tree.jiter.MonoIterator;
import net.sf.saxon.lib.NamespaceConstant;

import java.util.Iterator;

/**
 * Represents the binding of a prefix to a URI. Also, in some contexts, represents an unbinding, by
 * virtue of the URI being set to a zero length string.
 *
 * @since 9.4
 */
public final class NamespaceBinding implements NamespaceBindingSet {

    private String prefix;
    private String uri;

    public final static NamespaceBinding XML = new NamespaceBinding("xml", NamespaceConstant.XML);
    public final static NamespaceBinding DEFAULT_UNDECLARATION = new NamespaceBinding("", "");

    public final static NamespaceBinding[] EMPTY_ARRAY = new NamespaceBinding[0];

    /**
     * Create a binding of a prefix to a URI
     *
     * @param prefix the prefix: either an NCName, or a zero-length string to bind the default namespace.
     *               Must not be null.
     * @param uri    the namespace URI: either a URI, or a zero-length string to unbind the prefix. Must
     *               not be null.
     */

    public NamespaceBinding(String prefix, String uri) {
        this.prefix = prefix;
        this.uri = uri;
        if (prefix == null || uri == null) {
            throw new NullPointerException();
        }
    }

    @Override
    public String getURI(String prefix) {
        return prefix.equals(this.prefix) ? uri : null;
    }

    /**
     * Create a binding of a prefix to a URI. Static factory method for the convenience of compiled bytecode;
     * reuses standard NamespaceBinding objects where possible
     *
     * @param prefix the prefix: either an NCName, or a zero-length string to bind the default namespace.
     *               Must not be null.
     * @param uri    the namespace URI: either a URI, or a zero-length string to unbind the prefix. Must
     *               not be null.
     * @return the namespace binding object
     */

    public static NamespaceBinding makeNamespaceBinding(CharSequence prefix, CharSequence uri) {
        if (prefix.length()==0 && uri.length()==0) {
            return DEFAULT_UNDECLARATION;
        } else if (prefix.equals("xml") && uri.equals(NamespaceConstant.XML)) {
            return XML;
        } else {
            return new NamespaceBinding(prefix.toString(), uri.toString());
        }
    }

    /**
     * Get the prefix part of the binding
     *
     * @return the prefix. Never null. The zero-length string indicates a binding for the default namespace.
     */

    public String getPrefix() {
        return prefix;
    }

    /**
     * Get the URI part of the binding
     *
     * @return the URI. Never null. The zero-length string indicates an unbinding of the prefix. For the
     *         default namespace (prefix="") this indicates that the prefix refers to names in no namespace; for other
     *         prefixes, it indicates that the prefix is not bound to any namespace and therefore cannot be used.
     */

    public String getURI() {
        return uri;
    }

    /**
     * Ask whether this is a binding for the XML namespace
     *
     * @return true if this is the binding of the prefix "xml" to the standard XML namespace.
     */

    public boolean isXmlNamespace() {
        return prefix.equals("xml");
    }

    /**
     * Ask whether this is an undeclaration of the default prefix, that is, a namespace binding
     * corresponding to <code>xmlns=""</code>
     *
     * @return true if this corresponding to <code>xmlns=""</code>
     */

    public boolean isDefaultUndeclaration() {
        return prefix.isEmpty() && uri.isEmpty();
    }

    /**
     * Returns an iterator over this singleton set of namespace bindings.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<NamespaceBinding> iterator() {
        return new MonoIterator<NamespaceBinding>(this);
    }

    /**
     * Test if this namespace binding is the same as another
     *
     * @param obj the comparand
     * @return true if the comparand is a Namespace binding of the same prefix to the same URI
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof NamespaceBinding &&
                prefix.equals(((NamespaceBinding) obj).getPrefix()) &&
                uri.equals(((NamespaceBinding) obj).getURI());
    }

    @Override
    public int hashCode() {
        return prefix.hashCode() ^ uri.hashCode();
    }

    public String toString() {
        return prefix + "=" + uri;
    }
}

