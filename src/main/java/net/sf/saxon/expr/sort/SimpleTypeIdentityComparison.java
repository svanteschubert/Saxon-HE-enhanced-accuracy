////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.om.AtomicSequence;

/**
 * This class implements equality and identity comparisons between values of simple types:
 * that is, atomic values, and sequences of atomic values, following the XSD-defined rules
 * for equality and identity comparisons. These are not always the same as the XPath-defined
 * rules. In particular, under XSD rules values are generally only comparable with others
 * of the same primitive type: a double and a float, for example, never compare equal.
 *
 * <p>In practice, identity comparison delivers the same result as equality comparison
 * in all cases except NaN: NaN is identical to itself but not equal to itself.</p>
 *
 * <p>This class is a singleton that cannot be externally instantiated.</p>
 */

public class SimpleTypeIdentityComparison extends SimpleTypeComparison {

    private static final SimpleTypeIdentityComparison INSTANCE = new SimpleTypeIdentityComparison();

    /**
     * Get the singleton instance of this class
     * @return  the singleton instance
     */
    
    public static SimpleTypeIdentityComparison getInstance() {
        return INSTANCE;
    }

    /**
     * Test whether two values are equal or identical under XSD rules.
     * @param a the first value
     * @param b the second value
     * @return true if the two values are equal under XSD rules, or if they are identical under XSD rules;
     * false in all other cases.
     */

    @Override
    public boolean equal(AtomicSequence a, AtomicSequence b) {
        return super.equalOrIdentical(a, b);
    }

}

