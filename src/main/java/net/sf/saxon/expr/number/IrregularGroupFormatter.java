////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.number;

import net.sf.saxon.str.StringTool;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntSet;

import java.util.List;

/**
 * Handles grouping separators when formatting a number in cases where the grouping separators are
 * not at regular intervals
 */

public class IrregularGroupFormatter extends NumericGroupFormatter {

    private final IntSet groupingPositions;
    private final List<Integer> separators;

    /**
     * Create a formatter for numbers where the grouping separators occur at irregular positions
     *
     * @param groupingPositions the positions where the separators are to be inserted
     * @param sep               array holding the separators to be inserted, as Unicode codepoints, in order starting
     *                          with the right-most
     * @param adjustedPicture   the formatting picture, after expansion and removal of grouping separators
     */

    public IrregularGroupFormatter(IntSet groupingPositions, List<Integer> sep, UnicodeString adjustedPicture) {
        this.groupingPositions = groupingPositions;
        separators = sep;
        this.adjustedPicture = adjustedPicture;
    }

    @Override
    public String format(String value) {
        StringValue in = new StringValue(value);
        int l, m = 0;
        for (l = 0; l < in.length(); l++) {
            if (groupingPositions.contains(l)) {
                m++;
            }
        }
        int[] out = new int[in.length32() + m];
        int j = 0;
        int k = out.length - 1;
        for (int i = in.length32() - 1; i >= 0; i--) {
            out[k--] = in.getContent().codePointAt(i);
            if ((i > 0) && groupingPositions.contains(in.length32() - i)) {
                out[k--] = separators.get(j++);
            }
        }
        return StringTool.fromCodePoints(out, out.length).toString();
    }

    /**
     * Get the grouping separator to be used. If more than one is used, return the last.
     * If no grouping separators are used, return null
     *
     * @return the grouping separator
     */
    @Override
    public String getSeparator() {
        if (separators.size() == 0) {
            return null;
        } else {
            int sep = separators.get(separators.size() - 1);
            StringBuilder fsb = new StringBuilder(16);
            fsb.appendCodePoint(sep);
            return fsb.toString();
        }
    }
}

