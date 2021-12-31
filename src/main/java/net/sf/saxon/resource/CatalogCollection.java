////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
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
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.jiter.MappingJavaIterator;

import javax.xml.transform.Source;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
     * @param config the Saxon Configuration
     * @param collectionURI the collection URI, which represents the location
     *                      of the catalog file
     */
    public CatalogCollection(Configuration config, String collectionURI) {
        super(config);
        this.collectionURI = collectionURI;
    }


    @Override
    public Iterator<String> getResourceURIs(XPathContext context) throws XPathException {
        StandardCollectionFinder.checkNotNull(collectionURI, context);
        return catalogContents(collectionURI, context);
    }


    @Override
    public Iterator<Resource> getResources(final XPathContext context) throws XPathException {

        StandardCollectionFinder.checkNotNull(collectionURI, context);

        Iterator<String> resourceURIs = getResourceURIs(context);

        return new MappingJavaIterator<>(resourceURIs, in -> {
            try {
                if (in.startsWith("data:")) {
                    try {
                        Resource basicResource = DataURIScheme.decode(new URI(in));
                        return makeTypedResource(context.getConfiguration(), basicResource);
                    } catch (URISyntaxException | IllegalArgumentException e) {
                        throw new XPathException(e);
                    }
                } else {
                    InputDetails id = getInputDetails(in);
                    id.parseOptions = new ParseOptions(context.getConfiguration().getParseOptions());
                    id.parseOptions.setSpaceStrippingRule(whitespaceRules);
                    id.resourceUri = in;
                    return makeResource(context.getConfiguration(), id);
                }
            } catch (XPathException e) {
                int onError = params == null ? URIQueryParameters.ON_ERROR_FAIL : params.getOnError();
                if (onError == URIQueryParameters.ON_ERROR_FAIL) {
                    return new FailedResource(in, e);
                } else if (onError == URIQueryParameters.ON_ERROR_WARNING) {
                    context.getController().warning("collection(): failed to parse " + in + ": " + e.getMessage(), e.getErrorCodeLocalPart(), null);
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
     * Return a StringBuilder initialized to the contents of an InputStream
     *
     * @param in the input stream (which is consumed by this method)
     * @return the StringBuilder, initialized to the contents of this InputStream
     * @throws IOException if an error occurs reading the resource
     */

    public static StringBuilder makeStringBuilderFromStream(InputStream in, String encoding) throws IOException {
        InputStreamReader is = new InputStreamReader(in, Charset.forName(encoding));
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(is);
        String read = br.readLine();

        while (read != null) {
            sb.append(read);
            read = br.readLine();
        }
        br.close();
        return sb;
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
        ParseOptions options = new ParseOptions();
        options.setSchemaValidationMode(Validation.SKIP);
        options.setDTDValidationMode(Validation.SKIP);
        TreeInfo catalog = context.getConfiguration().buildDocumentTree(source, options);
        if (catalog == null) {
            // we failed to read the catalogue
            XPathException err = new XPathException("Failed to load collection catalog " + href);
            err.setErrorCode("FODC0004");
            err.setXPathContext(context);
            throw err;
        }

        // Now return an iterator over the documents that it refers to

        AxisIterator iter =
                catalog.getRootNode().iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        NodeInfo top = iter.next();
        if (top == null || !("collection".equals(top.getLocalPart()) && top.getURI().isEmpty())) {
            String message;
            if (top == null) {
                message = "No outermost element found in collection catalog";
            } else {
                message = "Outermost element of collection catalog should be Q{}collection " +
                        "(found Q{" + top.getURI() + "}" + top.getLocalPart() + ")";
            }
            XPathException err = new XPathException(message);
            err.setErrorCode("FODC0004");
            err.setXPathContext(context);
            throw err;
        }
        iter.close();

        String stableAtt = top.getAttributeValue("", "stable");
        if (stableAtt != null) {
            if ("true".equals(stableAtt)) {
                stable = true;
            } else if ("false".equals(stableAtt)) {
                stable = false;
            } else {
                XPathException err = new XPathException(
                        "The 'stable' attribute of element <collection> must be true or false");
                err.setErrorCode("FODC0004");
                err.setXPathContext(context);
                throw err;
            }
        }

        AxisIterator documents = top.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        List<String> result = new ArrayList<>();
        NodeInfo item;
        while ((item = documents.next()) != null) {

            if (!("doc".equals(item.getLocalPart()) &&
                          item.getURI().isEmpty())) {
                XPathException err = new XPathException("Children of <collection> element must be <doc> elements");
                err.setErrorCode("FODC0004");
                err.setXPathContext(context);
                throw err;
            }
            String hrefAtt = item.getAttributeValue("", "href");
            if (hrefAtt == null) {
                XPathException err = new XPathException("A <doc> element in the collection catalog has no @href attribute");
                err.setErrorCode("FODC0004");
                err.setXPathContext(context);
                throw err;
            }
            String uri;
            try {
                uri = ResolveURI.makeAbsolute(hrefAtt, item.getBaseURI()).toString();
            } catch (URISyntaxException e) {
                XPathException err = new XPathException("Invalid base URI or href URI in collection catalog: ("
                                                                + item.getBaseURI() + ", " + hrefAtt + ")");
                err.setErrorCode("FODC0004");
                err.setXPathContext(context);
                throw err;
            }
            result.add(uri);

        }

        return result.iterator();
    }

    // TODO: provide control over error recovery (etc) through options in the catalog file.


}
