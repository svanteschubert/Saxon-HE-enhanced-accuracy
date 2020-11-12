////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.Locatable;
import net.sf.saxon.om.StructuredQName;

import java.util.function.BiConsumer;


/**
 * A construct whose execution can be notified to a TraceListener. In practice this is either
 * an expression, or a component such as a function or template or global variable.
 */

public interface Traceable extends Locatable {

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     * @return the QName of the object declared or manipulated by this instruction or expression
     */

    /*@Nullable*/
    StructuredQName getObjectName();

    /**
     * Get the properties of this object to be included in trace messages, by supplying
     * the property values to a supplied consumer function
     * @param consumer the function to which the properties should be supplied, as (property name,
     *                 value) pairs.
     */

    default void gatherProperties(BiConsumer<String, Object> consumer) {
    }



}

