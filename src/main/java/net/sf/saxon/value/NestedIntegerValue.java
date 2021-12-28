////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.AtomicMatchKey;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;

import java.util.Arrays;
import java.util.StringTokenizer;

/**
 * This class represents a dot-separated sequence of numbers such as 1.12.5, typically
 * used as a software version number.
 * <p>Although the class is implemented as an atomic type derived from xs:string, it
 * has not been integrated into the schema type hierarchy and cannot be used directly
 * in schema validation.</p>
 * <p>The class provides "smart" ordering, for example 1 &lt; 1.2 &lt; 1.12 &lt; 1.12.6</p>
 */


public class NestedIntegerValue extends AtomicValue implements Comparable, AtomicMatchKey {

    public static NestedIntegerValue ONE = new NestedIntegerValue(new int[]{1});
    public static NestedIntegerValue TWO = new NestedIntegerValue(new int[]{2});

    int[] value;

    public NestedIntegerValue(String v) throws XPathException {
        typeLabel = BuiltInAtomicType.STRING;
        parse(v);
    }

    public NestedIntegerValue(int[] val) {
        typeLabel = BuiltInAtomicType.STRING;
        value = val;
    }

    public static NestedIntegerValue parse(String v) throws XPathException {
        StringTokenizer st = new StringTokenizer(v, ".");
        int[] valuei = new int[st.countTokens()];
        try {
            for (int i = 0; st.hasMoreTokens(); i++) {
                valuei[i] = Integer.parseInt(st.nextToken());
            }
        } catch (NumberFormatException exc) {
            throw new XPathException("Nested integer value has incorrect format: " + v);
        }
        return new NestedIntegerValue(valuei);
    }

    public NestedIntegerValue append(int leaf) {
        int[] v = new int[value.length + 1];
        System.arraycopy(value, 0, v, 0, value.length);
        v[value.length] = leaf;
        return new NestedIntegerValue(v);
    }

    public NestedIntegerValue getStem() {
        if (value.length == 0) {
            return null;
        } else {
            int[] v = new int[value.length - 1];
            System.arraycopy(value, 0, v, 0, v.length);
            return new NestedIntegerValue(v);
        }
    }

    public int getDepth() {
        return value.length;
    }

    public int getLeaf() {
        if (value.length == 0) {
            return -1;
        } else {
            return value[value.length-1];
        }
    }


    @Override
    public Comparable getSchemaComparable() {
        return this;
    }

    @Override
    public AtomicMatchKey getXPathComparable(boolean ordered, StringCollator collator, int implicitTimezone) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof NestedIntegerValue) && Arrays.equals(value, ((NestedIntegerValue) o).value);
    }

    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.STRING;
    }

    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        NestedIntegerValue v = new NestedIntegerValue(value);
        v.typeLabel = typeLabel;
        return v;
    }

    @Override
    protected CharSequence getPrimitiveStringValue() {
        FastStringBuffer buffer = new FastStringBuffer(value.length * 2);
        for (int i = 0; i < value.length - 1; i++) {
            buffer.append(value[i] + ".");
        }
        buffer.append(value[value.length - 1] + "");
        return buffer;
    }


    @Override
    public int compareTo(Object other) {
        if (!(other instanceof NestedIntegerValue)) {
            throw new ClassCastException("NestedIntegerValue is not comparable to " + other.getClass());
        } else {
            NestedIntegerValue v2 = (NestedIntegerValue) other;
            for (int i = 0; i < value.length && i < v2.value.length; i++) {
                if (value[i] != v2.value[i]) {
                    if (value[i] < v2.value[i]) {
                        return -1;
                    } else {
                        return 1;
                    }
                }
            }
            return Integer.signum(value.length - v2.value.length);
        }
    }

}
