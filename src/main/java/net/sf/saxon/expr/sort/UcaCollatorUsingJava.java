////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.lib.SubstringMatcher;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.StringConverter;
import net.sf.saxon.type.ValidationFailure;
import net.sf.saxon.value.AnyURIValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.CollationElementIterator;
import java.text.CollationKey;
import java.text.Collator;
import java.text.RuleBasedCollator;
import java.util.*;


/**
 * This class implements (an approximation to) the UCA Collation Algorithm
 * relying solely on the built-in Java support (that is, without using the
 * ICU library). This provides a fallback implementation for Saxon-HE, and
 * it is used only when the collation URI does not include the option
 * fallback=no.
 */
public class UcaCollatorUsingJava implements SubstringMatcher {

    private String uri;
    private RuleBasedCollator uca;
    private Strength strengthLevel;
    private Properties properties;


    private static String keywords[] = {"fallback", "lang", "version", "strength",
            "alternate", "backwards", "normalization", "maxVariable",
            "caseLevel", "caseFirst", "numeric", "reorder"}; //, "hiraganaQuaternary"
    private static Set<String> keys = new HashSet<String>(Arrays.asList(keywords));

    /**
     * Create a collation from a given collation URI
     * @param uri the collation URI, in the format defined in the W3C <i>Functions and Operators</i>
     *            specification
     * @throws XPathException if the collation URI does not conform to the W3C rules, or if it
     * requires features that Saxon-HE does not support
     */

    public UcaCollatorUsingJava(String uri) throws XPathException {
        this.uri = uri;
        uca = (RuleBasedCollator) RuleBasedCollator.getInstance();
        setProps(parseProps(uri));
    }

    /**
     * Get the underlying Java Collator object
     * @return the underlying Java Collator
     */

    public RuleBasedCollator getRuleBasedCollator() {
        return uca;
    }

    private void error(String field, String allowed) throws XPathException {
        error("value of " + field + " must be " + allowed);
    }

    private void error(String field, String allowed, String requested) throws XPathException {
        error("value of " + field + " must be " + allowed + ", requested was:" + requested);
    }

    private void error(String message) throws XPathException {
        throw new XPathException("Error in UCA Collation URI " + uri + ": " + message, "FOCH0002");
    }

//    public String show() {
//        if (uca != null) {
//            return " UCAversion=" + uca.getUCAVersion()
//                    + " Version=" + uca.getVersion()
//                    + " lang=" + uca.get
//                    + " french=" + uca.isFrenchCollation()
//                    + " lowerFirst=" + uca.isLowerCaseFirst()
//                    + " upperFirst=" + uca.isUpperCaseFirst()
//                    + " strength=" + uca.getStrength();
//        } else {
//            return "No RuleBasedCollator initialised";
//        }
//    }

    /**
     * Factory method to create a CollationKey that can be used as a proxy for
     * string comparisons under this collation
     * @param source the string whose CollationKey is required
     * @return a CollationKey with the property that two collation keys are equal (according
     * to its {@link CollationKey#equals(Object)} method if their corresponding strings are
     * considered to be equal under this collation
     */
    public CollationKey getJavaCollationKey(String source) {
        return uca.getCollationKey(source);
    }

    /**
     * Get a hash code used to compare two collations. (Probably not used.)
     * @return a suitable hash code
     */

    @Override
    public int hashCode() {
        return uca.hashCode();
    }

