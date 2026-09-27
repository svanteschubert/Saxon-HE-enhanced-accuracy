////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.IContextAccessorFunction;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.registry.XPath31FunctionSet;
import net.sf.saxon.om.*;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpUsing;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.QNameValue;


/**
 * This class represents a reference to a function in its raw lexical form, before it has been resolved to an
 * actual function. Used in XQuery only, where function references can be forwards references to functions not
 * yet declared.
 */
@CSharpUsing(code="Saxon.Hej.trans")
public class DraftFunctionReference extends DraftExpression {

    private final String lexicalName;
    private final QueryModule queryModule;
    private final int arity;

    /**
     * Create a function call to a user-written function in a query or stylesheet
     */

    public DraftFunctionReference(String lexicalName,
                                  QueryModule env,
                                  int arity) {
        this.lexicalName = lexicalName;
        this.queryModule = env;
        this.arity = arity;
    }


    @Override
    public int getIntrinsicDependencies() {
        return StaticProperty.DEPENDS_ON_USER_FUNCTIONS;
    }

    /**
     * Determine whether this is an updating expression as defined in the XQuery update specification
     *
     * @return true if this is an updating expression
     */

    @Override
    public boolean isUpdatingExpression() {
        return false;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings variable bindings that need to be changed
     * @return the copy of the original expression
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        return new DraftFunctionReference(lexicalName, queryModule, arity);
    }

    /**
     * Determine the cardinality of the result
     */

    @Override
    protected int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Call the function, returning the value as an item. This method will be used
     * only when the cardinality is zero or one. If the function is tail recursive,
     * it returns an Object representing the arguments to the next (recursive) call
     */

    @Override
    public Item evaluateItem(XPathContext c) throws XPathException {
        throw cannotExecute();
    }

    /**
     * Call the function, returning an iterator over the results. (But if the function is
     * tail recursive, it returns an iterator over the arguments of the recursive call)
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext c) throws XPathException {
        throw cannotExecute();
    }

    /**
     * Process the function call in push mode
     * @param output the destination for the result
     * @param context the XPath dynamic context
     * @throws XPathException if a dynamic error occurs
     */

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        throw cannotExecute();
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        throw cannotExecute();
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "NamedFunctionRef";
    }

    @Override
    public String toString() {
        return lexicalName + "#" + arity;
    }

    @Override
    public String toShortString() {
        return toString();
    }

    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD;
    }


    @Override
    public Elaborator getElaborator() {
        throw cannotExecute();
    }

    private UnsupportedOperationException cannotExecute() {
        return new UnsupportedOperationException("Internal error. Function reference " + toShortString() + " has not been resolved.");
    }

    @Override
    public Expression simplify() throws XPathException {
        StructuredQName qName;
        FunctionLibrary lib = queryModule.getFunctionLibrary();
        if (NameChecker.isValidNCName(lexicalName)) {
            if (queryModule.getXPathVersion() >= 40) {
                // For an unprefixed function name in 4.0, we look first for a no-namespace name,
                // then for a name in the default function namespace.
                SymbolicName.F sn = new SymbolicName.F(new StructuredQName("", NamespaceUri.NULL, lexicalName), arity);
                if (lib.getFunctionItem(sn, queryModule) != null) {
                    qName = sn.getComponentName();
                } else {
                    qName = new StructuredQName("", queryModule.getDefaultFunctionNamespace(), lexicalName);
                }
            } else {
                qName = new StructuredQName("", queryModule.getDefaultFunctionNamespace(), lexicalName);
            }
        } else {
            NamespaceResolver resolver = getRetainedStaticContext();
            QNameParser qp = new QNameParser(resolver)
                    .withAcceptEQName(true, queryModule.getXPathVersion());
            qName = qp.parse(lexicalName, NamespaceUri.NULL);
        }

        if (queryModule.getConfiguration().isDisabledFunction(qName)) {
            throw new XPathException("Function " + qName.getEQName() + " has been disabled in this Saxon Configuration", "XPST0017")
                    .withLocation(getLocation());
        }

        SymbolicName.F symName = new SymbolicName.F(qName, arity);

        FunctionItem foundFunction = lib.getFunctionItem(symName, queryModule);
        if (foundFunction == null) {
            throw new XPathException("Function " + qName.getEQName()
                                             + "#" + arity + " not found", "XPST0017")
                    .asStaticError()
                    .withLocation(getLocation());
        }

        if (foundFunction instanceof IContextAccessorFunction caf && caf.dependsOnContext()) {
            // For a context-dependent function, return a call on function-lookup(), which saves the context
            SystemFunction lookup = XPath31FunctionSet.getInstance().makeFunction("function-lookup", 2);
            lookup.setRetainedStaticContext(queryModule.makeRetainedStaticContext());
            return lookup.makeFunctionCall(Literal.makeLiteral(new QNameValue(qName, BuiltInAtomicType.QNAME)),
                                           Literal.makeLiteral(Int64Value.makeIntegerValue(arity)));
        }

        Expression ref = XPathParser.makeNamedFunctionReference(qName, foundFunction);
        ExpressionTool.copyLocationInfo(this, ref);
        return ref;
    }

}
