////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.StringValue;

/**
 * This class supports the function string-to-codepoints()
 */

public class StringToCodepoints extends SystemFunction {

    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue val = (StringValue)arguments[0].head();
        if (val == null) {
            return EmptySequence.INSTANCE;
        }
        return SequenceTool.toLazySequence(val.iterateCharacters());
    }

}

