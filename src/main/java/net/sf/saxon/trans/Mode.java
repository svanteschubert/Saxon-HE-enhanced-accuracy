////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.Controller;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.instruct.Actor;
import net.sf.saxon.expr.instruct.ParameterSet;
import net.sf.saxon.expr.instruct.TailCall;
import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.TemplateRuleTraceListener;
import net.sf.saxon.trans.rules.*;
import net.sf.saxon.transpile.CSharpDelegate;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.SequenceType;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A Mode is a collection of rules; the selection of a rule to apply to a given element
 * is determined by a Pattern. A SimpleMode is a mode contained within a single package,
 * as opposed to a CompoundMode which can combine rules from several packages
 */

public abstract class Mode extends Actor {

    public static final StructuredQName OMNI_MODE_NAME =
            new StructuredQName("saxon", NamespaceUri.SAXON, "_omniMode");
    public static final StructuredQName UNNAMED_MODE_NAME =
            new StructuredQName("xsl", NamespaceUri.XSLT, "unnamed");
    public static final StructuredQName DEFAULT_MODE_NAME =
        new StructuredQName("xsl", NamespaceUri.XSLT, "default");


    protected StructuredQName modeName;
    private boolean streamable;

    public static final int RECOVER_WITH_WARNINGS = 1;
    private RecoveryPolicy recoveryPolicy = RecoveryPolicy.RECOVER_WITH_WARNINGS;
    public boolean mustBeTyped = false;
    public boolean mustBeUntyped = false;
    boolean hasRules = false;
    boolean bindingSlotsAllocated = false;
    boolean modeTracing = false;
    SequenceType defaultResultType = null;
    boolean enclosingMode = false;

    private Set<? extends Accumulator> accumulators;


    public Mode(StructuredQName modeName) {
        this.modeName = modeName;
    }

    @Override
    public Component.M getDeclaringComponent() {
        return (Component.M)super.getDeclaringComponent();
    }

    /**
     * Get the built-in template rules to be used with this Mode in the case where there is no
     * explicit template rule
     *
     * @return the built-in rule set, defaulting to the TextOnlyCopyRuleSet if no other rule set has
     * been supplied
     */

    public abstract BuiltInRuleSet getBuiltInRuleSet();

    /**
     * Determine if this is the unnamed mode
     *
     * @return true if this is the unnamed mode
     */

    public boolean isUnnamedMode() {
        return modeName.equals(UNNAMED_MODE_NAME);
    }

    /**
     * Get the name of the mode (for diagnostics only)
     *
     * @return the mode name. Null for the default (unnamed) mode
     */

    public StructuredQName getModeName() {
        return modeName;
    }

    /**
     * Get the active component of this mode. For a simple mode this is the mode itself;
     * for a compound mode it is the "overriding" part
     */

    public abstract SimpleMode getActivePart();

    /**
     * Get the maximum precedence of the rules in this mode
     */

    public abstract int getMaxPrecedence();

    /**
     * Get the highest rank of the rules in this mode
     *
     * @return the highest rank
     */

    public abstract int getMaxRank();

    /**
     * Compute a rank for each rule, as a combination of the precedence and priority, to allow
     * rapid comparison.  This method also checks that there are no conflicts for
     * property values in different xsl:mode declarations
     *
     * @param start the lowest rank to use
     * @throws XPathException if an error occurs processing the rules
     */

    public abstract void computeRankings(int start) throws XPathException;

    /**
     * Get a title for the mode: either "Mode mode-name" or "The unnamed mode" as appropriate
     * @param initialCaps true if the first letter should be upper case
     * @return a title for the mode
     */

    public String getModeTitle(boolean initialCaps) {
        if (initialCaps) {
            return isUnnamedMode() ? "The unnamed mode" : "Mode " + getModeName().getDisplayName();
        } else {
            return isUnnamedMode() ? "the unnamed mode" : "mode " + getModeName().getDisplayName();
        }
    }

    /**
     * Switch tracing on or off
     */

    public void setModeTracing(boolean tracing) {
        this.modeTracing = tracing;
    }

    public boolean isModeTracing() {
        return modeTracing;
    }

