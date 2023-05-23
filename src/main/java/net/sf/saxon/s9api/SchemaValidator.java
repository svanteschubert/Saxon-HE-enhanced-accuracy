////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.EventSource;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.jaxp.ReceivingDestination;
import net.sf.saxon.lib.InvalidityHandler;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;

/**
 * A <tt>SchemaValidator</tt> is an object that is used for validating instance documents against a schema.
 * The schema consists of the collection of schema components that are available within the schema
 * cache maintained by the SchemaManager, together with any additional schema components located
 * during the course of validation by means of an xsl:schemaLocation or xsi:noNamespaceSchemaLocation
 * attribute within the instance document.
 * <p>If validation fails, an exception is thrown. If validation succeeds, the validated document
 * can optionally be written to a specified destination. This will be a copy of the original document,
 * augmented with default values for absent elements and attributes, and carrying type annotations
 * derived from the schema processing. Expansion of defaults can be suppressed by means of the method
 * {@link #setExpandAttributeDefaults(boolean)}.</p>
 * <p>A <tt>SchemaValidator</tt> is serially reusable but not thread-safe. That is, it should normally
 * be used in the thread where it is created, but it can be used more than once, to validate multiple
 * input documents.</p>
 * <p>A <tt>SchemaValidator</tt> is a <tt>Destination</tt>, which allows it to receive the output of a
 * query or transformation to be validated.</p>
 * <p>Saxon does not deliver the full PSVI as described in the XML schema specifications,
 * only the subset of the PSVI properties featured in the XDM data model.</p>
 */

public abstract class SchemaValidator extends AbstractDestination {

    /**
     * The validation mode may be either strict or lax. The default is strict; this method may be called
     * to indicate that lax validation is required. With strict validation, validation fails if no element
     * declaration can be located for the outermost element. With lax validation, the absence of an
     * element declaration results in the content being considered valid.
     *
     * @param lax true if validation is to be lax, false if it is to be strict
     */

    public abstract void setLax(boolean lax);

    /**
     * Ask whether validation is to be in lax mode.
     *
     * @return true if validation is to be in lax mode, false if it is to be in strict mode.
     */

    public abstract boolean isLax();

    /**
     * Set the ErrorListener to be used while validating instance documents.
     * The setErrorReporter, setInvalidityHandler, and setValidityReporting
     * are mutually exclusive - setting any one of them will cancel the others. Please note
     * that setErrorReporter has the drawback of creating an exception for every
     * validation error, which is expensive.
     *
     * @param listener The error listener to be used. This is notified of all errors detected during the
     *                 validation episode.
     * @deprecated since 10.0. Use {@link #setInvalidityHandler(InvalidityHandler)}
     */

    public abstract void setErrorListener(ErrorListener listener);

    /**
     * Get the ErrorListener being used while validating instance documents
     *
     * @return listener The error listener in use. This is notified of all errors detected during the
     *         validation episode. Returns null if no user-supplied ErrorListener has been set.
     * @deprecated since 10.0. Use {@link #getInvalidityHandler()}
     */

    public abstract ErrorListener getErrorListener();

    /**
     * Set the InvalidityHandler to be used when validating instance documents.
     * The setErrorReporter, setInvalidityHandler, and setValidityReporting
     * are mutually exclusive - setting any one of them will cancel the others.
     * @param handler the InvalidityHandler to be used.
     */

    public abstract void setInvalidityHandler(InvalidityHandler handler);

    /**
     * Get the InvalidityHandler used when validating instance documents
     * @return the InvalidityHandler being used
     */

    public abstract InvalidityHandler getInvalidityHandler();
    /**
     * Say whether validation statistics are to be collected. Calling this method
     * has the side-effect of deleting any statistics previously collected.
     *
     * @param collect true if validation statistics are to be collected (default is false)
     * @since 9.6
     */

    public abstract void setCollectStatistics(boolean collect);

    /**
     * Ask whether validation statistics are to be collected
     *
     * @return true if validation statistics are to be collected (default is false)
     * @since 9.6
     */

    public abstract boolean isCollectStatistics();

    /**
     * Report the validation statistics from the most recent validation episode
     * Does nothing if no validation statistics have been collected.
     *
     * @param destination the destination to which the statistics will be written.
     *                    The XML format of the destination is not guaranteed to be stable across
     *                    Saxon releases.
     * @throws SaxonApiException if any error occurs writing the statistics
     * @since Saxon 9.6
     */

    public abstract void reportValidationStatistics(Destination destination) throws SaxonApiException;

