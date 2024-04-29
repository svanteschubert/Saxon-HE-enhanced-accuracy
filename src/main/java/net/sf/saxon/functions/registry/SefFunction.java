////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.PackageLoaderHE;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a system function whose implementation is written in XSLT, and which
 * is implemented here by loading the pre-compiled code from a system SEF file.
 */

public class SefFunction extends SystemFunction {

    private final static NamespaceUri FN_ALIAS = NamespaceUri.of("http://ns.saxonica.com/xpath-functions");
    protected SymbolicName.F functionAlias;

    /**
     * Set the details of this type of function
     *
     * @param entry information giving details of the function signature and other function properties
     */
    @Override
    public void setDetails(BuiltInFunctionSet.Entry entry) {
        super.setDetails(entry);
        functionAlias = new SymbolicName.F(
                new StructuredQName("", FN_ALIAS, getDetails().name.getLocalPart()), getDetails().maxArity);
    }

    /**
     * Make an expression that either calls this function, or that is equivalent to a call
     * on this function
     *
     * @param arguments the supplied arguments to the function call
     * @return either a function call on this function, or an expression that delivers
     * the same result
     */

    @Override
    public Expression makeFunctionCall(Expression... arguments) {
        Expression e = new SystemFunctionCall(this, arguments);
        e.setRetainedStaticContext(getRetainedStaticContext());
        return e;
    }

    protected StylesheetPackage pack;

    protected Sequence callFunction(XPathContext context, SymbolicName.F name, Sequence[] args) throws XPathException {
        if (pack == null) {
            Configuration config = context.getConfiguration();
            PackageLoaderHE loader = new PackageLoaderHE(config);
            List<String> messages = new ArrayList<>();
            InputStream is = Version.platform.locateResource("sef/function-library.sef.xml", messages);
            if (is == null) {
                StringBuilder sb = new StringBuilder("Failed to load built-in function library: function-library.sef.xml");
                for (String msg : messages) {
                    sb.append(": ").append(msg);
                }
                throw new XPathException(sb.toString());
            }
            pack = loader.loadPackage(new StreamSource(is));
        }

        UserFunction targetFunction;
        XPathContextMajor c2;
        Component target = pack.getComponent(name);
        if (target == null) {
            throw new XPathException("Built-in XSLT function " + getDetails().name.getEQName() + " not found");
        }
        if (target.isHiddenAbstractComponent()) {
            throw new XPathException("Cannot call an abstract function (" +
                                             name.getComponentName().getDisplayName() +
                                             ") with no implementation", "XTDE3052");
        }
        targetFunction = (UserFunction) target.getActor();
        c2 = targetFunction.makeNewContext(context, null);
        c2.setCurrentComponent(target);
        try {
            return targetFunction.call(c2, args);
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }

    /**
     * Invoke the function
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws XPathException if a dynamic error occurs within the function
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] args) throws XPathException {
        for (int i=0; i<args.length; i++) {
            args[i] = args[i].materialize();
        }
        return callFunction(context, functionAlias, args);
    }

}

