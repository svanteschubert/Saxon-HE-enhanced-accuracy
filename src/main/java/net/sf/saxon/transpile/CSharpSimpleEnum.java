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
 * Annotate a Java enum to indicate that a simple mapping to a C# enum should be used.
 * This mapping is used essentially for enum's that consist of nothing more that a set of
 * enumeration constants.
 *
 * The flags property is used to indicate that the enumeration constants should be powers of two,
 * and the C# class will be annotated [Flags]..
 */

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)

public @interface CSharpSimpleEnum {
    boolean flags() default false;
}
