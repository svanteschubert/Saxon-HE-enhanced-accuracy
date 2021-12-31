/**
 * <p>This package contains the code to map XML Schema and XPath regular expressions
 * to a regular expression engine of the underlying Java platform.</p>
 * <p>Most of the classes implement a regular expression engine derived from Apache's Jakarta
 * project. The code of Jakarta has been modified so that the regular expressions implement
 * the syntax of XSD/XPath regular expressions. There have also been extensive changes
 * for performance reasons</p>
 * <p>In addition, there are classes to provide direct access to the native JDK
 * regular expression engine. The flags value ";j" may be used to select this
 * engine. The resulting syntax/semantics will not be an exact match to the XPath
 * definition.</p>
 * <p>Users should not normally need to use these classes directly.</p>
 * <p>Earlier versions of Saxon included a translator from XPath regular expressions
 * to Java regular expressions, based on that produced by James Clark. This
 * mechanism is no longer used.</p>
 */
package net.sf.saxon.regex;
