////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize.charcode;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharp;

import javax.xml.transform.OutputKeys;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * This class delivers a CharacterSet object for a given named encoding.
 * <p>It maintains a mapping from character set names to class names, and a separate mapping
 * from character set names to instances of those classes. This means that a class is not
 * actually instantiated until the encoding is used, but once instantiated, the same instance
 * is used whenever that encoding is used again in the same Configuration.</p>
 * <p>Note that the purpose of the CharacterSet object is only to record which Unicode
 * characters are represented in the encoding, so that non-encodable characters can
 * be represented as XML or HTML character references. The actual translation from Unicode
 * codepoints to bytes in the chosen encoding is left to the Java IO library.</p>
 */


public class CharacterSetFactory {

    private final HashMap<String, CharacterSet> characterSets = new HashMap<>(10);

    /**
     * Class has a single instance per Configuration
     */
    public CharacterSetFactory() {
        HashMap<String, CharacterSet> c = characterSets;
        UTF8CharacterSet utf8 = UTF8CharacterSet.getInstance();
        c.put("utf8", utf8);
        UTF16CharacterSet utf16 = UTF16CharacterSet.getInstance();
        c.put("utf16", utf16);
        ASCIICharacterSet acs = ASCIICharacterSet.getInstance();
        c.put("ascii", acs);
        c.put("iso646", acs);
        c.put("usascii", acs);
        ISO88591CharacterSet lcs = ISO88591CharacterSet.getInstance();
        c.put("iso88591", lcs);
    }

    /**
     * Register an implementation of a character set, using the class name
     *
     * @param encoding the name of the character set
     * @param charSet  the name of a class that implements {@link net.sf.saxon.serialize.charcode.CharacterSet}
     */

    public void setCharacterSetImplementation(/*@NotNull*/ String encoding, CharacterSet charSet) {
        characterSets.put(normalizeCharsetName(encoding), charSet);
    }

    /**
     * Normalize the name of a character set
     *
     * @param name the character set name
     * @return the normalized name (removes hyphens and underscores and converts to lower-case)
     */

    private static String normalizeCharsetName(String name) {
        return name.replace("-", "").replace("_", "").toLowerCase();
    }

    /**
     * Make a CharacterSet appropriate to the encoding
     *
     * @param details the serialization properties
     * @return the constructed CharacterSet
     * @throws XPathException if the encoding is not recognized
     */

    public CharacterSet getCharacterSet(Properties details)
        throws XPathException {

        String encoding = details.getProperty(OutputKeys.ENCODING);
        if (encoding == null) {
            return UTF8CharacterSet.getInstance();
        }
        return getCharacterSet(encoding);
    }

    /**
     * Make a CharacterSet appropriate to the encoding
     * @param encoding the required encoding
     * @return the constructed CharacterSet
     * @throws XPathException if the encoding is not recognized
     */

    public CharacterSet getCharacterSet(String encoding)
            throws XPathException {

        if (encoding == null) {
            return UTF8CharacterSet.getInstance();
        } else {
            String encodingKey = normalizeCharsetName(encoding);
            CharacterSet cs = characterSets.get(encodingKey);
            if (cs != null) {
                return cs;
            }

            CSharp.emitCode("return Saxon.Helpers.DotNetCharacterSet.GetCharacterSet(encoding);");


            // Otherwise see if the Java VM knows anything about the character set

            Charset charset;
            try {
                charset = Charset.forName(encoding);
                CharacterSet res = JavaCharacterSet.makeCharSet(charset);
                characterSets.put(encodingKey, res);
                return res;
            } catch (IllegalCharsetNameException err) {
                throw new XPathException("Invalid encoding name: " + encoding, "SESU0007");
            } catch (UnsupportedCharsetException err) {
                throw new XPathException("Unknown encoding requested: " + encoding, "SESU0007");
            }
        }
    }


    /**
     * Main program is a utility to give a list of the character sets supported
     * by the Java VM
     *
     * @param args command line arguments (none needed)
     * @throws Exception if anything goes wrong
     */

    public static void main(String[] args) throws Exception {
        System.err.println("Available Character Sets in the java.nio package for this Java VM:");
        for (String s : Charset.availableCharsets().keySet()) {
            System.err.println("    " + s);
        }
        System.err.println("Registered Character Sets in Saxon:");
        CharacterSetFactory factory = new CharacterSetFactory();
        for (Map.Entry e : factory.characterSets.entrySet()) {
            System.err.println("    " + e.getKey() + " = " + e.getValue().getClass().getName());
        }
    }

}

