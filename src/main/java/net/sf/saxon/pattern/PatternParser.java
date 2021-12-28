////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.trans.XPathException;

/**
 * Interface to a parser of XSLT patterns. There were originally subclasses for XSLT 2.0 and
 * XSLT 3.0 patterns
 */
public interface PatternParser {

    /**
     * Parse a string representing an XSLT pattern
     *
     *
     * @param pattern the pattern expressed as a String
     * @param env     the static context for the pattern
     * @return a Pattern object representing the result of parsing
     * @throws net.sf.saxon.trans.XPathException
     *          if the pattern contains a syntax error
     */

    Pattern parsePattern(String pattern, StaticContext env) throws XPathException;


}

