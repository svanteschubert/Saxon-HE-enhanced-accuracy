////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.type.Affinity;

import java.util.*;

/**
 * A QNameTest that is the union of a number of supplied QNameTests
 */
public class UnionQNameTest implements QNameTest {

    private final Set<QNameTest> tests;

    public UnionQNameTest(List<QNameTest> tests) {
        this.tests = new HashSet<>(tests);
    }

    public UnionQNameTest(QNameTest... test) {
        this.tests = new HashSet<>(Arrays.asList(test));
    }

    public Set<QNameTest> getTests() {
        return tests;
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

    public Affinity relationship(QNameTest other) {

        // Turn the RHS into a singleton union where necessary
        UnionQNameTest rhs = other instanceof UnionQNameTest ? (UnionQNameTest)other : new UnionQNameTest(other);

        // Algorithm:
        // * If the two sets of predicates are equal, the result is SAME_TYPE.
        // * If every predicate on the RHS is subsumed by (or same as) a predicate on the LHS, the result is SUBSUMES.
        // * If every predicate on the LHS is subsumed by (or same as) a predicate on the RHS, the result is SUBSUMED_BY.
        // * If every pair of predicates from the LHS and RHS are disjoint, the result is DISJOINT,
        // * Otherwise, the result is OVERLAP.

        // Step 1:
        if (samePredicates(this, rhs)) {
            return Affinity.SAME_TYPE;
        }
        if (allSubsumed(rhs, this)) {
            return Affinity.SUBSUMES;
        }
        if (allSubsumed(this, rhs)) {
            return Affinity.SUBSUMED_BY;
        }
        if (allDisjoint(this, rhs)) {
            return Affinity.DISJOINT;
        }
        return Affinity.OVERLAPS;
    }

    private static boolean samePredicates(UnionQNameTest lhs, UnionQNameTest rhs) {
        return lhs.tests.equals(rhs.tests);
    }

    private static boolean allSubsumed(UnionQNameTest lhs, UnionQNameTest rhs) {
        for (QNameTest test1 : lhs.tests) {
            boolean found = false;
            for (QNameTest test2 : rhs.tests) {
                Affinity a = test1.relationship(test2);
                if (a == Affinity.SAME_TYPE || a == Affinity.SUBSUMED_BY) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean allDisjoint(UnionQNameTest lhs, UnionQNameTest rhs) {
        for (QNameTest test1 : lhs.tests) {
            for (QNameTest test2 : rhs.tests) {
                Affinity a = test1.relationship(test2);
                if (a != Affinity.DISJOINT) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UnionQNameTest) {
            UnionQNameTest rhs = (UnionQNameTest) obj;
            return tests.size() == rhs.tests.size() && tests.containsAll(rhs.tests);
        } else {
            return false;
        }

    }

    @Override
    public int hashCode() {
        int h = 0x57832abc + tests.size();
        for (QNameTest t : tests) {
            h ^= t.hashCode();
        }
        return h;
    }
    

}

