////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex.charclass;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.serialize.charcode.XMLCharacterData;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.tiny.TinyElementImpl;
import net.sf.saxon.type.Type;
import net.sf.saxon.z.*;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Data for Regular expression character categories. The data is in an XML file derived from the Unicode
 * database (In Saxon 9.6, this is based on Unicode 6.2.0). Since Saxon 9.4,
 * we no longer make use of Java's support for character categories since there are too many differences
 * from Unicode.
 */
public class Categories {

    /**
     * A Category is a CharacterClass represented in a regular expression as \p{Xx}.
     * The label Xx is retained, and can be used to determine whether or not two
     * categories are disjoint.
     */

    public static class Category implements CharacterClass {

        private String label;
        private IntPredicate predicate;

        public Category(String label, java.util.function.IntPredicate predicate) {
            this.label = label;
            this.predicate = predicate;
        }

        @Override
        public boolean test(int value) {
            return predicate.test(value);
        }

        @Override
        public boolean isDisjoint(CharacterClass other) {
            if (other instanceof Category) {
                char majorCat0 = label.charAt(0);
                String otherLabel = ((Category)other).label;
                char majorCat1 = otherLabel.charAt(0);
                return majorCat0 != majorCat1 ||
                        (label.length() > 1 && otherLabel.length() > 1 && !label.equals(otherLabel));

            } else if (other instanceof InverseCharacterClass) {
                return other.isDisjoint(this);
            } else if (other instanceof SingletonCharacterClass) {
                return !test(((SingletonCharacterClass)other).getCodepoint());
            } else if (other instanceof IntSetCharacterClass) {
                IntSet intSet = other.getIntSet();
                if (intSet.size() > 100) {
                    // too expensive to test, and increasingly likely to be non-disjoint anyway
                    return false;
                }
                IntIterator ii = intSet.iterator();
                while (ii.hasNext()) {
                    if (test(ii.next())) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        public IntSet getIntSet() {
            return extent(predicate);
        }

        private static IntSet extent(IntPredicate predicate) {
            if (predicate instanceof IntSetPredicate) {
                return ((IntSetPredicate) predicate).getIntSet();
            }
            return null;
        }
    }


    private static HashMap<String, Category> CATEGORIES = null;

    static void build() {

        CATEGORIES = new HashMap<>(30);

        InputStream in = Configuration.locateResource("categories.xml", new ArrayList<>(), new ArrayList<>());
        if (in == null) {
            throw new RuntimeException("Unable to read categories.xml file");
        }

        Configuration config = new Configuration();
        ParseOptions options = new ParseOptions();
        options.setSchemaValidationMode(Validation.SKIP);
        options.setDTDValidationMode(Validation.SKIP);
        options.setTreeModel(Builder.TINY_TREE);
        NodeInfo doc;
        try {
            doc = config.buildDocumentTree(new StreamSource(in, "categories.xml"), options).getRootNode();
        } catch (XPathException e) {
            throw new RuntimeException("Failed to build categories.xml", e);
        }

        int fp_name = config.getNamePool().allocateFingerprint("", "name");
        int fp_f = config.getNamePool().allocateFingerprint("", "f");
        int fp_t = config.getNamePool().allocateFingerprint("", "t");

        AxisIterator iter = doc.iterateAxis(AxisInfo.DESCENDANT, new NameTest(Type.ELEMENT, "", "cat", config.getNamePool()));
        iter.forEach(item -> {
            String cat = ((TinyElementImpl)item).getAttributeValue(fp_name);
            IntRangeSet irs = new IntRangeSet();
            for (NodeInfo r : ((NodeInfo)item).children(NodeKindTest.ELEMENT)) {
                String from = ((TinyElementImpl)r).getAttributeValue(fp_f);
                String to = ((TinyElementImpl) r).getAttributeValue(fp_t);
                irs.addRange(Integer.parseInt(from, 16), Integer.parseInt(to, 16));
            }
            CATEGORIES.put(cat, new Category(cat, new IntSetPredicate(irs)));
        });

        String c = "CLMNPSZ";
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            IntPredicate ip = null;
            for (Map.Entry<String, Category> entry : CATEGORIES.entrySet()) {
                if (entry.getKey().charAt(0) == ch) {
                    ip = ip == null ? entry.getValue() : ip.or(entry.getValue());
                }
            }
            String label = ch + "";
            CATEGORIES.put(label, new Category(label, ip));
        }
    }

    public final static CharacterClass ESCAPE_s =
            new IntSetCharacterClass(IntArraySet.make(new int[]{9, 10, 13, 32}, 4));

    public final static CharacterClass ESCAPE_S = new InverseCharacterClass(ESCAPE_s);

    public final static PredicateCharacterClass ESCAPE_i =
            new PredicateCharacterClass(value -> XMLCharacterData.isNCNameStart11(value) || value == ':');

    public final static CharacterClass ESCAPE_I = new InverseCharacterClass(ESCAPE_i);

    public final static PredicateCharacterClass ESCAPE_c =
            new PredicateCharacterClass(value -> XMLCharacterData.isNCName11(value) || value == ':');

    public final static CharacterClass ESCAPE_C = new InverseCharacterClass(ESCAPE_c);

    public final static Category ESCAPE_d = getCategory("Nd");

    public final static CharacterClass ESCAPE_D = new InverseCharacterClass(ESCAPE_d);

    static Category CATEGORY_P = getCategory("P");
    static Category CATEGORY_Z = getCategory("Z");
    static Category CATEGORY_C = getCategory("C");

    public final static PredicateCharacterClass ESCAPE_w =
            new PredicateCharacterClass(value -> !(CATEGORY_P.test(value) || CATEGORY_Z.test(value) || CATEGORY_C.test(value)));

    public final static CharacterClass ESCAPE_W = new InverseCharacterClass(ESCAPE_w);

    /**
     * Get a predicate to test characters for membership of one of the Unicode
     * character categories
     *
     * @param cat a one-character or two-character category name, for example L or Lu
     * @return a predicate that tests whether a given character belongs to the category
     */

    public synchronized static Category getCategory(String cat) {
        if (CATEGORIES == null) {
            build();
        }
        return CATEGORIES.get(cat);
    }


}

// The following stylesheet was used to generate the categories.xml file from the Unicode 6.2.0 database:

//<?xml version="1.0" encoding="UTF-8"?>
//<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
//    xmlns:xs="http://www.w3.org/2001/XMLSchema"
//    xmlns:f="http://local-functions/"
//    exclude-result-prefixes="xs f"
//    xpath-default-namespace="http://www.unicode.org/ns/2003/ucd/1.0"
//    version="3.0">
//
//    <!-- Output XML representation of character categories -->
//
//    <xsl:output method="xml" indent="yes" encoding="us-ascii" saxon:suppress-indentation="cat" xmlns:saxon="http://saxon.sf.net/"/>
//
//    <xsl:param name="v6" select="doc('ucd.all.flat.xml')"/>
//
//    <xsl:key name="cat-key" match="*[@cp]" use="@gc"/>
//    <xsl:key name="range-key" match="*[@first-cp]" use="@gc"/>
//
//    <xsl:template name="main">
//      <categories>
//        <xsl:variable name="categories" select="distinct-values($v6/ucd/repertoire/(char|reserved)/@gc)"/>
//        <xsl:for-each select="$categories">
//            <xsl:sort select="."/>
//
//            <cat name="{.}">
//              <xsl:variable name="chars" select="key('cat-key', ., $v6)/@cp"/>
//              <xsl:variable name="codes" select="for $c in $chars return f:hexToInt(0,$c)"/>
//
//              <xsl:variable name="ranges" as="element()*">
//                <xsl:for-each-group select="$codes" group-adjacent=". - position()">
//                  <range f="{f:intToHex(current-group()[1])}" t="{f:intToHex(current-group()[1] + count(current-group()) - 1)}"/>
//                </xsl:for-each-group>
//
//                <xsl:for-each select="key('range-key', ., $v6)">
//                  <range f="{f:intToHex(f:hexToInt(0,@first-cp))}" t="{f:intToHex(f:hexToInt(0,@last-cp))}"/>
//                </xsl:for-each>
//              </xsl:variable>
//
//              <xsl:perform-sort select="$ranges">
//                <xsl:sort select="f:hexToInt(0,@f)"/>
//              </xsl:perform-sort>
//
//            </cat>
//        </xsl:for-each>
//      </categories>
//    </xsl:template>
//
//
//    <xsl:function name="f:hexToInt" as="xs:integer">
//      <xsl:param name="acc" as="xs:integer"/>
//      <xsl:param name="in" as="xs:string"/>
//      <xsl:choose>
//        <xsl:when test="$in eq ''">
//          <xsl:sequence select="$acc"/>
//        </xsl:when>
//        <xsl:otherwise>
//          <xsl:variable name="first" select="string-length(substring-before('0123456789ABCDEF', substring($in, 1, 1)))"/>
//          <xsl:sequence select="f:hexToInt($acc * 16 + $first, substring($in, 2))"/>
//        </xsl:otherwise>
//      </xsl:choose>
//    </xsl:function>
//
//    <xsl:function name="f:intToHex" as="xs:string">
//      <xsl:param name="in" as="xs:integer"/>
//      <xsl:choose>
//        <xsl:when test="$in eq 0">
//          <xsl:sequence select="''"/>
//        </xsl:when>
//        <xsl:otherwise>
//          <xsl:variable name="last" select="substring('0123456789ABCDEF', $in mod 16 + 1, 1)"/>
//          <xsl:sequence select="concat(f:intToHex($in idiv 16), $last)"/>
//        </xsl:otherwise>
//      </xsl:choose>
//    </xsl:function>
//
//</xsl:stylesheet>
