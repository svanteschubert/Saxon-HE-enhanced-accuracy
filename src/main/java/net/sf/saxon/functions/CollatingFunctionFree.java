////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Generic class for all functions that take an optional collation argument,
 * where the collation argument is supplied and has not yet been resolved. The
 * class provides methods that either do early evaluation of the collation
 * argument (converting the function to the corresponding instance of
 * {@link net.sf.saxon.functions.CollatingFunctionFixed}, or failing that,
 * the collation argument is evaluated at run-time.
 */

public class CollatingFunctionFree extends SystemFunction {

    /**
     * Get the argument position (0-based) containing the collation name
     *
     * @return the position of the argument containing the collation URI
     */

    private int getCollationArgument() {
        // the collation argument is generally the last, but we keep it flexible
        return getArity() - 1;
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
        Expression c = arguments[arguments.length - 1];
        if (c instanceof Literal) {
            String coll = ((Literal) c).getValue().getStringValue();
            try {
                URI collUri = new URI(coll);
                if (!collUri.isAbsolute()) {
                    collUri = ResolveURI.makeAbsolute(coll, getStaticBaseUriString());
                    coll = collUri.toASCIIString();
                }
            } catch (URISyntaxException e) {
                visitor.getStaticContext().issueWarning(
                        "Cannot resolve relative collation URI " + coll, c.getLocation());
            }
            CollatingFunctionFixed fn = bindCollation(coll);
            Expression[] newArgs = new Expression[arguments.length - 1];
            System.arraycopy(arguments, 0, newArgs, 0, newArgs.length);
            return fn.makeFunctionCall(newArgs);
        }
        return null;
    }

    /**
     * Create an instance of (a subclass of) CollatingFunctionFixed representing the underlying
     * function but with the collator already bound
     * @param collationName the name of the collation to be used
     * @return a function to implement this function with a fixed collation
     * @throws XPathException if the collation is unknown
     */

    public CollatingFunctionFixed bindCollation(String collationName) throws XPathException {
        Configuration config = getRetainedStaticContext().getConfiguration();
        CollatingFunctionFixed fixed = (CollatingFunctionFixed)config.makeSystemFunction(
                getFunctionName().getLocalPart(), getArity()-1);
        fixed.setRetainedStaticContext(getRetainedStaticContext());
        fixed.setCollationName(collationName);
        return fixed;
    }

    /**
     * Expand a collation URI, which may be a relative URI reference
     *
     * @param collationName     the collation URI as provided
     * @param expressionBaseURI the base URI against which the collation URI will be resolved if it is relative
     * @return the resolved (expanded) absolute collation URI
     * @throws net.sf.saxon.trans.XPathException if the collation URI cannot be resolved
     */

    public static String expandCollationURI(String collationName, URI expressionBaseURI) throws XPathException {
        try {
            URI collationURI = new URI(collationName);
            if (!collationURI.isAbsolute()) {
                if (expressionBaseURI == null) {
                    throw new XPathException("Cannot resolve relative collation URI '" + collationName +
                        "': unknown or invalid base URI", "FOCH0002");
                }
                collationURI = expressionBaseURI.resolve(collationURI);
                collationName = collationURI.toString();
            }
        } catch (URISyntaxException e) {
            throw new XPathException("Collation name '" + collationName + "' is not a valid URI", "FOCH0002");
        }
        return collationName;
    }

    /**
     * Invoke the function. This is done in effect by currying the function: that is,
     * creating a new function in which the collation argument is bound, and then invoking
     * that new function.
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs within the function
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] args) throws XPathException {
        int c = getCollationArgument();
        String collation = args[c].head().getStringValue();
        collation = expandCollationURI(collation, getRetainedStaticContext().getStaticBaseUri());
        CollatingFunctionFixed fixed = bindCollation(collation);
        Sequence[] retainedArgs = new Sequence[args.length - 1];
        System.arraycopy(args, 0, retainedArgs, 0, c);
        if (c+1 < getArity()) {
            System.arraycopy(args, c+1, retainedArgs, c, getArity() - c);
        }
        return fixed.call(context, retainedArgs);
    }

    @Override
    public String getStreamerName() {
        try {
            return bindCollation(NamespaceConstant.CODEPOINT_COLLATION_URI).getStreamerName();
        } catch (XPathException e) {
            throw new AssertionError(e); // should not happen
        }
    }
}

