////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package net.sf.saxon.type;

import net.sf.saxon.transpile.CSharpInjectMembers;
import org.w3c.dom.TypeInfo;

@CSharpInjectMembers(code={
        "public const int DERIVATION_RESTRICTION = 1;",
        "public const int DERIVATION_EXTENSION = 2;",
        "public const int DERIVATION_UNION = 4;",
        "public const int DERIVATION_LIST = 8;",
        "public const int DERIVE_BY_SUBSTITUTION = 16;",
})
public abstract class Derivation {

    // DerivationMethods. These constants, with one addition are copied from org.w3.dom.TypeInfo.
    // They are used as bit-significant flags.

    /**
     * If the document's schema is an XML Schema [<a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/'>XML Schema Part 1</a>]
     * , this constant represents the derivation by <a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/#key-typeRestriction'>
     * restriction</a> if complex types are involved, or a <a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/#element-restriction'>
     * restriction</a> if simple types are involved.
     * <br>  The reference type definition is derived by restriction from the
     * other type definition if the other type definition is the same as the
     * reference type definition, or if the other type definition can be
     * reached recursively following the {base type definition} property
     * from the reference type definition, and all the <em>derivation methods</em> involved are restriction.
     */
    public final static int DERIVATION_RESTRICTION = TypeInfo.DERIVATION_RESTRICTION;
    /**
     * If the document's schema is an XML Schema [<a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/'>XML Schema Part 1</a>]
     * , this constant represents the derivation by <a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/#key-typeExtension'>
     * extension</a>.
     * <br>  The reference type definition is derived by extension from the
     * other type definition if the other type definition can be reached
     * recursively following the {base type definition} property from the
     * reference type definition, and at least one of the <em>derivation methods</em> involved is an extension.
     */
    public final static int DERIVATION_EXTENSION = TypeInfo.DERIVATION_EXTENSION;
    /**
     * If the document's schema is an XML Schema [<a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/'>XML Schema Part 1</a>]
     * , this constant represents the <a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/#element-union'>
     * union</a> if simple types are involved.
     * <br> The reference type definition is derived by union from the other
     * type definition if there exists two type definitions T1 and T2 such
     * as the reference type definition is derived from T1 by
     * <code>DERIVATION_RESTRICTION</code> or
     * <code>DERIVATION_EXTENSION</code>, T2 is derived from the other type
     * definition by <code>DERIVATION_RESTRICTION</code>, T1 has {variety} <em>union</em>, and one of the {member type definitions} is T2. Note that T1 could be
     * the same as the reference type definition, and T2 could be the same
     * as the other type definition.
     */
    public final static int DERIVATION_UNION = TypeInfo.DERIVATION_UNION;
    /**
     * If the document's schema is an XML Schema [<a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/'>XML Schema Part 1</a>]
     * , this constant represents the <a href='http://www.w3.org/TR/2001/REC-xmlschema-1-20010502/#element-list'>list</a>.
     * <br> The reference type definition is derived by list from the other
     * type definition if there exists two type definitions T1 and T2 such
     * as the reference type definition is derived from T1 by
     * <code>DERIVATION_RESTRICTION</code> or
     * <code>DERIVATION_EXTENSION</code>, T2 is derived from the other type
     * definition by <code>DERIVATION_RESTRICTION</code>, T1 has {variety} <em>list</em>, and T2 is the {item type definition}. Note that T1 could be the same as
     * the reference type definition, and T2 could be the same as the other
     * type definition.
     */
    public final static int DERIVATION_LIST = TypeInfo.DERIVATION_LIST;

    /**
     * Derivation by substitution.
     * This constant, unlike the others, is NOT defined in the DOM level 3 TypeInfo interface.
     */

    public final static int DERIVE_BY_SUBSTITUTION = 16;


}

