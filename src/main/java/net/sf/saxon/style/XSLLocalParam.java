////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.LocalParam;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.instruct.UserFunctionParameter;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.trans.FunctionStreamability;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.EnumSet;
import java.util.function.Supplier;

/**
 * An xsl:param element representing a local parameter (template or function parameter) in the stylesheet. <br>
 * The xsl:param element has mandatory attribute name and optional attributes
 * select, required, as, ...
 */

public class XSLLocalParam extends XSLGeneralVariable {

    @SuppressWarnings("FieldMayBeFinal")
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
    protected void prepareAttributes() {
        if (!prepared) {
            prepared = true;
            sourceBinding.setProperty(SourceBinding.BindingProperty.PARAM, true);
            if (getParent() instanceof XSLFunction) {
                sourceBinding.setProperty(SourceBinding.BindingProperty.REQUIRED, true);
                if (getCompilation().getCompilerInfo().getXsltVersion() < 40) {
                    permittedAttributes.remove(SourceBinding.BindingProperty.SELECT);
                    sourceBinding.setProperty(SourceBinding.BindingProperty.DISALLOWS_CONTENT, true);
                }
            }
            sourceBinding.prepareAttributes(permittedAttributes);
            if (sourceBinding.hasProperty(SourceBinding.BindingProperty.TUNNEL) && !(getParent() instanceof XSLTemplate)) {
                compileError("For attribute 'tunnel' within an " + getParent().getDisplayName() +
                        " parameter, the only permitted value is 'no'", "XTSE0020");
            }
            if (getParent() instanceof XSLFunction) {
                int pos = getParameterPosition();
                if (getCompilation().getCompilerInfo().getXsltVersion() >= 40) {
                    UserFunction uf = ((XSLFunction)getParent()).getCompiledFunction();
                    if (pos < uf.getParameterDefinitions().length) {
                        // (if not, something is wrong; it will be reported later)
                        UserFunctionParameter ufp = new UserFunctionParameter();
                        uf.getParameterDefinitions()[pos] = ufp;
                        ufp.setRequiredType(getRequiredType());
                        ufp.setVariableQName(getVariableQName());
                        ufp.setSlotNumber(getSlotNumber());
                        ufp.setRequired(isRequiredParam());
                        if (pos == 0 && uf.getDeclaredStreamability() != FunctionStreamability.UNCLASSIFIED) {
                            ufp.setFunctionStreamability(uf.getDeclaredStreamability());
                        }
                        Expression defaultVal = sourceBinding.getSelectExpression();
                        if (defaultVal != null) {
                            if (!(defaultVal instanceof Literal || defaultVal instanceof ContextItemExpression)) {
                                 compileError("The default value for a function parameter must be either a literal, or '.' (temporary Saxon restriction)");
                            }
                        }
                        if (defaultVal == null && !sourceBinding.hasProperty(SourceBinding.BindingProperty.REQUIRED)) {
                            defaultVal = new DefaultedArgumentExpression();
                        }
                        final Expression finalDefault = defaultVal;
                        ufp.setDefaultValueExpression(() -> finalDefault);
                    }
                } else {
                    if (!sourceBinding.hasProperty(SourceBinding.BindingProperty.REQUIRED)) {
                        compileError("For attribute 'required' within an " + getParent().getDisplayName() +
                                             " parameter, the only permitted value is 'yes'", "XTSE0020");
                    }
                }
            }
        }
    }

    private int getParameterPosition() {
        return Navigator.getNumberSimple(this, null) - 1;
    }

