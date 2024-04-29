////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.AtomicSequenceConverter;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.instruct.CallTemplate;
import net.sf.saxon.expr.instruct.NamedTemplate;
import net.sf.saxon.expr.instruct.WithParam;
import net.sf.saxon.functions.registry.VendorFunctionSetHE;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingIncident;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
 * This element is a surrogate for an extension element (or indeed an xsl element)
 * for which no implementation is available.
 */

public class AbsentExtensionElement extends StyleElement {

    CallTemplate instruction;
    boolean useTailRecursion;

    /**
     * Ask whether the element is in the XSLT namespace
     *
     * @return true if the element is in the XSLT namespace
     */
    @Override
    public boolean isInXsltNamespace() {
        return getNodeName().hasURI(NamespaceUri.XSLT);
    }

    @Override
    public boolean isInstruction() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     */

    @Override
    protected boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Process the attributes of this element and all its children
     */

    @Override
    public void processAllAttributes() throws XPathException {
        if (reportingCircumstances == OnFailure.IGNORED_INSTRUCTION) {
            return;
        }
        if (reportingCircumstances == OnFailure.REPORT_ALWAYS) {
            compileError(validationError);
        }

        if (getEffectiveVersion() >= 40 && !isInXsltNamespace()) {
            PrincipalStylesheetModule pack = getPrincipalStylesheetModule();
            NamedTemplate template = pack.getNamedTemplate(getNodeName().getStructuredQName());
            if (template != null) {
                CallTemplate insn = new CallTemplate(template, template.getTemplateName(), false, ((StyleElement)getParent()).isWithinDeclaredStreamableConstruct());
                List<NamedTemplate.LocalParamInfo> declaredParams = template.getLocalParamDetails();

                // Check that all the required parameters are supplied
                for (NamedTemplate.LocalParamInfo param : declaredParams) {
                    if (param.isRequired && !param.isTunnel) {
                        if (attributes().get(param.name.getNamespaceUri(), param.name.getLocalPart()) == null) {
                            compileError("No value supplied for required parameter " +
                                                 Err.wrap(param.name.getDisplayName(), Err.VARIABLE), "XTSE0690");
                        }
                    }
                }

                // check that all supplied parameters are declared, and handle the parameter type
                List<WithParam> params = new ArrayList<>();
                for (AttributeInfo att : attributes().asList()) {
                    StructuredQName name = att.getNodeName().getStructuredQName();
                    if (!name.hasURI(NamespaceUri.XSLT) && !name.hasURI(NamespaceUri.XML)) {
                        for (NamedTemplate.LocalParamInfo param : declaredParams) {
                            if (param.name.equals(name) && !param.isTunnel) {
                                SequenceType required = param.requiredType;
                                WithParam withParam = new WithParam();
                                withParam.setVariableQName(name);
                                withParam.setRequiredType(SequenceType.ANY_SEQUENCE);
                                if (required.getCardinality() == StaticProperty.EXACTLY_ONE && required.getPrimaryType().isPlainType()) {
                                    if (required.getPrimaryType() == BuiltInAtomicType.BOOLEAN) {
                                        Expression avt = makeAttributeValueTemplate(att.getValue(), att);
                                        Expression toBool = VendorFunctionSetHE.getInstance().makeFunction("yes-no-boolean", 1).makeFunctionCall(avt);
                                        withParam.setSelectExpression(insn, toBool);
                                    } else {
                                        Expression avt = makeAttributeValueTemplate(att.getValue(), att);
                                        if (required.getPrimaryType() != BuiltInAtomicType.STRING) {
                                            avt = new AtomicSequenceConverter(avt, BuiltInAtomicType.UNTYPED_ATOMIC);
                                        }
                                        withParam.setSelectExpression(insn, avt);
                                    }
                                } else {
                                    Expression select = makeExpression(att.getValue(), att);
                                    withParam.setSelectExpression(insn, select);
                                }
                                params.add(withParam);
                            }
                        }
                    }
                }
                insn.setActualParameters(params.toArray(new WithParam[]{}), new WithParam[]{});
                this.instruction = insn;
            }
        }

        if (isTopLevel() && forwardsCompatibleModeIsEnabled()) {
            // do nothing
        } else {
            super.processAllAttributes();
        }
    }

    @Override
    protected void prepareAttributes() {
    }

    /**
     * Mark tail-recursive calls on templates and functions.
     * For most instructions, this does nothing.
     */

    @Override
    protected boolean markTailCalls() {
        useTailRecursion = true;
        if (instruction != null) {
            instruction.setTailRecursive(true);
        }
        return true;
    }

    /**
     * Recursive walk through the stylesheet to validate all nodes
     */

    @Override
    public void validateSubtree(ComponentDeclaration decl, boolean excludeStylesheet) throws XPathException {
        if (reportingCircumstances == OnFailure.IGNORED_INSTRUCTION || (isTopLevel() && forwardsCompatibleModeIsEnabled())) {
            // do nothing
        } else {
            super.validateSubtree(decl, excludeStylesheet);
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
    }

    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {

        if (instruction != null) {
            return instruction;
        }

        if (isTopLevel() || reportingCircumstances == OnFailure.IGNORED_INSTRUCTION) {
            return null;
        }

        // if there are fallback children, compile the code for the fallback elements

        if (validationError == null) {
            validationError = new XmlProcessingIncident("Unknown instruction");
        }
        return fallbackProcessing(exec, decl, this);
    }
}

