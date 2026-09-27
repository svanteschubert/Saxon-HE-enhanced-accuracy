////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * <p>This package provides an interface to Saxon tracing and debugging capabilities.</p>
 * <p>The package includes three tracing modules that can be optionally selected:
 * <code>XSLTTraceListener</code>, <code>XQueryTraceListener</code>, and
 * <code>TimingTraceListener</code>. These all receive notification of the same events,
 * but select and format the events in different ways to meet different requirements.
 * Other events are notified through the <code>TraceListener</code> interface that
 * are ignored by tracing applications, but may be of interest to debuggers.</p>
 */
package net.sf.saxon.trace;
