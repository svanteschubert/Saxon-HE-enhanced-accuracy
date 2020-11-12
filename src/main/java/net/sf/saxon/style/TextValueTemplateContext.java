////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LocalVariableReference;
import net.sf.saxon.expr.VariableReference;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;

/**
 * Define the static context for expressions appearing between curly braces in a text value template.
 * The rules for binding local variables are slightly different from the rules for variables appearing
 * within attribute nodes.
 */
public class TextValueTemplateContext extends ExpressionContext {

    TextValueTemplateNode textNode;

    public TextValueTemplateContext(StyleElement parent, TextValueTemplateNode textNode) {
        super(parent, null);
        this.textNode = textNode;
    }

    /**
     * Bind a variable to an object that can be used to refer to it
     *
     * @param qName the name of the variable
     * @return a VariableDeclaration object that can be used to identify it in the Bindery,
     * @throws net.sf.saxon.trans.XPathException
     *          if the variable has not been declared
     */
    @Override
    public Expression bindVariable(StructuredQName qName) throws XPathException {
        SourceBinding siblingVar = bindLocalVariable(qName);
        if (siblingVar == null) {
            return super.bindVariable(qName);
        } else {
            VariableReference var = new LocalVariableReference(qName);
            siblingVar.registerReference(var);
            return var;
        }
    }

    private SourceBinding bindLocalVariable(StructuredQName qName) {
        NodeInfo curr = textNode;

        // first search for a local variable declaration

        AxisIterator preceding = curr.iterateAxis(AxisInfo.PRECEDING_SIBLING);
        while ((curr = preceding.next()) != null) {
            if (curr instanceof XSLGeneralVariable) {
                SourceBinding sourceBinding = ((XSLGeneralVariable) curr).getBindingInformation(qName);
                if (sourceBinding != null) {
                    return sourceBinding;
                }
            }

        }
        return null;
    }

}

