////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

/**
 * This exception occurs when an attempt is made to dereference a reference from one
 * schema component to another, if the target of the reference cannot be found. Note that
 * an unresolved reference is not necessarily an error: a schema containing unresolved
 * references may be used for validation, provided the components containing the
 * unresolved references are not actually used.
 * @since 10.0 - changed in 10.0 to be an unchecked exception
 */

public abstract class MissingComponentException extends RuntimeException {

    public MissingComponentException(String ref) {
        super(ref);
    }
}
