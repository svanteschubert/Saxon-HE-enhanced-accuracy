////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.style.PackageVersion;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * This class encapsulates a string used as the value of the document-uri() property of a document,
 * together with a normalized representation of the string used for equality comparisons. The idea
 * is that on Windows systems, document URIs are compared using case-blind comparison, but the original
 * case is retained for display purposes.
 *
 * <p>The package name and version of the document reference are retained, because calls of doc()
 * in different packages, using the same absolute URI, may return different documents, as a result
 * of the treatment of whitespace and type annotations varying.</p>
 */
public class DocumentKey {

    public final static boolean CASE_BLIND_FILES = new File("a").equals(new File("A"));

    private String displayValue;
    private String normalizedValue;
    private String packageName = "";
    private PackageVersion packageVersion = PackageVersion.ONE;

    /**
     * Create a DocumentURI object that wraps a given URI
     *
     * @param uri the URI to be wrapped. Must not be null
     * @throws NullPointerException if uri is null
     */

    public DocumentKey(String uri) {
        Objects.requireNonNull(uri);
        this.displayValue = uri;
        this.normalizedValue = normalizeURI(uri);
    }

    public DocumentKey(String uri, String packageName, PackageVersion version) {
        Objects.requireNonNull(uri);
        this.displayValue = uri;
        this.normalizedValue = normalizeURI(uri);
        this.packageName = packageName;
        this.packageVersion = version;
    }

    public String getAbsoluteURI() {
        return displayValue;
    }


    @Override
    public String toString() {
        return displayValue;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DocumentKey
                && normalizedValue.equals(((DocumentKey) obj).normalizedValue)
                && packageName.equals(((DocumentKey)obj).packageName)
                && packageVersion.equals(((DocumentKey)obj).packageVersion);
    }

    @Override
    public int hashCode() {
        return normalizedValue.hashCode();
    }

    /**
     * Normalize the representation of file: URIs to give better equality matching than straight
     * string comparison. The main purpose is (a) to eliminate the distinction between "file:/" and
     * "file:///", and (b) to normalize case in the case of Windows filenames: especially the distinction
     * between "file:/C:" and "file:/c:".
     *
     * @param uri the URI to be normalized
     * @return the normalized URI.
     */

    public static String normalizeURI(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.startsWith("FILE:")) {
            uri = "file:" + uri.substring(5);
        }
        if (uri.startsWith("file:")) {
            if (uri.startsWith("file:///")) {
                uri = "file:/" + uri.substring(8);
            }
            if (uri.startsWith("file:/")) {
                // Use getCanonicalPath() to remove any "." and ".." path segments
                try {
                    String cpath = new File(uri.substring(5)).getCanonicalPath();
                    uri = "file:" + cpath;
                } catch (IOException ioe) {
                    // nop
                }
            }
            if (CASE_BLIND_FILES) {
                uri = uri.toLowerCase();
            }
        }
        return uri;
    }
}

