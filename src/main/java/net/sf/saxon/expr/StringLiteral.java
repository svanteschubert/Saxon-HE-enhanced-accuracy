////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.GroundedValue;
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
        super((GroundedValue)value);
    }

    /**
     * Create a StringLiteral that wraps any CharSequence (including, of course, a String)
     *
     * @param value     the CharSequence to be wrapped
     */

    public StringLiteral(CharSequence value) {
        this(StringValue.makeStringValue(value));
    }

    /**
     * Get the value represented by this Literal
     *
     * @return the constant value
     */
    @Override
    public StringValue getValue() {
        return (StringValue)super.getValue();
    }

    /**
     * Get the string represented by this StringLiteral
     *
     * @return the underlying string
     */

    public String getStringValue() {
        //noinspection RedundantCast
        return ((StringValue) getValue()).getStringValue();
    }

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        StringLiteral stringLiteral = new StringLiteral(getValue());
        ExpressionTool.copyLocationInfo(this, stringLiteral);
        return stringLiteral;
    }
}

