////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.om;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A prefix pool maintains a two-way mapping from namespace prefixes (as strings) to
 * integer prefix codes. Prefix codes always fit in 10 bits, but are handled as ints.
 *
 * Until 9.8, prefixes were managed by the NamePool. The NamePool now only handles
 * fingerprints, which are integer representations of the URI and local name parts of
 * a QName. Prefix codes are now used only in the tinytree, and the table of codes
 * is local to a document. For this reason, access is not synchronised.
 */
public class PrefixPool {

    String[] prefixes = new String[8];
    int used = 0;
    Map<String, Integer> index = null;

    public PrefixPool() {
        prefixes[0] = "";
        used = 1;
    }

    /**
     * Get the prefix code corresponding to a given prefix, allocating a new code if necessary
     * @param prefix the namespace prefix. If empty, the prefix code is always zero.
     * @return the integer prefix code (always fits in 10 bits)
     */

    public int obtainPrefixCode(String prefix) {
        if (prefix.isEmpty()) {
            return 0;
        }
        // Create an index if it's going to be useful
        if (index == null && used > 8) {
            makeIndex();
        }
        // See if the prefix is already known
        if (index != null) {
            Integer existing = index.get(prefix);
            if (existing != null) {
                return existing;
            }
        } else {
            for (int i=0; i<used; i++) {
                if (prefixes[i].equals(prefix)) {
                    return i;
                }
            }
        }
        // Allocate a new code
        int code = used++;
        if (used >= prefixes.length) {
            prefixes = Arrays.copyOf(prefixes, used * 2);
        }
        prefixes[code] = prefix;
        if (index != null) {
            index.put(prefix, code);
        }
        return code;
    }

    private void makeIndex() {
        index = new HashMap<>(used);
        for (int i=0; i<used; i++) {
            index.put(prefixes[i], i);
        }
    }

    /**
     * Get the prefix corresponding to a given code
     * @param code the prefix code (which must have been allocated)
     * @return the corresponding prefix
     * @throws IllegalArgumentException if the code has not been allocated
     */

    public String getPrefix(int code) {
        if (code < used) {
            return prefixes[code];
        }
        throw new IllegalArgumentException("Unknown prefix code " + code);
    }

    /**
     * Eliminate unused space, on the assumption that no further prefixes will be added to the pool,
     * and that subsequent access will be to get the prefix for a code, and not vice versa.
     */

    public void condense() {
        prefixes = Arrays.copyOf(prefixes, used);
        index = null;
    }
}

