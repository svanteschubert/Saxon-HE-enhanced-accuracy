////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.event.FilterFactory;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.TreeModel;
import net.sf.saxon.type.SchemaType;
import org.xml.sax.EntityResolver;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.util.List;

/**
 * This class is an extension of the JAXP Source interface. The class can be used
 * wherever a JAXP Source object can be used, and it provides additional information
 * about the way that the Source is to be processed: for example, it indicates
 * whether or not it should be validated against a schema. Other options that can
 * be set include the SAX XMLReader to be used, and the choice of whether a source
 * in the form of an existing tree should be copied or wrapped.
 * <p>Internally, an AugmentedSource combines an underlying Source object with a
 * {@link ParseOptions} object holding the selected options. Many Saxon interfaces allow
 * the ParseOptions to be supplied directly, making this class unnecessary; but it is useful
 * when passing a Source to a JAXP interface that does not allow further options to be
 * supplied.</p>
 * <p>Note that in general a <code>Source</code> object can only be used once; it is
 * consumed by use. An augmentedSource object is consumed by use if the underlying
 * source object is consumed by use.</p>
 *
 * @since 8.8
 */

public class AugmentedSource implements Source {

    private Source source;
    private ParseOptions options = new ParseOptions();
    private String systemID;

    /**
     * Create an AugmentedSource that wraps a given Source object (which must not itself be an
     * AugmentedSource)
     *
     * @param source the Source object to be wrapped. This must be an implementation of Source
     *               that Saxon recognizes, or an implementation for which a {@link net.sf.saxon.lib.SourceResolver} has been
     *               registered with the {@link net.sf.saxon.Configuration}. The source must not itself be an
     *               AugmentedSource.
     *               <p>As an alternative to this constructor, consider using the factory method
     *               {@link #makeAugmentedSource}, which does accept any kind of Source including an
     *               AugmentedSource as input.</p>
     * @throws IllegalArgumentException if the wrapped source is an AugmentedSource
     * @since 8.8
     */

    private AugmentedSource(Source source) {
        if (source instanceof AugmentedSource) {
            throw new IllegalArgumentException("Contained source must not be an AugmentedSource");
        }
        this.source = source;
    }

    /**
     * Create an AugmentedSource that wraps a given Source object (which must not itself be an
     * AugmentedSource), with supplied ParseOptions
     *
     * @param source the Source object to be wrapped. This must be an implementation of Source
     *               that Saxon recognizes, or an implementation for which a {@link net.sf.saxon.lib.SourceResolver} has been
     *               registered with the {@link net.sf.saxon.Configuration}. The source must not itself be an
     *               AugmentedSource.
     *               <p>As an alternative to this constructor, consider using the factory method
     *               {@link #makeAugmentedSource}, which does accept any kind of Source including an
     *               AugmentedSource as input.</p>
     * @param options the ParseOptions to be used. This object may be subsequently modified and the changes
     *                will be effective; furthermore, methods on the AugumentedSource will modify the
     *                supplied ParseOptions.
     * @throws IllegalArgumentException if the wrapped source is an AugmentedSource
     * @since 8.8
     */

    public AugmentedSource(Source source, ParseOptions options) {
        if (source instanceof AugmentedSource) {
            throw new IllegalArgumentException("Contained source must not be an AugmentedSource");
        }
        this.source = source;
        this.options = options;
    }

    /**
     * Create an AugmentedSource that wraps a given Source object. If this is already
     * an AugmentedSource, the original AugmentedSource is returned. Note that this means that
     * setting any properties on the returned AugmentedSource will also affect the original.
     *
     * @param source the Source object to be wrapped
     * @return an AugmentedSource
     * @since 8.8
     */

    public static AugmentedSource makeAugmentedSource(Source source) {
        if (source instanceof AugmentedSource) {
            return (AugmentedSource) source;
        }
        return new AugmentedSource(source);
    }

