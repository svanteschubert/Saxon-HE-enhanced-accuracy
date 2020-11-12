////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeTest;

/**
 * This is a marker interface that acts as a surrogate for an object representing
 * a global element or attribute declaration.
 * The real implementation of these declarations is available in the schema-aware
 * version of the Saxon product.
 */
public interface SchemaDeclaration {

    /**
     * Get the name of the schema component
     *
     * @return the fingerprint of the component name
     */

    int getFingerprint();

    /**
     * Get the name of the schema component
     *
     * @return the component name as a structured QName
     */

    StructuredQName getComponentName();

    /**
     * Get the simple or complex type associated with the element or attribute declaration
     *
     * @return the simple or complex type
     */

    SchemaType getType() throws MissingComponentException;

    /**
     * Create a NodeTest that implements the semantics of schema-element(name) or
     * schema-attribute(name) applied to this element or attribute declaration.
     * @throws MissingComponentException if the type of the declaration is not present in the schema.
     */

    NodeTest makeSchemaNodeTest() throws MissingComponentException;

    /**
     * Determine, in the case of an Element Declaration, whether it is nillable.
     */

    boolean isNillable();

    /**
     * Determine, in the case of an Element Declaration, whether the declaration is abstract
     */

    boolean isAbstract();

    /**
     * Determine, in the case of an Element Declaration, whether there are type alternatives defined
     *
     * @return true if the element uses conditional type assignment
     */

    boolean hasTypeAlternatives();


}

