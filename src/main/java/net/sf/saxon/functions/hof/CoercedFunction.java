////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.FunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.type.coercion.CoercionRules;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;

/**
 * A function item obtained by coercing a supplied function; this adds a wrapper to perform dynamic
 * type checking of the arguments in any call, and type checking of the result.
 */
public class CoercedFunction extends AbstractFunction {

    private FunctionItem targetFunction;
    private final SpecificFunctionType requiredType;
    private final boolean allow40;
    private final boolean acceptReducedArity;

    /**
     * Create a CoercedFunction as a wrapper around a target function
     *
     * @param targetFunction    the function to be wrapped by a type-checking layer
     * @param requiredType      the type of the coerced function, that is the type required
     *                          by the context in which the target function is being used
     * @param allow40 true if the 4.0 rules apply: the supplied function may have lower arity
     *                          than the required type
     * @throws XPathException if the arity of the supplied function does not match the arity of the required type
     */

    public CoercedFunction(FunctionItem targetFunction, SpecificFunctionType requiredType, boolean allow40, boolean acceptReducedArity) throws XPathException {
        if (targetFunction.getArity() != requiredType.getArity()) {
            if (targetFunction.getArity() > requiredType.getArity() || !allow40) {
                throw new XPathException(
                        wrongArityMessage(targetFunction, requiredType.getArity()), "XPTY0004");
            }
        }
        this.targetFunction = targetFunction;
        this.requiredType = requiredType;
        this.allow40 = allow40;
        this.acceptReducedArity = acceptReducedArity;
    }

    /**
     * Create a CoercedFunction whose target function is not yet known (happens during package re-loading)
     * @param requiredType the type of the coerced function, that is the type required
     *                     by the context in which the target function is being used
     */

    public CoercedFunction(SpecificFunctionType requiredType) {
        this.requiredType = requiredType;
        this.allow40 = false;
        this.acceptReducedArity = false;
    }

    /**
     * Set the target function
     * @param targetFunction the function to be wrapped by a type-checking layer
     * @throws XPathException if the arity of the supplied function does not match the arity of the required type
     */

    public void setTargetFunction(FunctionItem targetFunction) throws XPathException {
        if (targetFunction.getArity() != requiredType.getArity()) {
            if (targetFunction.getArity() > requiredType.getArity() || !allow40) {
                throw new XPathException(
                        wrongArityMessage(targetFunction, requiredType.getArity()), "XPTY0004");
            }
        }
        this.targetFunction = targetFunction;
    }

    /**
     * Type check the function (may modify it by adding code for converting the arguments)
     *
     * @param visitor         the expression visitor, supplies context information
     * @param contextItemType the context item type at the point where the function definition appears
     * @throws XPathException if type checking of the target function fails
     *
     */
    @Override
    public void typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        if (targetFunction instanceof AbstractFunction) {
            ((AbstractFunction) targetFunction).typeCheck(visitor, contextItemType);
        }
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */

    @Override
    public FunctionItemType getFunctionItemType() {
        return requiredType;
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return null indicating that this is an anonymous inline function
     */

    /*@Nullable*/
    @Override
    public StructuredQName getFunctionName() {
        return targetFunction.getFunctionName();
    }

    /**
     * Get a description of this function for use in error messages. For named functions, the description
     * is the function name (as a lexical QName). For others, it might be, for example, "inline function",
     * or "partially-applied ends-with function".
     *
     * @return a description of the function for use in error messages
     */
    @Override
    public String getDescription() {
        if (targetFunction instanceof CoercedFunction) {
            return targetFunction.getDescription();
        } else {
            return targetFunction.getDescription() + " (used where the required type is " + requiredType + ")";
        }
    }

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */

    @Override
    public int getArity() {
        return requiredType.getArity();
    }

    /**
     * Get the function annotations. These are the same as the annotations of the base function
     * @return the annotations of the base function
     */
    @Override
    public AnnotationList getAnnotations() {
        return targetFunction.getAnnotations();
    }

    /**
     * Invoke the function
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws net.sf.saxon.trans.XPathException if execution of the function fails
     *
     */

    @Override
    public Sequence call(XPathContext context, Sequence[] args) throws XPathException {
        SpecificFunctionType req = requiredType;
        SequenceType[] argTypes = targetFunction.getFunctionItemType().getArgumentTypes();
        int suppliedArity = Math.min(args.length, argTypes.length);
        Configuration config = context.getConfiguration();
        CoercionRules coercionRules = CoercionRules.forVersion(config, allow40 ? 40 : 31);
        Sequence[] targetArgs = new Sequence[suppliedArity];
        for (int i = 0; i < suppliedArity; i++) {
            GroundedValue gVal = args[i].materialize();
            if (argTypes[i].matches(gVal)) {
                targetArgs[i] = gVal;
            } else {
                final int pos = i;
                Supplier<RoleDiagnostic> role =
                        () -> new RoleDiagnostic(RoleDiagnostic.FUNCTION, targetFunction.getDescription(), pos);
                targetArgs[i] = coercionRules.coerce(gVal, argTypes[i], SequenceType.ANY_SEQUENCE, config, role, Loc.NONE).makeRepeatable();
            }
        }
        // TODO: don't materialize the result if static type checking tells us the result will be OK
        GroundedValue rawResult = targetFunction.call(context, targetArgs).materialize();
        if (req.getResultType().matches(rawResult)) {
            return rawResult;
        } else {
            Supplier<RoleDiagnostic> role =
                    () -> new RoleDiagnostic(RoleDiagnostic.FUNCTION_RESULT, getDescription(), 0);
            return coercionRules.coerce(rawResult, req.getResultType(), SequenceType.ANY_SEQUENCE, config, role, Loc.NONE);
        }
    }


    private static String wrongArityMessage(FunctionItem supplied, int expected) {
        return "The supplied function (" + supplied.getDescription() + ") has " + FunctionCall.plural(supplied.getArity(), "parameter") +
                " - expected a function with " + FunctionCall.plural(expected, "parameter");
    }

    /**
     * Export information about this function item to the SEF file
     *
     * @param out the SEF output destination
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("coercedFn");
        out.emitAttribute("type", requiredType.toExportString());
        new FunctionLiteral(targetFunction).export(out);
        out.endElement();
    }

}

// Copyright (c) 2009-2026 Saxonica Limited
