/**
 * <p>This package provides classes that feed SAX-like events from one tree to another.
 * Some of these classes are associated with serializing the output of a stylesheet, but there
 * are also classes for building a tree from a stream of events, for stripping whitespace, and
 * so on.</p>
 * <p>The {@link net.sf.saxon.event.Receiver} interface defines a class that accepts a stream of events, with one method
 * defined for each kind of event. The events are modelled on the design of SAX, but adapted
 * to the XPath data model and to the use of Saxon's NamePool. Attributes and namespaces are
 * notified individually <i>after</i> the start of the relevant element. Many of the classes
 * in this package are implementations of the <code>Receiver</code> interface.</p>
 * <p>The immediate output of node constructors in a query or stylesheet goes to a {@link
 * net.sf.saxon.event.Outputter}.When constructing the content of an element,
 * a {@link net.sf.saxon.event.ComplexContentOutputter}
 * is used;
 * when constructing the content of a node such as a text node or attribute, a <code>SequenceOutputter</code>
 * is used instead.</p>
 * <p>The final destination of the push pipeline is sometimes a serializer, and sometimes a tree builder.
 * The final serialization classes are subclasses of <code>Emitter</code>, but some of the serialization work
 * (such as indentation or application of character maps) is done by other classes on the pipeline. These
 * are generally constructed by extending the <code>ProxyReceiver</code> class.</p>
 * <p>The Emitter is an abstract implementation of the Receiver interface. As well as supporting
 * the Receiver interface, it provides methods for controlling the destination of serialized output
 * (a Writer or OutputStream) and for setting serialization properties (in a Properties object).
 * In practice nearly all the implementations of Receiver are currently subclasses of Emitter,
 * but this may change in the future.</p>
 * <p>The package includes emitters for the standard output methods xml, html, and text, and
 * proxy emitters to allow a sequence of filters to be applied to the output.</p>,
 * <p>The class <code>ContentHandlerProxy</code> allows events to be converted into standard SAX events and
 * sent to a SAX2 <code>ContentHandler</code>. Similarly, the class <code>ProxyReceiver</code> acts as a
 * <code>ContentHandler</code>, accepting SAX2 events and feeding them into a <code>Receiver</code> pipeline.</p>
 * <p>The class <code>Builder</code> is a <code>Receiver</code> that constructs a tree representation of the
 * document in memory. There are two subclasses for Saxon's two native tree models. Other classes such as
 * a <code>Stripper</code> and a <code>NamespaceReducer</code> are used to modify the document by adding
 * filters to the pipeline.</p>
 * <p>Saxon's schema validator and serializer are both implemented using this push pipeline model.
 * The classes that perform
 * schema validation are part of package: {@link com.saxonica.validate}, while the serialization classes
 * are in {@link net.sf.saxon.serialize}.</p>
 */
package net.sf.saxon.event;
