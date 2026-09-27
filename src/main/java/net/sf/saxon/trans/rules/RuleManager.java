////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans.rules;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.Pattern;
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

    private final StylesheetPackage stylesheetPackage;
    private final Configuration config;
    private final SimpleMode unnamedMode;           // template rules with default mode
    private final HashMap<StructuredQName, Mode> modes;
    // tables of rules for non-default modes
    //private SimpleMode omniMode = null;       //template rules that specify mode="all"
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
     * Get the stylesheet package
     *
     * @return the stylesheet package
     */

    public StylesheetPackage getStylesheetPackage() {
        return stylesheetPackage;
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
        if (modeName.equals(Mode.OMNI_MODE_NAME)) {
            throw new IllegalArgumentException("#all is not a real mode");
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
     * @see Pattern
     */

    public void registerRule(Pattern pattern, TemplateRule eh,
                            Mode mode, StylesheetModule module, double priority, int position, int part) {

        if (Double.isNaN(priority)) {
            priority = pattern.getDefaultPriority();
        }

        if (mode instanceof SimpleMode) {
            ((SimpleMode) mode).addRule(pattern, eh, module, module.getPrecedence(), priority, position, part);
        } else {
            mode.getActivePart().addRule(pattern, eh, module, mode.getMaxPrecedence(), priority, position, part);
        }
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
        unnamedMode.resolveProperties(this);
        for (Mode mode : modes.values()) {
            mode.getActivePart().resolveProperties(this);
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

