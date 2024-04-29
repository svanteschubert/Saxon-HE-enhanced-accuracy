////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StandardURIChecker;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.*;

import java.util.function.Supplier;

/**
 * A namespace constructor instruction. (xsl:namespace in XSLT 2.0, or namespace{}{} in XQuery 1.1)
 */

public class NamespaceConstructor extends SimpleNodeConstructor {

    private final Operand nameOp;

    /**
     * Create an xsl:namespace instruction for dynamic construction of namespace nodes
     *
     * @param name the expression to evaluate the name of the node (that is, the prefix)
     */

    public NamespaceConstructor(Expression name) {
        nameOp = new Operand(this, name, OperandRole.SINGLE_ATOMIC);
    }

    public Expression getNameExp() {
        return nameOp.getChildExpression();
    }

    public void setNameExp(Expression nameExp) {
        nameOp.setChildExpression(nameExp);
    }

    @Override
    public Iterable<Operand> operands() {
        return operandList(selectOp, nameOp);
    }

    /**
     * Set the name of this instruction for diagnostic and tracing purposes
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_NAMESPACE;
    }

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return NodeKindTest.NAMESPACE;
    }

    @Override
    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    @Override
    public void localTypeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        StaticContext env = visitor.getStaticContext();
        nameOp.typeCheck(visitor, contextItemType);

        Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "namespace/name", 0);
        // See bug 2110. XQuery does not use the function conversion rules here, and disallows xs:anyURI.
        // In XSLT the name is an AVT so we automatically get a string; in XQuery we'll use the standard
        // mechanism to get an atomic value, and then check the type "by hand" at run time.
        setNameExp(env.getConfiguration().getTypeChecker(false).staticTypeCheck(
                getNameExp(), SequenceType.OPTIONAL_ATOMIC, role, visitor));
        adoptChildExpression(getNameExp());

        // Do early checking of name if known statically

        if (getNameExp() instanceof Literal) {
            evaluatePrefix(env.makeEarlyEvaluationContext());
        }

    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings the rebinding map
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        NamespaceConstructor exp = new NamespaceConstructor(getNameExp().copy(rebindings));
        exp.setSelect(getSelect().copy(rebindings));
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }


    @Override
    public NodeName evaluateNodeName(XPathContext context) throws XPathException {
        String prefix = evaluatePrefix(context);
        return new NoNamespaceName(prefix);
    }

    private String evaluatePrefix(XPathContext context) throws XPathException {
        AtomicValue value = (AtomicValue) getNameExp().evaluateItem(context);
        if (value == null) {
            return "";
        }
        if (!(value instanceof StringValue) || value instanceof AnyURIValue) {
            // Can only happen in XQuery
            XPathException err = new XPathException(
                "Namespace prefix is not an xs:string or xs:untypedAtomic", "XPTY0004", getLocation());
            err.setIsTypeError(true);
            throw dynamicError(getLocation(), err, context);
        }
        String prefix = Whitespace.trim(value.getStringValue());
        return checkPrefix(prefix, context);
    }

    public String checkPrefix(String prefix, XPathContext context) throws XPathException {
        prefix = Whitespace.trim(prefix);
        if (!(prefix.isEmpty() || NameChecker.isValidNCName(prefix))) {
            String errorCode = isXSLT() ? "XTDE0920" : "XQDY0074";
            XPathException err = new XPathException("Namespace prefix is invalid: " + prefix, errorCode, getLocation());
            throw dynamicError(getLocation(), err, context);
        }

        if (prefix.equals("xmlns")) {
            String errorCode = isXSLT() ? "XTDE0920" : "XQDY0101";
            XPathException err = new XPathException("Namespace prefix 'xmlns' is not allowed", errorCode, getLocation());
            throw dynamicError(getLocation(), err, context);
        }
        return prefix;
    }


    @Override
    public void processValue(UnicodeString value, Outputter output, XPathContext context) throws XPathException {
        String prefix = evaluatePrefix(context);
        String uri = value.toString();
        checkPrefixAndUri(prefix, uri, context);

        output.namespace(prefix, NamespaceUri.of(uri), ReceiverOption.REJECT_DUPLICATES);
    }


    /**
     * Evaluate as an expression. We rely on the fact that when these instructions
     * are generated by XQuery, there will always be a valueExpression to evaluate
     * the content
     */

    @Override
    public NodeInfo evaluateItem(XPathContext context) throws XPathException {
        NodeInfo node = (NodeInfo) super.evaluateItem(context);
        assert node != null;
        String prefix = node.getLocalPart();
        String uri = node.getStringValue();
        checkPrefixAndUri(prefix, uri, context);
        return node;
    }

