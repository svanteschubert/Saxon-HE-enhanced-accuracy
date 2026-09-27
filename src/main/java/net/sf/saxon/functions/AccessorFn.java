////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
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

/**
 * This class supports the get_X_from_Y functions defined in XPath 2.0
 */

public abstract class AccessorFn extends ScalarSystemFunction {
    
    @CSharpSimpleEnum(flags=true)
    public enum Component {
        YEAR, MONTH, DAY, HOURS, MINUTES, SECONDS, TIMEZONE,
        LOCALNAME, NAMESPACE, PREFIX, WHOLE_SECONDS
    }
    
    public abstract Component getComponentId();

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

