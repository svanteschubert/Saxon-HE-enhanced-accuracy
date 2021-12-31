////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.AbsolutePath;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.ValidationFailure;
import net.sf.saxon.value.EmptySequence;

import javax.xml.transform.dom.DOMLocator;

/**
 * This class <code>StandardInvalidityHandler</code>, despite its name, is not directly used by Saxon, and in particular
 * it is NOT the default InvalidityHandler. It is however available for use by applications, either directly or by
 * subclassing. (The default InvalidityHandler wraps a supplied ErrorListener).
 *
 * This InvalidityHandler logs validation error messages to a supplied {@link Logger}, using the Logger belonging
 * to the supplied {@link Configuration} as the default destination.
 */

public class StandardInvalidityHandler extends StandardDiagnostics implements InvalidityHandler {

    private Configuration config;
    private Logger logger;

    /**
     * Create a Standard Invalidity Handler
     * @param config the Saxon configuration
     */

    public StandardInvalidityHandler(Configuration config) {
        this.config = config;
        this.logger = config.getLogger();
    }

    /**
     * Set output destination for error messages (default is the Logger registered with the Configuration)
     *
     * @param logger The Logger to use for error messages
     */

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Get the Logger used for displaying messages
     *
     * @return the registered Logger. This defaults to the Logger supplied by the {@link Configuration}
     */

    public Logger getLogger() {
        return logger;
    }

    /**
     * Get the Saxon Configuration object
     * @return the configuration
     */

    public Configuration getConfiguration(){
        return config;
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

    /**
     * Receive notification of a validity error.
     * @param failure Information about the nature of the invalidity
     */

    @Override
    public void reportInvalidity(Invalidity failure) throws XPathException{
        Logger localLogger = logger;
        if (localLogger == null) {
            localLogger = config.getLogger();
        }

        String explanation = getExpandedMessage(failure);
        String constraintReference = getConstraintReferenceMessage(failure);
        String validationLocation = ((ValidationFailure) failure).getValidationLocationText();
        String contextLocation = ((ValidationFailure) failure).getContextLocationText();
        String finalMessage = "Validation error " +
                getLocationMessage(failure) +
                "\n  " +
                wordWrap(explanation) +
                //wordWrap(validationLocation.isEmpty() ? "" : "\n  " + validationLocation) +
                wordWrap(contextLocation.isEmpty() ? "" : "\n  " + contextLocation) +
                wordWrap(constraintReference == null ? "" : "\n  " + constraintReference) +
                formatListOfOffendingNodes((ValidationFailure)failure);

        localLogger.error(finalMessage);
    }


    /**
     * Get a string identifying the location of an error.
     *
     * @param err the exception containing the location information
     * @return a message string describing the location
     */

    public String getLocationMessage(Invalidity err) {
        String locMessage = "";
        String systemId = null;
        NodeInfo node = err.getInvalidNode();
        AbsolutePath path;
        String nodeMessage = null;
        int lineNumber = err.getLineNumber();
        if (err instanceof DOMLocator) {
            nodeMessage = "at " + ((DOMLocator) err).getOriginatingNode().getNodeName() + ' ';
        } else if (lineNumber == -1 && (path = err.getPath()) != null) {
            nodeMessage = "at " + path + ' ';
        } else if (node != null) {
            nodeMessage = "at " + Navigator.getPath(node) + ' ';
        }
        boolean containsLineNumber = lineNumber != -1;
        if (nodeMessage != null) {
            locMessage += nodeMessage;
        }
        if (containsLineNumber) {
            locMessage += "on line " + lineNumber + ' ';
            if (err.getColumnNumber() != -1) {
                locMessage += "column " + err.getColumnNumber() + ' ';
            }
        }

        systemId = err.getSystemId();
      
        if (systemId != null && systemId.length() != 0) {
            locMessage += (containsLineNumber ? "of " : "in ") + abbreviateLocationURI(systemId) + ':';
        }
        return locMessage;
    }

    /**
     * Get a string containing the message for this exception and all contained exceptions
     *
     * @param err the exception containing the required information
     * @return a message that concatenates the message of this exception with its contained exceptions,
     *         also including information about the error code and location.
     */

    public String getExpandedMessage(Invalidity err) {
        String code = err.getErrorCode();
        return (code==null ? "" : code + ": ") + err.getMessage();
    }

    /**
     * Get the constraint reference as a string for inserting into an error message.
     * The default implementation formats a message containing the values of
     * {@link Invalidity#getSchemaPart()}, {@link Invalidity#getConstraintName()},
     * and {@link Invalidity#getConstraintClauseNumber()}.
     *
     * @return the constraint reference as a message, or null if no information is available
     */

    /*@Nullable*/
    public String getConstraintReferenceMessage(Invalidity err) {
        if (err.getSchemaPart() == -1) {
            return null;
        }
        return "See http://www.w3.org/TR/xmlschema-" + err.getSchemaPart() + "/#" + err.getConstraintName()
            + " clause " + err.getConstraintClauseNumber();
    }

    /**
     * Get the value to be associated with a validation exception. May return null.
     * In the case of the InvalidityReportGenerator, this returns the XML document
     * containing the validation report
     *
     * @return a value (or null). This will be the value returned as the value of
     * the variable $err:value during try/catch processing
     */
    @Override
    public Sequence endReporting() throws XPathException {
        return EmptySequence.getInstance();
    }
}

