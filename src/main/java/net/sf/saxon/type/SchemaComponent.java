////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.om.FunctionItem;

/**
 * This is a marker interface that represents any "schema component" as defined in the XML Schema
 * specification. This may be a user-defined schema component or a built-in schema component. Since
 * all built-in schema components are types, every SchemaComponent in practice is either a
 * {@link com.saxonica.ee.schema.UserSchemaComponent} or a {@link SchemaType} or both.
 */
public interface SchemaComponent {

    /**
     * Get the validation status of this component.
     *
     * @return one of the values {@link SchemaValidationStatus#UNVALIDATED}, {@link SchemaValidationStatus#VALIDATING},
     *         {@link SchemaValidationStatus#VALIDATED}, {@link SchemaValidationStatus#INVALID}, {@link SchemaValidationStatus#INCOMPLETE}
     */

    SchemaValidationStatus getValidationStatus();

    /**
     * Get the redefinition level. This is zero for a component that has not been redefined;
     * for a redefinition of a level-0 component, it is 1; for a redefinition of a level-N
     * component, it is N+1. This concept is used to support the notion of "pervasive" redefinition:
     * if a component is redefined at several levels, the top level wins, but it is an error to have
     * two versions of the component at the same redefinition level.
     *
     * @return the redefinition level
     */

    int getRedefinitionLevel();



}

