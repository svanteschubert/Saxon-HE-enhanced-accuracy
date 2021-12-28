////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.style.StyleElement;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This instruction corresponds to a single QName appearing within a use-attribute-sets attribute on a
 * literal result element, xsl:element, or xsl:copy instruction. Binding to a specific attribute set
 * component is done in the same way as function calls or call-template, allowing attribute sets defined
 * in one package to be overridden in another.
 */
public class UseAttributeSet extends Instruction implements ComponentInvocation, ContextOriginator {

    private StructuredQName targetName;
    private AttributeSet target;
    private boolean isDeclaredStreamable;
    private int bindingSlot = -1;

    /**
     * Create a use-attribute-set expression
     *
     * @param name the name of the target attribute set
     */

    public UseAttributeSet(StructuredQName name, boolean streamable) {
        this.targetName = name;
        this.isDeclaredStreamable = streamable;
    }

    @Override
    public boolean isInstruction() {
        return false;
    }

    /**
     * Make an expression whose effect is to expand the attribute sets named in an [xsl]use-attribute-sets
     * attribute, for example on a literal result element
     *
     * @param targets     the QNames contained in the use-attribute-sets attribute
     * @param instruction the instruction on which the use-attribute-sets attribute appears
     * @return the required expression
     * @throws XPathException if an error occurs, for example no attribute set found with the required name
     */

    public static Expression makeUseAttributeSets(StructuredQName[] targets, StyleElement instruction) throws XPathException {
        List<UseAttributeSet> list = makeUseAttributeSetInstructions(targets, instruction);
        return makeCompositeExpression(list);
    }

    /**
     * Make an list of expressions whose combined effect is to expand the attribute sets named in an
     * [xsl]use-attribute-sets attribute, for example on a literal result element
     *
     * @param targets     the QNames contained in the use-attribute-sets attribute
     * @param instruction the instruction on which the use-attribute-sets attribute appears
     * @return the required expression
     * @throws XPathException if an error occurs, for example no attribute set found with the required name
     */

    public static List<UseAttributeSet> makeUseAttributeSetInstructions(StructuredQName[] targets, StyleElement instruction) throws XPathException {
        List<UseAttributeSet> list = new ArrayList<>(targets.length);
        for (StructuredQName name : targets) {
            UseAttributeSet use = makeUseAttributeSet(name, instruction);
            if (use != null) {
                list.add(use);
            }
        }
        return list;
    }

    /**
     * Given a list of UseAttributeSet expressions, combine them into a single expression
     *
     * @param targets the list of expressions
     * @return the combined expression
     */

    public static Expression makeCompositeExpression(List<UseAttributeSet> targets) {
        if (targets.size() == 0) {
            return Literal.makeEmptySequence();
        } else if (targets.size() == 1) {
            return targets.get(0);
        } else {
            return new Block(targets.toArray(new Expression[0]));
        }
    }

    /**
     * Make a UseAttributeSet expression corresponding to a single attribute-set name
     *
     * @param name        the name of the attribute-set to be expanded
     * @param instruction the containing instruction
     * @return a UseAttributeSet expression whose effect is to expand this attribute-set
     * @throws XPathException if a dynamic error occurs
     */

    private static UseAttributeSet makeUseAttributeSet(StructuredQName name, StyleElement instruction) throws XPathException {
        AttributeSet target;
        if (name.hasURI(NamespaceConstant.XSLT) && name.getLocalPart().equals("original")) {
            target = (AttributeSet) instruction.getXslOriginal(StandardNames.XSL_ATTRIBUTE_SET);
        } else {
            Component invokee = instruction.getContainingPackage().getComponent(new SymbolicName(StandardNames.XSL_ATTRIBUTE_SET, name));
            instruction.getPrincipalStylesheetModule().getAttributeSetDeclarations(name);
            if (invokee == null) {
                instruction.compileError("Unknown attribute set " + name.getEQName(), "XTSE0710");
                return null; // to prevent compile warnings
            }
            target = (AttributeSet) invokee.getActor();
        }
        UseAttributeSet invocation = new UseAttributeSet(name, target.isDeclaredStreamable());
        invocation.setTarget(target);
        invocation.setBindingSlot(-1);
        invocation.setRetainedStaticContext(instruction.makeRetainedStaticContext());
        return invocation;
    }

    public boolean isDeclaredStreamable() {
        return isDeclaredStreamable;
    }

    /**
     * Set the attribute set to be used.
     *
     * @param target the attribute set to be used
     */

    public void setTarget(AttributeSet target) {
        this.target = target;
    }

    /**
     * Set the binding slot to be used. This is the offset within the binding vector of the containing
     * component where the actual target component is to be found. The target template is not held directly
     * in the invocation instruction/expression itself because it can be overridden in a using package.
     *
     * @param slot the offset in the binding vector of the containing package where the target component
     *             can be found.
     */

    @Override
    public void setBindingSlot(int slot) {
        bindingSlot = slot;
    }

    /**
     * Get the binding slot to be used. This is the offset within the binding vector of the containing
     * component where the actual target component is to be found.
     *
     * @return the offset in the binding vector of the containing package where the target component
     * can be found.
     */

    @Override
    public int getBindingSlot() {
        return bindingSlot;
    }

    /**
     * Get the symbolic name of the component that this invocation references
     *
     * @return the symbolic name of the target component
     */

