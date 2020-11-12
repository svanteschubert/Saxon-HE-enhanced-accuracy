////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.type;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.FunctionAnnotationHandler;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.Item;
import net.sf.saxon.query.Annotation;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.trans.XPathException;

/**
 * The item type function(*) when it is preceded by one or more annotation assertions
 */
public class AnyFunctionTypeWithAssertions extends AnyFunctionType {

    private AnnotationList assertions;
    private Configuration config;

    /**
     * Construct an item type representing the item type function(*) with a list of annotation assertions
     * @param assertions the annotation assertions
     */

    public AnyFunctionTypeWithAssertions(AnnotationList assertions, Configuration config) {
        this.assertions = assertions;
        this.config = config;
    }

    /**
     * Get the list of annotation assertions defined on this function item type.
     *
     * @return the list of annotation assertions, or an empty list if there are none
     */

    @Override
    public AnnotationList getAnnotationAssertions() {
        return assertions;
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @param th the type hierarchy cache
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item, TypeHierarchy th) throws XPathException {
        return item instanceof Function && checkAnnotationAssertions(assertions, (Function) item, th.getConfiguration());
    }

    private static boolean checkAnnotationAssertions(AnnotationList assertions, Function item, Configuration config) {
        AnnotationList annotations = item.getAnnotations();
        for (Annotation ann : assertions) {
            FunctionAnnotationHandler handler = config.getFunctionAnnotationHandler(ann.getAnnotationQName().getURI());
            if (handler != null) {
                boolean ok = handler.satisfiesAssertion(ann, annotations);
                if (!ok) {
                    return false;
                }
            }
        }
        return true;
    }
}

