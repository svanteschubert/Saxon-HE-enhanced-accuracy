////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.FunctionCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;

/**
 * A function item obtained by coercing a supplied function; this adds a wrapper to perform dynamic
 * type checking of the arguments in any call, and type checking of the result.
 */
public class CoercedFunction extends AbstractFunction {

    private Function targetFunction;
    private SpecificFunctionType requiredType;

    /**
     * Create a CoercedFunction as a wrapper around a target function
     *
     * @param targetFunction the function to be wrapped by a type-checking layer
     * @param requiredType the type of the coerced function, that is the type required
     *                     by the context in which the target function is being used
     * @throws XPathException if the arity of the supplied function does not match the arity of the required type
     */

    public CoercedFunction(Function targetFunction, SpecificFunctionType requiredType) throws XPathException {
        if (targetFunction.getArity() != requiredType.getArity()) {
            throw new XPathException(
                    wrongArityMessage(targetFunction, requiredType.getArity()), "XPTY0004");
        }
        this.targetFunction = targetFunction;
        this.requiredType = requiredType;
    }

    /**
     * Create a CoercedFunction whose target function is not yet known (happens during package re-loading)
     * @param requiredType the type of the coerced function, that is the type required
     *                     by the context in which the target function is being used
     */

    public CoercedFunction(SpecificFunctionType requiredType) {
        this.requiredType = requiredType;
    }

    /**
     * Set the target function
     * @param targetFunction the function to be wrapped by a type-checking layer
     * @throws XPathException if the arity of the supplied function does not match the arity of the required type
     */

    public void setTargetFunction(Function targetFunction) throws XPathException {
        if (targetFunction.getArity() != requiredType.getArity()) {
            throw new XPathException(
                    wrongArityMessage(targetFunction, requiredType.getArity()), "XPTY0004");
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
        return "coerced " + targetFunction.getDescription();
    }

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */

    @Override
    public int getArity() {
        return targetFunction.getArity();
    }

    @Override
    public AnnotationList getAnnotations() {     // Bug 5085
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
        TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
        Sequence[] targetArgs = new Sequence[args.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = args[i].materialize();
            if (argTypes[i].matches(args[i], th)) {
                targetArgs[i] = args[i];
            } else {
                RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION, targetFunction.getDescription(), i);
                targetArgs[i] = th.applyFunctionConversionRules(args[i], argTypes[i], role, Loc.NONE);
            }
        }
        Sequence rawResult = targetFunction.call(context, targetArgs);
        rawResult = rawResult.materialize();
            // Needed because we have to examine the value twice, once to check its type, and then to convert it.
            // It would be better if applyFunctionConversionRules were a mapping function...
        if (req.getResultType().matches(rawResult, th)) {
            return rawResult;
        } else {
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.FUNCTION_RESULT, targetFunction.getDescription(), 0);
            return th.applyFunctionConversionRules(rawResult, req.getResultType(), role, Loc.NONE);
        }
    }

    /**
     * Factory method to create a CoercedFunction with a given type, for a given targetFunction.
     * It is assumed that we have already established that coercion is needed.
     * Called from Bytecode compiler.
     * @param suppliedFunction the function to be coerced
     * @param requiredType the target type for the coercion
     * @param role diagnostic information about the role of the expression being coerced
     * @return the function after coercion
     * @throws XPathException if the function cannot be coerced to the required type (for example,
     * because it has the wrong arity)
     */

    public static CoercedFunction coerce(Function suppliedFunction, SpecificFunctionType requiredType,
                                         RoleDiagnostic role)
            throws XPathException {
        // DO NOT DELETE THIS METHOD: IT IS CALLED FROM BYTECODE
        int arity = requiredType.getArity();
        if (suppliedFunction.getArity() != arity) {
            String msg = role.composeErrorMessage(
                    requiredType, suppliedFunction, null);
            msg += ". " + wrongArityMessage(suppliedFunction, arity);
            throw new XPathException(msg, "XPTY0004");
        }
        return new CoercedFunction(suppliedFunction, requiredType);
    }

    private static String wrongArityMessage(Function supplied, int expected) {
        return "The supplied function (" + supplied.getDescription() + ") has " + FunctionCall.pluralArguments(supplied.getArity()) +
                " - expected " + expected;
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

// Copyright (c) 2009-2020 Saxonica Limited
