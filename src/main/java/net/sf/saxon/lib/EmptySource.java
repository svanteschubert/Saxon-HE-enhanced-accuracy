////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import javax.xml.transform.Source;

/**
 * This class is an implementation of the JAXP Source interface. It represents
 * a dummy Source, which resolves to an empty sequence. A URIResolver (called
 * perhaps in furtherance of the doc() or document() function can choose to
 * return an {@code EmptySource} for example if a URI is not accessible; the effect
 * is that the call on doc() or document() returns an empty sequence. This may
 * be useful to emulate the behaviour of earlier versions of XSLT.
 *
 * <p>The class is a singleton marker class.</p>
 *
 * <p>See also bug 4752.</p>
 *
 * @since 10.4
 */

public class EmptySource implements Source {

    private final static EmptySource THE_INSTANCE = new EmptySource();

    private EmptySource() {}

    public static EmptySource getInstance() {
        return THE_INSTANCE;
    }

    /**
     * Set the system identifier for this Source. This is a dummy
     * implementation to satisfy the interface, and it does nothing.
     *
     * @param systemId The system identifier as a URL string.
     */
    @Override
    public void setSystemId(String systemId) {

    }

    /**
     * Get the system identifier that was set with setSystemId.
     *
     * @return null (always). The class is stateless.
     */
    @Override
    public String getSystemId() {
        return null;
    }
}

