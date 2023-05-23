////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.style.Compilation;
import net.sf.saxon.style.ComponentDeclaration;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.TraceableComponent;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.trans.rules.RuleTarget;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The runtime object corresponding to an xsl:template element in the stylesheet.
 * <p>Note that the Template object no longer has precedence information associated with it; this is now
 * only in the Rule object that references this Template. This allows two rules to share the same template,
 * with different precedences. This occurs when a stylesheet module is imported more than once, from different
 * places, with different import precedences.</p>
 * <p>From Saxon 9.7, the old Template class is split into NamedTemplate and TemplateRule.</p>
 * <p>From Saxon 9.8, a subclass is used in Saxon-EE</p>
 */

public class TemplateRule implements RuleTarget, Location, ExpressionOwner, TraceableComponent {

    // The body of the template is represented by an expression,
    // which is responsible for any type checking that's needed.

    protected Expression body;
    protected Pattern matchPattern;
    private boolean hasRequiredParams;
    private boolean bodyIsTailCallReturner;
    private SequenceType requiredType;
    private boolean declaredStreamable;
    private ItemType requiredContextItemType = AnyItemType.getInstance();
    private boolean absentFocus;
    private SlotManager stackFrameMap;
    private PackageData packageData;
    private String systemId;
    private int lineNumber;
    private int columnNumber;


    private List<Rule> rules = new ArrayList<>();
    protected List<TemplateRule> slaveCopies = new ArrayList<>();

    /**
     * Create a template
     */

    public TemplateRule() {
    }

    /**
     * Set the match pattern used with this template
     *
     * @param pattern the match pattern (may be null for a named template)
     */

    public void setMatchPattern(Pattern pattern) {
//        if (matchPattern != pattern) {
//            for (Rule r : rules) {
//                r.setPattern(pattern);
//            }
//        }
        matchPattern = pattern;

    }

    @Override
    public Expression getBody() {
        return body;
    }

    @Override
    public Expression getChildExpression() {
        return body;
    }

    @Override
    public Location getLocation() {
        return this;
    }

    /**
     * Get the properties of this object to be included in trace messages, by supplying
     * the property values to a supplied consumer function
     *
     * @param consumer the function to which the properties should be supplied, as (property name,
     *                 value) pairs.
     */
    @Override
    public void gatherProperties(BiConsumer<String, Object> consumer) {
        consumer.accept("match", getMatchPattern().toShortString());
    }

    /**
     * Set the required context item type. Used when there is an xsl:context-item child element
     *
     * @param type        the required context item type
     * @param absentFocus true if the context item is treated as absent even if supplied (use="absent")
     */

    public void setContextItemRequirements(ItemType type, boolean absentFocus) {
        requiredContextItemType = type;
        this.absentFocus = absentFocus;
    }

    public int getComponentKind() {
        return StandardNames.XSL_TEMPLATE;
    }

    /**
     * Get the match pattern used with this template
     *
     * @return the match pattern, or null if this is a named template with no match pattern. In the case of
     * a template rule whose pattern is a union pattern, this will be the original union pattern; the individual
     * Rule objects contain the branches of the union pattern.
     */

    public Pattern getMatchPattern() {
        return matchPattern;
    }

    /**
     * Set the expression that forms the body of the template
     *
     * @param body the body of the template
     */

    @Override
    public void setBody(Expression body) {
        this.body = body;
        bodyIsTailCallReturner = (body instanceof TailCallReturner);
    }

    public void setStackFrameMap(SlotManager map) {
        stackFrameMap = map;
    }

    public SlotManager getStackFrameMap() {
        return stackFrameMap;
    }

//    @Override
//    public void allocateAllBindingSlots(StylesheetPackage pack) {
//        super.allocateAllBindingSlots(pack);
//        if (matchPattern != null) {
//            allocateBindingSlotsRecursive(pack, this, matchPattern);
//        }
//    }

    /**
     * Set whether this template has one or more required parameters
     *
     * @param has true if the template has at least one required parameter
     */

    public void setHasRequiredParams(boolean has) {
        hasRequiredParams = has;
    }

    /**
     * Ask whether this template has one or more required parameters
     *
     * @return true if this template has at least one required parameter
     */

    public boolean hasRequiredParams() {
        return hasRequiredParams;
    }

    /**
     * Set the required type to be returned by this template
     *
     * @param type the required type as defined in the "as" attribute on the xsl:template element
     */

