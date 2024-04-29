////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.NumericValue;

import java.util.function.Function;


/**
 * Abstract class providing functionality common to functions math:sin(), math:cos(), math:sqrt() etc;
 * contains the concrete implementations of these functions as inner subclasses
 */
public class MathFunctionSet extends BuiltInFunctionSet {

    private static final MathFunctionSet THE_INSTANCE = new MathFunctionSet();

    public static MathFunctionSet getInstance() {
        return THE_INSTANCE;
    }

    private MathFunctionSet() {
        init();
    }

    private void reg1(String name, Function<Double, Double> method) {
        register(name, 1, e -> e.populate(() -> new TrigFn1(method), BuiltInAtomicType.DOUBLE, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DOUBLE, OPT, EMPTY));
    }


    private void init() {

        // Arity 0 functions

        register("pi", 0, e -> e.populate(PiFn::new, BuiltInAtomicType.DOUBLE, ONE, 0));

        // Arity 1 functions

        reg1("sin", Math::sin);
        reg1("cos", Math::cos);
        reg1("tan", Math::tan);
        reg1("asin", Math::asin);
        reg1("acos", Math::acos);
        reg1("atan", Math::atan);
        reg1("sqrt", Math::sqrt);
        reg1("log", Math::log);
        reg1("log10", Math::log10);
        reg1("exp", Math::exp);
        reg1("exp10", input -> Math.pow(10, input));

        // Arity 2 functions

        register("pow", 2, e -> e.populate(PowFn::new, BuiltInAtomicType.DOUBLE, OPT, CARD0)
                .arg(0, BuiltInAtomicType.DOUBLE, OPT, EMPTY)
                .arg(1, BuiltInAtomicType.DOUBLE, ONE, null));

        register("atan2", 2, e -> e.populate(Atan2Fn::new, BuiltInAtomicType.DOUBLE, ONE, 0)
                .arg(0, BuiltInAtomicType.DOUBLE, ONE, null)
                .arg(1, BuiltInAtomicType.DOUBLE, ONE, null));

    }

    @Override
    public NamespaceUri getNamespace() {
        return NamespaceUri.MATH;
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

    public static class TrigFn1 extends SystemFunction {

        private final Function<Double, Double> method;

        public TrigFn1(Function<Double, Double> method) {
            this.method = method;
        }

        @Override
        public GroundedValue call(XPathContext context, Sequence[] args) throws XPathException {
            DoubleValue in = (DoubleValue) args[0].head();
            if (in == null) {
                return EmptySequence.getInstance();
            } else {
                return new DoubleValue(method.apply(in.getDoubleValue()));
            }
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
        public GroundedValue call(XPathContext context, Sequence[] args) throws XPathException {
            DoubleValue x = (DoubleValue) args[0].head();
            DoubleValue result;
            if (x == null) {
                return EmptySequence.getInstance();
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
                return result;
            }
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

// Copyright (c) 2018-2023 Saxonica Limited
