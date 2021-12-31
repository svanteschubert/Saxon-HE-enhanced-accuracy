////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;

import javax.xml.transform.SourceLocator;

/**
 * Abstract superclass for all instructions in the compiled stylesheet.
 * This represents a compiled instruction, and as such, the minimum information is
 * retained from the original stylesheet. <br>
 * Note: this class implements SourceLocator: that is, it can identify where in the stylesheet
 * the source instruction was located.
 */

public abstract class Instruction extends Expression implements TailCallReturner {

    /**
     * Constructor
     */

    public Instruction() {
    }
    
    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered. For instructions this is the process() method.
     */

    @Override
    public int getImplementationMethod() {
        return Expression.PROCESS_METHOD;
    }

    @Override
    public boolean isInstruction() {
        return true;
    }

    /**
     * Get the namecode of the instruction for use in diagnostics
     *
     * @return a code identifying the instruction: typically but not always
     *         the fingerprint of a name in the XSLT namespace
     */

    public int getInstructionNameCode() {
        return -1;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */

    @Override
    public String getExpressionName() {
        int code = getInstructionNameCode();
        if (code >= 0 & code < 1024) {
            return StandardNames.getDisplayName(code);
        } else {
            return super.getExpressionName();
        }
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return Type.ITEM_TYPE;
    }

    /**
     * Get the cardinality of the sequence returned by evaluating this instruction
     *
     * @return the static cardinality
     */

    @Override
    public int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    @Override
    public abstract Iterable<Operand> operands();

    /**
     * ProcessLeavingTail: called to do the real work of this instruction. This method
     * must be implemented in each subclass. The results of the instruction are written
     * to the current Receiver, which can be obtained via the Controller.
     *
     *
     * @param output the destination for the result
     * @param context The dynamic context of the transformation, giving access to the current node,
     *                the current variables, etc.
     * @return null if the instruction has completed execution; or a TailCall indicating
     *         a function call or template call that is delegated to the caller, to be made after the stack has
     *         been unwound so as to save stack space.
     */

    @Override
    public abstract TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException;

    /**
     * Process the instruction, without returning any tail calls
     *
     * @param output the destination for the result
     * @param context The dynamic context, giving access to the current node,
     */

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        try {
            TailCall tc = processLeavingTail(output, context);
            while (tc != null) {
                tc = tc.processLeavingTail();
            }
        } catch (XPathException err) {
            err.maybeSetFailingExpression(this);
            err.maybeSetContext(context);
            throw err;
        }
    }

    /**
     * Get a SourceLocator identifying the location of this instruction
     *
     * @return the location of this instruction in the source stylesheet or query
     */

    public SourceLocator getSourceLocator() {
        return getLocation();
    }

    /**
     * Construct an exception with diagnostic information. Note that this method
     * returns the exception, it does not throw it: that is up to the caller.
     *
     * @param loc     the location of the error
     * @param error   The exception containing information about the error
     * @param context The controller of the transformation
     * @return an exception based on the supplied exception, but with location information
     *         added relating to this instruction
     */

    protected static XPathException dynamicError(Location loc, XPathException error, XPathContext context) {
        if (error instanceof TerminationException) {
            return error;
        }
        error.maybeSetLocation(loc);
        error.maybeSetContext(context);
        return error;
    }

    /**
     * Assemble a ParameterSet. Method used by instructions that have xsl:with-param
     * children. This method is used for the non-tunnel parameters.
     *
     * @param context      the XPath dynamic context
     * @param actualParams the set of with-param parameters that specify tunnel="no"
     * @return a ParameterSet
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs evaluating one of the
     *          parameters
     */

    public static ParameterSet assembleParams(XPathContext context,
                                              WithParam[] actualParams)
            throws XPathException {
        if (actualParams == null || actualParams.length == 0) {
            return null;
        }
        ParameterSet params = new ParameterSet(actualParams.length);
        for (WithParam actualParam : actualParams) {
            params.put(actualParam.getVariableQName(),
                    actualParam.getSelectValue(context),
                    actualParam.isTypeChecked());
        }
        return params;
    }

    /**
     * Assemble a ParameterSet. Method used by instructions that have xsl:with-param
     * children. This method is used for the tunnel parameters.
     *
     * @param context      the XPath dynamic context
     * @param actualParams the set of with-param parameters that specify tunnel="yes"
     * @return a ParameterSet
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs evaluating one of the
     *          tunnel parameters
     */

