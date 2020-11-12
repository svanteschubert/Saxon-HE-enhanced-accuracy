////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.Configuration;

/**
 * Interface representing a factory class for instantiating instances of a specific class
 */
public class Instantiator<T> implements Maker<T> {

    private String className;
    private Configuration config;

    public Instantiator(String className, Configuration config) {
        this.className = className;
        this.config = config;
    }

    /**
     * Obtain an instance of type T, either by making a new instance or by reusing an existing instance
     *
     * @throws XPathException if the attempt fails
     */

    @Override
    public T make() throws XPathException {
        Object o = config.getInstance(className, null);
        try {
            return (T)o;
        } catch (ClassCastException e) {
            throw new XPathException("Instantiating " + className + " produced an instance of incompatible class " + o.getClass());
        }
    }

}

// Copyright (c) 2015-2020 Saxonica Limited
