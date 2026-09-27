// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern.qname;

import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.UnionQNameTest;
import net.sf.saxon.type.Affinity;

import java.util.Objects;

public class NamespaceQNameTest implements QNameTest {

    private final NamespaceUri namespace;

    public NamespaceQNameTest(NamespaceUri namespace) {
        Objects.requireNonNull(namespace);
        this.namespace = namespace;
    }

    @Override
    public boolean matches(StructuredQName qname) {
        return qname.hasURI(namespace);
    }

    @Override
    public String exportQNameTest() {
        return toString();
    }

    public NamespaceUri getNamespace() {
        return namespace;
    }

    public Affinity relationship(QNameTest other) {
        if (other instanceof AnyQNameTest) {
            return Affinity.SUBSUMED_BY;
        } else if (other instanceof SpecificQNameTest) {
            return ((SpecificQNameTest) other).getStructuredQName().hasURI(namespace) ? Affinity.SUBSUMES : Affinity.DISJOINT;
        } else if (other instanceof PortableQNameTest) {
            return ((PortableQNameTest) other).getStructuredQName().hasURI(namespace) ? Affinity.SUBSUMES : Affinity.DISJOINT;
        } else if (other instanceof NamespaceQNameTest) {
            return ((NamespaceQNameTest) other).namespace == namespace ? Affinity.SAME_TYPE : Affinity.DISJOINT;
        } else if (other instanceof LocalQNameTest) {
            return Affinity.OVERLAPS;
        } else if (other instanceof NoQNameTest) {
            return Affinity.SUBSUMES;
        } else if (other instanceof UnionQNameTest) {
            return new UnionQNameTest(this).relationship(other);
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public String toString() {
        return "Q{" + namespace + "}*";
    }

    @Override
    public int hashCode() {
        return namespace.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NamespaceQNameTest && namespace == ((NamespaceQNameTest)obj).namespace;
    }
}

