////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.functions.Current;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.style.ExpressionContext;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.*;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;

/**
 * A Pattern represents the result of parsing an XSLT pattern string. <p>
 * Patterns are created by calling the static method Pattern.make(string). <p>
 * The pattern is used to test a particular node by calling match().
 */

public abstract class Pattern extends PseudoExpression {

    private double priority = 0.5;
    private boolean recoverable = true;
    private String originalText;

    /**
     * Static factory method to make a Pattern by parsing a String. <br>
     *
     * @param pattern     The pattern text as a String
     * @param env         An object defining the compile-time context for the expression
     * @param packageData The package containing this pattern
     * @return The pattern object
     * @throws net.sf.saxon.trans.XPathException if the pattern is invalid
     */

    public static Pattern make(String pattern, StaticContext env, PackageData packageData) throws XPathException {

        int languageLevel = env.getConfiguration().getConfigurationProperty(Feature.XPATH_VERSION_FOR_XSLT);
        if (languageLevel == 30) {
            languageLevel = 305; // XPath 3.0 + XSLT extensions
        }
        int lineNumber = env instanceof ExpressionContext ? ((ExpressionContext) env).getStyleElement().getLineNumber() : -1;
        PatternParser parser = (PatternParser) env.getConfiguration().newExpressionParser("PATTERN", false, languageLevel);
        ((XPathParser) parser).setLanguage(XPathParser.ParsedLanguage.XSLT_PATTERN, 30);
        Pattern pat = parser.parsePattern(pattern, env);
        pat.setRetainedStaticContext(env.makeRetainedStaticContext());
        // System.err.println("Simplified [" + pattern + "] to " + pat.getClass() + " default prio = " + pat.getDefaultPriority());
        pat = pat.simplify();
        return pat;
    }

    /**
     * Replace any call to current() within a contained expression by a reference to a variable
     * @param exp the expression in which the replacement is to take place (which must not itself be
     *            a call to current())
     * @param binding the binding for the variable reference
     */

    protected static void replaceCurrent(Expression exp, LocalBinding binding) {
        for (Operand o : exp.operands()) {
            Expression child = o.getChildExpression();
            if (child.isCallOn(Current.class)) {
                LocalVariableReference ref = new LocalVariableReference(binding);
                o.setChildExpression(ref);
            } else {
                replaceCurrent(child, binding);
            }
        }
    }

    /**
     * Ask whether a pattern has dependencies on local variables
     * @param pattern the pattern (typically a pattern in an xsl:number or xsl:for-each-group); or null
     * @return true if the pattern is non-null and has dependencies on local variables
     */

    public static boolean patternContainsVariable (Pattern pattern) {
        return pattern != null &&
                (pattern.getDependencies() & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES) != 0;
    }

    /**
     * Ask whether the expression can be lifted out of a loop, assuming it has no dependencies
     * on the controlling variable/focus of the loop
     * @param forStreaming true if we are generating code for streaming
     */

    @Override
    public boolean isLiftable(boolean forStreaming) {
        return false;
    }

    /**
     * Replace any calls on current() by a variable reference bound to the supplied binding
     */

    public void bindCurrent(LocalBinding binding) {
        // default: no action
    }

    /**
     * Ask whether the pattern is anchored on a call on current-group()
     * @return true if calls on matchesBeneathAnchor should test with all nodes in the
     * current group as anchor nodes. If false, only the first node in a group is
     * treated as the anchor node
     */

    public boolean matchesCurrentGroup() {
        return false;
    }

    /**
     * Set the original text of the pattern for use in diagnostics
     *
     * @param text the original text of the pattern
     */

    public void setOriginalText(String text) {
        originalText = text;
    }

    /**
     * Ask whether dynamic errors in evaluating the pattern should be recovered. This is the default
     * for normal XSLT patterns, but patterns used internally to represent scannable streaming expressions
     * are non-recoverable
     * @return true if dynamic errors in pattern evaluation should be treated as recoverable (if an error
     * occurs, the pattern is treated as non-matching; a warning is sent to the ErrorListener).
     */
    public boolean isRecoverable() {
        return recoverable;
    }

