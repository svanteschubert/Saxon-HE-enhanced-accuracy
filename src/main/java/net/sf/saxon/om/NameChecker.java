////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.serialize.charcode.XMLCharacterData;
import net.sf.saxon.str.StringTool;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.z.IntIterator;

/**
 * The NameChecker performs validation and analysis of XML names.
 *
 * <p>In releases prior to 9.6, there were two name checkers, one for XML 1.0 and
 * one for XML 1.1. However, XML 1.0 fifth edition uses the same rules for XML names
 * as XML 1.1, so they were actually checking the same rules for names (although they
 * were different when checking for valid characters). From 9.6, the name checker
 * no longer performs checks for valid XML characters, so only one name checker is
 * needed, and the methods have become static.</p>
 */

public abstract class NameChecker {

    /**
     * Validate whether a given string constitutes a valid QName, as defined in XML Namespaces.
     * Note that this does not test whether the prefix is actually declared.
     *
     * @param codePoints the name to be tested, supplied as a codepoint iterator
     * @return true if the name is a lexically-valid QName
     */

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isQName(IntIterator codePoints) {
        boolean atStart = true;
        boolean foundColon = false;
        while (codePoints.hasNext()) {
            int ch = codePoints.next();
            if (ch == ':') {
                if (atStart || foundColon) {
                    return false;
                }
                atStart = true;
                foundColon = true;
            } else {
                if (atStart) {
                    if (!isNCNameStartChar(ch)) {
                        return false;
                    }
                    atStart = false;
                } else {
                    if (!isNCNameChar(ch)) {
                        return false;
                    }
                }
            }
        }
        return !atStart;
    }

    /**
     * Extract the prefix from a QName. Note, the QName is assumed to be valid.
     *
     * @param qname The lexical QName whose prefix is required
     * @return the prefix, that is the part before the colon. Returns an empty
     *         string if there is no prefix
     */

    public static String getPrefix(String qname) {
        int colon = qname.indexOf(':');
        if (colon < 0) {
            return "";
        }
        return qname.substring(0, colon);
    }

    public static String[] getQNameParts(String qname) throws QNameException {
        String[] parts = new String[2];
        int len = qname.length();
        int colon = qname.indexOf(':', 0);
        if (colon < 0) {
            parts[0] = "";
            parts[1] = qname;
            if (!isValidNCName(StringTool.codePoints(qname))) {
                throw new QNameException("Invalid QName " + Err.wrap(qname));
            }
        } else {
            if (colon == 0) {
                throw new QNameException("QName cannot start with colon: " + Err.wrap(qname));
            }
            if (colon == len - 1) {
                throw new QNameException("QName cannot end with colon: " + Err.wrap(qname));
            }
            parts[0] = qname.substring(0, colon);
            parts[1] = qname.substring(colon + 1);

            if (!isValidNCName(parts[1])) {
                if (!isValidNCName(parts[0])) {
                    throw new QNameException("Both the prefix " + Err.wrap(parts[0]) +
                                                     " and the local part " + Err.wrap(parts[1]) + " are invalid");
                }
                throw new QNameException("Invalid QName local part " + Err.wrap(parts[1]));
            }
        }
        return parts;
    }


    /**
     * Validate a QName, and return the prefix and local name. Both parts are checked
     * to ensure they are valid NCNames.
     * <p><i>Used from compiled code</i></p>
     *
     * @param qname the lexical QName whose parts are required. Note that leading and trailing
     *              whitespace is not permitted
     * @return an array of two strings, the prefix and the local name. The first
     *         item is a zero-length string if there is no prefix.
     * @throws XPathException if not a valid QName.
     */

    /*@NotNull*/
    public static String[] checkQNameParts(String qname) throws XPathException {
        try {
            String[] parts = getQNameParts(qname);
            if (parts[0].length() > 0 && !isValidNCName(parts[0])) {
                throw new XPathException("Invalid QName prefix " + Err.wrap(parts[0]));
            }
            return parts;
        } catch (QNameException e) {
            throw new XPathException(e.getMessage(), "FORG0001");
        }
    }

    /**
     * Validate whether a given string constitutes a valid NCName, as defined in XML Namespaces.
     *
     * @param codePoints the name to be tested, as a codepoint iterator.
     *                   Any whitespace trimming must have already been applied.
     * @return true if the name is a lexically-valid QName
     */

    public static boolean isValidNCName(IntIterator codePoints) {
        boolean first = true;
        while (codePoints.hasNext()) {
            int ch = codePoints.next();
            if (first) {
                if (!isNCNameStartChar(ch)) {
                    return false;
                }
                first = false;
            } else {
                if (!isNCNameChar(ch)) {
                    return false;
                }
            }
        }
        return !first;
    }

    public static boolean isValidNCName(String str) {
        return isValidNCName(StringTool.codePoints(str));
    }

    /**
     * Check to see if a string is a valid Nmtoken according to [7]
     * in the XML 1.0 Recommendation
     *
     * @param in the string to be tested.
     *                Any whitespace trimming must have already been applied.
     * @return true if nmtoken is a valid Nmtoken
     */

    public static boolean isValidNmtoken(UnicodeString in) {
        IntIterator codePoints = in.codePoints();
        boolean empty = true;
        while (codePoints.hasNext()) {
            int ch = codePoints.next();
            empty = false;
            if (ch != ':' && !isNCNameChar(ch)) {
                return false;
            }
        }
        return !empty;
    }


    /**
     * Test whether a character can appear in an NCName
     *
     * @param ch the character to be tested
     * @return true if this is a valid character in an NCName. The rules for XML 1.0 fifth
     *         edition are the same as the XML 1.1 rules, and these are the rules that we use.
     */

    public static boolean isNCNameChar(int ch) {
        return XMLCharacterData.isNCName11(ch);
    }

    /**
     * Test whether a character can appear at the start of an NCName
     *
     * @param ch the character to be tested
     * @return true if this is a valid character at the start of an NCName. The rules for XML 1.0 fifth
     *         edition are the same as the XML 1.1 rules, and these are the rules that we use.
     */

    public static boolean isNCNameStartChar(int ch) {
        return XMLCharacterData.isNCNameStart11(ch);
    }

}

