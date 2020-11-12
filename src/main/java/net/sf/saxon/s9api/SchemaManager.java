////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.SchemaURIResolver;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;

/**
 * The SchemaManager is used to load schema documents, and to set options for the way in which they are loaded.
 * At present all the resulting schema components are held in a single pool owned by the Processor object.
 * <p>To obtain a <code>SchemaManager</code>, use the method {@link net.sf.saxon.s9api.Processor#getSchemaManager()}.</p>
 * <p>In a schema-aware Processor there is exactly one
 * <code>SchemaManager</code> (in a non-schema-aware Processor there is none).</p>
 * <p>The cache of compiled schema definitions can include only one schema
 * component (for example a type, or an element declaration) with any given name.
 * An attempt to compile two different schemas in the same namespace will usually
 * therefore fail.</p>
 * <p>As soon as a type definition or element declaration is used for the first
 * time in a validation episode, it is marked as being "sealed": this prevents subsequent
 * modifications to the component. Examples of modifications that are thereby disallowed
 * include adding to the substitution group of an existing element declaration, adding subtypes
 * to an existing type, or redefining components using &lt;xs:redefine&gt;</p>
 */
public abstract class SchemaManager {

    public SchemaManager() {
    }

    /**
     * Set the version of XSD in use. The value must be "1.0" or "1.1". The default is currently "1.0",
     * but this may change in a future release.
     *
     * @param version the version of the XSD specification/language: either "1.0" or "1.1".
     */

    public abstract void setXsdVersion(String version);

    /**
     * Get the version of XSD in use. The value will be "1.0" or "1.1"
     *
     * @return the version of XSD in use.
     */

    public abstract String getXsdVersion();

    /**
     * Set the ErrorListener to be used while loading and validating schema documents
     *
     * @param listener The error listener to be used. This is notified of all errors detected during the
     *                 compilation. May be set to null to revert to the default ErrorListener.
     * @deprecated since 10.0. Use {@link #setErrorReporter(ErrorReporter)}.
     */

    public abstract void setErrorListener(/*@Nullable*/ ErrorListener listener);

    /**
     * Get the ErrorListener being used while loading and validating schema documents
     *
     * @return listener The error listener in use. This is notified of all errors detected during the
     *         compilation. Returns null if no user-supplied ErrorListener has been set.
     * @deprecated since 10.0. Use {@link #getErrorReporter}
     */

    /*@Nullable*/
    public abstract ErrorListener getErrorListener();

    public abstract void setErrorReporter(ErrorReporter reporter);

    public abstract ErrorReporter getErrorReporter();

    /**
     * Set the SchemaURIResolver to be used during schema loading. This SchemaURIResolver, despite its name,
     * is <b>not</b> used for resolving relative URIs against a base URI; it is used for dereferencing
     * an absolute URI (after resolution) to return a {@link javax.xml.transform.Source} representing the
     * location where a schema document can be found.
     * <p>This SchemaURIResolver is used to dereference the URIs appearing in <code>xs:import</code>,
     * <code>xs:include</code>, and <code>xs:redefine</code> declarations.</p>
     *
     * @param resolver the SchemaURIResolver to be used during schema loading.
     */

    public abstract void setSchemaURIResolver(SchemaURIResolver resolver);

    /**
     * Get the SchemaURIResolver to be used during schema loading.
     *
     * @return the URIResolver used during stylesheet compilation. Returns null if no user-supplied
     *         URIResolver has been set.
     */

    public abstract SchemaURIResolver getSchemaURIResolver();

    /**
     * Load a schema document from a given Source. The schema components derived from this schema
     * document are added to the cache of schema components maintained by this SchemaManager
     *
     * @param source the document containing the schema. The getSystemId() method applied to this Source
     *               must return a base URI suitable for resolving <code>xs:include</code> and <code>xs:import</code>
     *               directives. The document may be either a schema document in source XSD format, or a compiled
     *               schema in Saxon-defined SCM format (as produced using the -export option)
     * @throws SaxonApiException if the schema document is not valid, or if its contents are inconsistent
     *                           with the schema components already held by this SchemaManager.
     */

    public abstract void load(Source source) throws SaxonApiException;

    /**
     * Import a precompiled Schema Component Model from a given Source. The schema components derived from this schema
     * document are added to the cache of schema components maintained by this SchemaManager
     *
     * @param source the XML file containing the schema component model, as generated by a previous call on
     *               {@link #exportComponents}
     * @throws SaxonApiException if a failure occurs loading the schema from the supplied source
     */

    public abstract void importComponents(Source source) throws SaxonApiException;

    /**
     * Export a precompiled Schema Component Model containing all the components (except built-in components)
     * that have been loaded into this Processor.
     *
     * @param destination the destination to recieve the precompiled Schema Component Model in the form of an
     *                    XML document
     * @throws SaxonApiException if a failure occurs writing the schema components to the supplied destination
     */

    public abstract void exportComponents(Destination destination) throws SaxonApiException;

    /**
     * Create a SchemaValidator which can be used to validate instance documents against the schema held by this
     * SchemaManager
     *
     * @return a new SchemaValidator
     */

    public abstract SchemaValidator newSchemaValidator();


}

