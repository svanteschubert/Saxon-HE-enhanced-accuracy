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
 * This annotation appears on the declation of a method or field if, for some reason,
 * the automatic generation of the correct modifiers for C# produces incorrect results.
 *
 * A common use case is for methods in inner classes, which are not handled by the
 * pre-allocation of virtual/override modifiers in the refined digest.
 *
 * All modifiers should be listed, replacing any modifiers (and Override annotations)
 * present in the Java source code
 */

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface CSharpSuppressWarnings {
    String value() default "";
}
