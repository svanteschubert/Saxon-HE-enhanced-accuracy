////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.xpath;

import net.sf.saxon.functions.CallableFunction;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.value.SequenceType;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionResolver;
import java.util.Arrays;
import java.util.List;

/**
 * The XPathFunctionLibrary is a FunctionLibrary that supports binding of XPath function
 * calls to instances of the JAXP XPathFunction interface returned by an XPathFunctionResolver.
 */

public class XPathFunctionLibrary implements FunctionLibrary {

    private XPathFunctionResolver resolver;

    /**
     * Construct a XPathFunctionLibrary
     */

    public XPathFunctionLibrary() {
    }

    /**
     * Set the resolver
     *
     * @param resolver The XPathFunctionResolver wrapped by this FunctionLibrary
     */

    public void setXPathFunctionResolver(XPathFunctionResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Get the resolver
     *
     * @return the XPathFunctionResolver wrapped by this FunctionLibrary
     */

    public XPathFunctionResolver getXPathFunctionResolver() {
        return resolver;
    }

    /**
     * Bind a function, given the URI and local parts of the function name,
     * and the list of expressions supplied as arguments. This method is called at compile
     * time.
     *
     * @param functionName  The name of the function
     * @param staticArgs   The expressions supplied statically in the function call. The intention is
     *                     that the static type of the arguments (obtainable via getItemType() and getCardinality() may
     *                     be used as part of the binding algorithm.
     * @param env          The static context
     * @param reasons      If no matching function is found by the function library, it may add
     *                     a diagnostic explanation to this list explaining why none of the available
     *                     functions could be used.
     * @return An object representing the extension function to be called, if one is found;
     *         null if no extension function was found matching the required name, arity, or signature.
     */

    /*@Nullable*/
    @Override
    public Expression bind(/*@NotNull*/ SymbolicName.F functionName, /*@NotNull*/  Expression[] staticArgs, StaticContext env, List<String> reasons) {
        if (resolver == null) {
            return null;
        }
        StructuredQName qn = functionName.getComponentName();
        QName name = new QName(qn.getURI(), qn.getLocalPart());
        XPathFunction function = resolver.resolveFunction(name, functionName.getArity());
        if (function == null) {
            return null;
        }
        XPathFunctionCall fc = new XPathFunctionCall(qn, function);
        fc.setArguments(staticArgs);
        return fc;
    }

    /**
     * Test whether a function with a given name and arity is available; if so, return a function
     * item that can be dynamically called.
     * <p>This supports the function-lookup() function in XPath 3.0.</p>
     *
     * @param symbolicName  the qualified name of the function being called
     * @param staticContext the static context to be used by the function, in the event that
     *                      it is a system function with dependencies on the static context
     * @return if a function of this name and arity is available for calling, then a corresponding
     *         function item; or null if the function does not exist
     * @throws net.sf.saxon.trans.XPathException
     *          in the event of certain errors, for example attempting to get a function
     *          that is private
     */
    @Override
    public Function getFunctionItem(SymbolicName.F symbolicName, StaticContext staticContext) throws XPathException {
        if (resolver == null) {
            return null;
        }
        StructuredQName functionName = symbolicName.getComponentName();
        int arity = symbolicName.getArity();
        QName name = new QName(functionName.getURI(), functionName.getLocalPart());
        XPathFunction function = resolver.resolveFunction(name, arity);
        if (function == null) {
            return null;
        }
        XPathFunctionCall functionCall = new XPathFunctionCall(functionName, function);
        SequenceType[] argTypes = new SequenceType[arity];
        Arrays.fill(argTypes, SequenceType.ANY_SEQUENCE);
        FunctionItemType functionType = new SpecificFunctionType(argTypes, SequenceType.ANY_SEQUENCE);
        return new CallableFunction(symbolicName, functionCall, functionType);
    }

    /**
     * Test whether a function with a given name and arity is available
     * <p>This supports the function-available() function in XSLT.</p>
     *
     * @param functionName the qualified name of the function being called
     * @return true if a function of this name and arity is available for calling
     */
    @Override
    public boolean isAvailable(SymbolicName.F functionName) {
        return resolver != null &&
                resolver.resolveFunction(
                        new QName(functionName.getComponentName().getURI(), functionName.getComponentName().getLocalPart()),
                        functionName.getArity()) != null;
    }

    /**
     * This method creates a copy of a FunctionLibrary: if the original FunctionLibrary allows
     * new functions to be added, then additions to this copy will not affect the original, or
     * vice versa.
     *
     * @return a copy of this function library. This must be an instance of the original class.
     */

    /*@NotNull*/
    @Override
    public FunctionLibrary copy() {
        XPathFunctionLibrary xfl = new XPathFunctionLibrary();
        xfl.resolver = resolver;
        return xfl;
    }


}

