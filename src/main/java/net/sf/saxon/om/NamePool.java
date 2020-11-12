////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.lib.NamespaceConstant;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A NamePool holds a collection of expanded names, each containing a namespace URI,
 * and a local name.
 * <p>Each expanded name is allocated a unique 20-bit fingerprint. The fingerprint enables
 * the URI and the local name to be determined. Some subsystems (notably the Tinytree) use
 * the top 10 bits to represent the prefix, but the NamePool is no longer concerned with
 * managing prefixes, and prefixes do not have global codes.</p>
 * <p>The NamePool has been redesigned in Saxon 9.7 to make use of two Java
 * ConcurrentHashMaps, one from QNames to integers, one from integers to QNames.
 * This gives better scaleability in terms of multithreaded concurrency and in terms
 * of the capacity of the NamePool and retention of performance as the size of
 * the vocabulary increases.</p>
 * <p>Fingerprints in the range 0 to 1023 are reserved for system use, and are allocated as constants
 * mainly to names in the XSLT and XML Schema namespaces: constants representing these names
 * are found in {@link StandardNames}.</p>

 * <p>The fingerprint -1 is reserved to mean "not known" or inapplicable.</p>
 * <p>Modified in 9.4 to remove namespace codes.</p>
 * <p>Modified in 9.7 to remove URI codes.</p>
 * <p>Modified in 9.8 to remove namecodes and all handling of prefixes.</p>
 */

public final class NamePool {

    /**
     * FP_MASK is a mask used to obtain a fingerprint from a nameCode. Given a
     * nameCode nc, the fingerprint is <code>nc &amp; NamePool.FP_MASK</code>.
     * (In practice, Saxon code often uses the literal constant 0xfffff,
     * to extract the bottom 20 bits).
     * <p>The difference between a fingerprint and a nameCode is that
     * a nameCode contains information
     * about the prefix of a name, the fingerprint depends only on the namespace
     * URI and local name. Note that the "null" nameCode (-1) does not produce
     * the "null" fingerprint (also -1) when this mask is applied.</p>
     */

    public static final int FP_MASK = 0xfffff;

    // Since fingerprints in the range 0-1023 belong to predefined names, user-defined names
    // will always have a fingerprint above this range, which can be tested by a mask.

    public static final int USER_DEFINED_MASK = 0xffc00;

    // Limit: maximum number of fingerprints

    private static final int MAX_FINGERPRINT = FP_MASK;

    // A map from QNames to fingerprints

    private final ConcurrentHashMap<StructuredQName, Integer> qNameToInteger = new ConcurrentHashMap<>(1000);

    // A map from fingerprints to QNames

    private final ConcurrentHashMap<Integer, StructuredQName> integerToQName = new ConcurrentHashMap<>(1000);

    // Next fingerprint available to be allocated. Starts at 1024 as low-end fingerprints are statically allocated to system-defined
    // names

    private AtomicInteger unique = new AtomicInteger(1024);

    // A map containing suggested prefixes for particular URIs

    private ConcurrentHashMap<String, String> suggestedPrefixes = new ConcurrentHashMap<>();



    /**
     * Create a NamePool
     */

    public NamePool() {

    }

    /**
     * Suggest a preferred prefix to be used with a given URI
     * @param uri the URI
     * @param prefix the suggested prefix
     */

    public void suggestPrefix(String prefix, String uri) {
        suggestedPrefixes.put(uri, prefix);
    }

    /**
     * Get a QName for a given namecode.
     *
     * @param nameCode a code identifying an expanded QName, e.g. of an element or attribute
     * @return a qName containing the URI and local name corresponding to the supplied name code.
     * The prefix will be set to an empty string.
     */

    public StructuredQName getUnprefixedQName(int nameCode) {
        int fp = nameCode & FP_MASK;
        if ((fp & USER_DEFINED_MASK) == 0) {
            return StandardNames.getUnprefixedQName(fp);
        }
        return integerToQName.get(fp);
    }

    /**
     * Get a QName for a given fingerprint.
     *
     * @param fingerprint a code identifying an expanded QName, e.g. of an element or attribute
     * @return a qName containing the URI and local name corresponding to the supplied fingerprint.
     * There will be no prefix
     */

    public StructuredQName getStructuredQName(int fingerprint) {
        return getUnprefixedQName(fingerprint);
    }

    /**
     * Determine whether a given namecode has a non-empty prefix (and therefore, in the case of attributes,
     * whether the name is in a non-null namespace
     *
     * @param nameCode the name code to be tested
     * @return true if the name has a non-empty prefix
     */

    public static boolean isPrefixed(int nameCode) {
        return (nameCode & 0x3ff00000) != 0;
    }

    /**
     * Suggest a prefix for a given URI. If there are several, it's undefined which one is returned.
     * If there are no prefixes registered for this URI, return null.
     *
     * @param uri the namespace URI
     * @return a prefix that has previously been associated with this URI, if available; otherwise null
     */

    public String suggestPrefixForURI(String uri) {
        if (uri.equals(NamespaceConstant.XML)) {
            return "xml";
        }
        return suggestedPrefixes.get(uri);
    }

