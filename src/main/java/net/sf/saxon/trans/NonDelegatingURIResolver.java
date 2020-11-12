////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import javax.xml.transform.URIResolver;

/**
 * This is a marker interface: if a URIResolver implements this interface and returns null from
 * its resolve() method, then the standard URI resolver will not be invoked.
 * <p>The main use case for this is to support protocols that the standard Java java.net.URL class
 * does not recognize. In the case of doc-available(), we want to return false, rather than throwing
 * an exception in such cases.</p>
 */

public interface NonDelegatingURIResolver extends URIResolver {
    // marker interface only
}

