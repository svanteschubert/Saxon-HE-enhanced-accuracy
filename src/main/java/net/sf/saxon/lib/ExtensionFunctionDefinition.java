////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;

/**
 * This abstract class is provided to allow user-written extension functions to be implemented
 * with the full capabilities of functions that are an intrinsic part of the Saxon product.
 * In particular, the class has the opportunity to save data from the static context and
 * to optimize itself at compile time.
 * <p>There should be one class implementing this interface for each function name; if there
 * are several functions with the same name but different arity, the same class should implement
 * them all.</p>
 * <p>Note that an IntegratedFunction is trusted; calls are allowed even if the configuration option
 * {@link net.sf.saxon.lib.FeatureKeys#ALLOW_EXTERNAL_FUNCTIONS} is false. In cases where an IntegratedFunction
 * is used to load and execute untrusted code, it should check this configuration option before doing so.</p>
 *
 * @since 9.2
 */
public abstract class ExtensionFunctionDefinition {

    /**
     * Get the name of the function, as a QName.
     * <p>This method must be implemented in all subclasses</p>
     *
     * @return the function name
     */

    public abstract StructuredQName getFunctionQName();

    /**
     * Get the minimum number of arguments required by the function
     * <p>The default implementation returns the number of items in the result of calling
     * {@link #getArgumentTypes}</p>
     *
     * @return the minimum number of arguments that must be supplied in a call to this function
     */

    public int getMinimumNumberOfArguments() {
        return getArgumentTypes().length;
    }

    /**
     * Get the maximum number of arguments allowed by the function.
     * <p>The default implementation returns the value of {@link #getMinimumNumberOfArguments}
     *
     * @return the maximum number of arguments that may be supplied in a call to this function
     */

    public int getMaximumNumberOfArguments() {
        return getMinimumNumberOfArguments();
    }

    /**
     * Get the required types for the arguments of this function.
     * <p>This method must be implemented in all subtypes.</p>
     *
     * @return the required types of the arguments, as defined by the function signature. Normally
     *         this should be an array of size {@link #getMaximumNumberOfArguments()}; however for functions
     *         that allow a variable number of arguments, the array can be smaller than this, with the last
     *         entry in the array providing the required type for all the remaining arguments.
     */

    public abstract SequenceType[] getArgumentTypes();

    /**
     * Get the type of the result of the function
     * <p>This method must be implemented in all subtypes.</p>
     *
     * @param suppliedArgumentTypes the static types of the supplied arguments to the function.
     *                              This is provided so that a more precise result type can be returned in the common
     *                              case where the type of the result depends on the types of the arguments.
     * @return the return type of the function, as defined by its function signature
     */

    public abstract SequenceType getResultType(SequenceType[] suppliedArgumentTypes);

    /**
     * Ask whether the result actually returned by the function can be trusted,
     * or whether it should be checked against the declared type.
     *
     * @return true if the function implementation warrants that the value it returns will
     *         be an instance of the declared result type. The default value is false, in which case
     *         the result will be checked at run-time to ensure that it conforms to the declared type.
     *         If the value true is returned, but the function returns a value of the wrong type, the
     *         consequences are unpredictable.
     */

    public boolean trustResultType() {
        return false;
    }

    /**
     * Ask whether the result of the function depends on the focus, or on other variable parts
     * of the context.
     *
     * @return true if the result of the function depends on the context item, position, or size.
     *         Despite the method name, the method should also return true if the function depends on other
     *         parts of the context that vary from one part of the query/stylesheet to another, for example
     *         the XPath default namespace.
     *         <p>The default implementation returns false.</p>
     *         <p>The method must return true if the function
     *         makes use of any of these values from the dynamic context. Returning true inhibits certain
     *         optimizations, such as moving the function call out of the body of an xsl:for-each loop,
     *         or extracting it into a global variable.</p>
     */

    public boolean dependsOnFocus() {
        return false;
    }

    /**
     * Ask whether the function has side-effects. If the function does have side-effects, the optimizer
     * will be less aggressive in moving or removing calls to the function. However, calls on functions
     * with side-effects can never be guaranteed.
     *
     * @return true if the function has side-effects (including creation of new nodes, if the
     *         identity of those nodes is significant). The default implementation returns false.
     */

    public boolean hasSideEffects() {
        return false;
    }

    /**
     * Create a call on this function. This method is called by the compiler when it identifies
     * a function call that calls this function.
     *
     * @return an expression representing a call of this extension function
     */

    public abstract ExtensionFunctionCall makeCallExpression();

    /**
     * Get a Function item representing this ExtensionFunctionDefinition
     * @return a function item corresponding to this extension function
     */

    public final Function asFunction() {

        return new AbstractFunction() {

            /**
             * Invoke the function
             *
             * @param context the XPath dynamic evaluation context
             * @param args    the actual arguments to be supplied
             * @return the result of invoking the function
             * @throws XPathException if a dynamic error occurs within the function
             */
            @Override
            public Sequence call(XPathContext context, Sequence[] args) throws XPathException {
                return makeCallExpression().call(context, args);
            }

            /**
             * Get the item type of the function item
             *
             * @return the function item's type
             */
            @Override
            public FunctionItemType getFunctionItemType() {
                return new SpecificFunctionType(getArgumentTypes(), getResultType(getArgumentTypes()));
            }

            /**
             * Get the name of the function, or null if it is anonymous
             *
             * @return the function name, or null for an anonymous inline function
             */
            @Override
            public StructuredQName getFunctionName() {
                return getFunctionQName();
            }

            /**
             * Get the arity of the function
             *
             * @return the number of arguments in the function signature
             */
            @Override
            public int getArity() {
                return getArgumentTypes().length;
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
                return getFunctionQName().getDisplayName();
            }

            /**
             * Ask whether the result of the function should be checked against the declared return type
             * @return true if the result does not need to be checked (which can cause catastrophic
             * failure if this trust is misplaced)
             */
            @Override
            public boolean isTrustedResultType() {
                return trustResultType();
            }
        };

    }


}

