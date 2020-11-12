////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.value.StringToDouble11;

/**
 * A Comparer used for comparing sort keys when data-type="number". The items to be
 * compared are converted to numbers, and the numbers are then compared directly. NaN values
 * compare equal to each other, and equal to an empty sequence, but less than anything else.
 * <p>This class is used in XSLT only, so there is no need to handle XQuery's "empty least" vs
 * "empty greatest" options.</p>
 *
 * @author Michael H. Kay
 */

public class NumericComparer11 extends NumericComparer {

    private static NumericComparer11 THE_INSTANCE = new NumericComparer11();

    /*@NotNull*/
    public static NumericComparer getInstance() {
        return THE_INSTANCE;
    }

    protected NumericComparer11() {
        converter = StringToDouble11.getInstance();
    }

    /**
     * Create a string representation of this AtomicComparer that can be saved in a compiled
     * package and used to reconstitute the AtomicComparer when the package is reloaded
     *
     * @return a string representation of the AtomicComparer
     */
    @Override
    public String save() {
        return "NC11";
    }
}

