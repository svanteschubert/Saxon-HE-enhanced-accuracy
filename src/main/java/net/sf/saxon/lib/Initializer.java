////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;

import javax.xml.transform.TransformerException;

/**
 * This interface can be implemented by users (there are no implementations in Saxon itself). It is
 * used only when Saxon is invoked from the command line, and the -init:class option is used on the command
 * line to nominate an implementation of this class. The initialize() method of the supplied class will
 * then be called to perform any user-defined initialization of the Configuration.
 * <p>The initializer is invoked after all other options on the command line have been processed; the initializer
 * can therefore examine the Configuration to see what options have been set, and it can modify them accordingly.</p>
 *
 * @since 9.3
 */
public interface Initializer {

    /**
     * Initialize the Configuration
     *
     * @param config the Configuration to be initialized
     * @throws TransformerException if the initializer chooses to abort processing for any reason
     */

    void initialize(Configuration config) throws TransformerException;
}

