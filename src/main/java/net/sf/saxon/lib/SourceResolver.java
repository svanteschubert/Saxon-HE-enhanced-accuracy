////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.Source;


/**
 * This interface defines a SourceResolver. A SourceResolver can be registered as
 * part of the Configuration, and enables new kinds of Source to be recognized
 * beyond those that are natively recognized by Saxon.
 * <p>The task of the SourceResolver is to take any Source as input, and to return
 * a Source that has native support in Saxon: that is, one of the classes
 * StreamSource, SAXSource, DOMSource, {@link net.sf.saxon.om.NodeInfo},
 * or {@link net.sf.saxon.pull.PullSource}</p>
 *
 * @author Michael H. Kay
 */

public interface SourceResolver {

    /**
     * Resolve a Source.
     *
     * @param source A source object, typically the source supplied as the first
     *               argument to {@link javax.xml.transform.Transformer#transform(javax.xml.transform.Source, javax.xml.transform.Result)}
     *               or similar methods.
     * @param config The Configuration. This provides the SourceResolver with access to
     *               configuration information; it also allows the SourceResolver to invoke the
     *               resolveSource() method on the Configuration object as a fallback implementation.
     * @return a source object that Saxon knows how to process. This must be an instance of one
     *         of the classes  StreamSource, SAXSource, DOMSource, {@link AugmentedSource},
     *         {@link net.sf.saxon.om.NodeInfo},
     *         or {@link net.sf.saxon.pull.PullSource}. Return null if the Source object is not
     *         recognized
     * @throws XPathException if the Source object is recognized but cannot be processed
     */

    /*@Nullable*/
    public Source resolveSource(Source source, Configuration config) throws XPathException;

}

