////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.regex;

import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntSet;

import java.util.HashMap;
import java.util.Map;

/**
 * The History object is used to avoid backtracking too far. In particular, when a greedy repeat
 * operator allows zero repetitions (something we try to optimize away, but it isn't always possible,
 * then if we backtrack all the way to the start position, we only allow a match at this position if
 * no match at this position is currently recorded in the history.
 */
public class History {

    private Map<Operation, IntSet> zeroLengthMatches = new HashMap<Operation, IntSet>();

    public boolean isDuplicateZeroLengthMatch(Operation op, int position) {
        IntSet positions = zeroLengthMatches.get(op);
        if (positions == null) {
            positions = new IntHashSet(position);
            positions.add(position);
            zeroLengthMatches.put(op, positions);
            return false;
        } else {
            // return true if the position was already present in the list
            return !positions.add(position);
        }
    }
}

