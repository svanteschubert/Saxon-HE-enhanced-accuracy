////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.str.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

/**
 * Subclass of Literal used specifically for string literals, as this is a common case
 */
public class StringLiteral extends Literal {

    private final String valueAsString;

    /**
     * Create a StringLiteral that wraps a StringValue
     *
     * @param value     the StringValue
     */

    public StringLiteral(StringValue value) {
        super(entwine(value));
        valueAsString = value.getStringValue();
    }

    private static StringValue entwine(StringValue in) {
        UnicodeString us = in.getUnicodeStringValue();
        if (us instanceof Twine8 || us instanceof Twine16 || us instanceof Twine24
                || us instanceof EmptyUnicodeString || us instanceof UnicodeChar) {
            return in;
        }
        UnicodeString twine = TwineBuilder.make(in.length32()).append(us).toUnicodeString();
        return new StringValue(twine, in.getItemType());
    }

    /**
     * Create a StringLiteral that wraps any UnicodeString
     *
     * @param value the UnicodeString to be wrapped
     */

    public StringLiteral(UnicodeString value) {
        this(new StringValue(value));
    }

    /**
     * Create a StringLiteral that wraps a String
     *
     * @param value the String to be wrapped
     */

    public StringLiteral(String value) {
        this(new StringValue(StringTool.fromCharSequence(value)));
    }

    /**
     * Get the value represented by this Literal
     *
     * @return the constant value
     */
    @Override
    public StringValue getGroundedValue() {
        return (StringValue)super.getGroundedValue();
    }

    /**
     * Get the string represented by this StringLiteral, as a UnicodeString
     *
     * @return the underlying string
     */

    public UnicodeString getUnicodeString() {
        return getGroundedValue().getUnicodeStringValue();
    }

    /**
     * Get the string represented by this StringLiteral, as a String
     *
     * @return the underlying string
     */

    public String stringify() {
        return valueAsString;
    }

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        StringLiteral stringLiteral = new StringLiteral(getGroundedValue());
        ExpressionTool.copyLocationInfo(this, stringLiteral);
        return stringLiteral;
    }

    /**
     * Test whether the value is an instance of a supplied type
     *
     * @param req the required type
     * @param th  the type hierarchy cache
     * @return true if the value is (strictly) an instance of the type, in the sense of the "instance of" operator
     */
    @Override
    public boolean isInstance(SequenceType req, TypeHierarchy th) {
        int requiredCardinality = req.getCardinality();
        if ((requiredCardinality & StaticProperty.ALLOWS_ONE) == 0) {
            return false;
        }
        return req.getPrimaryType().matches(getGroundedValue());
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        return '"' + Err.truncate30(getUnicodeString()).toString() + '"';
    }
}

