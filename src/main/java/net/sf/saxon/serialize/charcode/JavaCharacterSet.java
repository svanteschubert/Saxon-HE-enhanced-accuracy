////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize.charcode;

import net.sf.saxon.tree.tiny.CharSlice;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.HashMap;

/**
 * This class establishes properties of a character set that is
 * known to the Java VM but not specifically known to Saxon. It determines whether particular
 * characters are encodable by calling {@link CharsetEncoder#canEncode(char)}, and then caches
 * this information locally.
 */

public class JavaCharacterSet implements CharacterSet {

    public static HashMap<Charset, JavaCharacterSet> map;

    private CharsetEncoder encoder;

    // This class is written on the assumption that the CharsetEncoder.canEncode()
    // method may be expensive. For BMP characters, it therefore remembers the results
    // so each character is only looked up the first time it is encountered.

    private byte[] charinfo = new byte[65536];
    // rely on initialization to zeroes

    //private final static byte UNKNOWN = 0;
    private static final byte GOOD = 1;
    private static final byte BAD = 2;

    private JavaCharacterSet(Charset charset) {
        encoder = charset.newEncoder();
    }

    public static synchronized JavaCharacterSet makeCharSet(/*@NotNull*/ Charset charset) {
        if (map == null) {
            map = new HashMap<Charset, JavaCharacterSet>(10);
        }
        JavaCharacterSet c = map.get(charset);
        if (c == null) {
            c = new JavaCharacterSet(charset);
            map.put(charset, c);
        }
        return c;
    }

    @Override
    public final boolean inCharset(int c) {
        // Assume ASCII chars are always OK
        if (c <= 127) {
            return true;
        }
        if (c <= 65535) {
            if (charinfo[c] == GOOD) {
                return true;
            } else if (charinfo[c] == BAD) {
                return false;
            } else {
                if (encoder.canEncode((char) c)) {
                    charinfo[c] = GOOD;
                    return true;
                } else {
                    charinfo[c] = BAD;
                    return false;
                }
            }
        } else {
            char[] cc = new char[2];
            cc[0] = UTF16CharacterSet.highSurrogate(c);
            cc[1] = UTF16CharacterSet.lowSurrogate(c);
            return encoder.canEncode(new CharSlice(cc));
        }

    }

    @Override
    public String getCanonicalName() {
        return encoder.charset().name();
    }
}

