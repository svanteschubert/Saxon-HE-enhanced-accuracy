////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.gizmo;

import java.util.List;

/**
 Interface for a simple line-based question/answer dialog with the user
 */

public interface Talker {
    /**
     * Send some output to the user and get some input back
     * @param out output to be sent to the user
     * @return the user's response
     */
    String exchange(String out);

    default void setAutoCompletion(List<String> candidates) {}
}

