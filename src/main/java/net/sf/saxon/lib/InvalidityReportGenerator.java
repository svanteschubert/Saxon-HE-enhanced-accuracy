////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import javax.xml.stream.XMLStreamWriter;

/**
 * This class <code>InvalidityReportGenerator</code> extends the standard error handler for errors found during validation
 * of an instance document against a schema, used if user specifies -report option on validate. Its effect is to output
 * the validation errors found into the filename specified in an XML format.
 */

public class InvalidityReportGenerator extends StandardInvalidityHandler {

    public static final String REPORT_NS = "http://saxon.sf.net/ns/validation";


    public InvalidityReportGenerator(Configuration config) {
            super(config);
        }

    /**
        * Create a Report Invalidity Handler writing to a Receiver
        *
        * @param config   the Saxon configuration
        * @param receiver required to output the validation errors
     */
    public InvalidityReportGenerator(Configuration config, Outputter receiver) throws XPathException {
        super(config);

    }

    /**
     * Set the receiver
     * @param receiver required to output the validation errors
     */
    public void setReceiver(Outputter receiver){
        //no action
    }



    /**
     * Set the XML document that is to be validated
     *
     * @param id of the source document
     */
    public void setSystemId(String id) {

    }

    /**
     * Set the XSD document used to validation process
     *
     * @param name of xsd file
     */
    public void setSchemaName(String name) {

    }

    public int getErrorCount() {
        return 0;
    }

    public int getWarningCount() {
        return 0;
    }

    public void setXsdVersion(String version) {

    }

    public XMLStreamWriter getWriter() {
        return null;
    }

    /**
     * Receive notification of a validity error.
     *
     * @param failure Information about the nature of the invalidity
     */
    @Override
    public void reportInvalidity(Invalidity failure) throws XPathException {

    }


    /**
     * At the start of a validation episode, initialize the handler
     *
     * @param systemId optional; may be used to represent the destination of any report produced
     * @throws XPathException if initialization of the invalidity handler fails for any reason
     */
    @Override
    public void startReporting(String systemId) throws XPathException {
        // no action
    }

    @Override
    public Sequence endReporting() throws XPathException {
       return null;

    }

    /**
     * Create metedata element which contains summary information in the output XML document
     *
     * @throws XPathException
     */

    public void createMetaData() throws XPathException {
        // no action
    }

}
