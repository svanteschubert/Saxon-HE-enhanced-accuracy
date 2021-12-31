/**
 * <p>This package contains a general-purpose implementation of immutable hash trie maps,
 * used to underpin the Saxon implementation of XSLT 3.0 maps.</p>
 * <p>The implementation was written by Michael S. Froh (msfroh) and released under an MIT license.
 * It was forked from github project msfroh/functional_snippets on 28 March 2014. Very little code
 * has been changed during the integration with Saxon, but a great deal of un-needed code has been
 * removed. In addition, it has been retrofitted from Java 7 to Java 5.</p>
 * <p>The code betrays Scala origins in the use of classes such as Option, which is a class that
 * provided a type-safe way of handling null values, and Tuple2 which can hold any pair of values.</p>
 */
package net.sf.saxon.ma.trie;