    /**
     * This method can be called before running a validation to define a destination to which validation
     * reports should be written. The validation report is in XML format, and the Destination may therefore
     * be (for example) a Serializer, an XdmDestination, or an XsltTransformer. (An XsltTransformer might be
     * used to render the report as HTML, for example). The format of the validation report is defined in a
     * schema which is available in the saxon-resources download file.
     *
     * Calling this method has the effect of setting an InvalidityHandler internally, which cancels any
     * user-defined InvalidityHandler or ErrorListener that has been set.
     *
     * This option applies only to the next call of {@link #validate} or {@link #validateMultiple}.
     * After such a call, the registered {@code InvalidityHandler} is reset to its initial default state (which causes
     * validation errors to be output to {@code System.err}), and {@link #setValidityReporting} must be called again if reports
     * are required for subsequent validations.
     *
     * If multiple documents are validated using the {@link #validateMultiple} method, the reports for each validation will
     * be combined into a single report. Using this mechanism when validating multiple documents simultaneously is
     * recommended, because with other mechanisms, validation failures for different source documents will be
     * interleaved, and it becomes an application responsibility to organize which failures relate to which
     * source document.
     *
     * The {@link #setErrorListener}, {@link #setInvalidityHandler}, and {@link #setValidityReporting}
     * are mutually exclusive - setting any one of them will cancel the others.
     * @param destination where XML will be sent
     * @throws SaxonApiException if the destination is unsuitable
     */
    public abstract void setValidityReporting(Destination destination) throws SaxonApiException;


    /**
     * Say whether the schema processor is to take account of any xsi:schemaLocation and
     * xsi:noNamespaceSchemaLocation attributes encountered while validating an instance document
     *
     * @param recognize true if these two attributes are to be recognized; false if they are to
     *                  be ignored. Default is true.
     */

    public abstract void setUseXsiSchemaLocation(boolean recognize);

    /**
     * Ask whether the schema processor is to take account of any xsi:schemaLocation and
     * xsi:noNamespaceSchemaLocation attributes encountered while validating an instance document
     *
     * @return true if these two attributes are to be recognized; false if they are to
     *         be ignored. Default is true.
     */

    public abstract boolean isUseXsiSchemaLocation();


    /**
     * Set the Destination to receive the validated document. If no destination is supplied, the
     * validated document is discarded.
     *
     * @param destination the destination to receive the validated document
     */

    public abstract void setDestination(/*@Nullable*/ Destination destination);

    /**
     * Get the Destination that will receive the validated document. Return null if no destination
     * has been set.
     *
     * @return the destination to receive the validated document, or null if none has been supplied
     */


    /*@Nullable*/
    public abstract Destination getDestination();

    /**
     * Set the name of the required top-level element of the document to be validated (that is, the
     * name of the outermost element of the document). If no value is supplied, there is no constraint
     * on the required element name
     *
     * @param name the name of the document element, as a QName; or null to remove a previously-specified
     *             value.
     */

    public abstract void setDocumentElementName(QName name);

    /**
     * Get the name of the required top-level element of the document to be validated.
     *
     * @return the name of the required document element, or null if no value has been set.
     */

    public abstract QName getDocumentElementName();

    /**
     * Set the name of the required type of the top-level element of the document to be validated.
     * If no value is supplied, there is no constraint on the required type
     *
     * @param name the name of the type of the document element, as a QName;
     *             or null to remove a previously-specified value. This must be the name of a type in the
     *             schema (typically but not necessarily a complex type).
     * @throws SaxonApiException if there is no known type with this name
     */

    public abstract void setDocumentElementTypeName(QName name) throws SaxonApiException;
    /**
     * Get the name of the required type of the top-level element of the document to be validated.
     *
     * @return the name of the required type of the document element, or null if no value has been set.
     */

    public abstract QName getDocumentElementTypeName();

    /**
     * Get the schema type against which the document element is to be validated
     *
     * @return the schema type
     */

    protected abstract SchemaType getDocumentElementType();
    /**
     * Set whether attribute defaults defined in a schema are to be expanded or not
     * (by default, fixed and default attribute values are expanded, that is, they are inserted
     * into the document during validation as if they were present in the instance being validated)
     *
     * @param expand true if defaults are to be expanded, false if not
     */

    public abstract void setExpandAttributeDefaults(boolean expand);

    /**
     * Ask whether attribute defaults defined in a schema are to be expanded or not
     * (by default, fixed and default attribute values are expanded, that is, they are inserted
     * into the document during validation as if they were present in the instance being validated)
     *
     * @return true if defaults are to be expanded, false if not
     */

