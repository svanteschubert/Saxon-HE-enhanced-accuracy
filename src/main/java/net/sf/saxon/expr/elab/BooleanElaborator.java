////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.str.StringConstants;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.BooleanValue;

/**
 * Abstract superclass for elaborators whose primary evaluation method is to return a single boolean. This class provides
 * olternative modes of evaluation (such as pull and item evaluation), all implemented in terms of the
 * boolean evaluator which must be supplied in a subclass.
 */

public abstract class BooleanElaborator extends Elaborator {

    @Override
    public PullEvaluator elaborateForPull() {
        BooleanEvaluator b = elaborateForBoolean();
        return context -> SingletonIterator.makeIterator(BooleanValue.get(b.eval(context)));
    }

    @Override
    public PushEvaluator elaborateForPush() {
        BooleanEvaluator b = elaborateForBoolean();
        return (out, context) -> {
            out.append(BooleanValue.get(b.eval(context)));
            return null;
        };
    }

    @Override
    public ItemEvaluator elaborateForItem() {
        BooleanEvaluator b = elaborateForBoolean();
        return context -> BooleanValue.get(b.eval(context));
    }

    public abstract BooleanEvaluator elaborateForBoolean();

    @Override
    public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
        BooleanEvaluator b = elaborateForBoolean();
        return context -> b.eval(context) ? StringConstants.TRUE : StringConstants.FALSE;
    }


}