    /**
     * Get the list of accumulators declared on the xsl:mode/@use-accumulators attribute
     *
     * @return the list of accumulators applicable when this is the initial mode
     */
    public Set<? extends Accumulator> getAccumulators() {
        if (accumulators == null) {
            return Collections.emptySet();
        } else {
            return accumulators;
        }
    }

    /**
     * Set the list of accumulators declared on the xsl:mode/@use-accumulators attribute
     *
     * @param accumulators the list of accumulators applicable when this is the initial mode
     */

    public void setAccumulators(Set<? extends Accumulator> accumulators) {
        this.accumulators = accumulators;
    }


    @Override
    public SymbolicName getSymbolicName() {
        return new SymbolicName(StandardNames.XSL_MODE, getModeName());
    }

    public StructuredQName getObjectName() {
        return getModeName();
    }

    /**
     * Ask whether there are any template rules in this mode
     * (a mode could exist merely because it is referenced in apply-templates)
     *
     * @return true if no template rules exist in this mode
     */

    public abstract boolean isEmpty();

    /**
     * Say whether this is an enclosing mode (4.0 feature)
     * @param enclosing true if this is an enclosing mode
     */

    public void setEnclosingMode(boolean enclosing) {
        this.enclosingMode = enclosing;
    }

    /**
     * Ask whether this is an enclosing mode (4.0 feature)
     * @return true if this is an enclosing mode
     */

    public boolean isEnclosingMode() {
        return enclosingMode;
    }

    /**
     * Set the policy for handling recoverable errors. Note that for some errors the decision can be
     * made at run-time, but for the "ambiguous template match" error, the decision is (since 9.2)
     * fixed at compile time.
     *
     * @param policy the recovery policy to be used.
     */

    public void setRecoveryPolicy(RecoveryPolicy policy) {
        recoveryPolicy = policy;
    }

    public void setHasRules(boolean hasRules) {
        this.hasRules = hasRules;
    }

    /**
     * Get the policy for handling recoverable errors. Note that for some errors the decision can be
     * made at run-time, but for the "ambiguous template match" error, the decision is (since 9.2)
     * fixed at compile time.
     *
     * @return the current policy.
     */

    public RecoveryPolicy getRecoveryPolicy() {
        return recoveryPolicy;
    }

    /**
     * Say that this mode is (or is not) streamable
     *
     * @param streamable true if this mode is a streamable mode
     */

    public void setStreamable(boolean streamable) {
        this.streamable = streamable;
    }

    /**
     * Ask whether this mode is declared to be streamable
     *
     * @return true if this mode is declared with the option streamable="yes"
     */

    public boolean isDeclaredStreamable() {
        return streamable;
    }


    /**
     * Get the "explicit namespaces" matched by this mode. Returns a set containing all the namespaces
     * matched by specific template rules in this mode
     *
     * @param pool the NamePool for the configuration
     * @return the set of all namespace URIs of names explicitly matched by rules in this mode
     */

    public abstract Set<NamespaceUri> getExplicitNamespaces(NamePool pool);

    public void setDefaultResultType(SequenceType type) {
        defaultResultType = type;
    }

    public SequenceType getDefaultResultType() {
        return defaultResultType;
    }


    /**
     * Walk over all the rules, applying a specified action to each one.
     *
     * @param action an action that is to be applied to all the rules in this Mode
     * @throws XPathException if an error occurs processing any of the rules
     */

    public abstract void processRules(RuleAction action) throws XPathException;

    /**
     * Make a new XPath context for evaluating patterns if there is any possibility that the
     * pattern uses local variables
     *
     * @param context The existing XPath context
     * @return a new XPath context
     */

    public XPathContext makeNewContext(XPathContext context) {
        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(context.getController());   // WHY?
        c2.openStackFrame(getStackFrameSlotsNeeded());
        if (!(context.getCurrentComponent().getActor() instanceof Accumulator)) {
            c2.setCurrentComponent(context.getCurrentMode()); // bug 3706
        }
        return c2;
    }

    /**
     * Get the rule corresponding to a given item, by finding the best pattern match.
     *
     * @param item    the item to be matched
     * @param context the XPath dynamic evaluation context
     * @return the best matching rule, if any (otherwise null).
     * @throws XPathException if an error occurs matching a pattern
     */

