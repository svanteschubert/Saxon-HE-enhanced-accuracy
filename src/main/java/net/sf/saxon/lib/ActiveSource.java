////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Source;

/**
 * An ActiveSource is a Source that is capable of delivering an XML document to a Receiver;
 */

public interface ActiveSource extends Source {

    /**
     * Deliver the content of the source to a supplied Receiver.
     * <p>For many (but not all) implementations of {@code Source}, this method consumes
     * the source and can therefore only be called once.</p>
     * @param receiver the receiver to which events representing the parsed XML document will be sent
     * @param options options for parsing the source
     * @throws XPathException if parsing fails for any reason. The detailed diagnostics will
     * have been sent to the error reporter.
     */
    void deliver(Receiver receiver, ParseOptions options) throws XPathException;

}

