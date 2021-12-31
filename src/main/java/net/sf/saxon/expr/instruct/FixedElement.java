////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.CombinedNodeTest;
import net.sf.saxon.pattern.ContentTypeTest;
import net.sf.saxon.pattern.NameTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.*;

import java.util.function.BiConsumer;


/**
 * An instruction that creates an element node whose name is known statically.
 * Used for literal results elements in XSLT, for direct element constructors
 * in XQuery, and for xsl:element in cases where the name and namespace are
 * known statically.
 */

public class FixedElement extends ElementCreator {

    private NodeName elementName;
    /*@NotNull*/ protected NamespaceMap namespaceBindings;
    private ItemType itemType;

    /**
     * Create an instruction that creates a new element node
     *
     * @param elementName                 Represents the name of the element node
     * @param namespaceBindings           List of namespaces to be added to the element node.
     *                                    Supply an empty array if none are required.
     * @param inheritNamespacesToChildren true if the children of this element inherit its namespaces
     * @param inheritNamespacesFromParent true if this element inherits namespaces from its parent
     * @param schemaType                  Type annotation for the new element node
     * @param validation                  Validation mode to be applied, for example STRICT, LAX, SKIP
     */
    public FixedElement(NodeName elementName,
                        NamespaceMap namespaceBindings,
                        boolean inheritNamespacesToChildren,
                        boolean inheritNamespacesFromParent,
                        SchemaType schemaType,
                        int validation) {
        this.elementName = elementName;
        this.namespaceBindings = namespaceBindings;
        this.bequeathNamespacesToChildren = inheritNamespacesToChildren;
        this.inheritNamespacesFromParent = inheritNamespacesFromParent;
        setValidationAction(validation, schemaType);
        preservingTypes = schemaType == null && validation == Validation.PRESERVE;
    }
    
    @Override
    public Iterable<Operand> operands() {
        return contentOp;
    }

    /**
     * Simplify an expression. This performs any context-independent rewriting
     *
     *
     *
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is discovered during expression rewriting
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        preservingTypes |= !getPackageData().isSchemaAware();
        return super.simplify();
    }

    /**
     * Check statically whether the content of the element creates attributes or namespaces
     * after creating any child nodes
     *
     * @param env the static context
     * @throws net.sf.saxon.trans.XPathException if the content sequence is invalid
     *
     */

    @Override
    protected void checkContentSequence(StaticContext env) throws XPathException {
        super.checkContentSequence(env);
        itemType = computeFixedElementItemType(this, env,
                getValidationAction(), getSchemaType(), elementName, getContentExpression());
    }

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        Expression e = super.optimize(visitor, contextItemType);
        if (e != this) {
            return e;
        }
        // Remove any unnecessary creation of namespace nodes by child literal result elements.
        // Specifically, if this instruction creates a namespace node, then a child literal result element
        // doesn't need to create the same namespace if all the following conditions are true:
        // (a) the child element is in the same namespace as its parent, and
        // (b) this element doesn't specify xsl:inherit-namespaces="no"
        // (c) the child element is incapable of creating attributes in a non-null namespace

