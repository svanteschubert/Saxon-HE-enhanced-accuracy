////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.lib.CollectionFinder;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.ResourceCollection;
import net.sf.saxon.om.*;
import net.sf.saxon.resource.AbstractResourceCollection;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.wrapper.SpaceStrippedDocument;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.ExternalObject;
import net.sf.saxon.value.ObjectValue;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Implement the fn:collection() function. This is responsible for calling the
 * registered {@link CollectionFinder}. For the effect of the default
 * system-supplied CollectionFinder, see {@link net.sf.saxon.resource.StandardCollectionFinder}
 */

public class CollectionFn extends SystemFunction implements Callable {

    /**
     * URI representing a collection that is always empty, regardless of any collection URI resolver
     */
    public static String EMPTY_COLLECTION_URI = "http://saxon.sf.net/collection/empty";

    /**
     * An empty collection
     */

    public final static ResourceCollection EMPTY_COLLECTION = new EmptyCollection(EMPTY_COLLECTION_URI);

    /**
     * Implementation of an empty collection
     */

    private static class EmptyCollection implements ResourceCollection {
        private String collectionUri;

        EmptyCollection(String cUri) {
            collectionUri = cUri;
        }

        @Override
        public String getCollectionURI() {
            return collectionUri;
        }

        @Override
        public Iterator<String> getResourceURIs(XPathContext context) {
            return new ArrayList<String>().iterator();
        }

        @Override
        public Iterator<Resource> getResources(XPathContext context) {
            return new ArrayList<Resource>().iterator();
        }

        @Override
        public boolean isStable(XPathContext context) {
            return true;
        }

