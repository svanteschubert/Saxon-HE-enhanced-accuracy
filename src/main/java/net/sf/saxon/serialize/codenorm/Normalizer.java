////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize.codenorm;

import net.sf.saxon.Configuration;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;

/**
 * Implements Unicode Normalization Forms C, D, KC, KD.
 * Copyright (c) 1991-2005 Unicode, Inc.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 * For documentation, see UAX#15.<br>
 * The Unicode Consortium makes no expressed or implied warranty of any
 * kind, and assumes no liability for errors or omissions.
 * No liability is assumed for incidental and consequential damages
 * in connection with or arising out of the use of the information here.
 *
 * @author Mark Davis
 *         Updates for supplementary code points: Vladimir Weinstein &amp; Markus Scherer
 *         Modified to remove dependency on ICU code: Michael Kay
 */

public class Normalizer {

    /**
     * The requested normalization form.
     */
    private int form;

    /**
     * Create a normalizer for a given form. Private constructor: use the {@link #make factory method}
     *
     * @param form   the normalization form required: for example {@link Normalizer#C}, {@link Normalizer#D}
     * @throws XPathException if normalization fails
     */
    private Normalizer(int form)  {
        this.form = form;
    }

    /**
     * Create a normalizer for a given form: static synchronized factory method
     *
     * @param form   the normalization form required: for example {@link Normalizer#C}, {@link Normalizer#D}
     * @param config the Saxon configuration
     * @throws XPathException if normalization fails
     */

    public static synchronized Normalizer make(int form, Configuration config) throws XPathException {
        if (data == null) {
            data = UnicodeDataParserFromXML.build(config);
        }
        return new Normalizer(form);
    }

    /**
     * Masks for the form selector
     */
    static final int
            COMPATIBILITY_MASK = 1,
            COMPOSITION_MASK = 2;

    /**
     * Normalization Form Selector
     */
    public static final int
            D = 0,
            C = COMPOSITION_MASK,
            KD = COMPATIBILITY_MASK,
            KC = (byte) (COMPATIBILITY_MASK + COMPOSITION_MASK),
            NO_ACTION = 8;

    /**
     * Normalizes text according to the chosen form
     *
     * @param source the original text, unnormalized
     * @return target      the resulting normalized text
     */
    public CharSequence normalize(CharSequence source) {

        if (form == NO_ACTION || source.length() == 0) {
            return source;
        }

        // First decompose the source into target,
        // then compose if the form requires.

        FastStringBuffer target = new FastStringBuffer(source.length() + 8);
        internalDecompose(source, target);
        if ((form & COMPOSITION_MASK) != 0) {
            internalCompose(target);
        }
        return target;
    }


    /**
     * Decomposes text, either canonical or compatibility,
     * replacing contents of the target buffer.
     * //    * @param   form        the normalization form. If COMPATIBILITY_MASK
     * //    *                      bit is on in this byte, then selects the recursive
     * //    *                      compatibility decomposition, otherwise selects
     * //    *                      the recursive canonical decomposition.
     *
     * @param source the original text, unnormalized
     * @param target the resulting normalized text
     */
    private void internalDecompose(CharSequence source, FastStringBuffer target) {
        FastStringBuffer buffer = new FastStringBuffer(8);
        boolean canonical = (form & COMPATIBILITY_MASK) == 0;
        int ch32;
        for (int i = 0; i < source.length(); ) {
            buffer.setLength(0);
            ch32 = source.charAt(i++);
            if (ch32 < 128) {
                // fast path - for ASCII characters, decomposition is a no-op
                target.cat((char) ch32);
                continue;
            }
            if (UTF16CharacterSet.isHighSurrogate(ch32)) {
                char low = source.charAt(i++);
                ch32 = UTF16CharacterSet.combinePair((char) ch32, low);
            }
            data.getRecursiveDecomposition(canonical, ch32, buffer);

            // add all of the characters in the decomposition.
            // (may be just the original character, if there was
            // no decomposition mapping)

            int ch;
            for (int j = 0; j < buffer.length(); ) {
                ch = buffer.charAt(j++);
                if (UTF16CharacterSet.isHighSurrogate(ch)) {
                    char low = buffer.charAt(j++);
                    ch = UTF16CharacterSet.combinePair((char) ch, low);
                }
                int chClass = data.getCanonicalClass(ch);
                int k = target.length(); // insertion point
                if (chClass != 0) {

                    // bubble-sort combining marks as necessary

                    int ch2;
                    while (k > 0) {
                        int step = 1;
                        ch2 = target.charAt(k - 1);
                        if (UTF16CharacterSet.isSurrogate(ch2)) {
                            step = 2;
                            char high = target.charAt(k - 2);
                            ch2 = UTF16CharacterSet.combinePair(high, (char) ch2);
                        }
                        if (data.getCanonicalClass(ch2) <= chClass) break;
                        k -= step;
                    }
                }
                if (ch < 65536) {
                    target.insert(k, (char) ch);
                } else {
                    target.insertWideChar(k, ch);
                }
            }
        }
    }

