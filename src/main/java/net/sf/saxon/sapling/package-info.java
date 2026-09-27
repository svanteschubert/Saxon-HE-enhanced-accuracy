////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * <p>This package defines a public API for constructing trees; it represents trees-under-construction
 * using a lightweight immutable data structure called a <i>sapling</i>; once construction of a sapling
 * tree is complete, it can be copied to form a fully-functional XDM tree in the form of an
 * <code>XdmNode</code> or <code>NodeInfo</code> object.</p>
 * <p>An overview of the API can be found in the Javadoc for class {@link net.sf.saxon.sapling.SaplingNode}.</p>
 */
package net.sf.saxon.sapling;
