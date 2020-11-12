////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import java.util.Properties;


/**
 * Abstract superclass for calls to functions in the standard function library
 */

public abstract class SystemFunction extends AbstractFunction {

    private int arity;
    private BuiltInFunctionSet.Entry details;
    private RetainedStaticContext retainedStaticContext;

    /**
     * Make a system function call (one in the standard function namespace).
     *
     * @param name      The local name of the function.
     * @param rsc       Necessary information about the static context
     * @param arguments the arguments to the function call
     * @return a FunctionCall that implements this function, if it
     * exists, or null if the function is unknown.
     */

    /*@Nullable*/
    public static Expression makeCall(String name, RetainedStaticContext rsc, Expression... arguments)  {
        SystemFunction f = makeFunction(name, rsc, arguments.length);
        if (f == null) {
            return null;
        }
        Expression expr = f.makeFunctionCall(arguments);
        expr.setRetainedStaticContext(rsc);
        return expr;
    }

    /**
     * Make a system function item (one in the standard function namespace).
     *
     * @param name      The local name of the function.
     * @param rsc       Necessary information about the static context
     * @param arity     the arity of the function
     * @return          the function item
     */

    public static SystemFunction makeFunction(String name, RetainedStaticContext rsc, int arity) {
        if (rsc == null) {
            throw new NullPointerException();
        }
        SystemFunction fn = rsc.getConfiguration().makeSystemFunction(name, arity);
        if (fn == null) {
            rsc.getConfiguration().makeSystemFunction(name, arity);
            throw new IllegalStateException(name);
        }
        fn.setRetainedStaticContext(rsc);
        return fn;
    }

    /**
     * Make an expression that either calls this function, or that is equivalent to a call
     * on this function
     * @param arguments the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result
     */

    public Expression makeFunctionCall(Expression... arguments) {
        Expression e = new SystemFunctionCall(this, arguments);
        e.setRetainedStaticContext(getRetainedStaticContext());
        return e;
    }

    /**
     * Set the arity of the function
     * @param arity the number of arguments
     */

    public void setArity(int arity) {
        this.arity = arity;
    }

    /**
     * Get an estimate of the net cost of evaluating the function, excluding the cost of evaluating
     * its arguments. The result is 0 for very simple functions like position() and exists(), 1 by
     * default, and higher values for particularly expensive functions.
     * @return the estimated cost
     */