    private void checkPrefixAndUri(String prefix, String uri, XPathContext context) throws XPathException {
        if (prefix.equals("xml") != uri.equals(NamespaceConstant.XML)) {
            String errorCode = isXSLT() ? "XTDE0925" : "XQDY0101";
            XPathException err = new XPathException("Namespace prefix 'xml' and namespace uri " + NamespaceConstant.XML +
                    " must only be used together", errorCode, getLocation());
            throw dynamicError(getLocation(), err, context);
        }

        if (uri.isEmpty()) {
            String errorCode = isXSLT() ? "XTDE0930" : "XQDY0101";
            XPathException err = new XPathException("Namespace URI is an empty string", errorCode, getLocation());
            throw dynamicError(getLocation(), err, context);
        }

        if (uri.equals(NamespaceConstant.XMLNS)) {
            String errorCode = isXSLT() ? "XTDE0905" : "XQDY0101";
            XPathException err = new XPathException("A namespace node cannot have the reserved namespace " +
                    NamespaceConstant.XMLNS, errorCode, getLocation());
            throw dynamicError(getLocation(), err, context);
        }

        if (context.getConfiguration().getXsdVersion() == Configuration.XSD10 &&   //W3C bug 30180
                !StandardURIChecker.getInstance().isValidURI(uri)) {
            XPathException de = new XPathException(
                "The string value of the constructed namespace node must be a valid URI", "XTDE0905", getLocation());
            throw dynamicError(getLocation(), de, context);
        }
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("namespace", this);
        String flags = "";
        if (isLocal()) {
            flags += "l";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        out.setChildRole("name");
        getNameExp().export(out);
        out.setChildRole("select");
        getSelect().export(out);
        out.endElement();
    }

    @Override
    public Elaborator getElaborator() {
        return new NamespaceConstructorElaborator();
    }


    private static class NamespaceConstructorElaborator extends SimpleNodePushElaborator {
        @Override
        public PushEvaluator elaborateForPush() {
            NamespaceConstructor expr = (NamespaceConstructor) getExpression();
            Location loc = expr.getLocation();
            String literalPrefix = expr.getNameExp() instanceof StringLiteral
                    ? ((StringLiteral)expr.getNameExp()).stringify() : null;
            NamespaceUri literalUri = expr.getSelect() instanceof StringLiteral
                    ? NamespaceUri.of(((StringLiteral)expr.getSelect()).stringify()) : null;
            if (literalPrefix != null && literalUri != null) {
                try {
                    expr.checkPrefix(literalPrefix, getConfiguration().getConversionContext());
                    expr.checkPrefixAndUri(literalPrefix, literalUri.toString(), getConfiguration().getConversionContext());
                } catch (XPathException e) {
                    return (output, context) -> {
                        throw e;
                    };
                }
                return (output, context) ->  {
                    output.namespace(literalPrefix, literalUri, ReceiverOption.REJECT_DUPLICATES);
                    return null;
                };
            } else {
                StringEvaluator nameEval = expr.getNameExp().makeElaborator().elaborateForString(true);
                StringEvaluator contentEval = expr.getSelect().makeElaborator().elaborateForString(true);
                return (output, context) -> {
                    String prefix = nameEval.eval(context);
                    String uri = contentEval.eval(context);
                    expr.checkPrefix(prefix, context);
                    expr.checkPrefixAndUri(prefix, uri, context);
                    try {
                        output.namespace(prefix, NamespaceUri.of(uri), ReceiverOption.REJECT_DUPLICATES);
                    } catch (XPathException err) {
                        throw Instruction.dynamicError(loc, err, context);
                    }

                    return null;
                };
            }

        }

        @Override
        public ItemEvaluator elaborateForItem() {
            NamespaceConstructor expr = (NamespaceConstructor) getExpression();
            Location loc = expr.getLocation();
            String literalPrefix = expr.getNameExp() instanceof StringLiteral
                    ? ((StringLiteral) expr.getNameExp()).stringify() : null;
            NamespaceUri literalUri = expr.getSelect() instanceof StringLiteral
                    ? NamespaceUri.of(((StringLiteral) expr.getSelect()).stringify()) : null;
            if (literalPrefix != null && literalUri != null) {
                try {
                    expr.checkPrefix(literalPrefix, getConfiguration().getConversionContext());
                    expr.checkPrefixAndUri(literalPrefix, literalUri.toString(), getConfiguration().getConversionContext());
                } catch (XPathException e) {
                    return (context) -> {
                        throw e;
                    };
                }
                return (context) -> {
                    Orphan o = new Orphan(context.getConfiguration());
                    o.setNodeKind(Type.NAMESPACE);
                    o.setStringValue(literalUri.toUnicodeString());
                    if (!literalPrefix.isEmpty()) {
                        o.setNodeName(new NoNamespaceName(literalPrefix));
                    }
                    return o;
                };
            } else {
                StringEvaluator nameEval = expr.getNameExp().makeElaborator().elaborateForString(true);
                UnicodeStringEvaluator contentEval = expr.getSelect().makeElaborator().elaborateForUnicodeString(true);
                return context -> {
                    String prefix = nameEval.eval(context);
                    UnicodeString uri = contentEval.eval(context);
                    expr.checkPrefix(prefix, context);
                    expr.checkPrefixAndUri(prefix, uri.toString(), context);
                    Orphan o = new Orphan(context.getConfiguration());
                    o.setNodeKind(Type.NAMESPACE);
                    o.setStringValue(uri);
                    if (!prefix.isEmpty()) {
                        o.setNodeName(new NoNamespaceName(prefix));
                    }
                    return o;
                };
            }
        }
    }
}
