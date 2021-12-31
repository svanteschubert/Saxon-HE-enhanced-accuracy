/**
 * <p>This package is Saxon's implementation of the JAXP API designed for executing XPath 1.0 expressions
 * directly from a Java application. Saxon extends the interface to handle XPath 2.0, though if the application
 * makes extensive use of XPath 2.0 features, then the <b>s9api</b> interface offers a better fit
 * to the XPath 2.0 data model. The API can be used either in a free-standing
 * Java application (that is, where there is no XSLT stylesheet), or it can be
 * used from within Java extension functions called from XPath expressions within
 * a stylesheet.</p>
 * <p>The API itself is defined by JAXP 1.3, in interfaces such as <code>javax.xml.xpath.XPath</code>.<
 * These interfaces are included in Java Standard Edition from JDK 1.5 onwards.</p>
 * <p>The interfaces provided by Saxon extend the JAXP 1.3 interfaces in various ways. There
 * are three reasons for this:</p>
 * <ul>
 * <li><p>Saxon supports XPath 2.0 rather than 1.0</p></li>
 * <li><p>The package retains support for some interfaces that were provided before JAXP 1.3 was released.
 * (Most of these extensions have been deprecated for several releases, and most removed in Saxon 9.6).</p></li>
 * <li><p>There are methods that allow an escape into Saxon's more low-level APIs, needed by
 * anyone doing serious software integration.</p></li>
 * </ul>
 * <p>For most applications the preferred interface is
 * the s9api {@link net.sf.saxon.s9api.XPathCompiler}</p>
 */
package net.sf.saxon.xpath;
