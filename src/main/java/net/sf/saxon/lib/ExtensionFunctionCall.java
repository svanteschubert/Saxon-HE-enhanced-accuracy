////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

/**
 * This abstract class is provided to allow user-written extension functions to be implemented
 * with the full capabilities of functions that are an intrinsic part of the Saxon product.
 * In particular, the class has the opportunity to save data from the static context and
 * to optimize itself at compile time.
 * <p>Instances of this class are created by calling the method makeCallExpression() on the
 * {@link ExtensionFunctionDefinition} object that represents the definition of the function.</p>
 * <p>The compiler will create one instance of this class for each function call appearing in the
 * expression being compiled. The class must therefore have a public zero-argument constructor.</p>
 * <p>The compiler will ensure that the supplied arguments in the extension function call are converted
 * if necessary to the declared argument types, by applying the standard conversion rules. The result
 * returned by the function is checked against the declared return type, but no conversion takes place:
 * the returned value must strictly conform to the declared type.</p>
 * <p>Note that an <code>ExtensionFunctionCall</code> is trusted; calls are allowed even if the configuration option
 * {@link net.sf.saxon.lib.FeatureKeys#ALLOW_EXTERNAL_FUNCTIONS} is false. In cases where an <code>ExtensionFunctionCall</code>
 * is used to load and execute untrusted code, it should check this configuration option before doing so.</p>
 *
 * @since 9.2; modified in 9.5 to use Sequence rather than SequenceIterator for the arguments and result
 */
public abstract class ExtensionFunctionCall implements Callable {

    ExtensionFunctionDefinition definition;

    /**
     * This method is called by the system to provide information about the extension function call.
     * It should not be called by the implementor of the extension function.
     *
     * @param definition the extension function definition
     */

    public final void setDefinition(ExtensionFunctionDefinition definition) {
        this.definition = definition;
    }

    /**
     * Get the definition of this extension function
     *
     * @return the function definition from which this <code>ExtensionFunctionCall</code> was created
     */

    public final ExtensionFunctionDefinition getDefinition() {
        return definition;
    }

    /**
     * Supply static context information.
     * <p>This method is called during compilation to provide information about the static context in which
     * the function call appears. If the implementation of the function needs information from the static context,
     * then it should save it now, as it will not be available later at run-time.</p>
     * <p>The implementation also has the opportunity to examine the expressions that appear in the
     * arguments to the function call at this stage. These might already have been modified from the original
     * expressions as written by the user. The implementation must not modify any of these expressions.</p>
     * <p>The default implementation of this method does nothing.</p>
     *
     * @param context    The static context in which the function call appears. The method must not modify
     *                   the static context.
     * @param locationId An integer code representing the location of the call to the extension function
     *                   in the stylesheet; can be used in conjunction with the locationMap held in the static context for diagnostics
     * @param arguments  The XPath expressions supplied in the call to this function. The method must not
     *                   modify this array, or any of the expressions contained in the array. This parameter will
     *                   be null in the case where the supplied arguments are null, that is when binding a
     *                   higher-order function reference or a call on fn:function-lookup().
     * @throws XPathException if the implementation is able to detect a static error in the way the
     *                        function is being called (for example it might require that the types of the arguments are
     *                        consistent with each other).
     */

    public void supplyStaticContext(StaticContext context, int locationId, Expression[] arguments) throws XPathException {
        // default implementation does nothing
    }

    /**
     * Rewrite the function call. This method is called at compile time. It gives the implementation
     * an opportunity to replace itself with an optimized implementation that returns the same result.
     * This includes the ability to pre-evaluate the function and return its result as a literal value.
     * <p>There is also a further opportunity to perform static checking at this stage and to throw an error
     * if the arguments are invalid.</p>
     *
     * @param context   The static context in which the function call appears. The method must not modify
     *                  the static context.
     * @param arguments The XPath expressions supplied in the call to this function. This method is called
     *                  after type-checking, so these expressions may have been modified by adding atomization operators
     *                  or type-checking operations, for example.
     * @return an expression to be evaluated at run-time in place of this function call. Return null
     *         if no rewriting is required and the function call should be used unchanged. Return a
     *         {@link net.sf.saxon.expr.Literal} representing the result of the function call if the function call
     *         can be precomputed at compile time
     * @throws XPathException if the implementation is able to detect a static error in the way the
     *                        function is being called (for example it might require that the types of the arguments are
     *                        consistent with each other).
     */

