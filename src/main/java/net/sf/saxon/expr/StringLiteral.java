////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.str.StringTool;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.value.StringValue;

/**
 * Subclass of Literal used specifically for string literals, as this is a common case
 */
public class StringLiteral extends Literal {

    /**
     * Create a StringLiteral that wraps a StringValue
     *
     * @param value     the StringValue
     */

    public StringLiteral(StringValue value) {
        super(value);
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

    public UnicodeString getString() {
        return getGroundedValue().getUnicodeStringValue();
    }

    /**
     * Get the string represented by this StringLiteral, as a String
     *
     * @return the underlying string
     */

    public String stringify() {
        return getGroundedValue().getStringValue();
    }

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        StringLiteral stringLiteral = new StringLiteral(getGroundedValue());
        ExpressionTool.copyLocationInfo(this, stringLiteral);
        return stringLiteral;
    }
}

