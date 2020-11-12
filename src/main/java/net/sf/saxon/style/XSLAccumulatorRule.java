////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

/**
 * Handler for xsl:accumulator-rule elements in a stylesheet (XSLT 3.0).
 */

public class XSLAccumulatorRule extends StyleElement {

    private Pattern match;
    private boolean postDescent;
    private Expression select;
    private boolean capture;

    @Override
    public void prepareAttributes() {

        String matchAtt = null;
        String newValueAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getDisplayName();
            if (attName.getURI().isEmpty()) {
                switch (f) {
                    case "match":
                        matchAtt = value;
                        break;
                    case "select":
                        newValueAtt = value;
                        select = makeExpression(newValueAtt, att);
                        break;
                    case "phase":
                        String phaseAtt = Whitespace.trim(value);
                        if ("start".equals(phaseAtt)) {
                            postDescent = false;
                        } else if ("end".equals(phaseAtt)) {
                            postDescent = true;
                        } else {
                            postDescent = true;
                            compileError("phase must be 'start' or 'end'", "XTSE0020");
                        }
                        break;
                    default:
                        checkUnknownAttribute(attName);
                        break;
                }
            } else if (attName.hasURI(NamespaceConstant.SAXON)) {
                if (isExtensionAttributeAllowed(attName.getDisplayName())) {
                    if (attName.getLocalPart().equals("capture")) {
                        capture = processBooleanAttribute("saxon:capture", value);
                    }
                }
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (matchAtt == null) {
            reportAbsence("match");
            matchAtt = "non-existent-element";
        }
        match = makePattern(matchAtt, "match");

        if (capture && !postDescent) {
            compileWarning("saxon:capture has no effect on a pre-descent accumulator rule",
                           SaxonErrorCode.SXWN9000);
        }

    }


    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        select = typeCheck("select", select);
        match = typeCheck("match", match);
        if (select != null && hasChildNodes()) {
            compileError("If the xsl:accumulator-rule element has a select attribute then it must have no children");
        }
    }

    public Expression getNewValueExpression(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        if (select == null) {
            select = compileSequenceConstructor(compilation, decl, true);
        }
        return select;
    }

    public Pattern getMatch() {
        return match;
    }

    public void setMatch(Pattern match) {
        this.match = match;
    }

    public boolean isPostDescent() {
        return postDescent;
    }

    public void setPostDescent(boolean postDescent) {
        this.postDescent = postDescent;
    }

    public boolean isCapture() {
        return capture;
    }

    public Expression getSelect() {
        return select;
    }

    public void setSelect(Expression select) {
        this.select = select;
    }

    @Override
    public SourceBinding hasImplicitBinding(StructuredQName name) {
        if (name.getLocalPart().equals("value") && name.hasURI("")) {
            SourceBinding sb = new SourceBinding(this);
            sb.setVariableQName(new StructuredQName("", "", "value"));
            assert ((XSLAccumulator)getParent()) != null;
            sb.setDeclaredType(((XSLAccumulator)getParent()).getResultType());
            sb.setProperty(SourceBinding.BindingProperty.IMPLICITLY_DECLARED, true);
            return sb;
        } else {
            return null;
        }
    }
}
