////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.Cardinality;


/**
 * An instruction that creates an element node. There are two subtypes, FixedElement
 * for use where the name is known statically, and Element where it is computed
 * dynamically. To allow use in both XSLT and XQuery, the class acts both as an
 * Instruction and as an Expression.
 */

public abstract class ElementCreator extends ParentNodeConstructor {

    /**
     * ElementCreationDetails is a package of information and callbacks designed
     * to parameterize the element construction process, to allow maximum use of
     * common code for direct interpreted evaluation, elaborated evaluation,
     * streamed evaluation, etc; supporting the variations that exist between
     * computed element constructors, fixed element constructors, and shallow copy.
     */

    public static abstract class ElementCreationDetails {

        /**
         * Get the name of the element node to be constructed
         * @param context evaluation context
         * @return the element name
         * @throws XPathException if computation of the element name fails
         */

        public abstract NodeName getNodeName(XPathContext context) throws XPathException;

        /**
         * Get the base URI to be assigned to the new element node
         * @param context evaluation context
         * @return the base URI
         * @throws XPathException if computation of the base URI fails
         */

        public abstract String getSystemId(XPathContext context) throws XPathException;

    }


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
        return NodeKindType.ELEMENT;
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
    protected int computeSpecialProperties() {
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
            setValidationAction(null, Validation.PRESERVE, null);
        }
    }

    /**
     * Check statically whether the content of the element creates attributes or namespaces
     * after creating any child nodes
     *
     * @throws XPathException if the content is found to be incorrect
     */

    @Override
    protected void checkContentSequence() throws XPathException {
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
                        UnicodeString value = ((StringLiteral) ((ValueOf) component).getSelect()).getUnicodeString();
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
                    warning(
                            "Creating an attribute here will fail if previous instructions create any children",
                            SaxonErrorCode.SXWN9030);
                } else if ((foundChild || foundPossibleChild) && possibleNodeKinds == UType.NAMESPACE) {
                    warning(
                            "Creating a namespace node here will fail if previous instructions create any children",
                            SaxonErrorCode.SXWN9030);
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

    //public abstract NodeName getElementName(XPathContext context, /*@Nullable*/ NodeInfo copiedNode) throws XPathException;

    /**
     * Get the base URI for the element being constructed
     *
     * @param context    the XPath dynamic evaluation context
     * @param copiedNode the node being copied (for xsl:copy), otherwise null
     * @return the base URI of the constructed element
     */

    //public abstract String getNewBaseURI(XPathContext context, NodeInfo copiedNode);

    /**
     * Callback to output namespace bindings for the new element. This method is responsible
     * for ensuring that a namespace binding is always generated for the namespace of the element
     * name itself.
     *
     * @param receiver   the Outputter where the namespace bindings are to be written
     * @param nodeName   the name of the element being created
     * @param details    details supplied to the process() call
     * @throws XPathException if a dynamic error occurs
     */

    public abstract void outputNamespaceNodes(
            Outputter receiver, NodeName nodeName, /*@Nullable*/ ElementCreationDetails details)
            throws XPathException;

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered. For instructions this is the process() method.
     */

    @Override
    public int getImplementationMethod() {
        return Expression.PROCESS_METHOD;
    }


    void exportValidationAndType(ExpressionPresenter out) {
        if (getValidationAction() != Validation.SKIP && getValidationAction() != Validation.BY_TYPE) {
            out.emitAttribute("validation", Validation.describe(getValidationAction()));
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
        if (preservingTypes) {
            flags += "V";
        }
        return flags;
    }

    public void setInheritanceFlags(String flags) {
        inheritNamespacesFromParent = !flags.contains("P");
        bequeathNamespacesToChildren = !flags.contains("C");
        if (flags.contains("V")) {
            preservingTypes = true;
        }
    }

    public ElementCreationDetails makeElementCreationDetails() {
        throw new UnsupportedOperationException();
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

