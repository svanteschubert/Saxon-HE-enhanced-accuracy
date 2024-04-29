////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.transpile;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation appears on the declaration of a method that creates an instance of
 * an anonymous inner class, and it is used to define what information needs to be
 * passed to the generated C# inner class.
 *
 * <p>The parameter {@code outer} indicates whether a reference to the outer class ("this")
 * should be passed. This defaults to true and should be set to false only if the
 * containing method is non-static.</p>
 *
 * <p>The parameter {@code extra} is multi-valued, and contains one entry for each extra
 * parameter to be passed through. Parameters already present in the constructor of the
 * inner class are included automatically. The value of the each entry is in the form
 * of a variable parameter declaration (type, then name, whitespace separated), for example
 * <code>"Saxon.Hej.s9api.Location loc"</code>.</p>
 */

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface CSharpInnerClass {
    boolean outer() default false;
    String[] extra() default {};
}
