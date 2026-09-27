////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * <p>This package provides Saxon's preferred Java API for XSLT, XQuery, XPath, and XML Schema processing.
 * Unlike standard Java APIs such as JAXP and XQJ, this API provides a consistent and integrated
 * approach to the entire functionality of the Saxon product.</p>
 * <p>An application starts by loading a {@link net.sf.saxon.s9api.Processor}, which allows configuration options
 * to be set. Whenever possible, an application should instantiate a single <code>Processor</code> and use
 * it for the duration of the application, across all threads.</p>
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
 * <p>The interfaces for schema processing in Saxon-EE have changed a little in Saxon 13 to accommodate
 * the fact that a {@link net.sf.saxon.s9api.Processor} can now hold multiple schemas, each of which can
 * be used independently (previously multiple schema documents could be loaded, but they were
 * all aggregated into a single schema). A schema is represented by the class {@link net.sf.saxon.s9api.XsdSchema},
 * and is built using an {@link net.sf.saxon.s9api.XsdCompiler} obtained from the {@link net.sf.saxon.s9api.Processor}.
 * The {@link net.sf.saxon.s9api.XsdSchema} can be used to create a {@link net.sf.saxon.s9api.SchemaValidator} for
 * validating instance documents, and it can be supplied to an XSLT, XQuery, or XPath compiler for executing
 * schema-aware transformations and queries.</p>
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
