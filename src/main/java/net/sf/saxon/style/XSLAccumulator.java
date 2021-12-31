////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import com.saxonica.ee.stream.PostureAndSweep;
import com.saxonica.ee.stream.Streamability;
import com.saxonica.ee.stream.Sweep;
import com.saxonica.ee.trans.ContextItemStaticInfoEE;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.accum.AccumulatorRegistry;
import net.sf.saxon.expr.accum.AccumulatorRule;
import net.sf.saxon.expr.instruct.Actor;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.functions.AccumulatorFn;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.*;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for xsl:accumulator elements in a stylesheet (XSLT 3.0).
 */

public class XSLAccumulator extends StyleElement implements StylesheetComponent {

    private Accumulator accumulator = new Accumulator();
    private SlotManager slotManager;

    /**
     * Get the corresponding Procedure object that results from the compilation of this
     * StylesheetProcedure
     */
    @Override
    public Actor getActor() {
        if (accumulator.getDeclaringComponent() == null) {
            accumulator.makeDeclaringComponent(Visibility.PRIVATE, getContainingPackage());
        }
        return accumulator;
    }

    @Override
    public SymbolicName getSymbolicName() {
        StructuredQName qname = accumulator.getAccumulatorName();
        return qname==null ? null : new SymbolicName(StandardNames.XSL_ACCUMULATOR, null);
    }