    public abstract Rule getRule(Item item, XPathContext context) throws XPathException;

    /**
     * Get the rule corresponding to a given item, by finding the best Pattern match.
     *
     * @param item    the item to be matched
     * @param context the XPath dynamic evaluation context
     * @param filter  a filter to select which rules should be considered
     * @return the best matching rule, if any (otherwise null).
     * @throws XPathException if an error occurs
     */


    /*@Nullable*/
    public abstract Rule getRule(Item item, XPathContext context, Predicate<Rule> filter) throws XPathException;

    /**
     * Get the rule corresponding to a given Node, by finding the best Pattern match, subject to a minimum
     * and maximum precedence. (This supports xsl:apply-imports)
     *
     * @param item    the item to be matched
     * @param min     the minimum import precedence
     * @param max     the maximum import precedence
     * @param context the XPath dynamic evaluation context
     * @return the Rule registered for that node, if any (otherwise null).
     * @throws XPathException if an error occurs evaluating match patterns
     */

    public Rule getRule(Item item, final int min, final int max, XPathContext context) throws XPathException {
        return getRule(item, context, r -> {
            int p = r.getPrecedence();
            return p >= min && p <= max;
        });
    }

    /**
     * Get the rule corresponding to a given Node, by finding the next-best Pattern match
     * after the specified object.
     *
     * @param item        the NodeInfo referring to the node to be matched
     * @param currentRule the current rule; we are looking for the next match after the current rule
     * @param context     the XPath dynamic evaluation context
     * @return the object (e.g. a NodeHandler) registered for that element, if any (otherwise null).
     * @throws XPathException if an error occurs matching a pattern
     */

    public Rule getNextMatchRule(Item item, final Rule currentRule, XPathContext context) throws XPathException {
        return getRule(item, context, r -> {
            int comp = r.compareRank(currentRule);
            if (comp < 0) {
                // the rule has lower precedence or priority than the current rule
                return true;
            } else if (comp == 0) {
                int seqComp = Integer.compare(r.getSequence(), currentRule.getSequence());
                if (seqComp < 0) {
                    // the rule is before the current rule in declaration order
                    return true;
                } else if (seqComp == 0) {
                    // we have two branches of the same union pattern; examine the parent pattern to see which is first
                    return r.getPartNumber() < currentRule.getPartNumber();
                }
            }
            return false;
        });
    }

    /**
     * Export all template rules in this mode in a form that can be re-imported.
     * Note that template rules with union patterns may have been split into multiple
     * rules. We need to avoid outputting them more than once.
     *
     * @param out used to display the expression tree
     */

    public abstract void exportTemplateRules(ExpressionPresenter out) throws XPathException;

    /**
     * Explain all template rules in this mode in a form that can be re-imported.
     * Note that template rules with union patterns may have been split into multiple
     * rules. We need to avoid outputting them more than once.
     *
     * @param out used to display the expression tree
     */

    public abstract void explainTemplateRules(ExpressionPresenter out) throws XPathException;


    /**
     * Process selected nodes using the template rules registered for this mode.
     *
     * @param parameters       A ParameterSet containing the parameters to
     *                         the handler/template being invoked. Specify null if there are no
     *                         parameters.
     * @param tunnelParameters A ParameterSet containing the parameters to
     *                         the handler/template being invoked. Specify null if there are no
     *                         parameters.
     * @param separator        Text node to be inserted between the output of successive input items;
     *                         may be null
     * @param output           The destination for the result of the selected templates
     * @param context          A newly-created context object (this must be freshly created by the caller,
     *                         as it will be modified by this method). The nodes to be processed are those
     *                         selected by the currentIterator in this context object. There is also a precondition
     *                         that this mode must be the current mode in this context object.
     * @param locationId       location of this apply-templates instruction in the stylesheet
     * @return a TailCall returned by the last template to be invoked, or null,
     * indicating that there are no outstanding tail calls.
     * @throws XPathException if any dynamic error occurs
     */

