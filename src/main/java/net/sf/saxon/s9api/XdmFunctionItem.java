////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.streams.Step;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.TypeHierarchy;

import java.util.stream.Stream;

/**
 * The class XdmFunctionItem represents a function item
 */

public class XdmFunctionItem extends XdmItem {

    protected XdmFunctionItem() {}

    public XdmFunctionItem(Function fi) {
        setValue(fi);
    }

    /**
     * Get the name of the function
     *
     * @return the function name, as a QName, or null for an anonymous inline function item
     */

    public QName getName() {
        Function fi = (Function) getUnderlyingValue();
        StructuredQName sq = fi.getFunctionName();
        return sq == null ? null : new QName(sq);
    }

    /**
     * Get the arity of the function
     *
     * @return the arity of the function, that is, the number of arguments in the function's signature
     */

    public int getArity() {
        Function fi = (Function) getUnderlyingValue();
        return fi.getArity();
    }

    /**
     * Determine whether the item is an atomic value
     *
     * @return false, the item is not an atomic value, it is a function item
     */

    @Override
    public boolean isAtomicValue() {
        return false;
    }

    /**
     * Get a system function. This can be any function defined in XPath 3.1 functions and operators,
     * including functions in the math, map, and array namespaces. It can also be a Saxon extension
     * function, provided a licensed Processor is used.
     * @return the requested function, or null if there is no such function. Note that some functions
     * (those with particular context dependencies) may be unsuitable for dynamic calling.
     * @throws SaxonApiException no longer thrown, but retained in the declaration for backwards compatibility
     */

    public static XdmFunctionItem getSystemFunction(Processor processor, QName name, int arity) throws SaxonApiException {
        Configuration config = processor.getUnderlyingConfiguration();
        Function f = config.getSystemFunction(name.getStructuredQName(), arity);
        return f==null ? null : new XdmFunctionItem(f);
    }

    /**
     * Get an equivalent Java Function object representing this XdmFunction.
     * This is possible only for arity-1 functions.
     * @return a Java Function. This takes an XdmValue
     * as its argument, and returns the function result in the form of an XdmValue.
     * The Function throws an unchecked exception if evaluation fails
     * @throws IllegalStateException if the arity of the function is not one (1).
     */

    public java.util.function.Function<? super XdmValue, ? extends XdmValue> asFunction(Processor processor) {
        if (getArity() == 1) {
            return (java.util.function.Function<XdmValue, XdmValue>) arg -> {
                try {
                    return XdmFunctionItem.this.call(processor, arg);
                } catch (SaxonApiException e) {
                    throw new SaxonApiUncheckedException(e);
                }
            };
        } else {
            throw new IllegalStateException("Function arity must be one");
        }
    }

    /**
     * Get an equivalent Step object representing this XdmFunction.
     * This is possible only for arity-1 functions.
     *
     * @return a Step. This takes an XdmItem
     * as its argument, and returns the function result in the form of an XdmStream.
     * The Function throws an unchecked exception if evaluation fails
     * @throws IllegalStateException if the arity of the function is not one (1).
     */

    public Step<XdmItem> asStep(Processor processor) {
        if (getArity() == 1) {
            return new Step<XdmItem>() {
                @Override
                public Stream<? extends XdmItem> apply(XdmItem arg) {
                    try {
                        return XdmFunctionItem.this.call(processor, arg).stream();
                    } catch (SaxonApiException e) {
                        throw new SaxonApiUncheckedException(e);
                    }
                }
            };
        } else {
            throw new IllegalStateException("Function arity must be one");
        }
    }


    /**
     * Call the function
     *
     * @param arguments the values to be supplied as arguments to the function. The "function
     *                  conversion rules" will be applied to convert the arguments to the required
     *                  type when necessary.
     * @param processor the s9api Processor
     * @return the result of calling the function
     */

    public XdmValue call(Processor processor, XdmValue... arguments) throws SaxonApiException {
        if (arguments.length != getArity()) {
            throw new SaxonApiException("Supplied " + arguments.length + " arguments, required " + getArity());
        }
        try {
            Function fi = (Function) getUnderlyingValue();
            FunctionItemType type = fi.getFunctionItemType();
            Sequence[] argVals = new Sequence[arguments.length];
            TypeHierarchy th = processor.getUnderlyingConfiguration().getTypeHierarchy();
            for (int i = 0; i < arguments.length; i++) {
                net.sf.saxon.value.SequenceType required = type.getArgumentTypes()[i];
                Sequence val = arguments[i].getUnderlyingValue();
                if (!required.matches(val, th)) {
                    RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION, "", i);
                    val = th.applyFunctionConversionRules(val, required, role, Loc.NONE);
                }
                argVals[i] = val;
            }
            Configuration config = processor.getUnderlyingConfiguration();
            Controller controller = new Controller(config);
            XPathContext context = controller.newXPathContext();
            context = fi.makeNewContext(context, controller);

            Sequence result = fi.call(context, argVals);
            if (!fi.isTrustedResultType()) {
                result = result.materialize();
                net.sf.saxon.value.SequenceType required = type.getResultType();
                if (!required.matches(result, th)) {
                    RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION_RESULT, "", 0);
                    result = th.applyFunctionConversionRules(result.materialize(), required, role, Loc.NONE);
                }
            }
            Sequence se = result.materialize();
            return XdmValue.wrap(se);
        } catch (XPathException e) {
            throw new SaxonApiException(e);
        }
    }

}
