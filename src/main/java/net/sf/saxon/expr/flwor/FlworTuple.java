////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.ObjectValue;

/**
 * A tuple, as it appears in an XQuery tuple stream handled by extended FLWOR expressions.
 */
public class FlworTuple<T extends Sequence> extends ObjectValue<T[]> {

    public FlworTuple(T[] values) {
        super(values);
    }

    public T[] getValues() {
        return getObject();
    }
    
}

