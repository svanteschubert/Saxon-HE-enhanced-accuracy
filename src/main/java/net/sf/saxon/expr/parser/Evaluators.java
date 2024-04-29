////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

/**
 * Constants for different mechanisms of expression evaluation
 */

public class Evaluators {

    // These numeric constants must be stable as they are held in the SEF file

    public static final int UNDECIDED = -1;
    public static final int EVALUATE_LITERAL = 0;
    public static final int EVALUATE_VARIABLE = 1;
    public static final int EAGER_SEQUENCE = 2;
    public static final int MAKE_CLOSURE = 3;
    public static final int MAKE_MEMO_CLOSURE=4;
    public static final int RETURN_EMPTY_SEQUENCE = 5;
    public static final int EVALUATE_AND_MATERIALIZE_VARIABLE = 6;
    public static final int CALL_EVALUATE_OPTIONAL_ITEM = 7;
    public static final int ITERATE_AND_MATERIALIZE = 8;
    public static final int PROCESS = 9;
    public static final int LAZY_TAIL_EXPRESSION = 10;
    public static final int SHARED_APPEND_EXPRESSION = 11;
    public static final int MAKE_INDEXED_VARIABLE = 12;
    public static final int MAKE_SINGLETON_CLOSURE = 13;
    public static final int EVALUATE_SUPPLIED_PARAMETER = 14;
    public static final int STREAMING_ARGUMENT = 15;
    public static final int CALL_EVALUATE_SINGLE_ITEM = 16;



}

