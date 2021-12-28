////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.registry;

/**
 * This is a marker interface representing an abstract superclass of JavaExtensionFunctionFactory
 * and DotNetExtensionFunctionFactory. These play equivalent roles in the system: that is, they
 * are responsible for determining how the QNames of extension functions are bound to concrete
 * implementation classes; but they do not share the same interface.
 * <p>This interface was introduced in Saxon 8.9. Prior to that, <code>ExtensionFunctionFactory</code>
 * was a concrete class - the class now named <code>JavaExtensionFunctionFactory</code>.</p>
 */

public interface ExtensionFunctionFactory {
}

