////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.Function;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;

import java.util.List;

/**
 * The <tt>OnDemandFunctionSet</tt> represents a function library where the implementation classes
 * are loaded dynamically on demand. The idea is that no failure should occur if implementations
 * are not available unless the functions are actually required. The class contains the name of
 * a real FunctionLibrary containing the function implementations; that FunctionLibrary is dynamically
 * loaded if an attempt is made to bind a function name in the namespace registered with this
 * class.
 * <p>This mechanism is currently used for the SQL function library, because the implementations
 * of these functions are shipped in a separate JAR file (and this is not available on .NET).
 * If the target function library cannot be loaded, the relevant functions will be reported as
 * being not available.</p>
 */

public class OnDemandFunctionSet implements FunctionLibrary {

    private Configuration config;
    private String namespace;
    private String libraryClass;
    private FunctionLibrary library;

    public OnDemandFunctionSet(Configuration config, String namespace, String libraryClass) {
        this.config = config;
        this.namespace = namespace;
        this.libraryClass = libraryClass;
    }

    /**
     * Given a function name, test whether it is in the namespace handled by this
     * {@code OnDemandFunctionSet}, and if so (and if the library is not already loaded),
     * attempt to dynamically load the target {@code FunctionLibrary}.
     * @param functionName the function that is required
     * @param reasons either null, or a list to which reasons for failure can be added
     * @return true if the function name is in the namespace recognized by this library.
     */

    private boolean load(SymbolicName.F functionName, List<String> reasons) {
        if (functionName.getComponentName().hasURI(namespace)) {
            if (library == null) {
                try {
                    Object lib = config.getDynamicLoader().getInstance(libraryClass, null);
                    if (lib instanceof FunctionLibrary) {
                        library = (FunctionLibrary)lib;
                    } else {
                        if (reasons != null) {
                            reasons.add("Class " + libraryClass + " was loaded but it is not a FunctionLibrary");
                        }
                        return false;
                    }
                } catch (XPathException e) {
                    if (reasons != null) {
                        reasons.add("Failed to load class " + libraryClass + ": " + e.getMessage());
                    }
                    return false;
                }
            }
            library.setConfiguration(config);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isAvailable(SymbolicName.F functionName) {
        boolean match = load(functionName, null);
        return match && library.isAvailable(functionName);
    }

    @Override
    public Expression bind(SymbolicName.F functionName, Expression[] staticArgs, StaticContext env, List<String> reasons) {
        boolean match = load(functionName, reasons);
        if (match) {
            return library.bind(functionName, staticArgs, env, reasons);
        } else {
            return null;
        }
    }

    @Override
    public FunctionLibrary copy() {
        return this;
    }

    @Override
    public Function getFunctionItem(SymbolicName.F functionName, StaticContext staticContext) throws XPathException {
        boolean match = load(functionName, null);
        if (match) {
            return library.getFunctionItem(functionName, staticContext);
        } else {
            return null;
        }
    }

}

