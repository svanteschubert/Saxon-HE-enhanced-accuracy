////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.number;

import net.sf.saxon.regex.UnicodeString;
import net.sf.saxon.tree.util.FastStringBuffer;

/**
 * A RegularGroupFormatter is a NumericGroupFormatter that inserts a separator
 * at constant intervals through a number: for example, a comma after every three
 * digits counting from the right.
 */

public class RegularGroupFormatter extends NumericGroupFormatter {

    private int groupSize;
    private String groupSeparator;

    /**
     * Create a RegularGroupFormatter
     *
     * @param grpSize         the grouping size. If zero, no grouping separators are inserted
     * @param grpSep          the grouping separator (normally but not necessarily a single character)
     * @param adjustedPicture The picture, adjusted to conform to the rules of the xsl:number function,
     *                        which means the picture supplied to format-integer minus any modifiers, and minus grouping separators
     *                        and optional-digit signs
     */

    public RegularGroupFormatter(int grpSize, String grpSep, UnicodeString adjustedPicture) {
        groupSize = grpSize;
        groupSeparator = grpSep;
        this.adjustedPicture = adjustedPicture;
    }

    @Override
    public String format(/*@NotNull*/ FastStringBuffer value) {
        if (groupSize > 0 && groupSeparator.length() > 0) {
            UnicodeString valueEx = UnicodeString.makeUnicodeString(value);
            FastStringBuffer temp = new FastStringBuffer(FastStringBuffer.C16);
            for (int i = valueEx.uLength() - 1, j = 0; i >= 0; i--, j++) {
                if (j != 0 && (j % groupSize) == 0) {
                    temp.prepend(groupSeparator);
                }
                temp.prependWideChar(valueEx.uCharAt(i));
            }
            return temp.toString();
        } else {
            return value.toString();
        }
    }

    /**
     * Get the grouping separator to be used
     *
     * @return the grouping separator
     */
    @Override
    public String getSeparator() {
        return groupSeparator;
    }
}

