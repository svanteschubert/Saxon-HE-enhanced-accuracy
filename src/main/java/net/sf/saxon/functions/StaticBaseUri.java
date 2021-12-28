////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AnyURIValue;

/**
 * Implement the XPath function static-base-uri()
 */
public class StaticBaseUri extends SystemFunction {

    @Override
    public AnyURIValue call(XPathContext context, Sequence[] args) throws XPathException {
        return new AnyURIValue(getRetainedStaticContext().getStaticBaseUriString());
    }

    @Override
    public Expression makeFunctionCall(Expression[] arguments) {
        PackageData pd = getRetainedStaticContext().getPackageData();
        if (pd.isRelocatable()) {
            return super.makeFunctionCall(arguments);
        } else {
            return Literal.makeLiteral(new AnyURIValue(getRetainedStaticContext().getStaticBaseUriString()));
        }
    }
}
