////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

import java.util.List;

/**
 * An object derived from a xsl:with-param element in the stylesheet. <br>
 */

public class WithParam  {

    public static WithParam[] EMPTY_ARRAY = new WithParam[0];

    private Operand selectOp;
    //private int parameterId;
    private boolean typeChecked = false;
    private int slotNumber = -1;
    private SequenceType requiredType;
    private StructuredQName variableQName;
    private Evaluator evaluator = null;

    public WithParam() {
    }

    /**
     * Set the expression to which this variable is bound
     * @param parent the parent expression
     * @param select the initializing expression
     */

    public void setSelectExpression(Expression parent, Expression select) {
        selectOp = new Operand(parent, select, OperandRole.NAVIGATE);
    }

    /**
     * Get the select operand
     */

    public Operand getSelectOperand() {
        return selectOp;
    }

    /**
     * Get the expression to which this variable is bound
     *
     * @return the initializing expression
     */

    public Expression getSelectExpression() {
        return selectOp.getChildExpression();
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

    public SequenceType getRequiredType() {
        return requiredType;
    }

    /**
     * Get the slot number allocated to this variable. This is used only for xsl:iterate and xsl:evaluate;
     * template parameters are identified by name, not by slot number.
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

    public StructuredQName getVariableQName() {
        return variableQName;
    }

    /**
     * Say whether this parameter will have been typechecked by the caller to ensure it satisfies
     * the required type, in which case the callee need not do a dynamic type check
     *
     * @param checked true if the caller has done static type checking against the required type
     */

    public void setTypeChecked(boolean checked) {
        typeChecked = checked;
    }


    public int getInstructionNameCode() {
        return StandardNames.XSL_WITH_PARAM;
    }

    /**
     * Static method to simplify a set of with-param elements
     *
     * @param params  the set of parameters to be simplified
     * @throws XPathException if a static error is found
     */

    public static void simplify(WithParam[] params) throws XPathException {
        if (params != null) {
            for (WithParam param : params) {
                param.selectOp.setChildExpression(param.selectOp.getChildExpression().simplify());
            }
        }
    }

    /**
     * Static method to typecheck a set of with-param elements
     *
     * @param params          the set of parameters to be checked
     * @param visitor         the expression visitor
     * @param contextItemType static information about the context item type and existence
     * @throws XPathException if a static error is found
     */


    public static void typeCheck(WithParam[] params, ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        if (params != null) {
            for (WithParam param : params) {
                param.selectOp.typeCheck(visitor, contextItemType);
            }
        }
    }

    /**
     * Static method to optimize a set of with-param elements
     *
     * @param params          the set of parameters to be optimized
     * @param visitor         the expression visitor
     * @param contextItemType static information about the context item type and existence
     * @throws XPathException if a static error is found
     */

    public static void optimize(ExpressionVisitor visitor, WithParam[] params, ContextItemStaticInfo contextItemType) throws XPathException {
        if (params != null) {
            for (WithParam param : params) {
                param.selectOp.optimize(visitor, contextItemType);
                param.computeEvaluator();
            }
        }
    }

    /**
     * Get the evaluation mode of the variable
     *
     * @return the evaluation mode (a constant in {@link EvaluationMode}
     */

    public EvaluationMode getEvaluationMode() {
        if (evaluator == null) {
            computeEvaluator();
        }
        return evaluator.getEvaluationMode();
    }



    private void computeEvaluator() {
        evaluator = ExpressionTool.lazyEvaluator(selectOp.getChildExpression(), true);
    }


    /**
     * Static method to copy a set of parameters
     * @param parent the new parent expression
     * @param params the parameters to be copied
     * @return the resulting copy
     */

    public static WithParam[] copy(Expression parent, WithParam[] params, RebindingMap rebindings) {
        if (params == null) {
            return null;
        }
        WithParam[] result = new WithParam[params.length];
        for (int i = 0; i < params.length; i++) {
            result[i] = new WithParam();
            //result[i].parameterId = params[i].parameterId;
            result[i].slotNumber = params[i].slotNumber;
            result[i].typeChecked = params[i].typeChecked;
            result[i].selectOp = new Operand(parent, params[i].selectOp.getChildExpression().copy(rebindings), OperandRole.NAVIGATE);
            result[i].requiredType = params[i].requiredType;
            result[i].variableQName = params[i].variableQName;
        }
        return result;
    }

    /**
     * Static method to gather the XPath expressions used in an array of WithParam parameters (add them to the supplied list)
     *
     * @param parent the containing expression
     * @param params the set of with-param elements to be searched
     * @param list   the list to which the subexpressions will be added
     */

    public static void gatherOperands(Expression parent, WithParam[] params, List<Operand> list) {
        if (params != null) {
            for (WithParam param : params) {
                list.add(param.selectOp);
            }
        }
    }


    /**
     * Static method to export a set of parameters
     *
     * @param params the set of parameters to be exported
     * @param out    the destination for the output
     * @param tunnel true if these are tunnel parameters
     */

    public static void exportParameters(WithParam[] params, ExpressionPresenter out, boolean tunnel) throws XPathException{
        if (params != null) {
            for (WithParam param : params) {
                out.startElement("withParam");
                out.emitAttribute("name", param.variableQName);
                String flags = "";
                if (tunnel) {
                    flags += "t";
                }
                if (param.isTypeChecked()) {
                    flags += "c";
                }
                if (!flags.isEmpty()) {
                    out.emitAttribute("flags", flags);
                }
                ExpressionPresenter.ExportOptions options = (ExpressionPresenter.ExportOptions) out.getOptions();
                if (param.getRequiredType() != SequenceType.ANY_SEQUENCE) {
                    out.emitAttribute("as", param.getRequiredType().toAlphaCode());
                }
                if (param.getSlotNumber() != -1) {
                    out.emitAttribute("slot", param.getSlotNumber() + "");
                }
                param.selectOp.getChildExpression().export(out);
                out.endElement();
            }
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
        // There is a select attribute: do a lazy evaluation of the expression,
        // which will already contain any code to force conversion to the required type.
        if (evaluator == null) {
            computeEvaluator();
        }
        int savedOutputState = context.getTemporaryOutputState();
        context.setTemporaryOutputState(StandardNames.XSL_WITH_PARAM);
        Sequence result = evaluator.evaluate(selectOp.getChildExpression(), context);
        context.setTemporaryOutputState(savedOutputState);
        return result;
    }


    /**
     * Ask whether static type checking has been done
     *
     * @return true if the caller has done static type checking against the type required by the callee
     */

    public boolean isTypeChecked() {
        return typeChecked;
    }
}

