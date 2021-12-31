/**
 * <p>This package provides classes for handling different character sets, especially
 * when serializing the output of a query or transformation. </p>
 * <p>Most of the classes in this package are implementations of the interface <code>CharacterSet</code>.
 * The sole
 * function of these classes is to determine whether a particular character is present in the
 * character set or not: if not, Saxon has to replace it with a character reference.</p>
 * <p>The actual translation of Unicode characters to characters in the selected encoding
 * is left to the Java run-time library. (Note that different versions of Java support
 * different sets of encodings, and there is no easy way to find out which encodings
 * are supported in a given installation).</p>
 * <p>It is possible to configure Saxon to support additional character sets by writing an
 * implementation of the <code>CharacterSet</code> interface, and registering this class with the
 * <code>Configuration</code> using the call <code>getCharacterSetFactory().setCharacterSetImplementation()</code></p>
 * <p>If an output encoding is requested that Saxon does not recognize, but which the Java
 * platform does recognize, then Saxon attempts to determine which characters the encoding
 * can represent, so that unsupported characters can be written as numeric character references.
 * Saxon wraps the Java <code>CharSet</code> object in a <code>JavaCharacterSet</code> object,
 * and tests whether a character is encodable by calling the Java interrogative
 * <code>encoding.canEncode()</code>, caching the result locally. Since this mechanism
 * appears to have become reliable in JDK 1.5, it is now used much more widely than before,
 * and most character sets are now supported in Saxon by relying on this mechanism.</p>
 */
package net.sf.saxon.serialize.charcode;
