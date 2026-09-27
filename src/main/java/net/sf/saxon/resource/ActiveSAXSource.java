////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.expr.number.Numberer_en;
import net.sf.saxon.lib.*;
import net.sf.saxon.om.Action;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.*;

import javax.xml.transform.sax.SAXSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class extends the standard SAXSource class by providing a {@link #deliver(Receiver, ParseOptions)}
 * method, meaning that Saxon can treat it just like any other {@link ActiveSource} implementation.
 *
 * <p>This makes it easier to extract code from the product that's specific to the Java platform
 * (in particular, support for SAX APIs).</p>
 */

public class ActiveSAXSource extends SAXSource implements ActiveSource  {

    Consumer<XMLReader> parserPool;

    public ActiveSAXSource(XMLReader parser, InputSource source) {
        super(parser, source);
    }

    /**
     * Construct an ActiveSAXSource from a SAXSource
     * @param source the input source object, whose properties are copied. The source must
     *               include a non-null XMLReader.
     */
    public ActiveSAXSource(SAXSource source) throws IllegalArgumentException, XPathException {
        if (source.getXMLReader() == null) {
            throw new IllegalArgumentException("Supplied SAXSource contains no XMLReader");
        }
        setInputSource(source.getInputSource());
        setXMLReader((source.getXMLReader()));
        setSystemId(source.getSystemId());
    }

    public ActiveSAXSource(InputSource inputSource, Configuration config) {
        setInputSource(inputSource);
        setXMLReader(config.getSourceParser());
        setSystemId(inputSource.getSystemId());
    }

    /**
     * Supply a mechanism to recycle the XMLReader after use
     * @param parserPool a place to return the parser after parsing is complete
     */

    public void setParserPool(Consumer<XMLReader> parserPool) {
        this.parserPool = parserPool;
    }

    @Override
    public void deliver(Receiver receiver, ParseOptions options) throws XPathException {
        ErrorHandler errorHandler = null;
        try {
            Logger logger = receiver.getPipelineConfiguration().getConfiguration().getLogger();
            XMLReader parser = getXMLReader();
            //System.err.println("Using parser " + parser.getClass());

            configureParser(parser);  // Bug 5276

            ReceivingContentHandler rch = new ReceivingContentHandler();
            rch.setReceiver(receiver);
            rch.setPipelineConfiguration(receiver.getPipelineConfiguration());
            parser.setContentHandler(rch);
            parser.setDTDHandler(rch);
            parser.setProperty("http://xml.org/sax/properties/lexical-handler", rch);

            errorHandler = parser.getErrorHandler();
            if (errorHandler == null) {
                errorHandler = options.getErrorHandler();
                if (errorHandler == null) {
                    if (options.getErrorReporter() != null) {
                        errorHandler = new StandardErrorHandler(options.getErrorReporter());
                    } else {
                        errorHandler = new StandardErrorHandler(receiver.getPipelineConfiguration().getConfiguration().makeErrorReporter());
                    }
                }
                parser.setErrorHandler(errorHandler);
            }

            if (!options.isExpandAttributeDefaults()) {
                try {
                    parser.setFeature("http://xml.org/sax/features/use-attributes2", true);
                } catch (SAXNotRecognizedException | SAXNotSupportedException err) {
                    // ignore the failure, we did our best (Xerces gives us an Attribute2 even though it
                    // doesn't recognize this request!)
                }
            }

            boolean dtdRecover = options.getDTDValidationMode() == Validation.LAX;

            Map<String, Boolean> parserFeatures = options.getParserFeatures();
            Map<String, Object> parserProperties = options.getParserProperties();

            List<Action> resetActions = null;
            if (parserFeatures != null) {
                resetActions = new ArrayList<>();
                for (Map.Entry<String, Boolean> entry : parserFeatures.entrySet()) {
                    try {
                        String name = entry.getKey();
                        boolean value = entry.getValue();
                        boolean oldValue = parser.getFeature(name);
                        resetActions.add(() -> {
                            try {
                                parser.setFeature(name, oldValue);
                            } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
                                // ignore the exception
                            }
                        });
                        if (name.equals("http://apache.org/xml/features/xinclude")) {
                            boolean tryAgain = false;
                            try {
                                // This feature name is supported in Xerces 2.9.0
                                parser.setFeature(name, value);
                            } catch (SAXNotRecognizedException | SAXNotSupportedException err) {
                                tryAgain = true;
                            }
                            if (tryAgain) {
                                try {
                                    // This feature name is supported in the version of Xerces bundled with JDK 1.5
                                    parser.setFeature(name + "-aware", value);
                                } catch (SAXNotRecognizedException err) {
                                    throw new XPathException(namedParser(parser) +
                                                                     " does not recognize request for XInclude processing", err);
                                } catch (SAXNotSupportedException err) {
                                    throw new XPathException(namedParser(parser) +
                                                                     " does not support XInclude processing", err);
                                }
                            }
                        } else {
                            parser.setFeature(name, value);
                        }
                    } catch (SAXNotRecognizedException err) {
                        // No warning if switching a feature off doesn't work: bugs 2540 and 2551
                        if (entry.getValue()) {
                            logger.warning(namedParser(parser) + " does not recognize the feature " + entry.getKey());
                        }
                    } catch (SAXNotSupportedException err) {
                        if (entry.getValue()) {
                            logger.warning(namedParser(parser) + " does not support the feature " + entry.getKey());
                        }
                    }
                }
            }

            if (parserProperties != null) {
                if (resetActions == null) {
                    resetActions = new ArrayList<>();
                }
                for (Map.Entry<String, Object> entry : parserProperties.entrySet()) {
                    try {
                        final Object oldValue = parser.getProperty(entry.getKey());
                        resetActions.add(() -> {
                            try {
                                parser.setProperty(entry.getKey(), oldValue);
                            } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
                                // ignore the exception
                            }
                        });
                        parser.setProperty(entry.getKey(), entry.getValue());
                    } catch (SAXNotRecognizedException err) {
                        logger.warning(namedParser(parser) + " does not recognize the property " + entry.getKey());
                    } catch (SAXNotSupportedException err) {
                        logger.warning(namedParser(parser) + " does not support the property " + entry.getKey());
                    }

                }
            }

            boolean xInclude = options.isXIncludeAware();
            if (xInclude) {
                boolean tryAgain = false;
                try {
                    // This feature name is supported in the version of Xerces bundled with JDK 1.5
                    parser.setFeature("http://apache.org/xml/features/xinclude-aware", true);
                } catch (SAXNotRecognizedException | SAXNotSupportedException err) {
                    tryAgain = true;
                }
                if (tryAgain) {
                    try {
                        // This feature name is supported in Xerces 2.9.0
                        parser.setFeature("http://apache.org/xml/features/xinclude", true);
                    } catch (SAXNotRecognizedException err) {
                        throw new XPathException(namedParser(parser) +
                                                         " does not recognize request for XInclude processing", err);
                    } catch (SAXNotSupportedException err) {
                        throw new XPathException(namedParser(parser) +
                                                         " does not support XInclude processing", err);
                    }
                }
            }
            //System.err.println("Parse " + getSystemId());
            ///////////////////////////////
            parser.parse(getInputSource());
            ///////////////////////////////
            if (errorHandler instanceof StandardErrorHandler) {
                int errs = ((StandardErrorHandler) errorHandler).getFatalErrorCount();
                if (errs > 0) {
                    throw new XPathException("The XML parser reported " + errs + (errs == 1 ? " error" : " errors"));
                }
                errs = ((StandardErrorHandler) errorHandler).getErrorCount();
                if (errs > 0) {
                    String message = "The XML parser reported " + new Numberer_en().toWords("", errs).toLowerCase() +
                            " validation error" + (errs == 1 ? "" : "s");
                    if (dtdRecover) {
                        message += ". Processing continues, because recovery from validation errors was requested";
                        logger.warning(message);
                    } else {
                        throw new XPathException(message);
                    }
                }
            }

            if (parserPool != null) {
                if (resetActions != null) {
                    for (Action a : resetActions) {
                        a.doAction();
                    }
                }
                parserPool.accept(parser);
            }
        } catch (XPathException xpe) {
            throw xpe;
        } catch (UncheckedXPathException uxpe) {
            throw uxpe.getXPathException();
        } catch (SAXException err) {
            Exception nested = err.getException();
            if (nested instanceof XPathException) {
                throw (XPathException) nested;
            } else if (nested instanceof RuntimeException) {
                throw (RuntimeException) nested;
            } else if (errorHandler instanceof StandardErrorHandler seh) {
                // Bug 6257. The information notified by the XML parser to the ErrorHandler
                // is much more usable than what we can get by wrapping the SAXException that
                // is thrown on completion of a failed parse, so we use the former if available
                ErrorReporter er = seh.getErrorReporter();
                if (er instanceof StandardErrorReporter ser) {
                    XmlProcessingError latestError = ser.getLatestError();
                    if (latestError != null) {
                        XPathException exception = new XPathException(ser.getExpandedMessage(latestError))
                                .withErrorCode(SaxonErrorCode.SXXP0003)
                                .withLocation(latestError.getLocation()); // bug 6858
                        exception.setHasBeenReported(latestError.isAlreadyReported());
                        throw exception;
                    }
                }
            }
            XPathException de = new XPathException(err).withErrorCode(SaxonErrorCode.SXXP0003);
            de.setHasBeenReported(true);
            throw de;
        } catch (java.io.IOException err) {
            throw new XPathException("I/O error reported by XML parser processing " +
                                             getSystemId(), err).withErrorCode(SaxonErrorCode.SXXP0003);
        } catch (Exception ex) {
            throw ex;
        }
    }

    private static String namedParser(XMLReader parser) {
        return "Selected XML parser " + parser.getClass().getName();
    }

    /**
     * Configure a SAX parser to ensure it has the correct namespace properties set
     *
     * @param parser the parser to be configured
     * @throws XPathException if the parser cannot be configured to the
     *                        required settings (namespaces=true, namespace-prefixes=false). Note that the SAX
     *                        specification requires every XMLReader to support these settings, so this error implies
     *                        that the XMLReader is non-conformant; this is not uncommon in cases where the XMLReader
     *                        is user-written.
     */

    public static void configureParser(XMLReader parser) throws XPathException {
        try {
            parser.setFeature("http://xml.org/sax/features/namespaces", true);
        } catch (SAXNotSupportedException err) {    // SAX2 parsers MUST support this feature!
            throw new XPathException(
                    "The SAX2 parser " + parser.getClass().getName() +
                            " does not recognize the 'namespaces' feature", err);
        } catch (SAXNotRecognizedException err) {
            throw new XPathException(
                    "The SAX2 parser " + parser.getClass().getName() +
                            " does not support setting the 'namespaces' feature to true", err);
        }

        try {
            parser.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
        } catch (SAXNotSupportedException err) {    // SAX2 parsers MUST support this feature!
            throw new XPathException(
                    "The SAX2 parser " + parser.getClass().getName() +
                            " does not recognize the 'namespace-prefixes' feature", err);
        } catch (SAXNotRecognizedException err) {
            throw new XPathException(
                    "The SAX2 parser " + parser.getClass().getName() +
                            " does not support setting the 'namespace-prefixes' feature to false", err);
        }

    }

}


