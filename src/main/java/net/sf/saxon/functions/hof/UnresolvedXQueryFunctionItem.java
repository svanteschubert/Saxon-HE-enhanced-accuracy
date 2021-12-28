////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;

public class UnresolvedXQueryFunctionItem extends AbstractFunction {
    private final XQueryFunction fd;
    private final SymbolicName.F functionName;
    private final UserFunctionReference ref;

    public UnresolvedXQueryFunctionItem(XQueryFunction fd, SymbolicName.F functionName, UserFunctionReference ref) {
        this.fd = fd;
        this.functionName = functionName;
        this.ref = ref;
    }

    @Override
    public FunctionItemType getFunctionItemType() {
        return new SpecificFunctionType(fd.getArgumentTypes(), fd.getResultType());
    }

    @Override
    public StructuredQName getFunctionName() {
        return functionName.getComponentName();
    }

    @Override
    public int getArity() {
        return fd.getNumberOfArguments();
    }

    @Override
    public Sequence call(XPathContext context, Sequence[] args) throws XPathException {
        return ((Function) ref.evaluateItem(context)).call(context, args);
    }

    @Override
    public String getDescription() {
        return functionName.toString();
    }

    public UserFunctionReference getFunctionReference() {
        return ref;
    }
}

// Copyright (c) 2018-2020 Saxonica Limited

