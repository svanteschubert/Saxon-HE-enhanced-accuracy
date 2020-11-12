////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.instruct.GlobalContextRequirement;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.packages.PackageDetails;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handler for xsl:use-package elements in stylesheet.
 */
public class XSLUsePackage extends StyleElement {

    private String nameAtt = null;
    private PackageVersionRanges versionRanges = null;
    private StylesheetPackage usedPackage;
    private List<XSLAccept> acceptors = null;

    /**
     * Bind to the package to which this xsl:use-package element refers.
     */

    void findUsedPackage(CompilerInfo info) throws XPathException {
        if (usedPackage == null) {
            if (nameAtt == null) {
                nameAtt = Whitespace.trim(getAttributeValue("", "name"));
            }
            if (nameAtt == null) {
                reportAbsence("name");
                nameAtt = "unnamed-package";
            }

            PackageVersionRanges ranges = getPackageVersionRanges();
            PackageDetails pack = ranges == null ? null : info.getPackageLibrary().findPackage(nameAtt, ranges);
            usedPackage = pack == null ? null : pack.loadedPackage;
            if (usedPackage == null) {
                compileErrorInAttribute("Package " + nameAtt + " could not be found", "XTSE3000", "name");
                // For error recovery, create an empty package
                usedPackage = getConfiguration().makeStylesheetPackage();
                usedPackage.setJustInTimeCompilation(info.isJustInTimeCompilation());
            }
            GlobalContextRequirement gcr = usedPackage.getContextItemRequirements();
            if (gcr != null && !gcr.isMayBeOmitted()) {
                compileError("Package " + getAttributeValue("name") +
                                     " requires a global context item, so it cannot be used as a library package", "XTTE0590");
            }
        }
    }


    /**
     * Get the package to which this xsl:use-package element refers. Assumes that findPackage()
     * has already been called.
     *
     * @return the package that is referenced.
     */

    @Override
    public StylesheetPackage getUsedPackage() {
        return usedPackage;
    }

    /**
     * Get the ranges of package versions this use-package directive will accept.
     * <p>
     * <p>This will involve processing the attributes once to derive any ranges declared (and the name of the required package).
     * If no range is defined, the catchall '*' is assumed. </p>
     *
     * @return the ranges of versions of the named package that this declaration will accept
     * @throws XPathException should not happen
     */

    private PackageVersionRanges getPackageVersionRanges() throws XPathException {
        if (versionRanges == null) {
            prepareAttributes();
        }
        return versionRanges;
    }


