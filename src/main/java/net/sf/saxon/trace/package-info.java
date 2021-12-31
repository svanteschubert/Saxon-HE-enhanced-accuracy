/**
 * <p>This package provides an interface to Saxon tracing and debugging capabilities.</p>
 * <p>The package was originally created by Edwin Glaser.</p>
 * <p>The package includes three tracing modules that can be optionally selected:
 * <code>XSLTTraceListener</code>, <code>XQueryTraceListener</code>, and
 * <code>TimedTraceListener</code>. These all receive notification of the same events,
 * but select and format the events in different ways to meet different requirements.
 * Other events are notified through the <code>TraceListener</code> interface that
 * are ignored by tracing applications, but may be of interest to debuggers.</p>
 */
package net.sf.saxon.trace;
