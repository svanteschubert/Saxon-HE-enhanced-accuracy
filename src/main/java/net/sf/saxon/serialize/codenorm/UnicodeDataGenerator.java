////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize.codenorm;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.StreamWriterToReceiver;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class reads the Unicode character database, extracts information needed
 * to perform unicode normalization, and writes this information out in the form of the
 * Java "source" module UnicodeData.java. This class is therefore executed (via its main()
 * method) at the time Saxon is built - it only needs to be rerun when the Unicode data tables
 * have changed.
 * <p>The class is derived from the sample program NormalizerData.java published by the
 * Unicode consortium. That code has been modified so that instead of building the run-time
 * data structures directly, they are written to a Java "source" module, which is then
 * compiled. Also, the ability to construct a condensed version of the data tables has been
 * removed.</p>
 * <p>Copyright (c) 1991-2005 Unicode, Inc.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 * For documentation, see UAX#15.</p>
 *
 * @author Mark Davis
 * @author Michael Kay: Saxon modifications.
 */
class UnicodeDataGenerator {
    static final String copyright = "Copyright � 1998-1999 Unicode, Inc.";

    /**
     * Testing flags
     */

    private static final boolean DEBUG = false;

    /**
     * Constants for the data file version to use.
     */
//    static final boolean NEW_VERSION = true;
    private static String dir;

    private final static String UNICODE_DATA = "UnicodeData.txt";
    private final static String COMPOSITION_EXCLUSIONS = "CompositionExclusions.txt";

    private static List<Integer> canonicalClassKeys = new ArrayList<>(30000);
    private static List<Integer> canonicalClassValues = new ArrayList<>(30000);

    private static List<Integer> decompositionKeys = new ArrayList<>(6000);
    private static List<String> decompositionValues = new ArrayList<>(6000);

    private static List<Integer> exclusionList = new ArrayList<>(200);
    private static List<Integer> compatibilityList = new ArrayList<>(8000);

    private UnicodeDataGenerator() {
    }

    /**
     * Called exactly once by NormalizerData to build the static data
     */

    static void build() {
        try {
            readExclusionList();
            buildDecompositionTables();
        } catch (java.io.IOException e) {
            System.err.println("Can't load data file." + e + ", " + e.getMessage());
        }
    }

// =============================================================
// Building Decomposition Tables
// =============================================================

    /**
     * Reads exclusion list and stores the data
     */

    // Modified by MHK: the original code expects the hex character code to be always four hex digits
    private static void readExclusionList() throws java.io.IOException {
        if (DEBUG) {
            System.out.println("Reading Exclusions");
        }
        BufferedReader in = new BufferedReader(new FileReader(dir + '/' + COMPOSITION_EXCLUSIONS), 5 * 1024);
        while (true) {

            // read a line, discarding comments and blank lines

            String line = in.readLine();
            if (line == null) {
                break;
            }
            int comment = line.indexOf('#');                    // strip comments
            if (comment != -1) {
                line = line.substring(0, comment);
            }
            if (line.isEmpty()) {
                continue;
            }                   // ignore blanks

            // store -1 in the excluded table for each character hit

            int z = line.indexOf(' ');
            if (z < 0) {
                z = line.length();
            }
            int value = Integer.parseInt(line.substring(0, z), 16);
            exclusionList.add(value);

        }
        in.close();
    }

