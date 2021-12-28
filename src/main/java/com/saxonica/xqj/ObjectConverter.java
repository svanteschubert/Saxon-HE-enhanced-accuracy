////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import net.sf.saxon.om.Item;

import javax.xml.xquery.XQException;
import javax.xml.xquery.XQItemAccessor;
import javax.xml.xquery.XQItemType;

/**
 * This interface is based on the "CommonHandler" concept defined in early drafts of XQJ. It defines the data
 * conversion routines used by the Saxon XQJ implementation to convert between native Java objects and XDM values.
 * Most applications will use the Saxon-supplied implementation {@link StandardObjectConverter}, but it is possible
 * to supply an alternative implementation using the method {@link SaxonXQDataFactory#setObjectConverter}
 */
public interface ObjectConverter {

    /**
     * Convert an Item to a Java object
     *
     * @param xqItemAccessor the XQJ object representing the item to be converted
     * @return the Java object that results from the conversion
     * @throws XQException
     */

    Object toObject(XQItemAccessor xqItemAccessor) throws XQException;

    /**
     * Convert a Java object to an Item, when no information is available about the required type
     *
     * @param value the supplied Java object. If null is supplied, null is returned.
     * @return the Item that results from the conversion
     * @throws XQException if the Java object cannot be converted to an XQItem
     */

    Item convertToItem(Object value) throws XQException;

    /**
     * Convert a Java object to an Item, when a required type has been specified. Note that Saxon only calls
     * this method when none of the standard conversions defined in the XQJ specification is able to handle
     * the object.
     *
     * @param value the supplied Java object. If null is supplied, null is returned.
     * @param type  the required XPath data type
     * @return the Item that results from the conversion
     * @throws XQException if the Java object cannot be converted to an XQItem
     */

    public Item convertToItem(Object value, XQItemType type) throws XQException;
}