    @Override
    public void checkCompatibility(Component component) {
        // no action: accumulators cannot be overridden
    }

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import).
     *
     * @return true for this element
     */

    @Override
    public boolean isDeclaration() {
        return true;
    }

    /**
     * Method to handle the name attributes, which may need to be evaluated early if there are forwards
     * references to this accumulator
     */

    private void prepareSimpleAttributes() {

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getDisplayName();
            if (f.equals("name")) {
                String name = Whitespace.trim(value);
                accumulator.setAccumulatorName(makeQName(name, null, "name"));
            } else if (f.equals("streamable")) {
                accumulator.setDeclaredStreamable(false);
                boolean streamable = processStreamableAtt(value);
                accumulator.setDeclaredStreamable(streamable);
            } else if (attName.hasURI(NamespaceConstant.SAXON) && attName.getLocalPart().equals("trace")) {
                if (isExtensionAttributeAllowed(attName.getDisplayName())) {
                    accumulator.setTracing(processBooleanAttribute("saxon:trace", value));
                }
            } else {
                // report the error later
            }
        }

        if (accumulator.getAccumulatorName() == null) {
            reportAbsence("name");
        }

    }

    @Override
    public void prepareAttributes() {

        //prepareSimpleAttributes();

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("name")) {
                // no action
            } else if (f.equals("streamable")) {
                // no action
            } else if (f.equals("initial-value")) {
                accumulator.setInitialValueExpression(makeExpression(value, att));
            } else if (f.equals("as")) {
                try {
                    SequenceType requiredType = makeSequenceType(value);
                    accumulator.setType(requiredType);
                } catch (XPathException e) {
                    compileErrorInAttribute(e.getMessage(), e.getErrorCodeLocalPart(), "as");
                }
            } else if (attName.hasURI(NamespaceConstant.SAXON) && attName.getLocalPart().equals("trace")) {
                if (isExtensionAttributeAllowed(attName.getDisplayName())) {
                    accumulator.setTracing(processBooleanAttribute("saxon:trace", value));
                }
            } else {
                checkUnknownAttribute(attName);
            }
            // TODO: add saxon:as
        }

        if (accumulator.getType() == null) {
            accumulator.setType(SequenceType.ANY_SEQUENCE);
        }

        if (accumulator.getInitialValueExpression() == null) {
            reportAbsence("initial-value");
            StringLiteral zls = new StringLiteral(StringValue.EMPTY_STRING);
            zls.setRetainedStaticContext(makeRetainedStaticContext());
            accumulator.setInitialValueExpression(zls);
        }

    }

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {

        Configuration config = compilation.getConfiguration();

        // Prepare the initial value expression

        {
            accumulator.setPackageData(compilation.getPackageData());
            accumulator.obtainDeclaringComponent(decl.getSourceElement());
            Expression init = accumulator.getInitialValueExpression();
            ExpressionVisitor visitor = ExpressionVisitor.make(getStaticContext());
            init = init.typeCheck(visitor, config.getDefaultContextItemStaticInfo());
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:accumulator-rule/select", 0);
            init = config.getTypeChecker(false).staticTypeCheck(init, accumulator.getType(), role, visitor);
            init = init.optimize(visitor, config.getDefaultContextItemStaticInfo());
            SlotManager stackFrameMap = slotManager;
            ExpressionTool.allocateSlots(init, 0, stackFrameMap);
            accumulator.setSlotManagerForInitialValueExpression(stackFrameMap);
            checkInitialStreamability(init);
            accumulator.setInitialValueExpression(init);
            accumulator.addChildExpression(init);
        }

        // Prepare the new-value (select) expressions

        int position = 0;
        for (NodeInfo curr : children(XSLAccumulatorRule.class::isInstance)) {
            XSLAccumulatorRule rule = (XSLAccumulatorRule) curr;
            Pattern pattern = rule.getMatch();
            Expression newValueExp = rule.getNewValueExpression(compilation, decl);
            ExpressionVisitor visitor = ExpressionVisitor.make(getStaticContext());
            newValueExp = newValueExp.typeCheck(visitor, config.makeContextItemStaticInfo(pattern.getItemType(), false));
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:accumulator-rule/select", 0);
            newValueExp = config.getTypeChecker(false).staticTypeCheck(newValueExp, accumulator.getType(), role, visitor);
            newValueExp = newValueExp.optimize(visitor, getConfiguration().makeContextItemStaticInfo(pattern.getItemType(), false));
            SlotManager stackFrameMap = getConfiguration().makeSlotManager();
            stackFrameMap.allocateSlotNumber(new StructuredQName("", "", "value"));
            ExpressionTool.allocateSlots(newValueExp, 1, stackFrameMap);
            boolean isPreDescent = !rule.isPostDescent();
            SimpleMode mode = isPreDescent ? accumulator.getPreDescentRules() : accumulator.getPostDescentRules();
            AccumulatorRule action = new AccumulatorRule(newValueExp, stackFrameMap, rule.isPostDescent());
            mode.addRule(pattern, action, decl.getModule(), decl.getModule().getPrecedence(), 1, position++, 0);

            checkRuleStreamability(rule, pattern, newValueExp);

            if (accumulator.isDeclaredStreamable() && rule.isPostDescent() && rule.isCapture()) {
                action.setCapturing(true);
            }

            ItemType itemType = pattern.getItemType();
            if (itemType instanceof NodeTest) {
                if (!itemType.getUType().overlaps(UType.DOCUMENT.union(UType.CHILD_NODE_KINDS))) {
                    rule.compileWarning("An accumulator rule that matches attribute or namespace nodes has no effect", "SXWN9999");
                }
            } else if (itemType instanceof AtomicType) {
                rule.compileWarning("An accumulator rule that matches atomic values has no effect", "SXWN9999");
            }

            accumulator.addChildExpression(newValueExp);
            accumulator.addChildExpression(pattern);
        }

        accumulator.getPreDescentRules().allocateAllPatternSlots();
        accumulator.getPostDescentRules().allocateAllPatternSlots();
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * If there is no name, the value will be -1.
     */

    /*@NotNull*/
    @Override
    public StructuredQName getObjectName() {
        StructuredQName qn = super.getObjectName();
        if (qn == null) {
            String nameAtt = Whitespace.trim(getAttributeValue("", "name"));
            if (nameAtt == null) {
                return new StructuredQName("saxon", NamespaceConstant.SAXON, "badly-named-accumulator" + generateId());
            }
            qn = makeQName(nameAtt, null, "name");
            setObjectName(qn);
        }
        return qn;
    }


    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) {
        if (accumulator.getAccumulatorName() == null) {
            prepareSimpleAttributes();
        }
        accumulator.setImportPrecedence(decl.getPrecedence());
        if (top.getAccumulatorManager() == null) {
            StyleNodeFactory styleNodeFactory = getCompilation().getStyleNodeFactory(true);
            AccumulatorRegistry manager = styleNodeFactory.makeAccumulatorManager();
            top.setAccumulatorManager(manager);
            getCompilation().getPackageData().setAccumulatorRegistry(manager);
        }
        AccumulatorRegistry mgr = top.getAccumulatorManager();
        Accumulator existing = mgr.getAccumulator(accumulator.getAccumulatorName());
        if (existing != null) {
            int existingPrec = existing.getImportPrecedence();
            if (existingPrec == decl.getPrecedence()) {
                compileError("There are two accumulators with the same name (" +
                        accumulator.getAccumulatorName().getDisplayName() + ") and the same import precedence", "XTSE3350");
            }
            if (existingPrec > decl.getPrecedence()) {
                return;
            }
        }
        mgr.addAccumulator(accumulator);
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {

        slotManager = getConfiguration().makeSlotManager();

        // check the element is at the top level of the stylesheet

        checkTopLevel("XTSE0010", true);

        // only permitted child is XSLAccumulatorRule, and there must be at least one

        boolean foundRule = false;
        for (NodeInfo curr : children()) {
            if (curr instanceof XSLAccumulatorRule) {
                foundRule = true;
            } else {
                compileError("Only xsl:accumulator-rule is allowed here", "XTSE0010");
            }
        }

        if (!foundRule) {
            compileError("xsl:accumulator must contain at least one xsl:accumulator-rule", "XTSE0010");
        }

    }

    @Override
    public SlotManager getSlotManager() {
        return slotManager;
    }

    @Override
    public void optimize(ComponentDeclaration declaration) throws XPathException {
        // no action
    }


    /**
     * Get the type of value returned by this function
     *
     * @return the declared result type, or the inferred result type
     *         if this is more precise
     */
    public SequenceType getResultType() {
        return accumulator.getType();
    }


    /**
     * Generate byte code if appropriate
     * @param opt the optimizer
     */
    @Override
    public void generateByteCode(Optimizer opt) {
        // no action currently
    }

    private void checkInitialStreamability(Expression init) throws XPathException {
        // Check streamability constraints
    }

    private void checkRuleStreamability(XSLAccumulatorRule rule, Pattern pattern, Expression newValueExp) throws XPathException {
        // Check streamability constraints
    }

    private void notStreamable(StyleElement rule, String message) {
        boolean fallback = getConfiguration().getBooleanProperty(Feature.STREAMING_FALLBACK);
        if (fallback) {
            message += ". Falling back to non-streaming implementation";
            rule.compileWarning(message, "XTSE3430");
            rule.getCompilation().setFallbackToNonStreaming(true);
        } else {
            rule.compileError(message, "XTSE3430");
        }
    }

}
