////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern.qname;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.type.Affinity;

/**
 * Interface for tests against a QName. It is only concerned with testing a name.
 * It is used (among other things) for matching error codes against the codes specified in a try/catch clause,
 * and to match component names in xsl:accept and xsl:expose.
 */

public interface QNameTest {

    /**
     * Test whether the QNameTest matches a given QName
     *
     * @param qname the QName to be matched
     * @return true if the name matches, false if not
     */

    boolean matches(StructuredQName qname);

    /**
     * Export the QNameTest as a string for use in a SEF file (typically in a catch clause).
     * @return a string representation of the QNameTest, suitable for use in export files. The format is
     * a sequence of alternatives, space-separated, where each alternative is one of '*',
     * '*:localname', 'Q{uri}*', or 'Q{uri}local'.
     */

    String exportQNameTest();

    /**
     * Determine the relationship of this QNameTest to another QNameTest: one of
     * (same, subsumes, subsumed-by, overlaps, disjoint)
     */

    Affinity relationship(QNameTest other);

}
