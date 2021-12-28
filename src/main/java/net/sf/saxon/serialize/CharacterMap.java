////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.tree.tiny.CompressedWhitespace;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.z.IntHashMap;
import net.sf.saxon.z.IntIterator;

/**
 * This class defines a character map, that is, a mapping from characters to strings used by the serializer
 * when mapping individual characters in the output.
 */
public class CharacterMap {

    private StructuredQName name;
    private IntHashMap<String> charMap;
    private int min = Integer.MAX_VALUE;    // the lowest mapped character
    private int max = 0;                    // the highest mapped character
    private boolean mapsWhitespace = false;

    /**
     * Create a CharacterMap from a raw map of integers to strings
     *
     * @param map the mapping of integer Unicode character codes to strings. This must not be subsequently changed.
     */

    public CharacterMap(StructuredQName name, IntHashMap<String> map) {
        this.name = name;
        this.charMap = map;
        init();
    }

    /**
     * Create a CharacterMap that combines a set of existing character maps.
     *
     * @param list the list of existing character maps. If the same character
     *             is mapped by more than one map in the list, the last mapping takes
     *             precedence
     */

    public CharacterMap(Iterable<CharacterMap> list) {
        charMap = new IntHashMap<String>(64);
        for (CharacterMap map : list) {
            IntIterator keys = map.charMap.keyIterator();
            while (keys.hasNext()) {
                int next = keys.next();
                charMap.put(next, map.charMap.get(next));
            }
        }
        init();
    }

    private void init() {
        IntIterator keys = charMap.keyIterator();
        while (keys.hasNext()) {
            int next = keys.next();
            if (next < min) {
                min = next;
            }
            if (next > max) {
                max = next;
            }
            if (!mapsWhitespace && Whitespace.isWhitespace(next)) {
                mapsWhitespace = true;
            }
        }
        if (min > 0xD800) {
            // if all the mapped characters are above the BMP, we need to check
            // surrogates
            min = 0xD800;
        }
    }

    /**
     * Get the name of the character map
     * @return the name given in the stylesheet if this is an un-expanded character map, or null if
     * this is an expanded map (a map combined with the character maps that it references)
     */

    public StructuredQName getName() {
        return name;
    }

    /**
     * Expand all the characters in a string using this character mapping
     *
     * @param in          the input string to be mapped
     * @param insertNulls true if null (0) characters are to be inserted before
     *                    and after replacement characters. This is done to signal
     *                    that output escaping of these characters is disabled. The flag is set to true when writing
     *                    XML or HTML, but to false when writing TEXT.
     */

    /*@NotNull*/
    public CharSequence map(CharSequence in, boolean insertNulls) {

        if (!mapsWhitespace && in instanceof CompressedWhitespace) {
            return in;
        }

        // First scan the string to see if there are any possible mapped
        // characters; if not, don't bother creating the new buffer

        boolean move = false;
        for (int i = 0; i < in.length(); ) {
            char c = in.charAt(i++);
            if (c >= min && c <= max) {
                move = true;
                break;
            }
        }
        if (!move) {
            return in;
        }

        FastStringBuffer buffer = new FastStringBuffer(in.length() * 2);
        int i = 0;
        while (i < in.length()) {
            char c = in.charAt(i++);
            if (c >= min && c <= max) {
                if (UTF16CharacterSet.isHighSurrogate(c)) {
                    // assume the string is properly formed
                    char d = in.charAt(i++);
                    int s = UTF16CharacterSet.combinePair(c, d);
                    String rep = charMap.get(s);
                    if (rep == null) {
                        buffer.cat(c);
                        buffer.cat(d);
                    } else {
                        if (insertNulls) {
                            buffer.cat((char) 0);
                            buffer.append(rep);
                            buffer.cat((char) 0);
                        } else {
                            buffer.append(rep);
                        }
                    }
                } else {
                    String rep = charMap.get(c);
                    if (rep == null) {
                        buffer.cat(c);
                    } else {
                        if (insertNulls) {
                            buffer.cat((char) 0);
                            buffer.append(rep);
                            buffer.cat((char) 0);
                        } else {
                            buffer.append(rep);
                        }
                    }
                }
            } else {
                buffer.cat(c);
            }
        }
        return buffer;
    }

    /**
     * Write the character map to an export file
     * @param out the output destination
     */

    public void export(ExpressionPresenter out) {
        out.startElement("charMap");
        out.emitAttribute("name", name);
        for (IntIterator iter = charMap.keyIterator(); iter.hasNext();) {
            int c = iter.next();
            String s = charMap.get(c);
            out.startElement("m");
            out.emitAttribute("c", c+"");
            out.emitAttribute("s", s);
            out.endElement();
        }
        out.endElement();
    }


}

