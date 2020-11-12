////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.Err;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.SequenceType;

import java.util.Optional;

/**
 * A RoleDiagnostic (formerly RoleLocator) identifies the role in which an expression is used,
 * for example as the third argument of the concat() function. This information is stored in an
 * ItemChecker or CardinalityChecker so that good diagnostics can be
 * achieved when run-time type errors are detected.
 */
public class RoleDiagnostic {

    private int kind;
    private String operation;
    private int operand;
    private String errorCode = "XPTY0004";  // default error code for type errors

    public static final int FUNCTION = 0;
    public static final int BINARY_EXPR = 1;
    public static final int TYPE_OP = 2;
    public static final int VARIABLE = 3;
    public static final int INSTRUCTION = 4;
    public static final int FUNCTION_RESULT = 5;
    public static final int ORDER_BY = 6;
    public static final int TEMPLATE_RESULT = 7;
    public static final int PARAM = 8;
    public static final int UNARY_EXPR = 9;
    public static final int UPDATING_EXPR = 10;
    //public static final int GROUPING_KEY = 11;  // 9.8 and earlier
    public static final int EVALUATE_RESULT = 12;
    public static final int CONTEXT_ITEM = 13;
    public static final int AXIS_STEP = 14;
    public static final int OPTION = 15;
    public static final int CHARACTER_MAP_EXPANSION = 16;
    //public static final int DOCUMENT_ORDER = 17;  // 9.8 and earlier
    //public static final int MAP_CONSTRUCTOR = 18;  // 9.8 and earlier
    public static final int MATCH_PATTERN = 19;
    public static final int MISC = 20;


    /**
     * Create information about the role of a subexpression within its parent expression
     *
     * @param kind      the kind of parent expression, e.g. a function call or a variable reference
     * @param operation the name of the object in the parent expression, e.g. a function name or
     *                  instruction name. A QName is provided in display form, prefix:local.
     *                  For a string, the special format element/attribute is recognized, for example xsl:for-each/select,
     *                  to identify the role of an XPath expression in a stylesheet.
     * @param operand   Ordinal position of this subexpression, e.g. the position of an argument in
     *                  a function call
     */



    public RoleDiagnostic(int kind, String operation, int operand) {
        this.kind = kind;
        this.operation = operation;
        this.operand = operand;
    }

    /**
     * Set the error code to be produced if a type error is detected
     *
     * @param code The error code
     */

    public void setErrorCode(/*@Nullable*/ String code) {
        if (code != null) {
            this.errorCode = code;
        }
    }

    /**
     * Get the error code to be produced if a type error is detected
     *
     * @return code The error code
     */

    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Ask whether the error code represents a type error
     *
     * @return true if the error is treated as a type error
     */

    public boolean isTypeError() {
        return !errorCode.startsWith("FORG") && !errorCode.equals("XPDY0050");
    }

    /**
     * Construct and return the error message indicating a type error
     *
     * @return the constructed error message
     */
    public String getMessage() {
        String name = operation;

        switch (kind) {
            case FUNCTION:
                if (name.equals("saxon:call") || name.equals("saxon:apply")) {
                    if (operand == 0) {
                        return "target of the dynamic function call";
                    } else {
                        return ordinal(operand) + " argument of the dynamic function call";
                    }
                } else {
                    return ordinal(operand + 1) + " argument of " +
                            (name.isEmpty() ? "the anonymous function" : name + "()");
                }
            case BINARY_EXPR:
                return ordinal(operand + 1) + " operand of '" + name + '\'';
            case UNARY_EXPR:
                return "operand of '-'";
            case TYPE_OP:
                return "value in '" + name + "' expression";
            case VARIABLE:
                if (name.equals("saxon:context-item")) {
                    return "context item";
                } else {
                    return "value of variable $" + name;
                }
            case INSTRUCTION:
                int slash = name.indexOf('/');
                String attributeName = "";
                if (slash >= 0) {
                    attributeName = name.substring(slash + 1);
                    name = name.substring(0, slash);
                }
                return "@" + attributeName + " attribute of " + (name.equals("LRE") ? "a literal result element" : name);
            case FUNCTION_RESULT:
                if (name.isEmpty()) {
                    return "result of the anonymous function";
                } else {
                    return "result of a call to " + name;
                }
            case TEMPLATE_RESULT:
                return "result of template " + name;
            case ORDER_BY:
                return ordinal(operand + 1) + " sort key";
            case PARAM:
                return "value of parameter $" + name;
            case UPDATING_EXPR:
                return "value of the " + ordinal(operand + 1) + " operand of " + name + " expression";
            case EVALUATE_RESULT:
                return "result of the expression {" + name + "} evaluated by xsl:evaluate";
            case CONTEXT_ITEM:
                return "context item";
            case AXIS_STEP:
                return "context item for the " + AxisInfo.axisName[operand] + " axis";
            case OPTION:
                return "value of the " + name + " option";
            case CHARACTER_MAP_EXPANSION:
                return "substitute value for character '" + name + "' in the character map";
            case MATCH_PATTERN:
                return "match pattern";
            case MISC:
                return operation;
            default:
                return "";
        }
    }

