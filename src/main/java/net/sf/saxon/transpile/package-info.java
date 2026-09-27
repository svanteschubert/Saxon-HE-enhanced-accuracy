/**
 * <p>This package contains classes that support the transpilation of Saxon Java source code
 * to C#, from which the SaxonCS product is built.</p>
 *
 * <p>Most of the classes in this package implement Java annotations. These are used, for example,
 * to define an alternative C# body for a method, or alternative modifiers required by C#,
 * or to indicate that an {@code enum} class can be converted directly to a C# {@code enum}.
 * These annotations have no effect on the behaviour of the Java code, but the transpiler
 * interprets them as directives controlling the generation of C# code.</p>
 *
 * <p>The class {@code CSharp} contains static methods which are executable, but the Java implementation
 * of these methods does nothing; the transpiler converts these calls into C# code that fulfils a useful
 * purpose.</p>
 */
package net.sf.saxon.transpile;

// Copyright (c) 2025-2026 Saxonica Limited.
