////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal class used for instrumentation purposes. It maintains a number of counters and displays these at the
 * end of the run. Currently implemented only for the Transform command line, with -t option set.
 */

public class Instrumentation {

    public static final boolean ACTIVE = false;

    public static HashMap<String, Long> counters = new HashMap<>();

    public static void count(String counter) {
        if (counters.containsKey(counter)) {
            counters.put(counter, counters.get(counter) + 1);
        } else {
            counters.put(counter, 1L);
        }
    }

    public static void count(String counter, long increment) {
        if (counters.containsKey(counter)) {
            counters.put(counter, counters.get(counter) + increment);
        } else {
            counters.put(counter, increment);
        }
    }

    public static void report() {
        if (ACTIVE && !counters.isEmpty()) {
            System.err.println("COUNTERS");
            for (Map.Entry<String, Long> c : counters.entrySet()) {
                System.err.println(c.getKey() + " = " + c.getValue());
            }
        }
    }

    public static void reset() {
        counters.clear();
    }

}

