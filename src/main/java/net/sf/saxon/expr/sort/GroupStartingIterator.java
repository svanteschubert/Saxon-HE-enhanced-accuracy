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
import net.sf.saxon.om.Item;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;

import java.util.ArrayList;

/**
 * A GroupStartingIterator iterates over a sequence of groups defined by
 * xsl:for-each-group group-starting-with="x". The groups are returned in
 * order of first appearance.
 */

public class GroupStartingIterator extends GroupMatchingIterator implements LookaheadIterator, GroupIterator {

    public GroupStartingIterator(Expression select, Pattern startPattern,
                                 XPathContext context)
            throws XPathException {
        this.select = select;
        this.pattern = startPattern;
        baseContext = context;
        runningContext = context.newMinorContext();
        this.population = runningContext.trackFocus(select.iterate(context));
        // the first item in the population always starts a new group
        next = population.next();
    }

    @Override
    public int getLength() throws XPathException {
        GroupStartingIterator another = new GroupStartingIterator(select, pattern, baseContext);
        return Count.steppingCount(another);
    }

    @Override
    protected void advance() throws XPathException {
        currentMembers = new ArrayList<>(10);
        currentMembers.add(current);
        while (true) {
            Item nextCandidate = population.next();
            if (nextCandidate == null) {
                break;
            }
            if (pattern.matches(nextCandidate, runningContext)) {
                next = nextCandidate;
                return;
            } else {
                currentMembers.add(nextCandidate);
            }
        }
        next = null;
    }

}

