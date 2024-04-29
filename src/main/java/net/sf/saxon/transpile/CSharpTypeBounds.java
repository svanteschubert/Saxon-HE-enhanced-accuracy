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
 * This annotation appears on the declaration of a class using generic type
 * parameters if the C# code needs to have type bounds that cannot be inferred
 * directly from the Java declaration. A typical use case is to declare bounds
 * of {@code T:class} if T is constrained to be a reference type - that is, a value
 * that is potentially nullable. Without this bound declaration, assignment of
 * T to null will be rejected by the C# compiler.
 */

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface CSharpTypeBounds {
    String[] bounds() default {};
}
