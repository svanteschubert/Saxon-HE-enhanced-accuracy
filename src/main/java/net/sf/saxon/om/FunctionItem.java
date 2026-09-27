////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.ContextOriginator;
import net.sf.saxon.expr.OperandRole;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicMatcher;
import net.sf.saxon.functions.DeepEqual;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;

/**
 * XDM 3.0 introduces a third kind of item, beyond nodes and atomic values: the function. Functions
 * implement this interface.
 *
 * @since 12.0; previously named <code>Function</code>.
 */

public interface FunctionItem extends Item, Callable, GroundedValue {

    // TODO: Currently SystemFunction and UserFunction implement this interface, despite
    //  the fact that they support an arity range, which dynamic functions don't allow. In effect
    //  they act as function items for the function at the top end of the arity range. Need
    //  to better reflect the 4.0 data model when it's finalised.

    /**
     * Ask whether this function item is a map
     * @return true if this function item is a map, otherwise false
     */

    boolean isMap();

    /**
     * Ask whether this function item is an array
     * @return true if this function item is an array, otherwise false
     */

    boolean isArray();

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */

    FunctionItemType getFunctionItemType();

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */

    StructuredQName getFunctionName();

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */

    int getArity();

    /**
     * Ask whether the function is sequence-variadic (accepts a variable number of
     * arguments)
     * @return true if the function is sequence-variadic; default is false
     */

    default boolean isSequenceVariadic() {
        return false;
    }

    /**
     * Get the roles of the arguments, for the purposes of streaming
     * @return an array of OperandRole objects, one for each argument
     */

    OperandRole[] getOperandRoles();

    /**
     * Get the function annotations (as defined in XQuery). Returns an empty
     * list if there are no function annotations.
     * @return the function annotations
     */

    AnnotationList getAnnotations();

    /**
     * Prepare an XPathContext object for evaluating the function
     *
     * @param callingContext the XPathContext of the function calling expression
     * @param originator identifies the location of the caller for diagnostics
     * @return a suitable context for evaluating the function (which may or may
     * not be the same as the caller's context)
     */

    XPathContext makeNewContext(XPathContext callingContext, ContextOriginator originator);

    /**
     * Test whether this FunctionItem is deep-equal to another function item,
     * under the rules of the deep-equal function
     *
     * @param other    the other function item
     * @param context  the dynamic evaluation context
     * @param comparer the object to perform the comparison
     * @param flags    options for how the comparison is performed
     * @return true if the two function items are deep-equal
     * @throws XPathException if the comparison cannot be performed
     */

    boolean deepEquals(FunctionItem other, XPathContext context, AtomicMatcher comparer, int flags) throws XPathException;

    /**
     * Test whether this FunctionItem is deep-equal to another function item,
     * under the rules of the deep-equal function in XPath 4.0
     *
     * @param other    the other function item
     * @param context  the dynamic evaluation context
     * @param options  the options supplied to the deep-equal function
     * @return true if the two function items are deep-equal
     * @throws XPathException if the comparison cannot be performed
     */

    boolean deepEqual40(FunctionItem other, XPathContext context, DeepEqual.DeepEqualOptions options) throws XPathException;

    /**
     * Get a description of this function for use in error messages. For named functions, the description
     * is the function name (as a lexical QName). For others, it might be, for example, "inline function",
     * or "partially-applied ends-with function".
     * @return a description of the function for use in error messages
     */

    String getDescription();

    /**
     * Output information about this function item to the diagnostic explain() output
     * @param out the destination for the information
     * @throws XPathException if things go wrong
     */

    void export(ExpressionPresenter out) throws XPathException;

    /**
     * Ask if the function can be trusted to return a result of the correct type
     * @return true if the implementation can be trusted
     */

    boolean isTrustedResultType();

    /**
     * Provide a short string showing the contents of the item, suitable
     * for use in error messages
     *
     * @return a depiction of the item suitable for use in error messages
     */
    @Override
    default String toShortString() {
        return getDescription();
    }

    /**
     * Get the genre of this item
     *
     * @return the genre: specifically, {@link Genre#FUNCTION}. Overridden for maps and arrays.
     */
    @Override
    default Genre getGenre() {
        return Genre.FUNCTION;
    }
    
}

