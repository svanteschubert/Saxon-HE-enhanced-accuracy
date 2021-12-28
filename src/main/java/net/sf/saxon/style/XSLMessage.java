////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.Message;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;


/**
 * An xsl:message element in the stylesheet. <br>
 */

public final class XSLMessage extends StyleElement {

    private Expression terminate = null;
    private Expression select = null;
    private Expression errorCode = null;
    private Expression timer = null;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    @Override
    public boolean isInstruction() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return true: yes, it may contain a template-body
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    @Override
    public void prepareAttributes() {

        String terminateAtt = null;
        String selectAtt = null;
        String errorCodeAtt = null;
        for (AttributeInfo att : attributes()) {
            String value = att.getValue();
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            switch (f) {
                case "terminate":
                    terminateAtt = Whitespace.trim(value);
                    terminate = makeAttributeValueTemplate(terminateAtt, att);
                    break;
                case "select":
                    selectAtt = value;
                    select = makeExpression(selectAtt, att);
                    break;
                case "error-code":
                    errorCodeAtt = value;
                    errorCode = makeAttributeValueTemplate(errorCodeAtt, att);
                    break;
                default:
                    if (attName.hasURI(NamespaceConstant.SAXON) && attName.getLocalPart().equals("time")) {
                        isExtensionAttributeAllowed(attName.getDisplayName());
                        boolean timed = processBooleanAttribute("saxon:time", value);
                        if (timed) {
                            timer = makeExpression(
                                    "format-dateTime(Q{http://saxon.sf.net/}timestamp(),'[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f,3-3] - ')", att);
                        }
                    } else {
                        checkUnknownAttribute(attName);
                    }
                    break;
            }
        }

        if (terminateAtt == null) {
            terminateAtt = "no";
            terminate = makeAttributeValueTemplate(terminateAtt, null);
        }

        checkAttributeValue("terminate", terminateAtt, true, StyleElement.YES_NO);

    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        select = typeCheck("select", select);
        terminate = typeCheck("terminate", terminate);
        if (errorCode == null) {
            errorCode = new StringLiteral("Q{http://www.w3.org/2005/xqt-errors}XTMM9000");
        } else {
            errorCode = typeCheck("error-code", errorCode);
        }
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        Expression b = compileSequenceConstructor(exec, decl, true);
        if (b != null) {
            if (select == null) {
                select = b;
            } else {
                select = Block.makeBlock(select, b);
                select.setLocation(
                    allocateLocation());
            }
        }
        if (timer != null) {
            select = Block.makeBlock(timer, select);
        }
        if (select == null) {
            select = new StringLiteral("xsl:message (no content)");
        }

        if (errorCode instanceof StringLiteral) {
            // resolve any QName prefix now
            String code = ((StringLiteral) errorCode).getStringValue();
            if (code.contains(":") && !code.startsWith("Q{")) {
                StructuredQName name = makeQName(code, null, "error-code");
                errorCode = new StringLiteral(name.getEQName());
            }
        }
        Message m = new Message(select, terminate, errorCode);
        m.setRetainedStaticContext(makeRetainedStaticContext());
        return m;
    }

}
