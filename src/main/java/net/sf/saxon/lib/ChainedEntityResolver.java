////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;


import java.io.IOException;
import java.util.Objects;

/**
 * An EntityResolver that first tries one supplied EntityResolver, and if that
 * returns null, falls back to another. Either EntityResolver may itself
 * be a <code>ChainedEntityResolver</code>, so a chain of any length can be
 * established.
 * @since 11.1
 */

public class ChainedEntityResolver implements EntityResolver2 {

    private final EntityResolver first;
    private final EntityResolver second;

    /**
     * Create a composite entity resolver
     * @param first the first entity resolver to be used
     * @param second the entity resolver to be used if the first one returns null
     */
    public ChainedEntityResolver(EntityResolver first, EntityResolver second) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        this.first = first;
        this.second = second;
    }


    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        InputSource is = first.resolveEntity(publicId, systemId);
        if (is == null) {
            is = second.resolveEntity(publicId, systemId);
        }
        return is;
    }


    @Override
    public InputSource getExternalSubset(String name, String baseURI) throws SAXException, IOException {
        InputSource is = null;
        if (first instanceof EntityResolver2) {
            is = ((EntityResolver2)first).getExternalSubset(name, baseURI);
        }
        if (is == null && second instanceof EntityResolver2) {
            is = ((EntityResolver2) second).getExternalSubset(name, baseURI);
        }
        return is;
    }


    @Override
    public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId) throws SAXException, IOException {
        InputSource is;
        if (first instanceof EntityResolver2) {
            is = ((EntityResolver2) first).resolveEntity(name, publicId, baseURI, systemId);
        } else {
            is = first.resolveEntity(publicId, systemId);
        }
        if (is == null) {
            if (second instanceof EntityResolver2) {
                is = ((EntityResolver2) second).resolveEntity(name, publicId, baseURI, systemId);
            } else {
                is = second.resolveEntity(publicId, systemId);
            }
        }
        return is;
    }

}

