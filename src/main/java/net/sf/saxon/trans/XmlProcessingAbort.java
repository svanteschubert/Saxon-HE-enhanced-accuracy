////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.lib.ErrorReporter;

/**
 * An unchecked exception, triggered when a user-supplied {@link ErrorReporter} requests
 * that processing should be aborted
 */

public class XmlProcessingAbort extends RuntimeException {

    public XmlProcessingAbort(String message) {
        super(message);
    }
}

