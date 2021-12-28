////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.instruct.Choose;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XmlProcessingIncident;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for xsl:evaluate elements in XSLT 3.0 stylesheet. <br>
 */

public class XSLEvaluate extends StyleElement {

    Expression xpath = null;
    SequenceType requiredType = SequenceType.ANY_SEQUENCE;
    Expression namespaceContext = null;
    Expression contextItem = null;
    Expression baseUri = null;
    Expression schemaAware = null;
    Expression withParams = null;
    Expression options = null;
    boolean hasFallbackChildren;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    @Override
    public boolean isInstruction() {
        return true;
    }

    /**
     * Specify that xsl:sort is a permitted child
     */

    @Override
    protected boolean isPermittedChild(StyleElement child) {
        return child instanceof XSLLocalParam;
    }

    /**
     * Determine the type of item returned by this instruction (only relevant if
     * it is an instruction).
     *
     * @return the item type returned
     */

    protected ItemType getReturnedItemType() {
        return AnyItemType.getInstance();
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return false: no, it may not contain a sequence constructor
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return false;
    }


    @Override
    public void prepareAttributes() {

        AttributeMap atts = attributes();

        String xpathAtt = null;
        String asAtt = null;
        String contextItemAtt = null;
        String baseUriAtt = null;
        String namespaceContextAtt = null;
        String schemaAwareAtt = null;
        String withParamsAtt = null;

        for (AttributeInfo att : atts) {
            final NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            switch (f) {
                case "xpath":
                    xpathAtt = att.getValue();
                    xpath = makeExpression(xpathAtt, att);
                    break;
                case "as":
                    asAtt = att.getValue();
                    break;
                case "context-item":
                    contextItemAtt = att.getValue();
                    contextItem = makeExpression(contextItemAtt, att);
                    break;
                case "base-uri":
                    baseUriAtt = att.getValue();
                    baseUri = makeAttributeValueTemplate(baseUriAtt, att);
                    break;
                case "namespace-context":
                    namespaceContextAtt = att.getValue();
                    namespaceContext = makeExpression(namespaceContextAtt, att);
                    break;
                case "schema-aware":
                    schemaAwareAtt = Whitespace.trim(att.getValue());
                    schemaAware = makeAttributeValueTemplate(schemaAwareAtt, att);
                    break;
                case "with-params":
                    withParamsAtt = att.getValue();
                    withParams = makeExpression(withParamsAtt, att);
                    break;
                default:
                    if (attName.getLocalPart().equals("options") && attName.getURI().equals(NamespaceConstant.SAXON)) {
                        if (isExtensionAttributeAllowed(attName.getDisplayName())) {
                            options = makeExpression(att.getValue(), att);
                        }
                    } else {
                        checkUnknownAttribute(attName);
                    }
                    break;
            }
        }

        if (xpathAtt == null) {
            reportAbsence("xpath");
        }

        if (asAtt != null) {
            try {
                requiredType = makeSequenceType(asAtt);
            } catch (XPathException e) {
                compileErrorInAttribute(e.getMessage(), e.getErrorCodeLocalPart(), "as");
            }
        }

        if (contextItemAtt == null) {
            contextItem = Literal.makeEmptySequence();
        }

        if (schemaAwareAtt == null) {
            schemaAware = new StringLiteral("no");
        } else if (schemaAware instanceof StringLiteral) {
            checkAttributeValue("schema-aware", schemaAwareAtt, true, StyleElement.YES_NO);
        }

        if (withParamsAtt == null) {
            withParamsAtt = "map{}";
            withParams = makeExpression(withParamsAtt, null);
        }

        if (options == null) {
            options = makeExpression("map{}", null);
        }
    }


    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        getContainingPackage().setRetainUnusedFunctions();
        if (xpath == null) {
            xpath = new StringLiteral("''");
        }
        xpath = typeCheck("xpath", xpath);
        baseUri = typeCheck("base-uri", baseUri);
        contextItem = typeCheck("context-item", contextItem);
        namespaceContext = typeCheck("namespace-context", namespaceContext);
        schemaAware = typeCheck("schema-aware", schemaAware);
        withParams = typeCheck("with-params", withParams);
        options = typeCheck("options", options);

