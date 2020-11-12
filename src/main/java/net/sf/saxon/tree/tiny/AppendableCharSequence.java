////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.tiny;

/**
 * Defines a CharSequence to which characters can be appended
 */
public interface AppendableCharSequence extends CharSequence {

    /**
     * Append characters to this CharSequence
     *
     * @param chars the characters to be appended
     * @return the concatenated results
     */
    AppendableCharSequence cat(CharSequence chars);

    /**
     * Append a single character to this CharSequence
     * @param c the character to be appended
     * @return the concatenated results
     */

    AppendableCharSequence cat(char c);

    /**
     * Set the length. If this exceeds the current length, this method is a no-op.
     * If this is less than the current length, characters beyond the specified point
     * are deleted.
     *
     * @param length the new length
     */

    void setLength(int length);
}
