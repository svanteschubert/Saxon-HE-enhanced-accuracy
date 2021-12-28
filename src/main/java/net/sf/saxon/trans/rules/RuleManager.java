////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.*;
import net.sf.saxon.style.StylesheetModule;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.*;

import java.util.Collection;
import java.util.HashMap;

/**
 * <B>RuleManager</B> maintains a set of template rules, one set for each mode
 */

public final class RuleManager {

    private StylesheetPackage stylesheetPackage;
    private Configuration config;
    private SimpleMode unnamedMode;           // template rules with default mode
    private HashMap<StructuredQName, Mode> modes;
    // tables of rules for non-default modes
    private SimpleMode omniMode = null;       //template rules that specify mode="all"
    private boolean unnamedModeExplicit;
    private CompilerInfo compilerInfo; // We may need access to information on the compilation as distinct from the configuration
    private int nextSequenceNumber = 0;

    /**
     * create a RuleManager and initialise variables.
     */

    public RuleManager(StylesheetPackage pack) {
        this(pack, pack.getConfiguration().getDefaultXsltCompilerInfo());
    }

    public RuleManager(StylesheetPackage pack, CompilerInfo compilerInfo) {
        this.stylesheetPackage = pack;
        this.config = pack.getConfiguration();
        this.compilerInfo = compilerInfo;
        this.unnamedMode = config.makeMode(Mode.UNNAMED_MODE_NAME, this.compilerInfo);
        Component c = unnamedMode.makeDeclaringComponent(Visibility.PRIVATE, stylesheetPackage);
        c.setVisibility(Visibility.PRIVATE, VisibilityProvenance.DEFAULTED);
        this.stylesheetPackage.addComponent(c);
        this.modes = new HashMap<>(5);
    }

    /**
     * Say that the unnamed mode has been explicitly declared in an xsl:mode declaration
     *
     * @param declared true if it has been explicitly declared
     */

    public void setUnnamedModeExplicit(boolean declared) {
        unnamedModeExplicit = declared;
    }

    /**
     * Ask whether the unnamed mode has been explicitly declared in an xsl:mode declaration
     *
     * @return true if it has been explicitly declared
     */

    public boolean isUnnamedModeExplicit() {
        return unnamedModeExplicit;
    }

    /**
     * Set the compiler information specifically.
     *
     * @param compilerInfo the compiler options in use
     */
    public void setCompilerInfo(CompilerInfo compilerInfo) {
        this.compilerInfo = compilerInfo;
    }

    /**
     * Get all registered modes
     *
     * @return a collection containing all registered modes excluding the unnamed mode
     */

    public Collection<Mode> getAllNamedModes() {
        return modes.values();
    }

    /**
     * Get the mode object for the unnamed mode
     *
     * @return the unnamed mode
     */

    /*@NotNull*/
    public SimpleMode getUnnamedMode() {
        return unnamedMode;
    }

    /**
     * Get the Mode object for a named mode. If there is not one already registered.
     * a new Mode is created. This uses a makeMode() method in the configuration
     *
     * @param modeName       The name of the mode. Supply null to get the default
     *                       mode or Mode.ALL_MODES to get the Mode object containing "mode=all" rules
     * @param createIfAbsent if true, then if the mode does not already exist it will be created.
     *                       If false, then if the mode does not already exist the method returns null.
     *                       But if the requested mode is the omnimode, it is considered to always exist,
     *                       and is therefore created regardless.
     * @return the Mode with this name
     */


    /*@Nullable*/
    public Mode obtainMode(StructuredQName modeName, boolean createIfAbsent) {
        if (modeName == null || modeName.equals(Mode.UNNAMED_MODE_NAME)) {
            return unnamedMode;
        }
        if (modeName.equals(Mode.OMNI_MODE)) {
            if (omniMode == null) {
                omniMode = config.makeMode(modeName, compilerInfo);
            }
            return omniMode;
        }

        Mode m = modes.get(modeName);
        if (m == null && createIfAbsent) {
            m = config.makeMode(modeName, compilerInfo);
            modes.put(modeName, m);
            Component c = m.makeDeclaringComponent(Visibility.PRIVATE, stylesheetPackage);
            c.setVisibility(Visibility.PRIVATE, VisibilityProvenance.DEFAULTED);
            stylesheetPackage.addComponent(c);
        }
        return m;
    }

    public void registerMode(Mode mode) {
        modes.put(mode.getModeName(), mode);
    }

    public boolean existsOmniMode() {
        return omniMode != null;
    }

    public int allocateSequenceNumber() {
        return nextSequenceNumber++;
    }

    /**
     * Register a template for a particular pattern.
     *
     * @param pattern  Must be a valid Pattern.
     * @param eh       The Template to be used
     * @param mode     The processing mode to which this template applies
     * @param module   The stylesheet module containing the template rule
     * @param priority The priority of the rule: if an element matches several patterns, the
     *                 one with highest priority is used. The value is NaN if no explicit
     *                 priority was specified
     * @param position The relative position of the rule in declaration order
     * @param part     Zero for a "real" rule; an incremented integer for rules generated
     *                 by splitting a real rule on a union pattern, in cases where no user-specified
     *                 priority is supplied.
     * @return the number of (sub-)rules registered
     * @see Pattern
     */

