////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.gnode.NamedXNodeType;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.z.IntBlockSet;
import net.sf.saxon.z.IntSet;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * This singleton class provides knowledge of the names and contents of Unicode character blocks,
 * as referenced using the <code>\p{IsXXXXX}</code> construct in a regular expression. The underlying
 * data is in an XML resource file UnicodeBlocks.xml
 */
public class UnicodeBlocks {

    private final Map<String, IntSet> blocks = new HashMap<>(250);

    private UnicodeBlocks() {
        build();
    }

    private static class Holder {
        // See https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
        // The idea here is that the initialization occurs the first time getInstance() is called,
        // and it is automatically synchronized by virtue of the Java class loading rules.
        public static final UnicodeBlocks INSTANCE = new UnicodeBlocks();
    }

    private static UnicodeBlocks getInstance() {
        return Holder.INSTANCE;
    }

    public static IntSet getBlock(String name) throws RESyntaxException {
        UnicodeBlocks instance = getInstance();
        IntSet cc = instance.blocks.get(name);
        if (cc != null) {
            return cc;
        }
        cc = instance.blocks.get(normalizeBlockName(name));
        return cc;
    }

    private static String normalizeBlockName(String name) {
        StringBuilder fsb = new StringBuilder(name.length());
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
                    fsb.append(c);
                    break;
            }
        }
        return fsb.toString();
    }

    private void build() throws RESyntaxException {
        InputStream in = Version.platform.locateResource("unicodeBlocks.xml", new ArrayList<>());
        if (in == null) {
            throw new RESyntaxException("Unable to read unicodeBlocks.xml file");
        }

        Configuration config = new Configuration();
        ParseOptions options = new ParseOptions()
                .withSchemaValidationMode(Validation.SKIP)
                .withDTDValidationMode(Validation.SKIP)
                .withSpaceStrippingRule(AllElementsSpaceStrippingRule.INSTANCE)
                .withPleaseCloseAfterUse(true);
        TreeInfo doc;
        try {
            doc = config.buildDocumentTree(new StreamSource(in, "unicodeBlocks.xml"), options);
        } catch (XPathException e) {
            throw new RESyntaxException("Failed to process unicodeBlocks.xml: " + e.getMessage());
        }

        SequenceIterator iter = doc.getRootNode().iterateDescendantAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.NULL, "block", config));
        while (true) {
            NodeInfo item = (NodeInfo)iter.next();
            if (item == null) {
                break;
            }
            String blockName = normalizeBlockName(item.getAttributeValue(NamespaceUri.NULL, "name"));
            System.err.println("Loading " + blockName);
            IntSet range = null;
            for (NodeInfo rangeElement : item.children(NodeKindType.ELEMENT)) {
                int from = Integer.parseInt(rangeElement.getAttributeValue(NamespaceUri.NULL, "from").substring(2), 16);
                int to = Integer.parseInt(rangeElement.getAttributeValue(NamespaceUri.NULL, "to").substring(2), 16);
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