    /**
     * Composes text in place. Target must already
     * have been decomposed.
     *
     * @param target input: decomposed text.
     *               output: the resulting normalized text.
     */
    private void internalCompose(FastStringBuffer target) {

        int starterPos = 0;
        //int starterCh = UTF16.charAt(target,0);
        //int compPos = (starterCh<65536 ? 1 : 2); // length of last composition
        int starterCh = target.charAt(0);
        int compPos = 1;
        if (UTF16CharacterSet.isHighSurrogate(starterCh)) {
            starterCh = UTF16CharacterSet.combinePair((char) starterCh, target.charAt(1));
            compPos++;
        }
        int lastClass = data.getCanonicalClass(starterCh);
        if (lastClass != 0) lastClass = 256; // fix for strings staring with a combining mark
        int oldLen = target.length();

        // Loop on the decomposed characters, combining where possible

        int ch;
        //for (int decompPos = compPos; decompPos < target.length(); decompPos += (ch<65536 ? 1 : 2)) {
        for (int decompPos = compPos; decompPos < target.length(); ) {
            ch = target.charAt(decompPos++);
            if (UTF16CharacterSet.isHighSurrogate(ch)) {
                ch = UTF16CharacterSet.combinePair((char) ch, target.charAt(decompPos++));
            }
            //ch = UTF16.charAt(target, decompPos);
            int chClass = data.getCanonicalClass(ch);
            int composite = data.getPairwiseComposition(starterCh, ch);
            if (composite != NormalizerData.NOT_COMPOSITE && (lastClass < chClass || lastClass == 0)) {
                setCharAt(target, starterPos, composite);
                // we know that we will only be replacing non-supplementaries by non-supplementaries
                // so we don't have to adjust the decompPos
                starterCh = composite;
            } else {
                if (chClass == 0) {
                    starterPos = compPos;
                    starterCh = ch;
                }
                lastClass = chClass;
                setCharAt(target, compPos, ch);
                if (target.length() != oldLen) { // MAY HAVE TO ADJUST!
                    decompPos += target.length() - oldLen;
                    oldLen = target.length();
                }
                compPos += (ch < 65536 ? 1 : 2);
            }
        }
        target.setLength(compPos);
    }

    /**
     * Set the 32-bit character at a particular 16-bit offset in a string buffer,
     * replacing the previous character at that position, and taking account of the
     * fact that either, both, or neither of the characters might be a surrogate pair.
     *
     * @param target the StringBuffer in which the data is to be inserted
     * @param offset the position at which the data is to be inserted
     * @param ch32   the character to be inserted, as a 32-bit Unicode codepoint
     */

    private static void setCharAt(FastStringBuffer target, int offset, int ch32) {
        if (ch32 < 65536) {
            if (UTF16CharacterSet.isHighSurrogate(target.charAt(offset))) {
                target.setCharAt(offset, (char) ch32);
                target.removeCharAt(offset + 1);
            } else {
                target.setCharAt(offset, (char) ch32);
            }
        } else {
            if (UTF16CharacterSet.isHighSurrogate(target.charAt(offset))) {
                target.setCharAt(offset, UTF16CharacterSet.highSurrogate(ch32));
                target.setCharAt(offset + 1, UTF16CharacterSet.lowSurrogate(ch32));
            } else {
                target.setCharAt(offset, UTF16CharacterSet.highSurrogate(ch32));
                target.insert(offset + 1, UTF16CharacterSet.lowSurrogate(ch32));
            }
        }
    }

    /**
     * Contains normalization data from the Unicode Character Database.
     */
    /*@Nullable*/ private static NormalizerData data = null;

    /**
     * Just accessible for testing.
     * @param ch a character
     * @return true if the character is an excluded character
     */
//    boolean getExcluded (char ch) {
//        return data.getExcluded(ch);
//    }

    /**
     * Just accessible for testing.
     * @param ch a character
     * @return the raw decomposition mapping of the character
     */
//    String getRawDecompositionMapping (char ch) {
//        return data.getRawDecompositionMapping(ch);
//    }
}

// * The class is derived from the sample program Normalizer.java published by the
// * Unicode consortium. 

// * Updates for supplementary code points: Vladimir Weinstein & Markus Scherer
// * Modified to remove dependency on ICU code: Michael Kay, Saxonica Limited
