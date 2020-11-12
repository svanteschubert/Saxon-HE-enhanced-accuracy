////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.One;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.NumericValue;


/**
 * Abstract class providing functionality common to functions math:sin(), math:cos(), math:sqrt() etc;
 * contains the concrete implementations of these functions as inner subclasses
 */
public class MathFunctionSet extends BuiltInFunctionSet {

    private static MathFunctionSet THE_INSTANCE = new MathFunctionSet();

    public static MathFunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private MathFunctionSet() {
        init();
    }

    private void reg1(String name, Class<? extends SystemFunction> implementation) {
        register(name, 1, implementation, BuiltInAtomicType.DOUBLE, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DOUBLE, OPT, EMPTY);
    }


    private void init() {

        // Arity 0 functions

        register("pi", 0, PiFn.class, BuiltInAtomicType.DOUBLE, ONE, 0);

        // Arity 1 functions

        reg1("sin", SinFn.class);
        reg1("cos", CosFn.class);
        reg1("tan", TanFn.class);
        reg1("asin", AsinFn.class);
        reg1("acos", AcosFn.class);
        reg1("atan", AtanFn.class);
        reg1("sqrt", SqrtFn.class);
        reg1("log", LogFn.class);
        reg1("log10", Log10Fn.class);
        reg1("exp", ExpFn.class);
        reg1("exp10", Exp10Fn.class);

        // Arity 2 functions

        register("pow", 2, PowFn.class, BuiltInAtomicType.DOUBLE, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DOUBLE, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.DOUBLE, ONE, null);

        register("atan2", 2, Atan2Fn.class, BuiltInAtomicType.DOUBLE, ONE, 0)
                .arg(0, BuiltInAtomicType.DOUBLE, ONE, null)
                .arg(1, BuiltInAtomicType.DOUBLE, ONE, null);

    }

    @Override
    public String getNamespace() {
        return NamespaceConstant.MATH;
    }

    @Override
    public String getConventionalPrefix() {
        return "math";
    }

    /**
     * Implement math:pi
     */

    public static class PiFn extends SystemFunction {
        @Override
        public Expression makeFunctionCall(Expression... arguments) {
            return Literal.makeLiteral(new DoubleValue(Math.PI));
        }

        @Override
        public DoubleValue call(XPathContext context, Sequence[] arguments) throws XPathException {
            return new DoubleValue(Math.PI);
        }
    }

    /**
     * Generic superclass for all the arity-1 trig functions
     */

    private static abstract class TrigFn1 extends SystemFunction {

        protected abstract double compute(double input);

        @Override
        public ZeroOrOne call(XPathContext context, Sequence[] args) throws XPathException {
            DoubleValue in = (DoubleValue) args[0].head();
            if (in == null) {
                return ZeroOrOne.empty();
            } else {
                return One.dbl(compute(in.getDoubleValue()));
            }
        }
    }

    /**
     * Implement math:sin
     */

    public static class SinFn extends TrigFn1 {

        @Override
        protected double compute(double input) {
            return Math.sin(input);
        }
    }

    /**
     * Implement math:cos
     */

    public static class CosFn extends TrigFn1 {

        @Override
        protected double compute(double input) {
            return Math.cos(input);
        }
    }

    /**
     * Implement math:tan
     */

    public static class TanFn extends TrigFn1 {

        @Override
        protected double compute(double input) {
            return Math.tan(input);
        }
    }

    /**
     * Implement math:asin
     */

    public static class AsinFn extends TrigFn1 {

        @Override
        protected double compute(double input) {
            return Math.asin(input);
        }
    }

    /**
     * Implement math:acos
     */

    public static class AcosFn extends TrigFn1 {

        @Override
        protected double compute(double input) {
            return Math.acos(input);
        }
    }

    /**
     * Implement math:atan
     */

    public static class AtanFn extends TrigFn1 {

        @Override
        protected double compute(double input) {
            return Math.atan(input);
        }
    }

    /**
     * Implement math:sqrt
     */

    public static class SqrtFn extends TrigFn1 {

        @Override
        protected double compute(double input) {
            return Math.sqrt(input);
        }
    }

    /**
     * Implement math:log
     */

    public static class LogFn extends TrigFn1 {

        @Override
        protected double compute(double input) {
            return Math.log(input);
        }
    }

    /**
     * Implement math:log10
     */

    public static class Log10Fn extends TrigFn1 {

        @Override
        protected double compute(double input) {
            return Math.log10(input);
        }
    }

    /**
     * Implement math:exp
     */

    public static class ExpFn extends TrigFn1 {

        @Override
        protected double compute(double input) {
            return Math.exp(input);
        }
    }

    /**
     * Implement math:exp10
     */

    public static class Exp10Fn extends TrigFn1 {

        @Override
        protected double compute(double input) {
            return Math.pow(10, input);
        }
    }

    /**
     * Implement math:pow
     */

    public static class PowFn extends SystemFunction {
        /**
         * Invoke the function
         *
         * @param context the XPath dynamic evaluation context
         * @param args    the actual arguments to be supplied
         * @return the result of invoking the function
         * @throws XPathException if a dynamic error occurs within the function
         */
        @Override
        public ZeroOrOne call(XPathContext context, Sequence[] args) throws XPathException {
            DoubleValue x = (DoubleValue) args[0].head();
            DoubleValue result;
            if (x == null) {
                result = null;
            } else {
                double dx = x.getDoubleValue();
                if (dx == 1) {
                    result = x;
                } else {
                    NumericValue y = (NumericValue) args[1].head();
                    assert y != null;
                    double dy = y.getDoubleValue();
                    if (dx == -1 && Double.isInfinite(dy)) {
                        result = new DoubleValue(1.0e0);
                    } else {
                        result = new DoubleValue(Math.pow(dx, dy));
                    }
                }
            }
            return new ZeroOrOne(result);
        }
    }

    /**
     * Implement math:atan2
     */

    public static class Atan2Fn extends SystemFunction {
        @Override
        public DoubleValue call(XPathContext context, Sequence[] arguments) throws XPathException {
            DoubleValue y = (DoubleValue) arguments[0].head();
            assert y != null;
            DoubleValue x = (DoubleValue) arguments[1].head();
            assert x != null;
            double result = Math.atan2(y.getDoubleValue(), x.getDoubleValue());
            return new DoubleValue(result);
        }
    }


}

// Copyright (c) 2018-2020 Saxonica Limited
