////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.transpile;

/**
 * This class contains dummy methods which, if called, have no effect; but calls
 * on these methods are detected by the Java-to-C# converter and affect the C# code
 * that is generated.
 */

public class CSharp {

    /**
     * CSharp.emitCode("goto label") causes the converter to include
     * the code "goto label" in the generated C# code. The argument must
     * be supplied as a string literal. At run time, the method has no effect.
     * @param code the C# code to be emitted.
     */

    public static void emitCode(String code) {
        // do nothing at run time
    }

    /**
     * CSharp.methodRef is an identity function that signals to the
     * CSharp transpiler that the argument expression is a non-static method reference.
     *
     * @param methodReference must be supplied in the form of a method reference object::method
     * @param <T>             the class of the argument
     * @return the argument, unchanged.
     */


    public static <T> T methodRef(T methodReference) {
        return methodReference;
    }

    /**
     * CSharp.staticRef is an identity function that signals to the
     * CSharp transpiler that the argument expression is a static method reference.
     *
     * @param methodReference must be supplied in the form of a method reference ClassName::method
     * @param <T>             the class of the argument
     * @return the argument, unchanged.
     */

    public static <T> T staticRef(T methodReference) {
        return methodReference;
    }

    /**
     * CSharp.constructor is an identity function that signals to the
     * CSharp transpiler that the argument expression is a method reference to
     * a constructor with a specified number of arguments.
     *
     * @param methodReference must be supplied in the form of a method reference ClassName::new
     * @param arity the number of arguments in the constructor (must be a numeric literal)
     * @param <T>             the class of the argument
     * @return the argument, unchanged.
     */

    public static <T> T constructorRef(T methodReference, int arity) {
        return methodReference;
    }
}

