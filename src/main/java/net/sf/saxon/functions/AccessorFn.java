////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.elab.ItemEvaluator;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.ItemElaborator;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpSimpleEnum;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.IntegerValue;

/**
 * This class supports the get_X_from_Y functions defined in XPath 2.0
 */

public abstract class AccessorFn extends ScalarSystemFunction {

    @CSharpSimpleEnum
    public enum Component {
        YEAR, MONTH, DAY, HOURS, MINUTES, SECONDS, TIMEZONE,
        LOCALNAME, NAMESPACE, PREFIX, MICROSECONDS, NANOSECONDS, WHOLE_SECONDS, YEAR_ALLOWING_ZERO
    }

    public abstract Component getComponentId();

    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     *         unknown or not applicable.
     */
    //@Override
    @Override
    public IntegerValue[] getIntegerBounds() {
        switch (getComponentId()) {
            case YEAR:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-100000), Int64Value.makeIntegerValue(+100000)};
            case MONTH:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-11), Int64Value.makeIntegerValue(+11)};
            case DAY:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-31), Int64Value.makeIntegerValue(+31)};
            case HOURS:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-24), Int64Value.makeIntegerValue(+24)};
            case MINUTES:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-59), Int64Value.makeIntegerValue(+59)};
            case SECONDS:
                return new IntegerValue[]{Int64Value.makeIntegerValue(-59), Int64Value.makeIntegerValue(+59)};
            default:
                return null;
        }
    }

    /**
     * Evaluate the expression
     */

    @Override
    public AtomicValue evaluate(Item item, XPathContext context) throws XPathException {
        return ((AtomicValue)item).getComponent(getComponentId());
    }


    public static class YearFromDateTime extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.YEAR;
        }
    }

    public static class MonthFromDateTime extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.MONTH;
        }
    }

    public static class DayFromDateTime extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.DAY;
        }
    }

    public static class HoursFromDateTime extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.HOURS;
        }
    }

    public static class MinutesFromDateTime extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.MINUTES;
        }
    }

    public static class SecondsFromDateTime extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.SECONDS;
        }
    }

    public static class TimezoneFromDateTime extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.TIMEZONE;
        }
    }

    public static class YearFromDate extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.YEAR;
        }
    }

    public static class MonthFromDate extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.MONTH;
        }
    }

    public static class DayFromDate extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.DAY;
        }
    }

    public static class TimezoneFromDate extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.TIMEZONE;
        }
    }

    public static class HoursFromTime extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.HOURS;
        }
    }

    public static class MinutesFromTime extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.MINUTES;
        }
    }

    public static class SecondsFromTime extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.SECONDS;
        }
    }

    public static class TimezoneFromTime extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.TIMEZONE;
        }
    }

    public static class YearsFromDuration extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.YEAR;
        }
    }

    public static class MonthsFromDuration extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.MONTH;
        }
    }

    public static class DaysFromDuration extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.DAY;
        }
    }

    public static class HoursFromDuration extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.HOURS;
        }
    }

    public static class MinutesFromDuration extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.MINUTES;
        }
    }


    public static class SecondsFromDuration extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.SECONDS;
        }
    }

    public static class LocalNameFromQName extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.LOCALNAME;
        }
    }

    public static class PrefixFromQName extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.PREFIX;
        }
    }

    public static class NamespaceUriFromQName extends AccessorFn {

        @Override
        public Component getComponentId() {
            return Component.NAMESPACE;
        }
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new AccessorFnElaborator();
    }

    /**
     * Elaborator for accessor functions such as hours-from-date-Time, minutes-from-duration
     */

    public static class AccessorFnElaborator extends ItemElaborator {

        public ItemEvaluator elaborateForItem() {
            final SystemFunctionCall fnc = (SystemFunctionCall) getExpression();
            final AccessorFn fn = (AccessorFn) fnc.getTargetFunction();
            final Component component = fn.getComponentId();
            final ItemEvaluator argEval = fnc.getArg(0).makeElaborator().elaborateForItem();
            final boolean nullable = Cardinality.allowsZero(fnc.getArg((0)).getCardinality());
            return context -> {
                AtomicValue base = ((AtomicValue) argEval.eval(context));
                if (nullable && base == null) {
                    return null;
                }
                return base.getComponent(component);
            };
        }


    }
}

