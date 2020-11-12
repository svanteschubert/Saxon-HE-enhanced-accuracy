////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.serialize.charcode.XMLCharacterData;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;

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
     * @param name the name to be tested
     * @return true if the name is a lexically-valid QName
     */

    public static boolean isQName(String name) {
        int colon = name.indexOf(':');
        if (colon < 0) {
            return isValidNCName(name);
        }
        return colon != 0 &&
                colon != name.length() - 1 &&
                isValidNCName(name.substring(0, colon)) &&
                isValidNCName(name.substring(colon + 1));
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

    /**
     * Validate a QName, and return the prefix and local name. The local name is checked
     * to ensure it is a valid NCName. The prefix is not checked, on the theory that the caller
     * will look up the prefix to find a URI, and if the prefix is invalid, then no URI will
     * be found.
     *
     * @param qname the lexical QName whose parts are required. Note that leading and trailing
     *              whitespace is not permitted
     * @return an array of two strings, the prefix and the local name. The first
     *         item is a zero-length string if there is no prefix.
     * @throws QNameException if not a valid QName.
     */

    public static String[] getQNameParts(CharSequence qname) throws QNameException {
        String[] parts = new String[2];
        int colon = -1;
        int len = qname.length();
        for (int i = 0; i < len; i++) {
            if (qname.charAt(i) == ':') {
                colon = i;
                break;
            }
        }
        if (colon < 0) {
            parts[0] = "";
            parts[1] = qname.toString();
            if (!isValidNCName(parts[1])) {
                throw new QNameException("Invalid QName " + Err.wrap(qname));
            }
        } else {
            if (colon == 0) {
                throw new QNameException("QName cannot start with colon: " + Err.wrap(qname));
            }
            if (colon == len - 1) {
                throw new QNameException("QName cannot end with colon: " + Err.wrap(qname));
            }
            parts[0] = qname.subSequence(0, colon).toString();
            parts[1] = qname.subSequence(colon + 1, len).toString();

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
    public static String[] checkQNameParts(CharSequence qname) throws XPathException {
        try {
            String[] parts = getQNameParts(qname);
            if (parts[0].length() > 0 && !isValidNCName(parts[0])) {
                throw new XPathException("Invalid QName prefix " + Err.wrap(parts[0]));
            }
            return parts;
        } catch (QNameException e) {
            XPathException err = new XPathException(e.getMessage());
            err.setErrorCode("FORG0001");
            throw err;
        }
    }

    /**
     * Validate whether a given string constitutes a valid NCName, as defined in XML Namespaces.
     *
     * @param ncName the name to be tested. Any whitespace trimming must have already been applied.
     * @return true if the name is a lexically-valid QName
     */

    public static boolean isValidNCName(CharSequence ncName) {
        if (ncName.length() == 0) {
            return false;
        }
        int s = 1;
        char ch = ncName.charAt(0);
        if (UTF16CharacterSet.isHighSurrogate(ch)) {
            if (!isNCNameStartChar(UTF16CharacterSet.combinePair(ch, ncName.charAt(1)))) {
                return false;
            }
            s = 2;
        } else {
            if (!isNCNameStartChar(ch)) {
                return false;
            }
        }
        for (int i = s; i < ncName.length(); i++) {
            ch = ncName.charAt(i);
            if (UTF16CharacterSet.isHighSurrogate(ch)) {
                if (!isNCNameChar(UTF16CharacterSet.combinePair(ch, ncName.charAt(++i)))) {
                    return false;
                }
            } else {
                if (!isNCNameChar(ch)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check to see if a string is a valid Nmtoken according to [7]
     * in the XML 1.0 Recommendation
     *
     * @param nmtoken the string to be tested. Any whitespace trimming must have already been applied.
     * @return true if nmtoken is a valid Nmtoken
     */

    public static boolean isValidNmtoken(CharSequence nmtoken) {
        if (nmtoken.length() == 0) {
            return false;
        }
        for (int i = 0; i < nmtoken.length(); i++) {
            char ch = nmtoken.charAt(i);
            if (UTF16CharacterSet.isHighSurrogate(ch)) {
                if (!isNCNameChar(UTF16CharacterSet.combinePair(ch, nmtoken.charAt(++i)))) {
                    return false;
                }
            } else {
                if (ch != ':' && !isNCNameChar(ch)) {
                    return false;
                }
            }
        }
        return true;
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

