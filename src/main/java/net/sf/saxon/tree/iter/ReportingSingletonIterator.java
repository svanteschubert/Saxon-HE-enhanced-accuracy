////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.elab.LearningEvaluator;
import net.sf.saxon.om.Item;

/**
 * This is a subclass of <code>SingletonIterator</code> that reports to
 * a <code>LearningEvaluator</code> when the single target item is actually
 * read. The <code>LearningEvaluator</code> uses this information to decide
 * whether to perform lazy or eager evaluation.
 */

public class ReportingSingletonIterator extends SingletonIterator {

    private final LearningEvaluator learningEvaluator;
    private final int serialNumber;

    /**
     * Create a <code>ReportingSingletonIterator</code>
     * @param item the single item to be delivered by the iterator
     * @param learningEvaluator the class to be notified when the item is read
     * @param serialNumber a serial number identifying this evaluation
     */

    public ReportingSingletonIterator(Item item, LearningEvaluator learningEvaluator, int serialNumber) {
        super(item);
        this.learningEvaluator = learningEvaluator;
        this.serialNumber = serialNumber;
    }

    @Override
    public Item next() {
        Item result = super.next();
        if (result != null) {
            learningEvaluator.reportCompletion(serialNumber);
        }
        return result;
    }
}

