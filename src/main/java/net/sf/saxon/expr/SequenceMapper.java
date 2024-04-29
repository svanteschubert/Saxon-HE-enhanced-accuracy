////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

/**
 * SequenceMapper is an implementation of {@link MappingFunction} that wraps
 * a function typically supplied as a lambda expression.
 *
 * <p>NOTE: Java allows a lambda expression to be used wherever a {@link MappingFunction}
 * is needed, but C# does not (it's not possible in C# to have a class implementing
 * a delegate). So if a lambda expression is wanted, use an {@link SequenceMapper}
 * as a wrapper.</p>
 */

public class SequenceMapper implements MappingFunction {

    @FunctionalInterface
    public interface Lambda {
        SequenceIterator map(Item item) throws XPathException;
    }

    private final Lambda lambda;

    private SequenceMapper(Lambda lambda) {
        this.lambda = lambda;
    }

    public static SequenceMapper of(Lambda lambda) {
        return new SequenceMapper(lambda);
    }

    /**
     * Map one item to a sequence
     *
     * @param item The input item to be mapped.
     * @return the output sequence.
     * @throws XPathException if a dynamic error occurs
     */

    /*@Nullable*/
    public SequenceIterator map(Item item) throws XPathException {
        return lambda.map(item);
    }

}

