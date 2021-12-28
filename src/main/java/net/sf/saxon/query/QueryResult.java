////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.om.CopyOptions;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.type.Type;

import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * This utility class takes the result sequence produced by a query, and wraps it as
 * an XML document. The class is never instantiated.
 */
public class QueryResult {

    /*@NotNull*/ public static String RESULT_NS = "http://saxon.sf.net/xquery-results";

    private QueryResult() {
    }

    /**
     * Convenience method to serialize a node using default serialization options, placing
     * the result in a string.
     *
     * @param nodeInfo the node to be serialized. This must not be an attribute or namespace node.
     * @return the serialization of the node
     * @throws net.sf.saxon.trans.XPathException
     *          if a serialization error occurs
     * @since 9.0
     */

    public static String serialize(/*@NotNull*/ NodeInfo nodeInfo) throws XPathException {
        StringWriter sw = new StringWriter();
        Properties props = new Properties();
        props.setProperty("method", "xml");
        props.setProperty("indent", "yes");
        props.setProperty("omit-xml-declaration", "yes");
        serialize(nodeInfo, new StreamResult(sw), props);
        return sw.toString();
    }

    /**
     * Take the results of a query (or any other SequenceIterator) and create
     * an XML document containing copies of all items in the sequence, each item wrapped in a containing
     * element that identifies its type
     *
     * @param iterator The values to be wrapped
     * @param config   The Saxon configuration used to evaluate the query
     * @return the document containing the wrapped results
     * @throws XPathException if any failure occurs
     * @since 8.8
     */

