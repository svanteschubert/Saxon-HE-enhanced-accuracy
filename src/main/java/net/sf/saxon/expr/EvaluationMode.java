////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.Evaluator;

/**
 * An evaluation mode represents a way in which expressions can be evaluated
 */

public enum EvaluationMode {

    // Note the integer codes must be stable as they are used in SEF files

    // TODO: combine EvaluationMode and Evaluator into a single class

    UNDECIDED(-1, Evaluator.EAGER_SEQUENCE),
    EVALUATE_LITERAL(0, Evaluator.LITERAL),
    EVALUATE_VARIABLE(1, Evaluator.VARIABLE),
    MAKE_CLOSURE(3, Evaluator.LAZY_SEQUENCE),
    MAKE_MEMO_CLOSURE(4, Evaluator.MEMO_CLOSURE),
    RETURN_EMPTY_SEQUENCE(5, Evaluator.EMPTY_SEQUENCE),
    EVALUATE_AND_MATERIALIZE_VARIABLE(6, Evaluator.VARIABLE),
    CALL_EVALUATE_OPTIONAL_ITEM(7, Evaluator.OPTIONAL_ITEM),
    ITERATE_AND_MATERIALIZE(8, Evaluator.EAGER_SEQUENCE),
    PROCESS(9, Evaluator.PROCESS),
    LAZY_TAIL_EXPRESSION(10, Evaluator.LAZY_TAIL),
    SHARED_APPEND_EXPRESSION(11, Evaluator.SHARED_APPEND),
    MAKE_INDEXED_VARIABLE(12, Evaluator.MAKE_INDEXED_VARIABLE),
    MAKE_SINGLETON_CLOSURE(13, Evaluator.SINGLETON_CLOSURE),
    EVALUATE_SUPPLIED_PARAMETER(14, Evaluator.SUPPLIED_PARAMETER),
    STREAMING_ARGUMENT(15, Evaluator.STREAMING_ARGUMENT),
    CALL_EVALUATE_SINGLE_ITEM(16, Evaluator.SINGLE_ITEM);

    private final int code;
    private final Evaluator evaluator;

    EvaluationMode(int code, Evaluator evaluator) {
        this.code = code;
        this.evaluator = evaluator;
    }

    /**
     * Get the integer code associated with an evaluation mode. These codes should be considered
     * stable, as they are recorded in SEF files
     * @return an integer code
     */
    public int getCode() {
        return code;
    }

    /**
     * Get the evaluator corresponding to a particular evaluation mode
     * @return the corresponding evaluator
     */

    public Evaluator getEvaluator() {
        return evaluator;
    }

    /**
     * Get the evaluation mode with a particular integer code. This is used when reloading a stylesheet
     * from a SEF file
     * @param code the integer code
     * @return the corresponding evaluation mode
     */

    public static EvaluationMode forCode(int code) {
        for (EvaluationMode eval : EvaluationMode.values()) {
            if (eval.getCode() == code) {
                return eval;
            }
        }
        return ITERATE_AND_MATERIALIZE;
    }

}