    @Override
    protected void prepareAttributes() {
        AttributeMap atts = attributes();
        String ranges = "*";
        for (AttributeInfo att : atts) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            if (f.equals("name")) {
                nameAtt = Whitespace.trim(att.getValue());
            } else if (f.equals("package-version")) {
                ranges = Whitespace.trim(att.getValue()).replaceAll("\\\\", "");
            } else {
                checkUnknownAttribute(attName);
            }
        }
        try {
            versionRanges = new PackageVersionRanges(ranges);
        } catch (XPathException e) {
            compileError(e);
        }
    }

    @Override
    public boolean isDeclaration() {
        return true;
    }

    @Override
    public void validate(ComponentDeclaration decl) {
        for (NodeInfo child : children()) {
            if (child.getNodeKind() == Type.TEXT) {
                compileError("Character content is not allowed as a child of xsl:use-package");
            } else //noinspection StatementWithEmptyBody
                if (child instanceof XSLAccept || child instanceof XSLOverride) {
                // no action
            } else {
                compileError("Child element " + Err.wrap(child.getDisplayName(), Err.ELEMENT) +
                                     " is not allowed as a child of xsl:use-package", "XTSE0010");
            }
        }
    }

    private Set<SymbolicName> getExplicitAcceptedComponentNames() throws XPathException {
        Set<SymbolicName> explicitAccepts = new HashSet<>();
        for (NodeInfo child : children(XSLAccept.class::isInstance)) {
            Set<ComponentTest> explicitComponentTests = ((XSLAccept) child).getExplicitComponentTests();
            for (ComponentTest test : explicitComponentTests) {
                SymbolicName name = test.getSymbolicNameIfExplicit();
                explicitAccepts.add(name);
            }
        }
        return explicitAccepts;
    }

    @Override
    public void postValidate() throws XPathException {
        for (NodeInfo curr : children()) {
            if (curr instanceof XSLOverride || curr instanceof XSLAccept) {
                ((StyleElement) curr).postValidate();
            }
        }
        Set<SymbolicName> accepts = getExplicitAcceptedComponentNames();
        Set<SymbolicName> overrides = getNamedOverrides();
        if (!accepts.isEmpty()) {
            for (SymbolicName o : overrides) {
                if (accepts.contains(o)) {
                    compileError("Cannot accept and override the same component (" + o + ")", "XTSE3051");
                }
                if (o.getComponentKind() == StandardNames.XSL_FUNCTION) {
                    // Bug 4326: where xsl:accept gives the function QName but not the arity, the entry will have an arity of -1
                    SymbolicName n = new SymbolicName.F(o.getComponentName(), -1);
                    if (accepts.contains(n)) {
                        compileError("Cannot accept and override the same function (" + o + ")", "XTSE3051");
                    }
                }
            }
        }
    }

    /**
     * Get the child xsl:accept elements
     *
     * @return the list of child xsl:accept elements
     */

    List<XSLAccept> getAcceptors() {
        if (this.acceptors == null) {
            acceptors = new ArrayList<>();
            for (NodeInfo decl : children(XSLAccept.class::isInstance)) {
                acceptors.add((XSLAccept) decl);
            }
        }
        return acceptors;
    }

    /**
     * Process all the xsl:override declarations in the xsl:use-package, adding the overriding named components
     * to the list of top-level declarations
     *
     * @param module    the top-level stylesheet module of this package
     * @param topLevel  the list of declarations in this package (to which this method appends)
     * @param overrides set of named components for which this xsl:use-package provides an override
     *                  (which this method populates).
     * @throws XPathException in the event of an error.
     */

    void gatherNamedOverrides(PrincipalStylesheetModule module,
                              List<ComponentDeclaration> topLevel,
                              Set<SymbolicName> overrides)
            throws XPathException {
        if (usedPackage == null) {
            return; // error already reported
        }
        for (NodeInfo override : children(XSLOverride.class::isInstance)) {
            for (NodeInfo overridingDeclaration : override.children(StylesheetComponent.class::isInstance)) {
                ComponentDeclaration decl = new ComponentDeclaration(module, (StyleElement) overridingDeclaration);
                topLevel.add(decl);
                SymbolicName name = ((StylesheetComponent) overridingDeclaration).getSymbolicName();
                if (name != null) {
                    overrides.add(name);
                } else if (overridingDeclaration instanceof XSLTemplate && overridingDeclaration.getAttributeValue("", "match") != null) {
                    StructuredQName[] modeNames = ((XSLTemplate)overridingDeclaration).getModeNames();
                    for (StructuredQName m : modeNames) {
                        overrides.add(new SymbolicName(StandardNames.XSL_MODE, m));
                    }
                }
            }
        }
    }

    private Set<SymbolicName> getNamedOverrides() {
        Set<SymbolicName> overrides = new HashSet<>();
        AxisIterator kids = iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        NodeInfo override;
        while ((override = kids.next()) != null) {
            if (override instanceof XSLOverride) {
                AxisIterator overridings = override.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
                NodeInfo overridingDeclaration;
                while ((overridingDeclaration = overridings.next()) != null) {
                    if (overridingDeclaration instanceof StylesheetComponent) {
                        SymbolicName name = ((StylesheetComponent) overridingDeclaration).getSymbolicName();
                        if (name != null) {
                            overrides.add(name);
                        }
                    }
                }
            }
        }
        return overrides;
    }


    /**
     * Process all the xsl:override declarations in the xsl:use-package, adding the overriding template rules
     * to the list of top-level declarations
     *
     * @param module    the top-level stylesheet module of this package (the using package)
     * @param overrides set of named components for which this xsl:use-package provides an override
     *                  (which this method populates). If the xsl:override contains any template rules, then the named
     *                  mode will be included in this list, but the individual template rules will not be added to
     *                  the top-level list.
     * @throws XPathException in the event of an error.
     */

    void gatherRuleOverrides(PrincipalStylesheetModule module, Set<SymbolicName> overrides)
            throws XPathException {
        StylesheetPackage thisPackage = module.getStylesheetPackage();
        RuleManager ruleManager = module.getRuleManager();
        AxisIterator kids = iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        Set<SymbolicName> overriddenModes = new HashSet<>();

        // Process all template rules within xsl:override elements
        NodeInfo override;
        while ((override = kids.next()) != null) {
            if (override instanceof XSLOverride) {
                AxisIterator overridings = override.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
                NodeInfo overridingDeclaration;
                while ((overridingDeclaration = overridings.next()) != null) {
                    if (overridingDeclaration instanceof XSLTemplate && overridingDeclaration.getAttributeValue("", "match") != null) {
                        StructuredQName[] modeNames = ((XSLTemplate) overridingDeclaration).getModeNames();
                        for (StructuredQName modeName : modeNames) {
                            if (modeName.equals(Mode.OMNI_MODE)) {
                                ((StyleElement) overridingDeclaration).compileError(
                                        "The mode name #all must not appear in an overriding template rule", "XTSE3440");
                                continue;
                            }
                            SymbolicName symbolicName = new SymbolicName(StandardNames.XSL_MODE, modeName);
                            overrides.add(symbolicName);
                            overriddenModes.add(symbolicName);
                            Component.M derivedComponent = (Component.M)thisPackage.getComponent(symbolicName);

                            if (derivedComponent == null) {
                                ((StyleElement) overridingDeclaration).compileError(
                                        "Mode " + modeName.getDisplayName() + " is not defined in the used package", "XTSE3060");
                                continue;
                            }

                            if (derivedComponent.getBaseComponent() == null) {
                                ((StyleElement) overridingDeclaration).compileError(
                                        "Mode " + modeName.getDisplayName() +
                                                " cannot be overridden because it is local to this package", "XTSE3440");
                                continue;
                            }

                            Component.M usedComponent = (Component.M)derivedComponent.getBaseComponent();

                            if (usedComponent.getVisibility() == Visibility.FINAL) {
                                ((StyleElement) overridingDeclaration).compileError(
                                        "Cannot define overriding template rules in mode " + modeName.getDisplayName() +
                                                " because it has visibility=final", "XTSE3060");
                                continue;
                            }

                            Mode usedMode = usedComponent.getActor();
                            if (usedComponent.getVisibility() != Visibility.PUBLIC) {
                                ((StyleElement) overridingDeclaration).compileError(
                                        "Cannot override template rules in mode " + modeName.getDisplayName() +
                                                ", because the mode is not public", "XTSE3060");
                                continue;
                            }
                            if (derivedComponent.getActor() == usedMode) {
                                SimpleMode overridingMode = new SimpleMode(modeName);
                                CompoundMode newCompoundMode = new CompoundMode(usedMode, overridingMode);
                                newCompoundMode.setDeclaringComponent(derivedComponent);
                                ruleManager.registerMode(newCompoundMode);
                                derivedComponent.setActor(newCompoundMode);
                            }
                        }
                    }

                }
            }
        }

        // Now process all public/final modes in the used package that have not been overridden by new template rules

        RuleManager usedPackageRuleManager = usedPackage.getRuleManager();
        if (usedPackageRuleManager != null) {
            for (Mode m : usedPackageRuleManager.getAllNamedModes()) {
                SymbolicName sn = m.getSymbolicName();
                if (!overriddenModes.contains(sn)) {
                    Component c = thisPackage.getComponent(sn);
                    if (c != null && c.getVisibility() != Visibility.PRIVATE) {
                        ruleManager.registerMode((Mode) c.getActor());
                    }
                }
            }
        }
    }


}

