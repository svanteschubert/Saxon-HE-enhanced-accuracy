////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.value;

/**
 * Abstract class representing the XDM type xs:decimal. An instance of xs:decimal that is also
 * an instance of xs:integer will be implemented as an instance of IntegerValue; every other
 * xs:decimal will be implemented as an instance of BigDecimalValue.
 * @since 9.8: in previous releases, the concrete class BigDecimalValue was named DecimalValue,
 * and its instances did not include integers. The new hierarchy is designed to reflect the
 * XDM type hierarchy more faithfully
 */

public abstract class DecimalValue extends NumericValue {
}