    /*@Nullable*/
    public TailCall applyTemplates(
            ParameterSet parameters,
            ParameterSet tunnelParameters,
            NodeInfo separator,
            Outputter output,
            XPathContextMajor context,
            Location locationId)
            throws XPathException {
        Controller controller = context.getController();
        SequenceIterator iterator = context.getCurrentIterator();
        TailCall tc = null;
        TraceListener traceListener = controller.isTracing() ? controller.getTraceListener() : null;

        // Iterate over this sequence

        LookaheadIterator lookaheadIter = (iterator instanceof LookaheadIterator li && li.supportsHasNext())
                        ? li
                        : null;
        TemplateRule previousTemplate = null;
        boolean first = true;

        while (true) {

            // process any tail calls returned from previous nodes. We need to do this before changing
            // the context. If we have a LookaheadIterator, we can tell whether we're positioned at the
            // end without changing the current position, and we can then return the last tail call to
            // the caller and execute it further down the stack, reducing the risk of running out of stack
            // space. In other cases, we need to execute the outstanding tail calls before moving the iterator

            if (tc != null) {
                if (lookaheadIter != null && !lookaheadIter.hasNext()) {
                    break;
                }
                do {
                    tc = tc.processLeavingTail();
                } while (tc != null);
            }

            Item item = iterator.next();
            if (item == null) {
                break;
            }

            if (separator != null) {
                if (first) {
                    first = false;
                } else {
                    output.append(separator);
                }
            }

            if (mustBeTyped) {
                checkMustBeTyped(item);
            } else if (mustBeUntyped) {
                checkMustByUntyped(item);
            }

            // find the template rule for this node

            if (traceListener != null) {
                traceListener.startRuleSearch();
            }

            Rule rule = getRule(item, context);

            if (traceListener != null) {
                handleTraceListener(rule, item, traceListener);
            }

            TemplateRuleTraceListener ruleTraceListener = null;
            if (modeTracing) {
                ruleTraceListener = handleRuleTraceListener(ruleTraceListener, controller, locationId, item, rule);
            }

            if (rule == null) {             // Use the default action for the node
                // No need to open a new stack frame
                getBuiltInRuleSet().process(item, parameters, tunnelParameters, output, context, locationId);

            } else {
                RuleInfo result = handleRuleNotNull(rule, traceListener, context, item, previousTemplate, parameters, tunnelParameters, output);
                tc = result.tc();
                previousTemplate = result.rule();
            }
            if (modeTracing) {
                ruleTraceListener.leave();
            }
        }

        // return the TailCall returned from the last node processed
        return tc;
    }

    
    private record RuleInfo (TailCall tc, TemplateRule rule) {}

    private void checkMustBeTyped(Item item) throws XPathException
    {
        if (item instanceof NodeInfo node) {
            int kind = node.getNodeKind();
            if (kind == Type.ELEMENT || kind == Type.ATTRIBUTE) {
                SchemaType annotation = node.getSchemaType();
                if (annotation == Untyped.INSTANCE || annotation == BuiltInAtomicType.UNTYPED_ATOMIC) {
                    throw new XPathException(getModeTitle(true) + " requires typed nodes, but the input is untyped", "XTTE3100");
                }
            }
        }
    }

    private void checkMustByUntyped(Item item) throws XPathException
    {
        if (item instanceof NodeInfo node) {
            int kind = node.getNodeKind();
            if (kind == Type.ELEMENT || kind == Type.ATTRIBUTE) {
                SchemaType annotation = node.getSchemaType();
                if (!(annotation == Untyped.INSTANCE || annotation == BuiltInAtomicType.UNTYPED_ATOMIC)) {
                    throw new XPathException(getModeTitle(true) + " requires untyped nodes, but the input is typed", "XTTE3110");
                }
            }
        }
    }

