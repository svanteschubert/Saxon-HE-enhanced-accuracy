////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.xpath;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.PathMap;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.EmptySequence;

import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;
import java.util.ArrayList;
import java.util.List;


/**
 * This class is an expression that calls an external function supplied using the
 * JAXP XPathFunction interface
 */

public class XPathFunctionCall extends FunctionCall implements Callable {

    private StructuredQName name;
    private XPathFunction function;

    /**
     * Default constructor
     */

    public XPathFunctionCall(StructuredQName name, XPathFunction function) {
        this.function = function;
    }

    /**
     * Get the qualified name of the function being called
     *
     * @return the qualified name
     */
    @Override
    public StructuredQName getFunctionName() {
        return name;
    }

    /**
     * Get the target function to be called
     *
     * @param context the dynamic evaluation context
     * @return always null
     */
    @Override
    public Function getTargetFunction(XPathContext context) {
        return null;
    }

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the external function might have side-effects and might use the context)
     *
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    @Override
    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }

    /**
     * Determine which aspects of the context the expression depends on. XPath external
     * functions are given no access to context information so they cannot have any
     * dependencies on it.
     */

    @Override
    public int getIntrinsicDependencies() {
        return 0;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        return new XPathFunctionCall(name, function);
    }


    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet
     * @return the pathMapNode representing the focus established by this expression, in the case where this
     *         expression is the first operand of a path expression or filter expression. For an expression that does
     *         navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     *         expressions, it is the same as the input pathMapNode.
     */

    /*@NotNull*/
    @Override
    public PathMap.PathMapNodeSet addToPathMap(/*@NotNull*/ PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        return addExternalFunctionCallToPathMap(pathMap, pathMapNodeSet);
    }

    /**
     * Evaluate the function. <br>
     *
     * @param context The context in which the function is to be evaluated
     * @return a Value representing the result of the function.
     * @throws XPathException if the function cannot be evaluated.
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(/*@NotNull*/ XPathContext context) throws XPathException {
        Sequence[] argValues = new Sequence[getArity()];
        for (int i = 0; i < argValues.length; i++) {
            argValues[i] = SequenceTool.toLazySequence(getArg(i).iterate(context));
        }
        return call(context, argValues).iterate();
    }


    /**
     * Call an extension function previously identified using the bind() method. A subclass
     * can override this method.
     *
     * @param context   The XPath dynamic context
     * @param argValues The values of the arguments
     * @return The value returned by the extension function
     */

    /*@Nullable*/
    @Override
    public Sequence call(/*@NotNull*/ XPathContext context, Sequence[] argValues /*@NotNull*/) throws XPathException {
        // An argument is supplied to the extension function as a List, unless it is a singleton.
        // The items within the list are converted to the "natural Java representation", for example
        // a double is passed as a Double, a string as a String.
        List<Object> convertedArgs = new ArrayList<>(argValues.length);
        Configuration config = context.getConfiguration();
        for (Sequence argValue : argValues) {
            List<Object> target = new ArrayList<>();
            argValue.iterate().forEachOrFail(item -> {
                PJConverter converter = PJConverter.allocate(
                        config, Type.getItemType(item, config.getTypeHierarchy()), StaticProperty.ALLOWS_ONE, Object.class);
                target.add(converter.convert(item, Object.class, context));
            });
            if (target.size() == 1) {
                convertedArgs.add(target.get(0));
            } else {
                convertedArgs.add(target);
            }
        }

        try {
            Object result = function.evaluate(convertedArgs);
            if (result == null) {
                return EmptySequence.getInstance();
            }
            JPConverter converter = JPConverter.allocate(result.getClass(), null, config);
            return converter.convert(result, context);
        } catch (XPathFunctionException e) {
            throw new XPathException(e);
        }
    }

    /**
     * Determine the data type of the expression, if possible. All expressions return
     * sequences, in general; this method determines the type of the items within the
     * sequence, assuming that (a) this is known in advance, and (b) it is the same for
     * all items in the sequence.
     * <p>This method will always return a result, though it may be the best approximation
     * that is available at the time.</p>
     *
     * @return the item type
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return Type.ITEM_TYPE;
    }

    /**
     * Determine the cardinality of the result
     *
     * @return ZERO_OR_MORE (we don't know)
     */
    @Override
    public int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }



}

