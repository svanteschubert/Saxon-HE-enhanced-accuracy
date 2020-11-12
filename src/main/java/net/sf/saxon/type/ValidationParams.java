////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.sxpath.XPathDynamicContext;
import net.sf.saxon.sxpath.XPathVariable;
import net.sf.saxon.trans.XPathException;

import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a collection of parameter values for use in schema validation;
 * it defines values for the parameters declared using the saxon:param XSD extension.
 * <p>The implementation is just a HashMap; giving the class a name helps type safety.</p>
 */

public class ValidationParams extends HashMap<StructuredQName, Sequence> {

    public ValidationParams() {
        super(20);
    }

    public static void setValidationParams(Map<StructuredQName, XPathVariable> declaredParams, ValidationParams actualParams, XPathDynamicContext context) throws XPathException {
        for (StructuredQName p : declaredParams.keySet()) {
            XPathVariable var = declaredParams.get(p);
            Sequence paramValue = actualParams.get(p);
            if (paramValue != null) {
                context.setVariable(var, paramValue);
            } else {
                context.setVariable(var, var.getDefaultValue());
            }
        }
    }
}

