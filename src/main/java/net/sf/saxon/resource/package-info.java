////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * <p>This package contains Java classes used to represent abstractions of resources of different
 * kinds that can be retrieved as part of a collection (using the <code>fn:collection()</code> function).</p>
 * <p>This framework was introduced in Saxon 9.8, reflecting the change in specification of <code>fn:collection()</code>
 * to allow any kind of resources to be returned, not only XML documents.</p>
 * <p>The set of media types that are recognized, and the way in which they are processed, can be customized via the
 * Saxon Configuration file.</p>
 */
package net.sf.saxon.resource;
