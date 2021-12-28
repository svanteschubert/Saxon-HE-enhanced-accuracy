////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

/**
 * Defines switches that can be used to control which optimizations take place.
 * The object is immutable.
 */
public class OptimizerOptions {

    public static final int LOOP_LIFTING = 1;
    public static final int EXTRACT_GLOBALS = 2;
    public static final int INLINE_VARIABLES = 4;
    public static final int INLINE_FUNCTIONS = 8;
    public static final int INDEX_VARIABLES = 16;
    public static final int CREATE_KEYS = 32;
    public static final int BYTE_CODE = 64;
    public static final int COMMON_SUBEXPRESSIONS = 128;
    public static final int MISCELLANEOUS = 256;
    public static final int SWITCH = 512;
    public static final int JIT = 1024;
    public static final int RULE_SET = 2048;
    public static final int REGEX_CACHE = 4096;
    public static final int VOID_EXPRESSIONS = 8192;
    public static final int TAIL_CALLS = 16384;
    public static final int CONSTANT_FOLDING = 32768;

    private int options;

    public final static OptimizerOptions FULL_HE_OPTIMIZATION = new OptimizerOptions("lvmt");
    public final static OptimizerOptions FULL_EE_OPTIMIZATION = new OptimizerOptions(-1);

    public OptimizerOptions(int options) {
        this.options = options;
    }

    public OptimizerOptions(String flags) {
        int opt = 0;
        if (flags.startsWith("-")) {
            opt = -1;
            for (int i = 0; i < flags.length(); i++) {
                char c = flags.charAt(i);
                opt &= ~decodeFlag(c);
            }
        } else {
            for (int i = 0; i < flags.length(); i++) {
                char c = flags.charAt(i);
                opt |= decodeFlag(c);
            }
        }
        this.options = opt;
    }

    private int decodeFlag(char flag) {
        switch (flag) {
            case 'c':
                return BYTE_CODE;
            case 'd':
                return VOID_EXPRESSIONS;
            case 'e':
                return REGEX_CACHE;
            case 'f':
                return INLINE_FUNCTIONS;
            case 'g':
                return EXTRACT_GLOBALS;
            case 'j':
                return JIT;
            case 'k':
                return CREATE_KEYS;
            case 'l':
                return LOOP_LIFTING;
            case 'm':
                return MISCELLANEOUS;
            case 'n':
                return CONSTANT_FOLDING;
            case 'r':
                return RULE_SET;
            case 's':
                return COMMON_SUBEXPRESSIONS;
            case 't':
                return TAIL_CALLS;
            case 'v':
                return INLINE_VARIABLES;
            case 'w':
                return SWITCH;
            case 'x':
                return INDEX_VARIABLES;

            default: return 0;
        }
    }

    public OptimizerOptions intersect(OptimizerOptions other) {
        return new OptimizerOptions(options & other.options);
    }

    public OptimizerOptions union(OptimizerOptions other) {
        return new OptimizerOptions(options | other.options);
    }

    public OptimizerOptions except(OptimizerOptions other) {
        return new OptimizerOptions(options & ~other.options);
    }

    public String toString() {
        String result = "";
        if (isSet(BYTE_CODE)) {
            result += "c";
        }
        if (isSet(VOID_EXPRESSIONS)) {
            result += "d";
        }
        if (isSet(REGEX_CACHE)) {
            result += "e";
        }
        if (isSet(INLINE_FUNCTIONS)) {
            result += "f";
        }
        if (isSet(EXTRACT_GLOBALS)) {
            result += "g";
        }
        if (isSet(JIT)) {
            result += "j";
        }
        if (isSet(CREATE_KEYS)) {
            result += "k";
        }
        if (isSet(LOOP_LIFTING)) {
            result += "l";
        }
        if (isSet(MISCELLANEOUS)) {
            result += "m";
        }
        if (isSet(CONSTANT_FOLDING)) {
            result += "n";
        }
        if (isSet(RULE_SET)) {
            result += "r";
        }
        if (isSet(COMMON_SUBEXPRESSIONS)) {
            result += "s";
        }
        if (isSet(TAIL_CALLS)) {
            result += "t";
        }
        if (isSet(INLINE_VARIABLES)) {
            result += "v";
        }
        if (isSet(SWITCH)) {
            result += "w";
        }
        if (isSet(INDEX_VARIABLES)) {
            result += "x";
        }
        return result;
    }

    public boolean isSet(int option) {
        return (options & option) != 0;
    }

    public int getOptions() {
        return options;
    }
}

