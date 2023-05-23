////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;

/**
 * Mapping function that maps the sequence of matching/non-matching strings to the
 * sequence delivered by applying the matching-substring and non-matching-substring
 * expressions respectively to each such string
 */

public class AnalyzeMappingFunction implements ContextMappingFunction {

    private RegexIterator base;
    private XPathContext c2;
    private Expression nonMatchExpr;
    private Expression matchingExpr;

    public AnalyzeMappingFunction(RegexIterator base, XPathContext c2, Expression nonMatchExpr, Expression matchingExpr) {
        this.base = base;
        this.c2 = c2;
        this.nonMatchExpr = nonMatchExpr;
        this.matchingExpr = matchingExpr;
    }

    /**
     * Map one item to a sequence.
     *
     * @param context The processing context. Some mapping functions use this because they require
     *                context information. Some mapping functions modify the context by maintaining the context item
     *                and position. In other cases, the context may be null.
     * @return either (a) a SequenceIterator over the sequence of items that the supplied input
     *         item maps to, or (b) an Item if it maps to a single item, or (c) null if it maps to an empty
     *         sequence.
     */

    @Override
    public SequenceIterator map(XPathContext context) throws XPathException {
        if (base.isMatching()) {
            if (matchingExpr != null) {
                return matchingExpr.iterate(c2);
            }
        } else {
            if (nonMatchExpr != null) {
                return nonMatchExpr.iterate(c2);
            }
        }
        return EmptyIterator.getInstance();
    }
}