    /**
     * Aay whether dynamic errors in evaluating the pattern should be recovered. This is the default
     * for normal XSLT patterns, but patterns used internally to represent scannable streaming expressions
     * are non-recoverable
     *
     * @param recoverable true if dynamic errors in pattern evaluation should be treated as recoverable (if an error
     * occurs, the pattern is treated as non-matching; a warning is sent to the ErrorListener).
     */

    public void setRecoverable(boolean recoverable) {
        this.recoverable = recoverable;
    }

    /**
     * Handle a dynamic error occurring in the course of pattern evaluation. The effect depends on (a) whether
     * the error is a circularity error, and (b) whether the pattern is marked as recoverable or not
     * @param ex  the exception
     * @param context the evaluation context
     * @throws XPathException if the error is found to be non-recoverable
     */

    protected void handleDynamicError(XPathException ex, XPathContext context) throws XPathException {
        if ("XTDE0640".equals(ex.getErrorCodeLocalPart())) {
            // Treat circularity error as fatal (test error213)
            throw ex;
        }
        if (!isRecoverable()) {
            // Typically happens when this is a pseudo-pattern used for scannable expressions when streaming
            throw ex;
        }
        context.getController().warning("An error occurred matching pattern {" + this + "}: " + ex.getMessage(),
                                        ex.getErrorCodeQName().getEQName(),
                                        getLocation());
    }


    /**
     * Simplify the pattern by applying any context-independent optimisations.
     * Default implementation does nothing.
     *
     */

    @Override
    public Pattern simplify() throws XPathException {
        return this;
    }

    /**
     * Type-check the pattern.
     *
     * @param visitor         the expression visitor
     * @param contextInfo     the type of the context item at the point where the pattern
     *                        is defined. Set to null if it is known that the context item is undefined.
     * @return the optimised Pattern
     */

