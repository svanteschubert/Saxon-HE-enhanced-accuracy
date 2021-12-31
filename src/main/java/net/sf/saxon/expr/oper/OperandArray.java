////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.oper;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.OperandRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Defines an operand set comprising an array of operands numbered zero to N. All operands must be present.
 * Typically (but not exclusively) used for the arguments of a function call. The operands may all have the
 * same operand roles, or have different operand roles.
 */

public class OperandArray implements Iterable<Operand> {

    private Operand[] operandArray;

    // operand role is currently always NAVIGATE, but this could change in the future

    public OperandArray(Expression parent, Expression[] args) {
        this.operandArray = new Operand[args.length];
        for (int i=0; i<args.length; i++) {
            operandArray[i] = new Operand(parent, args[i], OperandRole.NAVIGATE);
        }
    }

    public OperandArray(Expression parent, Expression[] args, OperandRole[] roles) {
        this.operandArray = new Operand[args.length];
        for (int i=0; i<args.length; i++) {
            operandArray[i] = new Operand(parent, args[i], roles[i]);
        }
    }

    public OperandArray(Expression parent, Expression[] args, OperandRole role) {
        this.operandArray = new Operand[args.length];
        for (int i=0; i<args.length; i++) {
            operandArray[i] = new Operand(parent, args[i], role);
        }
    }

    private OperandArray(Operand[] operands) {
        this.operandArray = operands;
    }

    /**
     * Returns an iterator over elements of type {@code T}.
     *
     * @return an Iterator.
     */
    
    @Override
    public Iterator<Operand> iterator() {
        return Arrays.asList(operandArray).iterator();
    }

    public Operand[] copy() {
        return Arrays.copyOf(operandArray, operandArray.length);
    }

    /**
     * Get the operand roles
     * @return the roles of the operands as an array, one per operand
     */

    public OperandRole[] getRoles() {
        OperandRole[] or = new OperandRole[operandArray.length];
        for (int i = 0; i < or.length; i++) {
            or[i] = operandArray[i].getOperandRole();
        }
        return or;
    }

    /**
     * Get the operand whose identifying number is n.
     * @param n the identifier of the operand (counting from zero)
     * @return the operand, or null in the case of an optional operand that is absent in the case
     * of this particular expression
     * @throws IllegalArgumentException if there cannot be an operand at this position
     */

    public Operand getOperand(int n) {
        try {
            return operandArray[n];
        } catch (ArrayIndexOutOfBoundsException a) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Get the child expression associated with the operand whose identifying number is n.
     * @param n the identifier of the operand
     * @return the expression associated with the operand, or null in the case of an optional operand that is absent in the case
     * of this particular expression
     * @throws IllegalArgumentException if there cannot be an operand at this position
     */

    public Expression getOperandExpression(int n) {
        try {
            return operandArray[n].getChildExpression();
        } catch (ArrayIndexOutOfBoundsException a) {
            throw new IllegalArgumentException(a);
        }
    }

    /**
     * Return a collection containing all the operands. Generally there is a significance to the order
     * of operands, which usually reflects the order in the raw textual expression.
     * @return the collection of operands
     */

    public Iterable<Operand> operands() {
        return Arrays.asList(operandArray);
    }

    public Iterable<Expression> operandExpressions() {
        List<Expression> list = new ArrayList<Expression>(operandArray.length);
        for (Operand o : operands()) {
            list.add(o.getChildExpression());
        }
        return list;
    }

    /**
     * Set the value of the operand with integer n.
     * The method should implement a fast path for the case where the operand has
     * not actually been changed.
     *
     * @param n identifies the expression to be set/replaced
     * @param child the new subexpression
     * @throws IllegalArgumentException if the value of n identifies no operand
     */

    public void setOperand(int n, Expression child) {
        try {
            if (operandArray[n].getChildExpression() != child) {
                operandArray[n].setChildExpression(child);
            }
        } catch (ArrayIndexOutOfBoundsException a) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Get the number of operands in the operand array
     * @return  the number of operands
     */

    public int getNumberOfOperands() {
        return operandArray.length;
    }

    /**
     * Utility method (doesn't really belong here) to test if every element of an array satisfies
     * some condition
     * @param args the array
     * @param condition the condition
     * @param <T> the type of the items in the array
     * @return true if the condition is true for every item in the array
     */

    public static <T> boolean every(T[] args, Predicate<T> condition) {
        for (T arg : args) {
            if (!condition.test(arg)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Utility method (doesn't really belong here) to test if some element of an array satisfies
     * some condition
     *
     * @param args      the array
     * @param condition the condition
     * @param <T>       the type of the items in the array
     * @return true if the condition is true for every item in the array
     */

    public static <T> boolean some(T[] args, Predicate<T> condition) {
        for (T arg : args) {
            if (condition.test(arg)) {
                return true;
            }
        }
        return false;
    }
}

