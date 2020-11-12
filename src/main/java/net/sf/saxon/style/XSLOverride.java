////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

/**
 * Represents an xsl:override element in a package manifest.
 */
public class XSLOverride extends StyleElement {

    @Override
    public void prepareAttributes() {
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            checkUnknownAttribute(attName);
        }
    }

    /**
     * Validate this element
     *
     * @param decl Not used
     */

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        for (NodeInfo curr : children()) {
            if (curr.getNodeKind() == Type.TEXT) {
                compileError("Character content is not allowed as a child of xsl:override", "XTSE0010");
            } else if (curr instanceof XSLFunction || curr instanceof XSLTemplate || curr instanceof XSLGlobalVariable
                    || curr instanceof XSLAttributeSet) {
                // OK
            } else {
                ((StyleElement) curr).compileError(
                        "Element " + curr.getDisplayName() + " is not allowed as a child of xsl:override", "XTSE0010");
            }
        }
    }

    @Override
    public void postValidate() throws XPathException {
        XSLUsePackage parent = (XSLUsePackage)getParent();
        assert parent != null;
        if (parent.getUsedPackage() != null) {
            for (NodeInfo curr : children()) {
                if (curr instanceof XSLFunction || curr instanceof XSLTemplate || curr instanceof XSLGlobalVariable
                        || curr instanceof XSLAttributeSet) {
                    StylesheetComponent procedure = (StylesheetComponent) curr;
                    SymbolicName name = procedure.getSymbolicName();
                    if (name == null) {
                        if (curr instanceof XSLTemplate) {
                            XSLTemplate decl = (XSLTemplate)curr;
                            if (decl.getMatch() == null) {
                                decl.compileError("An overriding template with no name must have a match pattern");
                            }
                            StructuredQName[] modeNames = decl.getModeNames();
                            for (StructuredQName modeName : modeNames) {
                                if (modeName.equals(Mode.OMNI_MODE)) {
                                    ((StyleElement) curr).compileError(
                                        "An overriding template rule must not specify mode=\"#all\"", "XTSE3440");
                                } else if (modeName.equals(Mode.UNNAMED_MODE_NAME)) {
                                    modeName = decl.getDefaultMode();
                                    if (modeName.equals(Mode.UNNAMED_MODE_NAME)) {
                                        ((StyleElement) curr).compileError(
                                            "An overriding template rule must not belong to the unnamed mode", "XTSE3440");
                                    }
                                }
                            }
                        } else {
                            ((StyleElement) curr).compileError(
                                "An overriding component (other than a template rule) must have a name", "XTSE3440");
                            return;
                        }
                    } else {

                        Component overridden = parent.getUsedPackage().getComponent(name);
                        if (overridden == null) {
                            ((StyleElement) curr).compileError("There is no " + StandardNames.getLocalName(name.getComponentKind()) +
                                " named " + name.getShortName() +
                                " in the used package", "XTSE3058");
                            return;
                        }
                        Visibility overriddenVis = overridden.getVisibility();
                        if (overriddenVis == null) {
                            overriddenVis = Visibility.PRIVATE;
                        }
                        if (overriddenVis == Visibility.FINAL || overriddenVis == Visibility.PRIVATE) {
                            ((StyleElement) curr).compileError(
                                    "The " + StandardNames.getLocalName(name.getComponentKind()) +
                                        " named " + name.getShortName()
                                            + " in the used package cannot be overridden because its visibility is " + overriddenVis.show(),
                                                               "XTSE3060");
                            return;
                        }
                        procedure.checkCompatibility(overridden);
                    }
                }
            }
        }
    }

    /**
     * Add a function library that recognizes the function call xsl:original, which is permitted
     * within a function that overrides another
     *
     * @param list the function library list to which the new function library should be added
     */

    public void addXSLOverrideFunctionLibrary(FunctionLibraryList list){
        list.addFunctionLibrary(XSLOriginalLibrary.getInstance());
    }
}
