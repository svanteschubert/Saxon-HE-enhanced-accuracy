////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

/**
 * The compiled form of an xsl:param element within a template in an XSLT stylesheet.
 * <p>The xsl:param element in XSLT has mandatory attribute name and optional attribute select. It can also
 * be specified as required="yes" or required="no".</p>
 * <p>This is used only for parameters to XSLT templates. For function calls, the caller of the function
 * places supplied arguments onto the callee's stackframe and the callee does not need to do anything.
 * Global parameters (XQuery external variables) are handled using {@link GlobalParam}.</p>
 * <p>The LocalParam class is also used to represent parameters with the saxon:iterate instruction</p>
 *
 * <p>Changed in Saxon 9.8 to combine the previously-separate LocalParamSetter and LocalParam classes into one</p>
 */

public final class LocalParam extends Instruction implements LocalBinding {

    private Operand conversionOp = null;
    private Evaluator conversionEvaluator = null;

    private static final int REQUIRED = 4;
    private static final int TUNNEL = 8;
    private static final int IMPLICITLY_REQUIRED = 16;  // a parameter that is required because the fallback
    // value is not a valid instance of the type.

    private byte properties = 0;
    private Operand selectOp = null;
    protected StructuredQName variableQName;
    private SequenceType requiredType;
    protected int slotNumber = -999;
    protected int referenceCount = 10;
    protected Evaluator evaluator = null;



    /**
     * Set the expression to which this variable is bound
     *
     * @param select the initializing expression
     */

    public void setSelectExpression(Expression select) {
        if (select != null) {
            if (selectOp == null) {
                selectOp = new Operand(this, select, OperandRole.NAVIGATE);
            } else {
                selectOp.setChildExpression(select);
            }
        } else {
            selectOp = null;
        }
        evaluator = null;
    }

    /**
     * Get the expression to which this variable is bound
     *
     * @return the initializing expression
     */

    public Expression getSelectExpression() {
        return selectOp == null ? null : selectOp.getChildExpression();
    }

    /**
     * Set the required type of this variable
     *
     * @param required the required type
     */

    public void setRequiredType(SequenceType required) {
        requiredType = required;
    }

    /**
     * Get the required type of this variable
     *
     * @return the required type
     */

    @Override
    public SequenceType getRequiredType() {
        return requiredType;
    }

    /**
     * Indicate that this variable represents a required parameter
     *
     * @param requiredParam true if this is a required parameter
     */

    public void setRequiredParam(boolean requiredParam) {
        if (requiredParam) {
            properties |= REQUIRED;
        } else {
            properties &= ~REQUIRED;
        }
    }

    /**
     * Indicate that this variable represents a parameter that is implicitly required (because there is no
     * usable default value)
     *
     * @param requiredParam true if this is an implicitly required parameter
     */

    public void setImplicitlyRequiredParam(boolean requiredParam) {
        if (requiredParam) {
            properties |= IMPLICITLY_REQUIRED;
        } else {
            properties &= ~IMPLICITLY_REQUIRED;
        }
    }

    /**
     * Indicate whether this variable represents a tunnel parameter
     *
     * @param tunnel true if this is a tunnel parameter
     */

    public void setTunnel(boolean tunnel) {
        if (tunnel) {
            properties |= TUNNEL;
        } else {
            properties &= ~TUNNEL;
        }
    }

    /**
     * Set the nominal number of references to this variable
     *
     * @param refCount the nominal number of references
     */

    public void setReferenceCount(int refCount) {
        referenceCount = refCount;
    }

//    /**
//     * Get the evaluation mode of the variable
//     *
//     * @return the evaluation mode (a constant in {@link ExpressionTool}
//     */
//
//    public int getEvaluationMode() {
//        if (evaluationMode == ExpressionTool.UNDECIDED) {
//            if (referenceCount == FilterExpression.FILTERED) {
//                evaluationMode = ExpressionTool.MAKE_INDEXED_VARIABLE;
//            } else {
//                evaluationMode = ExpressionTool.lazyEvaluationMode(getSelectExpression());
//            }
//        }
//        return evaluationMode;
//    }

