////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.z.IntArraySet;
import net.sf.saxon.z.IntHashMap;
import net.sf.saxon.z.IntToIntHashMap;
import net.sf.saxon.z.IntToIntMap;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * This class holds data about the case-variants of Unicode characters. The data is automatically
 * generated from the Unicode database.
 */
public class CaseVariants {

    // Use one hashmap for characters with a single case variant, another for characters with multiple
    // case variants, to reduce the number of objects that need to be allocated

    private static IntToIntMap monoVariants = null;
    private static IntHashMap<int[]> polyVariants = null;


    static void build() {

        monoVariants = new IntToIntHashMap(2500);
        polyVariants = new IntHashMap<>(100);

        InputStream in = Configuration.locateResource("casevariants.xml", new ArrayList<>(), new ArrayList<>());
        if (in == null) {
            throw new RuntimeException("Unable to read casevariants.xml file");
        }

        Configuration config = new Configuration();
        ParseOptions options = new ParseOptions();
        options.setSchemaValidationMode(Validation.SKIP);
        options.setDTDValidationMode(Validation.SKIP);
        NodeInfo doc;
        try {
            doc = config.buildDocumentTree(new StreamSource(in, "casevariants.xml"), options).getRootNode();
        } catch (XPathException e) {
            throw new RuntimeException("Failed to build casevariants.xml", e);
        }

        AxisIterator iter = doc.iterateAxis(AxisInfo.DESCENDANT, new NameTest(Type.ELEMENT, "", "c", config.getNamePool()));
        while (true) {
            NodeInfo item = iter.next();
            if (item == null) {
                break;
            }
            String code = item.getAttributeValue("", "n");
            int icode = Integer.parseInt(code, 16);
            String variants = item.getAttributeValue("", "v");
            String[] vhex = variants.split(",");
            int[] vint = new int[vhex.length];
            for (int i=0; i<vhex.length; i++) {
                vint[i] = Integer.parseInt(vhex[i], 16);
            }
            if (vhex.length == 1) {
                monoVariants.put(icode, vint[0]);
            } else {
                polyVariants.put(icode, vint);
            }
        }
    }

    /**
     * Get the case variants of a character
     *
     * @param code the character whose case variants are required
     * @return the case variants of the character, excluding the character itself
     */

    public synchronized static int[] getCaseVariants(int code) {
        if (monoVariants == null) {
            build();
        }
        int mono = monoVariants.get(code);
        if (mono != monoVariants.getDefaultValue()) {
            return new int[]{mono};
        } else {
            int[] result = polyVariants.get(code);
            if (result == null) {
                return IntArraySet.EMPTY_INT_ARRAY;
            } else {
                return result;
            }
        }
    }

    /**
     * Get the case variants of roman letters (A-Z, a-z), other than the letters A-Z and a-z themselves
     */

    /*@NotNull*/ public static int[] ROMAN_VARIANTS = {0x0130, 0x0131, 0x212A, 0x017F};

    // The data file casevariants.xml was formed by applying the following query to the XML
    // version of the Unicode database (for Saxon 9.6, the Unicode 6.2.0 version was used)

//    declare namespace u = "http://www.unicode.org/ns/2003/ucd/1.0";
//    <variants>{
//    let $chars := doc('ucd.all.flat.xml')/ * / * /u:char[@suc!='#' or @slc!='#']
//    for $c in $chars
//    let $variants := ($chars[(@cp, @suc[.!='#']) = $c/(@cp, @suc[.!='#'])] |
//                          $chars[(@cp, @slc[.!='#']) = $c/(@cp, @slc[.!='#'])]) except $c
//    return
//         if (count($variants) gt 0) then
//           <c n="{$c/@cp}" v="{string-join($variants/@cp, ",")}"/>
//         else ()
//
//    }</variants>

}