    public static ParameterSet assembleTunnelParams(XPathContext context,
                                                    WithParam[] actualParams)
            throws XPathException {
        ParameterSet existingParams = context.getTunnelParameters();
        if (existingParams == null) {
            return assembleParams(context, actualParams);
        }
        if (actualParams == null || actualParams.length == 0) {
            return existingParams;
        }

        ParameterSet newParams = new ParameterSet(existingParams, actualParams.length);
        for (WithParam actualParam : actualParams) {
            newParams.put(actualParam.getVariableQName(),
                          actualParam.getSelectValue(context),
                          false);
        }
        return newParams;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */

    @Override
    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        if (alwaysCreatesNewNodes()) {
            p |= StaticProperty.ALL_NODES_NEWLY_CREATED;
        }
        if (mayCreateNewNodes()) {
            return p;
        } else {
            return p | StaticProperty.NO_NODES_NEWLY_CREATED;
        }
    }

    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     */
    @Override
    public int getNetCost() {
        return 20;
    }

    /**
     * Ask whether this instruction potentially creates and returns new nodes.
     * Returns true if some branches or dynamic conditions result in new nodes being created.
     * This implementation returns a default value of false
     *
     * @return true if the instruction creates new nodes under some input conditions
     * (or if it can't be proved that it doesn't)
     */

    public boolean mayCreateNewNodes() {
        return false;
    }

    /**
     * Ask whether it is guaranteed that every item in the result of this instruction
     * is a newly created node.
     *
     * @return true if result of the instruction is always either an empty sequence or
     * a sequence consisting entirely of newly created nodes
     */

    public boolean alwaysCreatesNewNodes() {
        return false;
    }

    /**
     * Helper method for {@link #mayCreateNewNodes} - returns true if any operand creates new nodes
     * @return true if any operand creates new nodes
     */

    protected final boolean someOperandCreatesNewNodes() {
        for (Operand o : operands()) {
            Expression child = o.getChildExpression();
            int props = child.getSpecialProperties();
            if ((props & StaticProperty.NO_NODES_NEWLY_CREATED) == 0) {
                return true;
            }
        }
        return false;
    }


    /**
     * Evaluate an expression as a single item. This always returns either a single Item or
     * null (denoting the empty sequence). No conversion is done. This method should not be
     * used unless the static type of the expression is a subtype of "item" or "item?": that is,
     * it should not be called if the expression may return a sequence. There is no guarantee that
     * this condition will be detected.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the node or atomic value that results from evaluating the
     *         expression; or null to indicate that the result is an empty
     *         sequence
     * @throws XPathException if any dynamic error occurs evaluating the
     *                        expression
     */

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        int m = getImplementationMethod();
        if ((m & EVALUATE_METHOD) != 0) {
            throw new AssertionError(
                    "evaluateItem() is not implemented in the subclass " + getClass());
        } else if ((m & ITERATE_METHOD) != 0) {
            return iterate(context).next();
        } else {
            return ExpressionTool.getItemFromProcessMethod(this, context);
        }
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws XPathException if any dynamic error occurs evaluating the
     *                        expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        int m = getImplementationMethod();
        if ((m & EVALUATE_METHOD) != 0) {
            Item item = evaluateItem(context);
            if (item == null) {
                return EmptyIterator.emptyIterator();
            } else {
                return SingletonIterator.makeIterator(item);
            }
        } else if ((m & ITERATE_METHOD) != 0) {
            throw new AssertionError("iterate() is not implemented in the subclass " + getClass());
        } else {
            return ExpressionTool.getIteratorFromProcessMethod(this, context);
        }
    }

    /**
     * Evaluate an expression as a String. This function must only be called in contexts
     * where it is known that the expression will return a single string (or where an empty sequence
     * is to be treated as a zero-length string). Implementations should not attempt to convert
     * the result to a string, other than converting () to "". This method is used mainly to
     * evaluate expressions produced by compiling an attribute value template.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the value of the expression, evaluated in the current context.
     *         The expression must return a string or (); if the value of the
     *         expression is (), this method returns "".
     * @throws net.sf.saxon.trans.XPathException
     *                                      if any dynamic error occurs evaluating the
     *                                      expression
     * @throws java.lang.ClassCastException if the result type of the
     *                                      expression is not xs:string?
     */

    @Override
    public final CharSequence evaluateAsString(XPathContext context) throws XPathException {
        Item item = evaluateItem(context);
        if (item == null) {
            return "";
        } else {
            return item.getStringValue();
        }
    }

    /**
     * Establish whether this is an XSLT instruction or an XQuery instruction
     * (used to produce appropriate diagnostics)
     *
     * @return true for XSLT, false for XQuery
     */
    public boolean isXSLT() {
        return getPackageData().isXSLT();
    }


}


