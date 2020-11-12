////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Closure;

import java.util.Arrays;
import java.util.Map;

/**
 * A ParameterSet is a set of parameters supplied when calling a template.
 * It is a collection of name-value pairs.
 * (Use of numeric IDs dropped in 9.6 to support separate compilation of packages)
 */

public class ParameterSet {
    private StructuredQName[] keys;
    private Sequence[] values;
    private boolean[] typeChecked;
    private int used = 0;

    public static ParameterSet EMPTY_PARAMETER_SET = new ParameterSet(0);

    /**
     * Create an empty parameter set
     */

    public ParameterSet() {
        this(10);
    }

    /**
     * Create a parameter set specifying the initial capacity
     *
     * @param capacity the nominal number of entries in the parameter set
     */

    public ParameterSet(int capacity) {
        keys = new StructuredQName[capacity];
        values = (Sequence[])new Sequence[capacity];
        typeChecked = new boolean[capacity];
    }

    /**
     * Create a parameter set from a name/value map
     * @param map the supplied map
     */

    public ParameterSet(Map<StructuredQName, Sequence> map) {
        this(map.size());
        int i = 0;
        for (Map.Entry<StructuredQName, Sequence> entry : map.entrySet()) {
            keys[i] = entry.getKey();
            values[i] = entry.getValue();
            typeChecked[i++] = false;
        }
        used = i;
    }

    /**
     * Create a parameter set as a copy of an existing parameter set
     *
     * @param existing the parameter set to be copied
     * @param extra    the space to be allocated for additional entries
     */

    public ParameterSet(ParameterSet existing, int extra) {
        this(existing.used + extra);
        for (int i = 0; i < existing.used; i++) {
            put(existing.keys[i], existing.values[i], existing.typeChecked[i]);
        }
    }

    /**
     * Get the number of parameters in the parameter set
     * @return the number of parameters
     */

    public int size() {
        return used;
    }

    /**
     * Add a parameter to the ParameterSet
     *
     * @param id      The parameter id, representing its name.
     * @param value   The value of the parameter
     * @param checked True if the caller has done static type checking against the required type
     */

    public void put(StructuredQName id, Sequence value, boolean checked) {
        for (int i = 0; i < used; i++) {
            if (keys[i].equals(id)) {
                values[i] = value;
                typeChecked[i] = checked;
                return;
            }
        }
        if (used + 1 > keys.length) {
            int newLength = used <= 5 ? 10 : used * 2;
            values = Arrays.copyOf(values, newLength);
            keys = Arrays.copyOf(keys, newLength);
            typeChecked = Arrays.copyOf(typeChecked, newLength);
        }
        keys[used] = id;
        typeChecked[used] = checked;
        values[used++] = value;
    }

    /**
     * Get the set of key names
     * @return the key names
     */

    public StructuredQName[] getParameterNames() {
        return keys;
    }

    /**
     * Get the index position of a parameter
     *
     * @param id The numeric parameter id, representing its name.
     * @return The index position of the parameter, or -1 if not defined
     */

    public int getIndex(StructuredQName id) {
        for (int i = 0; i < used; i++) {
            if (keys[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get the value of the parameter at a given index
     *
     * @param index the position of the entry required
     * @return the value of the parameter at that position
     */

    public Sequence getValue(int index) {
        return values[index];
    }

    /**
     * Determine whether the parameter at a given index has been type-checked
     *
     * @param index the position of the entry required
     * @return true if the parameter at that position has been type-checked
     */

    public boolean isTypeChecked(int index) {
        return typeChecked[index];
    }

    /**
     * Clear all values
     */

    public void clear() {
        used = 0;
    }

    /**
     * If any values are non-memo closures, expand them
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs evaluating any closures
     */

    public void materializeValues() throws XPathException {
        for (int i = 0; i < used; i++) {
            if (values[i] instanceof Closure) {
                values[i] = ((Closure) values[i]).reduce();
            }
        }
    }

    public static final int NOT_SUPPLIED = 0;
    public static final int SUPPLIED = 1;
    public static final int SUPPLIED_AND_CHECKED = 2;

}
