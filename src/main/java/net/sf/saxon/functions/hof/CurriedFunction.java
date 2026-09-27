////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;

import java.util.Arrays;
import java.util.Objects;

/**
 * A function obtained by currying another function: that is, the result of a partial function application
 */
public class CurriedFunction extends AbstractFunction {

    private final FunctionItem targetFunction;
    private final Sequence[] boundValues;
    private final int[] unboundArgumentMap;
    private FunctionItemType functionType;

    /**
     * Create a curried (or partially applied) function
     *
     * @param targetFunction the base function to be curried
     * @param boundValues    the values to which the arguments are to be bound, representing
     *                       unbound values (placeholders) by null. So if the arguments
     *                       are (4, ?, 5) then this array will be [4, null, 5]. If keywords
     *                       are used in the call then they will have been resolved to
     *                       argument positions. The values supplied must conform to
     *                       the required type: coercion is the responsibility of the caller.
     * @param unboundArgMap  for unbound arguments (placeholders), mapping from
     *                       argument positions in the curried function to argument
     *                       positions in the target function. If the arguments are
     *                       (4, ?, 5) then this array will be [1], indicating that
     *                       the first and only argument in the curried function is
     *                       bound to the second (position==1) argument in the underlying
     *                       function. If keywords are used in the call then this
     *                       mapping may be non-trivial; for example if the base function
     *                       is f(x,y) and the partial function application is f(y:=?, x:=?),
     *                       then this array will contain [1,0]. The length of this array
     *                       is equal to the arity of the curried function.
     */

    public CurriedFunction(FunctionItem targetFunction, Sequence[] boundValues, int[] unboundArgMap) {
        this.targetFunction = Objects.requireNonNull(targetFunction);
        this.boundValues = boundValues;
        this.unboundArgumentMap = unboundArgMap;
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */

    @Override
    public FunctionItemType getFunctionItemType() {
        if (functionType == null) {
            FunctionItemType baseItemType = targetFunction.getFunctionItemType();
            SequenceType resultType = SequenceType.ANY_SEQUENCE;
            if (baseItemType instanceof SpecificFunctionType) {
                resultType = baseItemType.getResultType();
            }
            int placeholders = 0;
            for (Sequence boundArgument : boundValues) {
                if (boundArgument == null) {
                    placeholders++;
                }
            }
            SequenceType[] argTypes = new SequenceType[getArity()];
            if (baseItemType instanceof SpecificFunctionType) {
                for (int i = 0; i<unboundArgumentMap.length; i++) {
                    argTypes[i] = baseItemType.getArgumentTypes()[unboundArgumentMap[i]];
                }
//                for (int i = 0, j = 0; i < boundValues.length; i++) {
//                    if (boundValues[i] == null) {
//                        argTypes[j++] = baseItemType.getArgumentTypes()[i];
//                    }
//                }
            } else {
                Arrays.fill(argTypes, SequenceType.ANY_SEQUENCE);
            }

            functionType = new SpecificFunctionType(argTypes, resultType);
        }
        return functionType;
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */

    /*@Nullable*/
    @Override
    public StructuredQName getFunctionName() {
        if (getArity() == targetFunction.getArity()) {
            return targetFunction.getFunctionName();
        }
        return null;
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
        return "partially-applied function " + targetFunction.getDescription();
    }

    /**
     * Get the arity of the function (equal to the number of placeholders)
     *
     * @return the number of arguments in the function signature
     */

    @Override
    public int getArity() {
        return unboundArgumentMap.length;
    }

    /**
     * Get the function annotations (as defined in XQuery). Returns an empty
     * list if there are no function annotations. The function annotations on a partially
     * applied function are the same as the annotations on its base function.
     *
     * @return the function annotations
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
     * @throws net.sf.saxon.trans.XPathException if the function reports a dynamic error
     *
     */

    @Override
    public Sequence call(XPathContext context, Sequence[] args) throws XPathException {
        assert args.length == unboundArgumentMap.length;
        Sequence[] newArgs = new Sequence[boundValues.length];
        for (int i = 0; i < newArgs.length; i++) {
            if (boundValues[i] != null) {
                newArgs[i] = boundValues[i].materialize();
            }
        }
        for (int i = 0; i < args.length; i++) {
            newArgs[unboundArgumentMap[i]] = args[i];
        }
        XPathContext c2 = targetFunction.makeNewContext(context, null);
        if (targetFunction instanceof UserFunction) {
            ((XPathContextMajor) c2).setCurrentComponent(((UserFunction) targetFunction).getDeclaringComponent());
        }
        return targetFunction.call(c2, newArgs);
    }

    /**
     * Output information about this function item
     *
     * @param out the destination to write to
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("curriedFunc");
        targetFunction.export(out);
        out.startElement("args");
        for (Sequence seq : boundValues) {
            if (seq == null) {
                out.startElement("x");
                out.endElement();
            } else {
                Literal.exportValue(seq, out);
            }
        }
        out.endElement();
        out.endElement();
    }
}
