////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.accum;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.instruct.Actor;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.value.SequenceType;

import java.util.Map;

/**
 * Represents a single accumulator declared in an XSLT 3.0 stylesheet
 */
public class Accumulator extends Actor {

    private StructuredQName accumulatorName;

    private SimpleMode preDescentRules;
    private SimpleMode postDescentRules;

    private Expression initialValueExpression;
    private SequenceType type;
    private boolean streamable;
    private boolean universallyApplicable;
    private int importPrecedence;
    private boolean tracing;

    private SlotManager slotManagerForInitialValueExpression;



    public Accumulator() {
        preDescentRules = new SimpleMode(new StructuredQName("saxon", NamespaceConstant.SAXON, "preDescent"));
        postDescentRules = new SimpleMode(new StructuredQName("saxon", NamespaceConstant.SAXON, "postDescent"));
        // The "body" of an accumulator is an artificial expression that contains all the constituent expressions, for ease of management.
        body = Literal.makeEmptySequence();
    }

    /**
     * Get the symbolic name of the component
     *
     * @return the symbolic name
     */
    @Override
    public SymbolicName getSymbolicName() {
        return new SymbolicName(StandardNames.XSL_ACCUMULATOR, getAccumulatorName());
    }

    /**
     * Ask whether the accumulator is declared streamable
     *
     * @return true if it is declared with streamable="yes"
     */

    public boolean isDeclaredStreamable() {
        return streamable;
    }

    /**
     * Say whether the accumulator is declared streamable
     *
     * @param streamable true if it is declared with streamable="yes"
     */

    public void setDeclaredStreamable(boolean streamable) {
        this.streamable = streamable;
    }

    /**
     * Get the name of the accumulator
     *
     * @return the accumulator name
     */

    public StructuredQName getAccumulatorName() {
        return accumulatorName;
    }

    /**
     * Set the name of the pre-descent function
     *
     * @param firstName the function name (always present)
     */

    public void setAccumulatorName(StructuredQName firstName) {
        this.accumulatorName = firstName;
    }

    /**
     * Get the import precedence
     * @return  the import precedence of the accumulator
     */

    public int getImportPrecedence() {
        return importPrecedence;
    }

    /**
     * Set the import precedence
     * @param importPrecedence the import precedence of the accumulator
     */

    public void setImportPrecedence(int importPrecedence) {
        this.importPrecedence = importPrecedence;
    }

    /**
     * Say whether this accumulator is universally appicable to all documents
     * @param universal true if this accumulator is universally applicable
     */

    public void setUniversallyApplicable(boolean universal) {
        this.universallyApplicable = universal;
    }

    /**
     * Ask whether this accumulator is universally appicable to all documents
     * @return true if this accumulator is universally applicable
     */

    public boolean isUniversallyApplicable() {
        return universallyApplicable;
    }

    /**
     * Ask whether diagnostic tracing is enabled for this accumulator
     * @return true if diagnostic tracing is enabled
     */

    public boolean isTracing() {
        return tracing;
    }

    /**
     * Say whether diagnostic tracing is to be enabled for this accumulator
     * @param tracing true if diagnostic tracing is to be enabled
     */

    public void setTracing(boolean tracing) {
        this.tracing = tracing;
    }

    /**
     * Get the slotManager to be used for evaluating the initial-value expression
     * @return the slotManager for the initial-value expression (defining any local variables declared and used
     * within this expression)
     */

    public SlotManager getSlotManagerForInitialValueExpression() {
        return slotManagerForInitialValueExpression;
    }

    /**
     * Set the slotManager to be used for evaluating the initial-value expression
     * @param slotManagerForInitialValueExpression the slotManager for the initial-value expression
     * (defining any local variables declared and used within this expression)
     */

    public void setSlotManagerForInitialValueExpression(SlotManager slotManagerForInitialValueExpression) {
        this.slotManagerForInitialValueExpression = slotManagerForInitialValueExpression;
    }

    /**
     * Get the set of rules for phase="start", held in the form of a Mode object
     *
     * @return the Mode object containing all the rules that apply to phase="start"
     */

    public SimpleMode getPreDescentRules() {
        return preDescentRules;
    }

    /**
     * Set the set of rules for phase="start", held in the form of a Mode object
     *
     * @param preDescentRules the Mode object containing all the rules that apply to phase="start"
     */

    public void setPreDescentRules(SimpleMode preDescentRules) {
        this.preDescentRules = preDescentRules;
    }

