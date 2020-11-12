////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.value.SequenceType;

import javax.xml.xquery.XQItemType;
import javax.xml.xquery.XQSequenceType;

/**
 * Saxon implementation of the XQJ SequenceType interface
 */


public class SaxonXQSequenceType implements XQSequenceType {

    SequenceType sequenceType;
    Configuration config;

    SaxonXQSequenceType(SequenceType sequenceType, Configuration config) {
        this.sequenceType = sequenceType;
        this.config = config;
    }

    @Override
    public int getItemOccurrence() {
        int cardinality = sequenceType.getCardinality();
        switch (cardinality) {
            case StaticProperty.EXACTLY_ONE:
                return XQSequenceType.OCC_EXACTLY_ONE;
            case StaticProperty.ALLOWS_ZERO_OR_ONE:
                return XQSequenceType.OCC_ZERO_OR_ONE;
            case StaticProperty.ALLOWS_ONE_OR_MORE:
                return XQSequenceType.OCC_ONE_OR_MORE;
            case StaticProperty.ALLOWS_ZERO_OR_MORE:
                return XQSequenceType.OCC_ZERO_OR_MORE;
            default:
                return XQSequenceType.OCC_ZERO_OR_MORE;
        }
    }

    /*@NotNull*/
    @Override
    public XQItemType getItemType() {
        return new SaxonXQItemType(sequenceType.getPrimaryType(), config);
    }

    /*@Nullable*/
    public String getString() {
        String s = sequenceType.getPrimaryType().toString();
        switch (sequenceType.getCardinality()) {
            case StaticProperty.EXACTLY_ONE:
                return s;
            case StaticProperty.ALLOWS_ZERO_OR_ONE:
                return s + "?";
            case StaticProperty.ALLOWS_ONE_OR_MORE:
                return s + "+";
            case StaticProperty.ALLOWS_ZERO_OR_MORE:
                return s + "*";
            default:
                return s;
        }
    }

    /*@Nullable*/
    public String toString() {
        return getString();
    }
}
