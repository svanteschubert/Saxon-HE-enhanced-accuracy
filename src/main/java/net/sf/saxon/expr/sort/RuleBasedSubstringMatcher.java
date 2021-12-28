////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.tree.util.FastStringBuffer;

import java.text.CollationElementIterator;
import java.text.RuleBasedCollator;

/**
 * This class wraps a RuleBasedCollator to provide a SubstringMatcher. This
 * users the facilities offered by the RuleBasedCollator to implement the XPath
 * functions contains(), starts-with(), ends-with(), substring-before(), and
 * substring-after().
 */
public class RuleBasedSubstringMatcher extends SimpleCollation implements SubstringMatcher {

    /**
     * Create a RuleBasedSubstringMatcher
     * @param uri
     * @param collator the collation to be used
     */

    public RuleBasedSubstringMatcher(String uri, RuleBasedCollator collator) {
        super(uri, collator);
    }

    private RuleBasedCollator getRuleBasedCollator() {
        return (RuleBasedCollator) getComparator();
    }

    /**
     * Test whether one string contains another, according to the rules
     * of the XPath contains() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return true iff s1 contains s2
     */

    @Override
    public boolean contains(String s1, String s2) {
        RuleBasedCollator collator = getRuleBasedCollator();
        CollationElementIterator iter1 = collator.getCollationElementIterator(s1);
        CollationElementIterator iter2 = collator.getCollationElementIterator(s2);
        return collationContains(iter1, iter2, null, false);
    }

    /**
     * Test whether one string ends with another, according to the rules
     * of the XPath ends-with() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return true iff s1 ends with s2
     */

    @Override
    public boolean endsWith(String s1, String s2) {
        RuleBasedCollator collator = getRuleBasedCollator();
        CollationElementIterator iter1 = collator.getCollationElementIterator(s1);
        CollationElementIterator iter2 = collator.getCollationElementIterator(s2);
        return collationContains(iter1, iter2, null, true);
    }

    /**
     * Test whether one string starts with another, according to the rules
     * of the XPath starts-with() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return true iff s1 starts with s2
     */

    @Override
    public boolean startsWith(String s1, String s2) {
        RuleBasedCollator collator = getRuleBasedCollator();
        CollationElementIterator iter1 = collator.getCollationElementIterator(s1);
        CollationElementIterator iter2 = collator.getCollationElementIterator(s2);
        return collationStartsWith(iter1, iter2);
    }

    /**
     * Return the part of a string after a given substring, according to the rules
     * of the XPath substring-after() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return the part of s1 that follows the first occurrence of s2
     */

    @Override
    public String substringAfter(String s1, String s2) {
        RuleBasedCollator collator = getRuleBasedCollator();
        CollationElementIterator iter1 = collator.getCollationElementIterator(s1);
        CollationElementIterator iter2 = collator.getCollationElementIterator(s2);
        int[] ia = new int[2];
        boolean ba = collationContains(iter1, iter2, ia, false);
        if (ba) {
            return s1.substring(ia[1]);
        } else {
            return "";
        }
    }

    /**
     * Return the part of a string before a given substring, according to the rules
     * of the XPath substring-before() function
     *
     * @param s1 the containing string
     * @param s2 the contained string
     * @return the part of s1 that precedes the first occurrence of s2
     */

    @Override
    public String substringBefore(String s1, String s2) {
        RuleBasedCollator collator = getRuleBasedCollator();
        CollationElementIterator iter1 = collator.getCollationElementIterator(s1);
        CollationElementIterator iter2 = collator.getCollationElementIterator(s2);
        int[] ib = new int[2];
        boolean bb = collationContains(iter1, iter2, ib, false);
        if (bb) {
            return s1.substring(0, ib[0]);
        } else {
            return "";
        }
    }