    /**
     * Get the set of rules for phase="end", held in the form of a Mode object
     *
     * @return the Mode object containing all the rules that apply to phase="end"
     */

    public SimpleMode getPostDescentRules() {
        return postDescentRules;
    }

    /**
     * Set the set of rules for phase="end", held in the form of a Mode object
     *
     * @param postDescentRules the Mode object containing all the rules that apply to phase="end"
     */


    public void setPostDescentRules(SimpleMode postDescentRules) {
        this.postDescentRules = postDescentRules;
    }

    /**
     * Get the expression that computes the initial value of the accumulator
     *
     * @return the initial value expression
     */

    public Expression getInitialValueExpression() {
        return initialValueExpression;
    }

    /**
     * Set the expression that computes the initial value of the accumulator
     *
     * @param initialValueExpression the initial value expression
     */

    public void setInitialValueExpression(Expression initialValueExpression) {
        this.initialValueExpression = initialValueExpression;
    }

    /**
     * Add an expression to the list of expressions used by this accumulator
     * @param expression the expression to be added to the list
     */

    public void addChildExpression(Expression expression) {
        Expression e = Block.makeBlock(getBody(), expression);
        setBody(e);
    }

    /**
     * Get the declared type of the accumulator. The initial value and all intermediate values
     * must conform to this type
     *
     * @return the declared type of the accumulator
     */

    public SequenceType getType() {
        return type;
    }

    /**
     * Set the declared type of the accumulator. The initial value and all intermediate values
     * must conform to this type
     *
     * @param type the declared type of the accumulator
     */

    public void setType(SequenceType type) {
        this.type = type;
    }

    /**
     * Determine whether this accumulator is compatible with one that it overrides
     * @param other the accumlator that this one overrides
     * @return true if they are compatible, as defined in the XSLT 3.0 specification
     */

    public boolean isCompatible(Accumulator other) {
        return getAccumulatorName().equals(other.getAccumulatorName());
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     * @return the QName of the object declared or manipulated by this instruction or expression
     */
    public StructuredQName getObjectName() {
        return accumulatorName;
    }

    /**
     * Export expression structure. The abstract expression tree
     * is written to the supplied outputstream.
     *
     * @param presenter the expression presenter used to generate the XML representation of the structure
     */
    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        export(presenter, null);
    }

    /**
     * Export expression structure. The abstract expression tree
     * is written to the supplied outputstream.
     *
     * @param out the expression presenter used to display the structure
     */

    public void export(final ExpressionPresenter out, Map<Component, Integer> componentIdMap) throws XPathException {
//        if ("JS".equals(out.getOption("target"))) {
//            throw new XPathException("xsl:accumulator is not supported in Saxon-JS", SaxonErrorCode.SXJS0001);
//        }
        out.startElement("accumulator");
        out.emitAttribute("name", getObjectName());
        out.emitAttribute("line", getLineNumber() + "");
        out.emitAttribute("module", getSystemId());
        out.emitAttribute("as", type.toAlphaCode());
        out.emitAttribute("streamable", streamable ? "1" : "0");
        out.emitAttribute("slots", getSlotManagerForInitialValueExpression().getNumberOfVariables()+"");
        if (componentIdMap != null) {
            out.emitAttribute("binds", "" + getDeclaringComponent().listComponentReferences(componentIdMap));
        }
        if (isUniversallyApplicable()) {
            out.emitAttribute("flags", "u");
        }
        out.setChildRole("init");
        initialValueExpression.export(out);

        SimpleMode.RuleAction action = new SimpleMode.RuleAction() {
            @Override
            public void processRule(Rule r) throws XPathException {
                out.startElement("accRule");
                out.emitAttribute("slots", ((AccumulatorRule)r.getAction()).getStackFrameMap().getNumberOfVariables()+"");
                out.emitAttribute("rank", ""+r.getRank());
                if (((AccumulatorRule) r.getAction()).isCapturing()) {
                    out.emitAttribute("flags", "c");
                }
                r.getPattern().export(out);
                r.getAction().export(out);
                out.endElement();
            }
        };
        try {
            out.startElement("pre");
            out.emitAttribute("slots", preDescentRules.getStackFrameSlotsNeeded()+"");
            preDescentRules.processRules(action);
            out.endElement();
            out.startElement("post");
            out.emitAttribute("slots", postDescentRules.getStackFrameSlotsNeeded() + "");
            postDescentRules.processRules(action);
            out.endElement();
        } catch (XPathException e) {
            throw new AssertionError(e);
        }
        out.endElement();
    }
}
