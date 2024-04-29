////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.functions.AccessorFn;
import net.sf.saxon.value.DayTimeDurationValue;
import net.sf.saxon.value.NumericValue;

import java.math.BigDecimal;

/**
 * Utility class for collecting and reporting timing information, used only under diagnostic control
 */
public class Timer {

    private static final DayTimeDurationValue milliSecond = new DayTimeDurationValue(1, 0, 0, 0, 0, 1000);
    private final long start;
    private long prev;

    public Timer() {
        start = System.nanoTime();
        prev = start;
    }

    public static String showExecutionTimeNano(long nanosecs) {
        if (nanosecs < 1e9) {
            // time less than one second
            return (nanosecs / 1e6) + "ms";
        } else {
            try {
                double millisecs = nanosecs / 1e6;
                @SuppressWarnings("RedundantCast")
                DayTimeDurationValue d = (DayTimeDurationValue) milliSecond.multiply(millisecs);
                long days = ((NumericValue) d.getComponent(AccessorFn.Component.DAY)).longValue();
                long hours = ((NumericValue) d.getComponent(AccessorFn.Component.HOURS)).longValue();
                long minutes = ((NumericValue) d.getComponent(AccessorFn.Component.MINUTES)).longValue();
                BigDecimal seconds = ((NumericValue) d.getComponent(AccessorFn.Component.SECONDS)).getDecimalValue();
                StringBuilder fsb = new StringBuilder(256);
                if (days > 0) {
                    fsb.append(days + "days ");
                }
                if (hours > 0) {
                    fsb.append(hours + "h ");
                }
                if (minutes > 0) {
                    fsb.append(minutes + "m ");
                }
                fsb.append(seconds + "s");
                return fsb.toString() + " (" + nanosecs / 1e6 + "ms)";
            } catch (XPathException e) {
                return nanosecs / 1e6 + "ms";
            }

        }
    }

    public static String showMemoryUsed() {
        long value = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return "Memory used: " + (value / 1_000_000) + "Mb";
    }

    public void report(String label) {
        long time = System.nanoTime();
        System.err.println(label + " " + (time - prev)/1e6 + "ms");
        prev = time;
    }

    public void reportCumulative(String label) {
        long time = System.nanoTime();
        System.err.println(label + " " + (time - start)/1e6 + "ms");
        prev = time;
    }
}
