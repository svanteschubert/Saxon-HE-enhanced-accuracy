// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the coverage data for a single module.
 *
 * <p>This class was introduced to avoid a map-of-maps that seemed to confuse the
 * transpiler.</p>
 */

public class CoverageRecord {
    Map<Integer, Integer> coverage = new HashMap<>();
    void addLocation(int line) {
        if (!coverage.containsKey(line)) {
            coverage.put(line, 0);
        }
    }
    int getCover(int line) {
        Integer count = coverage.get(line);
        if (count == null) {
            return 0;
        }
        return count;
    }
    void addCover(int line) {
        if (coverage.containsKey(line)) {
            coverage.put(line, coverage.get(line) + 1);
        } else {
            coverage.put(line, 1);
        }
    }
    List<Integer> coveredLines() {
        List<Integer> result = new ArrayList<>(coverage.keySet());
        result.sort(Integer::compareTo);
        return result;
    }
}
