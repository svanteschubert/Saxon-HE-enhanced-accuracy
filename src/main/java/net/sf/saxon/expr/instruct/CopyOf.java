////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.accum.AccumulatorManager;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.nodetest.NamedXNodePredicate;
import net.sf.saxon.type.gnode.AnyXNodeType;
import net.sf.saxon.type.gnode.NamedXNodeType;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.pattern.qname.AnyQNameTest;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.tree.tiny.TinyNodeImpl;
import net.sf.saxon.tree.wrapper.VirtualCopy;
import net.sf.saxon.tree.wrapper.VirtualUntypedCopy;
import net.sf.saxon.type.*;
import net.sf.saxon.value.SequenceType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Supplier;


/**
 * An xsl:copy-of element in the stylesheet.
 */

public class CopyOf extends Instruction implements ValidatingInstruction {

    private final Operand selectOp;
    private final boolean copyNamespaces;
    private boolean copyAccumulators;
    private final int validation;
    private final SchemaType schemaType;
    private boolean requireDocumentOrElement = false;
    private final boolean rejectDuplicateAttributes;
    //private boolean readOnce = false;
    private final boolean validating;
    private boolean copyLineNumbers = false;
    private boolean copyForUpdate = false;
    private boolean isSchemaAware = true;
    private double invocations = 1;
    private double numberOfItems = 20;

    /**
     * Create an xsl:copy-of instruction (also used in XQuery for implicit copying)
     *
     * @param select                    expression that selects the nodes to be copied
     * @param copyNamespaces            true if namespaces are to be copied
     * @param validation                validation mode for the result tree
     * @param schemaType                schema type for validating the result tree
     * @param rejectDuplicateAttributes true if duplicate attributes are to be rejected (XQuery). False
     *                                  if duplicates are handled by discarding all but the first (XSLT).
     */

    public CopyOf(Expression select,
                  boolean copyNamespaces,
                  int validation,
                  SchemaType schemaType,
                  boolean rejectDuplicateAttributes) {
        selectOp = new Operand(this, select, OperandRole.SINGLE_ATOMIC);
        this.copyNamespaces = copyNamespaces;
        this.validation = validation;
        this.schemaType = schemaType;
        validating = schemaType != null || validation == Validation.STRICT || validation == Validation.LAX;
        this.rejectDuplicateAttributes = rejectDuplicateAttributes;
    }

    public Expression getSelect() {
        return selectOp.getChildExpression();
    }

    /**
     * Set the select expression
     *
     * @param select the new select expression
     */

    public void setSelect(Expression select) {
        selectOp.setChildExpression(select);
    }

    @Override
    public Iterable<Operand> operands() {
        return selectOp;
    }

    /**
     * Get the validation mode
     *
     * @return the validation mode
     */

    @Override
    public int getValidationAction() {
        return validation;
    }

    /**
     * Test if the instruction is doing validation
     *
     * @return true if it is
     */

    public boolean isValidating() {
        return validating;
    }

    /**
     * Get the schema type to be used for validation
     *
     * @return the schema type, or null if not validating against a type
     */

    @Override
    public SchemaType getSchemaType() {
        return schemaType;
    }

    /**
     * Set the "is schema aware" property
     *
     * @param schemaAware true if schema awareness is enabled
     */

    public void setSchemaAware(boolean schemaAware) {
        this.isSchemaAware = schemaAware;
    }

    /**
     * Set whether line numbers are to be copied from the source to the result.
     * Default is false.
     *
     * @param copy true if line numbers are to be copied
     */

    public void setCopyLineNumbers(boolean copy) {
        copyLineNumbers = copy;
    }

    /**
     * Determine whether this instruction creates new nodes.
     * The result depends on the type of the select expression.
     */

    @Override
    public final boolean mayCreateNewNodes() {
        return !getSelect().getItemType().isPlainType();
    }

    /**
     * Get the name of this instruction, for diagnostics and tracing
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_COPY_OF;
    }

    /**
     * For XQuery, the operand (select) must be a single element or document node.
     *
     * @param requireDocumentOrElement true if the argument must be a single element or document node
     */
    public void setRequireDocumentOrElement(boolean requireDocumentOrElement) {
        this.requireDocumentOrElement = requireDocumentOrElement;
    }

    /**
     * Test whether this expression requires a document or element node
     *
     * @return true if this expression requires the value of the argument to be a document or element node,
     * false if there is no such requirement
     */

    public boolean isDocumentOrElementRequired() {
        return requireDocumentOrElement;
    }

