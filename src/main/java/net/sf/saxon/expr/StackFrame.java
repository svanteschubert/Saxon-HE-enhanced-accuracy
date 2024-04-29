////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.transpile.CSharpReplaceBody;

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

    public static final Sequence[] EMPTY_ARRAY_OF_SEQUENCE = new Sequence[0];
    public static final StackFrame EMPTY = new StackFrame(SlotManager.EMPTY, EMPTY_ARRAY_OF_SEQUENCE);

    public StackFrame(SlotManager map, Sequence[] slots) {
        this.map = map;
        this.slots = slots;
    }

    public SlotManager getStackFrameMap() {
        return map;
    }

    public Sequence[] getStackFrameValues() {
        return slots;
    }

    public void setStackFrameValues(Sequence[] values) {
        slots = values;
    }

    public StackFrame copy() {
        Sequence[] v2 = Arrays.copyOf(slots, slots.length);
        StackFrame s = new StackFrame(map, v2);
        if (dynamicStack != null) {
            s.dynamicStack = shallowCopy(dynamicStack);
        }
        return s;
    }

    public void pushDynamicValue(Sequence value) {
        if (this == StackFrame.EMPTY) {
            throw new IllegalStateException("Immutable stack frame");
        }
        if (dynamicStack == null) {
            dynamicStack = newStack();
        }
        dynamicStack.push(value);
    }

    private Stack<Sequence> newStack() {
        // Separate method for the benefit of C#
        return new Stack<>();
    }

    // Shallow-copy of a stack is tricky in C# because iteration reverses the order
    @CSharpReplaceBody(code="return new Stack<Saxon.Hej.om.Sequence>(new Stack<Saxon.Hej.om.Sequence>(old));")
    private Stack<Sequence> shallowCopy(Stack<Sequence> old) {
        Stack<Sequence> s2 = newStack();
        s2.addAll(old);
        return s2;
    }

    public Sequence popDynamicValue() {
        return dynamicStack.pop();
    }

    public boolean holdsDynamicValue() {
        return dynamicStack != null && !dynamicStack.empty();
    }


}

