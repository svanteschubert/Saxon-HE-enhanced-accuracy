////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.flwor.LocalVariableBinding;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.Current;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.SequenceType;

/**
 * This class represents a pattern that sets the value of current() to the
 * node being matched, and then wraps another pattern that uses the value
 * of current()
 */
public class PatternThatSetsCurrent extends Pattern {

    private LocalVariableBinding binding;
    private Pattern wrappedPattern;

    public PatternThatSetsCurrent(Pattern wrappedPattern) {
        this(wrappedPattern, new LocalVariableBinding(Current.FN_CURRENT, SequenceType.SINGLE_ITEM));
    }

    public PatternThatSetsCurrent(Pattern wrappedPattern, LocalVariableBinding binding) {
        this.wrappedPattern = wrappedPattern;
        this.binding = binding;
        binding.setRequiredType(SequenceType.makeSequenceType(wrappedPattern.getItemType(), StaticProperty.EXACTLY_ONE));
        adoptChildExpression(wrappedPattern);
        setPriority(wrappedPattern.getDefaultPriority());
    }

    /**
     * Get the immediate sub-expressions of this expression, with information about the relationship
     * of each expression to its parent expression. Default implementation
     * works off the results of iterateSubExpressions()
     * <p>If the expression is a Callable, then it is required that the order of the operands
     * returned by this function is the same as the order of arguments supplied to the corresponding
     * call() method.</p>
     *
     * @return an iterator containing the sub-expressions of this expression
     */
    @Override
    public Iterable<Operand> operands() {
        return new Operand(this, wrappedPattern, OperandRole.SINGLE_ATOMIC);
    }

    /**
     * Get the binding of the item being matched by the pattern, that is, the binding that
     * represents the value of the current() function
     *
     * @return the binding of the item being matched by the pattern
     */

    public LocalBinding getCurrentBinding() {
        return binding;
    }

    /**
     * Ask whether this expression is, or contains, the binding of a given variable
     *
     * @param binding the variable binding
     * @return true if this expression is the variable binding (for example a ForExpression
     * or LetExpression) or if it is a FLWOR expression that binds the variable in one of its
     * clauses.
     */
    @Override
    public boolean hasVariableBinding(Binding binding) {
        return binding == this.binding;
    }

    /**
     * Allocate slots to any variables used within the pattern
     *
     * @param slotManager the slot manager representing the stack frame for local variables
     * @param nextFree    the next slot that is free to be allocated
     * @return the next slot that is free to be allocated
     */
    @Override
    public int allocateSlots(SlotManager slotManager, int nextFree) {
        slotManager.allocateSlotNumber(Current.FN_CURRENT);
        binding.setSlotNumber(nextFree++);
        return wrappedPattern.allocateSlots(slotManager, nextFree);
    }

    /**
     * Determine whether this Pattern matches the given item. This is the main external interface
     * for matching patterns: it sets current() to the node being tested
     *
     * @param item    The item to be tested against the Pattern
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the item matches the Pattern, false otherwise
     */
    @Override
    public boolean matches(Item item, XPathContext context) throws XPathException {
        context.setLocalVariable(binding.getLocalSlotNumber(), item);
        return wrappedPattern.matches(item, context);
    }

    /**
     * Get an ItemType that all the items matching this pattern must satisfy
     *
     * @return an ItemType, as specific as possible, which all the matching items satisfy
     */
    @Override
    public ItemType getItemType() {
        return wrappedPattern.getItemType();
    }

    /**
     * Simplify the pattern by applying any context-independent optimisations.
     * Default implementation does nothing.
     *
     */
    @Override
    public Pattern simplify() throws XPathException {
        wrappedPattern = wrappedPattern.simplify();
        return this;
    }

    /**
     * Type-check the pattern.
     *
     * @param visitor         the expression visitor
     * @param contextItemType the type of the context item at the point where the pattern
     *                        is defined. Set to null if it is known that the context item is undefined.
     * @return the optimised Pattern
     */
    @Override
    public Pattern typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        wrappedPattern = wrappedPattern.typeCheck(visitor, contextItemType);
        return this;
    }

    /**
     * Get a UType indicating which kinds of items this Pattern can match.
     *
     * @return a UType indicating all the primitive types of item that the pattern can match.
     */
    @Override
    public UType getUType() {
        return wrappedPattern.getUType();
    }

    /**
     * Determine the name fingerprint of nodes to which this pattern applies. Used for
     * optimisation.
     *
     * @return A fingerprint that the nodes must match, or -1 if it can match multiple fingerprints,
     * or it if matches atomic values
     */
    @Override
    public int getFingerprint() {
        return wrappedPattern.getFingerprint();
    }

    /**
     * Get the original pattern text
     */
    @Override
    public String reconstruct() {
        return wrappedPattern.toString();
    }

    /**
     * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
     *
     * @return typically {@link HostLanguage#XSLT} or {@link HostLanguage#XQUERY}
     */
    @Override
    public HostLanguage getHostLanguage() {
        return wrappedPattern.getHostLanguage();
    }

    /**
     * Test whether a pattern is motionless, that is, whether it can be evaluated against a node
     * without repositioning the input stream. This is a necessary condition for patterns used
     * as the match pattern of a streamed template rule.
     *
     * @return true if the pattern is motionless, that is, if it can be evaluated against a streamed
     * node without changing the position in the streamed input file
     */
    @Override
    public boolean isMotionless() {
        return wrappedPattern.isMotionless();
    }

    /**
     * Determine whether this pattern matches a given Node within the subtree rooted at a given
     * anchor node. This method is used when the pattern is used for streaming.
     *
     * @param node    The NodeInfo representing the Element or other node to be tested against the Pattern
     * @param anchor  The anchor node, which must match any AnchorPattern subpattern
     * @param context The dynamic context. Only relevant if the pattern
     *                uses variables, or contains calls on functions such as document() or key().
     * @return true if the node matches the Pattern, false otherwise
     */
    @Override
    public boolean matchesBeneathAnchor(NodeInfo node, NodeInfo anchor, XPathContext context) throws XPathException {
        return wrappedPattern.matchesBeneathAnchor(node, anchor, context);
    }

    /**
     * Convert the pattern to a typed pattern, in which an element name is treated as
     * schema-element(N)
     *
     * @param val either "strict" or "lax" depending on the value of xsl:mode/@typed
     * @return either the original pattern unchanged, or a new pattern as the result of the
     * conversion
     * @throws net.sf.saxon.trans.XPathException if the pattern cannot be converted
     */
    @Override
    public Pattern convertToTypedPattern(String val) throws XPathException {
        Pattern w2 = wrappedPattern.convertToTypedPattern(val);
        if (w2 == wrappedPattern) {
            return this;
        } else {
            return new PatternThatSetsCurrent(w2);
        }
    }

    /**
     * Copy a pattern. This makes a deep copy.
     *
     * @return the copy of the original pattern
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Pattern copy(RebindingMap rebindings) {
        LocalVariableBinding newCurrent = new LocalVariableBinding(Current.FN_CURRENT, SequenceType.SINGLE_ITEM);
        rebindings.put(binding, newCurrent);
        PatternThatSetsCurrent n = new PatternThatSetsCurrent(wrappedPattern.copy(rebindings), newCurrent);
        ExpressionTool.copyLocationInfo(this, n);
        n.setOriginalText(getOriginalText());
        return n;
    }

    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("p.withCurrent");
        wrappedPattern.export(presenter);
        presenter.endElement();
    }


    public Pattern getWrappedPattern() {
        return wrappedPattern;
    }

}