    /**
     * Set the properties for the UCA collation
     *
     * @param props the set of properties parsed from the UCA parameters
     * @throws XPathException
     */
    private void setProps(Properties props) throws XPathException {
        this.properties = props;
        boolean fallbackError = false;
        String fallback = props.getProperty("fallback");
        if (fallback != null) {
            switch (fallback) {
                case "yes":
                    break;
                case "no":
                    error("fallback=no is not supported in Saxon-HE");
                    break;
                default:
                    error("fallback", "yes|no");
                    break;
            }
        }

        String lang = props.getProperty("lang");
        if (lang != null && !lang.isEmpty()) {
            ValidationFailure vf = StringConverter.StringToLanguage.INSTANCE.validate(lang);
            if (vf != null) {
                error("lang", "a valid language code");
            }
            String language, country = "", variant = "";
            String[] parts = lang.split("-");
            language = parts[0];
            if (parts.length > 1) {
                country = parts[1];
            }
            if (parts.length > 2) {
                variant = parts[2];
            }
            Locale loc = new Locale(language, country, variant);
            uca = (RuleBasedCollator) Collator.getInstance(loc);
        }

        String strength = props.getProperty("strength");
        if (strength != null) {
            switch (strength) {
                case "primary":
                case "1":
                    setStrength(Collator.PRIMARY);
                    break;
                case "secondary":
                case "2":
                    setStrength(Collator.SECONDARY);
                    break;
                case "tertiary":
                case "3":
                    setStrength(Collator.TERTIARY);
                    break;
                case "quaternary":
                case "4":
                    setStrength(Collator.IDENTICAL); // fallback to nearest supported option

                    break;
                case "identical":
                case "5":
                    setStrength(Collator.IDENTICAL);
                    break;
            }
        }

        String normalization = props.getProperty("normalization");
        if (normalization != null) {
            if (normalization.equals("yes")) {
                uca.setDecomposition(java.text.Collator.CANONICAL_DECOMPOSITION);
            } else if (normalization.equals("no")) {
                uca.setDecomposition(java.text.Collator.NO_DECOMPOSITION);
            }
        }
    }

    /**
     * Get the properties of the collation
     * @return the properties of the collation
     */

    public Properties getProperties() {
        return properties;
    }

    private Properties parseProps(String uri) throws XPathException {
        URI uuri;
        try {
            uuri = new URI(uri);
        } catch (URISyntaxException err) {
            throw new XPathException(err);
        }
        ArrayList<String> unknownKeys = new ArrayList<String>();
        Properties props = new Properties();
        String query = AnyURIValue.decode(uuri.getRawQuery());
        if (query != null && !query.isEmpty()) {
            for (String s : query.split(";")) {
                String[] tokens = s.split("=");
                if (!keys.contains(tokens[0])) {
                    unknownKeys.add(tokens[0]);
                }
                props.setProperty(tokens[0], tokens[1]);
            }
        }
        String fallback = props.getProperty("fallback");
        if (fallback != null && fallback.equals("no") && !unknownKeys.isEmpty()) {
            StringBuilder message = new StringBuilder(unknownKeys.size() > 1 ? "unknown parameters:" : "unknown parameter:");
            for (String u : unknownKeys) {
                message.append(u).append(" ");
            }
            error(message.toString());
        }
        return props;
    }

    /**
     * Set the strength of the collation (primary, secondary, tertiary, etc)
     * @param newStrength the strength
     */

    public void setStrength(int newStrength) {
        uca.setStrength(newStrength);
    }

    /**
     * Get the strength of the collation (primary, secondary, tertiary, etc)
     * @return the strength
     */

    public int getStrength() {
        return uca.getStrength();
    }

    /**
     * Ask whether two strings are considered equal under this collation
     * @param s1 the first string
     * @param s2 the second string
     * @return true if the strings are considered equal
     */
    @Override
    public boolean comparesEqual(CharSequence s1, CharSequence s2) {
        return uca.compare(s1, s2) == 0;
    }

    /**
     * Get the collation URI corresponding to this collation
     * @return the collation URI
     */

    @Override
    public String getCollationURI() {
        return uri;
    }

    /**
     * Compare two strings for equality or ordering under the rules of this collation
     * @param o1 the first string
     * @param o2 the second string
     * @return -1, 0, or +1 according to the relative ordering of the strings
     */

    @Override
    public int compareStrings(CharSequence o1, CharSequence o2) {
        return uca.compare(o1, o2);
    }