    public int registerRule(Pattern pattern, TemplateRule eh,
                            Mode mode, StylesheetModule module, double priority, int position, int part) {

        // for a union pattern, register the parts separately
        // Technically this is only necessary if using default priorities and if the priorities
        // of the two halves are different. However, splitting increases the chance that the pattern
        // can be matched by hashing on the element name, so we do it always. But we need to do it
        // in such a way that next-match only processes the template once (test case next-match-024)
        if (pattern instanceof UnionPattern) {
            UnionPattern up = (UnionPattern) pattern;
            Pattern p1 = up.getLHS();
            Pattern p2 = up.getRHS();
            int lhsParts = registerRule(p1, eh, mode, module, priority, position, part);
            int rhsParts = registerRule(p2, eh, mode, module, priority, position, lhsParts);
            return lhsParts + rhsParts;
        }
        // some union patterns end up as a CombinedNodeTest. Need to split these.
        // (Same reasoning as above)
        if (pattern instanceof NodeTestPattern &&
                pattern.getItemType() instanceof CombinedNodeTest &&
                ((CombinedNodeTest) pattern.getItemType()).getOperator() == Token.UNION) {
            CombinedNodeTest cnt = (CombinedNodeTest) pattern.getItemType();
            NodeTest[] nt = cnt.getComponentNodeTests();
            final NodeTestPattern nt0 = new NodeTestPattern(nt[0]);
            ExpressionTool.copyLocationInfo(pattern, nt0);
            int lhsParts = registerRule(nt0, eh, mode, module, priority, position, part);
            final NodeTestPattern nt1 = new NodeTestPattern(nt[1]);
            ExpressionTool.copyLocationInfo(pattern, nt1);
            int rhsParts = registerRule(nt1, eh, mode, module, priority, position, lhsParts);
            return lhsParts + rhsParts;
        }
        if (Double.isNaN(priority)) {
            priority = pattern.getDefaultPriority();
        } else {
            // Priorities were user-allocated, so the rule-splitting is purely an optimization, so
            // we give all sub-rules the same part number, meaning that next-match will only
            // evaluate one of them
            part = 0;
        }

        if (mode instanceof SimpleMode) {
            ((SimpleMode) mode).addRule(pattern, eh, module, module.getPrecedence(), priority, position, part);
        } else {
            mode.getActivePart().addRule(pattern, eh, module, mode.getMaxPrecedence(), priority, position, part);
        }
        return 1;
    }


    /**
     * Get the template rule matching a given item whose import precedence
     * is in a particular range. This is used to support the xsl:apply-imports function
     *
     * @param item The item to be matched
     * @param mode The mode for which a rule is required
     * @param min  The minimum import precedence that the rule must have
     * @param max  The maximum import precedence that the rule must have
     * @param c    The Controller for the transformation
     * @return The template rule to be invoked
     * @throws XPathException if an error occurs matching a pattern
     */

    public Rule getTemplateRule(Item item, Mode mode, int min, int max, XPathContext c)
            throws XPathException {
        // DO NOT DELETE: USED FROM BYTECODE
        if (mode == null) {
            mode = unnamedMode;
        }
        return mode.getRule(item, min, max, c);
    }

    /**
     * Allocate rankings to the rules within each mode. This method must be called when all
     * the rules in each mode are known. This method also checks that there are no conflicts for
     * property values in different xsl:mode declarations
     *
     * @throws XPathException if an error occurs
     */

    public void computeRankings() throws XPathException {
        unnamedMode.computeRankings(0);
        for (Mode mode : modes.values()) {
            mode.computeRankings(0);
        }
    }

    /**
     * Invert streamable templates in all streamable modes
     *
     * @throws XPathException if the templates are not streamable
     */

    public void invertStreamableTemplates() throws XPathException {
        unnamedMode.invertStreamableTemplates();
        for (Mode mode : modes.values()) {
            mode.getActivePart().invertStreamableTemplates();
        }
    }

    /**
     * Check all modes for conflicts in property values
     *
     * @throws XPathException if a mode has conflicting property values
     */

    public void checkConsistency() throws XPathException {
        unnamedMode.checkForConflictingProperties();
        for (Mode mode : modes.values()) {
            mode.getActivePart().checkForConflictingProperties();
        }
    }

    /**
     * Explain (that is, output the expression tree) for all template rules
     *
     * @param presenter the object used to present the output
     */

    public void explainTemplateRules(ExpressionPresenter presenter) throws XPathException {
        unnamedMode.explain(presenter);
        for (Mode mode : modes.values()) {
            mode.explain(presenter);
        }
    }

    /**
     * Optimization of template rules
     * Only invoked when rule optimization has been turned on.
     * In this case we know the modes are instances of at least ModeEE
     */
    public void optimizeRules() {
        unnamedMode.optimizeRules();
        for (Mode mode : modes.values()) {
            mode.getActivePart().optimizeRules();
        }
    }
}

