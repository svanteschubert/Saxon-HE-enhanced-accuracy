////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

/**
 * Enumeration class giving the different streamability categories defined for stylesheet functions in XSLT 3.0
 */

public enum FunctionStreamability {
    UNCLASSIFIED("unclassified"),
    ABSORBING("absorbing"),
    INSPECTION("inspection"),
    FILTER("filter"),
    SHALLOW_DESCENT("shallow-descent"),
    DEEP_DESCENT("deep-descent"),
    ASCENT("ascent");

    public String streamabilityStr;

    public boolean isConsuming() {
        return this == ABSORBING || this == SHALLOW_DESCENT || this == DEEP_DESCENT;
    }

    public boolean isStreaming() {
        return this != UNCLASSIFIED;
    }

    FunctionStreamability(String v) {
        streamabilityStr = v;
    }

    public static FunctionStreamability of(String v) {
        switch (v) {
            case "unclassified":
            default:
                return UNCLASSIFIED;
            case "absorbing":
                return ABSORBING;
            case "inspection":
                return INSPECTION;
            case "filter":
                return FILTER;
            case "shallow-descent":
                return SHALLOW_DESCENT;
            case "deep-descent":
                return DEEP_DESCENT;
            case "ascent":
                return ASCENT;
        }
    }

}
