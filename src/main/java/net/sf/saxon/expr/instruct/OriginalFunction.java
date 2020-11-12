////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.*;
import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;

/**
 * This class represents a function invoked using xsl:original from within an xs:override element.
 */
public class OriginalFunction extends AbstractFunction implements Function, ContextOriginator {

    private UserFunction userFunction;
    private Component component;

    public OriginalFunction(Component component) {
        this.component = component;
        this.userFunction = (UserFunction)component.getActor();
    }

    /**
     * Invoke the function
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws XPathException if a dynamic error occurs within the function
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] args) throws XPathException {
        XPathContextMajor c2 = userFunction.makeNewContext(context, this);
        c2.setCurrentComponent(component);
        return userFunction.call(c2, args);
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */
    @Override
    public FunctionItemType getFunctionItemType() {
        return userFunction.getFunctionItemType();
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */
    @Override
    public StructuredQName getFunctionName() {
        return userFunction.getFunctionName();
    }

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */
    @Override
    public int getArity() {
        return userFunction.getArity();
    }

    /**
     * Get a description of this function for use in error messages. For named functions, the description
     * is the function name (as a lexical QName). For others, it might be, for example, "inline function",
     * or "partially-applied ends-with function".
     *
     * @return a description of the function for use in error messages
     */
    @Override
    public String getDescription() {
        return userFunction.getDescription();
    }

    /**
     * Get the name of the package containing the function
     */

    public String getContainingPackageName() {
        return component.getContainingPackage().getPackageName();
    }

    public Component getComponent() {
        return component;
    }

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        ExpressionPresenter.ExportOptions options = (ExpressionPresenter.ExportOptions) out.getOptions();
        out.startElement("origF");
        out.emitAttribute("name", getFunctionName());
        out.emitAttribute("arity", ""+getArity());
        out.emitAttribute("pack", options.packageMap.get(component.getContainingPackage())+"");
        out.endElement();
    }
}