    @Override
    public Pattern typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        return this;
    }

    /**
     * Get the dependencies of the pattern. The only interesting dependencies for a pattern are
     * dependencies on local variables or on user-defined functions. These are analyzed in those
     * patterns containing predicates.
     *
     * @return the dependencies, as a bit-significant mask
     */

    @Override
    public int getDependencies() {
        return 0;
    }

    /**
     * Allocate slots to any variables used within the pattern
     *
     * @param slotManager the slot manager representing the stack frame for local variables
     * @param nextFree    the next slot that is free to be allocated
     * @return the next slot that is free to be allocated
     */

    public int allocateSlots(SlotManager slotManager, int nextFree) {
        return nextFree;
    }

    /**
     * Test whether a pattern is motionless, that is, whether it can be evaluated against a node
     * without repositioning the input stream. This is a necessary condition for patterns used
     * as the match pattern of a streamed template rule.
     *
     * @return true if the pattern is motionless, that is, if it can be evaluated against a streamed
     * node without changing the position in the streamed input file
     */

    public boolean isMotionless() {
        // default implementation for subclasses
        return true;
    }

    /**
     * Evaluate a pattern as a boolean expression, returning true if the context item matches the pattern
     * @param context the evaluation context
     * @return true if the context item matches the pattern
     * @throws XPathException if an error occurs during pattern matching
     */

    @Override
    public final boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        return matches(context.getContextItem(), context);
    }

    /**
     * Determine whether this Pattern matches the given item. This is the main external interface
     * for matching patterns: it sets current() to the node being tested
     *
     * @param item    The item to be tested against the Pattern
     * @param context The dynamic context.
     * @return true if the node matches the Pattern, false otherwise
     * @throws XPathException if an error occurs while matching the pattern (the caller will usually
     *                        treat this the same as a false result)
     */

    public abstract boolean matches(Item item, XPathContext context) throws XPathException;

    /**
     * Determine whether this pattern matches a given Node within the subtree rooted at a given
     * anchor node. This method is used when the pattern is used for streaming.
     *
     * @param node    The NodeInfo representing the Element or other node to be tested against the Pattern
     * @param anchor  The anchor node, which must match any AnchorPattern subpattern
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the node matches the Pattern, false otherwise
     * @throws XPathException if an error occurs while matching the pattern (the caller will usually
     *                        treat this the same as a false result)
     */

    public boolean matchesBeneathAnchor(NodeInfo node, NodeInfo anchor, XPathContext context) throws XPathException {
        // default implementation ignores the anchor node
        return matches(node, context);
    }

    /**
     * Select all nodes in a document that match this pattern.
     *
     * @param document     the document
     * @param context the dynamic evaluation context
     * @return an iterator over the selected nodes in the document.
     */

    public SequenceIterator selectNodes(TreeInfo document, final XPathContext context) throws XPathException {
        NodeInfo doc = document.getRootNode();
        final UType uType = getUType();
        if (UType.DOCUMENT.subsumes(uType)) {
            if (matches(doc, context)) {
                return SingletonIterator.makeIterator(doc);
            } else {
                return EmptyIterator.ofNodes();
            }
        } else if (UType.ATTRIBUTE.subsumes(uType)) {
            AxisIterator allElements = doc.iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
            MappingFunction atts = item -> ((NodeInfo)item).iterateAxis(AxisInfo.ATTRIBUTE);
            SequenceIterator allAttributes = new MappingIterator(allElements, atts);
            ItemMappingFunction selection = item -> matches(item, context) ? (NodeInfo)item : null;
            return new ItemMappingIterator(allAttributes, selection);
        } else if (UType.NAMESPACE.subsumes(uType)) {
            AxisIterator allElements = doc.iterateAxis(AxisInfo.DESCENDANT, NodeKindTest.ELEMENT);
            MappingFunction atts = item -> ((NodeInfo)item).iterateAxis(AxisInfo.NAMESPACE);
            SequenceIterator allNamespaces = new MappingIterator(allElements, atts);
            ItemMappingFunction selection = item -> matches(item, context) ? (NodeInfo) item : null;
            return new ItemMappingIterator(allNamespaces, selection);

        } else if (UType.CHILD_NODE_KINDS.subsumes(uType)) {
            NodeTest nodeTest;
            if (uType.equals(UType.ELEMENT)) {
                nodeTest = NodeKindTest.ELEMENT; // common case, enables use of getAllElements()
            } else {
                nodeTest = new MultipleNodeKindTest(uType);
            }
            AxisIterator allChildren = doc.iterateAxis(AxisInfo.DESCENDANT, nodeTest);
            ItemMappingFunction selection = item -> matches(item, context) ? (NodeInfo)item : null;
            return new ItemMappingIterator(allChildren, selection);
        } else {
            int axis = uType.subsumes(UType.DOCUMENT) ? AxisInfo.DESCENDANT_OR_SELF : AxisInfo.DESCENDANT;
            AxisIterator allChildren = doc.iterateAxis(axis);
            MappingFunction processElement = item -> {
                AxisIterator mapper = SingleNodeIterator.makeIterator((NodeInfo)item);
                if (uType.subsumes(UType.NAMESPACE)) {
                    mapper = new ConcatenatingAxisIterator(mapper, ((NodeInfo)item).iterateAxis(AxisInfo.NAMESPACE));
                }
                if (uType.subsumes(UType.ATTRIBUTE)) {
                    mapper = new ConcatenatingAxisIterator(mapper, ((NodeInfo) item).iterateAxis(AxisInfo.ATTRIBUTE));
                }
                return mapper;
            };
            SequenceIterator attributesOrSelf = new MappingIterator(allChildren, processElement);
            ItemMappingFunction test = item -> {
                if (matches(item, context)) {
                    return (NodeInfo)item;
                } else {
                    return null;
                }
            };
            return new ItemMappingIterator(attributesOrSelf, test);

        }
    }

    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */

    public abstract UType getUType();


    /**
     * Determine the name fingerprint of nodes to which this pattern applies. Used for
     * optimisation.
     *
     * @return A fingerprint that the nodes must match, or -1 if it can match multiple fingerprints,
     * or it if matches atomic values
     */

    public int getFingerprint() {
        return -1;
    }

    /**
     * Get an ItemType that all the items matching this pattern must satisfy
     *
     * @return an ItemType, as specific as possible, which all the matching items satisfy
     */

    @Override
    public abstract ItemType getItemType();

    /**
     * Set a priority to override the default priority. This is used when the pattern is written in a complex
     * form such as a[true()] justifying a priority of 0.5, but then simplifies down to an NodeTestPattern
     *
     * @param priority the priority to be used if no explicit priority is given in the template rule
     */

    public void setPriority(double priority) {
        this.priority = priority;
    }

    /**
     * Determine the default priority to use if this pattern appears as a match pattern
     * for a template with no explicit priority attribute.
     *
     * @return the default priority for the pattern
     */

    public double getDefaultPriority() {
        return priority;
    }

    /**
     * Get the original text of the pattern, if known
     * @return the original text of the pattern as written; this may be null
     * in the case of a pattern constructed programmatically
     */

    public String getOriginalText() {
        return originalText;
    }

    /**
     * Get a string representation of the pattern. This will be in a form similar to the
     * original pattern text, but not necessarily identical. It is not guaranteed to be
     * in legal pattern syntax.
     */

    public String toString() {
        if (originalText != null) {
            return originalText;
        } else {
            return reconstruct();

        }
    }

    /**
     * Reconstruct a string representation of the pattern in cases where the original
     * string is not available
     */

    public String reconstruct() {
        return "pattern matching " + getItemType();
    }

    /**
     * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
     *
     * @return typically {@link HostLanguage#XSLT} or {@link HostLanguage#XQUERY}
     */

    public HostLanguage getHostLanguage() {
        return HostLanguage.XSLT;
    }

    /**
     * Convert the pattern to a typed pattern, in which an element name is treated as
     * schema-element(N)
     *
     * @param val either "strict" or "lax" depending on the value of xsl:mode/@typed
     * @return either the original pattern unchanged, or a new pattern as the result of the
     * conversion
     * @throws XPathException if the pattern cannot be converted
     */

    public Pattern convertToTypedPattern(String val) throws XPathException {
        return null;
    }

    @Override
    public Pattern toPattern(Configuration config) {
        return this;
    }

    @Override
    public abstract void export(ExpressionPresenter presenter) throws XPathException;

