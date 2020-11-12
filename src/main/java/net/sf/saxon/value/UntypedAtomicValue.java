////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.BuiltInAtomicType;

/**
 * An Untyped Atomic value. This inherits from StringValue for implementation convenience, even
 * though an untypedAtomic value is not a String in the data model type hierarchy.
 */

public class UntypedAtomicValue extends StringValue {

    public static final UntypedAtomicValue ZERO_LENGTH_UNTYPED =
            new UntypedAtomicValue("");

    /**
     * Constructor
     *
     * @param value the String value.  Must not be null.  The caller must ensure that the
     *              value will not subsequently change, even though it may be a mutable
     *              object such as a FastStringBuffer.
     */

    public UntypedAtomicValue(CharSequence value) {
        this.value = value;
        typeLabel = BuiltInAtomicType.UNTYPED_ATOMIC;
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     */

    /*@NotNull*/
    @Override
    public AtomicValue copyAsSubType(AtomicType typeLabel) {
        if (!typeLabel.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
            throw new UnsupportedOperationException();
        }
        return this;
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is AnyAtomicType.
     */

    /*@NotNull*/
    @Override
    public BuiltInAtomicType getPrimitiveType() {
        return BuiltInAtomicType.UNTYPED_ATOMIC;
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    @Override
    public final CharSequence getStringValueCS() {
        return value;
    }

    /**
     * For displaying the value in error messages, prefix with "u" to highlight that it's
     * untyped atomic
     * @return a short depiction of the string for use in error messages
     */

    @Override
    public String toShortString() {
        return "u" + super.toShortString();
    }


}

