////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;


import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Stripper;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.type.SchemaType;

/**
 * A whitespace stripping rule that strips all elements unless xml:space indicates that whitespace
 * should be preserved.
 */

public class AllElementsSpaceStrippingRule implements SpaceStrippingRule {

    private final static AllElementsSpaceStrippingRule THE_INSTANCE = new AllElementsSpaceStrippingRule();

    public static AllElementsSpaceStrippingRule getInstance() {
        return THE_INSTANCE;
    }

    /**
     * Decide whether an element is in the set of white-space preserving element types
     *
     * @param fingerprint identifies the element being tested
     * @param schemaType
     * @return STRIP_DEFAULT: strip spaces unless xml:space tells you not to.
     */

    @Override
    public int isSpacePreserving(NodeName fingerprint, SchemaType schemaType) {
        return Stripper.STRIP_DEFAULT;
    }

    /**
     * Make a filter to implement these space-stripping rules, or null if no filtering
     * is necessary
     *
     * @return a filter in the form of a ProxyReceiver, or null
     * @param next
     */
    @Override
    public ProxyReceiver makeStripper(Receiver next) {
        return new Stripper(this, next);
    }

    /**
     * Export this rule as part of an exported stylesheet
     *
     * @param presenter the output handler
     */
    @Override
    public void export(ExpressionPresenter presenter) {
        presenter.startElement("strip.all");
        presenter.endElement();
    }
}