    /**
     * Set whether this instruction is creating a copy for the purpose of updating (XQuery transform expression)
     *
     * @param forUpdate true if this copy is being created to support an update
     */

    public void setCopyForUpdate(boolean forUpdate) {
        copyForUpdate = forUpdate;
    }

    /**
     * Ask whether this instruction is creating a copy for the purpose of updating (XQuery transform expression)
     *
     * @return true if this copy is being created to support an update
     */

    public boolean isCopyForUpdate() {
        return copyForUpdate;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both iterate() and
     * process() methods natively.
     */

    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD | PROCESS_METHOD | WATCH_METHOD;
    }

    /**
     * Ask whether namespaces are to be copied or not
     *
     * @return true if namespaces are to be copied (the default)
     */

    public boolean isCopyNamespaces() {
        return copyNamespaces;
    }

    /**
     * Say whether accumulator values should be copied from the source document
     * @param copy true if values should be copied
     */

    public void setCopyAccumulators(boolean copy) {
        copyAccumulators = copy;
    }

    /**
     * Ask whether accumulator values should be copied from the source document
     *
     * @return true if values should be copied
     */

    public boolean isCopyAccumulators() {
        return copyAccumulators;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings information about variables whose bindings need to be replaced
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        CopyOf c = new CopyOf(getSelect().copy(rebindings), copyNamespaces, validation, schemaType, rejectDuplicateAttributes);
        ExpressionTool.copyLocationInfo(this, c);
        c.setCopyForUpdate(copyForUpdate);
        c.setCopyLineNumbers(copyLineNumbers);
        c.isSchemaAware = isSchemaAware;
        c.setCopyAccumulators(copyAccumulators);
        return c;
    }

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        ItemType in = getSelect().getItemType();
        if (!isSchemaAware) {
            return in;
        }
        Configuration config = getConfiguration();
        if (schemaType != null) {
            TypeHierarchy th = config.getTypeHierarchy();
            Affinity e = th.relationship(in, NodeKindType.ELEMENT);
            if (e == Affinity.SAME_TYPE || e == Affinity.SUBSUMED_BY) {
                return new NamedXNodeType(Type.ELEMENT, AnyQNameTest.getInstance(), schemaType, false, config);
            }
            Affinity a = th.relationship(in, NodeKindType.ATTRIBUTE);
            if (a == Affinity.SAME_TYPE || a == Affinity.SUBSUMED_BY) {
                return new NamedXNodeType(Type.ATTRIBUTE, AnyQNameTest.getInstance(), schemaType, false, config);
            }
        } else {
            switch (validation) {
                case Validation.PRESERVE:
                    return in;
                case Validation.STRIP: {
                    TypeHierarchy th = config.getTypeHierarchy();
                    Affinity e = th.relationship(in, NodeKindType.ELEMENT);
                    if (e == Affinity.SAME_TYPE || e == Affinity.SUBSUMED_BY) {
                        return new NamedXNodeType(Type.ELEMENT, AnyQNameTest.getInstance(), Untyped.INSTANCE, false, config);
                    }
                    Affinity a = th.relationship(in, NodeKindType.ATTRIBUTE);
                    if (a == Affinity.SAME_TYPE || a == Affinity.SUBSUMED_BY) {
                        return new NamedXNodeType(Type.ATTRIBUTE, AnyQNameTest.getInstance(), BuiltInAtomicType.UNTYPED_ATOMIC, false, config);
                    }
                    if (e != Affinity.DISJOINT || a != Affinity.DISJOINT) {
                        // it might be an element or attribute
                        if (in instanceof NodeTest) {
                            return AnyXNodeType.getInstance();
                        } else {
                            return AnyItemType.INSTANCE;
                        }
                    } else {
                        // it can't be an element or attribute, so stripping type annotations can't affect it
                        return in;
                    }
                }
                case Validation.STRICT:
                case Validation.LAX:
                    if (in instanceof NamedXNodePredicate) {
                        NamedXNodePredicate fpTest = (NamedXNodePredicate)in;
                        TypeHierarchy th = config.getTypeHierarchy();
                        int fp = fpTest.getRequiredFingerprint();
                        if (fp != -1) {
                            Affinity e = th.relationship(in, NodeKindType.ELEMENT);
                            if (e == Affinity.SAME_TYPE || e == Affinity.SUBSUMED_BY) {
                                Schema schema = getRetainedStaticContext().getImportedSchema();
                                IElementDecl elem = schema.getElementDecl(fp);
                                if (elem != null) {
                                    try {
                                        return new NamedXNodeType(Type.ELEMENT, AnyQNameTest.getInstance(), elem.getType(), false, config);
                                    } catch (MissingComponentException e1) {
                                        return new NamedXNodeType(Type.ELEMENT, AnyQNameTest.getInstance(), AnyType.INSTANCE, false, config);
                                    }
                                } else {
                                    // Although there is no element declaration now, there might be one at run-time
                                    return new NamedXNodeType(Type.ELEMENT, AnyQNameTest.getInstance(), AnyType.INSTANCE, false, config);
                                }
                            }
                            Affinity a = th.relationship(in, NodeKindType.ATTRIBUTE);
                            if (a == Affinity.SAME_TYPE || a == Affinity.SUBSUMED_BY) {
                                Schema schema = getRetainedStaticContext().getImportedSchema();
                                IAttributeDecl attr = schema.getAttributeDecl(fp);
                                if (attr != null) {
                                    try {
                                        return new NamedXNodeType(Type.ATTRIBUTE, AnyQNameTest.getInstance(), attr.getType(), false, config);
                                    } catch (MissingComponentException e1) {
                                        return new NamedXNodeType(Type.ATTRIBUTE, AnyQNameTest.getInstance(), AnySimpleType.INSTANCE, false, config);
                                    }
                                } else {
                                    // Although there is no attribute declaration now, there might be one at run-time
                                    return new NamedXNodeType(Type.ATTRIBUTE, AnyQNameTest.getInstance(), AnySimpleType.INSTANCE, false, config);
                                }
                            }
                        } else {
                            Affinity e = th.relationship(in, NodeKindType.ELEMENT);
                            if (e == Affinity.SAME_TYPE || e == Affinity.SUBSUMED_BY) {
                                return NodeKindType.ELEMENT;
                            }
                            Affinity a = th.relationship(in, NodeKindType.ATTRIBUTE);
                            if (a == Affinity.SAME_TYPE || a == Affinity.SUBSUMED_BY) {
                                return NodeKindType.ATTRIBUTE;
                            }
                        }
                        return AnyXNodeType.getInstance();
                    } else if (in instanceof AtomicType) {
                        return in;
                    } else {
                        return AnyItemType.INSTANCE;
                    }
            }
        }
        return getSelect().getItemType();
    }

    @Override
    public UType getStaticUType(UType contextItemType) {
        return getSelect().getStaticUType(contextItemType);
    }

    @Override
    public int getCardinality() {
        return getSelect().getCardinality();
    }

    @Override
    public int getDependencies() {
        return getSelect().getDependencies();
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        if (isDocumentOrElementRequired()) {
            // this implies the expression is actually an XQuery validate{} expression, hence the error messages
            Supplier<RoleDiagnostic> role =
                    () -> new RoleDiagnostic(RoleDiagnostic.TYPE_OP, "validate", 0, "XQTY0030");
            Configuration config = visitor.getConfiguration();
            setSelect(config.getTypeChecker(false).staticTypeCheck(
                    getSelect(), SequenceType.SINGLE_NODE, role, visitor));

            TypeHierarchy th = config.getTypeHierarchy();
            ItemType t = getSelect().getItemType();
            if (th.isSubType(t, NodeKindType.ATTRIBUTE)) {
                throw new XPathException("validate{} expression cannot be applied to an attribute", "XQTY0030");
            }
            if (th.isSubType(t, NodeKindType.TEXT)) {
                throw new XPathException("validate{} expression cannot be applied to a text node", "XQTY0030");
            }
            if (th.isSubType(t, NodeKindType.COMMENT)) {
                throw new XPathException("validate{} expression cannot be applied to a comment node", "XQTY0030");
            }
            if (th.isSubType(t, NodeKindType.PROCESSING_INSTRUCTION)) {
                throw new XPathException("validate{} expression cannot be applied to a processing instruction node", "XQTY0030");
            }
            if (th.isSubType(t, NodeKindType.NAMESPACE)) {
                throw new XPathException("validate{} expression cannot be applied to a namespace node", "XQTY0030");
            }
        }
        return this;
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        selectOp.optimize(visitor, contextItemType);
        if (Literal.isEmptySequence(getSelect())) {
            return getSelect();
        }
        adoptChildExpression(getSelect());
        if (getSelect().getItemType().isPlainType()) {
            return getSelect();
        }
        if (getSelect() instanceof Block /* && visitor.isOptimizeForStreaming() */) {
            // change copy-of(a, b, c) to (copy-of(a), copy-of(b), copy-of(c)) - bug 5958
            Block b1 = (Block)getSelect();
            Expression[] splitCopy = new Expression[b1.size()];
            for (int i=0; i<splitCopy.length; i++) {
                Expression exp = b1.getOperanda()[i].getChildExpression().copy(new RebindingMap());
                if (exp.getItemType().isPlainType()) {
                    splitCopy[i] = exp;
                } else {
                    splitCopy[i] = new CopyOf(exp, copyNamespaces, validation, schemaType, rejectDuplicateAttributes);
                }
            }
            return new Block(splitCopy);
        }
        return this;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("copyOf", this);
        if (validation != Validation.SKIP) {
            out.emitAttribute("validation", Validation.describe(validation));
        }
        if (schemaType != null) {
            out.emitAttribute("type", schemaType.getStructuredQName());
        }

        StringBuilder fsb = new StringBuilder(16);
        if (requireDocumentOrElement) {
            fsb.append('p');
        }
        if (rejectDuplicateAttributes) {
            fsb.append('a');
        }
        if (validating) {
            fsb.append('v');
        }
        if (copyLineNumbers) {
            fsb.append('l');
        }
        if (copyForUpdate) {
            fsb.append('u');
        }
        if (isSchemaAware) {
            fsb.append('s');
        }
        if (copyNamespaces) {
            fsb.append('c');
        }
        if (copyAccumulators) {
            fsb.append('m');
        }
        if (!fsb.isEmpty()) {
            out.emitAttribute("flags", fsb.toString());
        }
        String schemaRole = getRetainedStaticContext().getImportedSchemaRoleName();
        if (!schemaRole.isEmpty()) {
            out.emitAttribute("schemaRole", schemaRole);
        }
        getSelect().export(out);
        out.endElement();
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "CopyOf";
    }

    private void copyOneNode(XPathContext context, Outputter out, NodeInfo item, int copyOptions) throws XPathException {
        Controller controller = context.getController();
        boolean copyBaseURI = out.getSystemId() == null;
        int kind = item.getNodeKind();
        if (requireDocumentOrElement &&
                !(kind == Type.ELEMENT || kind == Type.DOCUMENT)) {
            throw new XPathException("Operand of validate expression must be a document or element node")
                    .withXPathContext(context)
                    .withErrorCode("XQTY0030");
        }
        final Configuration config = controller.getConfiguration();
        Schema schema = getRetainedStaticContext().getImportedSchema();
        switch (kind) {

            case Type.ELEMENT: {

                Outputter eval = out;
                if (validating) {
                    ParseOptions options = new ParseOptions()
                            .withSchemaValidationMode(validation);
                    SchemaType type = schemaType;
                    if (type == null && (validation == Validation.STRICT || validation == Validation.LAX)) {
                        // Bug 3062
                        String xsitype = item.getAttributeValue(NamespaceUri.SCHEMA_INSTANCE, "type");
                        if (xsitype != null) {
                            StructuredQName typeName;
                            try {
                                typeName = StructuredQName.fromLexicalQName(
                                        xsitype,
                                        true,
                                        0,
                                        item.getAllNamespaces());
                            } catch (XPathException e) {
                                throw new XPathException("Invalid QName in xsi:type attribute of element being validated: "
                                                                 + xsitype + ". " + e.getMessage(), "XTTE1510");
                            }
                            type = schema.getSchemaType(typeName);
                            if (type == null) {
                                throw new XPathException("Unknown xsi:type in element being validated: " + xsitype, "XTTE1510");
                            }
                        }
                    }
                    options = options
                            .withTopLevelType(type)
                            .withTopLevelElement(NameOfNode.makeName(item).getStructuredQName())
                            .withErrorReporter(context.getErrorReporter());
                    config.prepareValidationReporting(context, options);
                    Receiver validator = schema.getElementValidator(out, options, getLocation());
                    eval = new ComplexContentOutputter(validator);
                }
                if (copyBaseURI) {
                    eval.setSystemId(computeNewBaseUri(item, getStaticBaseURIString()));
                }

                PipelineConfiguration pipe = out.getPipelineConfiguration();
                if (copyLineNumbers) {
                    LocationCopier copier = new LocationCopier(false, out.getSystemId());
                    pipe.setCopyInformee(CSharp.methodRef(copier::notifyElementNode));
                }
                item.copy(eval, copyOptions, getLocation());
                //Navigator.copy(item, eval, copyOptions, getLocation());
                if (copyLineNumbers) {
                    pipe.setCopyInformee(null);
                }
                break;
            }
            case Type.ATTRIBUTE:
                if (schemaType != null && schemaType.isComplexType()) {
                    XPathException e = new XPathException("When copying an attribute with schema validation, the requested type must not be a complex type")
                            .withLocation(getLocation())
                            .withXPathContext(context)
                            .withErrorCode("XTTE1535");
                    throw dynamicError(getLocation(), e, context);
                }
                try {
                    copyAttribute(item, (SimpleType) schemaType, validation, this, out, context, rejectDuplicateAttributes);
                } catch (NoOpenStartTagException err) {
                    XPathException e = new XPathException(err.getMessage())
                            .withLocation(getLocation())
                            .withXPathContext(context)
                            .withErrorCode(err.getErrorCodeQName());
                    throw dynamicError(getLocation(), e, context);
                }
                break;

            case Type.TEXT:
                out.characters(item.getUnicodeStringValue(), getLocation(), ReceiverOption.NONE);
                break;

            case Type.PROCESSING_INSTRUCTION:
                if (copyBaseURI) {
                    out.setSystemId(item.getBaseURI());
                }
                out.processingInstruction(item.getDisplayName(), item.getUnicodeStringValue(), getLocation(), ReceiverOption.NONE);
                break;

            case Type.COMMENT:
                out.comment(item.getUnicodeStringValue(), getLocation(), ReceiverOption.NONE);
                break;

            case Type.NAMESPACE:
                try {
                    out.namespace(item.getLocalPart(), NamespaceUri.of(item.getStringValue()), ReceiverOption.NONE);
                } catch (NoOpenStartTagException err) {
                    XPathException e = new XPathException(err.getMessage())
                            .withXPathContext(context)
                            .withErrorCode(err.getErrorCodeQName());
                    throw dynamicError(getLocation(), e, context);
                }
                break;

            case Type.DOCUMENT: {
                ParseOptions options = new ParseOptions()
                        .withSchemaValidationMode(validation)
                        .withSpaceStrippingRule(NoElementsSpaceStrippingRule.INSTANCE)
                        .withTopLevelType(schemaType)
                        .withErrorReporter(context.getErrorReporter());
                config.prepareValidationReporting(context, options);
                Receiver val = schema.getDocumentValidator(out, item.getBaseURI(), options, getLocation());
                if (copyBaseURI) {
                    val.setSystemId(item.getBaseURI());
                }
                PipelineConfiguration savedPipe = null;
                if (copyLineNumbers) {
                    savedPipe = new PipelineConfiguration(val.getPipelineConfiguration());
                    LocationCopier copier = new LocationCopier(true, item.getBaseURI());
                    val.getPipelineConfiguration().setCopyInformee(CSharp.methodRef(copier::notifyElementNode));

                }
                item.copy(val, copyOptions, getLocation());
                if (copyLineNumbers) {
                    val.setPipelineConfiguration(savedPipe);
                }
                //                        if (val != out) {
                //                            See bug 2403
                //                            val.close(); // needed to flush out unresolved IDREF values when validating: test copy-5021
                //                        }
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown node kind " + item.getNodeKind());
        }
    }

    public static String computeNewBaseUri(NodeInfo source, String staticBaseURI) {
        // These rules are the rules for xsl:copy-of instruction in XSLT. The same code is used to support the
        // validate{} expression in XQuery. XQuery says nothing about the base URI of a node that results
        // from a validate{} expression, so until it does, we might as well use the same logic.
        String newBaseUri;
        String xmlBase = source.getAttributeValue(NamespaceUri.XML, "base");
        if (xmlBase != null) {
            try {
                URI xmlBaseUri = new URI(xmlBase);
                if (xmlBaseUri.isAbsolute()) {
                    newBaseUri = xmlBase;
                } else if (staticBaseURI != null) {
                    URI sbu = new URI(staticBaseURI);
                    URI abs = sbu.resolve(xmlBaseUri);
                    newBaseUri = abs.toString();
                } else {
                    newBaseUri = source.getBaseURI();
                }
            } catch (URISyntaxException err) {
                newBaseUri = source.getBaseURI();
            }
        } else {
            newBaseUri = source.getBaseURI();
        }
        return newBaseUri;
    }

    /**
     * Method shared by xsl:copy and xsl:copy-of to copy an attribute node
     *
     * @param source           the node to be copied
     * @param schemaType       the simple type against which the value is to be validated, if any
     * @param validation       one of preserve, strip, strict, lax
     * @param instruction      the calling instruction, used for diagnostics
     * @param output the destination for the result
     * @param context          the dynamic context
     * @param rejectDuplicates true if duplicate attributes with the same name are disallowed (XQuery)
     * @throws XPathException if a failure occurs
     */

    public static void copyAttribute(NodeInfo source,
                              SimpleType schemaType,
                              int validation,
                              Instruction instruction,
                              Outputter output, XPathContext context,
                              boolean rejectDuplicates)
            throws XPathException {
        int opt = rejectDuplicates ? ReceiverOption.REJECT_DUPLICATES : ReceiverOption.NONE;
        Schema schema = instruction.getRetainedStaticContext().getImportedSchema();
        SimpleType annotation = validateAttribute(schema, source, schemaType, validation, context);
        try {
            output.attribute(NameOfNode.makeName(source), annotation, source.getStringValue(), instruction.getLocation(), opt);
        } catch (XPathException e) {
            if (instruction.getPackageData().getHostLanguage() == HostLanguage.XQUERY && e.hasErrorCode("XTTE0950")) {
                e.setErrorCode("XQTY0086");
            }
            throw e.maybeWithLocation(instruction.getLocation())
                    .maybeWithContext(context);
        }
    }

    /**
     * Validate an attribute node and return the type annotation to be used
     *
     * @param source     the node to be copied
     * @param schemaType the simple type against which the value is to be validated, if any
     * @param validation one of preserve, strip, strict, lax
     * @param context    the dynamic context
     * @return the type annotation to be used for the attribute
     * @throws XPathException if the attribute is not valid
     */


    public static SimpleType validateAttribute(
            Schema schema, NodeInfo source, SimpleType schemaType, int validation, XPathContext context) throws XPathException {

        SimpleType annotation = BuiltInAtomicType.UNTYPED_ATOMIC;
        if (schemaType != null) {
            if (schemaType.isNamespaceSensitive()) {
                XPathException nsErr = new XPathException("Cannot create a parentless attribute whose " +
                                                                "type is namespace-sensitive (such as xs:QName)");
                nsErr.setErrorCode("XTTE1545");
                throw nsErr;
            }
            UnicodeString value = source.getUnicodeStringValue();
            ValidationFailure valErr = schemaType.validateContent(
                    value, DummyNamespaceResolver.INSTANCE, context.getConfiguration().getConversionRules());
            if (valErr != null) {
                valErr.setMessage("Attribute being copied does not match the required type. " +
                                       valErr.getMessage());
                valErr.setErrorCode("XTTE1510");
                throw valErr.makeException();
            }
            annotation = schemaType;
        } else if (validation == Validation.STRICT || validation == Validation.LAX) {
            UnicodeString value = source.getUnicodeStringValue();
            try {
                annotation = schema.validateAttribute(NameOfNode.makeName(source).getStructuredQName(), value, validation);
            } catch (ValidationException e) {
                XPathException err = XPathException.makeXPathException(e);
                err.setErrorCodeQName(e.getErrorCodeQName());
                err.setIsTypeError(true);
                throw err;
            }

        } else if (validation == Validation.PRESERVE) {
            annotation = (SimpleType) source.getSchemaType();
            if (!annotation.equals(BuiltInAtomicType.UNTYPED_ATOMIC) && annotation.isNamespaceSensitive()) {
                XPathException err = new XPathException("Cannot preserve type annotation when copying an attribute with namespace-sensitive content");
                err.setErrorCode(context.getController().getExecutable().getHostLanguage() == HostLanguage.XSLT ? "XTTE0950" : "XQTY0086");
                err.setIsTypeError(true);
                throw err;
            }
        }
        return annotation;
    }

    private boolean mustPush() {
        return schemaType != null || validation == Validation.LAX || validation == Validation.STRICT ||
                /*!copyNamespaces ||*/ copyForUpdate;
    }

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        PullEvaluator pull = makeElaborator().elaborateForPull();
        return pull.iterate(context);
//        final Controller controller = context.getController();
//        boolean isXSLT = getRetainedStaticContext().getPackageData().isXSLT();
//        assert controller != null;
//        if (schemaType == null && !copyForUpdate) {
//            SequenceIterator result = makeVirtualCopy(getSelect().iterate(context), controller, isXSLT);
//            if (result != null) {
//                return result;
//            }
//        }
//        PipelineConfiguration pipe = controller.makePipelineConfiguration();
//        pipe.setXPathContext(context);
//        SequenceCollector out = new SequenceCollector(pipe);
//        if (copyForUpdate) {
//            out.setTreeModel(TreeModel.LINKED_TREE);
//        }
//        pipe.setHostLanguage(getPackageData().getHostLanguage());
//        try {
//            process(new ComplexContentOutputter(out), context);
//        } catch (XPathException err) {
//            err.maybeSetLocation(getLocation());
//            err.maybeSetContext(context);
//            throw err;
//        }
//        return out.getSequence().iterate();
    }

    private ItemMappingIterator makeVirtualCopy(SequenceIterator input, Controller controller, boolean isXSLT) throws XPathException {
        if (validation == Validation.PRESERVE) {
            // create a virtual copy of the underlying nodes
            ItemMappingFunction copier = ItemMapper.of(item -> {
                if (item instanceof NodeInfo) {
                    if (((NodeInfo) item).getTreeInfo().isTyped()) {
                        if (!copyNamespaces && ((NodeInfo) item).getNodeKind() == Type.ELEMENT) {
                            // A lot of extra work here just to check for error XTTE0950, but the conditions are rare
                            Sink sink = new Sink(controller.makePipelineConfiguration());
                            ((NodeInfo) item).copy(sink, CopyOptions.TYPE_ANNOTATIONS, getLocation());
                        }
                        if (((NodeInfo) item).getNodeKind() == Type.ATTRIBUTE &&
                                ((SimpleType) ((NodeInfo) item).getSchemaType()).isNamespaceSensitive()) {
                            throw new XPathException("Cannot copy an attribute with namespace-sensitive content except as part of its containing element", "XTTE0950");
                        }
                    }
                    VirtualCopy vc = VirtualCopy.makeVirtualCopy((NodeInfo) item);
                    vc.setDropNamespaces(!copyNamespaces);
                    vc.getTreeInfo().setCopyAccumulators(copyAccumulators);
                    if (isXSLT && copyAccumulators) {
                        vc.getTreeInfo().setCopyAccumulators(true);
                        AccumulatorManager am = ((XsltController) controller).getAccumulatorManager();
                        am.setApplicableAccumulators(vc.getTreeInfo(), am.getApplicableAccumulators(((NodeInfo) item).getTreeInfo()));
                    }
                    if (((NodeInfo) item).getNodeKind() == Type.ELEMENT) {
                        vc.setSystemId(computeNewBaseUri((NodeInfo) item, getStaticBaseURIString()));
                    }
                    return vc;
                } else {
                    return item;
                }
            });
            return new ItemMappingIterator(input, copier, true);
        } else if (validation == Validation.STRIP) {
            // create a virtual copy of the underlying nodes
            ItemMappingFunction copier = ItemMapper.of(item -> {
                if (!(item instanceof NodeInfo)) {
                    return item;
                }
                VirtualCopy vc = VirtualUntypedCopy.makeVirtualUntypedTree((NodeInfo) item, (NodeInfo) item);
                if (copyAccumulators) {
                    vc.getTreeInfo().setCopyAccumulators(true);
                    AccumulatorManager am = ((XsltController) controller).getAccumulatorManager();
                    am.setApplicableAccumulators(vc.getTreeInfo(), am.getApplicableAccumulators(((NodeInfo) item).getTreeInfo()));
                }
                vc.setDropNamespaces(!copyNamespaces);
                if (((NodeInfo) item).getNodeKind() == Type.ELEMENT) {
                    vc.setSystemId(computeNewBaseUri((NodeInfo) item, getStaticBaseURIString()));
                }
                return vc;
            });
            return new ItemMappingIterator(input, copier, true);
        } else {
            return null;
        }
    }

    /**
     * Make an elaborator for this expression
     *
     * @return an appropriate {@link Elaborator}
     */
    @Override
    public Elaborator getElaborator() {
        return new CopyOfElaborator();
    }

    private static class CopyOfElaborator extends PushElaborator {

        @Override
        public PullEvaluator elaborateForPull() {
            CopyOf expr = (CopyOf)getExpression();
            boolean isXSLT = expr.getRetainedStaticContext().getPackageData().isXSLT();
            if (expr.schemaType == null && !expr.copyForUpdate &&
                    (expr.validation == Validation.PRESERVE || expr.validation == Validation.STRIP)) {
                PullEvaluator select = expr.getSelect().makeElaborator().elaborateForPull();
                return context -> {
                    Controller controller = context.getController();
                    return expr.makeVirtualCopy(select.iterate(context), controller, isXSLT);
                };
            } else {
                HostLanguage host = expr.getPackageData().getHostLanguage();
                int hostLanguageVersion = expr.getPackageData().getHostLanguageVersion();
                PushEvaluator push = elaborateForPush();
                return context -> {
                    final Controller controller = context.getController();
                    assert controller != null;
                    PipelineConfiguration pipe = controller.makePipelineConfiguration();
                    pipe.setXPathContext(context);
                    SequenceCollector out = new SequenceCollector(pipe, (int)(expr.numberOfItems / expr.invocations));
                    if (expr.copyForUpdate) {
                        out.setTreeModel(TreeModel.LINKED_TREE);
                    }
                    pipe.setHostLanguage(host, hostLanguageVersion);
                    try {
                        TailCall tc = push.processLeavingTail(new ComplexContentOutputter(out), context);
                        Expression.dispatchTailCall(tc);
                    } catch (XPathException err) {
                        err.maybeSetLocation(expr.getLocation());
                        err.maybeSetContext(context);
                        throw err;
                    }
                    GroundedValue result = out.getSequence();
                    expr.invocations++;
                    expr.numberOfItems += result.getLength();
                    return result.iterate();
                };
            }
        }

        @Override
        public ItemEvaluator elaborateForItem() {
            if (((CopyOf)getExpression()).copyForUpdate) {
                PushEvaluator pushEval = elaborateForPush();
                return context -> {
                    Controller controller = context.getController();
                    assert controller != null;
                    SequenceCollector seq = controller.allocateSequenceOutputter(1);
                    seq.setTreeModel(TreeModel.LINKED_TREE);
                    TailCall tc = pushEval.processLeavingTail(new ComplexContentOutputter(seq), context);
                    Expression.dispatchTailCall(tc);
                    seq.close();
                    return seq.getFirstItem();
                };
            } else {
                return super.elaborateForItem();
            }
        }

        @Override
        public PushEvaluator elaborateForPush() {
            CopyOf expr = (CopyOf) getExpression();
            if (expr.copyAccumulators) {
                if (expr.mustPush()) {
                    // This typically happens with the combination copy-accumulators=yes, validation=strict
                    // Test case accumulators-070
                    // We have to create a physical copy because of the validation requirement, but this makes
                    // it difficult to copy the accumulator values.
                    PullEvaluator selectPull = expr.getSelect().makeElaborator().elaborateForPull();
                    return (output, context) -> {
                        SequenceTool.supply(selectPull.iterate(context), (ItemConsumer<? super Item>) item -> {
                            if (item instanceof NodeInfo) {
                                TinyBuilder builder = new TinyBuilder(output.getPipelineConfiguration());
                                ComplexContentOutputter cco = new ComplexContentOutputter(builder);
                                cco.open();
                                expr.copyOneNode(context, cco, (NodeInfo) item, CopyOptions.ALL_NAMESPACES);
                                cco.close();
                                TinyNodeImpl copy = (TinyNodeImpl) builder.getCurrentRoot();
                                copy.getTree().setCopiedFrom((NodeInfo) item);
                                output.append(copy);
                            } else {
                                output.append(item);
                            }
                        });
                        return null;
                    };
                } else {
                    // Use the iterate() method to create a virtual copy.
                    PullEvaluator pull = elaborateForPull();
                    return (output, context) -> {
                        SequenceTool.supply(pull.iterate(context), (ItemConsumer<? super Item>) output::append);
                        return null;
                    };
                }
            } else {

                int copyOptions =
                        (expr.validation == Validation.SKIP ? 0 : CopyOptions.TYPE_ANNOTATIONS)
                                | (expr.copyNamespaces ? CopyOptions.ALL_NAMESPACES : 0)
                                | (expr.copyForUpdate ? CopyOptions.FOR_UPDATE : 0);
                PullEvaluator selectPull = expr.getSelect().makeElaborator().elaborateForPull();
                return (output, context) -> {
                    SequenceTool.supply(selectPull.iterate(context), (ItemConsumer<? super Item>) item -> {
                        if (item instanceof NodeInfo) {
                            expr.copyOneNode(context, output, (NodeInfo) item, copyOptions);
                        } else {
                            output.append(item, expr.getLocation(), ReceiverOption.ALL_NAMESPACES);
                        }
                    });
                    return null;
                };

            }
        }

    }


}