    private RuleInfo handleRuleNotNull(Rule rule, TraceListener traceListener, XPathContextMajor context, Item item, TemplateRule previousTemplate, ParameterSet parameters,
            ParameterSet tunnelParameters, Outputter output) throws XPathException
    {
        TemplateRule template = (TemplateRule) rule.getAction();
        if (template != previousTemplate) {
            // Reuse the previous stackframe unless it's a different template rule
            previousTemplate = template;
            template.initialize();
            context.openStackFrame(template.getStackFrameMap());
            context.setLocalParameters(parameters);
            context.setTunnelParameters(tunnelParameters);
            context.setCurrentMergeGroupIterator(null);
        }
        context.setCurrentTemplateRule(rule);
        TailCall tc;
        if (traceListener != null) {
            traceListener.startCurrentItem(item);
            tc = template.applyLeavingTail(output, context);
            if (tc != null) {
                // disable tail call optimization while tracing
                do {
                    tc = tc.processLeavingTail();
                } while (tc != null);
            }

            traceListener.endCurrentItem(item);
        } else {
            tc = template.applyLeavingTail(output, context);
        }

        return new RuleInfo(tc, previousTemplate);
    }

    private static TemplateRuleTraceListener handleRuleTraceListener(TemplateRuleTraceListener ruleTraceListener, Controller controller, Location locationId, Item item, Rule rule)
    {
        ruleTraceListener = ((XsltController)controller).getTemplateRuleTraceListener();
        if (ruleTraceListener == null) {
            ruleTraceListener = new TemplateRuleTraceListener(controller.getConfiguration().getLogger());
            ((XsltController) controller).setTemplateRuleTraceListener(ruleTraceListener);
        }
        ruleTraceListener.enter("apply-templates", locationId, item, rule==null ? null : (TemplateRule)rule.getAction());

        return ruleTraceListener;
    }

    private void handleTraceListener(Rule rule, Item item, TraceListener traceListener)
    {
        if (rule == null) {
            traceListener.endRuleSearch(getBuiltInRuleSet(), this, item);
        } else {
            traceListener.endRuleSearch(rule, this, item);
        }
    }


    public abstract int getStackFrameSlotsNeeded();

    /**
     * Return a code string for a built-in rule set. This can be specialised in subclasses for PE/EE
     *
     * @param builtInRuleSet the rule set to get a code
     * @return a simple string code or "???" if the ruleset is unknown
     */
    public static String getCodeForBuiltInRuleSet(BuiltInRuleSet builtInRuleSet) {
        if (builtInRuleSet instanceof ShallowCopyAllRuleSet) {
            return "CA";
        } else if (builtInRuleSet instanceof ShallowCopyRuleSet) {
            return "SC";
        } else if (builtInRuleSet instanceof ShallowSkipRuleSet) {
            return "SS";
        } else if (builtInRuleSet instanceof DeepCopyRuleSet) {
            return "DC";
        } else if (builtInRuleSet instanceof DeepSkipRuleSet) {
            return "DS";
        } else if (builtInRuleSet instanceof FailRuleSet) {
            return "FF";
        } else if (builtInRuleSet instanceof TextOnlyCopyRuleSet) {
            return "TC";
        } else if (builtInRuleSet instanceof RuleSetWithWarnings) {
            return getCodeForBuiltInRuleSet(((RuleSetWithWarnings) builtInRuleSet).getBaseRuleSet()) + "+W";
        } else if (builtInRuleSet instanceof RuleSetWithTypeCheck) {
            return getCodeForBuiltInRuleSet(((RuleSetWithTypeCheck) builtInRuleSet).getBaseRuleSet()) + "+C";
        } else {
            return "???";
        }
    }

    /**
     * Return a built-in rule set for a code string. 
     *
     * @param code the code used in export
     * @return a suitable ruleset
     */
    public static BuiltInRuleSet getBuiltInRuleSetForCode(String code, SequenceType requiredType, boolean copyNamespaces) {
        BuiltInRuleSet base;
        if (code.startsWith("SC")) {
            base = ShallowCopyRuleSet.getInstance(copyNamespaces);
        } else if (code.startsWith("SS")) {
            base = ShallowSkipRuleSet.getInstance();
        } else if (code.startsWith("DC")) {
            base = DeepCopyRuleSet.getInstance(copyNamespaces);
        } else if (code.startsWith("DS")) {
            base = DeepSkipRuleSet.getInstance();
        } else if (code.startsWith("FF")) {
            base = FailRuleSet.getInstance();
        } else if (code.startsWith("TC")) {
            base = TextOnlyCopyRuleSet.getInstance();
        } else if (code.startsWith("CA")) {
            base = ShallowCopyAllRuleSet.getInstance(copyNamespaces);
        } else {
            throw new IllegalArgumentException(code);
        }
        if (code.contains("+W")) {
            base = new RuleSetWithWarnings(base);
        }
        if (code.contains("+C")) {
            base = new RuleSetWithTypeCheck(base, requiredType);
        }
        return base;
    }

