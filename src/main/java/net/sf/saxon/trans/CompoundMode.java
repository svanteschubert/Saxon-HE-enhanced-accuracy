////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.ComponentBinding;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.rules.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A CompoundMode is a mode representing the templates contained within an xsl:override element in a using package
 * together with the rules in the corresponding mode of the base package.
 */
public class CompoundMode extends Mode {

    private Mode base;
    private SimpleMode overrides;
    private int overridingPrecedence;

    /**
     * Create a compound Mode
     * @param base the mode from the base (used) package
     * @param overrides the mode containing (only) the overriding template rules from the using package
     */

    public CompoundMode(Mode base, SimpleMode overrides) {
        super(base.getModeName());
        if (!base.getModeName().equals(overrides.getModeName())) {
            throw new AssertionError("Base and overriding modes must have the same name");
        }
        if (base.getModeName().equals(Mode.UNNAMED_MODE_NAME)) {
            throw new AssertionError("Cannot override an unnamed mode");
        }
        if (base.getModeName().equals(Mode.OMNI_MODE)) {
            throw new AssertionError("Cannot override mode='#all'");
        }
        this.base = base;
        this.overrides = overrides;
        this.mustBeTyped = base.mustBeTyped;
        this.mustBeUntyped = base.mustBeUntyped;
        this.overridingPrecedence = base.getMaxPrecedence() + 1;
    }

    /**
     * Get the built-in template rules to be used with this Mode in the case where there is no
     * explicit template rule
     *
     * @return the built-in rule set, defaulting to the TextOnlyCopyRuleSet if no other rule set has
     * been supplied
     */

    @Override
    public BuiltInRuleSet getBuiltInRuleSet() {
        return base.getBuiltInRuleSet();
    }

    /**
     * Get the active component of this mode. For a simple mode this is the mode itself;
     * for a compound mode it is the "overriding" part
     */
    @Override
    public SimpleMode getActivePart() {
        return overrides;
    }

    /**
     * Ask whether there are any template rules in this mode
     * (a mode could exist merely because it is referenced in apply-templates)
     *
     * @return true if no template rules exist in this mode
     */
    @Override
    public boolean isEmpty() {
        return base.isEmpty() && overrides.isEmpty();
    }

    /**
     * Get the maximum precedence of the rules in this mode
     */
    @Override
    public int getMaxPrecedence() {
        return overridingPrecedence;
    }

    /**
     * Get the highest rank of the rules in this mode
     * @return the highest rank
     */

    @Override
    public int getMaxRank() {
        return overrides.getMaxRank();
    }

    /**
     * Compute a rank for each rule, as a combination of the precedence and priority, to allow
     * rapid comparison.  This method also checks that there are no conflicts for
     * property values in different xsl:mode declarations
     *
     * @param start the lowest rank to use
     * @throws XPathException if an error occurs processing the rules
     */

    @Override
    public void computeRankings(int start) throws XPathException {
        overrides.computeRankings(base.getMaxRank() + 1);
    }

    /**
     * Walk over all the rules, applying a specified action to each one.
     *
     * @param action an action that is to be applied to all the rules in this Mode
     * @throws XPathException if an error occurs processing any of the rules
     */
    @Override
    public void processRules(RuleAction action) throws XPathException {
        overrides.processRules(action);
        base.processRules(action);
    }

    /**
     * Get the "explicit namespaces" matched by this mode. Returns a set containing all the namespaces
     * matched by specific template rules in this mode
     *
     * @param pool the NamePool for the configuration
     * @return the set of all namespace URIs of names explicitly matched by rules in this mode
     */
    @Override
    public Set<String> getExplicitNamespaces(NamePool pool) {
        HashSet<String> r = new HashSet<String>();
        r.addAll(base.getExplicitNamespaces(pool));
        r.addAll(overrides.getExplicitNamespaces(pool));
        return r;
    }

    /**
     * Allocate slot numbers to all the external component references in this component
     *
     * @param pack the containing package
     */

    @Override
    public void allocateAllBindingSlots(final StylesheetPackage pack) {
        if (!bindingSlotsAllocated) {
            List<ComponentBinding> baseBindings = base.getDeclaringComponent().getComponentBindings();
            List<ComponentBinding> newBindings = new ArrayList<ComponentBinding>(baseBindings);
            Component comp = getDeclaringComponent();
            comp.setComponentBindings(newBindings);
            SimpleMode.forceAllocateAllBindingSlots(pack, overrides, newBindings);
            bindingSlotsAllocated = true;
        }
    }

    /**
     * Get the rule corresponding to a given item, by finding the best pattern match.
     *
     * @param item    the item to be matched
     * @param context the XPath dynamic evaluation context
     * @return the best matching rule, if any (otherwise null).
     * @throws XPathException if an error occurs matching a pattern
     */

    @Override
    public Rule getRule(Item item, XPathContext context) throws XPathException {
        Rule r = overrides.getRule(item, context);
        if (r == null) {
            r = base.getRule(item, context);
        }
        return r;
    }

    @Override
    public int getStackFrameSlotsNeeded() {
        return Math.max(base.getStackFrameSlotsNeeded(), overrides.getStackFrameSlotsNeeded());
    }

    /**
     * Get the rule corresponding to a given item, by finding the best Pattern match.
     *
     * @param item    the item to be matched
     * @param context the XPath dynamic evaluation context
     * @param filter  a filter to select which rules should be considered
     * @return the best matching rule, if any (otherwise null).
     * @throws XPathException if an error occurs
     */
    @Override
    public Rule getRule(Item item, XPathContext context, SimpleMode.RuleFilter filter) throws XPathException {
        Rule r = overrides.getRule(item, context, filter);
        if (r == null) {
            r = base.getRule(item, context, filter);
        }
        return r;
    }

    /**
     * Export expression structure. The abstract expression tree
     * is written to the supplied outputstream.
     *
     * @param presenter the expression presenter used to generate the XML representation of the structure
     */
    @Override
    public void exportTemplateRules(ExpressionPresenter presenter) throws XPathException {
        overrides.exportTemplateRules(presenter);
        base.exportTemplateRules(presenter);
    }

    /**
     * Explain expression structure. The abstract expression tree
     * is written to the supplied outputstream.
     *
     * @param presenter the expression presenter used to generate the XML representation of the structure
     */
    @Override
    public void explainTemplateRules(ExpressionPresenter presenter) throws XPathException {
        overrides.explainTemplateRules(presenter);
        base.explainTemplateRules(presenter);
    }
}
