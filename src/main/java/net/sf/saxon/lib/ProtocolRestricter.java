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

public class ProtocolRestricter implements Predicate<URI> {

    private String rule;
    private List<Predicate<URI>> permitted = new ArrayList<>();

    public static Predicate<URI> make(String value) {
        Objects.requireNonNull(value);
        value = value.trim();
        if (value.equals("all")) {
            // Allow all URIs
            return uri -> true;
        } else {
            return new ProtocolRestricter(value);

        }
    }

    private ProtocolRestricter(String value) {
        this.rule = value;
        String[] tokens = value.split(",\\s*");
        for (String token : tokens) {
            if (token.startsWith("jar:") && token.length() > 4) {
                String subScheme = token.substring(4).toLowerCase();
                permitted.add(uri -> uri.getScheme().equals("jar") && uri.getSchemeSpecificPart().toLowerCase().startsWith(subScheme));
            } else {
                permitted.add(uri -> uri.getScheme().equals(token));
            }
        }
    }

    /**
     * Evaluates this predicate on the given argument.
     *
     * @param uri the input argument
     * @return {@code true} if the input argument matches the predicate,
     * otherwise {@code false}
     */
    @Override
    public boolean test(URI uri) {
        for (Predicate<URI> pred : permitted) {
            if (pred.test(uri)) {
                return true;
            }
        }
        return false;
    }

    public String toString() {
        return rule;
    }
}

