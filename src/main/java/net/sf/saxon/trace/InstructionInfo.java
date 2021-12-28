////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.StructuredQName;

import java.util.Iterator;


/**
 * Information about an instruction in the stylesheet or a construct in a Query, made
 * available at run-time to a TraceListener. Although the class is mainly used to provide
 * information for diagnostics, it also has a role in detecting circularities among
 * global variables.
 */

public interface InstructionInfo extends Location {

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     * @return the QName of the object declared or manipulated by this instruction or expression
     */

    /*@Nullable*/
    StructuredQName getObjectName();

    /**
     * Get the value of a particular property of the instruction. Properties
     * of XSLT instructions are generally known by the name of the stylesheet attribute
     * that defines them.
     *
     * @param name The name of the required property
     * @return The value of the requested property, or null if the property is not available
     */

    /*@Nullable*/
    Object getProperty(String name);

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property. The iterator may return properties whose
     * value is null.
     *
     * @return an iterator over the properties.
     */

    Iterator<String> getProperties();

}

