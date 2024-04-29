////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.elab;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;

/**
 * Evaluator for a variable that needs to be indexed, because the optimizer has identified
 * that it will contain a sequence that is accessed using predicates based on the content
 * Note, indexing is only used in Saxon-EE; the implementation in Saxon-HE is a dummy.
 */
public class IndexedVariableEvaluator implements SequenceEvaluator {

    final PullEvaluator puller;

    public IndexedVariableEvaluator(PullEvaluator select) {
        this.puller = select;
    }

    /**
     * Evaluate a construct to produce a value (which might be a lazily evaluated Sequence)
     *
     * @param context the evaluation context
     * @return a Sequence (not necessarily grounded)
     * @throws XPathException if a dynamic error occurs during the evaluation.
     */
    @Override
    public Sequence evaluate(XPathContext context) throws XPathException {
        try {
            return context.getConfiguration().obtainOptimizer().makeIndexedValue(puller.iterate(context));
        } catch (UncheckedXPathException e) {
            throw e.getXPathException();
        }
    }

}