    /**
     * Builds a decomposition table from a UnicodeData file
     */
    private static void buildDecompositionTables()
            throws java.io.IOException {
        if (DEBUG) {
            System.out.println("Reading Unicode Character Database");
        }
        BufferedReader in = new BufferedReader(new FileReader(dir + '/' + UNICODE_DATA), 64 * 1024);
        int value;
        int counter = 0;
        while (true) {

            // read a line, discarding comments and blank lines

            String line = in.readLine();
            if (line == null) {
                break;
            }
            int comment = line.indexOf('#');                    // strip comments
            if (comment != -1) {
                line = line.substring(0, comment);
            }
            if (line.isEmpty()) {
                continue;
            }
            if (DEBUG) {
                counter++;
                if ((counter & 0xFF) == 0) {
                    System.out.println("At: " + line);
                }
            }

            // find the values of the particular fields that we need
            // Sample line: 00C0;LATIN ...A GRAVE;Lu;0;L;0041 0300;;;;N;LATIN ... GRAVE;;;00E0;

            int start = 0;
            int end = line.indexOf(';'); // code
            try {
                value = Integer.parseInt(line.substring(start, end), 16);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Bad hex value in line:\n" + line);
            }

            end = line.indexOf(';', end + 1); // name
            //String name = line.substring(start,end);
            end = line.indexOf(';', end + 1); // general category
            end = line.indexOf(';', start = end + 1); // canonical class

            // check consistency: canonical classes must be from 0 to 255

            int cc = Integer.parseInt(line.substring(start, end));
            if (cc != (cc & 0xFF)) {
                System.err.println("Bad canonical class at: " + line);
            }
            canonicalClassKeys.add(value);
            canonicalClassValues.add(cc);
            //canonicalClass.put(value,cc);
            end = line.indexOf(';', end + 1); // BIDI
            end = line.indexOf(';', start = end + 1); // decomp

            // decomp requires more processing.
            // store whether it is canonical or compatibility.
            // store the decomp in one table, and the reverse mapping (from pairs) in another

            if (start != end) {
                String segment = line.substring(start, end);
                boolean compat = segment.charAt(0) == '<';
                if (compat) {
                    compatibilityList.add(value);
                    //isCompatibility.set(value);
                }
                String decomp = fromHex(segment);

                // check consistency: all canon decomps must be singles or pairs!

                if (decomp.length() < 1 || decomp.length() > 2 && !compat) {
                    System.err.println("Bad decomp at: " + line);
                }

                decompositionKeys.add(value);
                decompositionValues.add(decomp);
                //decompose.put(value, decomp);

                // only compositions are canonical pairs
                // skip if script exclusion

//                if (!compat && !isExcluded.get(value)) {
//                    char first = '\u0000';
//                    char second = decomp.charAt(0);
//                    if (decomp.length() > 1) {
//                        first = second;
//                        second = decomp.charAt(1);
//                    }
//
//                    // store composition pair in single integer
//
//                    pair = (first << 16) | second;
//                    if (DEBUG && value == '\u00C0') {
//                        System.out.println("debug2: " + line);
//                    }
//                    compose.put(pair, value);
//                } else if (DEBUG) {
//                    System.out.println("Excluding: " + decomp);
//                }
            }
        }
        in.close();
        if (DEBUG) {
            System.out.println("Done reading Unicode Character Database");
        }

        // add algorithmic Hangul decompositions
        // this is more compact if done at runtime, but for simplicity we
        // do it this way.

//        if (DEBUG) System.out.println("Adding Hangul");
//
//        for (int SIndex = 0; SIndex < SCount; ++SIndex) {
//            int TIndex = SIndex % TCount;
//            char first, second;
//            if (TIndex != 0) { // triple
//                first = (char)(SBase + SIndex - TIndex);
//                second = (char)(TBase + TIndex);
//            } else {
//                first = (char)(LBase + SIndex / NCount);
//                second = (char)(VBase + (SIndex % NCount) / TCount);
//            }
//            pair = (first << 16) | second;
//            value = SIndex + SBase;
//            decompose.put(value, String.valueOf(first) + second);
//            compose.put(pair, value);
//        }
//        if (DEBUG) System.out.println("Done adding Hangul");
    }

    /*
     * Hangul composition constants
     */
//    static final int
//        SBase = 0xAC00, LBase = 0x1100, VBase = 0x1161, TBase = 0x11A7,
//        LCount = 19, VCount = 21, TCount = 28,
//        NCount = VCount * TCount,   // 588
//        SCount = LCount * NCount;   // 11172

    /**
     * Utility: Parses a sequence of hex Unicode characters separated by spaces
     */

    // Modified by MHK. Original code assumed the characters were each 4 hex digits!
    public static String fromHex(String source) {
        FastStringBuffer result = new FastStringBuffer(8);
        for (int i = 0; i < source.length(); ++i) {
            char c = source.charAt(i);
            switch (c) {
                case ' ':
                    break; // ignore
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case 'A':
                case 'B':
                case 'C':
                case 'D':
                case 'E':
                case 'F':
                case 'a':
                case 'b':
                case 'c':
                case 'd':
                case 'e':
                case 'f':
                    int z = source.indexOf(' ', i);
                    if (z < 0) {
                        z = source.length();
                    }
                    try {
                        result.cat((char) Integer.parseInt(source.substring(i, z), 16));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Bad hex value in " + source);
                    }
                    i = z; // skip rest of number
                    break;
                case '<':
                    int j = source.indexOf('>', i); // skip <...>
                    if (j > 0) {
                        i = j;
                        break;
                    } // else fall through--error
                default:
                    throw new IllegalArgumentException("Bad hex value in " + source);
            }
        }
        return result.toString();
    }

    /**
     * Utility: Supplies a zero-padded hex representation of a Unicode character (without 0x, \\u)
     */
    public static String hex(char i) {
        String result = Integer.toString(i, 16).toUpperCase();
        return "0000".substring(result.length(), 4) + result;
    }

