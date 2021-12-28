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
import net.sf.saxon.expr.instruct.LocalParam;
import net.sf.saxon.expr.instruct.NextIteration;
import net.sf.saxon.expr.instruct.WithParam;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

/**
 * An xsl:next-iteration element in the stylesheet
 */

public class XSLNextIteration extends XSLBreakOrContinue {

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        validatePosition();
        if (xslIterate == null) {
            compileError("xsl:next-iteration must be a descendant of an xsl:iterate instruction");
        }
        for (NodeInfo child : children()) {
            if (child instanceof XSLWithParam) {
                if (((XSLWithParam) child).isTunnelParam()) {
                    compileError("An xsl:with-param element within xsl:iterate must not specify tunnel='yes'", "XTSE0020");
                }
            } else if (child.getNodeKind() == Type.TEXT) {
                // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    compileError("No character data is allowed within xsl:next-iteration", "XTSE0010");
                }
            } else {
                compileError("Child element " + Err.wrap(child.getDisplayName(), Err.ELEMENT) +
                                     " is not allowed as a child of xsl:next-iteration", "XTSE0010");
            }
        };
    }

    @Override
    public void postValidate() throws XPathException {

        if (xslIterate == null) {
            return; // previous error already reported
        }

        // check that every supplied parameter is declared in the saxon:iterate instruction

        for (NodeInfo w : children(XSLWithParam.class::isInstance)) {
            XSLWithParam withParam = (XSLWithParam) w;
            AxisIterator formalParams = xslIterate.iterateAxis(AxisInfo.CHILD);
            boolean ok = false;
            NodeInfo param;
            while ((param = formalParams.next()) != null) {
                if (param instanceof XSLLocalParam &&
                        ((XSLLocalParam) param).getVariableQName().equals(withParam.getVariableQName())) {
                    ok = true;
                    SequenceType required = ((XSLLocalParam) param).getRequiredType();
                    withParam.checkAgainstRequiredType(required);
                    break;
                }
            }
            if (!ok) {
                compileError("Parameter " +
                        withParam.getVariableQName().getDisplayName() +
                        " is not declared in the containing xsl:iterate instruction", "XTSE3130");
            }
        }
    }

    public SequenceType getDeclaredParamType(StructuredQName name) {
        for (NodeInfo param : xslIterate.children(XSLLocalParam.class::isInstance)) {
            if (((XSLLocalParam)param).getVariableQName().equals(name)) {
                return ((XSLLocalParam) param).getRequiredType();
            }
        }
        return null;
    }


    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        NextIteration call = new NextIteration();
        call.setRetainedStaticContext(makeRetainedStaticContext());
        WithParam[] actualParams = getWithParamInstructions(call, exec, decl, false);
        call.setParameters(actualParams);

        // For all declared parameters of the xsl:iterate instruction that are not present in the
        // actual parameters of the xsl:next-iteration, add an implicit <xsl:with-param name="p" select="$p"/>

        if (xslIterate != null) {
            AxisIterator declaredParams = xslIterate.iterateAxis(AxisInfo.CHILD);
            NodeInfo param;
            while ((param = declaredParams.next()) != null) {
                if (param instanceof XSLLocalParam) {
                    XSLLocalParam pdecl = (XSLLocalParam) param;
                    StructuredQName paramName = pdecl.getVariableQName();
                    LocalParam lp = pdecl.getCompiledParam();
                    boolean found = false;
                    for (WithParam actualParam : actualParams) {
                        if (paramName.equals(actualParam.getVariableQName())) {
                            actualParam.setSlotNumber(lp.getSlotNumber());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        WithParam wp = new WithParam();
                        wp.setVariableQName(paramName);
                        VariableReference ref = new LocalVariableReference(lp);
                        wp.setSelectExpression(call, ref);
                        //wp.setParameterId(psm.allocateUniqueParameterNumber(paramName));
                        wp.setSlotNumber(lp.getSlotNumber());
                        ref.setStaticType(pdecl.getRequiredType(), null, 0);
                        WithParam[] p2 = new WithParam[actualParams.length + 1];
                        p2[0] = wp;
                        System.arraycopy(actualParams, 0, p2, 1, actualParams.length);
                        actualParams = p2;
                    }
                }
            }
        }

        return call;
    }

}