    public void setRequiredType(SequenceType type) {
        requiredType = type;
    }

    /**
     * Get the required type to be returned by this template
     *
     * @return the required type as defined in the "as" attribute on the xsl:template element
     */

    public SequenceType getRequiredType() {
        if (requiredType == null) {
            return SequenceType.ANY_SEQUENCE;
        } else {
            return requiredType;
        }
    }

    /**
     * Register a rule for which this is the target
     *
     * @param rule a rule in which this is the target
     */
    @Override
    public void registerRule(Rule rule) {
        rules.add(rule);
    }

    /**
     * Get the rules that use this template. For a template with no match pattern, this will be
     * an empty list. For a union pattern, there will be one rule for each branch of the union.
     *
     * @return the rules corresponding to this template.
     */

    public List<Rule> getRules() {
        return rules;
    }


    public int getContainerGranularity() {
        return 0;
    }

    public PackageData getPackageData() {
        return packageData;
    }

    public void setPackageData(PackageData data) {
        this.packageData = data;
    }

    @Override
    public String getPublicId() {
        return null;
    }

    @Override
    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String id) {
        this.systemId = id;
    }

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int line) {
        this.lineNumber = line;
    }

    public void setColumnNumber(int col) {
        this.columnNumber = col;
    }

    @Override
    public int getColumnNumber() {
        return columnNumber;
    }

    /**
     * Get an immutable copy of this Location object. By default Location objects may be mutable, so they
     * should not be saved for later use. The result of this operation holds the same location information,
     * but in an immutable form.
     */
    @Override
    public Location saveLocation() {
        return this;
    }

    public ItemType getRequiredContextItemType() {
        return requiredContextItemType;
    }

    public boolean isAbsentFocus() {
        return absentFocus;
    }


    public List<LocalParam> getLocalParams() {
        List<LocalParam> result = new ArrayList<>();
        gatherLocalParams(getInterpretedBody(), result);
        return result;
    }

    private static void gatherLocalParams(Expression exp, List<LocalParam> result) {
        if (exp instanceof LocalParam) {
            result.add((LocalParam) exp);
        } else {
            for (Operand o : exp.operands()) {
                gatherLocalParams(o.getChildExpression(), result);
            }
        }
    }

    /**
     * Prepare for JIT compilation.
     *
     * @param compilation the XSLT compilation
     * @param decl        the component declaration of this template rule
     */

    public void prepareInitializer(Compilation compilation, ComponentDeclaration decl, StructuredQName modeName) {
        // No action in Saxon-HE
    }

    /**
     * Ensure that any first-time initialization has been done. Used in Saxon-EE
     * to do JIT compilation
     */

    public void initialize() throws XPathException {
        // No action needed in Saxon-HE
    }

    /**
     * Process the template, without returning any tail calls. This path is used by
     * xsl:apply-imports and xsl:next-match
     *
     *
     * @param output the destination for the result
     * @param context The dynamic context, giving access to the current node,
     * @throws XPathException if a dynamic error occurs while evaluating
     *                        the template
     */

    public void apply(Outputter output, XPathContextMajor context) throws XPathException {
        TailCall tc = applyLeavingTail(output, context);
        while (tc != null) {
            tc = tc.processLeavingTail();
        }
    }

    /**
     * Process this template, with the possibility of returning a tail call package if the template
     * contains any tail calls that are to be performed by the caller.
     *
     * @param output the destination for the result
     * @param context the XPath dynamic context
     * @return null if the template exited normally; but if it was a tail call, details of the call
     * that hasn't been made yet and needs to be made by the caller
     * @throws XPathException if a dynamic error occurs while evaluating
     *                        the template
     */

    public TailCall applyLeavingTail(Outputter output, XPathContext context) throws XPathException {
        //initialize();
        TypeHierarchy th = context.getConfiguration().getTypeHierarchy();
        if (requiredContextItemType != AnyItemType.getInstance() &&
                !requiredContextItemType.matches(context.getContextItem(), th)) {
            RoleDiagnostic role = new RoleDiagnostic(
                    RoleDiagnostic.MISC, "context item for the template rule", 0);
            String message = role.composeErrorMessage(requiredContextItemType, context.getContextItem(), th);
            XPathException err = new XPathException(message, "XTTE0590");
            err.setLocation(this);
            err.setIsTypeError(true);
            throw err;
        }
        if (absentFocus) {
            context = context.newMinorContext();
            context.setCurrentIterator(null);
        }

        try {
            if (bodyIsTailCallReturner) {
                return ((TailCallReturner) body).processLeavingTail(output, context);
            } else {
                body.process(output, context);
                return null;
            }
        } catch (UncheckedXPathException e) {
            XPathException xe = e.getXPathException();
            xe.maybeSetLocation(this);
            xe.maybeSetContext(context);
            throw xe;
        } catch (XPathException e) {
            e.maybeSetLocation(this);
            e.maybeSetContext(context);
            throw e;
        } catch (Exception e2) {
            String message = "Internal error evaluating template rule "
                    + (getLineNumber() > 0 ? " at line " + getLineNumber() : "")
                    + (getSystemId() != null ? " in module " + getSystemId() : "");
            e2.printStackTrace();
            throw new RuntimeException(message, e2);
        }
    }

    /**
     * Output diagnostic explanation to an ExpressionPresenter
     */

    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        // NOT USED - see Rule.export
        throw new UnsupportedOperationException();
    }

    /**
     * Say whether or not this template is declared as streamable
     *
     * @param streamable true if the template belongs to a streamable mode; set to false if it does not belong
     *                   to a streamable mode, or if it is found that the template is not actually streamable, and fallback
     *                   to non-streaming has been requested.
     */

    public void setDeclaredStreamable(boolean streamable) {
        // Overridden in Saxon-EE
    }

    public boolean isDeclaredStreamable() {
        // Overridden in Saxon-EE
        return false;
    }

    public void explainProperties(ExpressionPresenter presenter) throws XPathException {
        if (getRequiredContextItemType() != AnyItemType.getInstance()) {
            SequenceType st = SequenceType.makeSequenceType(getRequiredContextItemType(), StaticProperty.EXACTLY_ONE);
            presenter.emitAttribute("cxt", st.toAlphaCode());
        }

        String flags = "";
        if (!absentFocus) {
            flags += "s";
        }
        presenter.emitAttribute("flags", flags);
        if (getRequiredType() != SequenceType.ANY_SEQUENCE) {
            presenter.emitAttribute("as", getRequiredType().toAlphaCode());
        }
        presenter.emitAttribute("line", getLineNumber() + "");
        presenter.emitAttribute("module", getSystemId());
        if (isDeclaredStreamable()) {
            presenter.emitAttribute("streamable", "1");
        }
    }

    public Expression getInterpretedBody() {
        return body.getInterpretedExpression();
    }


    /**
     * Create a copy of a template rule. This is needed when copying a rule from the "omniMode" (mode=#all)
     * to a specific mode. Because we want the rules to be chained in the right order within the mode object,
     * we create the copy as soon as we know it is needed. The problem is that at this stage many of the properties
     * of the template rule are still uninitialised. So we mark the new copy as a slave of the original, and at
     * the end of the compilation process we update all the slave copies to match the properties of the original.
     */

    public TemplateRule copy() {
        TemplateRule tr = new TemplateRule();
        if (body == null || matchPattern == null) {
            slaveCopies.add(tr);
        } else {
            copyTo(tr);
        }
        return tr;
    }

    /**
     * Update the properties of template rules that have been marked as slave copies of this one (typically the same
     * template, but in a different mode).
     */

    public void updateSlaveCopies() {
        for (TemplateRule tr : slaveCopies) {
            copyTo(tr);
        }
    }

    protected void copyTo(TemplateRule tr) {
        if (body != null) {
            tr.body = body.copy(new RebindingMap());
        }
        if (matchPattern != null) {
            tr.matchPattern = matchPattern.copy(new RebindingMap());
        }
        tr.hasRequiredParams = hasRequiredParams;
        tr.bodyIsTailCallReturner = bodyIsTailCallReturner;
        tr.requiredType = requiredType;
        tr.declaredStreamable = declaredStreamable; // ? this can vary from one mode to another
        tr.requiredContextItemType = requiredContextItemType;
        tr.absentFocus = absentFocus;
        tr.stackFrameMap = stackFrameMap;
        tr.packageData = packageData;
        tr.systemId = systemId;
        tr.lineNumber = lineNumber;
    }

    @Override
    public void setChildExpression(Expression expr) {
        setBody(expr);
    }

    @Override
    public StructuredQName getObjectName() {
        return null;
    }

    @Override
    public String getTracingTag() {
        return "xsl:template";
    }


}

