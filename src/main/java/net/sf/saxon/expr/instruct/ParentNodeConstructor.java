////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Schema;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.UType;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.SequenceType;

/**
 * An abstract class to act as a common parent for instructions that create element nodes
 * and document nodes.
 */

public abstract class ParentNodeConstructor extends Instruction
        implements ValidatingInstruction, InstructionWithComplexContent {

    private final static OperandRole SAME_FOCUS_CONTENT =
            new OperandRole(0, OperandUsage.ABSORPTION, SequenceType.ANY_SEQUENCE);

    protected Operand contentOp;
    private ParseOptions validationOptions = null;
    protected Schema schema;

    /**
     * Flag set to true if validation=preserve and no schema type supplied for validation; also true
     * when validation="strip" if there is no need to physically strip type annotations
     */

    protected boolean preservingTypes = true;

    /**
     * Create a document or element node constructor instruction
     */

    public ParentNodeConstructor() {
    }

    /**
     * Get the schema type chosen for validation; null if not defined
     *
     * @return the type to be used for validation. (For a document constructor, this is the required
     *         type of the document element)
     */

    @Override
    public SchemaType getSchemaType() {
        return validationOptions == null ? null : validationOptions.getTopLevelType();
    }

    /**
     * Get the validation options
     *
     * @return the validation options for the content of the constructed node. May be null if no
     *         validation was requested.
     */

    public ParseOptions getValidationOptions() {
        return validationOptions;
    }

    public void setValidationOptions(ParseOptions options) {
        this.validationOptions = options;
    }

    /**
     * Set the validation mode for the new document or element node
     *
     * @param schema     the schema to be used for validation (can be null if not validating)
     * @param mode       the validation mode, for example {@link Validation#STRICT}
     * @param schemaType the required type (for validation by type). Null if not
     *                   validating by type
     */


    public void setValidationAction(Schema schema, int mode, /*@Nullable*/ SchemaType schemaType) {
        this.schema = schema;
        preservingTypes = mode == Validation.PRESERVE && schemaType == null;
        if (!preservingTypes) {
            if (validationOptions == null) {
                validationOptions = new ParseOptions();
            }
            if (schemaType == Untyped.INSTANCE) {
                validationOptions = validationOptions.withSchemaValidationMode(Validation.SKIP);
            } else {
                validationOptions = validationOptions.withSchemaValidationMode(mode)
                        .withTopLevelType(schemaType);
            }
            validationOptions = validationOptions.withSchema(schema);
        }
    }


    /**
     * Get the validation mode for this instruction
     *
     * @return the validation mode, for example {@link Validation#STRICT} or {@link Validation#PRESERVE}
     */
    @Override
    public int getValidationAction() {
        return validationOptions == null ? Validation.PRESERVE : validationOptions.getSchemaValidationMode();
    }


    public Schema getSchema() {
        return schema;
    }

    /**
     * Set that the newly constructed node and everything underneath it will automatically be untyped,
     * without any need to physically remove type annotations, even though validation=STRIP is set.
     */

    public void setNoNeedToStrip() {
        preservingTypes = true;
    }

    /**
     * Set the expression that constructs the content of the element
     *
     * @param content the content expression
     */

    public void setContentExpression(Expression content) {
        if (contentOp == null) {
            contentOp = new Operand(this, content, SAME_FOCUS_CONTENT);
        } else {
            contentOp.setChildExpression(content);
        }
    }

    /**
     * Get the expression that constructs the content of the element
     *
     * @return the content expression
     */

    @Override
    public Expression getContentExpression() {
        return contentOp == null ? null : contentOp.getChildExpression();
    }

    public Operand getContentOperand() {
        return contentOp;
    }

    /**
     * Get the cardinality of the sequence returned by evaluating this instruction
     *
     * @return the static cardinality
     */

    @Override
    protected int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        checkContentSequence();
        return this;
    }

    /**
     * Ask whether common subexpressions found in the operands of this expression can
     * be extracted and evaluated outside the expression itself. The result is irrelevant
     * in the case of operands evaluated with a different focus, which will never be
     * extracted in this way, even if they have no focus dependency.
     *
     * @return false for this kind of expression
     */
    @Override
    public boolean allowExtractingCommonSubexpressions() {
        return false;
    }

    /**
     * Check that the child instructions don't violate any obvious constraints for this kind of node
     *
     * @throws XPathException if the check fails
     */

    protected abstract void checkContentSequence() throws XPathException;

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        optimizeChildren(visitor, contextItemType);
        if (!Literal.isEmptySequence(getContentExpression())) {
            if (getContentExpression() instanceof Block) {
                setContentExpression(((Block) getContentExpression()).mergeAdjacentTextInstructions());
            }
            // This code removed 25 Aug 2016. We no longer introduce copy operations. But we could go
            // further, and try to get rid of more unnecessary copy operations (whether streaming or not...)
            //  Reinstated 14 Oct 2016
            if (visitor.isOptimizeForStreaming()) {
                visitor.obtainOptimizer().makeCopyOperationsExplicit(this, contentOp);
            }
        }
        if (visitor.getStaticContext().getPackageData().isSchemaAware()) {
            if (getValidationAction() == Validation.STRIP) {
                if (getContentExpression().hasSpecialProperty(StaticProperty.ALL_NODES_UNTYPED)) {
                    setNoNeedToStrip();
                }
                UType contentType = getContentExpression().getItemType().getUType();
                if (!contentType.overlaps(UType.DOCUMENT.union(UType.ELEMENT).union(UType.ATTRIBUTE))) {
                    // No need to strip type annotations if there are none needing to be stripped
                    setNoNeedToStrip();
                }
            }
        } else {
            setValidationAction(null, Validation.STRIP, null);
            setNoNeedToStrip();
        }
        return this;
    }


    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true.
     */

    @Override
    public final boolean mayCreateNewNodes() {
        return true;
    }

    /**
     * Ask whether it is guaranteed that every item in the result of this instruction
     * is a newly created node
     *
     * @return true if result of the instruction is always either an empty sequence or
     * a sequence consisting entirely of newly created nodes
     */

    @Override
    public boolean alwaysCreatesNewNodes() {
        return true;
    }

    @Override
    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Determine whether this elementCreator performs validation or strips type annotations
     *
     * @return false if the instruction performs validation of the constructed output or if it strips
     *         type annotations, otherwise true
     */

    public boolean isPreservingTypes() {
        return preservingTypes;
    }

    public boolean isLocal() {
        return ExpressionTool.isLocalConstructor(this);
    }
}


