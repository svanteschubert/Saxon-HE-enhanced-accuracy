////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import java.util.Properties;

/**
 * Interface allowing localization modules for different languages to be dynamically loaded
 */
public abstract class LocalizerFactory {


    /**
     * Set properties for a particular language. The properties available are specific to the
     * LocalizerFactory in use. Default implementation does nothing.
     *
     * @param lang       the language
     * @param properties properties of this language
     * @since 9.2
     */

    public void setLanguageProperties(String lang, Properties properties) {
        // no action
    }


    /**
     * Get the numberer for a given language
     *
     * @param language the language code (for example "de" or "en-GB"). The value may be null,
     *                 in which case a default language should be assumed.
     * @param country  the country, as used in format-date(). This is not the country associated
     *                 with the language, but the one associated with the date to be formatted. It is primarily
     *                 used to determine a civil time zone name. The value may be null, in which case a default
     *                 country should be assumed.
     * @return the appropriate numberer, or null if none is available (in which case the English
     *         numberer will be used)
     */

    public abstract Numberer getNumberer(String language, String country);

    /**
     * Copy the state of this factory to create a new LocalizerFactory. The default implementation
     * returns this LocalizerFactory unchanged (which should only be done if it is immutable).
     *
     * @return a copy of this LocalizerFactory
     */

    public LocalizerFactory copy() {
        return this;
    }
}

