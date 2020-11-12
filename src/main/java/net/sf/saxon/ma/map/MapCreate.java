////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.map;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;


/**
 * This is a variant of the map:new() or map:merge() function which differs in that duplicate
 * keys are considered an error. It is not available directly to users, but underpins the map
 * constructor expression in XPath and the xsl:map instruction in XSLT.
 *
 * Moved to the Saxon namespace in 9.8 - see bug 2740 and test case function-available-1017
 */
public class MapCreate extends SystemFunction {

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {

        SequenceIterator iter = arguments[0].iterate();
        MapItem baseMap = (MapItem) iter.next();
        if (baseMap == null) {
            return new HashTrieMap();
        } else {
            if (!(baseMap instanceof HashTrieMap)) {
                baseMap = HashTrieMap.copy(baseMap);
            }
            MapItem next;
            while ((next = (MapItem) iter.next()) != null) {
                for (KeyValuePair pair : next.keyValuePairs()) {
                    if (baseMap.get(pair.key) != null) {
                        throw new XPathException("Duplicate key value (" + pair.key + ") in map", "XQDY0137");
                    } else {
                        baseMap = baseMap.addEntry(pair.key, pair.value);
                    }
                }
            }
            return baseMap;
        }

    }

    @Override
    public String getStreamerName() {
        return "NewMap";
    }
}

// Copyright (c) 2010-2020 Saxonica Limited