    public abstract boolean isExpandAttributeDefaults();

    /**
     * Set the value of a schema parameter (a parameter defined in the schema using
     * the <code>saxon:param</code> extension)
     *
     * @param name  the name of the schema parameter, as a QName
     * @param value the value of the schema parameter, or null to clear a previously set value
     * @since 9.5
     */

    public abstract void setParameter(QName name, XdmValue value);

    /**
     * Get the value that has been set for a schema parameter (a parameter defined in the schema using
     * the <code>saxon:param</code> extension)
     *
     * @param name the parameter whose name is required
     * @return the value that has been set for the parameter, or null if no value has been set
     * @since 9.5
     */

    public abstract XdmValue getParameter(QName name);


    /**
     * Validate an instance document supplied as a Source object
     *
     * @param source the instance document to be validated. The call getSystemId() applied to
     *               this source object must return the base URI used for dereferencing any xsi:schemaLocation
     *               or xsi:noNamespaceSchemaLocation attributes
     * @throws SaxonApiException if the source document is found to be invalid, or if error conditions
     *                           occur that prevented validation from taking place (such as failure to read or parse the input
     *                           document). The wrapped exception acting as the cause of the SaxonApiException can be used to
     *                           distinguish these failure conditions.
     */

    public abstract void validate(Source source) throws SaxonApiException;



    /**
     *  This method can be called to validate multiple source documents simultaneously (in parallel threads).
     *  The method returns when all the threads are complete. The number of threads used is set to the number
     *  of processors available on the machine.
     *
     *  When this method is used, any destination set using the setDestination() method is ignored. The
     *  post-validation documents (with type annotations and expanded default values, for example) are not
     *  made available.
     *
     *  It is recommended to use this method in conjunction with setValidityReport; the resulting report will
     *  detail the outcome of validation for each supplied Source, each in a separate section.
     *
     *  It is important that the systemId property of each Source object should be set; otherwise, it will not
     *  be possible in the resulting report to associate validation failures with individual source documents.
     *
     *  Note that the method does not throw an exception if any of the documents are found to be invalid, or if
     *  any of the documents cannot be validated (for example, because they are not well-formed XML, or because
     *  they cannot be retrieved from a web server). All such errors are reported in the validation report, or are
     *  notified to the registered ErrorListener or InvalidityHandler. By default, messages detailing the failures
     *  are simply written to System.err output.
     *
     *  If the Configuration option FeatureKeys.ALLOW_MULTITHREADING is set to false, the source documents are
     *  validated synchronously in a single thread.
     *
     * @param sources the Iterable of instance documents to be validated. The call getSystemId() applied to
     *               the source objects must return the base URI used for dereferencing any xsi:schemaLocation
     *               or xsi:noNamespaceSchemaLocation attributes
     * @throws SaxonApiException if the source document is found to be invalid, or if error conditions
     *                           occur that prevented validation from taking place (such as failure to read or parse the input
     *                           document). The wrapped exception acting as the cause of the SaxonApiException can be used to
     *                           distinguish these failure conditions.
     */
    public abstract void validateMultiple(Iterable<Source> sources) throws SaxonApiException;

    /**
     * Construct a Source object that applies validation to an underlying input Source.
     * This method does not cause validation to take place immediately: the
     * validation happens only when the returned {@code Source} is consumed. The {@code Destination}
     * associated with this SchemaValidator is ignored; indeed, it is overwritten. After
     * calling this method, the {@code SchemaValidator} should not be used again for any
     * other purpose.
     * @param input the input Source to be validated
     * @return a Source object that reads the supplied input source and returns events
     * corresponding to the result of validating the input source. The Source object
     * will be recognized by all Saxon methods that expect a {@code Source} as input,
     * but not (in general) by other products.
     * @since 9.9
     */

    public Source asSource(Source input) {
        return new EventSource() {
            {
                setSystemId(input.getSystemId());
            }
            @Override
            public void send(Receiver out) throws XPathException {
                setDestination(new ReceivingDestination(out));
                try {
                    validate(input);
                } catch (SaxonApiException e) {
                    throw XPathException.makeXPathException(e);
                }
            }
        };
    }

    @Override
    public abstract Receiver getReceiver(PipelineConfiguration pipe, SerializationProperties params) throws SaxonApiException;

    /**
     * Close the destination, allowing resources to be released. Saxon calls this method when
     * it has finished writing to the destination.
     */

    @Override
    public abstract void close() throws SaxonApiException;

}

