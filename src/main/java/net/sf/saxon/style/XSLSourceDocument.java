////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.accum.AccumulatorRegistry;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import java.util.HashSet;
import java.util.Set;

/**
 * Handler for xsl:source-document element in XSLT 3.0 stylesheet. <br>
 */

public class XSLSourceDocument extends StyleElement {

    private Expression href = null;
    private Set<Accumulator> accumulators = new HashSet<>();
    private boolean streaming = false;
    private ParseOptions parseOptions;


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
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true: yes, it may contain a sequence constructor
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    @Override
    protected boolean isWithinDeclaredStreamableConstruct() {
        return true;
    }


    @Override
    public void prepareAttributes() {

        parseOptions = new ParseOptions(getConfiguration().getParseOptions());

        String hrefAtt = null;
        String validationAtt = null;
        String typeAtt = null;
        String useAccumulatorsAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            StructuredQName name = attName.getStructuredQName();
            String value = att.getValue();
            String f = name.getClarkName();
            if (f.equals("href")) {
                hrefAtt = value;
                href = makeAttributeValueTemplate(hrefAtt, att);
            } else if (f.equals("validation")) {
                validationAtt = Whitespace.trim(value);
            } else if (f.equals("type")) {
                typeAtt = Whitespace.trim(value);
            } else if (f.equals("use-accumulators")) {
                useAccumulatorsAtt = Whitespace.trim(value);
            } else if (f.equals("streamable")) {
                streaming = processStreamableAtt(value);
            } else if (attName.hasURI(NamespaceConstant.SAXON)) {
                isExtensionAttributeAllowed(attName.getDisplayName());
                String local = attName.getLocalPart();
                switch (local) {
                    case "dtd-validation":
                        parseOptions.setDTDValidationMode(processBooleanAttribute(f, value) ? Validation.STRICT : Validation.SKIP);
                        break;
                    case "expand-attribute-defaults":
                        parseOptions.setExpandAttributeDefaults(processBooleanAttribute(f, value));
                        break;
                    case "line-numbering":
                        parseOptions.setLineNumbering(processBooleanAttribute(f, value));
                        break;
                    case "xinclude":
                        parseOptions.setXIncludeAware(processBooleanAttribute(f, value));
//                } else if (local.equals("tree-model")) {
//                    List<TreeModel> models = getConfiguration().getExternalObjectModels()
//                    parseOptions.setModel(processBooleanAttribute(f, value));
                        break;
                    case "validation-params":
                        // TODO
                        break;
                    case "strip-space":
                        switch (Whitespace.normalizeWhitespace(value).toString()) {
                            case "#all":
                                parseOptions.setSpaceStrippingRule(AllElementsSpaceStrippingRule.getInstance());
                                break;
                            case "#none":
                                parseOptions.setSpaceStrippingRule(NoElementsSpaceStrippingRule.getInstance());
                                break;
                            case "#ignorable":
                                parseOptions.setSpaceStrippingRule(IgnorableSpaceStrippingRule.getInstance());
                                break;
                            case "#default":
                                parseOptions.setSpaceStrippingRule(null);
                                break;
                            default:
                                invalidAttribute("saxon:strip-space", "#all|#none|#ignorable|#default");
                                break;
                        }
                        break;
                    default:
                        checkUnknownAttribute(attName);
                        break;
                }
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (hrefAtt == null) {
            reportAbsence("href");
        }

        if (validationAtt != null) {
            int validation = validateValidationAttribute(validationAtt);
            parseOptions.setSchemaValidationMode(validation);
        }

        if (typeAtt != null) {
            if (!isSchemaAware()) {
                compileError("The @type attribute is available only with a schema-aware XSLT processor", "XTSE1660");
            }
            parseOptions.setSchemaValidationMode(Validation.BY_TYPE);
            parseOptions.setTopLevelType(getSchemaType(typeAtt));
        }

        if (typeAtt != null && validationAtt != null) {
            compileError("The @validation and @type attributes are mutually exclusive", "XTSE1505");
        }

        if (useAccumulatorsAtt == null) {
            useAccumulatorsAtt = "";
        }

        AccumulatorRegistry registry = getPrincipalStylesheetModule().getStylesheetPackage().getAccumulatorRegistry();
        accumulators = registry.getUsedAccumulators(useAccumulatorsAtt, this);

    }


    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        //checkParamComesFirst(false);
        href = typeCheck("select", href);
        if (!hasChildNodes()) {
            compileWarning("An empty xsl:source-document instruction has no effect", SaxonErrorCode.SXWN9009);
        }
    }

    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        Configuration config = getConfiguration();
        if (parseOptions.getSpaceStrippingRule() == null) {
            parseOptions.setSpaceStrippingRule(getPackageData().getSpaceStrippingRule());
        }
        parseOptions.setApplicableAccumulators(accumulators);
        Expression action = compileSequenceConstructor(exec, decl, false);
        if (action == null) {
            // body of xsl:source-document is empty: it's a no-op.
            return Literal.makeEmptySequence();
        }
        try {
            ExpressionVisitor visitor = makeExpressionVisitor();
            action = action.simplify();
            action = action.typeCheck(visitor, config.makeContextItemStaticInfo(NodeKindTest.DOCUMENT, false));

            return config.makeStreamInstruction(
                        href, action, streaming, parseOptions, null, allocateLocation(),
                        makeRetainedStaticContext());

        } catch (XPathException err) {
            compileError(err);
            return null;
        }
    }

}
