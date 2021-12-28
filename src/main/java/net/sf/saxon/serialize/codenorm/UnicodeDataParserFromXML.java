////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize.codenorm;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.z.IntHashMap;
import net.sf.saxon.z.IntToIntHashMap;
import net.sf.saxon.z.IntToIntMap;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.StringTokenizer;

/**
 * This class reads the data compiled into class UnicodeData, and builds hash tables
 * that can be used by the Unicode normalization routines. This operation is performed
 * once only, the first time normalization is attempted after Saxon is loaded.
 */

class UnicodeDataParserFromXML {

    // This class is never instantiated
    private UnicodeDataParserFromXML() {
    }

    /**
     * Called exactly once by NormalizerData to build the static data
     */

    static NormalizerData build(Configuration config) throws XPathException {

        InputStream in = Configuration.locateResource(
            "normalizationData.xml", new ArrayList<>(), new ArrayList<>());
        if (in == null) {
            throw new XPathException("Unable to read normalizationData.xml file");
        }

        BitSet isExcluded = new BitSet(128000);
        BitSet isCompatibility = new BitSet(128000);

        ParseOptions options = new ParseOptions();
        options.setSchemaValidationMode(Validation.SKIP);
        options.setDTDValidationMode(Validation.SKIP);
        TreeInfo doc = config.buildDocumentTree(new StreamSource(in, "normalizationData.xml"), options);
        NodeInfo canonicalClassKeys = null;
        NodeInfo canonicalClassValues = null;
        NodeInfo decompositionKeys = null;
        NodeInfo decompositionValues = null;

        AxisIterator iter = doc.getRootNode().iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
        NodeInfo item;
        while ((item = iter.next()) != null) {
            switch (item.getLocalPart()) {
                case "CanonicalClassKeys":
                    canonicalClassKeys = item;
                    break;
                case "CanonicalClassValues":
                    canonicalClassValues = item;
                    break;
                case "DecompositionKeys":
                    decompositionKeys = item;
                    break;
                case "DecompositionValues":
                    decompositionValues = item;
                    break;
                case "ExclusionList":
                    readExclusionList(item.getStringValue(), isExcluded);
                    break;
                case "CompatibilityList":
                    readCompatibilityList(item.getStringValue(), isCompatibility);
                    break;
            }
        }

        IntToIntMap canonicalClass = new IntToIntHashMap(400);
        canonicalClass.setDefaultValue(0);
        readCanonicalClassTable(canonicalClassKeys.getStringValue(), canonicalClassValues.getStringValue(), canonicalClass);


        IntHashMap<String> decompose = new IntHashMap<>(18000);
        IntToIntMap compose = new IntToIntHashMap(15000);
        compose.setDefaultValue(NormalizerData.NOT_COMPOSITE);

        readDecompositionTable(decompositionKeys.getStringValue(), decompositionValues.getStringValue(),
                decompose, compose, isExcluded, isCompatibility);

        return new NormalizerData(canonicalClass, decompose, compose,
                isCompatibility, isExcluded);
    }

    /**
     * Reads exclusion list and stores the data
     */

    private static void readExclusionList(String s, BitSet isExcluded) {
        StringTokenizer st = new StringTokenizer(s);
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            int value = Integer.parseInt(tok, 32);
            isExcluded.set(value);
        }
    }

    /**
     * Reads compatibility list and stores the data
     */

    private static void readCompatibilityList(String s, BitSet isCompatible) {
        StringTokenizer st = new StringTokenizer(s);
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            int value = Integer.parseInt(tok, 32);
            isCompatible.set(value);
        }
    }

    /**
     * Read canonical class table (mapping from character codes to their canonical class)
     */

    private static void readCanonicalClassTable(String keyString, String valueString, IntToIntMap canonicalClasses) {
        List<Integer> keys = new ArrayList<>(5000);

        StringTokenizer st = new StringTokenizer(keyString);
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            int value = Integer.parseInt(tok, 32);
            keys.add(value);
        }

        int k = 0;
        st = new StringTokenizer(valueString);
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            int clss;
            int repeat = 1;
            int star = tok.indexOf('*');
            if (star < 0) {
                clss = Integer.parseInt(tok, 32);
            } else {
                repeat = Integer.parseInt(tok.substring(0, star));
                clss = Integer.parseInt(tok.substring(star + 1), 32);
            }
            for (int i = 0; i < repeat; i++) {
                canonicalClasses.put(keys.get(k++), clss);
            }
        }
    }

    /**
     * Read canonical class table (mapping from character codes to their canonical class)
     */

    private static void readDecompositionTable(
            String decompositionKeyString, String decompositionValuesString,
            IntHashMap<String> decompose, IntToIntMap compose,
            BitSet isExcluded, /*@NotNull*/ BitSet isCompatibility) {
        int k = 0;

        List<String> values = new ArrayList<>(1000);
        StringTokenizer st = new StringTokenizer(decompositionValuesString);
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            StringBuilder value = new StringBuilder();
            for (int c = 0; c < tok.length(); ) {
                char h0 = tok.charAt(c++);
                char h1 = tok.charAt(c++);
                char h2 = tok.charAt(c++);
                char h3 = tok.charAt(c++);
                int code = ("0123456789abcdef".indexOf(h0) << 12) +
                        ("0123456789abcdef".indexOf(h1) << 8) +
                        ("0123456789abcdef".indexOf(h2) << 4) +
                        "0123456789abcdef".indexOf(h3);
                value.append((char) code);
            }
            values.add(value.toString());
        }


        st = new StringTokenizer(decompositionKeyString);
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            int key = Integer.parseInt(tok, 32);
            String value = values.get(k++);
            decompose.put(key, value);
            // only compositions are canonical pairs
            // skip if script exclusion

            if (!isCompatibility.get(key) && !isExcluded.get(key)) {
                char first = '\u0000';
                char second = value.charAt(0);
                if (value.length() > 1) {
                    first = second;
                    second = value.charAt(1);
                }

                // store composition pair in single integer

                int pair = (first << 16) | second;
                compose.put(pair, key);
            }
        }

        // Add algorithmic Hangul decompositions
        // This fragment code is copied from the normalization code published by Unicode consortium.
        // See module net.sf.saxon.serialize.codenorm.Normalizer for applicable copyright information.

        for (int SIndex = 0; SIndex < SCount; ++SIndex) {
            int TIndex = SIndex % TCount;
            char first, second;
            if (TIndex != 0) { // triple
                first = (char) (SBase + SIndex - TIndex);
                second = (char) (TBase + TIndex);
            } else {
                first = (char) (LBase + SIndex / NCount);
                second = (char) (VBase + (SIndex % NCount) / TCount);
            }
            int pair = (first << 16) | second;
            int key = SIndex + SBase;
            decompose.put(key, String.valueOf(first) + second);
            compose.put(pair, key);
        }
    }

    /**
     * Hangul composition constants
     */
    private static final int
            SBase = 0xAC00, LBase = 0x1100, VBase = 0x1161, TBase = 0x11A7,
            LCount = 19, VCount = 21, TCount = 28,
            NCount = VCount * TCount,   // 588
            SCount = LCount * NCount;   // 11172

    // end of Unicode consortium code

}

// * Copyright (c) 1991-2005 Unicode, Inc.
// * For terms of use, see http://www.unicode.org/terms_of_use.html
// * For documentation, see UAX#15.<br>
// * The Unicode Consortium makes no expressed or implied warranty of any
// * kind, and assumes no liability for errors or omissions.
// * No liability is assumed for incidental and consequential damages
// * in connection with or arising out of the use of the information here.
