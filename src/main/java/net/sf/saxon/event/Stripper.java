////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.trans.rules.RuleTarget;
import net.sf.saxon.type.ComplexType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Untyped;
import net.sf.saxon.value.Whitespace;

import java.util.Arrays;


/**
 * The {@code Stripper} class performs whitespace stripping according to the rules of
 * the xsl:strip-space and xsl:preserve-space instructions.
 * It maintains details of which elements need to be stripped.
 * The code is written to act as a SAX-like filter to do the stripping.
 *
 * <p>If you found this code as a result of a Google search, sorry about the disappointment.</p>
 */


public class Stripper extends ProxyReceiver {

    public final static StripRuleTarget STRIP = new StripRuleTarget() {
    };
    public final static StripRuleTarget PRESERVE = new StripRuleTarget() {
    };
    protected SpaceStrippingRule rule;

    public Stripper(/*@NotNull*/ SpaceStrippingRule rule, /*@NotNull*/ Receiver next) {
        super(next);
        assert rule != null;
        this.rule = rule;
    }

    // stripStack is used to hold information used while stripping nodes. We avoid allocating
    // space on the tree itself to keep the size of nodes down. Each entry on the stack is two
    // booleans, one indicates the current value of xml-space is "preserve", the other indicates
    // that we are in a space-preserving element. See masks below for information about other bits.

    // We implement our own stack to avoid the overhead of allocating objects.

    private int[] stripStack = new int[100];
    private int top = 0;

    /**
     * Decide whether an element is in the set of white-space preserving element types
     *
     * @param name Identifies the name of the element whose whitespace is (or is not) to
     *             be preserved
     * @param type The schema type of this element
     * @return ALWAYS_PRESERVE if the element is in the set of white-space preserving
     *         element types, ALWAYS_STRIP if the element is to be stripped regardless of the
     *         xml:space setting, and STRIP_DEFAULT otherwise
     * @throws XPathException if the rules are ambiguous and ambiguities are to be
     *                        reported as errors
     */

    private int isSpacePreserving(NodeName name, SchemaType type) throws XPathException {
        return rule.isSpacePreserving(name, type);
    }

    public static final int ALWAYS_PRESERVE = 0x01;    // whitespace always preserved (e.g. xsl:text)
    public static final int ALWAYS_STRIP = 0x02;       // whitespace always stripped (e.g. xsl:choose)
    public static final int STRIP_DEFAULT = 0x00;      // no special action
    public static final int PRESERVE_PARENT = 0x04;    // parent element specifies xml:space="preserve"
    public static final int SIMPLE_CONTENT = 0x08;     // type annotation indicates simple typed content
    public static final int ASSERTIONS_EXIST = 0x10;   // XSD 1.1 assertions are in scope


    /**
     * Callback interface for SAX: not for application use
     */

    @Override
    public void open() throws XPathException {
        // System.err.println("Stripper#startDocument()");
        top = 0;
        stripStack[top] = ALWAYS_PRESERVE;             // {xml:space = default, preserve this element = true}
        super.open();
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        //System.err.println("startElement " + elemName.getDisplayName());
        nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);

        int preserveParent = stripStack[top];
        int preserve = preserveParent & (PRESERVE_PARENT | ASSERTIONS_EXIST);

        int elementStrip = isSpacePreserving(elemName, type);
        if (elementStrip == ALWAYS_PRESERVE) {
            preserve |= ALWAYS_PRESERVE;
        } else if (elementStrip == ALWAYS_STRIP) {
            preserve |= ALWAYS_STRIP;
        }
        if (type != Untyped.getInstance()) {
            if (preserve == 0) {
                // if the element has simple content, whitespace stripping is disabled
                if (type.isSimpleType() || ((ComplexType) type).isSimpleContent()) {
                    preserve |= SIMPLE_CONTENT;
                }
            }
            if (type instanceof ComplexType && ((ComplexType) type).hasAssertions()) {
                preserve |= ASSERTIONS_EXIST;
            }
        }

        // put "preserve" value on top of stack

        top++;
        if (top >= stripStack.length) {
            stripStack = Arrays.copyOf(stripStack, top * 2);
        }
        stripStack[top] = preserve;

        String xmlSpace = attributes.getValue(NamespaceUri.XML, "space");
        if (xmlSpace != null) {
            if (Whitespace.trim(xmlSpace).equals("preserve")) {
                stripStack[top] |= PRESERVE_PARENT;
            } else {
                stripStack[top] &= ~PRESERVE_PARENT;
            }
        }
    }

    /**
     * Handle an end-of-element event
     */

    @Override
    public void endElement() throws XPathException {
        nextReceiver.endElement();
        top--;
    }

    /**
     * Handle a text node
     */

    @Override
    public void characters(UnicodeString chars, Location locationId, int properties) throws XPathException {
        // assume adjacent chunks of text are already concatenated

        if (((((stripStack[top] & (ALWAYS_PRESERVE | PRESERVE_PARENT | SIMPLE_CONTENT | ASSERTIONS_EXIST)) != 0) &&
                (stripStack[top] & ALWAYS_STRIP) == 0)
                || !Whitespace.isAllWhite(chars))
                && !chars.isEmpty()) {
            nextReceiver.characters(chars, locationId, properties);
        }
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    @Override
    public boolean usesTypeAnnotations() {
        return true;
    }

    public static class StripRuleTarget implements RuleTarget {
        @Override
        public void export(ExpressionPresenter presenter) throws XPathException {
            // no-op
        }

        @Override
        public void registerRule(Rule rule) {
            // no action
        }
    }
}