    /**
     * Get the cardinality of the result of this instruction. An xsl:variable instruction returns nothing, so the
     * type is empty.
     *
     * @return the empty cardinality.
     */

    @Override
    public int getCardinality() {
        return StaticProperty.EMPTY;
    }

    @Override
    public boolean isAssignable() {
        return false;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    /**
     * If this is a local variable held on the local stack frame, return the corresponding slot number.
     * In other cases, return -1.
     */

    @Override
    public int getLocalSlotNumber() {
        return slotNumber;
    }

    /**
     * Ask whether this variable represents a required parameter
     *
     * @return true if this is a required parameter
     */

    public final boolean isRequiredParam() {
        return (properties & REQUIRED) != 0;
    }

    /**
     * Ask whether this variable represents a parameter that is implicitly required, because there is no usable
     * default value
     *
     * @return true if this variable is an implicitly required parameter
     */

    public final boolean isImplicitlyRequiredParam() {
        return (properties & IMPLICITLY_REQUIRED) != 0;
    }

    /**
     * Ask whether this variable represents a tunnel parameter
     *
     * @return true if this is a tunnel parameter
     */

    public final boolean isTunnelParam() {
        return (properties & TUNNEL) != 0;
    }


    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Expression e2 = super.typeCheck(visitor, contextItemType);
        if (e2 != this) {
            return e2;
        }
        checkAgainstRequiredType(visitor);
        return this;
    }

    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Expression e2 = super.optimize(visitor, contextItemType);
        if (e2 != this) {
            return e2;
        }
//        if (selectOp != null) {
//            computeEvaluationMode();
//        }
        return this;
    }

    public void computeEvaluationMode() {
        if (getSelectExpression() != null) {
            if (referenceCount == FilterExpression.FILTERED) {
                evaluator = Evaluator.MAKE_INDEXED_VARIABLE;
            } else {
                evaluator = ExpressionTool.lazyEvaluator(getSelectExpression(), referenceCount > 1);
            }
        }
    }


    /**
     * Copy an expression. This makes a deep copy.
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *                   that is used to update the bindings held in any
     *                   local variable references that are copied.
     * @return the copy of the original expression
     */

    @Override
    public LocalParam copy(RebindingMap rebindings) {
        LocalParam p2 = new LocalParam();
        if (conversionOp != null) {
            assert getConversion() != null;
            p2.setConversion(getConversion().copy(rebindings));
        }
        p2.conversionEvaluator = conversionEvaluator;
        p2.properties = properties;
        if (selectOp != null) {
            assert getSelectExpression() != null;
            p2.setSelectExpression(getSelectExpression().copy(rebindings));
        }
        p2.variableQName = variableQName;
        p2.requiredType = requiredType;
        p2.slotNumber = slotNumber;
        p2.referenceCount = referenceCount;
        p2.evaluator = evaluator;
        return p2;
    }

    @Override
    public void addReference(VariableReference ref, boolean isLoopingReference) {

    }

    /**
     * Check the select expression against the required type.
     *
     * @param visitor an expression visitor
     * @throws XPathException if the check fails
     */

    public void checkAgainstRequiredType(ExpressionVisitor visitor)
            throws XPathException {
        // Note, in some cases we are doing this twice.
        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.VARIABLE, variableQName.getDisplayName(), 0);
        //role.setSourceLocator(this);
        SequenceType r = requiredType;
        Expression select = getSelectExpression();
        if (r != null && select != null) {
            // check that the expression is consistent with the required type
            select = visitor.getConfiguration().getTypeChecker(false).staticTypeCheck(select, requiredType, role, visitor);
        }
    }

    /**
     * Evaluate the variable. That is,
     * get the value of the select expression if present or the content
     * of the element otherwise, either as a tree or as a sequence
     *
     * @param context the XPath dynamic context
     * @return the result of evaluating the variable
     * @throws net.sf.saxon.trans.XPathException
     *          if evaluation of the select expression fails
     *          with a dynamic error
     */

    public Sequence getSelectValue(XPathContext context) throws XPathException {
        Expression select = getSelectExpression();
        if (select == null) {
            throw new AssertionError("Internal error: No select expression");
            // The value of the variable is a sequence of nodes and/or atomic values
        } else if (select instanceof Literal) {
            // fast path for common case
            return ((Literal)select).getValue();
        } else {
            // There is a select attribute: do a lazy evaluation of the expression,
            // which will already contain any code to force conversion to the required type.
            int savedOutputState = context.getTemporaryOutputState();
            context.setTemporaryOutputState(StandardNames.XSL_WITH_PARAM);
            Sequence result;
            Evaluator eval = evaluator == null ? Evaluator.EAGER_SEQUENCE : evaluator;
            result = eval.evaluate(select, context);
            context.setTemporaryOutputState(savedOutputState);
            return result;
        }
    }

    /**
     * Get the slot number allocated to this variable
     *
     * @return the slot number, that is the position allocated to the variable on its stack frame
     */

    public int getSlotNumber() {
        return slotNumber;
    }

    /**
     * Set the slot number of this variable
     *
     * @param s the slot number, that is, the position allocated to this variable on its stack frame
     */

    public void setSlotNumber(int s) {
        slotNumber = s;
    }

    /**
     * Set the name of the variable
     *
     * @param s the name of the variable (a QName)
     */

    public void setVariableQName(StructuredQName s) {
        variableQName = s;
    }

    /**
     * Get the name of this variable
     *
     * @return the name of this variable (a QName)
     */

    @Override
    public StructuredQName getVariableQName() {
        return variableQName;
    }

    /**
     * Define a conversion that is to be applied to the supplied parameter value.
     *
     * @param convertor The expression to be applied. This performs type checking,
     *                  and the basic conversions implied by function calling rules, for example
     *                  numeric promotion, atomization, and conversion of untyped atomic values to
     *                  a required type. The conversion uses the actual parameter value as input,
     *                  referencing it using a VariableReference. The argument can be null to indicate
     *                  that no conversion is required.
     */
    public void setConversion(Expression convertor) {
        if (convertor != null) {
            if (conversionOp == null) {
                conversionOp = new Operand(this, convertor, OperandRole.SINGLE_ATOMIC);
            }
            conversionEvaluator = ExpressionTool.eagerEvaluator(convertor);
        } else {
            conversionOp = null;
        }
    }

    /**
     * Get the conversion expression
     *
     * @return the expression used to convert the value to the required type,
     *         or null if there is none
     */

    /*@Nullable*/
    public Expression getConversion() {
        return conversionOp == null ? null : conversionOp.getChildExpression();
    }

    public EvaluationMode getConversionEvaluationMode() {
        return conversionEvaluator.getEvaluationMode();
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     * @return the integer name code
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_PARAM;
    }


    /**
     * Get all the XPath expressions associated with this instruction
     * (in XSLT terms, the expression present on attributes of the instruction,
     * as distinct from the child instructions in a sequence construction)
     * @return an iterator over the subexpressions
     */

    @Override
    public Iterable<Operand> operands() {
        return operandSparseList(selectOp, conversionOp);
    }

    /**
     * Process the local parameter declaration
     *
     *
     * @param output the destination for the result
     * @param context the dynamic context
     * @return either null if processing is complete, or a tailcall if one is left outstanding
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs in the evaluation
     */

    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        int wasSupplied = context.useLocalParameter(variableQName, slotNumber, isTunnelParam());
        switch (wasSupplied) {
            case ParameterSet.SUPPLIED_AND_CHECKED:
                // No action needed
                break;

            case ParameterSet.SUPPLIED:
                // if a parameter was supplied by the caller, with no type-checking by the caller,
                // then we may need to convert it to the type required
                if (conversionOp != null) {
                    context.setLocalVariable(slotNumber,
                            conversionEvaluator.evaluate(getConversion(), context));
                    // We do an eager evaluation here for safety, because the result of the
                    // type conversion overwrites the slot where the actual supplied parameter
                    // is contained.
                }
                break;

            // don't evaluate the default if a value has been supplied or if it has already been
            // evaluated by virtue of a forwards reference

            case ParameterSet.NOT_SUPPLIED:
                if (isRequiredParam() || isImplicitlyRequiredParam()) {
                    String name = "$" + getVariableQName().getDisplayName();
                    int suppliedAsTunnel = context.useLocalParameter(variableQName, slotNumber, !isTunnelParam());
                    String message = "No value supplied for required parameter " + name;
                    if (isImplicitlyRequiredParam()) {
                        message += ". A value is required because " +
                                "the default value is not a valid instance of the required type";
                    }
                    if (suppliedAsTunnel != ParameterSet.NOT_SUPPLIED) {
                        if (isTunnelParam()) {
                            message += ". A non-tunnel parameter with this name was supplied, but a tunnel parameter is required";
                        } else {
                            message += ". A tunnel parameter with this name was supplied, but a non-tunnel parameter is required";
                        }
                    }
                    XPathException e = new XPathException(message);
                    e.setXPathContext(context);
                    e.setErrorCode("XTDE0700");
                    throw e;
                }
                context.setLocalVariable(slotNumber, getSelectValue(context));
        }
        return null;
    }

    /**
     * If the variable is bound to an integer, get the minimum and maximum possible values.
     * Return null if unknown or not applicable
     */

    @Override
    public IntegerValue[] getIntegerBoundsForVariable() {
        return null;
    }

    /**
     * Evaluate the variable
     */

    @Override
    public Sequence evaluateVariable(XPathContext c) {
        return c.evaluateLocalVariable(slotNumber);
    }


    /**
     * Check if parameter is compatible with another
     *
     * @param other - the LocalParam object to compare
     * @return result of the compatibility check
     */
    public boolean isCompatible(LocalParam other) {
        return getVariableQName().equals(other.getVariableQName()) &&
                getRequiredType().equals(other.getRequiredType()) &&
                isTunnelParam() == other.isTunnelParam();
    }

    /**
     * Ask whether the expression can be lifted out of a loop, assuming it has no dependencies
     * on the controlling variable/focus of the loop
     * @param forStreaming true if compiling streamable code
     */
    @Override
    public boolean isLiftable(boolean forStreaming) {
        return false;
    }

    /**
     * Ask whether this expression is, or contains, the binding of a given variable
     *
     * @param binding the variable binding
     * @return true if this expression is the variable binding (for example a ForExpression
     * or LetExpression) or if it is a FLWOR expression that binds the variable in one of its
     * clauses.
     */
    @Override
    public boolean hasVariableBinding(Binding binding) {
        return this == binding;
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction
     */
    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return ErrorType.getInstance();
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

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */
    @Override
    public int computeSpecialProperties() {
        return StaticProperty.HAS_SIDE_EFFECTS;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns a default value of false
     *
     * @return true if the instruction creates new nodes (or if it can't be proved that it doesn't)
     */
    @Override
    public boolean mayCreateNewNodes() {
        return false;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "param";
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        return "$" + getVariableQName().getDisplayName();
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("param", this);
        out.emitAttribute("name", getVariableQName());
        out.emitAttribute("slot", "" + getSlotNumber());
        String flags = getFlags();
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        ExpressionPresenter.ExportOptions options = (ExpressionPresenter.ExportOptions) out.getOptions();
        if (getRequiredType() != SequenceType.ANY_SEQUENCE) {
            out.emitAttribute("as", getRequiredType().toAlphaCode());
        }
        if (getSelectExpression() != null) {
            out.setChildRole("select");
            getSelectExpression().export(out);
        }
        Expression conversion = getConversion();
        if (conversion != null) {
            out.setChildRole("conversion");
            conversion.export(out);
        }
        out.endElement();
    }

    private String getFlags() {
        String flags = "";
        if (isTunnelParam()) {
            flags += "t";
        }
        if (isRequiredParam()) {
            flags += "r";
        }
        if (isImplicitlyRequiredParam()) {
            flags += "i";
        }
        return flags;
    }

    /**
     * Say that the bound value has the potential to be indexed
     */
    @Override
    public void setIndexedVariable() {}

    /**
     * Ask whether the binding is to be indexed
     *
     * @return true if the variable value can be indexed
     */
    @Override
    public boolean isIndexedVariable() {
        return false;
    }
}

