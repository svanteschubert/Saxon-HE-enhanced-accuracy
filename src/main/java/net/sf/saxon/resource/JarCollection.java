////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.URIQueryParameters;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Base64BinaryValue;
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.StringValue;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * A JarCollection represents a collection of resources held in a JAR or ZIP archive, accessed typically
 * using a URI using the (Java-defined) "jar" URI scheme, or simply a "file" URI where the target
 * file is a JAR or ZIP file.
 */


public class JarCollection extends AbstractResourceCollection {

    private XPathContext context;
    private String collectionURI;
    private SpaceStrippingRule whitespaceRules;


    /**
     * Create a JarCollection
     * @param context       The XPath dynamic context
     * @param collectionURI the collection URI used to identify this collection (typically but not necessarily
     *                      the location of the JAR file)
     * @param params        URI query parameters appearing on the collection URI
     */

    public JarCollection(XPathContext context, String collectionURI, URIQueryParameters params) {
        super(context.getConfiguration());
        this.context = context;
        this.collectionURI = collectionURI;
        this.params = params;

    }

    /**
     * Supply information about the whitespace stripping rules that apply to this collection.
     * This method will only be called when the collection() function is invoked from XSLT.
     *
     * @param rules the space-stripping rules that apply to this collection, derived from
     *              the xsl:strip-space and xsl:preserve-space declarations in the stylesheet
     *              package containing the call to the collection() function.
     * @return true if the collection finder intends to take responsibility for whitespace
     * stripping according to these rules; false if it wishes Saxon itself to post-process
     * any returned XML documents to strip whitespace. Returning true may either indicate
     * that the collection finder will strip whitespace before returning a document, or it
     * may indicate that it does not wish the space stripping rules to be applied.  The
     * default (returned by this method if not overridden) is false.
     */
    @Override
    public boolean stripWhitespace(SpaceStrippingRule rules) {
        this.whitespaceRules = rules;
        return true;
    }

    /**
     * Get the URI identifying this collection
     *
     * @return the collection URI
     */

    @Override
    public String getCollectionURI() {
        return collectionURI;
    }

    /**
     * Get the URIs of the resources within the collection
     *
     * @return a list of all the URIs of resources within the collection. The resources are identified
     * by URIs of the form collection-uri!entry-path
     * @throws XPathException if any error occurs accessing the JAR file contents
     * @param context dynamic evaluation context
     */

