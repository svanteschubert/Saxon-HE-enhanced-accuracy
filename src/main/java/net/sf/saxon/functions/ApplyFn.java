////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SystemFunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.ma.arrays.ArrayFunctionSet;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SquareArrayConstructor;
import net.sf.saxon.ma.map.MapFunctionSet;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.type.coercion.CoercionRules;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * This class implements the function fn:apply(), which is a standard function in XQuery 3.1.
 * The fn:apply function is also used internally to implement dynamic function calls.
 */

public class ApplyFn extends SystemFunction  {

    // This flag is nonNull when the fn:apply() call actually implements a dynamic function call.
    // It only affects the error code/message if the call fails
    private String dynamicFunctionCall;

    public ApplyFn() {
    }
    
    /**
     * Get the return type, given knowledge of the actual arguments
     *
     * @param args the actual arguments supplied
     * @return the best available item type that the function will return
     */
    @Override
    public ItemType getResultItemType(Expression[] args) {
        // Item type of the result is the same as that of the supplied function
        ItemType fnType = args[0].getItemType();
        if (fnType instanceof MapType) {
            return ((MapType)fnType).getValueType().getPrimaryType();
        } else if (fnType instanceof ArrayItemType) {
            return ((ArrayItemType) fnType).getMemberType().getPrimaryType();
        } else if (fnType instanceof FunctionItemType) {
            return ((FunctionItemType) fnType).getResultType().getPrimaryType();
        } else {
            return AnyItemType.getInstance();
        }
    }

    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {
        if (arguments.length == 2 && arguments[1] instanceof SquareArrayConstructor) {
            Expression target = arguments[0];
            if (target.getItemType() instanceof MapType) {
                // Convert $map($key) to map:get($map, $key)
                // This improves streamability analysis - see accumulator-053
                return makeGetCall(visitor, MapFunctionSet.getInstance(31), contextInfo, arguments);
            } else if (target.getItemType() instanceof ArrayItemType) {
                // Convert $array($key) to array:get($array, $key)
                return makeGetCall(visitor, ArrayFunctionSet.getInstance(31), contextInfo, arguments);
            }
        }
        return null;
    }

    private Expression makeGetCall(ExpressionVisitor visitor, BuiltInFunctionSet fnSet, ContextItemStaticInfo contextInfo, Expression[] arguments) throws XPathException {
        Expression target = arguments[0];
        Expression key = ((SquareArrayConstructor) arguments[1]).getOperanda().getOperand(0).getChildExpression();
        Expression getter = fnSet.makeFunction("get", 2).makeFunctionCall(target, key);
        getter.setRetainedStaticContext(target.getRetainedStaticContext());
        // Use custom diagnostics for type errors on the argument of the call (bug 4772)
        TypeChecker tc = visitor.getConfiguration().getTypeChecker(visitor.getStaticContext().isInBackwardsCompatibleMode());
        if (fnSet == MapFunctionSet.getInstance(31)) {
            Supplier<RoleDiagnostic> role =
                    () -> new RoleDiagnostic(RoleDiagnostic.MISC, "key value supplied when calling a map as a function", 0);
            ((SystemFunctionCall) getter).setArg(1, tc.staticTypeCheck(key, SequenceType.SINGLE_ATOMIC, role, visitor));
        } else {
            Supplier<RoleDiagnostic> role =
                    () -> new RoleDiagnostic(RoleDiagnostic.MISC, "subscript supplied when calling an array as a function", 0);
            ((SystemFunctionCall) getter).setArg(1, tc.staticTypeCheck(key, SequenceType.SINGLE_INTEGER, role, visitor));
        }
        return getter.typeCheck(visitor, contextInfo);
    }


    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments. The first argument is the function item
     *                  to be called; the second argument is an array containing
     *                  the arguments to be passed to that function, before conversion.
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        boolean is40 = getRetainedStaticContext().getPackageData().getHostLanguageVersion() >= 40;
        final FunctionItem function = (FunctionItem) arguments[0].head();
        Configuration config = context.getConfiguration();
        CoercionRules coercionRules = CoercionRules.forVersion(
                config,
                getRetainedStaticContext().getPackageData().getHostLanguageVersion());
        FunctionItemType fit = function.getFunctionItemType();

        ArrayItem args = (ArrayItem) arguments[1].head();

        if (is40 && function.getArity() != args.arrayLength()) {
            // In 4.0, excess arguments are ignored
            args = args.subArray(0, function.getArity());
        }

        if (function.getArity() != args.arrayLength()) {
            String errorCode = "FOAP0001";
            throw new XPathException(
                    "Number of arguments required for dynamic call to " + function.getDescription() + " is " + function.getArity() +
                            "; number supplied = " + args.arrayLength(), errorCode)
                    .withXPathContext(context);
        }

        Sequence[] argArray = new Sequence[args.arrayLength()];
        if (fit == AnyFunctionType.INSTANCE) {
            for (int i = 0; i < argArray.length; i++) {
                argArray[i] = args.get(i);
            }
        } else {
            for (int i = 0; i < argArray.length; i++) {
                SequenceType expected = fit.getArgumentTypes()[i];
                final int pos = i;
                Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(RoleDiagnostic.FUNCTION, "fn:apply", pos + 1);
                Sequence converted = coercionRules.coerce(
                        args.get(i), expected, SequenceType.ANY_SEQUENCE, config, role, Loc.NONE);
                argArray[i] = converted.materialize();
            }
        }

        if (function.isSequenceVariadic()) {
            List<Item> members = new ArrayList<>();
            for (GroundedValue mem : args.members()) {
                for (Item it : mem.asIterable()) {
                    members.add(it);
                }
            }
            final GroundedValue argSequence = SequenceExtent.makeSequenceExtent(members);
            List<GroundedValue> singletonArg = new ArrayList<>(1);
            singletonArg.add(argSequence);
        }


        Sequence rawResult = dynamicCall(function, context, argArray);
        if (function.isTrustedResultType()) {
            // trust system functions to return a result of the correct type
            return rawResult;
        } else {
            // Check the result of the function
            Supplier<RoleDiagnostic> resultRole =
                    () -> new RoleDiagnostic(RoleDiagnostic.FUNCTION_RESULT, "fn:apply", -1);
            return coercionRules.coerce(
                    rawResult, fit.getResultType(), SequenceType.ANY_SEQUENCE, config, resultRole, Loc.NONE);
        }
    }

    @Override
    public void exportAttributes(ExpressionPresenter out) {
        out.emitAttribute("dyn", dynamicFunctionCall);
    }

    /**
     * Import any attributes found in the export file, that is, any attributes output using
     * the exportAttributes method
     *
     * @param attributes the attributes, as a properties object
     */
    @Override
    public void importAttributes(Properties attributes) {
        dynamicFunctionCall = attributes.getProperty("dyn");
    }
}

