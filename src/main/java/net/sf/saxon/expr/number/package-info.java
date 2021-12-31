/**
 * <p>This package provides classes associated with numbering and the <code>xsl:number</code> instruction. </p>
 * <p>It is possible to extend the range of numberings available by providing a Numberer
 * for a specific language. This must be registered with the <code>Configuration</code>.
 * In earlier releases, there was a fixed relationship between the language and the
 * implementing class (the Numberer was always named Numberer_xx where xx is the language code,
 * corresponding to the value of the lang attribute in <code>xsl:number</code>). From Saxon 9.2,
 * this relationship no longer exists.</p>
 * <p>These classes also include code to support the localization of dates as defined
 * in the XSLT <code>format-dateTime()</code> group of functions.</p>
 * <p>The class <code>Numberer_en</code> provides the standard numbering options. As well as the
 * format tokens defined in the XSLT 1.0 specification (for example, "1", "001", "a", "i") it supports
 * other numbering options including:</p>
 * <li>
 * <ul>Greek upper and lower case letters</ul>
 * <ul>Cyrillic upper and lower case letters</ul>
 * <ul>Hebrew letters</ul>
 * <ul>Japanese: Hiragana-A, Hiragana-B, Katakana-A, or Katakana-B letters, and Kanji digits</ul>
 * <ul>English words: the format token "one" produces numbers such as "twenty five"</ul>
 * </li>
 * <p>Localizations for a number of European languages are provided in package <code>net.sf.saxon.option.local</code>.
 * In Saxon-PE and Saxon-EE these are issued in binary form as part of the Saxon JAR. For Saxon-HE, they are
 * issued only in source code form.</p>
 */
package net.sf.saxon.expr.number;
