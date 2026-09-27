// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.z.IntHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

public class FeatureIndex {

    private final static Map<String, FeatureData> byName = new HashMap<>();

    private final static IntHashMap<FeatureData> byCode = new IntHashMap<>();

    public static Iterable<String> getNames() {
        return new TreeSet<String>(byName.keySet());
    }

    static {
        FeatureData.init();
        for (FeatureData data : FeatureData.featureList)  {
            byName.put(data.uri, data);
            byCode.put(data.code, data);
        }
    }

    public static boolean exists(String featureName) {
        return byName.containsKey(featureName);
    }

    public static FeatureData getData(String featureName)  {
        return byName.get(featureName);
    }

    public static FeatureData getData(int code) {
        return byCode.get(code);
    }

}