    /*@Nullable*/
    public Expression rewrite(StaticContext context, Expression[] arguments) throws XPathException {
        // default implementation does nothing
        return null;
    }

    /**
     * Copy local data from one copy of the function to another. This method must be implemented
     * in any implementation that maintains local data retained from the static context; the job of the
     * method is to copy this local data to the supplied destination function.
     * <p>This method is called if a call to the extension function needs to be copied during
     * the process of optimization. For example, this occurs if the function containing the call
     * to the extension function is inlined.</p>
     * <p>If any objects held as local data for the function call are mutable then deep copies must
     * be made.</p>
     *
     * @param destination the function to which the local data must be copied. This will always
     *                    be an instance of the same function class as the source function.
     */

    public void copyLocalData(ExtensionFunctionCall destination) {
        // default implementation does nothing
    }

    /**
     * Evaluate this function call at run-time
     *
     * @param context   The XPath dynamic evaluation context
     * @param arguments The values of the arguments to the function call. Each argument value (which is in general
     *                  a sequence) is supplied in the form of <code>Sequence</code>. Any required conversions
     *                  to the declared types of the arguments will already have been performed.
     *                  <p>If the argument is always a singleton, then the single item may be obtained by calling
     *                  <code>arguments[i].head()</code>.</p>
     *                  <p>The implementation is not obliged to read all the items in each <code>SequenceIterator</code>
     *                  if they are not required to compute the result; but if any <code>SequenceIterator</code> is not read
     *                  to completion, it is good practice to call its <code>close()</code> method.</p>
     * @return the results of the function as a <code>Sequence</code>.
     *         <p>The implementation is responsible for ensuring that the result is a valid instance of the declared
     *         result type. Saxon will check that this is the case if the {@link net.sf.saxon.lib.ExtensionFunctionDefinition#trustResultType()}
     *         method returns false, but it will never convert the supplied result value to the declared result type.</p>
     *         <p>The <code>Sequence</code> objects used for the arguments will often be instances of <code>LazySequence</code>,
     *         which means that the items in the sequence are computed lazily on demand. This means that any errors that occur
     *         while computing the sequence might not be thrown until the relevant item is actually read from the sequence.</p>
     *         <p>If the result is a single item, it can be returned directly, since single items all implement <code>Sequence</code>.
     *         For example a string can be returned as an instance of {@link net.sf.saxon.value.StringValue}, and a boolean as an instance
     *         of {@link net.sf.saxon.value.BooleanValue}.
     *         If the result is an empty sequence, the method should return {@link net.sf.saxon.value.EmptySequence#getInstance()}</p>
     * @throws XPathException if a dynamic error occurs during evaluation of the function. The Saxon run-time
     *                        code will add information about the error location.
     */

    @Override
    public abstract Sequence call(XPathContext context, Sequence[] arguments) throws XPathException;

    /**
     * Compute the effective boolean value of the result.
     * <p>Implementations can override this method but are not required to do so. If it is overridden,
     * the result must be consistent with the rules for calculating effective boolean value. The method
     * should be implemented in cases where computing the effective boolean value is significantly cheaper
     * than computing the full result.</p>
     *
     * @param context   The XPath dynamic evaluation context
     * @param arguments The values of the arguments to the function call. Each argument value (which is in general
     *                  a sequence) is supplied in the form of <code>Sequence</code>. Any required conversions
     *                  to the declared types of the arguments will already have been performed.
     *                  <p>If the argument is always a singleton, then the single item may be obtained by calling
     *                  <code>arguments[i].head()</code>.</p>
     *                  <p>The implementation is not obliged to read all the items in each <code>Sequence</code>
     *                  if they are not required to compute the result; but if any <code>Sequence</code> is not read
     *                  to completion, it is good practice to call <code>close()</code> on the iterator.</p>
     * @return the effective boolean value of the result
     * @throws XPathException if a dynamic error occurs during evaluation of the function. The Saxon run-time
     *                        code will add information about the error location.
     */

    public boolean effectiveBooleanValue(XPathContext context, Sequence[] arguments) throws XPathException {
        return ExpressionTool.effectiveBooleanValue(call(context, arguments).iterate());
    }

    /**
     * Get a streamable implementation of this extension function. This interface is provisional
     * and currently only really intended for internal use; it is subject to change. If the value
     * returned is non-null, then it must be an instance of com.saxonica.ee.stream.adjunct
     * @return the streaming implementation of the extension function
     */

    public Object getStreamingImplementation() {
        return null;
    }


}

