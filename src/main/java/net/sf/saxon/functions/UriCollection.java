////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.CollectionFinder;
import net.sf.saxon.lib.ResourceCollection;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.jiter.MappingJavaIterator;
import net.sf.saxon.value.AnyURIValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

/**
 * Implement the fn:uri-collection() function (new in XQuery 3.0/XSLT 3.0). This is responsible for calling the
 * registered {@link net.sf.saxon.lib.CollectionFinder}. For the effect of the default
 * system-supplied CollectionFinder, see {@link net.sf.saxon.resource.StandardCollectionFinder}
 */

public class UriCollection extends SystemFunction {

    //@CSharpInnerClass(outer=false, extra={"java.util.Iterator<string> sources"})
    private SequenceIterator getUris(final String absoluteURI, final XPathContext context) throws XPathException {

        // Use a collection registered with the configuration if there is one

        ResourceCollection collection = context.getConfiguration().getRegisteredCollection(absoluteURI);

        // Call the user-supplied CollectionFinder to get the ResourceCollection

        if (collection == null) {
            CollectionFinder collectionFinder = context.getController().getCollectionFinder();
            if (collectionFinder != null) {
                collection = collectionFinder.findCollection(context, absoluteURI);
            }
        }

        if (collection == null) {
            // Should not happen, we're calling user code so we check for it.
            throw new XPathException("No collection has been defined for href: " + (absoluteURI == null ? "" : absoluteURI))
                    .withErrorCode("FODC0002").withXPathContext(context);

        }
        final Iterator<String> sources = collection.getResourceURIs(context);
        final Iterator<AnyURIValue> uris = new MappingJavaIterator<String, AnyURIValue>(sources, s -> new AnyURIValue(s));
        return new IteratorWrapper(uris);
    }

    private Sequence getDefaultUriCollection(XPathContext context) throws XPathException {
        String href = context.getConfiguration().getDefaultCollection();
        if (href == null) {
            throw new XPathException("No default collection has been defined", "FODC0002");
        } else {
            return new LazySequence(getUris(href, context));
        }
    }


    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    //@CSharpReplaceBody(code="throw new NotImplementedException();")
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        if (arguments.length == 0) {
            return getDefaultUriCollection(context);
        } else {
            Item arg = arguments[0].head();
            if (arg == null) {
                return getDefaultUriCollection(context);
            }
            String href = arg.getStringValue();
            URI hrefURI;
            try {
                hrefURI = new URI(href);
            } catch (URISyntaxException e) {
                throw new XPathException("Invalid URI passed to uri-collection: " + href, "FODC0004");
            }
            if (!hrefURI.isAbsolute()) {
                URI staticBaseUri = getRetainedStaticContext().getStaticBaseUri();
                if (staticBaseUri == null) {
                    throw new XPathException("No base URI available for uri-collection", "FODC0002");
                }
                hrefURI = staticBaseUri.resolve(hrefURI);
            }
            return new LazySequence(getUris(hrefURI.toString(), context));
        }

    }


}

