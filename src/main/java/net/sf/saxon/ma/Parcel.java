////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma;

import net.sf.saxon.ma.map.RecordTest;
import net.sf.saxon.ma.map.SingleEntryMap;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

/**
 * A Parcel is a way of wrapping an arbitrary sequence as a single item. It is implemented
 * as a single-entry map, the single key being the string "value", and the corresponding
 * value being the wrapped value.
 */
public class Parcel extends SingleEntryMap {

    /**
     * The key of the single entry, that is the string "value"
     */
    public static final StringValue parcelKey = new StringValue(new Twine8("value"));

    /**
     * The type of the singleton map: a record type, effectively <code>record(value: item()*)</code>
     */
    public static RecordTest TYPE = RecordTest.nonExtensible(
            new RecordTest.Field("value", SequenceType.ANY_SEQUENCE, false));

    /**
     * Create a parcel
     * @param content the value to be wrapped
     */
    public Parcel(GroundedValue content) {
        super(parcelKey, content);
    }

}

