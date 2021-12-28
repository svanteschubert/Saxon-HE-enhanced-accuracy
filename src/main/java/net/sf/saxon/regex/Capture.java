////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

/**
 * Represents a match of a captured subgroup
 */
public class Capture {

    public int groupNr;
    public int start;
    public int end;

    public Capture(int groupNr, int start, int end) {
        this.groupNr = groupNr;
        this.start = start;
        this.end = end;
    }
}

