////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.jaxp;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.JPConverter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.JavaExternalObjectType;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.UntypedAtomicValue;
import org.w3c.dom.Node;
import org.xml.sax.XMLFilter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Saxon implementation of the JAXP Transformer interface.
 * <p>Since Saxon 9.6, JAXP interfaces are implemented as a layer above the s9api interface</p>
 */
abstract class AbstractTransformerImpl extends IdentityTransformer {

    private XsltExecutable xsltExecutable;
    private Map<String, Object> parameters = new HashMap<>(8);

    AbstractTransformerImpl(XsltExecutable e) {
        super(e.getProcessor().getUnderlyingConfiguration());
        this.xsltExecutable = e;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    Destination makeDestination(final Result outputTarget) throws XPathException {
        Destination destination;

        if (outputTarget instanceof StreamResult) {
            StreamResult sr = (StreamResult) outputTarget;
            if (sr.getOutputStream() != null) {
                destination = xsltExecutable.getProcessor().newSerializer(sr.getOutputStream());
            } else if (sr.getWriter() != null) {
                destination = xsltExecutable.getProcessor().newSerializer(sr.getWriter());
            } else if (sr.getSystemId() != null) {
                URI uri;
                try {
                    uri = new URI(sr.getSystemId());
                } catch (URISyntaxException e) {
                    throw new XPathException("System ID in Result object is not a valid URI: " + sr.getSystemId(), e);
                }
                if (!uri.isAbsolute()) {
                    try {
                        uri = new File(sr.getSystemId()).getAbsoluteFile().toURI();
                    } catch (Exception e) {
                        // if we fail, we'll get another exception
                    }
                }
                File file = new File(uri);
                try {
                    if ("file".equals(uri.getScheme()) && !file.exists()) {
                        File directory = file.getParentFile();
                        if (directory != null && !directory.exists()) {
                            directory.mkdirs();
                        }
                        file.createNewFile();
                    }
                } catch (IOException err) {
                    throw new XPathException("Failed to create output file " + uri, err);
                }
                FileOutputStream stream;
                try {
                    stream = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    throw new XPathException("Failed to create output file", e);
                }
                destination = xsltExecutable.getProcessor().newSerializer(stream);
                ((Serializer) destination).setCloseOnCompletion(true);
            } else {
                throw new IllegalArgumentException("StreamResult supplies neither an OutputStream nor a Writer");
            }
            // Copy the local (API-defined) output properties to the Serializer.
            // (The stylesheet-defined properties will be copied later)
            Properties localOutputProperties = getLocalOutputProperties();
            for (String key : localOutputProperties.stringPropertyNames()) {
                QName propertyName = QName.fromClarkName(key);
                if (!(propertyName.getNamespaceURI().equals(NamespaceConstant.SAXON) && propertyName.getLocalName().equals("next-in-chain"))) {
                    ((Serializer) destination).setOutputProperty(QName.fromClarkName(key),
                                                                 localOutputProperties.getProperty(key));
                }
            }
        } else if (outputTarget instanceof SAXResult) {
            destination = new SAXDestination(((SAXResult) outputTarget).getHandler());
        } else if (outputTarget instanceof DOMResult) {
            Node root = ((DOMResult) outputTarget).getNode();
            if (root == null) {
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(true);
                    root = dbf.newDocumentBuilder().newDocument();
                    ((DOMResult) outputTarget).setNode(root);
                } catch (ParserConfigurationException e) {
                    throw new XPathException(e);
                }
            }
            destination = new DOMDestination(root);
        } else if (outputTarget instanceof Receiver) {
            destination = new ReceivingDestination((Receiver)outputTarget);
        } else {
            return null;
        }
        return destination;
    }



