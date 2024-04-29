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

