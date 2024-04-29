////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * <p>This package contains an implementation of XDM sequences known as the ZenoSequence.</p>
 * <p>A ZenoChain is a list of elements, organized as a sequence of contiguous chunks; it is
 * designed to ensure that elements can be efficiently added to either end of the sequence
 * without copying the entire sequence, while also leaving the original sequence intact.</p>
 * <p>It is thus an implementation of immutable persistent lists optimized for a scenario
 * where changes typically occur only at the ends of the sequence.</p>
 * <p>The ZenoSequence is a specialization of a ZenoChain for implementing XDM sequences.</p>
 */
package net.sf.saxon.ma.zeno;
