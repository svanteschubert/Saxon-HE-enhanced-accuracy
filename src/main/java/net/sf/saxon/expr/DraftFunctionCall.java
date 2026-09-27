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
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.hof.DynamicPartialApply;
import net.sf.saxon.functions.hof.FunctionLiteral;
import net.sf.saxon.om.*;
import net.sf.saxon.query.QueryModule;
import net.sf.saxon.query.XQueryParser;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpUsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;


/**
 * This class represents a call to a function in its raw lexical form, before it has been resolved to an
 * actual function. Used in XQuery only, where function references can be forwards references to functions not
 * yet declared.
 */
@CSharpUsing(code="Saxon.Hej.trans")
public class DraftFunctionCall extends DraftExpression {

    private final String lexicalName;
    private final QueryModule queryModule;
    private final Operand[] operanda;
    private final Map<StructuredQName, Integer> keywordArgs;

    /**
     * Create a function call to a user-written function in a query
     *
     * @param lexicalName The name of the function as written in the source query
     * @param queryModule The query module containing the function definition
     * @param arguments   The argument expressions, in order, representing any
     *                    "?" place marker by a PlaceHolder pseudo-expression
     * @param keywordArgs Mapping from argument keywords (where used) to the zero-based
     *                    position where the keyword is used
     */

    public DraftFunctionCall(String lexicalName,
                             QueryModule queryModule,
                             Expression[] arguments,
                             Map<StructuredQName, Integer> keywordArgs) {
        this.lexicalName = lexicalName;
        this.queryModule = queryModule;
        this.operanda = new Operand[arguments.length];
        int i=0;
        for (Expression arg : arguments) {
            operanda[i++] = new Operand(this, arg, OperandRole.NAVIGATE);
        }
        this.keywordArgs = keywordArgs;
    }

    @Override
    public Iterable<Operand> operands() {
        return Arrays.asList(operanda);
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
        return false; // we don't actually know yet
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
        Expression[] a2 = new Expression[operanda.length];
        int i=0;
        for (Operand operand : operanda) {
            a2[i++] = operand.getChildExpression().copy(rebindings);
        }
        return new DraftFunctionCall(lexicalName, queryModule, a2, keywordArgs);
    }

    private Expression[] getArguments() {
        Expression[] a2 = new Expression[operanda.length];
        int i = 0;
        for (Operand operand : operanda) {
            a2[i++] = operand.getChildExpression();
        }
        return a2;
    }

    /**
     * Determine the cardinality of the result
     */

