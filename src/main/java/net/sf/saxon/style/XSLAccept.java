////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.pattern.qname.SpecificQNameTest;
import net.sf.saxon.trans.*;

/**
 * Represents an xsl:accept element in an XSLT 3.0 package manifest.
 */
public class XSLAccept extends XSLAcceptExpose {
    @Override
    protected void prepareAttributes() {
        super.prepareAttributes();
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
        NodeInfo parent = getParent();
        if (!(parent instanceof XSLUsePackage)) {
            compileError("Parent of xsl:accept must be xsl:use-package");
            return;
        }
        StylesheetPackage pack = ((XSLUsePackage)parent).getUsedPackage();
        if (pack != null) {
            for (ComponentTest test : getExplicitComponentTests()) {
                QNameTest nameTest = test.getQNameTest();
                if (nameTest instanceof SpecificQNameTest) {
                    int kind = test.getComponentKind();
                    StructuredQName name = ((SpecificQNameTest)nameTest).getStructuredQName();
                    SymbolicName sName = kind == StandardNames.XSL_FUNCTION ?
                        new SymbolicName.F(name, test.getArity()) :
                        new SymbolicName(kind, name);
                    Component comp = pack.getComponent(sName);
                    boolean found = false;
                    if (comp == null) {
                        if (kind == StandardNames.XSL_FUNCTION && test.getArity() == -1) {
                            // This will match any function of the required name, regardless of arity
                            for (int i = 0; i <= pack.getMaxFunctionArity(); i++) {
                                sName = new SymbolicName.F(name, i);
                                comp = pack.getComponent(sName);
                                if (comp != null) {
                                    checkCompatibility(sName, comp.getVisibility(), getVisibility());
                                    found = true;
                                }
                            }

                        }
                    } else {
                        checkCompatibility(sName, comp.getVisibility(), getVisibility());
                        found = true;
                    }
                    if (!found) {
                        compileError("No " + sName.toString() + " exists in the used package", "XTSE3030");
                    }
                }
            }
        }
    }


    protected void checkCompatibility(SymbolicName name, Visibility declared, Visibility exposed) {
        if (!isCompatible(declared, exposed)) {
            String code = "XTSE3040";
            compileError("The " + name + " is declared as " + Err.describeVisibility(declared)
                                 + " and cannot be accepted as " + Err.describeVisibility(exposed), code);
        }
    }

    public static boolean isCompatible(Visibility declared, Visibility exposed) {
        return switch (declared) {
            case PUBLIC -> exposed == Visibility.PUBLIC || exposed == Visibility.PRIVATE ||
                    exposed == Visibility.FINAL || exposed == Visibility.HIDDEN;
            case ABSTRACT -> exposed == Visibility.ABSTRACT || exposed == Visibility.HIDDEN;
            case FINAL -> exposed == Visibility.PRIVATE ||
                    exposed == Visibility.FINAL || exposed == Visibility.HIDDEN;
            case UNDEFINED -> true;
            default -> false;
        };
    }
}
