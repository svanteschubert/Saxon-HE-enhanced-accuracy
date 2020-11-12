////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2017-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.SequenceExtent;

import javax.xml.transform.Result;
import java.util.ArrayList;
import java.util.List;


/**
 * This class is an implementation of the JAXP Result interface. It can be used to indicate that
 * the output of a transformation (either the principal result, or a secondary result) should be
 * delivered in "raw" form, that is, without building a tree (equivalently, without performing
 * "sequence normalization"). Once output has been written to a RawResult, it is available to
 * the caller in the form of a {@link Sequence}.
 *
 * @author Michael H. Kay
 */

public class RawResult implements Result {

    private String systemId;
    private List<Item> content = new ArrayList<Item>();

    /**
     * Set the system identifier for this Result.
     * <p>
     * <p>If the Result is not to be written to a file, the system identifier is optional.
     * The application may still want to provide one, however, for use in error messages
     * and warnings, or to resolve relative output identifiers.</p>
     *
     * @param systemId The system identifier as a URI string.
     */
    @Override
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Get the system identifier that was set with setSystemId.
     *
     * @return The system identifier that was set with setSystemId,
     * or null if setSystemId was not called.
     */
    @Override
    public String getSystemId() {
        return systemId;
    }

    /**
     * Method intended for internal use to append an item to the result
     */

    public void append(Item item) {
        content.add(item);
    }

    /**
     * On completion, get the sequence that has been written to this result object
     */

    public Sequence getResultSequence() {
        return new SequenceExtent(content);
    }
}

