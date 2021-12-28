////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.SuppliedParameterReference;
import net.sf.saxon.expr.instruct.LocalParam;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.EnumSet;

/**
 * An xsl:param element representing a local parameter (template or function parameter) in the stylesheet. <br>
 * The xsl:param element has mandatory attribute name and optional attributes
 * select, required, as, ...
 */

public class XSLLocalParam extends XSLGeneralVariable {

    private EnumSet<SourceBinding.BindingProperty> permittedAttributes = EnumSet.of(
            SourceBinding.BindingProperty.TUNNEL,
            SourceBinding.BindingProperty.REQUIRED,
            SourceBinding.BindingProperty.SELECT,
            SourceBinding.BindingProperty.AS);

    /*@Nullable*/ Expression conversion = null;
    private int slotNumber = -9876;  // initial value designed solely to show up when debugging
    private LocalParam compiledParam;
    private boolean prepared = false;

    /**
     * Ask whether this element contains a binding for a variable with a given name; and if it does,
     * return the source binding information
     *
     * @param name the variable name
     * @return the binding information if this element binds a variable of this name; otherwise null
     */

    @Override
    public SourceBinding getBindingInformation(StructuredQName name) {
        if (name.equals(sourceBinding.getVariableQName())) {
            return sourceBinding;
        } else {
            return null;
        }
    }

    /**
     * Get the slot number allocated to this variable (its position in the stackframe)
     *
     * @return the allocated slot number
     */

    public int getSlotNumber() {
        return slotNumber;
    }

    @Override
    public void prepareAttributes() {
        if (!prepared) {
            prepared = true;
            sourceBinding.setProperty(SourceBinding.BindingProperty.PARAM, true);
            if (getParent() instanceof XSLFunction) {
                permittedAttributes.remove(SourceBinding.BindingProperty.SELECT);
                sourceBinding.setProperty(SourceBinding.BindingProperty.DISALLOWS_CONTENT, true);
            }
            sourceBinding.prepareAttributes(permittedAttributes);
            if (sourceBinding.hasProperty(SourceBinding.BindingProperty.TUNNEL) && !(getParent() instanceof XSLTemplate)) {
                compileError("For attribute 'tunnel' within an " + getParent().getDisplayName() +
                        " parameter, the only permitted value is 'no'", "XTSE0020");
            }
            if (getParent() instanceof XSLFunction && getAttributeValue("", "required") != null) {
                if (!sourceBinding.hasProperty(SourceBinding.BindingProperty.REQUIRED)) {
                    compileError("For attribute 'required' within an " + getParent().getDisplayName() +
                        " parameter, the only permitted value is 'yes'", "XTSE0020");
                }
            }
        }
    }

    public void prepareTemplateSignatureAttributes() throws XPathException {
        if (!prepared) {
            sourceBinding.setProperty(SourceBinding.BindingProperty.PARAM, true);
            sourceBinding.prepareTemplateSignatureAttributes();
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {

        StructuredQName name = sourceBinding.getVariableQName();

        NodeInfo parent = getParent();

        if (!((parent instanceof StyleElement) && ((StyleElement) parent).mayContainParam())) {
            compileError("xsl:param must be immediately within a template, function or stylesheet", "XTSE0010");
        }

        if (hasChildNodes() && getParent() instanceof XSLFunction) {
            compileError("Function parameters cannot have a default value", "XTSE0760");
        }

        iterateAxis(AxisInfo.PRECEDING_SIBLING).forEachOrFail(node -> {
            if (node instanceof XSLLocalParam) {
                if (name.equals(((XSLLocalParam) node).sourceBinding.getVariableQName())) {
                    compileError("The name of the parameter is not unique", "XTSE0580");
                }
            } else if (node instanceof StyleElement && ((StyleElement)node).getFingerprint() != StandardNames.XSL_CONTEXT_ITEM) {
                compileError("xsl:param must not be preceded by other instructions", "XTSE0010");
            } else {
                // it must be a text node; allow it if all whitespace
                if (!Whitespace.isWhite(node.getStringValueCS())) {
                    compileError("xsl:param must not be preceded by text", "XTSE0010");
                }
            }
        });

        SlotManager p = getContainingSlotManager();
        if (p == null) {
            compileError("Local variable must be declared within a template or function", "XTSE0010");
        } else {
            slotNumber = p.allocateSlotNumber(name);
        }

        if (sourceBinding.hasProperty(SourceBinding.BindingProperty.REQUIRED)) {
            if (sourceBinding.getSelectExpression() != null) {
                // NB, we do this test before setting the default select attribute
                compileError("The select attribute must be omitted when required='yes'", "XTSE0010");
            }
            if (hasChildNodes()) {
                compileError("A parameter specifying required='yes' must have empty content", "XTSE0010");
            }
        }

        super.validate(decl);
    }

    public boolean isTunnelParam() {
        return sourceBinding.hasProperty(SourceBinding.BindingProperty.TUNNEL);
    }

    public boolean isRequiredParam() {
        return sourceBinding.hasProperty(SourceBinding.BindingProperty.REQUIRED);
    }

    @Override
    public void fixupReferences() throws XPathException {
        sourceBinding.fixupReferences(null);
        super.fixupReferences();
    }


    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
//        if (!"iterate".equals(getParent().getLocalPart()) &&
//                sourceBinding.getReferences().size() == 0 && !sourceBinding.hasProperty(SourceBinding.REQUIRED)) {
//            return null;
//        }
        if (getParent() instanceof XSLFunction) {
            // Do nothing. We did everything necessary while compiling the XSLFunction element.
            return null;
        } else {
            SequenceType declaredType = getRequiredType();
            StructuredQName name = sourceBinding.getVariableQName();
            int slot = getSlotNumber();
            if (declaredType != null) {
                SuppliedParameterReference pref = new SuppliedParameterReference(slot);
                pref.setRetainedStaticContext(makeRetainedStaticContext());
                pref.setLocation(allocateLocation());
                RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.PARAM, name.getDisplayName(), 0);
                //role.setSourceLocator(new ExpressionLocation(this));
                role.setErrorCode("XTTE0590");
                conversion = exec.getConfiguration().getTypeChecker(false).staticTypeCheck(
                        pref,
                        declaredType,
                        role, makeExpressionVisitor());
            }


            sourceBinding.handleSequenceConstructor(exec, decl);

            LocalParam binding = new LocalParam();
            binding.setSelectExpression(sourceBinding.getSelectExpression());
            binding.setConversion(conversion);
            binding.setVariableQName(name);
            binding.setSlotNumber(slot);
            binding.setRequiredType(getRequiredType());
            binding.setRequiredParam(sourceBinding.hasProperty(
                    SourceBinding.BindingProperty.REQUIRED));
            binding.setImplicitlyRequiredParam(
                    sourceBinding.hasProperty(SourceBinding.BindingProperty.IMPLICITLY_REQUIRED));
            binding.setTunnel(sourceBinding.hasProperty(SourceBinding.BindingProperty.TUNNEL));
            sourceBinding.fixupBinding(binding);
            return compiledParam = binding;

        }
    }

    public LocalParam getCompiledParam() {
        return compiledParam;
    }


    /**
     * Get the static type of the parameter. This is the declared type, because we cannot know
     * the actual value in advance.
     */

    public SequenceType getRequiredType() {
        SequenceType declaredType = sourceBinding.getDeclaredType();
        if (declaredType != null) {
            return declaredType;
        } else {
            return SequenceType.ANY_SEQUENCE;
        }
    }


}

