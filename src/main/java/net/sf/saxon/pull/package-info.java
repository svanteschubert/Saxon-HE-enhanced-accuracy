/**
 * <p>This package provides classes that interface Saxon to an XML parser that supplies data in the form
 * of a stream of events. It provides an interface, <code>PullProvider</code>, that is an abstraction
 * over the pull parser interfaces provided on Java and .NET, and that can in principle be implemented
 * by other data sources to deliver input documents as if they came from a parser.</p>
 * <p>The API, defined in class <code>PullProvider</code>, is loosely modelled on the StAX <code>XMLReader</code>
 * API. It is not identical, because it is designed as an intimate and efficient interface that integrates with
 * Saxon concepts such as the <code>SequenceIterator</code> and the <code>NamePool</code>. A class
 * <code>StaxBridge</code> is available that provides the <code>PullProvider</code> interface on top of a
 * StAX pull parser. In the .NET build, a similar class <code>DotNetPullProvider</code> interfaces Saxon to the
 * Microsoft <code>XmlTextReader</code>.</p>
 * <p>A source of data delivered by a <code>PullProvider</code> may be presented either as a <code>PullSource</code>
 * or as a <code>StaxSource</code>. Both these are accepted by any Saxon interface that allows a JAXP
 * <code>Source</code> object to be supplied.</p>
 * <p>Additional implementations of <code>PullProvider</code> are available in <code>Saxon-PE</code> and
 * <code>Saxon-EE</code>, specifically, implementations that deliver data by walking a Saxon tree structure
 * (represented by class <code>NodeInfo</code>), and implementations that allow queries to be evaluated
 * in pull mode, with lazy construction of temporary document and element nodes.</p>
 * <p>Some examples of application code using the pull interface with Saxon are provided in the
 * <code>PullExamples.java</code> file in the samples directory.</p>
 */
package net.sf.saxon.pull;
