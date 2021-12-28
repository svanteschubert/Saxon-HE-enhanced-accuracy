////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import javax.xml.transform.SourceLocator;

/**
 * A user-written implementation of the MessageListener interface may be registered with the XsltTransformer
 * to receive notification of xsl:message output. Each xsl:message instruction that is evaluated results in
 * a single call to the MessageListener
 */
public interface MessageListener {

    /**
     * Notify a message written using the <code>xsl:message</code> instruction
     *
     * @param content   a document node representing the message content. Note that the output of
     *                  <code>xsl:message</code> is always an XML document node. It can be flattened to obtain the
     *                  string value if required by calling <code>getStringValue()</code>.
     * @param terminate Set to true if <code>terminate="yes"</code> was specified or to false otherwise.
     *                  The message listener does not need to take any special action based on this parameter, but the information
     *                  is available if required. If <code>terminate="yes"</code> was specified, then the transformation will abort
     *                  with an exception immediately on return from this callback.
     * @param locator   an object that contains the location of the <code>xsl:message</code> instruction in the
     *                  stylesheet that caused this message to be output. This provides access to the URI of the stylesheet module
     *                  and the line number of the <code>xsl:message</code> instruction.
     */

    void message(XdmNode content, boolean terminate, SourceLocator locator);
}

