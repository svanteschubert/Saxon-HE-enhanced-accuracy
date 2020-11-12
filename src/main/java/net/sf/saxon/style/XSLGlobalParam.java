////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.GlobalParam;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

import java.util.EnumSet;

/**
 * An xsl:param element representing a global parameter (stylesheet parameter) in the stylesheet. <br>
 * The xsl:param element has mandatory attribute name and optional attributes
 * select, required, as, ...
 */

public class XSLGlobalParam extends XSLGlobalVariable {

    @Override
    protected EnumSet<SourceBinding.BindingProperty> getPermittedAttributes() {
        return EnumSet.of(
                SourceBinding.BindingProperty.REQUIRED,
                SourceBinding.BindingProperty.SELECT,
                SourceBinding.BindingProperty.AS,
                SourceBinding.BindingProperty.STATIC);
    }

    /*@Nullable*/ Expression conversion = null;

    public XSLGlobalParam() {
        sourceBinding.setProperty(SourceBinding.BindingProperty.PARAM, true);
    }

    /**
     * Default visibility for xsl:param is public
     * @return the declared visibility, or "public" if not declared
     */

    @Override
    public Visibility getVisibility()  {
        String statik = getAttributeValue("static");
        if (statik == null) {
            return Visibility.PUBLIC;
        } else {
            boolean isStatic = processBooleanAttribute("static", statik);
            return isStatic ? Visibility.PRIVATE : Visibility.PUBLIC;
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {

        if (sourceBinding.hasProperty(SourceBinding.BindingProperty.REQUIRED)) {
            if (sourceBinding.getSelectExpression() != null) {
                // NB, we do this test before setting the default select attribute
                compileError("The select attribute must be absent when required='yes'", "XTSE0010");
            }
            if (hasChildNodes()) {
                compileError("A parameter specifying required='yes' must have empty content", "XTSE0010");
            }
            Visibility vis = getVisibility();
            if (!sourceBinding.isStatic() &&
                    !(vis == Visibility.PUBLIC || vis == Visibility.FINAL || vis == Visibility.ABSTRACT)) {
                compileError("The visibility of a required non-static parameter must be public, final, or abstract", "XTSE3370");
            }
        }

        super.validate(decl);
    }


    /**
     * Compile a global xsl:param element: this ensures space is available for local variables declared within
     * this global variable
     */

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        if (sourceBinding.isStatic()) {
            super.compileDeclaration(compilation, decl);

        } else if (!redundant) {
            sourceBinding.handleSequenceConstructor(compilation, decl);

            GlobalParam binding = (GlobalParam)compiledVariable;
            binding.setPackageData(getCompilation().getPackageData());
            binding.obtainDeclaringComponent(this);
            Expression select = sourceBinding.getSelectExpression();
            binding.setBody(select);
            binding.setVariableQName(sourceBinding.getVariableQName());
            initializeBinding(binding);

            if (select != null && compilation.getCompilerInfo().getCodeInjector() != null) {
                compilation.getCompilerInfo().getCodeInjector().process(binding);
            }

            binding.setRequiredType(getRequiredType());
            binding.setRequiredParam(sourceBinding.hasProperty(SourceBinding.BindingProperty.REQUIRED));
            binding.setImplicitlyRequiredParam(sourceBinding.hasProperty(SourceBinding.BindingProperty.IMPLICITLY_REQUIRED));
            sourceBinding.fixupBinding(binding);
            //compiledVariable = binding;

            Component overridden = getOverriddenComponent();
            if (overridden != null) {
                checkCompatibility(overridden);
            }
        }
    }


    /**
     * Get the static type of the parameter. This is the declared type, because we cannot know
     * the actual value in advance.
     */

    @Override
    public SequenceType getRequiredType() {
        SequenceType declaredType = sourceBinding.getDeclaredType();
        if (declaredType != null) {
            return declaredType;
        } else {
            return SequenceType.ANY_SEQUENCE;
        }
    }

    public void insertBytecodeCandidate(Optimizer opt) {

    }
}

