////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.expr.sort.LRUCache;
import net.sf.saxon.functions.IriToUri;
import net.sf.saxon.value.Whitespace;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * This class checks whether a string is a valid URI. Different checking rules can be chosen by including
 * a different URIChecker in the {@link ConversionRules} used when the value is checked.
 */
public class StandardURIChecker implements URIChecker {

    private static StandardURIChecker THE_INSTANCE = new StandardURIChecker();

    public static StandardURIChecker getInstance() {
        return THE_INSTANCE;
    }

    /**
     * To prevent repeated validation of commonly used URIs (especially namespaces)
     * we keep a small cache. This is especially useful in the case of URIs that are
     * valid only after escaping, as otherwise an exception occurs during the validation process
     */

    /*@NotNull*/ private static ThreadLocal<LRUCache<CharSequence, Boolean>> caches =
            new ThreadLocal<LRUCache<CharSequence, Boolean>>();

    /**
     * Protected constructor to allow subclassing
     */

    protected StandardURIChecker() {
    }

    /**
     * Validate a string to determine whether it is a valid URI
     *
     * @param value the string to be checked
     * @return true if the string is considered to be a valid URI
     */

    @Override
    public boolean isValidURI(CharSequence value) {
        LRUCache<CharSequence, Boolean> cache = caches.get();
        if (cache == null) {
            cache = new LRUCache<CharSequence, Boolean>(50);
            caches.set(cache);
        }

        if (cache.get(value) != null) {
            return true;
        }

        String sv = Whitespace.trim(value);

        // Allow zero-length strings (RFC2396 is ambivalent on this point)
        if (sv.isEmpty()) {
            return true;
        }

        // Allow a string if the java.net.URI class accepts it
        try {
            new URI(sv);
            cache.put(value, Boolean.TRUE);
            return true;
        } catch (URISyntaxException e) {
            // keep trying
            // Note: it's expensive to throw exceptions on a success path, so we keep a cache.
        }

        // Allow a string if it can be escaped into a form that java.net.URI accepts
        sv = IriToUri.iriToUri(sv).toString();
        try {
            new URI(sv);
            cache.put(value, Boolean.TRUE);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static void main(String[] args) {
        System.err.println(args[0] + " is valid? - " + getInstance().isValidURI(args[0]));
    }
}

