////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;


import net.sf.saxon.event.IgnorableWhitespaceStripper;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.Stripper;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.type.ComplexType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Untyped;

/**
 * A whitespace stripping rule that strips whitespace text node children from all elements having an element-only content
 * model, regardless of the value of the xml:space attribute
 */

public class IgnorableSpaceStrippingRule implements SpaceStrippingRule {

    private final static IgnorableSpaceStrippingRule THE_INSTANCE = new IgnorableSpaceStrippingRule();

    public static IgnorableSpaceStrippingRule getInstance() {
        return THE_INSTANCE;
    }

    /**
     * Decide whether an element is in the set of white-space preserving element types
     *
     * @param name identifies the name of the element being tested
     * @param schemaType the type annotation of the element being tested
     * @return STRIP_DEFAULT: strip spaces unless xml:space tells you not to.
     */

    @Override
    public int isSpacePreserving(NodeName name, SchemaType schemaType) {
        if (schemaType != Untyped.getInstance() && schemaType.isComplexType() &&
                    !((ComplexType) schemaType).isSimpleContent() &&
                    !((ComplexType) schemaType).isMixedContent()) {
            return Stripper.ALWAYS_STRIP;
        } else {
            return Stripper.ALWAYS_PRESERVE;
        }
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
        return new IgnorableWhitespaceStripper(next);
    }


    /**
     * Export this rule as part of an exported stylesheet
     *
     * @param presenter the output handler
     */
    @Override
    public void export(ExpressionPresenter presenter) {
        presenter.startElement("strip.ignorable");
        presenter.endElement();
    }
}

