////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.s9api.Location;

/**
 * The ExpressionVisitor supports the various phases of processing of an expression tree which require
 * a recursive walk of the tree structure visiting each node in turn. In maintains a stack holding the
 * ancestor nodes of the node currently being visited.
 */

public class ExpressionVisitor  {

    private StaticContext staticContext;
    private boolean optimizeForStreaming = false;
    private boolean optimizeForPatternMatching = false;
    private Configuration config;
    private Optimizer optimizer;
    private int depth = 0;

    private boolean suppressWarnings = false;

    private final static int MAX_DEPTH = 500;


    /**
     * Create an ExpressionVisitor
     * @param config the Saxon configuration
     */

    public ExpressionVisitor(Configuration config) {
        this.config = config;
    }

    /**
     * Get the Configuration
     * @return the configuration
     */
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Get the static context for the expressions being visited. Note: this may not reflect all changes
     * in static context (e.g. namespace context, base URI) applying to nested expressions
     *
     * @return the static context
     */

    public StaticContext getStaticContext() {
        return staticContext;
    }

    /**
     * Set the static context for the expressions being visited. Note: this may not reflect all changes
     * in static context (e.g. namespace context, base URI) applying to nested expressions
     *
     * @param staticContext the static context
     */

    public void setStaticContext(StaticContext staticContext) {
        this.staticContext = staticContext;
    }

    /**
     * Factory method: make an expression visitor
     *
     * @param env the static context
     * @return the new expression visitor
     */

    public static ExpressionVisitor make(StaticContext env) {
        ExpressionVisitor visitor = new ExpressionVisitor(env.getConfiguration());
        visitor.setStaticContext(env);
        return visitor;
    }

    /**
     * Issue a warning message
     *
     * @param message the message
     * @param locator the query/stylesheet location associated with the message
     */

    public void issueWarning(String message, Location locator) {
        if (!isSuppressWarnings()) {
            staticContext.issueWarning(message, locator);
        }
    }

    /**
     * Create a dynamic context suitable for early evaluation of constant subexpressions
     * @return a dynamic evaluation context
     */

    public XPathContext makeDynamicContext() {
        return staticContext.makeEarlyEvaluationContext();
    }

    /**
     * Obtain an optimizer. The optimizer is created on the first call of this method and is
     * retained for use in subsequent calls.
     * @return a suitable optimizer, taking into account the optimization options defined by the
     * Configuration and by the static context
     */

    public Optimizer obtainOptimizer() {
        if (optimizer == null) {
            optimizer = config.obtainOptimizer(staticContext.getOptimizerOptions());
        }
        return optimizer;
    }

    /**
     * Tell the visitor to optimize expressions for evaluation in a streaming environment
     *
     * @param option true if optimizing for streaming
     */

    public void setOptimizeForStreaming(boolean option) {
        optimizeForStreaming = option;
    }

    /**
     * Ask whether the visitor is to optimize expressions for evaluation in a streaming environment
     *
     * @return true if optimizing for streaming
     */

    public boolean isOptimizeForStreaming() {
        return optimizeForStreaming;
    }

    /**
     * Tell the visitor to optimize expressions for evaluation as an XSLT match pattern
     *
     * @param option true if optimizing for pattern matching
     */

    public void setOptimizeForPatternMatching(boolean option) {
        optimizeForPatternMatching = option;
    }

    /**
     * Ask whether the visitor is to optimize expressions for evaluation as an XSLT match pattern
     *
     * @return true if optimizing for pattern matching
     */

    public boolean isOptimizeForPatternMatching() {
        return optimizeForPatternMatching;
    }

    /**
     * Get the target environment for which we are optimizing
     * @return the target environment (for example "EE" or "JS"). Defaults to the edition
     * of the current configuration
     */

    public String getTargetEdition() {
        return staticContext.getPackageData().getTargetEdition();
    }

    /**
     * Increment and test depth
     * @return true if the current depth is less than a maximum limit; and increment the current depth
     */

    public boolean incrementAndTestDepth() {
        return depth++ < MAX_DEPTH;
    }

    /**
     * Decrement depth
     */

    public void decrementDepth() {
        depth--;
    }

    /**
     * Ask if warnings have been suppressed. This typically happens when processing code that has been copied,
     * to prevent multiple warnings for the same code
     * @return true if warnings are suppressed
     */

    public boolean isSuppressWarnings() {
        return suppressWarnings;
    }

    /**
     * Say whether warnings should be suppressed. This is typically done when processing code that has been
     * copied, to prevent multiple warnings for the same code
     * @param suppressWarnings set to true to suppress warnings
     */

    public void setSuppressWarnings(boolean suppressWarnings) {
        this.suppressWarnings = suppressWarnings;
    }

}