    /**
     * Add a filter to the list of filters to be applied to the raw input
     *
     * @param filter a factory for the filter to be added
     */

    public void addFilter(FilterFactory filter) {
        options.addFilter(filter);
    }

    /**
     * Get the list of filters to be applied to the input. Returns null if there are no filters.
     *
     * @return the list of filters, if there are any
     */

    public List<FilterFactory> getFilters() {
        return options.getFilters();
    }

    /**
     * Get the Source object wrapped by this AugmentedSource
     *
     * @return the contained Source object
     * @since 8.8
     */

    public Source getContainedSource() {
        return source;
    }

    /**
     * Get the ParseOptions defined in this AugmentedSource
     *
     * @return the ParseOptions, a bundle of options equivalent to obtaining all the
     *         properties individually. Changes to this object will be live, that is, they will
     *         affect the AugmentedSource from which they came.
     */

    public ParseOptions getParseOptions() {
        return options;
    }

    /**
     * Set the tree model to use. Default is the tiny tree
     *
     * @param model typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     *              {@link net.sf.saxon.om.TreeModel#TINY_TREE_CONDENSED}, or {@link net.sf.saxon.om.TreeModel#LINKED_TREE}. However, in principle
     *              a user-defined tree model can be used.
     * @since 9.2
     */

    public void setModel(TreeModel model) {
        options.setModel(model);
    }

    /**
     * Get the tree model that will be used.
     *
     * @return typically one of the constants {@link net.sf.saxon.om.TreeModel#TINY_TREE},
     *         {@link TreeModel#TINY_TREE_CONDENSED}, or {@link TreeModel#LINKED_TREE}. However, in principle
     *         a user-defined tree model can be used.
     * @since 9.2
     */

    public TreeModel getModel() {
        return options.getModel();
    }


    /**
     * Set whether or not schema validation of this source is required
     *
     * @param option one of {@link Validation#STRICT},
     *               {@link Validation#LAX}, {@link Validation#STRIP},
     *               {@link Validation#PRESERVE}, {@link Validation#DEFAULT}
     * @since 8.8
     */

    public void setSchemaValidationMode(int option) {
        options.setSchemaValidationMode(option);
    }

    /**
     * Get whether or not schema validation of this source is required
     *
     * @return the validation mode requested, or {@link Validation#DEFAULT}
     *         to use the default validation mode from the Configuration.
     * @since 8.8
     */

    public int getSchemaValidation() {
        return options.getSchemaValidationMode();
    }

    /**
     * Set the name of the top-level element for validation.
     * If a top-level element is set then the document
     * being validated must have this as its outermost element
     *
     * @param elementName the QName of the required top-level element, or null to unset the value
     * @since 9.0. Type of argument changed in 9.5.
     */

    public void setTopLevelElement(StructuredQName elementName) {
        options.setTopLevelElement(elementName);
    }

    /**
     * Get the name of the top-level element for validation.
     * If a top-level element is set then the document
     * being validated must have this as its outermost element
     *
     * @return the QName of the required top-level element, or null if no value is set
     * @since 9.0. Type of result changed in 9.5.
     */

    public StructuredQName getTopLevelElement() {
        return options.getTopLevelElement();
    }

    /**
     * Set the type of the top-level element for validation.
     * If this is set then the document element is validated against this type
     *
     * @param type the schema type required for the document element, or null to unset the value
     * @since 9.0
     */

    public void setTopLevelType(SchemaType type) {
        options.setTopLevelType(type);
    }

    /**
     * Get the type of the document element for validation.
     * If this is set then the document element of the document
     * being validated must have this type
     *
     * @return the type of the required top-level element, or null if no value is set
     * @since 9.0
     */

    public SchemaType getTopLevelType() {
        return options.getTopLevelType();
    }

    /**
     * Set whether or not DTD validation of this source is required
     *
     * @param option one of {@link Validation#STRICT},
     *               {@link Validation#STRIP}, {@link Validation#DEFAULT}
     * @since 8.8
     */

