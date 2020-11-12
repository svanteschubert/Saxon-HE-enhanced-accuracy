////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.*;
import net.sf.saxon.expr.*;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;


/**
 * An instruction that creates an element node. There are two subtypes, FixedElement
 * for use where the name is known statically, and Element where it is computed
 * dynamically. To allow use in both XSLT and XQuery, the class acts both as an
 * Instruction and as an Expression.
 */

public abstract class ElementCreator extends ParentNodeConstructor {


    /**
     * The bequeathNamespacesToChildren flag indicates that the namespace nodes on the element created by this instruction
     * are to be inherited (copied) by the children of this element. That is, if this flag is false, the child
     * elements must carry a namespace undeclaration for all the namespaces on the parent, unless they are
     * redeclared in some way. This flag is set in XSLT based on the value of the [xsl:]inherit-namespaces
     * attribute on the element construction instruction, and in XQuery based on the value of "copy namespaces
     * inherit | no-inherit" in the query prolog.
     */

    boolean bequeathNamespacesToChildren = true;

    /**
     * The inheritNamespacesFromParent flag indicates that this element should inherit the namespaces of its
     * parent element in the result tree. That is, if this flag is false, this element must carry a namespace
     * undeclaration for all the namespaces on its parent, unless they are redeclared in some way. In XSLT this
     * flag is always true, but in XQuery it is set to false for a child element constructed using
     * {@code <parent><child/></parent>} as distinct from {@code <parent>{<child/>}parent} -- in the former case,
     * the child element is not copied and therefore namespace inheritance does not apply.
     */

    boolean inheritNamespacesFromParent = true;

    /**
     * Construct an ElementCreator. Exists for the benefit of subclasses.
     */

    public ElementCreator() {
    }

    /**
     * Get the item type of the value returned by this instruction
     *
     * @return the item type
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return NodeKindTest.ELEMENT;
    }

    @Override
    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Say whether this element causes its children to inherit namespaces
     *
     * @param inherit true if namespaces are to be inherited by the children
     */

    public void setBequeathNamespacesToChildren(boolean inherit) {
        bequeathNamespacesToChildren = inherit;
    }

    /**
     * Ask whether the inherit namespaces flag is set
     *
     * @return true if namespaces constructed on this parent element are to be inherited by its children
     */

    public boolean isBequeathNamespacesToChildren() {
        return bequeathNamespacesToChildren;
    }

    /**
     * Say whether this element causes inherits namespaces from its parent. True except in XQuery where
     * one direct element constructor forms the immediate content of another (see W3C bug 22334)
     *
     * @param inherit true if namespaces are to be inherited from the parent
     */

    public void setInheritNamespacesFromParent(boolean inherit) {
        inheritNamespacesFromParent = inherit;
    }

    /**
     * Ask whether this element inherits namespaces from its parent. True except in XQuery where
     * one direct element constructor forms the immediate content of another (see W3C bug 22334)
     *
     * @return true if this child element inherits namespaces from its parent element
     */

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isInheritNamespacesFromParent() {
        return inheritNamespacesFromParent;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @return a set of flags indicating static properties of this expression
     */

    @Override
    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties() |
                StaticProperty.SINGLE_DOCUMENT_NODESET;
        if (getValidationAction() == Validation.STRIP) {
            p |= StaticProperty.ALL_NODES_UNTYPED;
        }
        return p;
    }

    /**
     * Suppress validation on contained element constructors, on the grounds that the parent element
     * is already performing validation. The default implementation does nothing.
     */

    @Override
    public void suppressValidation(int parentValidationMode) {
        if (getValidationAction() == parentValidationMode && getSchemaType() == null) {
            // TODO: is this safe? e.g. if the child has validation=strict but matches a skip wildcard in the parent
            setValidationAction(Validation.PRESERVE, null);
        }
    }

    /**
     * Check statically whether the content of the element creates attributes or namespaces
     * after creating any child nodes
     *
     * @param env the static context
     * @throws XPathException if the content is found to be incorrect
     */

