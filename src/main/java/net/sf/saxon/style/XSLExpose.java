////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.NamedTemplate;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.QNameTest;
import net.sf.saxon.trans.*;

import java.util.List;

/**
 * Represents an xsl:expose element in an XSLT 3.0 package manifest.
 */
public class XSLExpose extends XSLAcceptExpose {

    protected void checkCompatibility(SymbolicName name, Visibility declared, Visibility exposed) {
        if (!isCompatible(declared, exposed)) {
            String code =  "XTSE3010";
            compileError("The " + name + " is declared as " + declared.show() + " and cannot be exposed as " + exposed.show(), code);
        }
    }

    public static boolean isCompatible(Visibility declared, Visibility exposed) {
        if (declared == null || declared == exposed) {
            return true;
        }
        switch (declared) {
            case PUBLIC:
                return exposed == Visibility.PUBLIC || exposed == Visibility.PRIVATE ||
                        exposed == Visibility.FINAL || exposed == Visibility.HIDDEN;
            case ABSTRACT:
                return exposed == Visibility.ABSTRACT || exposed == Visibility.HIDDEN;
            case FINAL:
                return exposed == Visibility.PRIVATE ||
                        exposed == Visibility.FINAL || exposed == Visibility.HIDDEN;
            default:
                return false;
        }
    }

    /**
     * Check that the stylesheet element is valid. This is called once for each element, after
     * the entire tree has been built. As well as validation, it can perform first-time
     * initialisation. The default implementation does nothing; it is normally overriden
     * in subclasses.
     *
     * @param decl the declaration to be validated
     * @throws net.sf.saxon.trans.XPathException
     *          if any error is found during validation
     */
    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        PrincipalStylesheetModule psm = getPrincipalStylesheetModule();
        Visibility exposedVisibility = getVisibility();

        // The following code checks that explicit references to components (as distinct from
        // wildcards) refer to actual components, and that the exposed visibility is consistent
        // with the declared visibility. It doesn't actually change the component's visibility property.
        // This is done later, in PrincipalStylesheetModuleEE#adjustExposedVisibility.

        for (ComponentTest test : getExplicitComponentTests()) {
            QNameTest nameTest = test.getQNameTest();
            if (nameTest instanceof NameTest) {
                StructuredQName qName = ((NameTest) nameTest).getMatchingNodeName();
                int kind = test.getComponentKind();
                SymbolicName sName = kind == StandardNames.XSL_FUNCTION ?
                    new SymbolicName.F(((NameTest) nameTest).getMatchingNodeName(), test.getArity()) :
                    new SymbolicName(kind, ((NameTest) nameTest).getMatchingNodeName());
                boolean found = false;
                switch(kind) {
                    case StandardNames.XSL_TEMPLATE: {
                        NamedTemplate template = psm.getNamedTemplate(qName);
                        found = template != null;
                        if (found) {
                            Visibility declared = template.getDeclaredVisibility();
                            checkCompatibility(template.getSymbolicName(), declared, exposedVisibility);
                            //template.getDeclaringComponent().setVisibility(exposedVisibility, false);
                        }
                        break;
                    }
                    case StandardNames.XSL_VARIABLE: {
                        SourceBinding binding = psm.getGlobalVariableBinding(qName);
                        if (binding != null && !(binding.getSourceElement() instanceof XSLGlobalParam)) {
                            found = true;
                        }
                        if (found) {
                            GlobalVariable var = ((XSLGlobalVariable) binding.getSourceElement()).getCompiledVariable();
                            Visibility declared = var.getDeclaredVisibility();
                            checkCompatibility(var.getSymbolicName(), declared, getVisibility());
                            //var.getDeclaringComponent().setVisibility(exposedVisibility, false);
                        }
                        break;
                    }
                    case StandardNames.XSL_ATTRIBUTE_SET: {
                        List<ComponentDeclaration> declarations = psm.getAttributeSetDeclarations(qName);
                        found = declarations != null && !declarations.isEmpty();
                        if (found) {
                            Visibility declared = declarations.get(0).getSourceElement().getDeclaredVisibility();
                            checkCompatibility(sName, declared, getVisibility());
                        }
                        break;
                    }
                    case StandardNames.XSL_MODE:
                        Mode mode = psm.getRuleManager().obtainMode(qName, false);
                        found = mode != null;
                        if (found) {
                            checkCompatibility(sName, mode.getDeclaredVisibility(), getVisibility());
                        }
                        if (getVisibility() == Visibility.ABSTRACT) {
                            // obviously wrong, though I don't see a rule in the spec
                            compileError("The visibility of a mode cannot be abstract");
                        }
                        break;
                    case StandardNames.XSL_FUNCTION:
                        StylesheetPackage pack = psm.getStylesheetPackage();
                        if (test.getArity() == -1) {
                            // This will match any function of the required name, regardless of arity
                            for (int i = 0; i <= pack.getMaxFunctionArity(); i++) {
                                sName = new SymbolicName.F(((NameTest) nameTest).getMatchingNodeName(), i);
                                Component fn = pack.getComponent(sName);
                                if (fn != null) {
                                    found = true;
                                    UserFunction userFunction = (UserFunction)fn.getActor();
                                    checkCompatibility(sName, userFunction.getDeclaredVisibility(), getVisibility());
                                }
                            }
                        } else {
                            Component fn = pack.getComponent(sName);
                            found = fn != null;
                            if (found) {
                                UserFunction userFunction = (UserFunction) fn.getActor();
                                checkCompatibility(sName, userFunction.getDeclaredVisibility(), getVisibility());
                            }
                        }
                        break;
                }
                if (!found) {
                    compileError("No " + sName.toString() + " exists in the containing package", "XTSE3020");
                }

            }
        }

    }

}
