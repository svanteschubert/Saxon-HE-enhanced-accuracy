// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

public class OperatorInfo {

    /**
     * Get a string representation of an operator
     */

    public String toString(OperatorSymbol symbol) {
        return symbol.toString();
    }

    /**
     * Get the precedence associated with a given operator
     *
     * @param symbol the operator in question
     * @return a higher number for higher precedence (closer binding)
     */

    public static int operatorPrecedence(OperatorSymbol symbol) {
        return switch (symbol) {
            case OR -> (4);
            case AND -> (5);
            case FEQ -> (6);
            case FNE -> (6);
            case FLT -> (6);
            case FGT -> (6);
            case FLE -> (6);
            case FGE -> (6);
            case EQUALS -> (6);
            case NE -> (6);
            case LT -> (6);
            case LE -> (6);
            case GT -> (6);
            case GE -> (6);
            case IS -> (6);
            case IS_NOT -> (6);
            case PRECEDES -> (6);
            case FOLLOWS -> (6);
            case PRECEDES_OR_IS -> (6);
            case FOLLOWS_OR_IS -> (6);
            case OTHERWISE -> (7);
            case CONCAT -> (8);
            case TO -> (9);
            case PLUS -> (10);
            case MINUS -> (10);
            case TIMES -> (11);
            case DIV -> (11);
            case IDIV -> (11);
            case MOD -> (11);
            case UNION -> (13);
            case INTERSECT -> (14);
            case EXCEPT -> (14);
            case INSTANCE_OF -> (15);
            case TREAT_AS -> (16);
            case CASTABLE_AS -> (17);
            case CAST_AS -> (18);
            case THIN_ARROW -> (19);
            case FAT_ARROW -> (20);
            case MAPPING_ARROW -> (21);
            default -> -1;

            // remainder commented out because not used in precedence parsing (but perhaps they could be)
//            case BANG:
//                return 20;
//            case SLASH:
//                return 21;
//            case SLASH_SLASH:
//                return 22;
//            case QMARK:
//                return 23;

        };
    }

    /**
     * Return the inverse of a relational operator, so that "a op b" can be
     * rewritten as "b inverse(op) a"
     *
     * @param operator the operator whose inverse is required
     * @return the inverse operator
     */

    public static OperatorSymbol inverse(OperatorSymbol operator) {
        switch (operator) {
            case LT:
                return OperatorSymbol.GT;
            case LE:
                return OperatorSymbol.GE;
            case GT:
                return OperatorSymbol.LT;
            case GE:
                return OperatorSymbol.LE;
            case FLT:
                return OperatorSymbol.FGT;
            case FLE:
                return OperatorSymbol.FGE;
            case FGT:
                return OperatorSymbol.FLT;
            case FGE:
                return OperatorSymbol.FLE;
            default:
                return operator;
        }
    }

    /**
     * Return the negation of a relational operator, so that "a op b" can be
     * rewritten as not(b op' a)
     *
     * @param operator the operator to be negated
     * @return the negated operator
     */

    public static OperatorSymbol negate(OperatorSymbol operator) {
        switch (operator) {
            case FEQ:
                return OperatorSymbol.FNE;
            case FNE:
                return OperatorSymbol.FEQ;
            case FLT:
                return OperatorSymbol.FGE;
            case FLE:
                return OperatorSymbol.FGT;
            case FGT:
                return OperatorSymbol.FLE;
            case FGE:
                return OperatorSymbol.FLT;
            default:
                throw new IllegalArgumentException("Invalid operator for negate()");
        }
    }

    public static boolean isOrderedOperator(OperatorSymbol operator) {
        return operator != OperatorSymbol.FEQ && operator != OperatorSymbol.FNE;
    }
}

