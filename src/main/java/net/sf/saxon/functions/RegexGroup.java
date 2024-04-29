////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.hof.CurriedFunction;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.ma.map.HashTrieMap;
import net.sf.saxon.ma.map.MapFunctionSet;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.FunctionItem;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.NumericValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;


public class RegexGroup extends ContextAccessorFunction {

    @Override
    public FunctionItem bindContext(XPathContext context) throws XPathException {
        FunctionItem alwaysEmptyFunction = new CallableFunction(1, new AlwaysEmpty(),
                                                                new SpecificFunctionType(
                                                                        new SequenceType[]{SequenceType.SINGLE_INTEGER},
                                                                        SequenceType.SINGLE_STRING));
        if (getRetainedStaticContext().getPackageData().getHostLanguageVersion() < 40) {
            return alwaysEmptyFunction;
        }
        RegexIterator ri = context.getCurrentRegexIterator();
        if (ri == null) {
            throw new XPathException("There is no current group", "XTDE1061");
        }
        int groups = ri.getNumberOfGroups();
        MapItem map = new HashTrieMap();
        for (int i=0; i<=groups; i++) {
            map = map.addEntry(Int64Value.makeIntegerValue(i), new StringValue(ri.getRegexGroup(i)));
        }
        final StructuredQName mapGetName = new StructuredQName("map", NamespaceUri.MAP_FUNCTIONS, "get");
        BuiltInFunctionSet lib = MapFunctionSet.getInstance(40);

        SymbolicName.F symbolicName = new SymbolicName.F(mapGetName, 3);
        FunctionItem mapGet3 = lib.getFunctionItem(symbolicName, new IndependentContext(context.getConfiguration()));
        return new CurriedFunction(mapGet3, new Sequence[]{map, null, alwaysEmptyFunction});
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        RegexIterator iter = context.getCurrentRegexIterator();
        if (iter == null) {
            return StringValue.EMPTY_STRING;
        }
        NumericValue gp0 = (NumericValue) arguments[0].head();
        UnicodeString s = iter.getRegexGroup((int) gp0.longValue());
        return StringValue.makeUStringValue(s);
    }

    private static class AlwaysEmpty implements Callable {
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            return StringValue.EMPTY_STRING;
        }

    }
}

