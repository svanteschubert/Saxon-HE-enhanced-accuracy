// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

//import com.saxonica.ee.schema.SchemaCompiler;

import javax.xml.transform.Source;

/**
 * A PrecompiledSchemaSource encapsulates a {@link Schema}; it implements the {@link javax.xml.transform.Source}
 * interface purely so that it can be included in a list of sources used to assemble a schema in the
 * {@link SchemaCompiler#loadSources(Iterable)} method. In general, it is not accepted by other Saxon interfaces
 * that expect a Source object.
 */

public class PrecompiledSchemaSource implements Source {

    private final Schema schema;
    public PrecompiledSchemaSource(Schema schema) {
        this.schema = schema;
    }

    public Schema getSchema() {
        return schema;
    }

    /**
     * Set the system identifier for this Source.
     *
     * <p>The system identifier is optional if the source does not
     * get its data from a URL, but it may still be useful to provide one.
     * The application can use a system identifier, for example, to resolve
     * relative URIs and to include in error messages and warnings.</p>
     *
     * @param systemId The system identifier as a URL string.
     */
    @Override
    public void setSystemId(String systemId) {
        throw new UnsupportedOperationException();
    }

    /**
     * Get the system identifier that was set with setSystemId.
     *
     * @return The system identifier that was set with setSystemId, or null
     * if setSystemId was not called.
     */
    @Override
    public String getSystemId() {
        return null;
    }
}

