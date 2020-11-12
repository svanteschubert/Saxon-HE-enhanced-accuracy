////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;


import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

/**
 * Interface for deciding whether a particular element is to have whitespace text nodes stripped
 */

public interface SpaceStrippingRule {

    /**
     * Decide whether an element is in the set of white-space preserving element types
     *
     * @param nodeName Identifies the name of the element whose whitespace is (or is not) to
     *                 be preserved
     * @param schemaType The type annotation of the element whose whitespace is (or is not) to
     *                   be preserved
     * @return {@link net.sf.saxon.event.Stripper#ALWAYS_PRESERVE} if the element is in the set of white-space preserving
     *         element types, {@link net.sf.saxon.event.Stripper#ALWAYS_STRIP} if the element is to be stripped regardless of the
     *         xml:space setting, and {@link net.sf.saxon.event.Stripper#STRIP_DEFAULT} otherwise
     * @throws net.sf.saxon.trans.XPathException
     *          if the rules are ambiguous and ambiguities are to be
     *          reported as errors
     */

    int isSpacePreserving(NodeName nodeName, SchemaType schemaType) throws XPathException;

    /**
     * Make a filter to implement these space-stripping rules, or null if no filtering
     * is necessary
     * @param next the Receiver that is to receiver the filtered event stream
     * @return a filter in the form of a ProxyReceiver, or null
     */

     ProxyReceiver makeStripper(Receiver next);

    /**
     * Export this rule as part of an exported stylesheet
     * @param presenter the output handler
     */

    void export(ExpressionPresenter presenter) throws XPathException;

}

