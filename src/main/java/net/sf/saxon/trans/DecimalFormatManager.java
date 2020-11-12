////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.om.StructuredQName;

import java.util.HashMap;
import java.util.Map;

/**
 * DecimalFormatManager manages the collection of named and unnamed decimal formats, for use by the
 * format-number() function.
 * <p>In XSLT 2.0, there is a single set of decimal formats shared by the whole stylesheet. In XQuery 3.0, however,
 * each query module has its own set of decimal formats, and in XSLT 3.0 decimal formats are local to a package.
 * The DecimalFormatManager to use is therefore linked from the format-number() call on the expression tree.</p>
 *
 * @author Michael H. Kay
 */

public class DecimalFormatManager {

    private DecimalSymbols defaultDFS;
    private HashMap<StructuredQName, DecimalSymbols> formatTable;   // table for named decimal formats
    private HostLanguage language;
    private int languageLevel;

    /**
     * create a DecimalFormatManager and initialise variables
     */

    public DecimalFormatManager(HostLanguage language, int languageLevel) {
        formatTable = new HashMap<>(10);
        defaultDFS = new DecimalSymbols(language, languageLevel);
        this.language = language;
        this.languageLevel = languageLevel;
    }

    /**
     * Get the default decimal-format.
     *
     * @return the default (unnamed) decimal format
     */

    public DecimalSymbols getDefaultDecimalFormat() {
        return defaultDFS;
    }

    /**
     * Get a named decimal-format registered using setNamedDecimalFormat
     *
     * @param qName The  name of the decimal format
     * @return the DecimalSymbols object corresponding to the given name, if any
     *         or null if not set.
     */

    /*@Nullable*/
    public DecimalSymbols getNamedDecimalFormat(StructuredQName qName) {
        DecimalSymbols ds = formatTable.get(qName);
        if (ds == null) {
            return null;
            // following two lines had been added to the code since 9.4, but they break XSLT test error089
//            ds = new DecimalSymbols();
//            formatTable.put(qName, ds);
        }
        return ds;
    }

    /**
     * Get a named decimal-format registered using setNamedDecimalFormat if it exists;
     * create it if it does not
     *
     * @param qName The  name of the decimal format
     * @return the DecimalSymbols object corresponding to the given name, if it exists,
     *         or a newly created DecimalSymbols object otherwise
     */

    public DecimalSymbols obtainNamedDecimalFormat(StructuredQName qName) {
        DecimalSymbols ds = formatTable.get(qName);
        if (ds == null) {
            ds = new DecimalSymbols(language, languageLevel);
            formatTable.put(qName, ds);
        }
        return ds;
    }

    /**
     * Get the names of all named decimal formats that have been registered
     * @return the collection of names
     */

    public Iterable<StructuredQName> getDecimalFormatNames() {
        return formatTable.keySet();
    }

    /**
     * Check the consistency of all DecimalSymbols objects owned by this DecimalFormatManager
     *
     * @throws XPathException if any inconsistencies are found
     */

    public void checkConsistency() throws XPathException {
        defaultDFS.checkConsistency(null);
        for (Map.Entry<StructuredQName, DecimalSymbols> entry : formatTable.entrySet()) {
            entry.getValue().checkConsistency(entry.getKey());
        }
    }

}