        for (NodeInfo child : children()) {
            if (child instanceof XSLWithParam) {
                // OK
            } else if (child instanceof XSLFallback) {
                hasFallbackChildren = true;
            } else if (child.getNodeKind() == Type.TEXT) {
                // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    compileError("No character data is allowed within xsl:evaluate", "XTSE0010");
                }
            } else {
                compileError("Child element " + Err.wrap(child.getDisplayName(), Err.ELEMENT) +
                                     " is not allowed as a child of xsl:evaluate", "XTSE0010");
            }
        };

        try {
            ExpressionVisitor visitor = makeExpressionVisitor();
            TypeChecker tc = getConfiguration().getTypeChecker(false);
            RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:evaluate/xpath", 0);
            xpath = tc.staticTypeCheck(xpath, SequenceType.SINGLE_STRING, role, visitor);

            role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:evaluate/context-item", 0);
            role.setErrorCode("XTTE3210");
            contextItem = tc.staticTypeCheck(contextItem, SequenceType.OPTIONAL_ITEM, role, visitor);

            role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:evaluate/namespace-context", 0);
            role.setErrorCode("XTTE3170");
            if (namespaceContext != null) {
                namespaceContext = tc.staticTypeCheck(namespaceContext, SequenceType.SINGLE_NODE, role, visitor);
            }

            role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:evaluate/with-params", 0);
            role.setErrorCode("XTTE3170");
            withParams = tc.staticTypeCheck(withParams,
                                            SequenceType.makeSequenceType(MapType.ANY_MAP_TYPE, StaticProperty.EXACTLY_ONE), role, visitor);

            role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:evaluate/saxon:options", 0);
            options = tc.staticTypeCheck(options,
                                         SequenceType.makeSequenceType(MapType.ANY_MAP_TYPE, StaticProperty.EXACTLY_ONE), role, visitor);

        } catch (XPathException err) {
            compileError(err);
        }
    }

    public Expression getTargetExpression() {
        return xpath;
    }

    public Expression getContextItemExpression() {
        return contextItem;
    }

    public Expression getBaseUriExpression() {
        return baseUri;
    }

    public Expression getNamespaceContextExpression() {
        return namespaceContext;
    }

    public Expression getSchemaAwareExpression() {
        return schemaAware;
    }

    public Expression getWithParamsExpression() {
        return withParams;
    }

    public Expression getOptionsExpression() {
        return options;
    }

    public SequenceType getRequiredType() {
        return requiredType;
    }

    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (getConfiguration().getBooleanProperty(Feature.DISABLE_XSL_EVALUATE)) {
            // If xsl:evaluate is statically disabled then we should execute any fallback children
            validationError = new XmlProcessingIncident("xsl:evaluate is not available in this configuration", "XTDE3175");
            return fallbackProcessing(exec, decl, this);
        } else {
            Expression evaluateExpr = getConfiguration().makeEvaluateInstruction(this, decl);
            if (evaluateExpr instanceof ErrorExpression) {
                return evaluateExpr;
            }
            // If there are any xsl:fallback children, we need to compile them, in case xsl:evaluate
            // is dynamically disabled at run-time.
            if (hasFallbackChildren) {
                // Generate a conditional expression switched on the value of system-property('xsl:supports-dynamic-evaluation')
                Expression[] conditions = new Expression[2];
                Expression sysProp = SystemFunction.makeCall("system-property",
                                                        makeRetainedStaticContext(),
                                                        new StringLiteral("Q{" + NamespaceConstant.XSLT + "}supports-dynamic-evaluation"));
                conditions[0] = new ValueComparison(sysProp, Token.FEQ, new StringLiteral("no"));
                conditions[1] = Literal.makeLiteral(BooleanValue.TRUE);
                Expression[] actions = new Expression[2];
                List<Expression> fallbackExpressions = new ArrayList<>();
                for (NodeInfo child : children(XSLFallback.class::isInstance)) {
                    fallbackExpressions.add(((XSLFallback) child).compileSequenceConstructor(exec, decl, false));
                }
                actions[0] = new Block(fallbackExpressions.toArray(new Expression[0]));
                actions[1] = evaluateExpr;
                return new Choose(conditions, actions);
            } else {
                return evaluateExpr;
            }
        }
    }


}
