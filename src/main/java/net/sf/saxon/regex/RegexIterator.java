////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;


/**
 * This interface defines an iterator that supports the evaluation of xsl:analyze-string.
 * It returns all the matching and non-matching substrings in an input string, and
 * provides access to their captured groups
 */
public interface RegexIterator extends SequenceIterator {

    /**
     * Get the next item in the sequence. This method changes the state of the
     * iterator.
     */
    @Override
    StringValue next();

    /**
     * Determine whether the current item in the sequence is a matching item or a non-matching item
     *
     * @return true if the current item is a matching item
     */

    boolean isMatching();

    /**
     * Get the number of captured groups in the current matching item
     */

    int getNumberOfGroups();

    /**
     * Get a substring that matches a parenthesised group within the regular expression
     *
     * @param number the number of the group to be obtained
     * @return the substring of the current item that matches the n'th parenthesized group
     *         within the regular expression
     */

    /*@Nullable*/
    UnicodeString getRegexGroup(int number);

    /**
     * Process a matching substring, performing specified actions at the start and end of each matching
     * group
     */

    void processMatchingSubstring(RegexMatchHandler action) throws XPathException;


}

