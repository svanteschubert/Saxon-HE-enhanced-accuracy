////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.z;

/**
 * This interface represents an integer predicate (that is, a boolean function of an integer).
 * It is provided for the purpose of C# transpilation: in Java, an IntPredicate can be implemented
 * either as a class implementing {@link java.util.function.IntPredicate}, or as a lambda expression,
 * but this duality is not available in C#. A class that implements {@link java.util.function.IntPredicate}
 * should therefore implement this extension of the interface, so that it can be transpiled to C#.
 */

public interface IntPredicateProxy
        extends java.util.function.IntPredicate
{
    boolean test(int value);

    default IntPredicateProxy union(IntPredicateProxy other) {
        return IntPredicateLambda.of(val -> test(val) || other.test(val));
    }

}