//    /**
//     * Copy location information (the line number, priority and references to package) from one pattern
//     * to another
//     *
//     * @param from the pattern containing the location information
//     * @param to   the pattern to which the information is to be copied
//     */

//    public static void copyLocationInfo(Pattern from, Pattern to) {
//        if (from != null && to != null) {
//            to.setSystemId(from.getSystemId());
//            to.setLineNumber(from.getLineNumber());
//            to.setPackageData(from.getPackageData());
//            to.setOriginalText(from.getOriginalText());
//            to.setPriority(from.getDefaultPriority());
//        }
//    }

    @Override
    public abstract Pattern copy(RebindingMap rebindings);

    /**
     * Perform optimisation of an expression and its subexpressions. This is the third and final
     * phase of static optimization.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo the static type of "." at the point where this expression is invoked.
     *                    The parameter is set to null if it is known statically that the context item will be undefined.
     *                    If the type of the context item is not known statically, the argument is set to
     *                    {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws net.sf.saxon.trans.XPathException if an error is discovered during this phase
     *                                           (typically a type error)
     */
    @Override
    public Pattern optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        return this;
    }


    @Override
    public String toShortString() {
        return toString();
    }

//    //#ifdefined STREAM
//
//    @Override
//    public PostureAndSweep getStreamability(ContextItemStaticInfo contextInfo, List<String> reasons) {
//        if (contextInfo.getContextItemPosture() == Posture.GROUNDED || isMotionless()) {
//            return PostureAndSweep.GROUNDED_AND_MOTIONLESS;
//        } else {
//            if (reasons != null) {
//                reasons.add("The pattern " + toShortString() + " is not motionless");
//            }
//            return PostureAndSweep.ROAMING_AND_FREE_RANGING;
//        }
//    }
//
//    //#endif


}

