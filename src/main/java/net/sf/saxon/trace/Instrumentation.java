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
 * Internal class used for instrumentation purposes. It maintains a number of counters and displays these on request.
 * The counters are output by calling {@code Instrumentation.report()}, typically at the end of a run.
 */

public class Instrumentation {

    // Set this flag to true to enable counters to be maintained
    public static final boolean ACTIVE = false;

    public static HashMap<String, Long> counters = new HashMap<>();

    // Increment a named counter
    public static void count(String counter) {
        if (ACTIVE) {
            if (counters.containsKey(counter)) {
                counters.put(counter, counters.get(counter) + 1);
            } else {
                counters.put(counter, 1L);
            }
        }
    }

    // Increase a named counter by a set amount
    public static void count(String counter, long increment) {
        if (ACTIVE) {
            if (counters.containsKey(counter)) {
                counters.put(counter, counters.get(counter) + increment);
            } else {
                counters.put(counter, increment);
            }
        }
    }

    public static String callStack() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        return stacktrace[3].getClassName() + "." + stacktrace[3].getMethodName() +
                                      " from " + stacktrace[4].getClassName() + "." + stacktrace[4].getMethodName() +
                                      " from " + stacktrace[5].getClassName() + "." + stacktrace[5].getMethodName();
    }

    // Output the current counter values
    public static void report() {
        if (ACTIVE && !counters.isEmpty()) {
            System.err.println("COUNTERS");
            for (Map.Entry<String, Long> c : counters.entrySet()) {
                System.err.println(c.getKey() + " = " + c.getValue());
            }
        }
    }

    // Reset all counters
    public static void reset() {
        if (ACTIVE) {
            counters.clear();
        }
    }

}

