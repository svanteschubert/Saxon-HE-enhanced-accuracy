////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.sort.XPathComparable;

/**
 * This interface is implemented by AtomicValues that can be compared without regard
 * to context - specifically, the result of {@code eq} and {@code lt} comparisons does not depend
 * on collations or on the context-dependent timezone.
 */

public interface ContextFreeAtomicValue {

    /**
     * Get an XPathComparable object that supports the semantics of context-free
     * eq and lt comparisons between atomic values. Note that in many cases the
     * returned XPathComparable will be the AtomicValue itself; however because
     * of the constraints of the generic {@link Comparable} interface, this
     * cannot be assumed.
     * @return an XPathComparable that can be used in comparisons with other atomic
     * values.
     */

    XPathComparable getXPathComparable();
}

