////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;

import java.util.Stack;

/**
 * The compiled form of an xsl:attribute-set element in the stylesheet.
 */

public class AttributeSet extends Actor {

    StructuredQName attributeSetName;
    private boolean declaredStreamable;

    /**
     * Create an empty attribute set
     */

    public AttributeSet() {
    }

    /**
     * Get the symbolic name of the component
     *
     * @return the symbolic name
     */
    @Override
    public SymbolicName getSymbolicName() {
        return new SymbolicName(StandardNames.XSL_ATTRIBUTE_SET, attributeSetName);
    }

    /**
     * Set the name of the attribute-set
     *
     * @param attributeSetName the name of the attribute-set
     */

    public void setName(StructuredQName attributeSetName) {
        this.attributeSetName = attributeSetName;
    }

    /**
     * Say whether this attribute set is declared to be streamable
     * @param value true if the attribute streamable="yes" is present
     */

    public void setDeclaredStreamable(boolean value) {
        this.declaredStreamable = value;
    }

    /**
     * Ask whether this attribute set is declared to be streamable
     * @return true if the attribute streamable="yes" is present
     */

    public boolean isDeclaredStreamable() {
        return this.declaredStreamable;
    }

    /**
     * Set the stack frame map which allocates slots to variables declared in this attribute set
     *
     * @param stackFrameMap the stack frame map
     */

    @Override
    public void setStackFrameMap(/*@Nullable*/ SlotManager stackFrameMap) {
        if (stackFrameMap != null) {
            super.setStackFrameMap(stackFrameMap);
        }
    }

    /**
     * Determine whether the attribute set has any dependencies on the focus
     *
     * @return the dependencies
     */

    public int getFocusDependencies() {
        return body.getDependencies() & StaticProperty.DEPENDS_ON_FOCUS;
    }

    /**
     * Evaluate an attribute set
     *
     * @param context the dynamic context
     * @throws XPathException if any failure occurs
     */

    public void expand(Outputter output, XPathContext context) throws XPathException {
        Stack<AttributeSet> stack = ((XsltController)context.getController()).getAttributeSetEvaluationStack();
        if (stack.contains(this)) {
            throw new XPathException("Attribute set " + getObjectName().getEQName() + " invokes itself recursively", "XTDE0640");
        }
        stack.push(this);
        getBody().process(output, context);
        stack.pop();
        if (stack.isEmpty()) {
            ((XsltController)context.getController()).releaseAttributeSetEvaluationStack();
        }
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     */

    public StructuredQName getObjectName() {
        return attributeSetName;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied outputstream.
     *
     * @param presenter the expression presenter used to display the structure
     */
    @Override
    public void export(ExpressionPresenter presenter) throws XPathException {
        presenter.startElement("attributeSet");
        presenter.emitAttribute("name", getObjectName());
        presenter.emitAttribute("line", getLineNumber() + "");
        presenter.emitAttribute("module", getSystemId());
        presenter.emitAttribute("slots", getStackFrameMap().getNumberOfVariables() + "");
        presenter.emitAttribute("binds", getDeclaringComponent().getComponentBindings().size() + "");
        if (isDeclaredStreamable()) {
            presenter.emitAttribute("flags", "s");
        }
        getBody().export(presenter);
        presenter.endElement();
    }


}

