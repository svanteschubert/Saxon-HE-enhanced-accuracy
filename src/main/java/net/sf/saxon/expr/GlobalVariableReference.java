////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;

import java.util.HashSet;
import java.util.Set;

/**
 * A reference to a global variable
 */
public class GlobalVariableReference extends VariableReference implements ComponentInvocation {

    int bindingSlot = -1;

    public GlobalVariableReference(StructuredQName name) {
        super(name);
    }

    public GlobalVariableReference(GlobalVariable var) {
        super(var);
    }

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        if (binding == null) {
            //System.err.println("copy unbound variable " + this);
            throw new UnsupportedOperationException("Cannot copy a variable reference whose binding is unknown");
        }
        GlobalVariableReference ref = new GlobalVariableReference(getVariableName());
        ref.copyFrom(this);
        return ref;
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
        if (bindingSlot != -1) {
            throw new AssertionError("Duplicate binding slot assignment");
        }
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
        return new SymbolicName(StandardNames.XSL_VARIABLE, getVariableName());
    }

    public void setTarget(Component target) {
        binding = (GlobalVariable) target.getActor();
    }

    public Component getTarget() {
        return ((GlobalVariable) binding).getDeclaringComponent();
    }

    @Override
    public Component getFixedTarget() {
        Component c = getTarget();
        Visibility v = c.getVisibility();
        if (v == Visibility.PRIVATE || v == Visibility.FINAL) {
            return c;
        } else {
            return null;
        }
    }

    /**
     * Evaluate this variable
     *
     * @param c the XPath dynamic context
     * @return the value of the variable
     * @throws net.sf.saxon.trans.XPathException if any error occurs
     */
    @Override
    public GroundedValue evaluateVariable(XPathContext c) throws XPathException {

        if (bindingSlot >= 0) {
            if (c.getCurrentComponent() == null) {
                throw new AssertionError("No current component");
            }
            Component target = c.getTargetComponent(bindingSlot);
            if (target.isHiddenAbstractComponent()) {
                XPathException err = new XPathException("Cannot evaluate an abstract variable ("
                                                                + getVariableName().getDisplayName()
                                                                + ") with no overriding declaration", "XTDE3052");
                err.setLocation(getLocation());
                throw err;
            }
            GlobalVariable p = (GlobalVariable) target.getActor();
            return p.evaluateVariable(c, target);
        } else {
            // code for references to final/private variables, also used in XQuery
            GlobalVariable b = (GlobalVariable) binding;
            return b.evaluateVariable(c, b.getDeclaringComponent());
        }

    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("gVarRef", this);
        out.emitAttribute("name", getVariableName());
        out.emitAttribute("bSlot", "" + getBindingSlot());
        out.endElement();
    }

    public Set<Expression> getPreconditions() {
        Set<Expression> pre = new HashSet<Expression>();
        //pre.add(this.copy());
        return pre;
    }
}