    public void setDTDValidationMode(int option) {
        options.setDTDValidationMode(option);
    }

    /**
     * Get whether or not DTD validation of this source is required
     *
     * @return the validation mode requested, or {@link Validation#DEFAULT}
     *         to use the default validation mode from the Configuration.
     * @since 8.8
     */

    public int getDTDValidation() {
        return options.getDTDValidationMode();
    }


    /**
     * Set whether line numbers are to be maintained in the constructed document
     *
     * @param lineNumbering true if line numbers are to be maintained
     * @since 8.8
     */

    public void setLineNumbering(boolean lineNumbering) {
        options.setLineNumbering(lineNumbering);
    }

    /**
     * Get whether line numbers are to be maintained in the constructed document
     *
     * @return true if line numbers are maintained
     * @since 8.8
     */

    public boolean isLineNumbering() {
        return options.isLineNumbering();
    }

    /**
     * Determine whether setLineNumbering() has been called
     *
     * @return true if setLineNumbering() has been called
     * @since 8.9
     */

    public boolean isLineNumberingSet() {
        return options.isLineNumberingSet();
    }

    /**
     * Set the SAX parser (XMLReader) to be used
     *
     * @param parser the SAX parser
     * @since 8.8
     */

    public void setXMLReader(XMLReader parser) {
        options.setXMLReader(parser);
        if (source instanceof SAXSource) {
            ((SAXSource) source).setXMLReader(parser);
        }
    }

    /**
     * Get the SAX parser (XMLReader) to be used
     *
     * @return the parser
     * @since 8.8
     */

    /*@Nullable*/
    public XMLReader getXMLReader() {
        XMLReader parser = options.getXMLReader();
        if (parser != null) {
            return parser;
        } else if (source instanceof SAXSource) {
            return ((SAXSource) source).getXMLReader();
        } else {
            return null;
        }
    }

    /**
     * Assuming that the contained Source is a node in a tree, indicate whether a tree should be created
     * as a view of this supplied tree, or as a copy.
     * <p>This option is used only when the Source is supplied to an interface such as the JAXP
     * Transformer.transform() method where there is no other way of indicating whether a supplied
     * external document should be wrapped or copied. It is not used when the Source is supplied to
     * a Saxon-defined interface.</p>
     *
     * @param wrap if true, the node in the supplied Source is wrapped, to create a view. If false, the node
     *             and its contained subtree is copied. If null, the system default is chosen.
     * @since 8.8
     */

    public void setWrapDocument(Boolean wrap) {
        options.setWrapDocument(wrap);
    }

    /**
     * Assuming that the contained Source is a node in a tree, determine whether a tree will be created
     * as a view of this supplied tree, or as a copy.
     * <p>This option is used only when the Source is supplied to an interface such as the JAXP
     * Transformer.transform() method where there is no other way of indicating whether a supplied
     * external document should be wrapped or copied. It is not used when the Source is supplied to
     * a Saxon-defined interface.</p>
     *
     * @return if true, the node in the supplied Source is wrapped, to create a view. If false, the node
     *         and its contained subtree is copied. If null, the system default is chosen.
     * @since 8.8
     */

    public Boolean getWrapDocument() {
        return options.getWrapDocument();
    }

    /**
     * Set the System ID. This sets the System Id to be returned by getSystemId.
     *
     * @param id the System ID. This provides a base URI for the document, and also the result
     *           of the document-uri() function
     * @since 8.8, changed in 9.7 it no longer modifies the original source object
     */

    @Override
    public void setSystemId(String id) {
        systemID = id;
    }

    /**
     * Get the System ID. Returns the System Id set by setSystemId if available,
     * or the System Id on the underlying Source object otherwise.
     *
     * @return the System ID: effectively the base URI.
     * @since 8.8
     */

