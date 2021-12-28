////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.lib;

import net.sf.saxon.query.Annotation;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Affinity;

/**
 * Interface to a user-supplied class that handles XQuery annotation assertions in a particular namespace.
 * The interface provides a callback method used to determine whether the set of annotations on a given user
 * defined function satisfies a particular annotation assertion defined in a function item test (for example,
 * the required function type for an argument of a higher-order function)
 */

public interface FunctionAnnotationHandler {

    /**
     * Get the namespace handled by this function annotation handler. This handler will only be called in respect
     * of annotation assertions that are in this namespace. Use the empty string to refer to the null namespace
     * @return the namespace handled by this function annotation handler.
     */

    String getAssertionNamespace();

    /**
     * Test whether a given set of annotations in this namespace is valid.
     * @param annotations the annotation list (filtered by namespace: all the annotations
     *                    will have names in the namespace for this annotation handler)
     * @param construct the construct on which this list of annotations appears. One of "DF" (declare function),
     *                  "DV" (declare variable), "IF" (inline function), "FT" (function test)
     * @throws XPathException if the annotation is invalid, or if it is inconsistent with the annotations
     * in the existing list.
     */

    void check(AnnotationList annotations, String construct) throws XPathException;

    /**
     * Test whether a function with a given list of annotations satisfies an annotation assertion present
     * on a function item test.
     * @param assertion the annotation assertion present in the function item test
     * @param annotationList the annotations present on the function being tested, filtered by namespace
     * @return true if the assertion is satisfied, false if not
     */

    boolean satisfiesAssertion(Annotation assertion, AnnotationList annotationList);

    /**
     * Test the relationship of one list of annotation assertions to another list of annotation assertions,
     * all from this namespace.
     *
     * <p>The lists will always be non-empty, because it is assumed that a test with an empty list of
     * assertions always subsumes a test with a non-empty list.</p>
     * @param firstList the first list of annotation assertions
     * @param secondList the second list of annotation assertions
     * @return the relationship between the two lists, as one of the values {@link net.sf.saxon.type.Affinity#SAME_TYPE},
     * {@link net.sf.saxon.type.Affinity#DISJOINT}, {@link net.sf.saxon.type.Affinity#OVERLAPS},
     * {@link net.sf.saxon.type.Affinity#SUBSUMES}, {@link net.sf.saxon.type.Affinity#SUBSUMED_BY}.
     * For example, if the first list is <code>%colour("blue")</code> and the second list is <code>%colour("green")</code>,
     * and no function can be both blue and green, then return <code>DISJOINT</code>. But if a function can have more than
     * one colour, return <code>OVERLAPS</code> because the set of functions conforming to the two assertions has a non-empty
     * intersection. If the first list is <code>%colour("any")</code> and the second list is <code>%colour("blue")</code>,
     * then return <code>SUBSUMES</code>, because the set of functions satisfying the first assertion is a superset of those
     * satisfying the second assertion.
     * <p>The result of this method must be consistent with the {@link #satisfiesAssertion(Annotation, AnnotationList)} method.
     * For example, if this method indicates that <code>%big</code> subsumes <code>%huge</code>, then it must indeed
     * be the case that the set of functions that satisfy the assertion <code>%big</code> is a superset of those that
     * satisfy the assertion <code>%huge</code>.</p>
     * <p>If in doubt, it is always safe to return <code>OVERLAPS</code>: the worst that can happen is that type-checking is deferred
     * until run-time.</p>
     */

    Affinity relationship(AnnotationList firstList, AnnotationList secondList);



}

