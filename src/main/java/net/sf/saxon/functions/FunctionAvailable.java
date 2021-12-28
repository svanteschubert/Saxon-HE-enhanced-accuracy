////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NameChecker;
import net.sf.saxon.om.QNameParser;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.NumericValue;

/**
 * This class implements the XSLT function-available functions.
 */

public class FunctionAvailable extends SystemFunction {

    @Override
    public Expression makeFunctionCall(Expression... arguments) {
        PackageData pack = getRetainedStaticContext().getPackageData();
        if (pack instanceof StylesheetPackage) {
            ((StylesheetPackage) pack).setRetainUnusedFunctions();
        }
        return super.makeFunctionCall(arguments);
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the context item
     * @param arguments   the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws net.sf.saxon.trans.XPathException if an error is detected
     */
    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, Expression... arguments) throws XPathException {

        // Note, the LATE property is set in the function details to avoid the function being evaluated by the preEvaluate() call.
        // This is because the full static context is needed, not the (smaller) RetainedStaticContext. Instead, pre-evaluation
        // for calls with fixed arguments is done during the optimization phase, which makes the full static context available.

        if (arguments[0] instanceof Literal && (arguments.length == 1 || arguments[1] instanceof Literal)) {
            String lexicalQName = ((Literal) arguments[0]).getValue().getStringValue();
            StaticContext env = visitor.getStaticContext();
            boolean b = false;

            QNameParser qp = new QNameParser(getRetainedStaticContext())
                    .withAcceptEQName(true)
                    .withErrorOnBadSyntax("XTDE1400")
                    .withErrorOnUnresolvedPrefix("XTDE1400");

            StructuredQName functionName = qp.parse(lexicalQName, env.getDefaultFunctionNamespace());

            int minArity = 0;
            int maxArity = 20;
            if (getArity() == 2) {
                minArity = (int) ((NumericValue) arguments[1].evaluateItem(env.makeEarlyEvaluationContext())).longValue();
                maxArity = minArity;
            }

            for (int i = minArity; i <= maxArity; i++) {
                SymbolicName.F sn = new SymbolicName.F(functionName, i);
                if (env.getFunctionLibrary().isAvailable(sn)) {
                    b = true;
                    break;
                }
            }

            return Literal.makeLiteral(BooleanValue.get(b));
        } else {
            return null;
        }
    }

    private boolean isFunctionAvailable(String lexicalName, String edition, int arity, XPathContext context) throws XPathException {
        if (arity == -1) {
            for (int i = 0; i < 20; i++) {
                if (isFunctionAvailable(lexicalName, edition, i, context)) {
                    return true;
                }
            }
            return false;
        }
        StructuredQName qName;
        try {
            if (NameChecker.isValidNCName(lexicalName)) {
                // we're in XSLT, where the default namespace for functions can't be changed
                String uri = NamespaceConstant.FN;
                qName = new StructuredQName("", uri, lexicalName);
            } else {
                qName = StructuredQName.fromLexicalQName(lexicalName,
                        false, true,
                        getRetainedStaticContext());
            }
        } catch (XPathException e) {
            e.setErrorCode("XTDE1400");
            e.setXPathContext(context);
            throw e;
        }

        final FunctionLibrary lib = context.getController().getExecutable().getFunctionLibrary();
        SymbolicName.F sn = new SymbolicName.F(qName, arity);
        // TODO: reinstate something along these lines. Removed because it doesn't build in HE 9.8
//        if (known && qName.hasURI(NamespaceConstant.FN) && !context.getConfiguration().getEditionCode().equals(edition)) {
//            // Target environment differs from compile-time environment: some functions might not be available
//            BuiltInFunctionSet.Entry details = XPath31HOFunctionSet.getInstance().getFunctionDetails(qName.getLocalPart(), sn.getArity());
//            if (details != null) {
//                if (((details.applicability & BuiltInFunctionSet.HOF) != 0) && ("HE".equals(edition) || "JS".equals(edition))) {
//                    return false;
//                }
//                // TODO: some further functions are not available in Saxon-JS
//            }
//        }
        return lib.isAvailable(sn);
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        String lexicalQName = arguments[0].head().getStringValue();
        int arity = -1;
        if (arguments.length == 2) {
            arity = (int) ((NumericValue) arguments[1].head()).longValue();
        }
        return BooleanValue.get(
                isFunctionAvailable(lexicalQName, getRetainedStaticContext().getPackageData().getTargetEdition(), arity, context));
    }
}

