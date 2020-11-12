////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.AbstractFunction;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.Converter;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;

/**
 * A function item representing a constructor function for an atomic type
 */

public class AtomicConstructorFunction extends AbstractFunction {

    private AtomicType targetType;
    private NamespaceResolver nsResolver;

    /**
     * Create the constructor function.
     *
     * @param targetType the type to which the function will convert its input
     * @param resolver   namespace resolver for use if the target type is namespace-sensitive
     */

    public AtomicConstructorFunction(AtomicType targetType, NamespaceResolver resolver) {
        this.targetType = targetType;
        this.nsResolver = resolver;
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */
    @Override
    public FunctionItemType getFunctionItemType() {
        return new SpecificFunctionType(
                new SequenceType[]{SequenceType.OPTIONAL_ATOMIC},
                SequenceType.makeSequenceType(targetType, StaticProperty.ALLOWS_ZERO_OR_ONE));
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous inline function
     */
    @Override
    public StructuredQName getFunctionName() {
        return targetType.getTypeName();
    }

    /**
     * Get a description of this function for use in error messages. For named functions, the description
     * is the function name (as a lexical QName). For others, it might be, for example, "inline function",
     * or "partially-applied ends-with function".
     *
     * @return a description of the function for use in error messages
     */
    @Override
    public String getDescription() {
        return getFunctionName().getDisplayName();
    }

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature
     */
    @Override
    public int getArity() {
        return 1;
    }

    /**
     * Invoke the function
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs within the function
     */
    @Override
    public ZeroOrOne call(XPathContext context, Sequence[] args) throws XPathException {
        AtomicValue val = (AtomicValue) args[0].head();
        if (val == null) {
            return ZeroOrOne.empty();
        }
        Configuration config = context.getConfiguration();
        Converter converter = config.getConversionRules().getConverter(val.getItemType(), targetType);
        if (converter == null) {
            XPathException ex = new XPathException("Cannot convert " + val.getItemType() + " to " + targetType, "XPTY0004");
            ex.setIsTypeError(true);
            throw ex;
        }
        converter = converter.setNamespaceResolver(nsResolver);
        return new ZeroOrOne(converter.convert(val).asAtomic());
    }

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("acFnRef");
        out.emitAttribute("name", targetType.getTypeName());
        out.endElement();
    }

    @Override
    public boolean isTrustedResultType() {
        return true;
    }
}

// Copyright (c) 2018-2020 Saxonica Limited