    public Supplier<Expression> getDefaultValueExpressionSupplier() {
        if (!prepared) {
            prepareAttributes();
        }
        return () -> {
            Expression select = sourceBinding.getSelectExpression();
            return select == null ? Literal.makeEmptySequence() : select;
        };
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
        boolean isFunction = getParent() instanceof XSLFunction;

        if (!((parent instanceof StyleElement) && ((StyleElement) parent).mayContainParam())) {
            compileError("xsl:param must be immediately within a template, function or stylesheet", "XTSE0010");
        }

        if (hasChildNodes() && isFunction && getCompilation().getCompilerInfo().getXsltVersion() != 40) {
            compileError("Function parameters cannot have a default value", "XTSE0760");
        }

        // it must be a text node; allow it if all whitespace
        SequenceTool.supply(iteratePrecedingSiblingAxis(AnyGNode.TEST), (ItemConsumer<? super Item>) node -> {
            if (node instanceof XSLLocalParam) {
                if (name.equals(((XSLLocalParam) node).sourceBinding.getVariableQName())) {
                    compileError("The name of the parameter (" + name + ") is not unique", "XTSE0580");
                }
                if (isFunction && isRequiredParam() && !((XSLLocalParam) node).isRequiredParam()) {
                    compileError("Parameter " + name + " is required, but an earlier parameter "
                                         + ((XSLLocalParam) node).sourceBinding.getVariableQName() + " is optional", "XTSE0761");
                    ((XSLLocalParam) node).sourceBinding.setProperty(SourceBinding.BindingProperty.REQUIRED, true);
                }
            } else if (node instanceof StyleElement && ((StyleElement) node).getFingerprint() != StandardNames.XSL_CONTEXT_ITEM) {
                compileError("xsl:param must not be preceded by other instructions", "XTSE0010");
            } else {
                // it must be a text node; allow it if all whitespace
                if (!Whitespace.isAllWhite(node.getUnicodeStringValue())) {
                    compileError("xsl:param must not be preceded by text", "XTSE0010");
                }
            }
        });

        SlotManager p = getContainingSlotManager();
        if (p == null) {
            compileError("Local variable must be declared within a template or function", "XTSE0010");
        } else {
            slotNumber = p.allocateSlotNumber(name, null);
        }

        if (sourceBinding.hasProperty(SourceBinding.BindingProperty.REQUIRED)) {
            if (sourceBinding.getSelectExpression() != null) {
                // NB, we do this test before setting the default select attribute
                String errorCode = isFunction ? "XTSE0760" : "XTSE0010";
                compileError("The select attribute must be omitted when required='yes'", errorCode);
            }
            if (hasChildNodes()) {
                String errorCode = isFunction ? "XTSE0760" : "XTSE0010";
                compileError("A parameter specifying required='yes' must have empty content", errorCode);
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

    /**
     * Ask whether variables declared in an "uncle" element are visible.
     * @return false if this xsl:param is a child of xsl:function (new rule in 4.0 draft)
     */

    protected boolean seesAvuncularVariables() {
        return !(getParent() instanceof XSLFunction);
    }

    @Override
    public void fixupReferences() throws XPathException {
        sourceBinding.fixupReferences(null);
        super.fixupReferences();
    }


    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (getParent() instanceof XSLFunction) {
            if (getCompilation().getCompilerInfo().getXsltVersion() >= 40 && !isRequiredParam()) {
                sourceBinding.handleSequenceConstructor(exec, decl);
                Expression selectExpression = sourceBinding.getSelectExpression();
                if (selectExpression == null) {
                    selectExpression = Literal.makeEmptySequence();
                } else {
                    Expression underlyingExpression = selectExpression;
                    while (underlyingExpression instanceof ItemChecker || underlyingExpression instanceof CardinalityChecker) {
                        underlyingExpression = ((UnaryExpression)underlyingExpression).getBaseExpression();
                    }
//                    if (!(underlyingExpression instanceof Literal || underlyingExpression instanceof ContextItemExpression)) {
//                        compileError("The default value for a function parameter must be either a literal, or '.' (temporary Saxon restriction)");
//                    }
                }

                int pos = getParameterPosition();
                final Expression finalSelect = selectExpression;
                ((XSLFunction)getParent()).getCompiledFunction().getParameterDefinitions()[pos]
                        .setDefaultValueExpression(() -> finalSelect);
            }
            return null;
        } else {
            SequenceType declaredType = getRequiredType();
            StructuredQName name = sourceBinding.getVariableQName();
            int slot = getSlotNumber();
            if (declaredType != null) {
                SuppliedParameterReference pref = new SuppliedParameterReference(slot);
                pref.setRetainedStaticContext(makeRetainedStaticContext());
                pref.setLocation(allocateLocation());
                Supplier<RoleDiagnostic> role =
                        () -> new RoleDiagnostic(RoleDiagnostic.PARAM, name.getDisplayName(), 0, "XTTE0590");
                conversion = exec.getConfiguration().getTypeChecker(false).staticTypeCheck(
                        pref,
                        declaredType,
                        role, makeExpressionVisitor());
            }


            sourceBinding.handleSequenceConstructor(exec, decl);

            LocalParam binding = new LocalParam();
            binding.setLocation(allocateLocation());
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
     * @return the declared type (or {@code item()*} if no type is declared)
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

