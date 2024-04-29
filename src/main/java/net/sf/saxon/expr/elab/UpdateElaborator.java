////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

/**
 * Abstract evaluator for XQuery Update expressions. Most evaluation modes (pull, push, item) etc are
 * not supported for such expressions, so the relevant methods throw an error. The concrete subclass
 * must provide an {@link Elaborator#elaborateForUpdate()} method
 */

public abstract class UpdateElaborator extends Elaborator {

    private void notAllowed() {
        throw new UnsupportedOperationException("Cannot evaluate updating expression");
    }

    @Override
    public SequenceEvaluator eagerly() {
        notAllowed();
        return null;
    }

    @Override
    public PullEvaluator elaborateForPull() {
        notAllowed();
        return null;
    }

    @Override
    public PushEvaluator elaborateForPush() {
        notAllowed();
        return null;
    }

    @Override
    public ItemEvaluator elaborateForItem() {
        notAllowed();
        return null;
    }

    public BooleanEvaluator elaborateForBoolean() {
        notAllowed();
        return null;
    }

    @Override
    public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
        notAllowed();
        return null;
    }

    @Override
    public abstract UpdateEvaluator elaborateForUpdate();
}