    @Override
    public final void export(ExpressionPresenter presenter) throws XPathException {
        int s = presenter.startElement("mode");
        if (!isUnnamedMode()) {
            presenter.emitAttribute("name", getModeName());
        }
        presenter.emitAttribute("onNo", getCodeForBuiltInRuleSet(getBuiltInRuleSet()));
        if (getBuiltInRuleSet() instanceof RuleSetWithTypeCheck) {
            presenter.emitAttribute("as", ((RuleSetWithTypeCheck)getBuiltInRuleSet()).getRequiredType().toAlphaCode());
        }
        String flags = "";
        if (isDeclaredStreamable()) {
            flags += "s";
        }
        if (isUnnamedMode()) {
            flags += "d";
        }
        if (mustBeTyped) {
            flags += "t";
        }
        if (mustBeUntyped) {
            flags += "u";
        }
        if (recoveryPolicy == RecoveryPolicy.DO_NOT_RECOVER) {
            flags += "F";
        } else if (recoveryPolicy == RecoveryPolicy.RECOVER_WITH_WARNINGS) {
            flags += "W";
        }
        if (!hasRules) {
            flags += "e";
        }
        if (!isCopyNamespaces()) {
            flags += "n";
        }
        if (!flags.isEmpty()) {
            presenter.emitAttribute("flags", flags);
        }
        exportUseAccumulators(presenter);
        presenter.emitAttribute("patternSlots", getStackFrameSlotsNeeded() + "");
        exportTemplateRules(presenter);
        int e = presenter.endElement();
        if (s != e) {
            throw new IllegalStateException("Export tree unbalanced for mode " + getModeName());
        }
    }

    protected void exportUseAccumulators(ExpressionPresenter presenter){}

    public boolean isMustBeTyped() {
        return mustBeTyped;
    }

    public abstract boolean isCopyNamespaces();

    public void explain(ExpressionPresenter presenter) throws XPathException {
        int s = presenter.startElement("mode");
        if (!isUnnamedMode()) {
            presenter.emitAttribute("name", getModeName());
        }
        presenter.emitAttribute("onNo", getCodeForBuiltInRuleSet(getBuiltInRuleSet()));
        String flags = "";
        if (isDeclaredStreamable()) {
            flags += "s";
        }
        if (isUnnamedMode()) {
            flags += "d";
        }
        if (mustBeTyped) {
            flags += "t";
        }
        if (mustBeUntyped) {
            flags += "u";
        }
        if (recoveryPolicy == RecoveryPolicy.DO_NOT_RECOVER) {
            flags += "F";
        } else if (recoveryPolicy == RecoveryPolicy.RECOVER_WITH_WARNINGS) {
            flags += "W";
        }
        if (!flags.isEmpty()) {
            presenter.emitAttribute("flags", flags);
        }

        presenter.emitAttribute("patternSlots", getStackFrameSlotsNeeded() + "");
        explainTemplateRules(presenter);
        int e = presenter.endElement();
        if (s != e) {
            throw new IllegalStateException("tree unbalanced");
        }
    }

    /**
     * Interface for helper classes used to filter a chain of rules
     */

    public interface RuleFilter {
        /**
         * Test a rule to see whether it should be included
         *
         * @param r the rule to be tested
         * @return true if the rule qualifies
         */
        boolean testRule(Rule r);
    }

    /**
     * Interface for helper classes used to process all the rules in the Mode
     */

    @FunctionalInterface
    @CSharpDelegate(true)
    public interface RuleAction {
        /**
         * Process a given rule
         *
         * @param r the rule to be processed
         * @throws XPathException if an error occurs, for example a dynamic error in evaluating a pattern
         */
        void processRule(Rule r) throws XPathException;
    }

}