    @Override
    protected void checkContentSequence(StaticContext env) throws XPathException {
        Operand[] components;
        if (getContentExpression() instanceof Block) {
            components = ((Block) getContentExpression()).getOperanda();
        } else {
            components = new Operand[]{contentOp};
        }

        boolean foundChild = false;
        boolean foundPossibleChild = false;
        for (Operand o : components) {
            Expression component = o.getChildExpression();
            ItemType it = component.getItemType();
            if (it.isAtomicType()) {
                foundChild = true;
            } else if (it instanceof FunctionItemType && !(it instanceof ArrayItemType)) {
                String which = it instanceof MapType ? "map" : "function";
                XPathException de = new XPathException(
                        "Cannot add a " + which + " as a child of a constructed element");
                de.setErrorCode(isXSLT() ? "XTDE0450" : "XQTY0105");
                de.setLocator(component.getLocation());
                de.setIsTypeError(true);
                throw de;
            } else if (it instanceof NodeTest) {
                boolean maybeEmpty = Cardinality.allowsZero(component.getCardinality());
                UType possibleNodeKinds = it.getUType();
                if (possibleNodeKinds.overlaps(UType.TEXT)) {
                    // the text node might turn out to be zero-length. If that's a possibility,
                    // then we only issue a warning. Also, we need to completely ignore a known
                    // zero-length text node, which is included to prevent space-separation
                    // in an XQuery construct like <a>{@x}{@y}</b>
                    if (component instanceof ValueOf &&
                            ((ValueOf) component).getSelect() instanceof StringLiteral) {
                        String value = ((StringLiteral) ((ValueOf) component).getSelect()).getStringValue();
                        if (value.isEmpty()) {
                            // continue;  // not an error
                        } else {
                            foundChild = true;
                        }
                    } else {
                        foundPossibleChild = true;
                    }
                } else if (!possibleNodeKinds.overlaps(UType.CHILD_NODE_KINDS)) {
                    if (maybeEmpty) {
                        foundPossibleChild = true;
                    } else {
                        foundChild = true;
                    }
                } else if (foundChild && possibleNodeKinds == UType.ATTRIBUTE && !maybeEmpty) {
                    XPathException de = new XPathException(
                            "Cannot create an attribute node after creating a child of the containing element");
                    de.setErrorCode(isXSLT() ? "XTDE0410" : "XQTY0024");
                    de.setLocator(component.getLocation());
                    throw de;
                } else if (foundChild && possibleNodeKinds == UType.NAMESPACE && !maybeEmpty) {
                    XPathException de = new XPathException(
                            "Cannot create a namespace node after creating a child of the containing element");
                    de.setErrorCode(isXSLT() ? "XTDE0410" : "XQTY0024");
                    de.setLocator(component.getLocation());
                    throw de;
                } else if ((foundChild || foundPossibleChild) && possibleNodeKinds == UType.ATTRIBUTE) {
                    env.issueWarning(
                            "Creating an attribute here will fail if previous instructions create any children",
                            component.getLocation());
                } else if ((foundChild || foundPossibleChild) && possibleNodeKinds == UType.NAMESPACE) {
                    env.issueWarning(
                            "Creating a namespace node here will fail if previous instructions create any children",
                            component.getLocation());
                }
            }

        }
    }

    /**
     * Determine (at run-time) the name code of the element being constructed
     *
     * @param context    the XPath dynamic evaluation context
     * @param copiedNode for the benefit of xsl:copy, the node being copied; otherwise null
     * @return the integer name code representing the element name
     * @throws XPathException if a failure occurs
     */

    public abstract NodeName getElementName(XPathContext context, /*@Nullable*/ NodeInfo copiedNode) throws XPathException;

    /**
     * Get the base URI for the element being constructed
     *
     * @param context    the XPath dynamic evaluation context
     * @param copiedNode the node being copied (for xsl:copy), otherwise null
     * @return the base URI of the constructed element
     */

    public abstract String getNewBaseURI(XPathContext context, NodeInfo copiedNode);