    /**
     * Get a collation key that can be used as a proxy for strings being compared
     * @param s the string whose collation key is required
     * @return a collation key
     */

    @Override
    public AtomicMatchKey getCollationKey(CharSequence s) {
        CollationKey ck = uca.getCollationKey(s.toString());
        return new CollationMatchKey(ck);
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
        makeStrengthObject();
        while (true) {
            int e0, e1;
            e1 = s1.next();
            if (e1 == CollationElementIterator.NULLORDER) {
                return true;
            }
            e0 = s0.next();
            if (e0 == CollationElementIterator.NULLORDER) {
                return false;
            }
            if (strengthLevel.compare(e0, e1) != 0) {
                return false;
            }
        }
    }

    private String show(int ce) {
        return "" + CollationElementIterator.primaryOrder(ce) + "/" +
                CollationElementIterator.secondaryOrder(ce) + "/" +
                CollationElementIterator.tertiaryOrder(ce);
    }

    private void makeStrengthObject() {
        if (strengthLevel == null) {
            switch (getStrength()) {
                case com.ibm.icu.text.Collator.PRIMARY:
                    strengthLevel = new Primary();
                    break;
                case com.ibm.icu.text.Collator.SECONDARY:
                    strengthLevel = new Secondary();
                    break;
                case com.ibm.icu.text.Collator.TERTIARY:
                    strengthLevel = new Tertiary();
                    break;
                default:
                    strengthLevel = new Identical();
                    break;
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
        makeStrengthObject();
        int e0, e1;
        e1 = s1.next();
        if (e1 == CollationElementIterator.NULLORDER) {
            return true;
        }
        e0 = CollationElementIterator.NULLORDER;
        while (true) {
            // scan the first string to find a matching character
            while (strengthLevel.compare(e0, e1) != 0) {
                e0 = s0.next();
                if (e0 == CollationElementIterator.NULLORDER) {
                    // hit the end, no match
                    return false;
                }
            }
            // matched first character, note the position of the possible match
            int start = s0.getOffset();
            if (collationStartsWith(s0, s1)) {
                if (matchAtEnd) {
                    e0 = s0.next();
                    if (e0 == CollationElementIterator.NULLORDER) {
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
            e1 = s1.next();
            // loop round to try again
        }
    }


    private interface Strength {
        int compare(int ce1, int ce2);
    }

    private class Primary implements Strength {
        @Override
        public int compare(int ce1, int ce2) {
            return Integer.compare(CollationElementIterator.primaryOrder(ce1), CollationElementIterator.primaryOrder(ce2));
        }

    }

    private class Secondary implements Strength {
        @Override
        public int compare(int ce1, int ce2) {
            int c1 = Integer.compare(CollationElementIterator.primaryOrder(ce1), CollationElementIterator.primaryOrder(ce2));
            if (c1 == 0) {
                return Integer.compare((int) CollationElementIterator.secondaryOrder(ce1), (int) CollationElementIterator.secondaryOrder(ce2));
            } else {
                return c1;
            }
        }

    }

    private class Tertiary implements Strength {
        @Override
        public int compare(int ce1, int ce2) {
            int c1 = Integer.compare(CollationElementIterator.primaryOrder(ce1), CollationElementIterator.primaryOrder(ce2));
            if (c1 == 0) {
                int c2 = Integer.compare((int) CollationElementIterator.secondaryOrder(ce1), (int) CollationElementIterator.secondaryOrder(ce2));
                if (c2 == 0) {
                    return Integer.compare((int) CollationElementIterator.tertiaryOrder(ce1), (int) CollationElementIterator.tertiaryOrder(ce2));
                } else {
                    return c2;
                }
            } else {
                return c1;
            }
        }

    }

    private class Identical implements Strength {
        @Override
        public int compare(int ce1, int ce2) {
            return Integer.compare(ce1, ce2);
        }

    }


}
