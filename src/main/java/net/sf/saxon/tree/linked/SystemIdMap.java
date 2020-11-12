////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import java.util.Arrays;

/**
 * System IDs are not held in nodes in the tree, because they are usually the same
 * for a whole document.
 * <p>
 * This class provides a map from element sequence numbers to System IDs: it is
 * linked to the root node of the tree.</p>
 * <p>
 * Note that the System ID is not necessarily the same as the Base URI. The System ID relates
 * to the external entity in which a node was physically located; this provides a default for
 * the Base URI, but this may be modified by specifying an xml:base attribute.</p>
 * <p>
 * The system IDs of nodes are assumed to be immutable; they can only be set sequentially,
 * as the nodes are being created. </p>
 *
 * @author Michael H. Kay
 */

public class SystemIdMap {

    private int[] sequenceNumbers;
    private String[] uris;
    private int allocated;

    public SystemIdMap() {
        sequenceNumbers = new int[4];
        uris = new String[4];
        allocated = 0;
    }

    /**
     * Set the system ID corresponding to a given sequence number
     * @param sequence the sequence number (position in document order) of the
     *                 node whose system ID is to be set. This must be one greater
     *                 that the previous system ID.
     */

    public void setSystemId(int sequence, String uri) {
        if (allocated > 0) {
            // ignore it if same as previous
            if (uri.equals(uris[allocated - 1])) {
                return;
            }
            if (sequence <= sequenceNumbers[allocated - 1]) {
                throw new IllegalArgumentException("System IDs of nodes are immutable");
            }
        }
        if (sequenceNumbers.length <= allocated + 1) {
            sequenceNumbers = Arrays.copyOf(sequenceNumbers, allocated * 2);
            uris = Arrays.copyOf(uris, allocated * 2);
        }
        sequenceNumbers[allocated] = sequence;
        uris[allocated] = uri;
        allocated++;
    }

    /**
     * Get the system ID corresponding to a given sequence number
     */

    /*@Nullable*/
    public String getSystemId(int sequence) {
        if (allocated == 0) {
            return null;
        }
        // could use a binary chop, but it's not important
        for (int i = 1; i < allocated; i++) {
            if (sequenceNumbers[i] > sequence) {
                return uris[i - 1];
            }
        }
        return uris[allocated - 1];
    }


}
