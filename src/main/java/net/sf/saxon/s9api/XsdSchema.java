// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.type.Schema;

/**
 * This class represents an XSD Schema - that is, a set of schema components, typically resulting
 * from compiling a collection of one or more source schema documents.
 *
 * <p>The class exists in all Saxon editions as an abstract place-holder class, but concrete
 * implementations exist only in Saxon-EE.</p>
 */

@CSharpModifiers(code={"internal", "abstract"})
public abstract class XsdSchema {

    /**
     * Create a {@link SchemaValidator} which can be used to validate instance documents against this schema
     * @return a new {@code SchemaValidator}
     * @since 13.0
     */

    public abstract SchemaValidator newValidator();

    /**
     * Create an {@link ItemTypeFactory} which can be used to create {@link ItemType}
     * objects based on the definitions in this schema.
     * @return an {@code ItemTypeFactory} linked to this schema.
     */

    public abstract ItemTypeFactory newItemTypeFactory();
    /**
     * Combine this schema with another, to form a new combined schema.
     * <p>Note: in relatively unusual circumstances, a document that is valid against one of the two input schemas
     * might not be valid against the combined schema. For example, if the first schema has a wildcard with
     * <code>processContents="lax"</code> then an element might be laxly validated when assessed using that schema,
     * but strictly validated when assessed against the combined schema; and strict validation may fail while
     * lax validation succeeded.</p>
     * @param other the other schema to combine with this one.
     * @return the combined schema, assuming the two schemas are compatible
     * @throws SaxonApiException if the two schemas cannot be combined, for example because they
     * contain components with duplicated names
     * @since 13.0
     */

    public abstract XsdSchema combine(XsdSchema other) throws SaxonApiException;

    /**
     * Get the underlying implementation-level Schema
     */

    public abstract Schema getUnderlyingSchema();

    /**
     * Export a precompiled Schema Component Model containing all the components (except built-in components)
     * in this schema.
     *
     * @param destination the destination to recieve the precompiled Schema Component Model in the form of an
     *                    XML document
     * @throws SaxonApiException if a failure occurs writing the schema components to the supplied destination
     */

    public abstract void exportComponents(Destination destination) throws SaxonApiException;

}

