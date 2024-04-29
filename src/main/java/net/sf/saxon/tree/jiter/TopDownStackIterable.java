////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package net.sf.saxon.tree.jiter;

import java.util.Iterator;
import java.util.Stack;

/**
 * Provides an iterable over a stack that iterates in order from top to bottom.
 * <p>Note: Java stacks natively iterate from bottom to top; C# stacks from top to bottom.</p>
 * @param <T> the type of the items on the stack
 */

public class TopDownStackIterable<T> implements Iterable<T> {

    private final Stack<T> stack;

    /**
     * Construct a top-down iterable view of a stack
     * @param stack the stack over which the top-down iterator will iterate
     */
    public TopDownStackIterable(Stack<T> stack) {
        this.stack = stack;
    }

    /**
     * Returns an iterator over elements in the stack, starting at the top of stack and proceeding to the bottom
     * @return a descending Iterator.
     */

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            int i = stack.size()-1;

            public boolean hasNext() {
                return i >= 0;
            }

            public T next() {
                return stack.get(i--);
            }
        };
    }
}


