// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

public class TraceLevel {

    /** No tracing **/
    public static final int NONE = 0;
    /** Function and template calls **/
    public static final int LOW = 1;
    /** Instructions (or the equivalent in XQuery) */
    public static final int NORMAL = 2;
    /** All expressions */
    public static final int HIGH = 3;


}