    public int getNetCost() {
        return 1;
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     * @param visitor the expression visitor
     * @param contextInfo information about the context item
     * @param arguments the supplied arguments to the function call. Note: modifying the contents
     *                  of this array should not be attempted, it is likely to have no effect.
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws XPathException if an error is detected
     */

    public Expression makeOptimizedFunctionCall (
            ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments)
            throws XPathException {
        Optimizer opt = visitor.obtainOptimizer();
        if (opt.isOptionSet(OptimizerOptions.CONSTANT_FOLDING)) {
            return fixArguments(arguments);
        } else {
            return null;
        }
    }

    /**
     * Optimize for constant argument values
     */

    public Expression fixArguments(Expression... arguments) throws XPathException {
        // Check if any arguments are known to be empty, with a declared result for that case
        for (int i = 0; i < getArity(); i++) {
            if (Literal.isEmptySequence(arguments[i]) && resultIfEmpty(i) != null) {
                return Literal.makeLiteral(details.resultIfEmpty[i].materialize());
            }
        }
        return null;
    }

    /**
     * Ask if the function always returns a known result when one of the arguments is an empty sequence
     * @param arg the argument whose value is an empty sequence (counting from zero)
     * @return the value to be returned when this argument is an empty sequence, or null if unknown / not applicable
     */

    protected Sequence resultIfEmpty(int arg) {
        return details.resultIfEmpty[arg];
    }

    /**
     * Get the static context in which the function operates, for use with functions whose result
     * depends on the static context
     * @return the retained static context
     */

    public RetainedStaticContext getRetainedStaticContext() {
        return retainedStaticContext;
    }

    /**
     * Set the static context in which the function operates, for use with functions whose result
     * depends on the static context
     *
     * @param retainedStaticContext the retained static context
     */

    public void setRetainedStaticContext(RetainedStaticContext retainedStaticContext) {
        this.retainedStaticContext = retainedStaticContext;
    }

    /**
     * Ask whether the result of the function depends on the context item
     * @return true if the function depends on the context item
     */

    public boolean dependsOnContextItem() {
        return (details.properties & (BuiltInFunctionSet.CITEM | BuiltInFunctionSet.CDOC)) != 0;
    }

    /**
     * Set the details of this type of function
     *
     * @param entry information giving details of the function signature and other function properties
     */

    public void setDetails(BuiltInFunctionSet.Entry entry) {
        details = entry;
    }

    /**
     * Get the details of the function signature
     *
     * @return information about the function signature and other function properties
     */

    public BuiltInFunctionSet.Entry getDetails() {
        return details;
    }

    /**
     * Get the qualified name of the function being called
     *
     * @return the qualified name
     */

    @Override
    public StructuredQName getFunctionName() {
        return details.name;
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
        return details.name.getDisplayName();
    }

    /**
     * Get the arity of the function (the number of arguments). Note that a subclass of SystemFunction may
     * support a family of functions with different arity, but an instance of the SystemFunction class always has
     * a single arity.
     * @return the arity of the function
     */

    @Override
    public int getArity() {
        return arity;
    }

    /**
     * Get the roles of the arguments, for the purposes of streaming
     *
     * @return an array of OperandRole objects, one for each argument
     */
    @Override
    public OperandRole[] getOperandRoles() {
        OperandRole[] roles = new OperandRole[getArity()];
        OperandUsage[] usages = details.usage;
        try {
            for (int i = 0; i < getArity(); i++) {
                roles[i] = new OperandRole(0, usages[i], getRequiredType(i));
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return roles;
    }

    /**
     * For a function that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     * unknown or not applicable.
     */

    /*@Nullable*/
    public IntegerValue[] getIntegerBounds() {
        return null;
    }

    /**
     * Method called during static type checking. This method may be implemented in subclasses so that functions
     * can take advantage of knowledge of the types of the arguments that will be supplied.
     * @param visitor an expression visitor, providing access to the static context and configuration
     * @param contextItemType information about whether the context item is set, and what its type is
     * @param arguments the expressions appearing as arguments in the function call
     */

    public void supplyTypeInformation (
        ExpressionVisitor visitor, ContextItemStaticInfo contextItemType, Expression[] arguments) throws XPathException {
        //default: no action
    }

    /**
     * Determine whether two functions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return (o instanceof SystemFunction) && super.equals(o);
    }

    /**
     * Return the error code to be used for type errors. This is overridden for functions
     * such as exactly-one(), one-or-more(), ...
     *
     * @return the error code to be used for type errors in the function call. Normally XPTY0004,
     * but different codes are used for functions such as exactly-one()
     */

    public String getErrorCodeForTypeErrors() {
        return "XPTY0004";
    }

    /**
     * Get the required type of the nth argument
     *
     * @param arg the number of the argument whose type is requested, zero-based
     * @return the required type of the argument as defined in the function signature
     */

    public SequenceType getRequiredType(int arg) {
        if (details == null) {
            return SequenceType.ANY_SEQUENCE;
        }
        return details.argumentTypes[arg];
        // this is overridden for concat()
    }

    /**
     * Determine the item type of the value returned by the function
     */
    public ItemType getResultItemType() {
        return details.itemType;
//        if (details == null) {
//            // probably an unresolved function call
//            return AnyItemType.getInstance();
//        }
//        ItemType type = details.itemType;
//        if ((details.properties & StandardFunction.AS_ARG0) != 0) {
//            if (getArity() > 0) {
//                return getArg(0).getItemType();
//            } else {
//                return AnyItemType.getInstance();
//                // if there is no first argument, an error will be reported
//            }
//        } else if ((details.properties & StandardFunction.AS_PRIM_ARG0) != 0) {
//            if (getArity() > 0) {
//                ItemType t0 = getArg(0).getItemType().getPrimitiveItemType();
//                return UType.NUMERIC.subsumes(t0.getUType()) ? t0 : type;
//            } else {
//                return AnyItemType.getInstance();
//                // if there is no first argument, an error will be reported
//            }
//        } else {
//            return type;
//        }
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */

    @Override
    public FunctionItemType getFunctionItemType() {
        SequenceType resultType = SequenceType.makeSequenceType(getResultItemType(), details.cardinality);
        return new SpecificFunctionType(details.argumentTypes, resultType);
    }

    /**
     * Get the return type, given knowledge of the actual arguments
     * @param args the actual arguments supplied
     * @return the best available item type that the function will return
     */

    public ItemType getResultItemType(Expression[] args) {
        if ((details.properties & BuiltInFunctionSet.AS_ARG0) != 0) {
            return args[0].getItemType();
        } else if ((details.properties & BuiltInFunctionSet.AS_PRIM_ARG0) != 0) {
            return args[0].getItemType().getPrimitiveItemType();
        } else {
            return details.itemType;
        }
    }

    /**
     * Get the cardinality, given knowledge of the actual arguments
     * @param args the actual arguments supplied
     * @return the most precise available cardinality that the function will return
     */

    public int getCardinality(Expression[] args) {
        int c = details.cardinality;
        if (c == BuiltInFunctionSet.OPT && (details.properties & BuiltInFunctionSet.CARD0) != 0 &&
                !Cardinality.allowsZero(args[0].getCardinality())) {
            return StaticProperty.EXACTLY_ONE;
        } else {
            return c;
        }
    }

    /**
     * Determine the special properties of this function. The general rule
     * is that a system function call is non-creative unless more details
     * are defined in a subclass.
     * @param arguments the actual arguments supplied in a call to the function
     */

    public int getSpecialProperties(Expression[] arguments) {
        if ((details.properties & BuiltInFunctionSet.NEW) != 0) {
            return StaticProperty.ALL_NODES_NEWLY_CREATED;
        }
        int p = StaticProperty.NO_NODES_NEWLY_CREATED;
        if ((details.properties & BuiltInFunctionSet.SIDE) != 0) {
             p |= StaticProperty.HAS_SIDE_EFFECTS;
        }
        return p;
    }

    /**
     * Helper method for subclasses: get the context item if it is a node, throwing appropriate errors
     * if not
     *
     * @param context the XPath dynamic context
     * @return the context item if it exists and is a node
     * @throws XPathException if there is no context item or if the context item is not a node
     */

    protected NodeInfo getContextNode(XPathContext context) throws XPathException {
        Item item = context.getContextItem();
        if (item == null) {
            XPathException err = new XPathException("Context item for " + getFunctionName() + "() is absent", "XPDY0002");
            err.maybeSetContext(context);
            throw err;
        } else if (!(item instanceof NodeInfo)) {
            XPathException err = new XPathException("Context item for " + getFunctionName() + "() is not a node", "XPTY0004");
            err.maybeSetContext(context);
            throw err;
        } else {
            return (NodeInfo) item;
        }
    }

    /**
     * Make a dynamic call to a supplied argument function (convenience method for use by implementations)
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws XPathException if a dynamic error occurs within the function
     */

    public static Sequence dynamicCall(Function f, XPathContext context, Sequence[] args) throws XPathException {
        context = f.makeNewContext(context, null);
        context.setCurrentOutputUri(null);
        return f.call(context, args);
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("fnRef");
        StructuredQName qName = getFunctionName();
        String name = qName.hasURI(NamespaceConstant.FN) ? qName.getLocalPart() : qName.getEQName();
        out.emitAttribute("name", name);
        out.emitAttribute("arity", getArity() + "");
        if ((getDetails().properties & BuiltInFunctionSet.DEPENDS_ON_STATIC_CONTEXT) != 0) {
            out.emitRetainedStaticContext(getRetainedStaticContext(), null);
        }
        out.endElement();
    }

    /**
     * Typecheck a call on this function
     */

    public Expression typeCheckCaller(FunctionCall caller, ExpressionVisitor visitor, ContextItemStaticInfo contextInfo)
            throws XPathException {
        return caller;
    }

    @Override
    public boolean isTrustedResultType() {
        return true;
    }

    public String getStaticBaseUriString() {
        return getRetainedStaticContext().getStaticBaseUriString();
    }

    /**
     * Export any context attributes held within the SystemFunction object. The implementation
     * will normally make one or more calls on out.emitAttribute(name, value).
     * @param out the export destination
     */

    public void exportAttributes(ExpressionPresenter out) {}

    /**
     * Export any implicit arguments held in optimized form within the SystemFunction call
     * @param out the export destination
     */

    public void exportAdditionalArguments(SystemFunctionCall call, ExpressionPresenter out) throws XPathException {
    }

    /**
     * Import any attributes found in the export file, that is, any attributes output using
     * the exportAttributes method
     * @param attributes the attributes, as a properties object
     * @throws XPathException
     */

    public void importAttributes(Properties attributes) throws XPathException {}

    /**
     * Get the (local) name of a class that can be used to generate bytecode for this
     * system function
     * @return the name of a bytecode generation class, or null if there is no bytecode
     * support for this function
     */
    public String getCompilerName() {
        return null;
    }

    /**
     * Get a name that identifies the class that can be used to evaluate this function
     * in streaming mode, that is, supplying one item or event at a time as it is
     * delivered by the parser. Special streaming support is generally available in
     * Saxon-EE for all system functions that take a sequence as their argument.
     * @return a name that identifies a class that supports streamed evaluation of this system
     * function, or null if no such class is available. The mapping of names to classes is
     * defined in the Saxon-EE class {@code StreamerMap}. In non-streaming Saxon editions
     * (HE and PE) the method always returns null.
     */

    public String getStreamerName() {
        return null;
    }

    /**
     * Provide a short string showing the contents of the item, suitable
     * for use in error messages
     *
     * @return a depiction of the item suitable for use in error messages
     */
    @Override
    public String toShortString() {
        return getFunctionName().getDisplayName() + '#' + getArity();
    }

    public String toString() {
        return getFunctionName().getDisplayName() + '#' + getArity();
    }


}

