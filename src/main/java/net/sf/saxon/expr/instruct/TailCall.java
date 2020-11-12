////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.trans.XPathException;


/**
 * Interface representing a Tail Call. This is a package of information passed back by a called
 * instruction to its caller, representing a call (and its arguments) that needs to be made
 * by the caller. This saves stack space by unwinding the stack before making the call.
 */

public interface TailCall {

    /**
     * Process this TailCall (that is, executed the template call that is packaged in this
     * object). This may return a further TailCall, which should also be processed: this
     * is the mechanism by which a nested set of recursive calls is converted into an iteration.
     *
     * @return a further TailCall, if the recursion continues, or null, indicating that the
     *         recursion has terminated.
     * @throws net.sf.saxon.trans.XPathException
     *          if any error occurs processing the tail call
     */

    TailCall processLeavingTail() throws XPathException;

}

