////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import javax.xml.XMLConstants;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * This class implements the rules in a property such as {@link XMLConstants#ACCESS_EXTERNAL_SCHEMA},
 * which constrain the set of URIs that can be used by supplying a list of permitted protocols.
 */

public class ProtocolRestrictor {

    private final Predicate<URI> predicate;
    private final String originalRule;

    /**
     * Create a predicate from a list of allowed protocols.
     *
     * <p>The value is a comma-separated list of permitted protocols. A protocol is the
     *  scheme portion of a URI, or in the case of the JAR protocol, "jar"
     *  plus the scheme portion separated by colon. The value "all" gives access
     *  to all protocols (which is the default). The value "" (empty string) disallows
     *  all external resource access. (The format is thus the same as for
     *  <code>XMLConstants.ACCESS_EXTERNAL_SCHEMA</code> and similar attributes.)
     *  @param value the list of allowed protocols
     */

    public ProtocolRestrictor(String value) {
        Objects.requireNonNull(value);
        this.originalRule = value;
        value = value.trim();
        if (value.equals("all")) {
            // Allow all URIs
            predicate = uri -> true;
        } else {
            final List<Predicate<URI>> permitted = new ArrayList<>();
            String[] tokens = value.split(",\\s*");
            for (String token : tokens) {
                if (token.startsWith("jar:") && token.length() > 4) {
                    String subScheme = token.substring(4).toLowerCase();
                    permitted.add(uri -> scheme(uri).equals("jar") &&
                            schemeSpecificPart(uri).toLowerCase().startsWith(subScheme));
                } else {
                    permitted.add(uri -> scheme(uri).equals(token));
                }
            }
            predicate = uri -> {
                for (Predicate<URI> pred : permitted) {
                    if (pred.test(uri)) {
                        return true;
                    }
                }
                return false;
            };
        }
    }

    public boolean test(URI uri) {
        return predicate.test(uri);
    }

    public String toString() {
        return originalRule;
    }

    // The following methods are extracted to enable the C# transpiler to recognise what it needs to do...

    private static String scheme(URI uri) {
        return uri.getScheme();
    }

    private static String schemeSpecificPart(URI uri) {
        return uri.getSchemeSpecificPart();
    }



}

