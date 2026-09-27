////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.*;
import net.sf.saxon.s9api.OccurrenceIndicator;

/**
 * This class contains static methods to manipulate the cardinality
 * property of a type.
 * Cardinality of expressions is denoted by one of the values ONE_OR_MORE, ZERO_OR_MORE,
 * ZERO_OR_ONE, EXACTLY_ONE, or EMPTY. These are combinations of the three bit-significant
 * values ALLOWS_ZERO, ALLOWS_ONE, and ALLOWS_MANY.
 */

public final class Cardinality {

    /**
     * Private constructor: no instances allowed
     */

    private Cardinality() {
    }

    /**
     * Determine whether multiple occurrences are allowed
     *
     * @param cardinality the cardinality of a sequence
     * @return true if the cardinality allows the sequence to contain more than one item
     */

    public static boolean allowsMany(int cardinality) {
        return (cardinality & StaticProperty.ALLOWS_MANY) != 0;
    }

    /**
     * Determine whether multiple occurrences are not only allowed, but likely.
     * This returns false for an expression that is the atomization of a singleton
     * node, since in that case it's quite unusual for the typed value to be a
     * non-singleton sequence.
     *
     * @param expression an expression
     * @return true if multiple occurrences are not only allowed, but likely. Return
     *         false if multiple occurrences are unlikely, even though they might be allowed.
     *         This is typically the case for the atomized sequence that is obtained by atomizing
     *         a singleton node.
     */

    public static boolean expectsMany(Expression expression) {
        if (expression instanceof VariableReference) {
            Binding b = ((VariableReference) expression).getBinding();
            if (b instanceof LetExpression) {
                return expectsMany(((LetExpression) b).getSequence());
            }
        }
        if (expression instanceof Atomizer) {
            return expectsMany(((Atomizer) expression).getBaseExpression());
        }
        if (expression instanceof FilterExpression) {
            return expectsMany(((FilterExpression) expression).getSelectExpression());
        }
        return allowsMany(expression.getCardinality());
    }

    /**
     * Determine whether empty sequence is allowed
     *
     * @param cardinality the cardinality of a sequence
     * @return true if the cardinality allows the sequence to be empty
     */

    public static boolean allowsZero(int cardinality) {
        return (cardinality & StaticProperty.ALLOWS_ZERO) != 0;
    }

    /**
     * Determine whether a given cardinality allows a specific number of items
     *
     * @param cardinality the cardinality
     * @param count       the number of items in a value
     * @return true if the cardinality allows a value with this number of items
     */

    public static boolean allows(int cardinality, int count) {
        if (count == 0) {
            return allowsZero(cardinality);
        } else if (count == 1) {
            return (cardinality & StaticProperty.ALLOWS_ONE) != 0;
        } else {
            return allowsMany(cardinality);
        }
    }

    /**
     * Form the union of two cardinalities. The cardinality of the expression "if (c) then e1 else e2"
     * is the union of the cardinalities of e1 and e2.
     *
     * @param c1 a cardinality
     * @param c2 another cardinality
     * @return the cardinality that allows both c1 and c2
     */

    public static int union(int c1, int c2) {
        int r = c1 | c2;
        // eliminate disallowed options
        if (r == (StaticProperty.ALLOWS_MANY | StaticProperty.ALLOWS_ZERO)) {
            r = StaticProperty.ALLOWS_ZERO_OR_MORE;
        }
        return r;
    }

    /**
     * Add two cardinalities
     *
     * @param c1 the first cardinality
     * @param c2 the second cardinality
     * @return the cardinality of a sequence formed by concatenating the sequences whose cardinalities
     *         are c1 and c2
     */

    public static int sum(int c1, int c2) {
        int mini = min(c1) + min(c2);
        int maxi = max(c1) + max(c2);
        return fromMinAndMax(mini, maxi);
    }

    /**
     * Get the minimum number of occurrences permitted by a given cardinality
     * @param cardinality the cardinality code
     * @return the minimum size of a sequence that satisfies this cardinality
     */
    static int min(int cardinality) {
        if (allowsZero(cardinality)) {
            return 0;
        } else if (cardinality == StaticProperty.ALLOWS_MANY) {
            return 2;
        } else {
            return 1;
        }
    }

    /**
     * Get the maximum number of occurrences permitted by a given cardinality
     *
     * @param cardinality the cardinality code
     * @return the maximum size of a sequence that satisfies this cardinality,
     * where 2 represents infinity.
     */
    static int max(int cardinality) {
        if (allowsMany(cardinality)) {
            return 2;
        } else if (cardinality == StaticProperty.ALLOWS_ZERO) {
            return 0;
        } else {
            return 1;
        }
    }

    public static int fromMinAndMax(int min, int max) {
        boolean zero = min == 0;
        boolean one = min <= 1 || max <= 1;
        boolean many = max > 1;
        return (zero ? StaticProperty.ALLOWS_ZERO : 0) +
                (one ? StaticProperty.ALLOWS_ONE : 0) +
                (many ? StaticProperty.ALLOWS_MANY : 0);
    }

    /**
     * Test if one cardinality subsumes another. Cardinality c1 subsumes c2 if every option permitted
     * by c2 is also permitted by c1.
     *
     * @param c1 a cardinality
     * @param c2 another cardinality
     * @return true if if every option permitted
     *         by c2 is also permitted by c1.
     */

    public static boolean subsumes(int c1, int c2) {
        return (c1 | c2) == c1;
    }


    /**
     * Multiply two cardinalities
     *
     * @param c1 the first cardinality
     * @param c2 the second cardinality
     * @return the product of the cardinalities, that is, the cardinality of the sequence
     *         "for $x in S1 return S2", where c1 is the cardinality of S1 and c2 is the cardinality of S2
     */