    /**
     * Add a parameter for the transformation.
     * <p>Pass a qualified name as a two-part string, the namespace URI
     * enclosed in curly braces ({}), followed by the local name. If the
     * name has a null URL, the String only contain the local name. An
     * application can safely check for a non-null URI by testing to see if the
     * first character of the name is a '{' character.</p>
     * <p>For example, if a URI and local name were obtained from an element
     * defined with &lt;xyz:foo
     * xmlns:xyz="http://xyz.foo.com/yada/baz.html"/&gt;,
     * then the qualified name would be "{http://xyz.foo.com/yada/baz.html}foo".
     * Note that no prefix is used.</p>
     *
     * @param name  The name of the parameter, which may begin with a
     *              namespace URI in curly braces ({}).
     * @param value The value object.  This can be any valid Java object. It is
     *              up to the processor to provide the proper object coercion or to simply
     *              pass the object on for use in an extension.
     * @throws NullPointerException     If value is null.
     * @throws IllegalArgumentException If the supplied value cannot be converted to the declared
     *                                  type of the corresponding stylesheet parameter
     */
    @Override
    public void setParameter(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        parameters.put(name, value);
        QName qName = QName.fromClarkName(name);
        XsltExecutable.ParameterDetails details = xsltExecutable.getGlobalParameters().get(qName);
        if (details == null) {
            // no parameter with this name is defined; we can simply ignore it
            return;
        }
        Configuration config = getConfiguration();
        net.sf.saxon.value.SequenceType required = details.getUnderlyingDeclaredType();
        Sequence converted;
        try {
            if (value instanceof Sequence) {
                converted = (Sequence)value;
            } else if (value instanceof String) {
                converted = new UntypedAtomicValue((String) value);
            } else if (required.getPrimaryType() instanceof JavaExternalObjectType) {
                converted = new ObjectValue<>(value);
            } else {
                JPConverter converter = JPConverter.allocate(value.getClass(), null, config);
                XPathContext context = getUnderlyingController().newXPathContext();
                converted = converter.convert(value, context);
            }
            if (converted == null) {
                converted = EmptySequence.getInstance();
            }

            if (required != null && !required.matches(converted, config.getTypeHierarchy())) {
                RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.VARIABLE, qName.toString(), -1);
                converted = config.getTypeHierarchy().applyFunctionConversionRules(
                        converted, required, role, Loc.NONE);
            }
        } catch (XPathException e) {
            throw new IllegalArgumentException(e);
        }

        setConvertedParameter(qName, XdmValue.wrap(converted));
    }

    protected abstract void setConvertedParameter(QName name, XdmValue value);

    /**
     * Get a parameter that was explicitly set with setParameter.
     * <p>This method does not return a default parameter value, which
     * cannot be determined until the node context is evaluated during
     * the transformation process.</p>
     *
     * @param name of <code>Object</code> to get
     * @return A parameter that has been set with setParameter, or null if no parameter
     * with this name has been set.
     */
    @Override
    public Object getParameter(String name) {
        return parameters.get(name);
    }

    /**
     * Clear all parameters set with setParameter.
     */
    @Override
    public void clearParameters() {
        parameters.clear();
    }

    /**
     * Get the output properties defined in the unnamed xsl:output declaration(s) within
     * the stylesheet
     * @return the values of output properties set in the stylesheet
     */

    @Override
    protected Properties getStylesheetOutputProperties() {
        return xsltExecutable.getUnderlyingCompiledStylesheet().getPrimarySerializationProperties().getProperties();
    }

    /**
     * Get the underlying s9api implementation class representing the compled stylesheet
     * which this transformer is executing
     * @return the underlying s9api XsltExecutable
     */

    public XsltExecutable getUnderlyingXsltExecutable() {
        return xsltExecutable;
    }

    /**
     * Get the internal Saxon Controller instance that implements this transformation.
     * Note that the Controller interface will not necessarily remain stable in future releases
     * @return the underlying Saxon Controller instance
     */

    public abstract Controller getUnderlyingController();

    /**
     * Create a JAXP XMLFilter which allows this transformation to be added to a SAX pipeline
     *
     * @return the transformation in the form of an XMLFilter. If the originating <code>TransformerFactory</code>
     * was an <code>StreamingTransformerFactory</code> and the stylesheet is streamable, then this XMLFilter
     * will operate in streaming mode.
     */

    public abstract XMLFilter newXMLFilter();


}