    @Override
    protected int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
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
        return lexicalName;
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
        return new UnsupportedOperationException("Internal error. Function call " + toShortString() + " has not been resolved.");
    }

    /**
     * The {@code simplify} method applied to a DraftFunctionCall when the entire query has been parsed, so all user-defined
     * functions are now known, enabling the name to be resolved. As well as locating the target function, keyword arguments
     * can be resolved to positional arguments.
     * @return either a FunctionCall or a CurriedFunction
     * @throws XPathException if resolution fails
     */
    @Override
    public Expression simplify() throws XPathException {
        simplifyChildren();
        StructuredQName qName;
        int arity = operanda.length;
        FunctionLibrary lib = queryModule.getFunctionLibrary();
        NamespaceUri defaultFunctionNamespace = queryModule.getDefaultFunctionNamespace();
        if (NameChecker.isValidNCName(lexicalName)) {
            if (queryModule.getXPathVersion() >= 40) {
                // For an unprefixed function name in 4.0, we look first for a no-namespace name,
                // then for a name in the default function namespace.
                SymbolicName.F sn = new SymbolicName.F(new StructuredQName("",NamespaceUri.NULL, lexicalName), arity);
                if (lib.getFunctionItem(sn, queryModule) != null) {
                    qName = sn.getComponentName();
                } else {
                    qName = new StructuredQName("", defaultFunctionNamespace, lexicalName);
                }
            } else {
                qName = new StructuredQName("", defaultFunctionNamespace, lexicalName);
            }
        } else {
            NamespaceResolver resolver = getRetainedStaticContext();
            QNameParser qp = new QNameParser(resolver)
                    .withAcceptEQName(true, queryModule.getXPathVersion())
                    .withUnescaper(new XQueryParser.Unescaper(queryModule.getConfiguration().getValidCharacterChecker()));
            qName = qp.parse(lexicalName, NamespaceUri.NULL);
        }

        if (queryModule.getConfiguration().isDisabledFunction(qName)) {
            throw new XPathException("Function " + qName.getEQName() + " has been disabled in this Saxon Configuration", "XPST0017")
                    .withLocation(getLocation());
        }

        SymbolicName.F functionName = new SymbolicName.F(qName, arity);

        return makeFunctionCall(lib, functionName, qName);
    }

    private Expression makeFunctionCall(FunctionLibrary lib, SymbolicName.F functionName, StructuredQName qName) throws XPathException {
        // Make a function call expression

        List<String> reasons = new ArrayList<>();
        Expression[] args = getArguments();
        Expression exp = lib.bind(functionName, args, keywordArgs, queryModule, reasons);
        if (exp == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("Cannot find a ").append(args.length).append(
                    "-argument function named ").append(functionName.getComponentName().getEQName()).append("()");
            for (String reason : reasons) {
                sb.append(". ").append(reason);
            }
            throw new XPathException(sb.toString(), "XPST0017").asStaticError();
        }
//
//        if (exp instanceof UserFunctionCall u && u.getFunction() == null) {
//            // Target function exists, but is not yet compiled
//            // Leave this as a DraftFunctionCall for now, and assume we'll get another chance
//            // to resolve it later
//            return this;
//        }

        if (exp instanceof FunctionCall fc) {

            // Inject any default value expressions
            for (int i = 0; i < fc.getArguments().length; i++) {
                if (fc.getArg(i) instanceof DefaultedArgumentExpression) {
                    if (fc instanceof UserFunctionCall) {
                        // defaults already expanded by XQueryFunctionLibrary.bind()
                    } else if (fc instanceof SystemFunctionCall sfc) {
                        Supplier<Expression> def = sfc.getTargetFunction().getDetails().getDefaultValueExpression(i);
                        fc.setArg(i, def==null ? Literal.makeEmptySequence() : def.get());
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }
                fc.adoptChildExpression(fc.getArg(i));
            }

            // If there are placeholders as well as keywords then we take advantage of the fact that
            // the call on bind() will have dealt with mapping keyword arguments to positional arguments,
            // and evaluating defaulted arguments

            if (hasPlaceholders()) {
                SymbolicName.F sn2 = new SymbolicName.F(functionName.getComponentName(), fc.getArguments().length);
                FunctionItem functionItem = lib.getFunctionItem(sn2, queryModule);
                return new DynamicPartialApply(new FunctionLiteral(functionItem), fc.getArguments());
            }
        } else if (hasPlaceholders()) {
            // For example, this happens with test xqhof10, xs:NCName(?), where bind() returns a CastExpression
            FunctionItem functionItem = lib.getFunctionItem(functionName, queryModule);
            return new DynamicPartialApply(new FunctionLiteral(functionItem), getArguments());
        }

        exp.setRetainedStaticContext(getRetainedStaticContext());
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    private boolean hasPlaceholders() {
        for (Operand op : operanda) {
            if (op.getChildExpression() instanceof PlaceHolder) {
                return true;
            }
        }
        return false;
    }

    private boolean hasKeywordArgs() {
        return keywordArgs != null && !keywordArgs.isEmpty();
    }

    private Expression makePartialApplication(Expression functionCall) throws XPathException {

        return null;

//        if (functionCall instanceof FunctionCall) {
//            // This will be a function call that includes placeholders in the argument list
//            // For example, if we have a call such as f(1, 2, ?), then we construct a function
//            // item with one argument $X whose effect is to call f(1, 2, $X)
//        }
//
//        FunctionItem target = lib.getFunctionItem(sn, queryModule);
//        if (target == null) {
//            String msg = "Cannot find a " + arity
//                    + "-argument function named " + name.getEQName() + "()";
//            throw new XPathException(msg, "XPST0017").asStaticError();
//        }
//        if (target instanceof IContextAccessorFunction) {
//            // For a context-dependent function, return a call on function-lookup(), which saves the context
//            SystemFunction lookup = XPath31FunctionSet.getInstance().makeFunction("function-lookup", 2);
//            lookup.setRetainedStaticContext(queryModule.makeRetainedStaticContext());
//            return lookup.makeFunctionCall(Literal.makeLiteral(new QNameValue(name, BuiltInAtomicType.QNAME)),
//                                           Literal.makeLiteral(Int64Value.makeIntegerValue(arity)));
//
//        }
//        Expression targetExp = XPathParser.makeNamedFunctionReference(name, target);
//        ExpressionTool.copyLocationInfo(this, targetExp);
//
//
//        return new DynamicPartialApply(targetExp, getArguments());
    }


}