    @Override
    public String getSystemId() {
        return systemID != null ? systemID : source.getSystemId();
    }

    /**
     * <p>Set state of XInclude processing.</p>
     * <p>If XInclude markup is found in the document instance, should it be
     * processed as specified in <a href="http://www.w3.org/TR/xinclude/">
     * XML Inclusions (XInclude) Version 1.0</a>.</p>
     * <p>XInclude processing defaults to <code>false</code>.</p>
     *
     * @param state Set XInclude processing to <code>true</code> or
     *              <code>false</code>
     * @since 8.9
     */
    public void setXIncludeAware(boolean state) {
        options.setXIncludeAware(state);
    }

    /**
     * <p>Determine whether setXIncludeAware() has been called.</p>
     *
     * @return true if setXIncludeAware() has been called
     * @since 8.9
     */

    public boolean isXIncludeAwareSet() {
        return options.isXIncludeAwareSet();
    }

    /**
     * <p>Get state of XInclude processing.</p>
     *
     * @return current state of XInclude processing. Default value is false.
     * @since 8.9
     */

    public boolean isXIncludeAware() {
        return options.isXIncludeAware();
    }

    /**
     * Set an EntityResolver to be used when parsing. Note this is used only
     * when a system-allocated parser is used; when a user-supplied parser
     * is used (for example in a SAXSource), the EntityResolver should be preinitialized.
     *
     * @param resolver the EntityResolver to be used
     * @since 8.9. The method had no useful effect in releases prior to 9.2.
     */

    public void setEntityResolver(EntityResolver resolver) {
        options.setEntityResolver(resolver);
    }

    /**
     * Get the EntityResolver that will be used when parsing
     *
     * @return the EntityResolver, if one has been set using {@link #setEntityResolver},
     *         otherwise null.
     * @since 8.9 The method had no useful effect in releases prior to 9.2.
     */

    public EntityResolver getEntityResolver() {
        return options.getEntityResolver();
    }

    /**
     * Set an ErrorReporter to be used when parsing
     *
     * @param listener the ErrorReporter to be used
     * @since 8.9. Changed in 10.0 to use an ErrorReporter rather than ErrorListener
     */

    public void setErrorReporter(ErrorReporter listener) {
        options.setErrorReporter(listener);
    }

    /**
     * Get the ErrorReporter that will be used when parsing
     *
     * @return the ErrorReporter, if one has been set using {@link #setErrorReporter},
     *         otherwise null.
     * @since 8.9. Changed in 10.0 to use an ErrorReporter rather than ErrorListener
     */

    public ErrorReporter getErrorReporter() {
        return options.getErrorReporter();
    }


    /**
     * Set whether or not the user of this Source is encouraged to close it as soon as reading is finished.
     * Normally the expectation is that any Stream in a StreamSource will be closed by the component that
     * created the Stream. However, in the case of a Source returned by a URIResolver, there is no suitable
     * interface (the URIResolver has no opportunity to close the stream). Also, in some cases such as reading
     * of stylesheet modules, it is possible to close the stream long before control is returned to the caller
     * who supplied it. This tends to make a difference on .NET, where a file often can't be opened if there
     * is a stream attached to it.
     *
     * @param close true if the source should be closed as soon as it has been consumed
     * @since 8.8
     */

    public void setPleaseCloseAfterUse(boolean close) {
        options.setPleaseCloseAfterUse(close);
    }

    /**
     * Determine whether or not the user of this Source is encouraged to close it as soon as reading is
     * finished.
     *
     * @return true if the source should be closed as soon as it has been consumed
     * @since 8.8
     */

    public boolean isPleaseCloseAfterUse() {
        return options.isPleaseCloseAfterUse();
    }

    /**
     * Close any resources held by this Source. This only works if the underlying Source is one that is
     * recognized as holding closable resources.
     *
     * @since 8.8
     */

    public void close() {
        ParseOptions.close(source);
    }


}

