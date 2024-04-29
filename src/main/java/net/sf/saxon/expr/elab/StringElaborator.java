////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.om.Item;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.StringValue;

/**
 * An abstract Elaborator for expressions that evaluate their result as a single string (that is, as an instance
 * of {@link UnicodeString}); they may also return null to indicate that the result is an empty sequence. The
 * method {@link #elaborateForUnicodeString} must be implemented in a concrete subclass; other evaluation methods
 * are implemented (by default) by calling {@link #elaborateForUnicodeString}.
 */
public abstract class StringElaborator extends Elaborator {

    public boolean returnZeroLengthWhenAbsent() {
        return false;
    }

    @Override
    public PullEvaluator elaborateForPull() {
        UnicodeStringEvaluator strEval = elaborateForUnicodeString(returnZeroLengthWhenAbsent());
        return context -> {
            UnicodeString value = strEval.eval(context);
            if (value == null) {
                return EmptyIterator.getInstance();
            } else {
                return SingletonIterator.makeIterator(StringValue.makeUStringValue(value));
            }
        };
    }

    @Override
    public PushEvaluator elaborateForPush() {
        ItemEvaluator ie = elaborateForItem();
        return (out, context) -> {
            Item it = ie.eval(context);
            if (it != null) {
                out.append(it);
            }
            return null;
        };
    }

    @Override
    public ItemEvaluator elaborateForItem() {
        UnicodeStringEvaluator strEval = elaborateForUnicodeString(returnZeroLengthWhenAbsent());
        return context -> {
            UnicodeString value = strEval.eval(context);
            if (value == null) {
                return null;
            } else {
                return StringValue.makeUStringValue(value);
            }
        };
    }

    public BooleanEvaluator elaborateForBoolean() {
        UnicodeStringEvaluator strEval = elaborateForUnicodeString(returnZeroLengthWhenAbsent());
        return context -> {
            UnicodeString value = strEval.eval(context);
            return value != null && !value.isEmpty();
        };
    }

    @Override
    public abstract UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent);


}

