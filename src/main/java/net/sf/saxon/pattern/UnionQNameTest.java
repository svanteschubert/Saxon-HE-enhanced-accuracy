////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.StructuredQName;

import java.util.ArrayList;
import java.util.List;

/**
 * A QNameTest that is the union of a number of supplied QNameTests
 */
public class UnionQNameTest implements QNameTest {

    List<QNameTest> tests;

    public UnionQNameTest(List<QNameTest> tests) {
        this.tests = new ArrayList<>(tests);
    }

    /**
     * Test whether the QNameTest matches a given QName
     *
     * @param qname the QName to be matched
     * @return true if the name matches, false if not
     */

    @Override
    public boolean matches(StructuredQName qname) {
        for (QNameTest test : tests) {
            if (test.matches(qname)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test whether the QNameTest matches a given fingerprint
     *
     * @param namePool the name pool
     * @param fp       the fingerprint of the QName to be matched
     * @return true if the name matches, false if not
     */
    @Override
    public boolean matchesFingerprint(NamePool namePool, int fp) {
        for (QNameTest test : tests) {
            if (test.matchesFingerprint(namePool, fp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The toString() method defines the format used in a package export, so it must be re-parseable
     * @return a string representation: the individual qname tests, separated by vertical bar
     */

    public String toString() {
        boolean started = false;
        StringBuilder fsb = new StringBuilder(256);
        for (QNameTest qt : tests) {
            if (started) {
                fsb.append("|");
            } else {
                started = true;
            }
            fsb.append(qt.toString());
        }
        return fsb.toString();
    }

    /**
     * Export the QNameTest as a string for use in a SEF file (typically in a catch clause).
     *
     * @return a string representation of the QNameTest, suitable for use in export files. The format is
     * a sequence of alternatives separated by spaces, where each alternative is one of '*',
     * '*:localname', 'Q{uri}*', or 'Q{uri}local'.
     */
    @Override
    public String exportQNameTest() {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (QNameTest test : tests) {
            if (!first) {
                builder.append(" ");
            } else {
                first = false;
            }
            builder.append(test.exportQNameTest());
        }
        return builder.toString();
    }

}

