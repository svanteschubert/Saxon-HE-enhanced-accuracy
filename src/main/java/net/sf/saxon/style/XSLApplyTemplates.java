////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.ApplyTemplates;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.sort.SortExpression;
import net.sf.saxon.expr.sort.SortKeyDefinitionList;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

import java.util.HashMap;


/**
 * An xsl:apply-templates element in the stylesheet
 */

public class XSLApplyTemplates extends StyleElement {

    /*@Nullable*/ private Expression select;
    private Expression separator;
    private StructuredQName modeName;   // null if no name specified or if conventional values such as #current used
    private boolean useCurrentMode = false;
    private boolean useTailRecursion = false;
    private boolean defaultedSelectExpression = true;
    private Mode mode;
    private String modeAttribute;

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

        String selectAtt;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            switch (f) {
                case "mode":
                    modeAttribute = Whitespace.trim(value);
                    break;
                case "select":
                    selectAtt = value;
                    select = makeExpression(selectAtt, att);
                    defaultedSelectExpression = false;
                    break;
                case "separator":
                    requireSyntaxExtensions("separator");
                    separator = makeAttributeValueTemplate(value, att);
                    break;
                default:
                    checkUnknownAttribute(attName);
                    break;
            }
        }

        if (modeAttribute != null) {
            switch (modeAttribute) {
                case "#current":
                    useCurrentMode = true;
                    break;
                case "#unnamed":
                    modeName = Mode.UNNAMED_MODE_NAME;
                    break;
                case "#default":
                    // do nothing;
                    break;
                default:
                    modeName = makeQName(modeAttribute, null, "mode");
                    break;
            }
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {

        // get the Mode object
        if (useCurrentMode) {
            // give a warning if we're not inside an xsl:template
            if (iterateAxis(AxisInfo.ANCESTOR, new NameTest(Type.ELEMENT, StandardNames.XSL_TEMPLATE, getNamePool())).next() == null) {
                issueWarning("Specifying mode=\"#current\" when not inside an xsl:template serves no useful purpose", this);
            }
        } else {
            PrincipalStylesheetModule psm = getPrincipalStylesheetModule();
            if (modeName == null) {
                // XSLT 3.0 allows a default mode to be specified on a containing element
                modeName = getDefaultMode();
                if ((modeName == null || modeName.equals(Mode.UNNAMED_MODE_NAME)) &&
                        psm.isDeclaredModes() && !psm.getRuleManager().isUnnamedModeExplicit()) {
                    compileError("The unnamed mode must be explicitly declared in an xsl:mode declaration", "XTSE3085");
                }
            } else if (modeName.equals(Mode.UNNAMED_MODE_NAME) && psm.isDeclaredModes() && !psm.getRuleManager().isUnnamedModeExplicit()) {
                compileError("The #unnamed mode must be explicitly declared in an xsl:mode declaration", "XTSE3085");
            }

            SymbolicName sName = new SymbolicName(StandardNames.XSL_MODE, modeName);
            StylesheetPackage containingPackage = decl.getSourceElement().getContainingPackage();
            HashMap<SymbolicName, Component> componentIndex = containingPackage.getComponentIndex();
            // see if there is a mode with this name in a used package
            Component existing = componentIndex.get(sName);
                if (existing != null) {
                    mode = (Mode)existing.getActor();
                }

            if (mode == null) {
                if (psm.isDeclaredModes()) {
                    compileError("Mode name " + modeName.getDisplayName() + " must be explicitly declared in an xsl:mode declaration", "XTSE3085");
                }
                mode = psm.getRuleManager().obtainMode(modeName, true);
            }
        }

        // handle sorting if requested

        for (NodeInfo child : children()) {
            if (child.getNodeKind() == Type.TEXT) {
                // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    compileError("No character data is allowed within xsl:apply-templates", "XTSE0010");
                }
            } else if (!(child instanceof XSLSort || child instanceof XSLWithParam)){
                compileError("Invalid element " + Err.wrap(child.getDisplayName(), Err.ELEMENT) +
                                     " within xsl:apply-templates", "XTSE0010");
            }
        }

        if (select == null) {
            Expression here = new ContextItemExpression();
            RoleDiagnostic role =
                    new RoleDiagnostic(RoleDiagnostic.CONTEXT_ITEM, "", 0);
            role.setErrorCode("XTTE0510");
            here = new ItemChecker(here, AnyNodeTest.getInstance(), role);
            select = new SimpleStepExpression(here, new AxisExpression(AxisInfo.CHILD, null));
            //select = new AxisExpression(AxisInfo.CHILD, null);
            select.setLocation(allocateLocation());
            select.setRetainedStaticContext(makeRetainedStaticContext());
        }

        select = typeCheck("select", select);

        if (separator != null) {
            separator = typeCheck("separator", separator);
        }

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
    public Expression compile(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        SortKeyDefinitionList sortKeys = makeSortKeys(compilation, decl);
        if (sortKeys != null) {
            useTailRecursion = false;
        }
        assert select != null;
        Expression sortedSequence = select;
        if (sortKeys != null) {
            sortedSequence = new SortExpression(select, sortKeys);
        }
        compileSequenceConstructor(compilation, decl, true);
        RuleManager rm = compilation.getPrincipalStylesheetModule().getRuleManager();
        ApplyTemplates app = new ApplyTemplates(
                sortedSequence,
                useCurrentMode,
                useTailRecursion,
                defaultedSelectExpression,
                isWithinDeclaredStreamableConstruct(),
                mode,
                rm);
        app.setActualParams(getWithParamInstructions(app, compilation, decl, false));
        app.setTunnelParams(getWithParamInstructions(app, compilation, decl, true));
        if (separator != null) {
            app.setSeparatorExpression(separator);
        }
        return app;
    }

}

