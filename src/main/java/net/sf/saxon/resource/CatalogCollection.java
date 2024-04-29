////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.DocumentFn;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.functions.URIQueryParameters;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.jiter.MappingJavaIterator;

import javax.xml.transform.Source;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * A resource collection implemented by means of a catalog file.
 *
 * <p>An example catalog file would be:</p>
 *
 * <pre>
 * {@code
 * <collection stable="true">
 *    <doc href="dir/contents.json"/>
 *    <doc href="dir/chap1.xml"/>
 *    <doc href="dir/chap2.xml"/>
 *    <doc href="dir/chap3.xml"/>
 *    <doc href="dir/chap4.xml"/>
 *    <doc href="dir/index.json"/>
 * </collection>
 * }
 * </pre>
 */

public class CatalogCollection extends AbstractResourceCollection {

    private boolean stable;
    private SpaceStrippingRule whitespaceRules;

    /**
     * Create a catalog collection
     *
     * @param config        the Saxon Configuration
     * @param collectionURI the collection URI, which represents the location
     *                      of the catalog file
     */

    public CatalogCollection(Configuration config, String collectionURI) {
        super(config);
        this.collectionURI = collectionURI;
    }


    @Override
    public Iterator<String> getResourceURIs(XPathContext context) throws XPathException {
        AbstractResourceCollection.checkNotNull(collectionURI, context);
        return catalogContents(collectionURI, context);
    }


    @Override
    public Iterator<? extends Resource> getResources(final XPathContext context) throws XPathException {

        AbstractResourceCollection.checkNotNull(collectionURI, context);

        Iterator<String> resourceURIs = getResourceURIs(context);

        return new MappingJavaIterator<String, Resource>(resourceURIs, input -> {
            @SuppressWarnings("UnnecessaryLocalVariable")    // type information needed by transpiler
            String uri = input;
            try {
                if (uri.startsWith("data:")) {
                    try {
                        Resource basicResource = DataURIScheme.decode(new URI(uri));
                        return makeTypedResource(context, basicResource);
                    } catch (URISyntaxException | IllegalArgumentException e) {
                        throw new XPathException(e);
                    }
                } else {
                    InputDetails id = getInputDetails(uri);
                    id.parseOptions = context.getConfiguration().getParseOptions()
                        .withSpaceStrippingRule(whitespaceRules);
                    id.resourceUri = uri;
                    return makeResource(context, id);
                }
            } catch (XPathException e) {
                Optional<Integer> onError = Optional.of(URIQueryParameters.ON_ERROR_FAIL);
                if (params != null) {
                    onError = params.getOnError();
                }
                if (onError.isPresent() && onError.get() == URIQueryParameters.ON_ERROR_FAIL) {
                    return new FailedResource(uri, e);
                } else if (onError.isPresent() && onError.get() == URIQueryParameters.ON_ERROR_WARNING) {
                    context.getController().warning("collection(): failed to parse " + uri + ": " + e.getMessage(), e.showErrorCode(), null);
                    return null;
                } else {
                    return null;
                }
            }
        });

    }


    @Override
    public boolean isStable(XPathContext context) {
        return stable;
    }

    /**
     * Return a String initialized to the contents of an InputStream
     *
     * @param input the input stream (which is consumed by this method)
     * @param encoding the character encoding of the input stream
     * @return the String, initialized to the contents of this InputStream
     * @throws IOException if an error occurs reading the resource
     */

    @CSharpReplaceBody(code="return new System.IO.StreamReader(input, System.Text.Encoding.GetEncoding(encoding)).ReadToEnd();")
    public static String makeStringFromStream(InputStream input, String encoding) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int length; (length = input.read(buffer)) != -1; ) {
            result.write(buffer, 0, length);
        }
        return result.toString(encoding);
    }


    /**
     * Return a collection defined as a list of URIs in a catalog file
     *
     * @param href    the absolute URI of the catalog file
     * @param context the dynamic evaluation context
     * @return an iterator over the documents in the collection
     * @throws XPathException if any failures occur
     */

    protected Iterator<String> catalogContents(String href, final XPathContext context)
            throws XPathException {

        Source source = DocumentFn.resolveURI(href, null, null, context);
        ParseOptions options = new ParseOptions()
                .withSchemaValidationMode(Validation.SKIP)
                .withDTDValidationMode(Validation.SKIP);
        TreeInfo catalog = context.getConfiguration().buildDocumentTree(source, options);
        if (catalog == null) {
            // we failed to read the catalogue
            throw new XPathException("Failed to load collection catalog " + href)
                    .withErrorCode("FODC0004")
                    .withXPathContext(context);
        }

        // Now return an iterator over the documents that it refers to

        AxisIterator iter =
                catalog.getRootNode().iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        NodeInfo top = iter.next();
        if (top == null || !("collection".equals(top.getLocalPart()) && top.getNamespaceUri() == NamespaceUri.NULL)) {
            String message;
            if (top == null) {
                message = "No outermost element found in collection catalog";
            } else {
                message = "Outermost element of collection catalog should be Q{}collection " +
                        "(found Q{" + top.getNamespaceUri() + "}" + top.getLocalPart() + ")";
            }
            throw new XPathException(message)
                    .withErrorCode("FODC0004")
                    .withXPathContext(context);
        }
        iter.close();

        String stableAtt = top.getAttributeValue(NamespaceUri.NULL, "stable");
        if (stableAtt != null) {
            if ("true".equals(stableAtt)) {
                stable = true;
            } else if ("false".equals(stableAtt)) {
                stable = false;
            } else {
                throw new XPathException(
                        "The 'stable' attribute of element <collection> must be true or false")
                        .withErrorCode("FODC0004")
                        .withXPathContext(context);
            }
        }

        AxisIterator documents = top.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        List<String> result = new ArrayList<>();
        NodeInfo item;
        while ((item = documents.next()) != null) {

            if (!("doc".equals(item.getLocalPart()) &&
                          item.getNamespaceUri() == NamespaceUri.NULL)) {
                throw new XPathException("Children of <collection> element must be <doc> elements")
                        .withErrorCode("FODC0004")
                        .withXPathContext(context);
            }
            String hrefAtt = item.getAttributeValue(NamespaceUri.NULL, "href");
            if (hrefAtt == null) {
                throw new XPathException("A <doc> element in the collection catalog has no @href attribute")
                        .withErrorCode("FODC0004")
                        .withXPathContext(context);
            }
            String uri;
            try {
                uri = ResolveURI.makeAbsolute(hrefAtt, item.getBaseURI()).toString();
            } catch (URISyntaxException e) {
                throw new XPathException("Invalid base URI or href URI in collection catalog: ("
                                                                + item.getBaseURI() + ", " + hrefAtt + ")")
                        .withErrorCode("FODC0004")
                        .withXPathContext(context);
            }
            result.add(uri);

        }

        return result.iterator();
    }

    // TODO: provide control over error recovery (etc) through options in the catalog file.


}
