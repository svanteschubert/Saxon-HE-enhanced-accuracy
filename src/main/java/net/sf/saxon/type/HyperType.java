////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

/**
 * Atomic types are the intersection of XPath item types and XSD simple types, and use multiple inheritance
 * to inherit methods representing the characteristics of both these kinds. In some cases the same method
 * is present in both hierarchies. This causes Java no problems, but C# can get upset about it, even though
 * the method definitions are 100% consistent.
 *
 * <p>This interface exists as a common supertype for both of these hierarchies. It's purely a place to
 * park method definitions that exist in both, so that C# doesn't complain.</p>
 */

public interface HyperType {

    /**
     * Test whether this type is namespace sensitive, that is, if a namespace context is needed
     * to translate between the lexical space and the value space. This is true for types derived
     * from, or containing, QNames and NOTATIONs
     *
     * @return true if any of the member types is namespace-sensitive, or if namespace sensitivity
     * cannot be determined because there are components missing from the schema.
     */

    boolean isNamespaceSensitive();

    /**
     * Get a description of the type for use in diagnostics. For a named type, this will be the
     * display name of the type. For an anonymous type, it will be a description of the type.
     */

    //String getDescription();
}