        @Override
        public boolean stripWhitespace(SpaceStrippingRule rules) {
            return false;
        }
    }

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        // See redmine bug 1652. We cannot assume that the nodes will be in document order because we can't assume
        // they will all be "new" documents. We can't even assume that they will be distinct.
        return (super.getSpecialProperties(arguments) & ~StaticProperty.NO_NODES_NEWLY_CREATED) | StaticProperty.PEER_NODESET;
    }

    private String getAbsoluteCollectionURI(String href, XPathContext context) throws XPathException {
        String absoluteURI;
        if (href == null) {
            absoluteURI = context.getConfiguration().getDefaultCollection();
        } else {
            URI uri;
            try {
                uri = new URI(href);
            } catch (URISyntaxException e) {
                href = IriToUri.iriToUri(href).toString();
                try {
                    uri = new URI(href);
                } catch (URISyntaxException e2) {
                    throw new XPathException(e2.getMessage(), "FODC0004");
                }
            }
            try {
                if (uri.isAbsolute()) {
                    absoluteURI = uri.toString();
                } else {
                    String base = getRetainedStaticContext().getStaticBaseUriString();
                    if (base != null) {
                        absoluteURI = ResolveURI.makeAbsolute(href, base).toString();
                    } else {
                        throw new XPathException("Relative collection URI cannot be resolved: no base URI available", "FODC0002");
                    }
                }
            } catch (URISyntaxException e) {
                throw new XPathException(e.getMessage(), "FODC0004");
            }
        }
        return absoluteURI;
    }

    /**
     * Get an iterator of the Resources in a ResourceCollection, returned in the form of external objects wrapping
     * a Resource object
     * @param collection the resource collection
     * @param context the XPath dynamic context
     * @return a SequenceIterator delivering ObjectValue&lt;Resource> items
     * @throws XPathException if a dynamic error occurs
     */

    private SequenceIterator getSequenceIterator(
            final ResourceCollection collection, final XPathContext context) throws XPathException {

        final Iterator<? extends Resource> sources = collection.getResources(context);

        return new SequenceIterator() {

            @Override
            public Item next() throws XPathException {
                try {
                    if (sources.hasNext()) {
                        return new ObjectValue<Resource>(sources.next());
                    } else {
                        return null;
                    }
                } catch (Exception e) {
                    throw XPathException.makeXPathException(e);
                }
            }

            @Override
            public void close() {
                if (sources instanceof Closeable) {
                    try {
                        ((Closeable)sources).close();
                    } catch (IOException e) {
                        throw new UncheckedXPathException(new XPathException(e));
                    }
                }
            }


        };
    }

    /**
     * Dynamic call on collection() function
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     * @return the sequence of nodes forming the collection
     * @throws XPathException if a dynamic error occurs
     */

    @Override
    public Sequence call(final XPathContext context, Sequence[] arguments) throws XPathException {
        String href;
        if (getArity() == 0) {
            // No arguments supplied: this gets the default collection
            href = context.getController().getDefaultCollection();
        } else {
            Item arg = arguments[0].head();
            if (arg == null) {
                href = context.getController().getDefaultCollection();
            } else {
                href = arg.getStringValue();
            }
        }

        if (href == null) {
            throw new XPathException("No default collection has been defined", "FODC0002");
        }

        String absoluteURI = getAbsoluteCollectionURI(href, context);

        // See if the collection has been cached

        PackageData packageData = getRetainedStaticContext().getPackageData();
        SpaceStrippingRule whitespaceRule = NoElementsSpaceStrippingRule.getInstance();
        String collectionKey = absoluteURI;
        if (packageData.isXSLT()) {
            whitespaceRule = ((StylesheetPackage) packageData).getSpaceStrippingRule();
            if (whitespaceRule != NoElementsSpaceStrippingRule.getInstance()) {
                collectionKey = ((StylesheetPackage) packageData).getPackageName() +
                        ((StylesheetPackage) packageData).getPackageVersion() +
                        " " +
                        absoluteURI;
            }
        }

        GroundedValue cachedCollection = (GroundedValue)context.getController().getUserData("saxon:collections", collectionKey);
        if (cachedCollection != null) {
            return cachedCollection;
        }

        // Call the user-supplied CollectionFinder to get the ResourceCollection

        CollectionFinder collectionFinder = context.getController().getCollectionFinder();
        ResourceCollection collection = collectionFinder.findCollection(context, absoluteURI);
        if (collection == null) {
            collection = new EmptyCollection(EMPTY_COLLECTION_URI);
        }

        // In XSLT, worry about whitespace stripping

        if (packageData instanceof StylesheetPackage && whitespaceRule != NoElementsSpaceStrippingRule.getInstance()) {
            if (collection instanceof AbstractResourceCollection) {
                boolean alreadyStripped = collection.stripWhitespace(whitespaceRule);
                if (alreadyStripped) {
                    whitespaceRule = null;
                }
            }
        }

        // Get an iterator over the resources in the collection
        SequenceIterator sourceSeq = getSequenceIterator(collection, context);

        // Get an iterator over the items representing the resources
        SequenceIterator result = context.getConfiguration()
                .getMultithreadedItemMappingIterator(sourceSeq,
                                                     item1 -> ((ExternalObject<Resource>)item1).getObject().getItem(context));

        // In XSLT, apply space-stripping to document nodes in the collection
        if (whitespaceRule != null) {
            final SpaceStrippingRule rule = whitespaceRule;
            ItemMappingFunction stripper = item -> {
                if (item instanceof NodeInfo && ((NodeInfo) item).getNodeKind() == Type.DOCUMENT) {
                    TreeInfo treeInfo = ((NodeInfo) item).getTreeInfo();
                    if (treeInfo.getSpaceStrippingRule() != rule) {
                        return new SpaceStrippedDocument(treeInfo, rule).getRootNode();
                    }
                }
                return item;
            };
            result = new ItemMappingIterator(result, stripper);
        }

        // If the collection is stable, cache the result
        if (collection.isStable(context) || context.getConfiguration().getBooleanProperty(Feature.STABLE_COLLECTION_URI)) {
            Controller controller = context.getController();
            DocumentPool docPool = controller.getDocumentPool();
            cachedCollection = result.materialize();
            SequenceIterator iter = cachedCollection.iterate();
            Item item;
            while ((item = iter.next()) != null) {
                if (item instanceof NodeInfo && ((NodeInfo)item).getNodeKind() == Type.DOCUMENT) {
                    String docUri = ((NodeInfo)item).getSystemId();
                    DocumentKey docKey = new DocumentKey(docUri);
                    TreeInfo info = item instanceof TreeInfo ? (TreeInfo)item : new GenericTreeInfo(controller.getConfiguration(), (NodeInfo)item);
                    docPool.add(info, docKey);
                }
            }
            context.getController().setUserData("saxon:collections", collectionKey, cachedCollection);
            return cachedCollection;
        }

        return new LazySequence(result);
    }

}

