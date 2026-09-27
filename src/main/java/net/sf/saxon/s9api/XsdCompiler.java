////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.SchemaURIResolver;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.type.Schema;

import javax.xml.transform.Source;
import java.io.File;

/**
 * The {@code XsdCompiler} is used to load schema documents, and to set options for the way in
 * which they are loaded.
 *
 * <p><i>The {@code XsdCompiler} is new in Saxon 13; it replaces the class {@link SchemaManager}
 * which is retained (but deprecated) to provide a degree of backwards compatibility.</i></p>
 *
 * <p>A schema, represented by an {@link XsdSchema} object, is a collection of schema components,
 * which may come from one schema document or from a collection of schema documents.
 * An {@link XsdCompiler} is used to create {@link XsdSchema} objects from source
 * schema documents.</p>
 *
 * <p>Schema processing requires Saxon-EE. The abstract class {@code XsdCompiler} exists in Saxon-HE
 * and Saxon-PE, but is never instantiated.
 * In Saxon-EE the subclass {@code XsdCompilerEE} is instantiated to do the heavy lifting, but this
 * is not normally exposed to users.</p>
 *
 * <p>An {@code XsdCompiler} is created using the factory method {@link Processor#newXsdCompiler}. The normal pattern
 * of usage is to set options, then call {@link #compile(File...)} or {@link #compile(Source...)} to compile
 * one or more schema documents into a schema. The schema can be used to validate instance documents using
 * the method {@link XsdSchema#newValidator()}, and it can be added to the static context of XQuery queries
 * and XSLT stylesheets using the methods {@link XQueryCompiler#useSchema(XsdSchema)} and
 * {@link XsltCompiler#useSchema(String, XsdSchema)}.</p>
 *
 * @since 13.0
 */

//@CSharpModifiers(code = {"internal"})
public abstract class XsdCompiler {

    /**
     * Get the associated s9api {@link Processor}
     *
     * @return the {@link Processor}
     */

    @CSharpModifiers(code = {"internal", "abstract"})
    public abstract Processor getProcessor();

    /**
     * Set the version of XSD in use for this schema. The value must be "1.0" or "1.1". The default is taken
     * from the configuration property {@link Feature#XSD_VERSION} at the time the
     * {@code XsdSchemaCompiler} is instantiated. By default this is "1.1".
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
     * Set the ErrorReporter to be used while loading and validating schema documents
     *
     * @param reporter The error reporter to be used. This is notified of all errors detected during the
     *                 compilation. May be set to null to revert to the default ErrorReporter.
     */
    public abstract void setErrorReporter(ErrorReporter reporter);

    /**
     * Get the ErrorReporter being used while loading and validating schema documents
     *
     * @return reporter The error reporter in use. This is notified of all errors detected during the
     * compilation. Returns null if no user-supplied ErrorReporter has been set.
     */

    public abstract ErrorReporter getErrorReporter();

    /**
     * Set the SchemaURIResolver to be used during schema loading. This SchemaURIResolver, despite its name,
     * is <b>not</b> used for resolving relative URIs against a base URI; it is used for dereferencing
     * an absolute URI (after resolution) to return a {@link Source} representing the
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
     * document are retained by this SchemaManager as the current schema, and are also returned
     * by the method as an {@link XsdSchema} object.
     *
     * <p>This method can only be called once on a given {@code SchemaManager} object.</p>
     *
     * <p>If the source is a {@code StreamSource} or a {@code SAXSource} with no user-supplied {@code XmlReader},
     * then Saxon will allocate and configure an {@code XmlReader}. This will NOT take account of any configuration
     * level parser settings (such as DTD validation, XInclude processing, and so on). The recommended way to customise
     * parsing options is by creating an {@code XmlReader} and supplying this as a property of a {@code SAXSource}. For
     * options when parsing included or imported stylesheet modules, it is possible to do the same in the
     * {@link SchemaURIResolver} set using the {@link #setSchemaURIResolver(SchemaURIResolver)} method.</p>
     *
     * @param source the documents containing the schema. The getSystemId() method applied to each Source
     *               must return a base URI suitable for resolving <code>xs:include</code> and <code>xs:import</code>
     *               directives. The document may be either a schema document in source XSD format, or a compiled
     *               schema in Saxon-defined SCM format.
     * @return the resulting schema.
     * @throws SaxonApiException if any of the schema documents is not valid, or if there are conflicts
     * between the schema documents.
     */

    @CSharpModifiers(code={"internal", "abstract"})
    public abstract XsdSchema compile(Source... source) throws SaxonApiException;

    /**
     * Load a schema document from a given File or set of files. The schema components derived from this schema
     * document are retained by this SchemaManager as the current schema, and are also returned
     * by the method as an {@link XsdSchema} object.
     *
     * @param file the document containing the schema. The getSystemId() method applied to this Source
     *               must return a base URI suitable for resolving <code>xs:include</code> and <code>xs:import</code>
     *               directives. The document may be either a schema document in source XSD format, or a compiled
     *               schema in Saxon-defined SCM format (as produced using the -export option)
     * @return the resulting schema.
     * @throws SaxonApiException if the schema document is not valid.
     * @since 12.0
     */

    @CSharpModifiers(code = {"internal", "abstract"})
    public abstract XsdSchema compile(File... file) throws SaxonApiException;

    /**
     * Combine a set of schemas into one
     * @param schemas the input schemas. These are not modified.
     * @return the combined schema
     * @throws SaxonApiException if the schemas are not compatible, for example if they contain
     * duplicate names.
     * @since 13.0
     */

    @CSharpModifiers(code = {"internal", "abstract"})
    public abstract XsdSchema combine(XsdSchema... schemas) throws SaxonApiException;

    /**
     * Return an empty schema. This contains only the built-in schema components
     * This may be useful for validating instance documents when all
     * the actual schema definitions are linked from the instance document using {@code xsi:schemaLocation}
     * and {@code xsi:noNamespaceSchemaLocation} attributes. It can also be used directly, for example, to
     * validate elements that have an xsi:type attribute naming a built-in atomic
     * type such as xs:ID.
     * @return a minimal schema containing only the built-in schema components
     */

    @CSharpModifiers(code = {"internal", "abstract"})
    public abstract XsdSchema emptySchema();

    /**
     * Import a precompiled Schema Component Model from a given Source. The result is delivered as an
     * <code>XsdSchema</code> object.
     *
     * @param source the XML file containing the schema component model, as generated by a previous call on
     *               {@link XsdSchema#exportComponents}
     * @return the schema produced by processing the supplied schema component model export file
     */


    @CSharpModifiers(code = {"internal", "abstract"})
    public abstract XsdSchema importComponents(Source source) throws SaxonApiException;

    /**
     * Construct an {@link XsdSchema} by wrapping an internal schema object. This is intended primarily
     * for internal use
     *
     * @param internalSchema the internal schema object
     */

    @CSharpModifiers(code = {"internal", "abstract"})
    public abstract XsdSchema wrapInternalSchema(Schema internalSchema);

}