    /**
     * Callback to output namespace bindings for the new element. This method is responsible
     * for ensuring that a namespace binding is always generated for the namespace of the element
     * name itself.
     *
     * @param receiver   the Outputter where the namespace bindings are to be written
     * @param nodeName   the name of the element being created
     * @param copiedNode the node being copied (for xsl:copy) or null otherwise
     * @throws XPathException if a dynamic error occurs
     */

    public abstract void outputNamespaceNodes(
            Outputter receiver, NodeName nodeName, /*@Nullable*/ NodeInfo copiedNode)
            throws XPathException;

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered. For instructions this is the process() method.
     */

    @Override
    public int getImplementationMethod() {
        return Expression.PROCESS_METHOD;
    }

    /**
     * Evaluate the instruction to produce a new element node. This method is typically used when there is
     * a parent element or document in a result tree, to which the new element is added.
     *
     *
     * @param output the destination for the result
     * @param context XPath dynamic evaluation context
     * @return null (this instruction never returns a tail call)
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context)
            throws XPathException {
        return processLeavingTail(output, context, null);
    }

    /**
     * Evaluate the instruction to produce a new element node. This method is typically used when there is
     * a parent element or document in a result tree, to which the new element is added.
     *
     *
     * @param out        The destination for the result
     * @param context    XPath dynamic evaluation context
     * @param copiedNode null except in the case of xsl:copy, when it is the node being copied; otherwise null
     * @return null (this instruction never returns a tail call)
     * @throws XPathException if a dynamic error occurs
     */
    public final TailCall processLeavingTail(Outputter out, XPathContext context, /*@Nullable*/ NodeInfo copiedNode)
            throws XPathException {

        try {

            NodeName elemName = getElementName(context, copiedNode);
            SchemaType typeCode = getValidationAction() == Validation.PRESERVE ? AnyType.getInstance() : Untyped.getInstance();

            Receiver elemOut = out;
            if (!preservingTypes) {
                ParseOptions options = new ParseOptions(getValidationOptions());
                options.setTopLevelElement(elemName.getStructuredQName());
                context.getConfiguration().prepareValidationReporting(context, options);
                Receiver validator = context.getConfiguration().getElementValidator(elemOut, options, getLocation());

                if (validator != elemOut) {
                    out = new ComplexContentOutputter(validator);
                }
                //elemOut = validator;
            }

            if (out.getSystemId() == null) {
                out.setSystemId(getNewBaseURI(context, copiedNode));
            }
            int properties = ReceiverOption.NONE;
            if (!bequeathNamespacesToChildren) {
                properties |= ReceiverOption.DISINHERIT_NAMESPACES;
            }
            if (!inheritNamespacesFromParent) {
                properties |= ReceiverOption.REFUSE_NAMESPACES;
            }
            properties |= ReceiverOption.ALL_NAMESPACES;

            out.startElement(elemName, typeCode, getLocation(), properties);

            // output the required namespace nodes via a callback

            outputNamespaceNodes(out, elemName, copiedNode);

            // process subordinate instructions to generate attributes and content
            getContentExpression().process(out, context);

            // output the element end tag (which will fail if validation fails)
            out.endElement();

            return null;

        } catch (XPathException e) {
            e.maybeSetLocation(getLocation());
            e.maybeSetContext(context);
            throw e;
        }
    }


    void exportValidationAndType(ExpressionPresenter out) {
        if (getValidationAction() != Validation.SKIP && getValidationAction() != Validation.BY_TYPE) {
            out.emitAttribute("validation", Validation.toString(getValidationAction()));
        }
        if (getValidationAction() == Validation.BY_TYPE) {
            SchemaType type = getSchemaType();
            if (type != null) {
                out.emitAttribute("type", type.getStructuredQName());
            }
        }
    }

    String getInheritanceFlags() {
        String flags = "";
        if (!inheritNamespacesFromParent) {
            flags += "P";
        }
        if (!bequeathNamespacesToChildren) {
            flags += "C";
        }
        return flags;
    }

    public void setInheritanceFlags(String flags) {
        inheritNamespacesFromParent = !flags.contains("P");
        bequeathNamespacesToChildren = !flags.contains("C");
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "ElementCreator";
    }
}

