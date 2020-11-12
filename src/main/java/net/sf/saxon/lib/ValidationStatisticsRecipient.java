////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.type.SchemaComponent;

import java.util.Map;

/**
 * Defines a class that is notified of validation statistics at the end of a validation episode
 */
public interface ValidationStatisticsRecipient {

    /**
     * Notify the validation statistics
     *
     * @param statistics the statistics, in the form of a map from schema components (currently,
     *                   element declarations and schema types) to a count of how often the component
     *                   was used during the validation episode
     * @throws SaxonApiException if any error occurs
     */

    void notifyValidationStatistics(Map<SchemaComponent, Integer> statistics) throws SaxonApiException;
}

