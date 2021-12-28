////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.lib.StandardDiagnostics;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;

/**
 * Supplied parameter reference: this is an internal expression used to refer to
 * the value of the n'th parameter supplied on a template call or a call to an inline function.
 * It is used within a type-checking expression designed to check the consistency
 * of the supplied value with the required type. This type checking is all done
 * at run-time, because the binding of apply-templates to actual template rules
 * is entirely dynamic.
 */

public class SuppliedParameterReference extends Expression {

    int slotNumber;
    SequenceType type;

    /**
     * Constructor
     *
     * @param slot identifies this parameter. The value -1 indicates that the value is to be obtained
     *             from the dynamic stack held in the context object.
     */

    public SuppliedParameterReference(int slot) {
        slotNumber = slot;
    }

    /**
     * Get the slot number
     *
     * @return the slot number
     */

    public int getSlotNumber() {
        return slotNumber;
    }

    /**
     * Set the type of the supplied value if known
     *
     * @param type of the supplied value
     */

    public void setSuppliedType(SequenceType type) {
        this.type = type;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        return this;
    }

    /**
     * Determine the data type of the expression, if possible.
     *
     * @return Type.ITEM, because we don't know the type of the supplied value
     *         in advance.
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        if (type != null) {
            return type.getPrimaryType();
        } else {
            return AnyItemType.getInstance();
        }
    }

    /**
     * Determine the intrinsic dependencies of an expression, that is, those which are not derived
     * from the dependencies of its subexpressions. For example, position() has an intrinsic dependency
     * on the context position, while (position()+1) does not. The default implementation
     * of the method returns 0, indicating "no dependencies".
     *
     * @return a set of bit-significant flags identifying the "intrinsic"
     *         dependencies. The flags are documented in class net.sf.saxon.value.StaticProperty
     */

    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_LOCAL_VARIABLES;
    }

    /**
     * Get the static cardinality
     *
     * @return ZERO_OR_MORE, unless we know the type of the supplied value
     *         in advance.
     */

    @Override
    public int computeCardinality() {
        if (type != null) {
            return type.getCardinality();
        } else {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        }
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
        SuppliedParameterReference exp = new SuppliedParameterReference(slotNumber);
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */
    @Override
    public int getImplementationMethod() {
        return EVALUATE_METHOD | ITERATE_METHOD;
    }

    /**
     * Get the value of this expression in a given context.
     *
     * @param c the XPathContext which contains the relevant variable bindings
     * @return the value of the variable, if it is defined
     */

    public Sequence evaluateVariable(XPathContext c) {
        if (slotNumber == -1) {
            return c.getStackFrame().popDynamicValue();
        }
        try {
            return c.evaluateLocalVariable(slotNumber);
        } catch (AssertionError e) {
            new StandardDiagnostics().printStackTrace(c, c.getConfiguration().getLogger(), 2);
            throw new AssertionError(e.getMessage() + ". No value has been set for parameter " + slotNumber);
        }
    }

    /**
     * Get the value of this expression in a given context.
     *
     * @param context the XPathContext which contains the relevant variable bindings
     * @return the value of the variable, if it is defined
     * @throws XPathException if the variable is undefined
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return evaluateVariable(context).iterate();
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
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        return evaluateVariable(context).head();
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "supplied";
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("supplied", this);
        destination.emitAttribute("slot", slotNumber + "");
        destination.endElement();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        return "suppliedParam(" + slotNumber + ")";
    }
}
