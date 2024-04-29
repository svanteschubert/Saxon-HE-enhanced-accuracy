////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

import net.sf.saxon.str.LargeTextBuffer;

import java.util.Arrays;

/**
 * Statistics on the size of TinyTree instances, kept so that the system can learn how much space to allocate to new trees
 */
public class Statistics {

    // We maintain statistics, recording how large the trees created under this Java VM
    // turned out to be. These figures are then used when allocating space for new trees, on the assumption
    // that there is likely to be some uniformity. The statistics are initialized to an arbitrary value
    // so that they can be used every time including the first time.

    // We keep data for the last 10 trees created, noting the space actually used for various arrays.
    // To decide the space allocation for the next tree, we examine these last 10 entries. The values
    // are combined by a bitwise "or" of these values, yielding a value that is typically a bit higher
    // than their maximum. So we're generally allocating more space than we need, but not by too much.
    // The algorithm works best when all the trees have similar sizes.

    private int treesCreated = 0;
    private final int[] last10Nodes = new int[10];
    private final int[] last10Attributes = new int[10];
    private final int[] last10Namespaces = new int[10];
    private final int[] last10Characters = new int[10];

    public Statistics() {
        this(4000, 100, 20, 4000);
    }

    public Statistics(int nodes, int atts, int namespaces, int chars) {
        Arrays.fill(last10Nodes, nodes);
        Arrays.fill(last10Attributes, atts);
        Arrays.fill(last10Namespaces, namespaces);
        Arrays.fill(last10Characters, chars);
    }

    private int getUpperBound(int[] last10) {
        int bits = 0;
        for (int i = 0; i < 10; i++) {
            bits |= last10[i];
        }
        return bits;
    }

    public int getAverageNodes() {
        return getUpperBound(last10Nodes);
    }

    public int getAverageAttributes() {
        return getUpperBound(last10Attributes);
    }

    public int getAverageNamespaces() {
        return getUpperBound(last10Namespaces);
    }

    public int getAverageCharacters() {
        return getUpperBound(last10Characters);
    }

    /**
     * Update the statistics held in static data. We don't bother to synchronize, on the basis that it doesn't
     * matter if the stats are wrong.
     * @param numberOfNodes the number of (non-attribute, non-namespace) nodes
     * @param numberOfAttributes the number of attribute nodes
     * @param numberOfNamespaces the number of namespace bindings (deltas on namespace nodes)
     * @param textBuffer the text buffer
     */

    public synchronized void updateStatistics(int numberOfNodes, int numberOfAttributes, int numberOfNamespaces, LargeTextBuffer textBuffer) {
        //System.err.println("STATS " + Objects.hashCode(this) + "@" + treesCreated + "= average " + getAverageNodes() + " actual " + numberOfNodes);
        int n0 = treesCreated;
        if (n0 < 1000000) {  // it should have stabilized by then, and we don't want to overflow
            int n = treesCreated++ % 10;
            last10Nodes[n] = numberOfNodes;
            last10Attributes[n] = numberOfAttributes;
            last10Namespaces[n] = numberOfNamespaces;
            last10Characters[n] = Math.max(textBuffer.length(), 65536);
        }

    }

    public String toString() {
        return treesCreated + "(" + getAverageNodes() + "," + getAverageAttributes()
                + "," + getAverageNamespaces() + "," + getAverageCharacters() + ")";
    }

}


