////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.ErrorExpression;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.ResultDocument;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.Whitespace;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

/**
 * An xsl:result-document element in the stylesheet.
 * <p>
 * The xsl:result-document element takes an attribute href="filename". The filename will
 * often contain parameters, e.g. {position()} to ensure that a different file is produced
 * for each element instance.
 * <p>
 * There is a further attribute "format" which determines the format of the
 * output file, it identifies the name of an xsl:output element containing the output
 * format details.
 */

public class XSLResultDocument extends StyleElement {

    public static final HashSet<String> fans = new HashSet<>(40);    // formatting attribute names

    static {
        fans.add("allow-duplicate-names");
        fans.add("build-tree");
        fans.add("byte-order-mark");
        fans.add("cdata-section-elements");
        fans.add("doctype-public");
        fans.add("doctype-system");
        fans.add("encoding");
        fans.add("escape-uri-attributes");
        fans.add("html-version");
        fans.add("include-content-type");
        fans.add("indent");
        fans.add("item-separator");
        fans.add("json-node-output-method");
        fans.add("media-type");
        fans.add("method");
        fans.add("normalization-form");
        fans.add("omit-xml-declaration");
        fans.add("output-version");
        fans.add("parameter-document");
        fans.add("standalone");
        fans.add("suppress-indentation");
        fans.add("undeclare-prefixes");

        fans.add(SaxonOutputKeys.ATTRIBUTE_ORDER);
        fans.add(SaxonOutputKeys.CANONICAL);
        fans.add(SaxonOutputKeys.CHARACTER_REPRESENTATION);
        fans.add(SaxonOutputKeys.DOUBLE_SPACE);
        fans.add(SaxonOutputKeys.INDENT_SPACES);
        fans.add(SaxonOutputKeys.LINE_LENGTH);
        fans.add(SaxonOutputKeys.NEWLINE);
        fans.add(SaxonOutputKeys.NEXT_IN_CHAIN);
        fans.add(SaxonOutputKeys.RECOGNIZE_BINARY);
        fans.add(SaxonOutputKeys.REQUIRE_WELL_FORMED);
        fans.add(SaxonOutputKeys.PROPERTY_ORDER);
        fans.add(SaxonOutputKeys.SINGLE_QUOTES);
        fans.add(SaxonOutputKeys.SUPPLY_SOURCE_LOCATOR);

    }

