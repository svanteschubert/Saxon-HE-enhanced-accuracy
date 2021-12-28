////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

/**
 * Statistics on the size of TinyTree instances, kept so that the system can learn how much space to allocate to new trees
 */
public class Statistics {

    // We maintain statistics, recording how large the trees created under this Java VM
    // turned out to be. These figures are then used when allocating space for new trees, on the assumption
    // that there is likely to be some uniformity. The statistics are initialized to an arbitrary value
    // so that they can be used every time including the first time. The count of how many trees have been
    // created so far is initialized artificially to 5, to provide some smoothing if the first real tree is
    // atypically large or small.

    private int treesCreated = 5;
    private double averageNodes = 4000.0;
    private double averageAttributes = 100.0;
    private double averageNamespaces = 20.0;
    private double averageCharacters = 4000.0;

    public Statistics() {
    }

    public Statistics(int nodes, int atts, int namespaces, int chars) {
        this.averageNodes = nodes;
        this.averageAttributes = atts;
        this.averageNamespaces = namespaces;
        this.averageCharacters = chars;
    }

    public double getAverageNodes() {
        return averageNodes;
    }

    public double getAverageAttributes() {
        return averageAttributes;
    }

    public double getAverageNamespaces() {
        return averageNamespaces;
    }

    public double getAverageCharacters() {
        return averageCharacters;
    }

    /**
     * Update the statistics held in static data. We don't bother to sychronize, on the basis that it doesn't
     * matter if the stats are wrong.
     * @param numberOfNodes the number of (non-attribute, non-namespace) nodes
     * @param numberOfAttributes the number of attribute nodes
     * @param numberOfNamespaces the number of namespace bindings (deltas on namespace nodes)
     * @param chars the number of characters in text nodes
     */

    public synchronized void updateStatistics(int numberOfNodes, int numberOfAttributes, int numberOfNamespaces, int chars) {
        int n0 = treesCreated;
        if (n0 < 1000000) {  // it should have stabilized by then, and we don't want to overflow
            int n1 = treesCreated + 1;
            treesCreated = n1;
            averageNodes = ((averageNodes * n0) + numberOfNodes) / n1;
            if (averageNodes < 10.0) {
                averageNodes = 10.0;
            }
            averageAttributes = ((averageAttributes * n0) + numberOfAttributes) / n1;
            if (averageAttributes < 10.0) {
                averageAttributes = 10.0;
            }
            averageNamespaces = ((averageNamespaces * n0) + numberOfNamespaces) / n1;
            if (averageNamespaces < 5.0) {
                averageNamespaces = 5.0;
            }
            averageCharacters = ((averageCharacters * n0) + chars) / n1;
            if (averageCharacters < 100.0) {
                averageCharacters = 100.0;
            }
        }

    }

    public String toString() {
        return treesCreated + "(" + averageNodes + "," + averageAttributes + "," + averageNamespaces + "," + averageCharacters + ")";
    }

}


