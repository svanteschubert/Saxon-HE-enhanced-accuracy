/**
 * <p>This package contains code for serializing trees using the standard W3C-defined serialization methods
 * (xml, html, text, etc). Additional Saxon-specific serialization methods are in package {@link
 * com.saxonica.serialize}.</p>
 * <p>Serialization in Saxon operates as a push-based event pipeline in which the components of the pipeline
 * implement the {@link net.sf.saxon.event.Receiver} interface.
 * This defines a class that accepts a stream of events, with one method
 * defined for each kind of event. The events are modelled on the design of SAX, but adapted
 * to the XPath data model and to the use of Saxon's NamePool. Attributes and namespaces are
 * notified individually <i>after</i> the start of the relevant element.</p>
 * <p>The pipeline for serialization is assembled by the {@link net.sf.saxon.lib.SerializerFactory} based
 * on a supplied set of serialization parameters. Only those components needed for the chosen serialization
 * parameters are included in the pipeline; for example, a Unicode normalizer is inserted at the appropriate
 * place in the pipeline if Unicode normalization is requested in the serialization parameters.</p>
 * <p>The immediate output of node constructors in a query or stylesheet goes to a {@link
 * net.sf.saxon.event.Outputter}.
 * This is a subclass of <code>Receiver</code> that can handle an arbitrary sequence, containing atomic values
 * as well as nodes. When constructing the content of an element, a {@link net.sf.saxon.event.ComplexContentOutputter}
 * is used;
 * when constructing the content of a node such as a text node or attribute, a <code>SequenceOutputter</code>
 * is used instead.</p>
 * <p>The final serialization classes are subclasses of {@link net.sf.saxon.serialize.Emitter}, but much of the
 * serialization work
 * (such as indentation or application of character maps) is done by other classes on the pipeline. These
 * are generally constructed by extending the {@link net.sf.saxon.event.ProxyReceiver} class.</p>
 * <p>The Emitter is an abstract implementation of the Receiver interface. As well as supporting
 * the Receiver interface, it provides methods for controlling the destination of serialized output
 * (a Writer or OutputStream) and for setting serialization properties (in a Properties object).
 * In practice nearly all the implementations of Receiver are currently subclasses of Emitter,
 * but this may change in the future.</p>
 * <p>The package includes emitters for the standard output methods xml, html, and text, and
 * proxy emitters to allow a sequence of filters to be applied to the output.</p>,
 */
package net.sf.saxon.serialize;
