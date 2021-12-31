/**
 * <p>This package provides the core classes of the SAXON XSLT library. </p>
 * <p>Some of the more important classes are listed below: </p>
 * <p>{@link net.sf.saxon.Query}:<br>
 * This is the command line interface to the XQuery processor, allowing you to
 * run a supplied query against a given source document.</p>
 * <p>{@link net.sf.saxon.Transform}:<br>
 * This is the command line interface to the XSLT processor, allowing you to
 * apply a given stylesheet to a given source document.</p>
 * <p>{@link net.sf.saxon.Configuration}:<br>
 * This class holds all the Saxon configuration information, and acts as the fundamental factory class
 * holding central resources and creating components such as validators and serializers.
 * </p>
 * <p><b>{@link net.sf.saxon.PreparedStylesheet}</b>:<br>
 * This represents a compiled XSLT stylesheet in memory. It is Saxon's implementation of the
 * <tt>javax.xml.transform.Templates</tt> interface defined in JAXP 1.1</p>
 * <p><b>{@link net.sf.saxon.Controller}</b>:<br>
 * This class represents the context information for a single execution of an XSLT stylesheet,
 * and allows the application to process the tree navigationally. It is Saxon's implementation
 * of the {@link javax.xml.transform.Transformer} interface defined in JAXP 1.1. It calls
 * user-supplied handlers registered with the RuleManager.
 * </p>
 */
package net.sf.saxon;
