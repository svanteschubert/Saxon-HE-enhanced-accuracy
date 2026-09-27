////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.SchemaURIResolver;
import net.sf.saxon.transpile.CSharpModifiers;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.File;

/**
 * The {@code SchemaManager} is used to load schema documents, and to set options for the way in which they are loaded.
 *
 * <p>The {@code SchemaManager} is deprecated from Saxon 13, and an {@link XsdCompiler} should be used instead.
 * The class is retained to provide a measure of backwards compatibility, but some changes may be needed. In particular:</p>
 *
 * <ul>
 *     <li>The schema must now be loaded (using {@link #load(File)}, {@link #load(Source...)},
 *     or {@link #importComponents(Source)}) before calling {@link #newSchemaValidator()} to
 *     construct a validator.</li>
 *     <li>The schema must be loaded in a single call of {@link #load(File)}, {@link #load(Source...)},
 *     or {@link #importComponents(Source)}; it can no longer be built incrementally.</li>
 *     <li>The schema is made available automatically to any {@link XsltCompiler}, {@link XQueryCompiler},
 *     or {@link XPathCompiler} that is created after the schema is loaded, but it has no effect on
 *     any pre-existing compilers.</li>
 *     <li></li>
 * </ul>
 *
 * <p><i>A note on terminology: a <b>schema</b> is a collection of schema components, such as type definitions,
 * constructed by compiling a set of <b>schema documents</b>.</i></p>

 * <p>In earlier Saxon releases the schema built using the {@code SchemaManager} was held globally at the level
 * of the {@link Processor} or {@link Configuration}, and new schema components were also added to this schema
 * as a side effect of other actions that load schemas, for example <code>xsl:import-schema</code> in XSLT,
 * <code>import schema</code> declarations in XQuery, and <code>xsi:schemaLocation</code> attributes in source
 * documents undergoing validation. This is no longer the case. The schema built using this class is not modified when
 * queries and stylesheets import additional schema documents.</p>
 *
 * <p>The {@code SchemaManager} is obtained using the call {@link Processor#getSchemaManager()}. Repeated calls
 * deliver the same {@code SchemaManager} each time. The {@code SchemaManager} is not thread-safe; in particular,
 * calls on {@link #load(Source...)} should not be made in multiple threads concurrently.</p>
 *
 * @deprecated since Saxon 13.0 - use {@link XsdCompiler}
 */

@CSharpModifiers(code = {"abstract", "internal"})
@Deprecated
public abstract class SchemaManager {

    protected boolean hasLoadedSchema;

    public SchemaManager() {
    }

    /**
     * Set the version of XSD in use for this schema. The value must be "1.0" or "1.1". The default is currently "1.0",
     * but this may change in a future release.
     *
     * @param version the version of the XSD specification/language: either "1.0" or "1.1".
     */

    public abstract void setXsdVersion(String version);

    /**
     * Get the version of XSD in use for this schema. The value will be "1.0" or "1.1"
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

    @Deprecated
    public abstract void setErrorListener(/*@Nullable*/ ErrorListener listener);

    /**
     * Get the ErrorListener being used while loading and validating schema documents
     *
     * @return listener The error listener in use. This is notified of all errors detected during the
     *         compilation. Returns null if no user-supplied ErrorListener has been set.
     * @deprecated since 10.0. Use {@link #getErrorReporter}
     */

    /*@Nullable*/
    @Deprecated
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
     * Load a schema document from a given {@code Source}, or from a number of sources. The schema components derived from this schema
     * document are retained by this SchemaManager as the current schema, and are also returned
     * by the method as an {@link XsdSchema} object.
     *
     * <p>This method can only be called once on a given {@code SchemaManager} object.</p>
     *
     * @param source the document(s) containing the schema(s). The getSystemId() method applied to this Source
     *               must return a base URI suitable for resolving <code>xs:include</code> and <code>xs:import</code>
     *               directives. Each document may be either a schema document in source XSD format, or a compiled
     *               schema in Saxon-defined SCM format (as produced using the -export option). If no schema
     *               documents are supplied, an empty schema is loaded.
     * @return the resulting schema.
     * @throws SaxonApiException if the schema document is not valid.
     * @since 9.1. From Saxon 12.3, the method can only be called once; to load from multiple schema documents,
     * either supply multiple sources in a single call to the {@link #load(Source...)} method, or use a separate
     * {@code SchemaManager} for each one and combine the resulting schemas.
     */

    public abstract XsdSchema load(Source... source) throws SaxonApiException;

    /**
     * Load a schema document from a given File. The schema components derived from this schema
     * document are retained by this SchemaManager as the current schema, and are also returned
     * by the method as an {@link XsdSchema} object.
     *
     * <p>This method can only be called once on a given {@code SchemaManager} object.</p>
     *
     * @param file the document containing the schema. The getSystemId() method applied to this Source
     *               must return a base URI suitable for resolving <code>xs:include</code> and <code>xs:import</code>
     *               directives. The document may be either a schema document in source XSD format, or a compiled
     *               schema in Saxon-defined SCM format (as produced using the -export option)
     * @return the resulting schema.
     * @throws SaxonApiException if the schema document is not valid.
     * @since 12.0
     */

    public XsdSchema load(File file) throws SaxonApiException {
        return load(new StreamSource(file));
    }

    /**
     * Reset the schema manager to its initial state, in particular, set the accumulated schema to be empty.
     */
    public abstract void clear();

    public abstract XsdSchema getXsdSchema();

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
     * SchemaManager.
     *
     * <p>The result of calling <code>schemaManager.newSchemaValidator()</code> is the same as the result of calling
     * <code>schemaManager.getXsdSchema().newSchemaValidator()</code>.</p>
     *
     * @return a new SchemaValidator
     * @throws IllegalStateException if no schema has been loaded (using either {@link #load(Source...)}
     * or {@link #importComponents(Source)}). To validate against an empty schema,
     * first call {{@link #load(Source...)}} supplying no arguments.
     */

    public abstract SchemaValidator newSchemaValidator();


}

