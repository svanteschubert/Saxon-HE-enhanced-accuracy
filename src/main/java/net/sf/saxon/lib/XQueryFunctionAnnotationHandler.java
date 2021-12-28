////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.lib;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.Annotation;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Affinity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Function annotation handler for annotations in the built-in namespace
 * http://www.w3.org/2012/xquery. This defines the annotations %public and %private,
 * and the (XQuery Update 3.0) annotations %updating and %simple
 */

public class XQueryFunctionAnnotationHandler implements FunctionAnnotationHandler {

    private static class DisallowedCombination {
        public DisallowedCombination(StructuredQName one, StructuredQName two, String errorCode, String... where) {
            this.one = one;
            this.two = two;
            this.errorCode = errorCode;
            this.where = new HashSet<String>(where.length);
            Collections.addAll(this.where, where);
        }

        public StructuredQName one;
        public StructuredQName two;
        public String errorCode;
        public Set<String> where;
    }

    private static DisallowedCombination[] blackList = {
            new DisallowedCombination(Annotation.SIMPLE, null, "XUST0032", "DV"),
            new DisallowedCombination(Annotation.UPDATING, null, "XUST0032", "DV"),
            new DisallowedCombination(Annotation.PUBLIC, null, "XQST0125", "IF"),
            new DisallowedCombination(Annotation.PRIVATE, null, "XQST0125", "IF"),
            new DisallowedCombination(Annotation.PRIVATE, Annotation.PRIVATE, "XQST0106", "DF"),
            new DisallowedCombination(Annotation.PRIVATE, Annotation.PUBLIC, "XQST0106", "DF"),
            new DisallowedCombination(Annotation.PUBLIC, Annotation.PUBLIC, "XQST0106", "DF"),
            new DisallowedCombination(Annotation.PUBLIC, Annotation.PRIVATE, "XQST0106", "DF"),
            new DisallowedCombination(Annotation.PRIVATE, Annotation.PRIVATE, "XQST0116", "DV"),
            new DisallowedCombination(Annotation.PRIVATE, Annotation.PUBLIC, "XQST0116", "DV"),
            new DisallowedCombination(Annotation.PUBLIC, Annotation.PUBLIC, "XQST0116", "DV"),
            new DisallowedCombination(Annotation.PUBLIC, Annotation.PRIVATE, "XQST0116", "DV"),
            new DisallowedCombination(Annotation.UPDATING, Annotation.UPDATING, "XUST0033", "DF", "IF"),
            new DisallowedCombination(Annotation.UPDATING, Annotation.SIMPLE, "XUST0033", "DF", "IF"),
            new DisallowedCombination(Annotation.SIMPLE, Annotation.SIMPLE, "XUST0033", "DF", "IF"),
            new DisallowedCombination(Annotation.SIMPLE, Annotation.UPDATING, "XUST0033", "DF", "IF"),
    };

    public XQueryFunctionAnnotationHandler() {

    }

    @Override
    public void check(AnnotationList annotations, String construct) throws XPathException {
        for (int i = 0; i < annotations.size(); i++) {
            Annotation ann = annotations.get(i);
            for (DisallowedCombination dc : blackList) {
                if (dc.one.equals(ann.getAnnotationQName()) && dc.where.contains(construct)) {
                    if (dc.two == null) {
                        throw new XPathException("Annotation %" + ann.getAnnotationQName().getLocalPart() + " is not allowed here",
                                                 dc.errorCode);
                    } else {
                        for (int j = 0; j < i; j++) {
                            Annotation other = annotations.get(j);
                            if (dc.two.equals(other.getAnnotationQName())) {
                                if (dc.two.equals(ann.getAnnotationQName())) {
                                    throw new XPathException("Annotation %" + ann.getAnnotationQName().getLocalPart() +
                                                                     " cannot appear more than once", dc.errorCode);
                                } else {
                                    throw new XPathException("Annotations %" + ann.getAnnotationQName().getLocalPart() +
                                                                     " and " + other.getAnnotationQName().getLocalPart() + " cannot appear together",
                                                             dc.errorCode);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the namespace handled by this function annotation handler. This handler will only be called in respect
     * of annotation assertions that are in this namespace. Use the empty string to refer to the null namespace
     *
     * @return the namespace handled by this function annotation handler, namely "http://www.w3.org/2012/xquery"
     */

    @Override
    public String getAssertionNamespace() {
        return "http://www.w3.org/2012/xquery";
    };

    /**
     * Test whether a function with a given list of annotations satisfies an annotation assertion present
     * on a function item test.
     *
     * @param assertion      the annotation assertion present in the function item test
     * @param annotationList the annotations present on the function being tested
     * @return true if the assertion is satisfied, false if not
     */

    @Override
    public boolean satisfiesAssertion(Annotation assertion, AnnotationList annotationList) {
        // annotation assertions are not defined for this namespace (surprisingly)
        return true;
    }

    /**
     * Test the relationship of one list of annotation assertions to another list of annotation assertions,
     * all from this namespace
     * <p>The lists will always be non-empty, because it is assumed that a test with an empty list of
     * assertions always subsumes a test with a non-empty list.</p>
     *
     * @param firstList  the first list of annotation assertions
     * @param secondList the second list of annotation assertions
     * @return the relationship between the two lists, as one of the values {@link Affinity#SAME_TYPE},
     * {@link Affinity#DISJOINT}, {@link Affinity#OVERLAPS},
     * {@link Affinity#SUBSUMES}, {@link Affinity#SUBSUMED_BY}.
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

    @Override
    public Affinity relationship(AnnotationList firstList, AnnotationList secondList) {
        return Affinity.OVERLAPS;
    }


}

