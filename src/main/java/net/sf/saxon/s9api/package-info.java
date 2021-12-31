/**
 * <p>This package provides Saxon's preferred Java API for XSLT, XQuery, XPath, and XML Schema processing.
 * The interface is designed to hide as much as possible of the detail of the
 * implementation. However, the API architecture faithfully reflects the internal architecture
 * of the Saxon product, unlike standard APIs such as JAXP and XQJ which in many cases force
 * compromises in the design and performance of the application.</p>
 * <p>An application starts by loading a {@link net.sf.saxon.s9api.Processor}, which allows configuration options
 * to be set. As far as possible, an application should instantiate a single <code>Processor</code>.</p>
 * <p>The interfaces for XSLT, XQuery, and XPath processing all follow the same pattern. There is a three-stage
 * execution model: first a compiler is created using a factory method in the <code>Processor</code> object.
 * The compiler holds compile-time options and the static context information. Then the compiler's
 * <code>compile()</code> method is called to create an executable, a representation of the compiled stylesheet,
 * query, or expression. This is thread-safe and immutable once created. To run the query or transformation,
 * first call the <code>load()</code> method to create a run-time object called variously an {@link
 * net.sf.saxon.s9api.XsltTransformer},
 * {@link net.sf.saxon.s9api.XQueryEvaluator}, or {@link net.sf.saxon.s9api.XPathSelector}. This holds run-time context
 * information
 * such as parameter settings and the initial context node; the object is therefore not shareable and should
 * only be run in a single thread; indeed it should normally only be used once. This object also provides
 * methods allowing the transformation or query to be executed.</p>
 * <p>In Saxon-EE the <code>Processor</code> owns a {@link net.sf.saxon.s9api.SchemaManager} that holds
 * the cache of schema components and can be used to load new schema components from source schema documents.
 * It can also be used to create a {@link net.sf.saxon.s9api.SchemaValidator}, which in turn is used to validate
 * instance
 * documents.</p>
 * <p>Source documents can be constructed using a {@link net.sf.saxon.s9api.DocumentBuilder}, which holds all the options
 * and parameters to control document building.</p>
 * <p>The output of a transformation, or of any other process that generates an XML tree, can be sent to a
 * {@link net.sf.saxon.s9api.Destination}. There are a number of implementations of this interface, including a
 * {@link net.sf.saxon.s9api.Serializer} which translates the XML document tree into lexical XML form.</p>
 * <p>There are classes to represent the objects of the XDM data model, including {@link net.sf.saxon.s9api.XdmValue},
 * {@link net.sf.saxon.s9api.XdmItem}, {@link net.sf.saxon.s9api.XdmNode}, and {@link
 * net.sf.saxon.s9api.XdmAtomicValue}. These can be manipulated using methods based on the Java 8 streams
 * processing model: for details see package {@link net.sf.saxon.s9api.streams}.</p>
 * <hr>
 */
package net.sf.saxon.s9api;
