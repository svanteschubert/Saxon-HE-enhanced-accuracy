////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.om.StructuredQName;

/**
 * Interface for tests against a QName. This is implemented by a subset of NodeTests, specifically
 * those that are only concerned with testing a name. It is used for matching error
 * codes against the codes specified in a try/catch clause, and also to match component names
 * in xsl:accept and xsl:expose.
 *
 * The various implementations of QNameTest typically match a node kind as well as node name. This
 * interface, however, is concerned only with matching of QNames, and the ability of an implementation
 * to match a node kind as well is redundant.
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
     * Generate Javascript code to test if a name matches the test.
     * @return JS code as a string. The generated code will be used
     * as the body of a JS function in which the argument name "q" is an
     * XdmQName object holding the name. The XdmQName object has properties
     * uri and local.
     * @param targetVersion The version of Saxon-JS being targeted
     */

    String generateJavaScriptNameTest(int targetVersion);

}
