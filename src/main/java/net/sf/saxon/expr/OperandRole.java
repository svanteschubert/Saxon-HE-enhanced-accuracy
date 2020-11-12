////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.expr.flwor.TupleExpression;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.PlainType;
import net.sf.saxon.value.SequenceType;

import java.util.function.Predicate;

/**
 * Defines the role of a child expression relative to its parent expression. The properties
 * of an operand role depend only on the kind of expression and not on the actual arguments
 * supplied to a specific instance of the kind of operand.
 */
public class OperandRole {

    // Bit settings in properties field

    public final static int SETS_NEW_FOCUS = 1;
    public final static int USES_NEW_FOCUS = 2;
    public final static int HIGHER_ORDER = 4;
    public final static int IN_CHOICE_GROUP = 8;
    public final static int CONSTRAINED_CLASS = 16;
    public final static int SINGLETON = 32; // set only where HIGHER_ORDER would otherwise imply repeated evaluation
    public final static int HAS_SPECIAL_FOCUS_RULES = 64;

    public final static OperandRole SAME_FOCUS_ACTION =
            new OperandRole(0, OperandUsage.TRANSMISSION, SequenceType.ANY_SEQUENCE);
    public final static OperandRole FOCUS_CONTROLLING_SELECT =
            new OperandRole(OperandRole.SETS_NEW_FOCUS, OperandUsage.INSPECTION, SequenceType.ANY_SEQUENCE);
    public final static OperandRole FOCUS_CONTROLLED_ACTION =
            new OperandRole(OperandRole.USES_NEW_FOCUS | OperandRole.HIGHER_ORDER, OperandUsage.TRANSMISSION, SequenceType.ANY_SEQUENCE);
    public final static OperandRole INSPECT =
            new OperandRole(0, OperandUsage.INSPECTION,  SequenceType.ANY_SEQUENCE);
    public final static OperandRole ABSORB =
            new OperandRole(0, OperandUsage.ABSORPTION, SequenceType.ANY_SEQUENCE);
    public final static OperandRole REPEAT_INSPECT =
            new OperandRole(OperandRole.HIGHER_ORDER, OperandUsage.INSPECTION, SequenceType.ANY_SEQUENCE);
    public final static OperandRole NAVIGATE =
            new OperandRole(0, OperandUsage.NAVIGATION,  SequenceType.ANY_SEQUENCE);
    public final static OperandRole REPEAT_NAVIGATE =
            new OperandRole(OperandRole.HIGHER_ORDER, OperandUsage.NAVIGATION, SequenceType.ANY_SEQUENCE);
    public final static OperandRole FLWOR_TUPLE_CONSTRAINED =
            new OperandRole(OperandRole.HIGHER_ORDER | OperandRole.CONSTRAINED_CLASS,
                            OperandUsage.NAVIGATION,
                            SequenceType.ANY_SEQUENCE,
                            expr -> expr instanceof TupleExpression);
    public final static OperandRole SINGLE_ATOMIC =
            new OperandRole(0, OperandUsage.ABSORPTION,  SequenceType.SINGLE_ATOMIC);
    public final static OperandRole ATOMIC_SEQUENCE =
            new OperandRole(0, OperandUsage.ABSORPTION,  SequenceType.ATOMIC_SEQUENCE);
    public final static OperandRole NEW_FOCUS_ATOMIC =
            new OperandRole(OperandRole.USES_NEW_FOCUS | OperandRole.HIGHER_ORDER, OperandUsage.ABSORPTION, SequenceType.ATOMIC_SEQUENCE);
    public final static OperandRole PATTERN =
            new OperandRole(OperandRole.USES_NEW_FOCUS | OperandRole.HIGHER_ORDER | OperandRole.CONSTRAINED_CLASS,
                            OperandUsage.ABSORPTION,
                            SequenceType.ATOMIC_SEQUENCE,
                            expr -> expr instanceof Pattern)
    ;

    int properties;
    private OperandUsage usage;
    private SequenceType requiredType = SequenceType.ANY_SEQUENCE;
    private Predicate<Expression> constraint;

    public OperandRole(int properties, OperandUsage usage) {
        this.properties = properties;
        this.usage = usage;
    }

    public OperandRole(int properties, OperandUsage usage, SequenceType requiredType) {
        this.properties = properties;
        this.usage = usage;
        this.requiredType = requiredType;
    }

    public OperandRole(int properties, OperandUsage usage, SequenceType requiredType, Predicate<Expression> constraint) {
        this.properties = properties;
        this.usage = usage;
        this.requiredType = requiredType;
        this.constraint = constraint;
    }

    /**
     * Ask whether the child expression sets the focus for evaluation of other child expressions
     * @return true if the child expression is evaluated with the same focus as its parent expression
     */

    public boolean setsNewFocus() {
        return (properties & SETS_NEW_FOCUS) != 0;
    }

    /**
     * Ask whether the child expression is evaluated with the same focus as its parent expression
     * @return true if the child expression is evaluated with the same focus as its parent expression
     */

    public boolean hasSameFocus() {
        return (properties & (USES_NEW_FOCUS | HAS_SPECIAL_FOCUS_RULES)) == 0;
    }

    public boolean hasSpecialFocusRules() {
        return (properties & HAS_SPECIAL_FOCUS_RULES) != 0;
    }

    /**
     * Ask whether the operand is a higher-order operand
     *
     * @return true if the operand is higher-order
     */

    public boolean isHigherOrder() {
        return (properties & HIGHER_ORDER) != 0;
    }

    /**
     * Ask whether the operand is a evaluated repeatedly during a single evaluation of the
     * parent expression
     * @return true if the operand is evaluated repeatedly
     */

    public boolean isEvaluatedRepeatedly() {
        return ((properties & HIGHER_ORDER) != 0) && ((properties & SINGLETON) == 0);
    }

    /**
     * Ask whether the operand is constrained to be of a particular class (preventing
     * substitution of a variable binding)
     * @return true if the operand expression is constrained to be of a particular class
     */

     public boolean isConstrainedClass() { return (properties & CONSTRAINED_CLASS) != 0; }

    /**
     * Set a constraint on the expression that can be associated with this operand type
     */

    public void setConstraint(Predicate<Expression> constraint) {
        this.constraint = constraint;
    }

    /**
     * Get any constraint on the expression that can be associated with this operand type
     * @return any constraint that has been registered, or null
     */

    public Predicate<Expression> getConstraint() {
        return constraint;
    }

    /**
     * Get the usage of the operand
     * @return the usage
     */

    public OperandUsage getUsage() {
        return usage;
    }

    /**
     * Get the required type of the operand
     * @return the required type
     */

    public SequenceType getRequiredType() {
        return requiredType;
    }

    /**
     * Ask whether the operand is a member of a choice group
     * @return true if the operand is in a choice group
     */

    public boolean isInChoiceGroup() {
        return (properties & IN_CHOICE_GROUP) != 0;
    }

    /**
     * Static method to get the type-determined usage for a particular ItemType
     * @param type the required type
     * @return the type-determined operand usage
     */

    public static OperandUsage getTypeDeterminedUsage(ItemType type) {
        if (type instanceof FunctionItemType) {
            return OperandUsage.INSPECTION;
        } else if (type instanceof PlainType) {
            return OperandUsage.ABSORPTION;
        } else {
            return OperandUsage.NAVIGATION;
        }
    }

    public OperandRole modifyProperty(int property, boolean on) {
        int newProp = on ? (properties | property) : (properties & ~property);
        return new OperandRole(newProp, usage, requiredType);
    }

    public int getProperties() {
        return properties;
    }

}

