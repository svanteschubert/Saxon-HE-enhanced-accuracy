// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.transpile.CSharpSimpleEnum;

/**
 * A {@code Symbol} is an abstraction of a token. In some cases there is a one-to-one correspondence
 * between symbols and tokens (for example the token {@code "!"} and the symbol
 * {@link #BANG}. Often however the meaning of a token depends on context,
 * and the symbol represents the deciphered meaning of the token, given its context.
 *
 * <p>Many symbols, but not all, represent XPath operators.</p>
 */
@CSharpSimpleEnum
public enum OperatorSymbol {

    AND,
    OR,
    PLUS,
    TIMES,
    MINUS,
    DIV,
    IDIV,
    MOD,
    NEGATE,
    EQUALS, NE, LT, LE, GT, GE,
    FEQ, FNE, FLT, FLE, FGT, FGE,
    IS, IS_NOT, PRECEDES, FOLLOWS, PRECEDES_OR_IS, FOLLOWS_OR_IS,
    UNION, INTERSECT, EXCEPT,
    OTHERWISE, CONCAT, TO,
    CAST_AS, CASTABLE_AS, TREAT_AS, INSTANCE_OF,
    FAT_ARROW, MAPPING_ARROW, THIN_ARROW, METHOD_CALL,
    SLASH, SLASH_SLASH,
    BANG, LOOKUP,
    AM_FILTER,
    NOT_AN_OPERATOR;


    @Override
    public String toString() {
        return switch (this) {
            case AND -> "and";
            case OR -> "or";
            case PLUS -> "+";
            case TIMES -> "*";
            case MINUS -> "-";
            case NEGATE -> "-";
            case DIV -> "div";
            case IDIV -> "idiv";
            case MOD -> "mod";
            case EQUALS -> "=";
            case NE -> "!=";
            case LT -> "<";
            case LE -> "<=";
            case GT -> ">";
            case GE -> ">=";
            case FEQ -> "eq";
            case FNE -> "ne";
            case FLT -> "lt";
            case FLE -> "le";
            case FGT -> "gt";
            case FGE -> "ge";
            case IS -> "is";
            case IS_NOT -> "is-not";
            case PRECEDES -> "<<";
            case FOLLOWS -> ">>";
            case PRECEDES_OR_IS -> "precedes-or-is";
            case FOLLOWS_OR_IS -> "follows-or-is";
            case UNION -> "union";
            case INTERSECT -> "intersect";
            case EXCEPT -> "except";
            case OTHERWISE -> "otherwise";
            case CONCAT -> "||";
            case TO -> "to";
            case CAST_AS -> "cast as";
            case CASTABLE_AS -> "castable as";
            case INSTANCE_OF -> "instance of";
            case TREAT_AS -> "treat as";
            case FAT_ARROW -> "=>";
            case THIN_ARROW -> "->";
            case MAPPING_ARROW -> "=!>";
            case METHOD_CALL -> "?>";
            case SLASH -> "/";
            case SLASH_SLASH -> "//";
            case BANG -> "!";
            case LOOKUP -> "?";
            case AM_FILTER -> "?[]";
            default -> "<unknown op>";
        };
    }
}

