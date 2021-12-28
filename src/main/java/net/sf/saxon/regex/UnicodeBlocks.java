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
import net.sf.saxon.om.AllElementsSpaceStrippingRule;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.Type;
import net.sf.saxon.z.IntBlockSet;
import net.sf.saxon.z.IntSet;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * This class provides knowledge of the names and contents of Unicode character blocks,
 * as referenced using the \p{IsXXXXX} construct in a regular expression. The underlying
 * data is in an XML resource file UnicodeBlocks.xml
 */
public class UnicodeBlocks {

    private static Map<String, IntSet> blocks = null;

    public static IntSet getBlock(String name) throws RESyntaxException {
        if (blocks == null) {
            readBlocks(new Configuration());
        }
        IntSet cc = blocks.get(name);
        if (cc != null) {
            return cc;
        }
        cc = blocks.get(normalizeBlockName(name));
        return cc;
    }

    private static String normalizeBlockName(String name) {
        FastStringBuffer fsb = new FastStringBuffer(name.length());
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            switch (c) {
                case ' ':
                case '\t':
                case '\r':
                case '\n':
                case '_':
                    // no action
                    break;
                default:
                    fsb.cat(c);
            }
        }
        return fsb.toString();
    }

    private synchronized static void readBlocks(Configuration config) throws RESyntaxException {
        blocks = new HashMap<>(250);
        InputStream in = Configuration.locateResource("unicodeBlocks.xml", new ArrayList<>(), new ArrayList<ClassLoader>());
        if (in == null) {
            throw new RESyntaxException("Unable to read unicodeBlocks.xml file");
        }

        ParseOptions options = new ParseOptions();
        options.setSchemaValidationMode(Validation.SKIP);
        options.setDTDValidationMode(Validation.SKIP);
        options.setSpaceStrippingRule(AllElementsSpaceStrippingRule.getInstance());
        TreeInfo doc;
        try {
            doc = config.buildDocumentTree(new StreamSource(in, "unicodeBlocks.xml"), options);
        } catch (XPathException e) {
            throw new RESyntaxException("Failed to process unicodeBlocks.xml: " + e.getMessage());
        }

        AxisIterator iter = doc.getRootNode().iterateAxis(AxisInfo.DESCENDANT, new NameTest(Type.ELEMENT, "", "block", config.getNamePool()));
        while (true) {
            NodeInfo item = iter.next();
            if (item == null) {
                break;
            }
            String blockName = normalizeBlockName(item.getAttributeValue("", "name"));
            IntSet range = null;
            for (NodeInfo rangeElement : item.children(NodeKindTest.ELEMENT)) {
                int from = Integer.parseInt(rangeElement.getAttributeValue("", "from").substring(2), 16);
                int to = Integer.parseInt(rangeElement.getAttributeValue("", "to").substring(2), 16);
                IntSet cr = new IntBlockSet(from, to);
                if (range == null) {
                    range = cr;
                } else if (range instanceof IntBlockSet) {
                    range = range.mutableCopy().union(cr);
                } else {
                    range = range.union(cr);
                }
            }
            blocks.put(blockName, range);
        }

    }
}

