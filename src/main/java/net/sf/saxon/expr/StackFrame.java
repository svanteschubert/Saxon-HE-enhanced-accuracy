////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;

import java.util.Arrays;
import java.util.Stack;

/**
 * This class represents a stack frame holding details of the variables used in a function or in
 * an XSLT template.
 */

public class StackFrame {
    protected SlotManager map;
    protected Sequence[] slots;
    protected Stack<Sequence> dynamicStack;

    public static final StackFrame EMPTY = new StackFrame(SlotManager.EMPTY, new Sequence[0]);

    public StackFrame(SlotManager map, Sequence[] slots) {
        this.map = map;
        this.slots = (Sequence[])slots;
    }

    public SlotManager getStackFrameMap() {
        return map;
    }

    public Sequence[] getStackFrameValues() {
        return slots;
    }

    public void setStackFrameValues(Sequence[] values) {
        slots = (Sequence[])values;
    }

    public StackFrame copy() {
        Sequence[] v2 = Arrays.copyOf(slots, slots.length);
        StackFrame s = new StackFrame(map, v2);
        if (dynamicStack != null) {
            s.dynamicStack = new Stack<>();
            s.dynamicStack.addAll(dynamicStack);
        }
        return s;
    }

    public void pushDynamicValue(Sequence value) {
        if (this == StackFrame.EMPTY) {
            throw new IllegalStateException("Immutable stack frame");
        }
        if (dynamicStack == null) {
            dynamicStack = new Stack<>();
        }
        dynamicStack.push(value);
    }

    public Sequence popDynamicValue() {
        return dynamicStack.pop();
    }

    public boolean holdsDynamicValue() {
        return dynamicStack != null && !dynamicStack.empty();
    }


}