        if (!bequeathNamespacesToChildren) {
            return this;
        }
//        if (!namespaceBindings.isEmpty()) {
//            if (getContentExpression() instanceof FixedElement) {
//                FixedElement fixedContent = (FixedElement) getContentExpression();
//                if (elementName.isInSameNamespace(fixedContent.getElementName())) {
//                    fixedContent.removeRedundantNamespaces(visitor, namespaceBindings);
//                }
//                return this;
//            }
//            if (getContentExpression() instanceof Block) {
//                for (Operand o : getContentExpression().operands()) {
//                    Expression exp = o.getChildExpression();
//                    if (exp instanceof FixedElement &&
//                            elementName.isInSameNamespace(((FixedElement) exp).getElementName())) {
//                        ((FixedElement) exp).removeRedundantNamespaces(visitor, namespaceBindings);
//                    }
//                }
//            }
//        }
        return this;
    }

    /**
     * Remove namespaces that are not required for this element because they are output on
     * the parent element
     *
     * @param visitor          the expression visitor
     * @param parentNamespaces the namespaces that are output by the parent element
     */

    private void removeRedundantNamespaces(ExpressionVisitor visitor, NamespaceMap parentNamespaces) {
        // It's only safe to remove any namespaces if the element is incapable of creating any attribute nodes
        // in a non-null namespace
        // This is because namespaces created on this element take precedence over namespaces created by namespace
        // fixup based on the prefix used in the attribute name (see atrs24)
        if (namespaceBindings.isEmpty()) {
            return;
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        ItemType contentType = getContentExpression().getItemType();
        boolean ok = th.relationship(contentType, NodeKindTest.ATTRIBUTE) == Affinity.DISJOINT;
        if (!ok) {
            // if the content might include attributes, discount any that are known to be in the null namespace
            if (getContentExpression() instanceof Block) {
                ok = true;
                for (Operand o : getContentExpression().operands()) {
                    Expression exp = o.getChildExpression();
                    if (exp instanceof FixedAttribute) {
                        if (!((FixedAttribute)exp).getAttributeName().hasURI("")) {
                            ok = false;
                            break;
                        }
                    } else {
                        ItemType childType = exp.getItemType();
                        if (th.relationship(childType, NodeKindTest.ATTRIBUTE) != Affinity.DISJOINT) {
                            ok = false;
                            break;
                        }
                    }
                }
            }
        }
        if (ok) {
            NamespaceMap reduced = namespaceBindings;
            // TODO: implement difference() operation on NamespaceMap
            for (NamespaceBinding childNamespace : namespaceBindings) {
                if (childNamespace.getURI().equals(parentNamespaces.getURI(childNamespace.getPrefix()))) {
                    reduced = reduced.remove(childNamespace.getPrefix());
                }
            }
            namespaceBindings = reduced;
        }
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *                   that is used to update the bindings held in any
     *                   local variable references that are copied.
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        FixedElement fe = new FixedElement(elementName, namespaceBindings, bequeathNamespacesToChildren,
                                           inheritNamespacesFromParent, getSchemaType(), getValidationAction());
        fe.setContentExpression(getContentExpression().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, fe);
        return fe;
    }

    /**
     * Determine the item type of an element being constructed
     *
     * @param instr       the FixedElement instruction
     * @param env         the static context
     * @param validation  the schema validation mode
     * @param schemaType  the schema type for validation
     * @param elementName the name of the element
     * @param content     the expression that computes the content of the element
     * @return the item type
     * @throws XPathException if a static error is detected
     */

    private ItemType computeFixedElementItemType(FixedElement instr, StaticContext env,
                                                 int validation, SchemaType schemaType,
                                                 NodeName elementName, Expression content) throws XPathException {
        final Configuration config = env.getConfiguration();
        ItemType itemType;
        int fp = elementName.obtainFingerprint(config.getNamePool());   // Bug #2563 - namespaced alias in unoptimized export
        if (schemaType == null) {
            if (validation == Validation.STRICT) {
                SchemaDeclaration decl = config.getElementDeclaration(fp);
                if (decl == null) {
                    XPathException err = new XPathException("There is no global element declaration for " +
                            elementName.getStructuredQName().getEQName() +
                            ", so strict validation will fail");
                    err.setErrorCode(instr.isXSLT() ? "XTTE1512" : "XQDY0084");
                    err.setIsTypeError(true); // technically this is a type error in XSLT but not in XQuery
                    err.setLocation(instr.getLocation());
                    throw err;
                }
                if (decl.isAbstract()) {
                    XPathException err = new XPathException("The element declaration for " +
                            elementName.getStructuredQName().getEQName() +
                            " is abstract, so strict validation will fail");
                    err.setErrorCode(instr.isXSLT() ? "XTTE1512" : "XQDY0027");
                    err.setIsTypeError(true); // technically this is a type error in XSLT but not in XQuery
                    err.setLocation(instr.getLocation());
                    throw err;
                }
                SchemaType declaredType = decl.getType();
                SchemaType xsiType = instr.getXSIType(env);
                if (xsiType != null) {
                    schemaType = xsiType;
                } else {
                    schemaType = declaredType;
                }

                itemType = new CombinedNodeTest(
                    new NameTest(Type.ELEMENT, fp, env.getConfiguration().getNamePool()),
                    Token.INTERSECT,
                    new ContentTypeTest(Type.ELEMENT, schemaType, config, false));
                if (xsiType != null || !decl.hasTypeAlternatives()) {
                    instr.getValidationOptions().setTopLevelType(schemaType);
                    try {
                        schemaType.analyzeContentExpression(content, Type.ELEMENT);
                    } catch (XPathException e) {
                        e.setErrorCode(instr.isXSLT() ? "XTTE1510" : "XQDY0027");
                        e.setLocation(instr.getLocation());
                        throw e;
                    }
                    if (xsiType != null) {
                        try {
                            config.checkTypeDerivationIsOK(xsiType, declaredType, 0);
                        } catch (SchemaException e) {
                            ValidationFailure ve = new ValidationFailure("The specified xsi:type " + xsiType.getDescription() +
                                " is not validly derived from the required type " + declaredType.getDescription());
                            ve.setConstraintReference(1, "cvc-elt", "4.3");
                            ve.setErrorCode(instr.isXSLT() ? "XTTE1515" : "XQDY0027");
                            ve.setLocator(instr.getLocation());
                            throw ve.makeException();
                        }
                    }
                }
            } else if (validation == Validation.LAX) {
                SchemaDeclaration decl = config.getElementDeclaration(fp);
                if (decl == null) {
                    env.issueWarning("There is no global element declaration for " +
                            elementName.getDisplayName(), instr.getLocation());
                    itemType = new NameTest(Type.ELEMENT, fp, config.getNamePool());
                } else {
                    schemaType = decl.getType();
                    instr.getValidationOptions().setTopLevelType(schemaType);
                    itemType = new CombinedNodeTest(
                            new NameTest(Type.ELEMENT, fp, config.getNamePool()),
                            Token.INTERSECT,
                            new ContentTypeTest(Type.ELEMENT, instr.getSchemaType(), config, false));
                    try {
                        schemaType.analyzeContentExpression(content, Type.ELEMENT);
                    } catch (XPathException e) {
                        e.setErrorCode(instr.isXSLT() ? "XTTE1515" : "XQDY0027");
                        e.setLocation(instr.getLocation());
                        throw e;
                    }
                }
            } else if (validation == Validation.PRESERVE) {
                // we know the result will be an element of type xs:anyType
                itemType = new CombinedNodeTest(
                        new NameTest(Type.ELEMENT, fp, config.getNamePool()),
                        Token.INTERSECT,
                        new ContentTypeTest(Type.ELEMENT, AnyType.getInstance(), config, false));
            } else {
                // we know the result will be an untyped element
                itemType = new CombinedNodeTest(
                        new NameTest(Type.ELEMENT, fp, config.getNamePool()),
                        Token.INTERSECT,
                        new ContentTypeTest(Type.ELEMENT, Untyped.getInstance(), config, false));
            }
        } else {
            itemType = new CombinedNodeTest(
                    new NameTest(Type.ELEMENT, fp, config.getNamePool()),
                    Token.INTERSECT,
                    new ContentTypeTest(Type.ELEMENT, schemaType, config, false)
            );
            try {
                schemaType.analyzeContentExpression(content, Type.ELEMENT);
            } catch (XPathException e) {
                e.setErrorCode(instr.isXSLT() ? "XTTE1540" : "XQDY0027");
                e.setLocation(instr.getLocation());
                throw e;
            }
        }
        return itemType;
    }

    /**
     * Get the type of the item returned by this instruction
     *
     * @return the item type
     */
    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        if (itemType == null) {
            return super.getItemType();
        }
        return itemType;
    }

    /**
     * Callback from the superclass ElementCreator to get the nameCode
     * for the element name
     *
     * @param context    The evaluation context (not used)
     * @param copiedNode For the benefit of the xsl:copy instruction, the node to be copied
     * @return the name code for the element name
     */

    @Override
    public NodeName getElementName(XPathContext context, NodeInfo copiedNode) {
        return elementName;
    }

    public NodeName getElementName() {
        return elementName;
    }


    /**
     * Get the properties of this object to be included in trace messages, by supplying
     * the property values to a supplied consumer function
     *
     * @param consumer the function to which the properties should be supplied, as (property name,
     *                 value) pairs.
     */
    @Override
    public void gatherProperties(BiConsumer<String, Object> consumer) {
        consumer.accept("name", getElementName());
    }



    @Override
    public String getNewBaseURI(XPathContext context, NodeInfo copiedNode) {
        return getStaticBaseURIString();
    }

    /**
     * Determine whether the element constructor creates a fixed xsi:type attribute, and if so, return the
     * relevant type.
     *
     * @param env the static context
     * @return the type denoted by the constructor's xsi:type attribute if there is one.
     *         Return null if there is no xsi:type attribute, or if the value of the xsi:type
     *         attribute is a type that is not statically known (this is allowed)
     * @throws XPathException if there is an xsi:type attribute and its value is not a QName.
     */

    private SchemaType getXSIType(StaticContext env) throws XPathException {
        if (getContentExpression() instanceof FixedAttribute) {
            return testForXSIType((FixedAttribute) getContentExpression(), env);
        } else if (getContentExpression() instanceof Block) {
            for (Operand o : getContentExpression().operands()) {
                Expression exp = o.getChildExpression();
                if (exp instanceof FixedAttribute) {
                    SchemaType type = testForXSIType((FixedAttribute) exp, env);
                    if (type != null) {
                        return type;
                    }
                }
            }
            return null;
        } else {
            return null;
        }
    }

    /**
     * Test whether a FixedAttribute child instruction of this FixedElement is creating an xsi:type
     * attribute whose value is known statically; if this is the case, then return the schema type
     * named in this attribute
     *
     * @param fat The FixedAttribute instruction
     * @param env The XPath static context
     * @return the schema type if this is an xsi:type attribute instruction whose value is known at compile time;
     *         otherwise null
     * @throws XPathException if an error occurs
     */

    private SchemaType testForXSIType(FixedAttribute fat, StaticContext env) throws XPathException {
        int att = fat.getAttributeFingerprint();
        if (att == StandardNames.XSI_TYPE) {
            Expression attValue = fat.getSelect();
            if (attValue instanceof StringLiteral) {
                try {
                    String[] parts = NameChecker.getQNameParts(
                            ((StringLiteral) attValue).getStringValue());
                    // The only namespace bindings we can trust are those declared on this element
                    // We could also trust those on enclosing LREs in the same function/template,
                    // but it's not a big win to go looking for them.
                    String uri = namespaceBindings.getURI(parts[0]);
                    if (uri == null) {
                        return null;
                    } else {
                        return env.getConfiguration().getSchemaType(new StructuredQName("", uri, parts[1]));
                    }
                } catch (QNameException e) {
                    throw new XPathException(e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    @Override
    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        if (parentType instanceof SimpleType) {
            XPathException err = new XPathException("Element " + elementName.getDisplayName() +
                    " is not permitted here: the containing element is of simple type " + parentType.getDescription());
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            throw err;
        } else if (((ComplexType) parentType).isSimpleContent()) {
            XPathException err = new XPathException("Element " + elementName.getDisplayName() +
                    " is not permitted here: the containing element has a complex type with simple content");
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            throw err;
        }

        // Check that a sequence consisting of this element alone is valid against the content model
        if (whole) {
            Expression parent = getParentExpression();
            Block block = new Block(new Expression[]{this});
            parentType.analyzeContentExpression(block, Type.ELEMENT);
            setParentExpression(parent);
        }

        SchemaType type;
        try {
            int fp = elementName.obtainFingerprint(getConfiguration().getNamePool());
            type = ((ComplexType) parentType).getElementParticleType(fp, true);
        } catch (MissingComponentException e) {
            throw new XPathException(e);
        }
        if (type == null) {
            XPathException err = new XPathException("Element " + elementName.getDisplayName() +
                    " is not permitted in the content model of the complex type " +
                    parentType.getDescription());
            err.setIsTypeError(true);
            err.setLocation(getLocation());
            err.setErrorCode(isXSLT() ? "XTTE1510" : "XQDY0027");
            throw err;
        }
        if (type instanceof AnyType) {
            return;
        }

        try {
            getContentExpression().checkPermittedContents(type, true);
        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            throw e;
        }
    }

    /**
     * Callback from the superclass ElementCreator to output the namespace nodes
     * @param out        The receiver to handle the output
     * @param nodeName   the name of this element
     * @param copiedNode in the case of xsl:copy, the node being copied
     */

    @Override
    public void outputNamespaceNodes(Outputter out, NodeName nodeName, NodeInfo copiedNode)
            throws XPathException {
        for (NamespaceBinding ns : namespaceBindings) {
            out.namespace(ns.getPrefix(), ns.getURI(), ReceiverOption.NONE);
        }
    }

    /**
     * Callback to get a list of the intrinsic namespaces that need to be generated for the element.
     * @return the namespace bindings that must be generated for the new element node
     */

    public NamespaceMap getActiveNamespaces() {
        return namespaceBindings;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("elem", this);
        out.emitAttribute("name", elementName.getDisplayName());
        out.emitAttribute("nsuri", elementName.getURI());
        String flags = getInheritanceFlags();
        if (!elementName.getURI().isEmpty() && elementName.getPrefix().isEmpty()) {
            flags += "d";  // "d" to indicate default namespace
        }
        if (isLocal()) {
            flags += "l";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.C256);
        if (!namespaceBindings.isEmpty()) {
            for (NamespaceBinding ns : namespaceBindings) {
                String prefix = ns.getPrefix();
                if (!prefix.equals("xml")) {
                    fsb.append(prefix.isEmpty() ? "#" : prefix);
                    if (!ns.getURI().equals(getRetainedStaticContext().getURIForPrefix(prefix, true))) {
                        fsb.cat('=');
                        fsb.append(ns.getURI());
                    }
                    fsb.cat(' ');
                }
            }
            fsb.setLength(fsb.length() - 1);
            out.emitAttribute("namespaces", fsb.toString());
        }
        exportValidationAndType(out);
        getContentExpression().export(out);
        out.endElement();
    }

    @Override
    public String toString() {
        return "<" + elementName.getStructuredQName().getDisplayName() + " {" + getContentExpression().toString() + "}/>";
    }

    @Override
    public String toShortString() {
        return "<" + elementName.getStructuredQName().getDisplayName() + " {" + getContentExpression().toShortString() + "}/>";
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "element";
    }
}

