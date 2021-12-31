////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Source;


/**
 * A SchemaURIResolver is used when resolving references to
 * schema documents. It takes as input the target namespace of the schema to be loaded, and a set of
 * location hints as input, and returns one or more Source objects containing the schema documents
 * to be imported.
 */

public interface SchemaURIResolver {

    /**
     * Set the configuration information for use by the resolver
     *
     * @param config the Saxon Configuration (which will always be an {@link com.saxonica.config.EnterpriseConfiguration})
     */

    void setConfiguration(Configuration config);

    /**
     * Resolve a URI identifying a schema document, given the target namespace URI and
     * a set of associated location hints.
     *
     * @param targetNamespace the target namespaces of the schema to be imported. The "null namesapce"
     *                        is identified by a zero-length string. In the case of an xsd:include directive, where no
     *                        target namespace is specified, the parameter is null.
     * @param baseURI         The base URI of the module containing the "import schema" declaration;
     *                        null if no base URI is known
     * @param locations       The set of URIs identified as schema location hints. In most cases (xs:include, xs:import,
     *                        xsi:schemaLocation, xsl:import-schema) there is only one URI in this list. With an XQuery "import module"
     *                        declaration, however, a list of URIs may be specified.
     * @return an array of Source objects each identifying a schema document to be loaded.
     *         These need not necessarily correspond one-to-one with the location hints provided.
     *         Returning a zero-length array indictates that no schema documents should be loaded (perhaps because
     *         the relevant schema components are already present in the schema cache).
     *         The method may also return null to indicate that resolution should be delegated to the standard
     *         (Saxon-supplied) SchemaURIResolver.
     * @throws net.sf.saxon.trans.XPathException
     *          if the module cannot be located, and if delegation to the default
     *          module resolver is not required.
     */

    Source[] resolve(String targetNamespace, String baseURI, String[] locations) throws XPathException;


}