    @Override
    public SymbolicName getSymbolicName() {
        return new SymbolicName(StandardNames.XSL_ATTRIBUTE_SET, targetName);
    }

    /**
     * Get the target attribute set to which this instruction is provisionally bound
     *
     * @return the provisional attribute set (which might be overridden in another package)
     */

    public AttributeSet getTargetAttributeSet() {
        return target;
    }

    @Override
    public Component getFixedTarget() {
        if (target != null && bindingSlot < 0) {
            return target.getDeclaringComponent();
        }
        return null;
    }


    @Override
    public Iterable<Operand> operands() {
        return Collections.emptyList();
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable, and after all type checking has been done.</p>
     *
     * @param visitor         an expression visitor
     * @param contextItemType the static type of "." at the point where this expression is invoked.
     *                        The parameter is set to null if it is known statically that the context item will be undefined.
     *                        If the type of the context item is not known statically, the argument is set to
     *                        {@link net.sf.saxon.type.Type#ITEM_TYPE}
     * @return the original expression, rewritten if appropriate to optimize execution
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        return this;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *                   that is used to update the bindings held in any
     *                   local variable references that are copied.
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        UseAttributeSet ua = new UseAttributeSet(targetName, isDeclaredStreamable);
        ua.setTarget(target);
        ua.setBindingSlot(bindingSlot);
        return ua;
    }

    /**
     * Perform type checking of an expression and its subexpressions.
     * <p>This checks statically that the operands of the expression have
     * the correct type; if necessary it generates code to do run-time type checking or type
     * conversion. A static type error is reported only if execution cannot possibly succeed, that
     * is, if a run-time type error is inevitable. The call may return a modified form of the expression.</p>
     * <p>This method is called after all references to functions and variables have been resolved
     * to the declaration of the function or variable. However, the types of such functions and
     * variables may not be accurately known if they have not been explicitly declared.</p>
     *
     * @param visitor     an expression visitor
     * @param contextInfo information about the static type of the context item
     * @return the original expression, rewritten to perform necessary
     * run-time type checks, and to perform other type-related
     * optimizations
     * @throws XPathException if an error is discovered during this phase
     *                        (typically a type error)
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        return this;
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return NodeKindTest.ATTRIBUTE;
    }


    /**
     * Determine the intrinsic dependencies of an expression, that is, those which are not derived
     * from the dependencies of its subexpressions. For example, position() has an intrinsic dependency
     * on the context position, while (position()+1) does not. The default implementation
     * of the method returns 0, indicating "no dependencies".
     *
     * @return a set of bit-significant flags identifying the "intrinsic"
     * dependencies. The flags are documented in class net.sf.saxon.value.StaticProperty
     */

    @Override
    public int getIntrinsicDependencies() {
        // we have to assume the worst; the target attribute set could be overridden by one that has focus dependencies
        return StaticProperty.DEPENDS_ON_XSLT_CONTEXT | StaticProperty.DEPENDS_ON_FOCUS;
    }

    /**
     * Get the target attribute sets of this instruction. Called from generated bytecode.
     *
     * @return the target attribute sets, as an array
     */

    public StructuredQName getTargetAttributeSetName() {
        return targetName;
    }

    /**
     * ProcessLeavingTail: called to do the real work of this instruction. This method
     * must be implemented in each subclass. The results of the instruction are written
     * to the current Receiver, which can be obtained via the Controller.
     *
     *
     * @param output the destination for the result
     * @param context The dynamic context of the transformation, giving access to the current node,
     *                the current variables, etc.
     * @return null if the instruction has completed execution; or a TailCall indicating
     * a function call or template call that is delegated to the caller, to be made after the stack has
     * been unwound so as to save stack space.
     */

    /*@Nullable*/
    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        Component target;

        if (bindingSlot < 0) {
            target = getFixedTarget();
        } else {
            target = context.getTargetComponent(bindingSlot);
            if (target.isHiddenAbstractComponent()) {
                XPathException err = new XPathException("Cannot expand an abstract attribute set ("
                                                                + targetName.getDisplayName()
                                                                + ") with no implementation", "XTDE3052");
                err.setLocation(getLocation());
                throw err;
            }
        }
        if (target == null) {
            throw new AssertionError("Failed to locate attribute set " + getTargetAttributeSetName().getEQName());
        }
        AttributeSet as = (AttributeSet) target.getActor();
        XPathContextMajor c2 = context.newContext();
        c2.setCurrentComponent(target);
        c2.setOrigin(this);
        SlotManager sm = as.getStackFrameMap();
        if (sm == null) {
            sm = SlotManager.EMPTY;
        }
        c2.openStackFrame(sm);
        as.expand(output, c2);
        return null;
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "useAS";
    }

    /**
     * Export of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("useAS", this);
        out.emitAttribute("name", targetName);
        out.emitAttribute("bSlot", "" + getBindingSlot());
        if (isDeclaredStreamable()) {
            out.emitAttribute("flags", "s");
        }
        out.endElement();
    }

    /**
     * Test whether this UseAttributeSets expression is equal to another
     *
     * @param obj the other expression
     */

    public boolean equals(Object obj) {
        if (!(obj instanceof UseAttributeSet)) {
            return false;
        }
        return targetName.equals(((UseAttributeSet) obj).targetName);
    }

    /**
     * Compute a hashcode
     */

    @Override
    public int computeHashCode() {
        return 0x86423719 ^ targetName.hashCode();
    }


    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "UseAttributeSet";
    }
}

