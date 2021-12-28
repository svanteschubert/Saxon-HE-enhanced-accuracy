////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.*;
import net.sf.saxon.om.*;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ManualIterator;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.QNameValue;

/**
 * This class supports the function-lookup() function in XPath 3.0. It takes as arguments
 * a function name (QName) and arity, and returns a function item representing that
 * function if found, or an empty sequence if not found.
 */

public class FunctionLookup extends ContextAccessorFunction {

    private XPathContext boundContext = null;

    public FunctionLookup() { }

    @Override
    public Expression makeFunctionCall(Expression... arguments) {
        PackageData pack = getRetainedStaticContext().getPackageData();
        if (pack instanceof StylesheetPackage) {
            ((StylesheetPackage)pack).setRetainUnusedFunctions();
        }
        return super.makeFunctionCall(arguments);
    }

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return super.equals(o) &&
                ExpressionTool.equalOrNull(getRetainedStaticContext(), ((FunctionLookup) o).getRetainedStaticContext());
    }

    /**
     * Bind a context item to appear as part of the function's closure. If this method
     * has been called, the supplied context item will be used in preference to the
     * context item at the point where the function is actually called.
     *
     * @param context the context to which the function applies. Must not be null.
     */
    @Override
    public Function bindContext(XPathContext context) {
        FunctionLookup bound = (FunctionLookup) SystemFunction.makeFunction("function-lookup", getRetainedStaticContext(), 2);
        FocusIterator focusIterator = context.getCurrentIterator();
        if (focusIterator != null) {
            XPathContext c2 = context.newMinorContext();
            ManualIterator mi =
                    new ManualIterator(context.getContextItem(), focusIterator.position());
            c2.setCurrentIterator(mi);
            bound.boundContext = c2;
        } else {
            bound.boundContext = context;
        }
        return bound;
    }

    public Function lookup(StructuredQName name, int arity, XPathContext context) throws XPathException {

        Controller controller = context.getController();
        Executable exec = controller.getExecutable();
        RetainedStaticContext rsc = getRetainedStaticContext();
        PackageData pd = rsc.getPackageData();
        FunctionLibrary lib = pd instanceof StylesheetPackage ?
                ((StylesheetPackage) pd).getFunctionLibrary() : exec.getFunctionLibrary();
        SymbolicName.F sn = new SymbolicName.F(name, arity);

        IndependentContext ic = new IndependentContext(controller.getConfiguration());
        ic.setDefaultCollationName(rsc.getDefaultCollationName());
        ic.setBaseURI(rsc.getStaticBaseUriString());
        ic.setDecimalFormatManager(rsc.getDecimalFormatManager());
        ic.setNamespaceResolver(rsc);
        ic.setPackageData(pd);
        try {
            Function fi = lib.getFunctionItem(sn, ic);
            if (fi instanceof UserFunction) {
                Visibility vis = ((UserFunction) fi).getDeclaredVisibility();
                if (vis == Visibility.ABSTRACT) {
                    return null;
                }
            }
            if (fi instanceof CallableFunction) {
                ((CallableFunction) fi).setCallable(new CallableWithBoundFocus(((CallableFunction) fi).getCallable(), context));
            } else if (fi instanceof ContextItemAccessorFunction) {
                return ((ContextItemAccessorFunction) fi).bindContext(context);
            } else if (fi instanceof SystemFunction && ((SystemFunction) fi).dependsOnContextItem()) {
                return new SystemFunctionWithBoundContextItem((SystemFunction) fi, context);
            }
            return fi;
        } catch (XPathException e) {
            if ("XPST0017".equals(e.getErrorCodeLocalPart())) {
                return null;
            }
            throw e;
        }
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
    public ZeroOrOne call(XPathContext context, Sequence[] arguments) throws XPathException {
        XPathContext c = boundContext == null ? context : boundContext;
        QNameValue qname = (QNameValue) arguments[0].head();
        IntegerValue arity = (IntegerValue) arguments[1].head();
        Function fi = lookup(qname.getStructuredQName(), (int) arity.longValue(), c);
        if (fi == null) {
            return ZeroOrOne.empty();
        }
        if (fi instanceof ContextAccessorFunction) {
            fi = ((ContextAccessorFunction) fi).bindContext(c);
        }
        Component target = fi instanceof UserFunction ? ((UserFunction) fi).getDeclaringComponent() : null;
        ExportAgent agent = out -> makeFunctionCall(Literal.makeLiteral(qname), Literal.makeLiteral(arity)).export(out);
        Function result = new UserFunctionReference.BoundUserFunction(agent, fi, target, c.getController());
        return new ZeroOrOne(result);
    }
}


// Copyright (c) 2011-2020 Saxonica Limited