    /*@Nullable*/
    public static NodeInfo wrap(SequenceIterator iterator, /*@NotNull*/ Configuration config) throws XPathException {
        PipelineConfiguration pipe = config.makePipelineConfiguration();
        TinyBuilder builder = new TinyBuilder(pipe);
        builder.setStatistics(config.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
        sendWrappedSequence(iterator, builder);
        return builder.getCurrentRoot();
    }

    /**
     * Take a sequence supplied in the form of an iterator and generate a wrapped represention of the
     * items in the sequence, the wrapped representation being a sequence of events sent to a supplied
     * Receiver, in which each item is wrapped in a containing element that identifies its type
     *
     * @param iterator    the input sequence
     * @param destination the Receiver to accept the wrapped output
     * @throws net.sf.saxon.trans.XPathException
     *          if a failure occurs processing the input iterator
     * @since 8.8
     */

    public static void sendWrappedSequence(SequenceIterator iterator, Receiver destination) throws XPathException {
        SerializerFactory sf = destination.getPipelineConfiguration().getConfiguration().getSerializerFactory();
        SequenceCopier.copySequence(iterator, sf.newSequenceWrapper(destination));
    }

    /**
     * Serialize a document containing wrapped query results (or any other document, in fact)
     * as XML.
     *
     * @param node             The document or element to be serialized
     * @param destination      The Result object to contain the serialized form
     * @param outputProperties Serialization options as defined in JAXP. The requested properties are
     *                         not validated.
     * @throws XPathException If serialization fails
     * @since 8.9
     */

    public static void serialize(/*@NotNull*/ NodeInfo node, /*@NotNull*/ Result destination, /*@NotNull*/ Properties outputProperties)
            throws XPathException {
        Configuration config = node.getConfiguration();
        serializeSequence(SingletonIterator.makeIterator(node), config, destination, outputProperties);
    }

    /**
     * Serialize a document or element node.
     *
     * @param node        The document or element to be serialized
     * @param destination The Result object to contain the serialized form
     * @param properties  Serialization options as defined in JAXP. The requested properties are
     *                    not validated.
     * @throws XPathException If serialization fails
     * @since 9.9.1.3
     */

    public static void serialize(NodeInfo node, Result destination, SerializationProperties properties)
            throws XPathException {
        Configuration config = node.getConfiguration();
        serializeSequence(SingletonIterator.makeIterator(node), config, destination, properties);
    }


    /**
     * Serialize an arbitrary sequence, without any special wrapping.
     *
     * @param iterator    the sequence to be serialized
     * @param config      the configuration (gives access to information such as the NamePool)
     * @param destination the output stream to which the output is to be written
     * @param outputProps a set of serialization properties as defined in JAXP. The requested properties are
     *                    not validated.
     * @throws XPathException if any failure occurs
     * @since 8.9
     */

    public static void serializeSequence(
            SequenceIterator iterator, Configuration config, OutputStream destination, Properties outputProps)
            throws XPathException {
        serializeSequence(iterator, config, new StreamResult(destination), outputProps);
        try {
            destination.flush();
        } catch (IOException err) {
            throw new XPathException(err);
        }
    }

    /**
     * Serialize an arbitrary sequence, without any special wrapping.
     *
     * @param iterator    the sequence to be serialized
     * @param config      the configuration (gives access to information such as the NamePool)
     * @param writer      the writer to which the output is to be written
     * @param outputProps a set of serialization properties as defined in JAXP. The requested properties are
     *                    not validated.
     * @throws XPathException if any failure occurs
     * @since 8.9
     */

    public static void serializeSequence(
            SequenceIterator iterator, Configuration config, Writer writer, Properties outputProps)
            throws XPathException {
        serializeSequence(iterator, config, new StreamResult(writer), outputProps);
        try {
            writer.flush();
        } catch (IOException err) {
            throw new XPathException(err);
        }
    }

    /**
     * Serialize a sequence to a given result
     *
     * @param iterator         the sequence to be serialized
     * @param config           the Saxon Configuration
     * @param result           the destination to receive the output
     * @param outputProperties the serialization properties to be used. The requested properties are
     *                         not validated.
     * @throws XPathException if any failure occurs
     * @since 9.0
     */

    public static void serializeSequence(
            /*@NotNull*/ SequenceIterator iterator, Configuration config, Result result, Properties outputProperties)
            throws XPathException {
        SerializerFactory sf = config.getSerializerFactory();
        Receiver tr = sf.getReceiver(result, new SerializationProperties(outputProperties));
        SequenceCopier.copySequence(iterator, tr);
    }

    /**
     * Serialize a sequence to a given result. This version of the method allows character maps to be supplied
     *
     * @param iterator   the sequence to be serialized
     * @param config     the Saxon Configuration
     * @param result     the destination to receive the output
     * @param properties the serialization properties to be used. The requested properties are
     *                   not validated.
     * @throws XPathException if any failure occurs
     * @since 9.9.1.3
     */

    public static void serializeSequence(
            SequenceIterator iterator, Configuration config, Result result, SerializationProperties properties)
            throws XPathException {
        SerializerFactory sf = config.getSerializerFactory();
        Receiver tr = sf.getReceiver(result, properties);
        SequenceCopier.copySequence(iterator, tr);
    }

    /**
     * Write an updated document back to disk, using the original URI from which it was read
     *
     * @param doc              an updated document. Must be a document node, or a parentless element, or an
     *                         element that has a document node as its parent. The document will be written to the URI
     *                         found in the systemId property of this node.
     * @param outputProperties serialization properties
     * @param backup           true if the old document at that location is to be copied to a backup file
     * @param log              destination for progress messages; if null, no progress messages are written
     * @throws XPathException if the document has no known URI, if the URI is not a writable location,
     *                        or if a serialization error occurs.
     */

    public static void rewriteToDisk(NodeInfo doc, Properties outputProperties, boolean backup, PrintStream log)
            throws XPathException {
        switch (doc.getNodeKind()) {
            case Type.DOCUMENT:
                // OK
                break;
            case Type.ELEMENT:
                NodeInfo parent = doc.getParent();
                if (parent != null && parent.getNodeKind() != Type.DOCUMENT) {
                    throw new XPathException("Cannot rewrite an element node unless it is top-level");
                }
                break;
            default:
                throw new XPathException("Node to be rewritten must be a document or element node");
        }
        String uri = doc.getSystemId();
        if (uri == null || uri.isEmpty()) {
            throw new XPathException("Cannot rewrite a document with no known URI");
        }
        URI u;
        try {
            u = new URI(uri);
        } catch (URISyntaxException e) {
            throw new XPathException("SystemId of updated document is not a valid URI: " + uri);
        }
        File existingFile = new File(u);
        File dir = existingFile.getParentFile();
        if (backup && existingFile.exists()) {
            File backupFile = new File(dir, existingFile.getName() + ".bak");
            if (log != null) {
                log.println("Creating backup file " + backupFile);
            }
            boolean success = existingFile.renameTo(backupFile);
            if (!success) {
                throw new XPathException("Failed to create backup file of " + backupFile);
            }
        }
        if (!existingFile.exists()) {
            if (log != null) {
                log.println("Creating file " + existingFile);
            }
            try {
                //noinspection ResultOfMethodCallIgnored
                existingFile.createNewFile();
            } catch (IOException e) {
                throw new XPathException("Failed to create new file " + existingFile);
            }
        } else {
            if (log != null) {
                log.println("Overwriting file " + existingFile);
            }
        }
        Configuration config = doc.getConfiguration();
        SerializerFactory factory = config.getSerializerFactory();
        Receiver r = factory.getReceiver(new StreamResult(existingFile), new SerializationProperties(outputProperties));
        doc.copy(r, CopyOptions.ALL_NAMESPACES, Loc.NONE);
        r.close();
    }
}
