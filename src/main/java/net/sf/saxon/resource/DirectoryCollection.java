////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.resource;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.URIQueryParameters;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Resource;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.jiter.MappingJavaIterator;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.StringValue;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * This class represents a resource collection containing all, or selected, files within a filestore
 * directory.
 */

public class DirectoryCollection extends AbstractResourceCollection {

    private File dirFile;
    private SpaceStrippingRule whitespaceRules;

    /**
     * Create a directory collection
     * @param collectionURI the collection URI
     * @param file the directory containing the files
     * @param params query parameters supplied as part of the URI
     */
    public DirectoryCollection(Configuration config, String collectionURI,
                               File file, URIQueryParameters params) throws XPathException {
        super(config);
        if (collectionURI == null) {
            throw new NullPointerException();
        }
        this.collectionURI = collectionURI;
        dirFile = file;
        if (params == null) {
            this.params = new URIQueryParameters("", config);
        } else {
            this.params = params;
        }
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

    @Override
    public Iterator<String> getResourceURIs(XPathContext context) {
        return directoryContents(dirFile, params);
    }

    @Override
    public Iterator<Resource> getResources(final XPathContext context) {
        final ParseOptions options = optionsFromQueryParameters(params, context);
        options.setSpaceStrippingRule(whitespaceRules);
        Boolean metadataParam = params.getMetaData();
        final boolean metadata = metadataParam != null && metadataParam;
        Iterator<String> resourceURIs = getResourceURIs(context);

        return new MappingJavaIterator<>(resourceURIs,
             in -> {
                 try {
                     InputDetails details = getInputDetails(in);
                     details.resourceUri = in;
                     details.parseOptions = options;
                     if (params.getContentType() != null) {
                         details.contentType = params.getContentType();
                     }
                     Resource resource = makeResource(context.getConfiguration(), details);
                     if (resource != null) {
                         if (metadata) {
                             return makeMetadataResource(resource, details);
                         } else {
                             return resource;
                         }
                     }
                     return null;
                 } catch (XPathException e) {
                     int onError = params.getOnError();
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

    /**
     * Make a metadata resource: that is, a resource containing not only the content of a file within
     * the directory structure, but properties of that file in the form of name/value pairs.
     * @param resource a resource representing the content of the file
     * @param details details about where the resource came from
     * @return a MetadataResource, being an object containing both the original content resource,
     * and an extensible set of properties of that resource.
     */

    private MetadataResource makeMetadataResource(Resource resource, InputDetails details) {
        Map<String, GroundedValue> properties = new HashMap<>();
        try {

            URI uri = new URI(resource.getResourceURI());

            if (details.contentType != null) {
                properties.put("content-type", StringValue.makeStringValue(details.contentType));
            }
            if (details.encoding != null) {
                properties.put("encoding", StringValue.makeStringValue(details.encoding));
            }

            if ("file".equals(uri.getScheme())) {
                File file = new File(uri);
                properties.put("path", StringValue.makeStringValue(file.getPath()));
                properties.put("absolute-path", StringValue.makeStringValue(file.getAbsolutePath()));
                properties.put("canonical-path", StringValue.makeStringValue(file.getCanonicalPath()));
                properties.put("can-read", BooleanValue.get(file.canRead()));
                properties.put("can-write", BooleanValue.get(file.canWrite()));
                properties.put("can-execute", BooleanValue.get(file.canExecute()));
                properties.put("is-hidden", BooleanValue.get(file.isHidden()));
                try {
                    properties.put("last-modified", DateTimeValue.fromJavaTime(file.lastModified()));
                } catch (XPathException e) {
                    // ignore the failure
                }
                properties.put("length", new Int64Value(file.length()));

            }
        } catch (URISyntaxException | IOException e) {
            // ignore
        }

        return new MetadataResource(resource.getResourceURI(), resource, properties);
    }


    /**
     * Return the contents of a collection that maps to a directory in filestore
     *
     * @param directory the directory to be processed
     * @param params    parameters indicating whether to process recursively, what to do on
     *                  errors, and which files to select
     * @return an iterator over the absolute URIs of the documents in the collection
     */

    protected Iterator<String> directoryContents(File directory, URIQueryParameters params) {

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

        Stack<Iterator<File>> directories = new Stack<>();
        directories.push(Arrays.asList(directory.listFiles(filter)).iterator());
        return new DirectoryIterator(directories, recurse, filter);

    }

    private static class DirectoryIterator implements Iterator<String> {

        private Stack<Iterator<File>> directories;
        private FilenameFilter filter;
        private boolean recurse;
        private String next = null;

        public DirectoryIterator(Stack<Iterator<File>> directories, boolean recurse, FilenameFilter filter) {
            this.directories = directories;
            this.recurse = recurse;
            this.filter = filter;
            advance();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public String next() {
            String s = next;
            advance();
            return s;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void advance() {
            if (directories.isEmpty()) {
                next = null;
            } else {
                Iterator<File> files = directories.peek();
                while (!files.hasNext()) {
                    directories.pop();
                    if (directories.isEmpty()) {
                        next = null;
                        return;
                    }
                    files = directories.peek();
                }
                File nextFile = files.next();
                if (nextFile.isDirectory()) {
                    if (recurse) {
                        directories.push(Arrays.asList(nextFile.listFiles(filter)).iterator());
                    }
                    advance();
                } else {
                    next = nextFile.toURI().toString();
                }
            }
        }
    }





}
