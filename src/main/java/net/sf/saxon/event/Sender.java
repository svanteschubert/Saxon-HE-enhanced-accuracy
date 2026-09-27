////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.lib.ActiveSource;
import net.sf.saxon.lib.AugmentedSource;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.type.Schema;
import net.sf.saxon.type.Type;

import javax.xml.transform.Source;
import java.util.List;

/**
 * Sender is a helper class that sends events to a Receiver from any kind of Source object
 */

public abstract class Sender {

    // Converted to an abstract static class in Saxon 9.3

    private Sender() {
    }

    /**
     * Send the contents of a Source to a Receiver.
     *
     * @param source   the source to be copied. Note that if the Source contains an InputStream
     *                 or Reader then it will be left open, unless it is an AugmentedSource with the pleaseCloseAfterUse
     *                 flag set. On the other hand, if it contains a URI that needs to be dereferenced to obtain
     *                 an InputStream, then the InputStream will be closed after use.
     * @param receiver the destination to which it is to be copied. The pipelineConfiguration
     *                 of this receiver must have been initialized. The implementation sends a sequence
     *                 of events to the {@code Receiver}, starting with an {@link Outputter#open()} call
     *                 and ending with {@link Outputter#close()}; this will be a <b>regular event sequence</b>
     *                 as defined by {@link RegularSequenceChecker}.
     * @param options  Parsing options. If source is an {@link AugmentedSource}, any options set in the
     *                 {@code AugmentedSource} are used in preference to those set in options. If neither specifies
     *                 a particular option, the defaults from the Configuration are used. If null is supplied,
     *                 the parse options from the PipelineConfiguration of the receiver are used.
     * @throws XPathException
     *          if any error occurs
     */

    public static void send(Source source, Receiver receiver, ParseOptions options)
            throws XPathException {
        Source suppliedSource = source;
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        if (options == null) {
            options = pipe.getParseOptions();
        }
        String systemId = source.getSystemId();
        AugmentedSource originalAugmentedSource = null;
        if (source instanceof AugmentedSource) {
            originalAugmentedSource = (AugmentedSource)source;
            options = options.merge(((AugmentedSource) source).getParseOptions());
            systemId = source.getSystemId();
            source = ((AugmentedSource) source).getContainedSource();
        }
        Configuration config = pipe.getConfiguration();
        options = options.applyDefaults(config);

        receiver.setSystemId(systemId);
        Receiver next = receiver;

        List<FilterFactory> filters = options.getFilters();
        if (filters != null) {
            for (int i = filters.size() - 1; i >= 0; i--) {
                FilterFactory ff = filters.get(i);   // Variable needed for C# type inference
                Receiver filter = ff.makeFilter(next);
                filter.setSystemId(source.getSystemId());
                next = filter;
            }
        }

        next = makeValidator(next, source.getSystemId(), options);


        SpaceStrippingRule strippingRule = options.getSpaceStrippingRule();
        if (strippingRule != null && !(strippingRule instanceof NoElementsSpaceStrippingRule)) {
            next = strippingRule.makeStripper(next);
        }

        if (!(source instanceof ActiveSource)) {
            source = config.resolveSource(source, options, config);
        }

        if (source == null) {
            throw new XPathException("A source of type " + suppliedSource.getClass().getName() +
                                             " is not supported in this environment");
        }

        ((ActiveSource) source).deliver(next, options);

        if (originalAugmentedSource != null && originalAugmentedSource.isPleaseCloseAfterUse()) {
            originalAugmentedSource.close();
        }

    }



    /**
     * Send a copy of a Saxon NodeInfo representing a document or element node to a receiver
     *
     * @param top      the root of the subtree to be send. Despite the method name, this can be a document
     *                 node or an element node
     * @param receiver the destination to receive the events
     * @throws XPathException if any error occurs
     * @throws IllegalArgumentException if the node is not a document or element node
     */


    public static void sendDocumentInfo(NodeInfo top, Receiver receiver, Location location)
            throws XPathException {
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        NamePool targetNamePool = pipe.getConfiguration().getNamePool();
        if (top.getConfiguration().getNamePool() != targetNamePool) {
            // This code allows a document in one Configuration to be copied to another, changing
            // namecodes as necessary
            receiver = new NamePoolConverter(receiver, top.getConfiguration().getNamePool(), targetNamePool);
        }
        LocationCopier copier = new LocationCopier(top.getNodeKind() == Type.DOCUMENT, location.getSystemId());
        pipe.setComponent(CopyInformee.class.getName(), copier);
        pipe.setCopyInformee(CSharp.methodRef(copier::notifyElementNode));

        // start event stream
        receiver.open();

        // copy the contents of the document
        switch (top.getNodeKind()) {
            case Type.DOCUMENT:
                top.copy(receiver, CopyOptions.ALL_NAMESPACES | CopyOptions.TYPE_ANNOTATIONS, location);
                break;
            case Type.ELEMENT:
                receiver.startDocument(ReceiverOption.NONE);
                top.copy(receiver, CopyOptions.ALL_NAMESPACES | CopyOptions.TYPE_ANNOTATIONS, location);
                receiver.endDocument();
                break;
            default:
                throw new IllegalArgumentException("Expected document or element node");

        }

        // end event stream
        receiver.close();
    }


    public static Receiver makeValidator(Receiver receiver, String systemId, ParseOptions options) throws XPathException {
        PipelineConfiguration pipe = receiver.getPipelineConfiguration();
        Configuration config = pipe.getConfiguration();
        int sv = options.getSchemaValidationMode();
        if (sv != Validation.PRESERVE && sv != Validation.DEFAULT && config.getEditionCode().equals("EE")) {
            Controller controller = pipe.getController();
            if (controller != null && !controller.getExecutable().isSchemaAware() && sv != Validation.STRIP) {
                throw new XPathException("Cannot use schema-validated input documents when the query/stylesheet is not schema-aware");
            }
            // Add a document validator to the pipeline if required
            Schema schema = options.getSchema();
            if (schema == null && sv == Validation.STRICT && !options.isUseXsiSchemaLocation()) {
                throw new XPathException("Cannot perform strict schema validation; no schema supplied");
            }
            receiver = config.addValidatorToPipeline(options, systemId, receiver);
        }
        return receiver;
    }


}