    /**
     * Determine whether one string starts with another, under the terms of a given
     * collating sequence.
     *
     * @param s0 iterator over the collation elements of the containing string
     * @param s1 iterator over the collation elements of the contained string
     * @return true if the first string starts with the second
     */

    private boolean collationStartsWith(CollationElementIterator s0,
                                        CollationElementIterator s1) {
        while (true) {
            int e0, e1;
            do {
                e1 = s1.next();
            } while (e1 == 0);
            if (e1 == -1) {
                return true;
            }
            do {
                e0 = s0.next();
            } while (e0 == 0);
            if (e0 != e1) {
                return false;
            }
        }
    }

    /**
     * Determine whether one string contains another, under the terms of a given
     * collating sequence. If matchAtEnd=true, the match must be at the end of the first
     * string.
     *
     * @param s0         iterator over the collation elements of the containing string
     * @param s1         iterator over the collation elements of the contained string
     * @param offsets    may be null, but if it is supplied, it must be an array of two
     *                   integers which, if the function returns true, will contain the start position of the
     *                   first matching substring, and the offset of the first character after the first
     *                   matching substring. This is not available for matchAtEnd=true
     * @param matchAtEnd true if the match is required to be at the end of the string
     * @return true if the first string contains the second
     */

    private boolean collationContains(CollationElementIterator s0,
                                      CollationElementIterator s1,
                                      /*@Nullable*/ int[] offsets,
                                      boolean matchAtEnd) {
        int e0, e1;
        do {
            e1 = s1.next();
        } while (e1 == 0);
        if (e1 == -1) {
            return true;
        }
        e0 = -1;
        while (true) {
            // scan the first string to find a matching character
            while (e0 != e1) {
                do {
                    e0 = s0.next();
                } while (e0 == 0);
                if (e0 == -1) {
                    // hit the end, no match
                    return false;
                }
            }
            // matched first character, note the position of the possible match
            int start = s0.getOffset();
            if (collationStartsWith(s0, s1)) {
                if (matchAtEnd) {
                    do {
                        e0 = s0.next();
                    } while (e0 == 0);
                    if (e0 == -1) {
                        // the match is at the end
                        return true;
                    }
                    // else ignore this match and keep looking
                } else {
                    if (offsets != null) {
                        offsets[0] = start - 1;
                        offsets[1] = s0.getOffset();
                    }
                    return true;
                }
            }
            // reset the position and try again
            s0.setOffset(start);

            // workaround for a difference between JDK 1.4.0 and JDK 1.4.1
            if (s0.getOffset() != start) {
                // JDK 1.4.0 takes this path
                s0.next();
            }
            s1.reset();
            e0 = -1;
            do {
                e1 = s1.next();
            } while (e1 == 0);
            // loop round to try again
        }
    }


    /**
     * Get a collation key for two Strings. The essential property of collation keys
     * is that if two values are equal under the collation, then the collation keys are
     * compare correctly under the equals() method.
     * @param s
     */

    @Override
    public AtomicMatchKey getCollationKey(CharSequence s) {
        return new CollationMatchKey(getRuleBasedCollator().getCollationKey(s.toString()));
    }


    /**
     * Test program to output the sequence of collation element iterators for a given input string
     *
     * @param args command line arguments (collationURI, test-string)
     */
    public static void main(String[] args) throws Exception {
        String rules = " ='-'='*'< a < b < c < d < e < f < g < h < i < j < k < l < m < n < o < p < q < r < s < t < u < v < w < x < y < z";
        RuleBasedCollator collator = new RuleBasedCollator(rules);

        for (int i = 0; i < args.length; i++) {
            System.err.println(args[i]);
            FastStringBuffer sb = new FastStringBuffer(FastStringBuffer.C256);
            CollationElementIterator iter = collator.getCollationElementIterator(args[i]);
            while (true) {
                int e = iter.next();
                if (e == -1) {
                    break;
                }
                sb.append(e + " ");
            }
            System.err.println(sb.toString());
        }


    }


}