    /**
     * Utility: Supplies a zero-padded hex representation of a Unicode character (without 0x, \\u)
     */
    public static String hex(String s, String sep) {
        FastStringBuffer result = new FastStringBuffer(20);
        for (int i = 0; i < s.length(); ++i) {
            if (i != 0) {
                result.append(sep);
            }
            result.append(hex(s.charAt(i)));
        }
        return result.toString();
    }

    /**
     * Generate the Java output from the data structure
     */

    private static void generateJava(PrintStream o) {
        o.println("package net.sf.saxon.serialize.codenorm;");
        o.println();
        o.println("//This module was generated by running net.sf.saxon.serialize.codenorm.UnicodeDataGenerator");
        o.println("//*** DO NOT EDIT! ***");
        o.println("//The strange format of this file is carefully chosen to avoid breaking Java compiler limits");
        o.println();
        o.println("public class UnicodeData {");

        // Output the canonical class table
        o.println("public static final String[] canonicalClassKeys = {");
        printArray(o, canonicalClassKeys.iterator());
        o.println("};");
        o.println("public static final String[] canonicalClassValues = {");
        printArray(o, canonicalClassValues.iterator());
        o.println("};");

        // Output the decomposition values (not including Hangul algorithmic decompositions)
        o.println("public static final String[] decompositionKeys = {");
        printArray(o, decompositionKeys.iterator());
        o.println("};");
        o.println("public static final String[] decompositionValues = {");
        printStringArray(o, decompositionValues.iterator());
        o.println("};");

        // Output the composition exclusions
        o.println("public static final String[] exclusionList = {");
        printArray(o, exclusionList.iterator());
        o.println("};");

        // Output the compatibility list
        o.println("public static final String[] compatibilityList = {");
        printArray(o, compatibilityList.iterator());
        o.println("};");

        o.println("}");

    }

    /**
     * Generate the XML output from the data structure
     */

    private static void generateXML(PrintStream o) throws XPathException, XMLStreamException {
        Configuration config = new Configuration();
        Result result = new StreamResult(o);
        Receiver receiver = config.getSerializerFactory().getReceiver(result);
        XMLStreamWriter w = new StreamWriterToReceiver(receiver);
        w.writeStartDocument();
        w.writeStartElement("UnicodeData");
        w.writeAttribute("version", "6.0.0");
        w.writeStartElement("CanonicalClassKeys");
        w.writeAttribute("format", "base32chars");
        w.writeCharacters(base32array(canonicalClassKeys));
        w.writeEndElement();
        w.writeStartElement("CanonicalClassValues");
        w.writeAttribute("format", "base32chars,runLength");
        w.writeCharacters(base32arrayRunLength(canonicalClassValues));
        w.writeEndElement();
        w.writeStartElement("DecompositionKeys");
        w.writeAttribute("format", "base32chars");
        w.writeCharacters(base32array(decompositionKeys));
        w.writeEndElement();
        w.writeStartElement("DecompositionValues");
        w.writeAttribute("format", "UCS16Strings,base16");
        w.writeCharacters(base32StringArray(decompositionValues));
        w.writeEndElement();
        w.writeStartElement("ExclusionList");
        w.writeAttribute("format", "base32chars");
        w.writeCharacters(base32array(exclusionList));
        w.writeEndElement();
        w.writeStartElement("CompatibilityList");
        w.writeAttribute("format", "base32chars");
        w.writeCharacters(base32array(compatibilityList));
        w.writeEndElement();
        w.writeEndElement();
        w.writeEndDocument();
        w.close();
    }


    /**
     * Output an array of integer values
     */

    private static void printArray(PrintStream o, Iterator<Integer> iter) {
        int count = 0;
        FastStringBuffer buff = new FastStringBuffer(128);
        if (!iter.hasNext()) {
            return;
        }
        buff.cat('"');
        while (true) {
            if (++count == 20) {
                count = 0;
                buff.append("\",");
                o.println(buff);
                buff.setLength(0);
                buff.cat('"');
            }
            int next = iter.next();
            buff.append(Integer.toString(next, 32));    // values are written in base-32 notation
            if (iter.hasNext()) {
                buff.append(",");
            } else {
                buff.append("\"");
                o.println(buff);
                return;
            }
        }
    }

