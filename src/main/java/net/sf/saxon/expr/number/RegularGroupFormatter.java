////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.number;

import net.sf.saxon.str.StringTool;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeString;

/**
 * A RegularGroupFormatter is a NumericGroupFormatter that inserts a separator
 * at constant intervals through a number: for example, a comma after every three
 * digits counting from the right.
 */

public class RegularGroupFormatter extends NumericGroupFormatter {

    private final int groupSize;
    private final String groupSeparator;

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
    public String format(/*@NotNull*/ String value) {
        if (groupSize > 0 && groupSeparator.length() > 0) {
            UnicodeString valueEx = StringView.tidy(value);
            StringBuilder temp = new StringBuilder(16);
            for (int i = valueEx.length32() - 1, j = 0; i >= 0; i--, j++) {
                if (j != 0 && (j % groupSize) == 0) {
                    temp.insert(0, groupSeparator);
                }
                StringTool.prependWideChar(temp, valueEx.codePointAt(i));
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

