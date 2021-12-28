////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.CallTemplate;
import net.sf.saxon.expr.instruct.NamedTemplate;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.List;

/**
 * An xsl:call-template element in the stylesheet
 */

public class XSLCallTemplate extends StyleElement {

    private static StructuredQName ERROR_TEMPLATE_NAME =
            new StructuredQName("saxon", NamespaceConstant.SAXON, "error-template");

    private StructuredQName calledTemplateName;   // the name of the called template
    private NamedTemplate template = null;             // the template to be called (which may subsequently be overridden in another package)
    private boolean useTailRecursion = false;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    @Override
    public boolean isInstruction() {
        return true;
    }

    @Override
    public void prepareAttributes() {

        String nameAttribute = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("name")) {
                nameAttribute = Whitespace.trim(value);
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (nameAttribute == null) {
            calledTemplateName = ERROR_TEMPLATE_NAME;
            reportAbsence("name");
            return;
        }

        calledTemplateName = makeQName(nameAttribute, null, "name");
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        for (NodeInfo child : children()) {
            if (child instanceof XSLWithParam) {
                // OK;
            } else if (child instanceof XSLFallback && mayContainFallback()) {
                // xsl:fallback is not allowed on xsl:call-template, but is allowed on saxon:call-template (cheat!)
            } else if (child.getNodeKind() == Type.TEXT) {
                // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    compileError("No character data is allowed within xsl:call-template", "XTSE0010");
                }
            } else {
                compileError("Child element " + Err.wrap(child.getDisplayName(), Err.ELEMENT) +
                        " is not allowed as a child of xsl:call-template", "XTSE0010");
            }
        }
        if (!calledTemplateName.equals(ERROR_TEMPLATE_NAME)) {
            template = findTemplate(calledTemplateName);
        }
    }

    @Override
    public void postValidate() throws XPathException {
        // check that a parameter is supplied for each required parameter
        // of the called template

        if (template != null) {
            checkParams();
        } else {
            throw new AssertionError("Target template not known");
        }
    }

    private void checkParams() throws XPathException {
        List<NamedTemplate.LocalParamInfo> declaredParams = template.getLocalParamDetails();
        for (NamedTemplate.LocalParamInfo param : declaredParams) {
            if (param.isRequired && !param.isTunnel) {
                boolean ok = false;
                for (NodeInfo withParam : children(XSLWithParam.class::isInstance)) {
                    if (((XSLWithParam) withParam).getVariableQName().equals(param.name)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    compileError("No value supplied for required parameter " +
                                         Err.wrap(param.name.getDisplayName(), Err.VARIABLE), "XTSE0690");
                }
            }
        }

        // check that every supplied parameter is declared in the called
        // template

        for (NodeInfo w : children()) {
            if (w instanceof XSLWithParam && !((XSLWithParam) w).isTunnelParam()) {
                XSLWithParam withParam = (XSLWithParam) w;
                boolean ok = false;
                for (NamedTemplate.LocalParamInfo param : declaredParams) {
                    if (param.name.equals(withParam.getVariableQName()) && !param.isTunnel) {
                        // Note: see bug 10534
                        ok = true;
                        SequenceType required = param.requiredType;
                        withParam.checkAgainstRequiredType(required);
                        break;
                    }
                }
                if (!ok && !xPath10ModeIsEnabled()) {
                    compileError("Parameter " +
                                         withParam.getVariableQName().getDisplayName() +
                                         " is not declared in the called template", "XTSE0680");

                }
            }
        }
    }

    private NamedTemplate findTemplate(StructuredQName templateName) throws XPathException {
        PrincipalStylesheetModule pack = getPrincipalStylesheetModule();
        NamedTemplate template = pack.getNamedTemplate(templateName);
        if (template == null) {
            if (templateName.hasURI(NamespaceConstant.XSLT) && templateName.getLocalPart().equals("original")) {
                // Handle xsl:original
                return (NamedTemplate) getXslOriginal(StandardNames.XSL_TEMPLATE);
            }
            compileError("Cannot find a template named " + calledTemplateName, "XTSE0650");
        }
        return template;
    }

    /**
     * Mark tail-recursive calls on templates and functions.
     * For most instructions, this does nothing.
     */

    @Override
    public boolean markTailCalls() {
        useTailRecursion = true;
        return true;
    }


    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (template == null) {
            return null;   // error already reported
        }

        CallTemplate call = new CallTemplate(template, calledTemplateName, useTailRecursion, isWithinDeclaredStreamableConstruct());
        call.setLocation(allocateLocation());
        call.setActualParameters(
                getWithParamInstructions(call, exec, decl, false),
                getWithParamInstructions(call, exec, decl, true));
        return call;
    }

}