    @Override
    public Iterator<String> getResourceURIs(XPathContext context) throws XPathException {
        FilenameFilter filter = null;
        boolean recurse = false;
        if (params != null) {

            FilenameFilter f = params.getFilenameFilter();
            if (f != null) {
                filter = f;
            }

            Boolean r = params.getRecurse();
            if (r != null) {
                recurse = r;
            }

        }

        ZipInputStream zipInputStream = getZipInputStream();
        List<String> result = new ArrayList<>();
        try {
            ZipEntry entry;
            String dirStr = "";
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    dirStr = entry.getName();
                }
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();

                    if (filter != null) {
                        if (dirStr.equals("") || !entryName.contains(dirStr)) {
                            if (entryName.contains("/")) {
                                dirStr = entryName.substring(0, entryName.lastIndexOf("/"));
                            } else {
                                dirStr = "";
                            }
                        }
                        if (filter.accept(new File(dirStr), entryName)) {
                            result.add(makeResourceURI(entryName));
                        }
                    } else {
                        result.add(makeResourceURI(entryName));
                    }
                }
                entry = zipInputStream.getNextEntry();

            }
        } catch (IOException e) {
            throw new XPathException("Unable to extract entry in JAR/ZIP file: " + collectionURI, e);
        }
        return result.iterator();
    }

    private ZipInputStream getZipInputStream() throws XPathException {
        URL url;
        try {
            url = new URL(collectionURI);
        } catch (MalformedURLException e) {
            throw new XPathException("Malformed JAR/ZIP file URI: " + collectionURI, e);
        }
        URLConnection connection;
        try {
            connection = url.openConnection();
        } catch (IOException e) {
            throw new XPathException("Unable to open connection to JAR/ZIP file URI: " + collectionURI, e);
        }
        InputStream stream;
        try {
            stream = connection.getInputStream();
        } catch (IOException e) {
            throw new XPathException("Unable to get input stream for JAR/ZIP file connection: " + collectionURI, e);
        }
        return new ZipInputStream(stream);
    }

    /**
     * Get an iterator over the resources in the collection
     * @param context the XPath evaluation context
     * @return an iterator over the resources in the collection. This may include instances of
     * {@link FailedResource} if there are resources that cannot be processed for some reason.
     * @throws XPathException if it is not possible to get an iterator.
     */

    @Override
    public Iterator<Resource> getResources(XPathContext context) throws XPathException {
        FilenameFilter filter = null;
        boolean recurse = false;
        if (params != null) {

            FilenameFilter f = params.getFilenameFilter();
            if (f != null) {
                filter = f;
            }

            Boolean r = params.getRecurse();
            if (r != null) {
                recurse = r;
            }

        }
        ZipInputStream zipInputStream = getZipInputStream();
        return new JarIterator(this.context, zipInputStream, filter);
    }


    private String makeResourceURI(String entryName) {
        return
            (collectionURI.startsWith("jar:") ? "" : "jar:") +
                collectionURI + "!/" + entryName;
    }


    private class JarIterator implements Iterator<Resource>, Closeable {
        private FilenameFilter filter;
        private Resource next = null;
        private XPathContext context;
        private ZipInputStream zipInputStream;
        private String dirStr = "";
        private ParseOptions options;
        private boolean metadata;


        public JarIterator(XPathContext context, ZipInputStream zipInputStream, FilenameFilter filter) {
            this.context = context;
            this.filter = filter;
            this.zipInputStream = zipInputStream;
            this.options = optionsFromQueryParameters(params, context);
            this.options.setSpaceStrippingRule(whitespaceRules);
            Boolean metadataParam = params == null ? null : params.getMetaData();
            metadata = metadataParam != null && metadataParam;

            advance();
        }

        @Override
        public boolean hasNext() {
            boolean more = next != null;
            if (!more) {
                try {
                    zipInputStream.close();
                } catch (IOException e) {
                    throw new UncheckedXPathException(new XPathException(e));
                }
            }
            return more;
        }

        @Override
        public Resource next() {
            Resource current = next;
            advance();
            return current;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void advance() {
            while (true) {
                ZipEntry entry;
                try {
                    entry = zipInputStream.getNextEntry();
                    if (entry == null) {
                        next = null;
                        return;
                    }
                } catch (IOException e) {
                    next = new FailedResource(null, new XPathException(e));
                    break;
                }
                if (entry.isDirectory()) {
                    dirStr = entry.getName();
                } else {
                    String entryName = entry.getName();
                    if (filter != null) {
                        if (dirStr.equals("") || !entryName.contains(dirStr)) {
                            if (entryName.contains("/")) {
                                dirStr = entryName.substring(0, entryName.lastIndexOf("/"));
                            } else {
                                dirStr = "";
                            }
                        }
                        if (!filter.accept(new File(dirStr), entryName)) {
                            continue;
                        }
                    }
                    String resourceURI = null;
                    try {
                        InputStream is = zipInputStream;
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        try {
                            byte[] buffer = new byte[4096];
                            int len = 0;
                            while ((len = is.read(buffer)) > 0) {
                                output.write(buffer, 0, len);
                            }
                        } catch (IOException err) {
                            throw new UncheckedXPathException(new XPathException(err));
                        } finally {
                            // we must always close the output file
                            try {
                                output.close();
                            } catch (IOException e) {
                                next = new FailedResource(null, new XPathException(e));
                            }
                        }
                        InputDetails details = new InputDetails();
                        details.binaryContent = output.toByteArray();
                        if (params != null && params.getContentType() != null) {
                            details.contentType = params.getContentType();
                        } else {
                            details.contentType = guessContentTypeFromName(entry.getName());
                        }
                        if (details.contentType == null) {
                            ByteArrayInputStream bais = new ByteArrayInputStream(details.binaryContent);
                            details.contentType = guessContentTypeFromContent(bais);
                            try {
                                bais.close();
                            } catch (IOException e) {
                                details.contentType = null;
                            }
                        }
                        details.parseOptions = options;
                        resourceURI = makeResourceURI(entry.getName());
                        details.resourceUri = resourceURI;
                        next = makeResource(context.getConfiguration(), details);
                        if (metadata) {
                            Map<String, GroundedValue> properties = makeProperties(entry);
                            next = new MetadataResource(resourceURI, next, properties);
                        }
                        return;
                    } catch (XPathException e) {
                        next = new FailedResource(resourceURI, e);
                    }
                }
            }

        }

        @Override
        public void close() throws IOException {
            zipInputStream.close();
        }
    }

    /**
     * Get the properties of a Zip file entry, for use when returning a MetadataResource containing this information
     * @param entry the current Zip file entry
     * @return a map containing the properties of this entry: comment, compressed-size, crc, extra, compression-method,
     * entry-name, size, and last-modified.
     */

    protected Map<String, GroundedValue> makeProperties(ZipEntry entry) {
        HashMap<String, GroundedValue> map = new HashMap<>(10);
        map.put("comment", StringValue.makeStringValue(entry.getComment()));
        map.put("compressed-size", new Int64Value(entry.getCompressedSize()));
        map.put("crc", new Int64Value(entry.getCrc()));
        byte[] extra = entry.getExtra();
        if (extra != null) {
            map.put("extra", new Base64BinaryValue(extra));
        }
        map.put("compression-method", new Int64Value(entry.getMethod()));
        map.put("entry-name", StringValue.makeStringValue(entry.getName()));
        map.put("size", new Int64Value(entry.getSize()));
        try {
            map.put("last-modified", DateTimeValue.fromJavaTime(entry.getTime()));
        } catch (XPathException err) {
            // ignore the failure
        }
        return map;
    }


}
