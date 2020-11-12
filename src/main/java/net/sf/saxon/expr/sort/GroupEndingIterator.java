////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.Count;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.ArrayList;

/**
 * A GroupEndingIterator iterates over a sequence of groups defined by
 * xsl:for-each-group group-ending-with="x". The groups are returned in
 * order of first appearance.
 */

public class GroupEndingIterator extends GroupMatchingIterator implements GroupIterator, LookaheadIterator {

    public GroupEndingIterator(Expression select, Pattern endPattern,
                               XPathContext context)
            throws XPathException {
        this.select = select;
        this.pattern = endPattern;
        baseContext = context;
        runningContext = context.newMinorContext();
        this.population = runningContext.trackFocus(select.iterate(context));
        // the first item in the population always starts a new group
        next = population.next();
    }

    @Override
    public int getLength() throws XPathException {
        GroupEndingIterator another = new GroupEndingIterator(select, pattern, baseContext);
        return Count.steppingCount(another);
    }

    @Override
    protected void advance() throws XPathException {
        currentMembers = new ArrayList<>(20);
        currentMembers.add(current);

        next = current;
        while (next != null) {
            if (pattern.matches(next, runningContext)) {
                next = population.next();
                if (next != null) {
                    break;
                }
            } else {
                next = population.next();
                if (next != null) {
                    currentMembers.add(next);
                }
            }
        }
    }


}

