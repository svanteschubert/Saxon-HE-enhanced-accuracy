////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * <p>This package provides a number of classes supporting the general capability to wrap external
 * XML tree models as instances of the Saxon {@link net.sf.saxon.om.NodeInfo} interface, making them
 * amenable to XPath processing.</p>
 * <p>The {@link net.sf.saxon.tree.wrapper.SpaceStrippedNode} class provides a virtual tree in which whitespace text nodes
 * in the underlying real tree are ignored.</p>
 * <p>The {@link net.sf.saxon.tree.wrapper.TypeStrippedNode} class provides a virtual tree in which type annotations in the
 * underlying real tree are ignored.</p>
 * <p>The {@link net.sf.saxon.tree.wrapper.VirtualCopy} class provides a tree that is the same as the underlying tree in
 * everything except node identity.</p>
 */
package net.sf.saxon.tree.wrapper;
