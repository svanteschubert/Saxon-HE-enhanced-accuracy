
/**
 * <p>This package is cloned from https://github.com/erdtman/java-json-canonicalization.
 * The package has full support for JSON Canonicalization. The only part Saxon uses
 * is the double-to-string conversion, but this is the majority of the code.</p>
 *
 * <p>The code is Apache-licensed.</p>
 *
 * <p>A small number of changes have been made to enable transpilation to C#:</p>
 * <ul>
 *     <li>The class JsonCanonicalizer has been dropped, since we don't need it (we only need the numeric formatting)</li>
 *     <li>In NumberDiyFp, instance fields have been renamed to avoid clashing with method names</li>
 *     <li>In NumberDtoA, calls on CSharp.emitCode() have been added to handle fall-through in switch statements</li>
 *     <li>In NumberDtoA, replaced "10." by "10.0"</li>
 *     <li>In NumberDtoA, dropped redundant assignment "S = mhi = null"</li>
 * </ul>
 */
package net.sf.saxon.serialize.jcs;
