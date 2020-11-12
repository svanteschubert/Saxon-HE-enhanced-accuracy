////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.event.CloseNotifier;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.s9api.Action;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Result;
import javax.xml.transform.TransformerException;
import java.util.ArrayList;
import java.util.List;


/**
 * This class is an implementation of the {@link ResultDocumentResolver} interface
 * that wraps a supplied {@link OutputURIResolver}. It is provided to give backwards
 * compatibility for applications that use the old {@code OutputURIResolver} interface.
 */

public class OutputURIResolverWrapper implements ResultDocumentResolver {

    private OutputURIResolver outputURIResolver;

    public OutputURIResolverWrapper(OutputURIResolver resolver) {
        this.outputURIResolver = resolver;
    }

    /**
     * Saxon calls this method when an {@code xsl:result-document} instruction
     * with an {@code href} attribute is evaluated
     * @param context the dynamic evaluation context
     * @param href the effective value of the href attribute
     * @param baseUri the base output URI of the transformation (typically, the
     *                destination of the principal output
     * @param properties
     * @return a new instance of the {@code ResultDocumentInstance} class;
     * which Saxon will then call for further information.
     */

    @Override
    public Receiver resolve(
            XPathContext context, String href, String baseUri, SerializationProperties properties)
            throws XPathException {
        OutputURIResolver r2 = outputURIResolver.newInstance();

        try {
            Result result = r2.resolve(href, baseUri);
            Action onClose = () -> {
                try {
                    r2.close(result);
                } catch (TransformerException te) {
                    throw new UncheckedXPathException(XPathException.makeXPathException(te));
                }
            };
            Receiver out;
            if (result instanceof Receiver) {
                out = (Receiver)result;
            } else {
                SerializerFactory factory = context.getConfiguration().getSerializerFactory();
                PipelineConfiguration pipe = context.getController().makePipelineConfiguration();
                pipe.setXPathContext(context);
                out = factory.getReceiver(result, properties, pipe);
            }
            List<Action> actions = new ArrayList<>();
            actions.add(onClose);
            return new CloseNotifier(out, actions);
        } catch (TransformerException e) {
            throw XPathException.makeXPathException(e);
        }
    }

    public OutputURIResolver getOutputURIResolver() {
        return outputURIResolver;
    }

}