    /**
     * Allocate a fingerprint from the pool, or a new Name if there is not a matching one there
     *
     * @param uri       the namespace URI. Use "" or null for the non-namespace.
     * @param local     the local part of the name
     * @return an integer (the "fingerprint") identifying the name within the namepool.
     * The fingerprint omits information about the prefix, and is the same as the nameCode
     * for the same name with a prefix equal to "".
     */

    public synchronized int allocateFingerprint(String uri, String local) {
        if (NamespaceConstant.isReserved(uri) || NamespaceConstant.SAXON.equals(uri)) {
            int fp = StandardNames.getFingerprint(uri, local);
            if (fp != -1) {
                return fp;
            }
        }
        StructuredQName qName = new StructuredQName("", uri, local);
        Integer existing = qNameToInteger.get(qName);
        if (existing != null) {
            return existing;
        }
        int next = unique.getAndIncrement();
        if (next > MAX_FINGERPRINT) {
            throw new NamePoolLimitException("Too many distinct names in NamePool");
        }
        existing = qNameToInteger.putIfAbsent(qName, next);
        if (existing == null) {
            integerToQName.put(next, qName);
            return next;
        } else {
            return existing;
        }
    }

    /**
     * Get the namespace-URI of a name, given its name code or fingerprint
     *
     * @param nameCode the name code or fingerprint of a name
     * @return the namespace URI corresponding to this name code. Returns "" for the
     * no-namespace.
     * @throws IllegalArgumentException if the nameCode is not known to the NamePool.
     */

    /*@NotNull*/
    public String getURI(int nameCode) {
        int fp = nameCode & FP_MASK;
        if ((fp & USER_DEFINED_MASK) == 0) {
            return StandardNames.getURI(fp);
        }
        return getUnprefixedQName(fp).getURI();
    }

    /**
     * Get the local part of a name, given its name code or fingerprint
     *
     * @param nameCode the integer name code or fingerprint of the name
     * @return the local part of the name represented by this name code or fingerprint
     */

    public String getLocalName(int nameCode) {
        return getUnprefixedQName(nameCode).getLocalPart();
    }

    /**
     * Get the display form of a name (the QName), given its name code or fingerprint
     *
     * @param nameCode the integer name code or fingerprint of a name in the name pool
     * @return the corresponding lexical QName (if a fingerprint was supplied, this will
     * simply be the local name)
     */

    public String getDisplayName(int nameCode) {
        return getStructuredQName(nameCode).getDisplayName();
    }

    /**
     * Get the Clark form of a name, given its name code or fingerprint
     *
     * @param nameCode the integer name code or fingerprint of a name in the name pool
     * @return the local name if the name is in the null namespace, or "{uri}local"
     * otherwise. The name is always interned.
     */

    public String getClarkName(int nameCode) {
        return getUnprefixedQName(nameCode).getClarkName();
    }

    /**
     * Get the EQName form of a name, given its name code or fingerprint
     *
     * @param nameCode the integer name code or fingerprint of a name in the name pool
     * @return the name in the form Q{}local for a name in no namespace, or Q{uri}local for
     * a name in a namespace
     */

    public String getEQName(int nameCode) {
        return getUnprefixedQName(nameCode).getEQName();
    }

    /**
     * Allocate a fingerprint given a Clark Name
     *
     * @param expandedName the name in Clark notation, that is "localname" or "{uri}localName"
     * @return the fingerprint of the name, which need not previously exist in the name pool
     */

    public int allocateClarkName(String expandedName) {
        String namespace;
        String localName;
        if (expandedName.charAt(0) == '{') {
            int closeBrace = expandedName.indexOf('}');
            if (closeBrace < 0) {
                throw new IllegalArgumentException("No closing '}' in Clark name");
            }
            namespace = expandedName.substring(1, closeBrace);
            if (closeBrace == expandedName.length()) {
                throw new IllegalArgumentException("Missing local part in Clark name");
            }
            localName = expandedName.substring(closeBrace + 1);
        } else {
            namespace = "";
            localName = expandedName;
        }

        return allocateFingerprint(namespace, localName);
    }

    /**
     * Get a fingerprint for the name with a given uri and local name.
     * These must be present in the NamePool.
     * The fingerprint has the property that if two fingerprint are the same, the names
     * are the same (ie. same local name and same URI).
     *
     * @param uri       the namespace URI of the required QName
     * @param localName the local part of the required QName
     * @return the integer fingerprint, or -1 if this is not found in the name pool
     */

    public int getFingerprint(String uri, String localName) {
        // A read-only version of allocate()

        if (NamespaceConstant.isReserved(uri) || uri.equals(NamespaceConstant.SAXON)) {
            int fp = StandardNames.getFingerprint(uri, localName);
            if (fp != -1) {
                return fp;
                // otherwise, look for the name in this namepool
            }
        }
        Integer fp = qNameToInteger.get(new StructuredQName("", uri, localName));
        return fp == null ? -1 : fp;

    }

    /**
     * Unchecked Exception raised when some limit in the design of the name pool is exceeded
     */
    public static class NamePoolLimitException extends RuntimeException {

        /**
         * Create the exception
         *
         * @param message the error message associated with the error
         */

        public NamePoolLimitException(String message) {
            super(message);
        }
    }

}

