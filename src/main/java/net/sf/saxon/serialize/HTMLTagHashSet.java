////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

/**
 * A simple class for testing membership of a fixed set of case-insensitive ASCII strings.
 * The class must be initialised with enough space for all the strings,
 * it will go into an infinite loop if it fills. The string matching is case-blind,
 * using an algorithm that works only for ASCII.
 * <p>The class implements part of the java.util.Set interface; it could be replaced with
 * an implementation of java.util.Set together with a class that implemented a customized
 * equals() method.</p>
 */

public class HTMLTagHashSet {

    String[] strings;
    int size;

    public HTMLTagHashSet(int size) {
        strings = new String[size];
        this.size = size;
    }

    public void add(String s) {
        int hash = (hashCode(s) & 0x7fffffff) % size;
        while (true) {
            if (strings[hash] == null) {
                strings[hash] = s;
                return;
            }
            if (strings[hash].equalsIgnoreCase(s)) {
                return;
            }
            hash = (hash + 1) % size;
        }
    }

    public boolean contains(/*@NotNull*/ String s) {
        int hash = (hashCode(s) & 0x7fffffff) % size;
        while (true) {
            if (strings[hash] == null) {
                return false;
            }
            if (strings[hash].equalsIgnoreCase(s)) {
                return true;
            }
            hash = (hash + 1) % size;
        }
    }

    private int hashCode(String s) {
        // get a hashcode that doesn't depend on the case of characters.
        // This relies on the fact that char & 0xDF is case-blind in ASCII
        int hash = 0;
        int limit = s.length();
        if (limit > 24) limit = 24;
        for (int i = 0; i < limit; i++) {
            hash = (hash << 1) + (s.charAt(i) & 0xdf);
        }
        return hash;
    }
}
