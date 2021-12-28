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
 * A NumericGroupFormatter is responsible for insertion of grouping separators
 * into a formatted number (for example, reformatting "1234" as "1,234").
 */

public abstract class NumericGroupFormatter {

    protected UnicodeString adjustedPicture;

    /**
     * Get the adjusted (preprocessed) picture
     *
     * @return the adjusted picture
     */

    public UnicodeString getAdjustedPicture() {
        return adjustedPicture;
    }

    /**
     * Reformat a number to add grouping separators
     *
     * @param value a buffer holding the number to be reformatted
     * @return the reformatted number
     */

    public abstract String format(FastStringBuffer value);

    /**
     * Get the grouping separator to be used, as a Unicode codepoint. If more than one is used, return the last.
     * If no grouping separators are used, return null
     *
     * @return the grouping separator
     */

    /*@Nullable*/
    public abstract String getSeparator();
}

