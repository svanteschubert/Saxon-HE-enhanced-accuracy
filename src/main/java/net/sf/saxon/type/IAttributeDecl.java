////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.StructuredQName;

/**
 * This is a marker interface that acts as a surrogate for an object representing
 * a global attribute declaration.
 * The real implementation of these declarations is available in the schema-aware
 * version of the Saxon product.
 */
public interface IAttributeDecl {

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
     * Get the simple type associated with the  attribute declaration
     *
     * @return the simple type
     */

    SimpleType getType() throws MissingComponentException;

}

