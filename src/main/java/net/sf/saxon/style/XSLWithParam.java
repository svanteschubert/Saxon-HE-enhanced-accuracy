////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.WithParam;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.value.SequenceType;

import java.util.EnumSet;

/**
 * An xsl:with-param element in the stylesheet. <br>
 * The xsl:with-param element has mandatory attribute name and optional attribute select
 */

public class XSLWithParam extends XSLGeneralVariable {

    private EnumSet<SourceBinding.BindingProperty> allowedAttributes = EnumSet.of(
            SourceBinding.BindingProperty.SELECT,
            SourceBinding.BindingProperty.AS,
            SourceBinding.BindingProperty.TUNNEL);

    @Override
    protected void prepareAttributes() {
        sourceBinding.prepareAttributes(allowedAttributes);
    }

    public boolean isTunnelParam() {
        return sourceBinding.hasProperty(SourceBinding.BindingProperty.TUNNEL);
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        super.validate(decl);

        // Check for duplicate parameter names

        AxisIterator iter = iterateAxis(AxisInfo.PRECEDING_SIBLING);
        Item prev;
        while ((prev = iter.next()) != null) {
            if (prev instanceof XSLWithParam) {
                if (sourceBinding.getVariableQName().equals(((XSLWithParam) prev).sourceBinding.getVariableQName())) {
                    compileError("Duplicate parameter name", "XTSE0670");
                }
            }
        }
    }

    public void checkAgainstRequiredType(SequenceType required) throws XPathException {
        sourceBinding.checkAgainstRequiredType(required);
    }

    /*@NotNull*/
    public WithParam compileWithParam(Expression parent, Compilation exec, ComponentDeclaration decl) throws XPathException {

        sourceBinding.handleSequenceConstructor(exec, decl);

        WithParam inst = new WithParam();
        inst.setSelectExpression(parent, sourceBinding.getSelectExpression());
        inst.setVariableQName(sourceBinding.getVariableQName());
        inst.setRequiredType(sourceBinding.getInferredType(true));
        return inst;
    }

}

