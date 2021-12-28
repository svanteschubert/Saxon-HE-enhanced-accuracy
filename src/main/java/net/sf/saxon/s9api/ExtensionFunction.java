////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

/**
 * This is an interface for simple external/extension functions. Users can implement this
 * interface and register the implementation with the {@link net.sf.saxon.s9api.Processor}; the function will
 * then be available for calling from all queries, stylesheets, and XPath expressions compiled
 * under this Processor.
 * <p>Extension functions implemented using this interface are expected to be free of side-effects,
 * and to have no dependencies on the static or dynamic context. A richer interface for extension
 * functions is provided via the {@link net.sf.saxon.lib.ExtensionFunctionDefinition} class.</p>
 */
public interface ExtensionFunction {

    /**
     * Return the name of the external function
     *
     * @return the name of the function, as a QName.
     */
    QName getName();

    /**
     * Declare the result type of the external function. The default for this method
     * returns the type <code>item()*</code>. Returning a more precise type enables
     * Saxon to do better static type checking, and to avoid run-time conversions.
     *
     * @return the result type of the external function. Saxon will check at run-time that
     * the actual value returned by the {@link #call} method is an instance of this type.
     */

    default SequenceType getResultType() {
        return SequenceType.ANY;
    }

    /**
     * Declare the types of the arguments
     *
     * @return an array of SequenceType objects, one for each argument to the function,
     *         representing the expected types of the arguments
     */

    SequenceType[] getArgumentTypes();

    /**
     * Call the function. The implementation of this method represents the body of the external function.
     *
     * @param arguments the arguments, as supplied in the XPath function call. These will always be of
     *                  the declared types. Arguments are converted to the required types according to the standard XPath
     *                  function conversion rules - for example, if the expected type is atomic and a node is supplied in the
     *                  call, the node will be atomized
     * @return the result of the function. This must be an instance of the declared return type; if it is not,
     *         a dynamic error will be reported
     * @throws SaxonApiException can be thrown if the implementation of the function detects a dynamic error
     */

    /*@NotNull*/
    XdmValue call(XdmValue[] arguments) throws SaxonApiException;
}