    private static String base32array(List<Integer> list) {
        int count = 0;
        Iterator<Integer> iter = list.iterator();
        FastStringBuffer buff = new FastStringBuffer(128);
        if (!iter.hasNext()) {
            return buff.toString();
        }
        while (true) {
            if (++count == 20) {
                count = 0;
                buff.append("\n");
            }
            int next = iter.next();
            buff.append(Integer.toString(next, 32));    // values are written in base-32 notation
            if (iter.hasNext()) {
                buff.append(" ");
            } else {
                buff.append("\n");
                return buff.toString();
            }
        }
    }

    private static String base32arrayRunLength(List<Integer> list) {
        int count = 0;
        Iterator<Integer> iter = list.iterator();
        FastStringBuffer buff = new FastStringBuffer(128);
        if (!iter.hasNext()) {
            return buff.toString();
        }
        int runLength = 1;
        int val = iter.next();
        int next;
        do {
            while (true) {
                if (iter.hasNext()) {
                    next = iter.next();
                    if (next == val) {
                        runLength++;
                    } else {
                        break;
                    }
                } else {
                    next = -1;
                    break;
                }
            }
            if (runLength != 1) {
                buff.append(Integer.toString(runLength));
                buff.append("*");
            }
            buff.append(Integer.toString(val, 32));    // values are written in base-32 notation
            if (++count == 20) {
                count = 0;
                buff.append("\n");
            } else {
                buff.append(" ");
            }
            val = next;
            runLength = 1;
        } while (val != -1);

        buff.append("\n");
        return buff.toString();
    }

    /**
     * Output an array of string values (using backslash-uuuu notation where appropriate)
     */

    private static String base32StringArray(List<String> in) {
        Iterator<String> iter = in.iterator();
        int count = 0;
        FastStringBuffer buff = new FastStringBuffer(128);
        if (!iter.hasNext()) {
            return "";
        }
        while (true) {
            if (++count == 20) {
                count = 0;
                buff.append("\n");
            }
            String value = iter.next();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                char b0 = "0123456789abcdef".charAt(c & 0xf);
                char b1 = "0123456789abcdef".charAt((c >> 4) & 0xf);
                char b2 = "0123456789abcdef".charAt((c >> 8) & 0xf);
                char b3 = "0123456789abcdef".charAt((c >> 12) & 0xf);
                buff.cat(b3);
                buff.cat(b2);
                buff.cat(b1);
                buff.cat(b0);
            }
            if (iter.hasNext()) {
                buff.append(" ");
            } else {
                return buff.toString();
            }
        }
    }

    private static void printStringArray(PrintStream o, Iterator<String> iter) {
        int count = 0;
        FastStringBuffer buff = new FastStringBuffer(128);
        if (!iter.hasNext()) {
            return;
        }
        while (true) {
            if (++count == 20) {
                count = 0;
                o.println(buff);
                buff.setLength(0);
            }
            String next = iter.next();
            appendJavaString(next, buff);
            if (iter.hasNext()) {
                buff.append(", ");
            } else {
                o.println(buff);
                return;
            }
        }
    }


    private static void appendJavaString(String value, FastStringBuffer buff) {
        buff.cat('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                buff.append("\\\\");
            } else if (c == '"') {
                buff.append("\\\"");
            } else if (c > 32 && c < 127) {
                buff.cat(c);
            } else {
                buff.append("\\u");
                char b0 = "0123456789abcdef".charAt(c & 0xf);
                char b1 = "0123456789abcdef".charAt((c >> 4) & 0xf);
                char b2 = "0123456789abcdef".charAt((c >> 8) & 0xf);
                char b3 = "0123456789abcdef".charAt((c >> 12) & 0xf);
                buff.cat(b3);
                buff.cat(b2);
                buff.cat(b1);
                buff.cat(b0);
            }
        }
        buff.cat('"');
    }

    /**
     * Main program. Run this program to regenerate the Java module UnicodeData.java against revised data
     * from the Unicode character database.
     * <p>Usage: java UnicodeDataGenerator dir >UnicodeData.java</p>
     * <p>where dir is the directory containing the files UnicodeData.text and CompositionExclusions.txt from the
     * Unicode character database.</p>
     */

    public static void main(/*@NotNull*/ String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java UnicodeDataGenerator dir UnicodeData.java");
            System.err.println("where dir is the directory containing the files UnicodeData.text and" +
                    " CompositionExclusions.txt from the Unicode character database");
        }
        dir = args[0];
        build();
        PrintStream o = new PrintStream(new FileOutputStream(new File(args[1])));
        //generateJava(o);
        generateXML(o);
    }
}

// * The class is derived from the sample program NormalizerData.java published by the
// * Unicode consortium. That code has been modified so that instead of building the run-time
// * data structures directly, they are written to a Java "source" module, which is then
// * compiled. Also, the ability to construct a condensed version of the data tables has been
// * removed.
