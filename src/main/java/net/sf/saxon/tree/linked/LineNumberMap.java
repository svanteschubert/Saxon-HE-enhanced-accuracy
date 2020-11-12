////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.linked;

import java.util.Arrays;

/**
 * Line numbers are not held in nodes in the tree, because they are not usually needed.
 * This class provides a map from element sequence numbers to line numbers: it is
 * linked to the root node of the tree.
 *
 * @author Michael H. Kay
 */

public class LineNumberMap {

    private int[] sequenceNumbers;
    private int[] lineNumbers;
    private int[] columnNumbers;
    private int allocated;

    /**
     * Create a LineNumberMap with an initial capacity of 200 nodes, which is expanded as necessary
     */

    public LineNumberMap() {
        sequenceNumbers = new int[200];
        lineNumbers = new int[200];
        columnNumbers = new int[200];
        allocated = 0;
    }

    /**
     * Set the line number corresponding to a given sequence number
     *
     * @param sequence the sequence number of the node
     * @param line     the line number position of the node
     * @param column   the column position of the node
     */

    public void setLineAndColumn(int sequence, int line, int column) {
        if (sequenceNumbers.length <= allocated + 1) {
            sequenceNumbers = Arrays.copyOf(sequenceNumbers, allocated * 2);
            lineNumbers = Arrays.copyOf(lineNumbers, allocated * 2);
            columnNumbers = Arrays.copyOf(columnNumbers, allocated * 2);
        }
        sequenceNumbers[allocated] = sequence;
        lineNumbers[allocated] = line;
        columnNumbers[allocated] = column;
        allocated++;
    }

    /**
     * Get the line number corresponding to a given sequence number
     *
     * @param sequence the sequence number held in the node
     * @return the corresponding line number
     */

    public int getLineNumber(int sequence) {
        if (sequenceNumbers.length > allocated) {
            condense();
        }
        int index = Arrays.binarySearch(sequenceNumbers, sequence);
        if (index < 0) {
            index = -index - 1;
            if (index > lineNumbers.length - 1) {
                index = lineNumbers.length - 1;
            }
        }
        return lineNumbers[index];
    }

    /**
     * Get the column number corresponding to a given sequence number
     *
     * @param sequence the sequence number held in the node
     * @return the corresponding column number
     */

    public int getColumnNumber(int sequence) {
        if (sequenceNumbers.length > allocated) {
            condense();
        }
        int index = Arrays.binarySearch(sequenceNumbers, sequence);
        if (index < 0) {
            index = -index - 1;
            if (index >= columnNumbers.length) {
                index = columnNumbers.length - 1;
            }
        }
        return columnNumbers[index];
    }

    private synchronized void condense() {
        sequenceNumbers = Arrays.copyOf(sequenceNumbers, allocated);
        lineNumbers = Arrays.copyOf(lineNumbers, allocated);
        columnNumbers = Arrays.copyOf(columnNumbers, allocated);
    }


}