    /**
     * Construct the part of the message giving the required item type
     *
     * @param requiredItemType the item type required by the context of a particular expression
     * @return a message of the form "Required item type of X is Y"
     */

    public String composeRequiredMessage(ItemType requiredItemType) {
        return "The required item type of the " + getMessage() +
                " is " + requiredItemType;
    }

    /**
     * Construct a full error message
     *
     * @param requiredItemType the item type required by the context of a particular expression
     * @param suppliedItemType the item type inferred by static analysis of an expression
     * @return a message of the form "Required item type of A is R; supplied value has item type S"
     */

    public String composeErrorMessage(ItemType requiredItemType, ItemType suppliedItemType) {
        return composeRequiredMessage(requiredItemType) +
                "; supplied value has item type " +
                suppliedItemType;
    }

    /**
     * Construct a full error message, containing the supplied expression (for use when
     * the error is reported statically)
     *
     * @param requiredItemType the item type required by the context of a particular expression
     * @param supplied the supplied expression
     * @return a message of the form "Required item type of A is R; supplied value has item type S"
     */

    public String composeErrorMessage(ItemType requiredItemType, Expression supplied, TypeHierarchy th) {
        if (supplied instanceof Literal) {
            String s = composeRequiredMessage(requiredItemType);
            Optional<String> more = SequenceType.makeSequenceType(requiredItemType, StaticProperty.ALLOWS_ZERO_OR_MORE)
                    .explainMismatch(((Literal)supplied).getValue(), th);
            if (more.isPresent()) {
                s = s + ". " + more.get();
            }
            return s;
        }
        return composeRequiredMessage(requiredItemType) +
                ", but the supplied expression {" + supplied.toShortString() + "} has item type " +
                supplied.getItemType();
    }


    /**
     * Construct a full error message, displaying the item in error (suitable for use when
     * a type error is reported dynamically)
     *
     * @param requiredItemType the item type required by the context of a particular expression
     * @param item the actual item in error. Must NOT be null (unlike earlier releases).
     * @return a message of the form "Required item type of A is R; supplied value has item type S"
     */

    public String composeErrorMessage(ItemType requiredItemType, Item item, TypeHierarchy th) {

        FastStringBuffer message = new FastStringBuffer(256);
        message.append(composeRequiredMessage(requiredItemType));
        message.append("; the supplied value ");
        message.cat(Err.depict(item));

        if (requiredItemType.getGenre() != item.getGenre()) {
            message.append(" is ");
            message.append(item.getGenre().getDescription());
        } else {
            message.append(" does not match. ");
            if (th != null) {
                Optional<String> more = requiredItemType.explainMismatch(item, th);
                more.ifPresent(message::append);
            }
        }
        return message.toString();
    }


    /**
     * Construct a full error message, in terms of UTypes
     *
     * @param requiredItemType the item type required by the context of a particular expression
     * @param suppliedItemType the item type inferred by static analysis of an expression
     * @return a message of the form "Required item type of A is R; supplied value has item type S"
     */

    public String composeErrorMessage(ItemType requiredItemType, UType suppliedItemType) {
        return composeRequiredMessage(requiredItemType) +
                "; supplied value has item type " +
                suppliedItemType;
    }

    /**
     * Save as a string, for use when serializing the expression tree
     * @return a string representation of the object
     */

    public String save() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C256);
        fsb.append(kind + "|");
        fsb.append(operand + "|");
        fsb.append(errorCode.equals("XPTY0004") ? "" : errorCode);
        fsb.append("|");
        fsb.append(operation);
        return fsb.toString();
    }

    /**
     * Reconstruct from a saved string
     * @param in the saved string representation of the RoleDiagnostic
     */

    public static RoleDiagnostic reconstruct(String in) {
        int v = in.indexOf('|');
        int kind = Integer.parseInt(in.substring(0, v));
        int w = in.indexOf('|', v+1);
        int operand = Integer.parseInt(in.substring(v+1, w));
        int x = in.indexOf('|', w+1);
        String errorCode = in.substring(w+1, x);
        String operation = in.substring(x+1);
        RoleDiagnostic cd = new RoleDiagnostic(kind, operation, operand);
        if (!errorCode.isEmpty()) {
            cd.setErrorCode(errorCode);
        }
        return cd;
    }

    /**
     * Get the ordinal representation of a number (used to identify which argument of a function
     * is in error)
     *
     * @param n the cardinal number
     * @return the ordinal representation
     */
    public static String ordinal(int n) {
        switch (n) {
            case 1:
                return "first";
            case 2:
                return "second";
            case 3:
                return "third";
            default:
                if (n >= 21) {
                    switch (n % 10) {
                        case 1:
                            return n + "st";
                        case 2:
                            return n + "nd";
                        case 3:
                            return n + "rd";
                    }
                }
                return n + "th";
        }
    }


}