    private Expression href;
    private StructuredQName formatQName;     // used when format is a literal string
    private Expression formatExpression;     // used when format is an AVT
    private int validationAction = Validation.STRIP;
    private SchemaType schemaType = null;
    private Map<StructuredQName, Expression> serializationAttributes = new HashMap<>(10);
    private boolean async = true;

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
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return true: yes, it may contain a template-body
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    @Override
    public void prepareAttributes() {
        String formatAttribute = null;
        String hrefAttribute = null;
        String validationAtt = null;
        String typeAtt = null;
        String useCharacterMapsAtt = null;


        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            StructuredQName name = attName.getStructuredQName();
            String value = att.getValue();
            String f = name.getClarkName();
            if (f.equals("format")) {
                formatAttribute = Whitespace.trim(value);
                formatExpression = makeAttributeValueTemplate(formatAttribute, att);
            } else if (f.equals("href")) {
                hrefAttribute = Whitespace.trim(value);
                href = makeAttributeValueTemplate(hrefAttribute, att);
            } else if (f.equals("validation")) {
                validationAtt = Whitespace.trim(value);
            } else if (f.equals("type")) {
                typeAtt = Whitespace.trim(value);
            } else if (f.equals("use-character-maps")) {
                useCharacterMapsAtt = Whitespace.trim(value);
            } else if (fans.contains(f) || (f.startsWith("{") && !StandardNames.SAXON_ASYCHRONOUS.equals(f))) {
                // this is a serialization attribute
                String val = value;
                if (!f.equals(SaxonOutputKeys.ITEM_SEPARATOR) && !f.equals(SaxonOutputKeys.NEWLINE)) {
                    val = Whitespace.trim(value);
                }
                Expression exp = makeAttributeValueTemplate(val, att);
                serializationAttributes.put(name, exp);
            } else if (name.getLocalPart().equals("asynchronous") && name.hasURI(NamespaceConstant.SAXON)) {
                async = processBooleanAttribute("saxon:asynchronous", value);
                if (getCompilation().getCompilerInfo().isCompileWithTracing()) {
                    async = false;
                } else if (!"EE".equals(getConfiguration().getEditionCode())) {
                    compileWarning("saxon:asynchronous - ignored when not running Saxon-EE",
                            SaxonErrorCode.SXWN9013);
                    async = false;
                }
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (formatAttribute != null) {
            if (formatExpression instanceof StringLiteral) {
                formatQName = makeQName(((StringLiteral) formatExpression).getStringValue(), "XTDE1460", "format");
                formatExpression = null;
            } else {
                getPrincipalStylesheetModule().setNeedsDynamicOutputProperties(true);
            }
        }

        if (validationAtt == null) {
            validationAction = getDefaultValidation();
        } else {
            validationAction = validateValidationAttribute(validationAtt);
        }
        if (typeAtt != null) {
            if (!isSchemaAware()) {
                compileError("The @type attribute is available only with a schema-aware XSLT processor", "XTSE1660");
            }
            schemaType = getSchemaType(typeAtt);
            validationAction = Validation.BY_TYPE;
        }

        if (typeAtt != null && validationAtt != null) {
            compileError("The @validation and @type attributes are mutually exclusive", "XTSE1505");
        }

        if (useCharacterMapsAtt != null) {
            String s = XSLOutput.prepareCharacterMaps(this, useCharacterMapsAtt, new Properties());
            serializationAttributes.put(new StructuredQName("", "", "use-character-maps"),
                    new StringLiteral(s));
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        if (href != null && !getConfiguration().getBooleanProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS)) {
            compileError("xsl:result-document is disabled when extension functions are disabled");
        }
        href = typeCheck("href", href);
        formatExpression = typeCheck("format", formatExpression);

        for (StructuredQName prop : serializationAttributes.keySet()) {
            final Expression exp1 = serializationAttributes.get(prop);
            final Expression exp2 = typeCheck(prop.getDisplayName(), exp1);
            if (exp1 != exp2) {
                serializationAttributes.put(prop, exp2);
            }
        }

        getContainingPackage().setCreatesSecondaryResultDocuments(true);

    }

    public static StructuredQName METHOD = new StructuredQName("", "", "method");
    public static StructuredQName BUILD_TREE = new StructuredQName("", "", "build-tree");

    /*@Nullable*/
    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {

        // Check that the call is not within xsl:variable or xsl:function.
        // This is a dynamic error, but worth detecting statically.
        // In fact this is a bit of a fudge. If a function or variable is inlined, we sometimes don't detect
        // XTDE1480 at run-time. Doing this static check improves our chances, though it won't catch all cases.
        AxisIterator ai = iterateAxis(AxisInfo.ANCESTOR);
        NodeInfo node;
        while ((node = ai.next()) != null) {
            if (node instanceof XSLGeneralVariable || node instanceof XSLFunction) {
                issueWarning("An xsl:result-document instruction inside " + node.getDisplayName() +
                        " will always fail at run-time", this);
                return new ErrorExpression("Call to xsl:result-document while in temporary output state", "XTDE1480", false);
            }
        }

        Properties globalProps;
        if (formatExpression == null) {
            try {
                globalProps = getPrincipalStylesheetModule().gatherOutputProperties(formatQName);
            } catch (XPathException err) {
                compileError("Named output format has not been defined", "XTDE1460");
                return null;
            }
        } else {
            globalProps = new Properties();
            getPrincipalStylesheetModule().setNeedsDynamicOutputProperties(true);
        }

        // If no serialization method was specified, we can work it out statically if the
        // first contained instruction is a literal result element. This saves effort at run-time.

        String method = null;
        if (formatExpression == null &&
                globalProps.getProperty("method") == null &&
                serializationAttributes.get(METHOD) == null) {
            AxisIterator kids = iterateAxis(AxisInfo.CHILD);
            NodeInfo first = kids.next();
            if (first instanceof LiteralResultElement) {
                if (first.getURI().equals(NamespaceConstant.XHTML) && first.getLocalPart().equals("html")) {
                    method = "xhtml";
                } else if (first.getLocalPart().equalsIgnoreCase("html") && first.getURI().isEmpty()) {
                    method = "html";
                } else {
                    method = "xml";
                }
                globalProps.setProperty("method", method);
            }
        }

        Properties localProps = new Properties();

        HashSet<StructuredQName> fixed = new HashSet<>(10);
        NamespaceResolver namespaceResolver = getStaticContext().getNamespaceResolver();
        for (StructuredQName property : serializationAttributes.keySet()) {
            Expression exp = serializationAttributes.get(property);
            if (exp instanceof StringLiteral) {
                String s = ((StringLiteral) exp).getStringValue();
                String lname = property.getLocalPart();
                String uri = property.getURI();
                try {

                    ResultDocument.setSerializationProperty(localProps, uri, lname, s,
                        namespaceResolver, false, exec.getConfiguration());
                    fixed.add(property);
                    if (property.equals(METHOD)) {
                        method = s;
                    }
                } catch (XPathException e) {
                    if (NamespaceConstant.SAXON.equals(e.getErrorCodeNamespace())) {
                        compileWarning(e.getMessage(), e.getErrorCodeQName());
                    } else {
                        e.setErrorCode("XTSE0020");
                        compileError(e);
                    }
                }
            }
        }
        for (StructuredQName p : fixed) {
            serializationAttributes.remove(p);
        }

        ResultDocument inst = new ResultDocument(globalProps,
                localProps,
                href,
                formatExpression,
                validationAction,
                schemaType,
                serializationAttributes,
                getContainingPackage().getCharacterMapIndex()
        );

        Expression content = compileSequenceConstructor(exec, decl, true);
        if (content == null) {
            content = Literal.makeLiteral(EmptySequence.getInstance());
        }
        inst.setContentExpression(content);
        inst.setAsynchronous(async);
        return inst;
    }

}