    public static int multiply(int c1, int c2) {
        if (c1 == StaticProperty.EMPTY || c2 == StaticProperty.EMPTY) {
            return StaticProperty.EMPTY;
        }
        if (c2 == StaticProperty.EXACTLY_ONE) {
            return c1;
        }
        if (c1 == StaticProperty.EXACTLY_ONE) {
            return c2;
        }
        if (c1 == StaticProperty.ALLOWS_ZERO_OR_ONE && c2 == StaticProperty.ALLOWS_ZERO_OR_ONE) {
            return StaticProperty.ALLOWS_ZERO_OR_ONE;
        }
        if (c1 == StaticProperty.ALLOWS_ONE_OR_MORE && c2 == StaticProperty.ALLOWS_ONE_OR_MORE) {
            return StaticProperty.ALLOWS_ONE_OR_MORE;
        }
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Display the cardinality as a string
     *
     * @param cardinality the cardinality value to be displayed
     * @return the representation as a string, for example "zero or one", "zero or more"
     */

    public static String describe(int cardinality) {
        return switch (cardinality) {
            case StaticProperty.ALLOWS_ZERO_OR_ONE -> "zero or one";
            case StaticProperty.EXACTLY_ONE -> "exactly one";
            case StaticProperty.ALLOWS_ZERO_OR_MORE -> "zero or more";
            case StaticProperty.ALLOWS_ONE_OR_MORE -> "one or more";
            case StaticProperty.EMPTY -> "exactly zero";
            case StaticProperty.ALLOWS_MANY -> "more than one";
            default -> "code " + cardinality;
        };
    }

    /**
     * Get the occurrence indicator representing the cardinality
     *
     * @param cardinality the cardinality value
     * @return the occurrence indicator, for example "*", "+", "?", "".
     */

    /*@NotNull*/
    public static String getOccurrenceIndicator(int cardinality) {
        return switch (cardinality) {
            case StaticProperty.ALLOWS_ZERO_OR_ONE -> "?";
            case StaticProperty.EXACTLY_ONE -> "";
            case StaticProperty.ALLOWS_ZERO_OR_MORE -> "*";
            case StaticProperty.ALLOWS_ONE_OR_MORE -> "+";
            case StaticProperty.ALLOWS_MANY -> "+";
            case StaticProperty.EMPTY -> "0";
            default -> "*";
            // Covers no bits set (which shouldn't arise) and zero_or_many (which can arise, but is too specific for our purposes)
        };
    }

    public static int fromOccurrenceIndicator(String indicator) {
        return switch (indicator) {
            case "?" -> StaticProperty.ALLOWS_ZERO_OR_ONE;
            case "*" -> StaticProperty.ALLOWS_ZERO_OR_MORE;
            case "+" -> StaticProperty.ALLOWS_ONE_OR_MORE;
            case "1" -> StaticProperty.ALLOWS_ONE;
            case "" -> StaticProperty.ALLOWS_ONE;  // TODO: obsolescent, delete this
            default -> StaticProperty.ALLOWS_ZERO;
        };
    }

    /**
     * Get the {@link OccurrenceIndicator} value corresponding to an internal cardinality property
     *
     * @param cardinality the internal cardinality property, for example {@link StaticProperty#ALLOWS_ONE_OR_MORE}.
     * @return the corresponding {@link OccurrenceIndicator}
     */

    public static OccurrenceIndicator getOccurrenceIndicatorForCardinality(int cardinality) {
        return switch (cardinality) {
            case StaticProperty.EMPTY -> OccurrenceIndicator.ZERO;
            case StaticProperty.ALLOWS_ZERO_OR_ONE -> OccurrenceIndicator.ZERO_OR_ONE;
            case StaticProperty.ALLOWS_ZERO_OR_MORE -> OccurrenceIndicator.ZERO_OR_MORE;
            case StaticProperty.ALLOWS_ONE -> OccurrenceIndicator.ONE;
            case StaticProperty.ALLOWS_ONE_OR_MORE -> OccurrenceIndicator.ONE_OR_MORE;
            default -> OccurrenceIndicator.ZERO_OR_MORE;
        };
    }

    /**
     * Ask whether a given occurrence indicator allows a value of a given size
     * @param indicator the given occurrence indicator
     * @param size the given size (number of items)
     * @return true if the occurrence indicator matches a sequence of the given length
     */
    public static boolean occurrenceIndicatorAllows(OccurrenceIndicator indicator, int size) {
        return switch (indicator) {
            case ZERO -> size == 0;
            case ZERO_OR_ONE -> size <= 1;
            case ZERO_OR_MORE -> true;
            case ONE -> size == 1;
            case ONE_OR_MORE -> size > 0;
            default -> false;
        };
    }

    /**
     * Get the internal StaticProperty value corresponding to an {@link OccurrenceIndicator}
     *
     * @param indicator {@link OccurrenceIndicator}
     * @return the corresponding internal cardinality property, for example {@link StaticProperty#ALLOWS_ONE_OR_MORE}.
     *
     */

    public static int staticPropertyFromOccurrenceIndicator(OccurrenceIndicator indicator) {
        return switch (indicator) {
            case ZERO -> StaticProperty.EMPTY;
            case ZERO_OR_ONE -> StaticProperty.ALLOWS_ZERO_OR_ONE;
            case ZERO_OR_MORE -> StaticProperty.ALLOWS_ZERO_OR_MORE;
            case ONE -> StaticProperty.ALLOWS_ONE;
            case ONE_OR_MORE -> StaticProperty.ALLOWS_ONE_OR_MORE;
            default -> StaticProperty.EMPTY;
        };
    }


}

