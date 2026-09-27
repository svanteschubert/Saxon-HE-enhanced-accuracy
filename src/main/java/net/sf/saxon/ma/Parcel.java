////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma;

import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.value.ObjectValue;

/**
 * A Parcel is a way of wrapping an arbitrary sequence as a single item. It is implemented
 * as a Java extension object. It is used in particular to wrap the context value when
 * this is not a single item: this is to avoid disruption to the large amount of Saxon code
 * that is written to assume the context value will be a single item (which it is most of the
 * time).
 */
public class Parcel extends ObjectValue<GroundedValue> {

//    /**
//     * The key of the single entry, that is the string "value"
//     */
//    public static final StringValue parcelKey = new StringValue(new Twine8("value"));
//
//    /**
//     * The type of the singleton map: a record type, effectively <code>record(value: item()*)</code>
//     */
//    public static RecordTest TYPE = RecordTest.nonExtensible(
//            new RecordTest.Field("value", SequenceType.ANY_SEQUENCE, false));

    /**
     * Create a parcel
     * @param content the value to be wrapped
     */
    public Parcel(GroundedValue content) {
        super(content);
    }

    public GroundedValue getValue() {
        return getObject();
    }


}

