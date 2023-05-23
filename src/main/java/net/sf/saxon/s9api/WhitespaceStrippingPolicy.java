////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.event.*;
import net.sf.saxon.om.*;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.Whitespace;

import java.util.function.Predicate;

/**
 * WhitespaceStrippingPolicy is class defining the possible policies for handling
 * whitespace text nodes in a source document.
 */

public class WhitespaceStrippingPolicy {

    private int policy;
    private SpaceStrippingRule stripperRules;

    /**
     * The value NONE indicates that all whitespace text nodes are retained. This is the default
     * when documents are processed without validation.
     */
    public static final WhitespaceStrippingPolicy NONE = new WhitespaceStrippingPolicy(Whitespace.NONE);

    /**
     * The value IGNORABLE indicates that whitespace text nodes in element-only content are
     * discarded. Content is element-only if it is defined by a schema or DTD definition that
     * does not allow mixed or PCDATA content. This option is in effect documentary only, since
     * this policy is always used when DTD or schema validation is selected for a {@link DocumentBuilder},
     * and is equivalent to {@link #NONE} otherwise.
     */
    public static final WhitespaceStrippingPolicy IGNORABLE = new WhitespaceStrippingPolicy(Whitespace.IGNORABLE);

    /**
     * The value ALL indicates that all whitespace-only text nodes are discarded.
     */
    public static final WhitespaceStrippingPolicy ALL = new WhitespaceStrippingPolicy(Whitespace.ALL);

    /**
     * UNSPECIFIED means that no other value has been specifically requested.
     */
    public static final WhitespaceStrippingPolicy UNSPECIFIED = new WhitespaceStrippingPolicy(Whitespace.UNSPECIFIED);

    /**
     * Create a custom whitespace stripping policy, by supplying a predicate that indicates for any given element,
     * whether whitespace text nodes among its children should be stripped or preserved. Note that xml:space attributes
     * that might be present have no effect on the outcome, and the decision applies only to immediate children,
     * not to descendants.
     * <p>Changed in 9.9 to use the standard Java 8 Predicate class in place of Saxon's version.</p>
     *
     * @param elementTest a predicate applied to element names, which should return true if whitespace-only
     *                    text node children of the element are to be stripped, false if they are to be retained.
     */
    public static WhitespaceStrippingPolicy makeCustomPolicy(final Predicate<QName> elementTest) {
        SpaceStrippingRule rule = new SpaceStrippingRule() {
            @Override
            public int isSpacePreserving(NodeName nodeName, SchemaType schemaType) {
                return elementTest.test(new QName(nodeName.getStructuredQName()))
                        ? Stripper.ALWAYS_STRIP
                        : Stripper.ALWAYS_PRESERVE;
            }

            @Override
            public ProxyReceiver makeStripper(Receiver next) {
                return new Stripper(this, next);
            }


            @Override
            public void export(ExpressionPresenter presenter) throws XPathException {
                throw new UnsupportedOperationException();
            }
        };

        WhitespaceStrippingPolicy wsp = new WhitespaceStrippingPolicy(Whitespace.XSLT);
        wsp.stripperRules = rule;
        return wsp;
    }

    private WhitespaceStrippingPolicy(int policy) {
        this.policy = policy;
        switch (policy) {
            case Whitespace.ALL:
                stripperRules = AllElementsSpaceStrippingRule.getInstance();
                break;
            case Whitespace.NONE:
                stripperRules = NoElementsSpaceStrippingRule.getInstance();
                break;
            case Whitespace.IGNORABLE:
                stripperRules = IgnorableSpaceStrippingRule.getInstance();
                break;
            default:
                break;
        }
    }

    /**
     * Create a WhitespaceStrippingPolicy based on the xsl:strip-space and xsl:preserve-space declarations
     * in a given XSLT stylesheet package
     *
     * @param pack the stylesheet package containing the xsl:strip-space and xsl:preserve-space declarations
     */

    protected WhitespaceStrippingPolicy(StylesheetPackage pack) {
        policy = Whitespace.XSLT;
        stripperRules = pack.getStripperRules();
    }

    protected int ordinal() {
        return policy;
    }

    protected SpaceStrippingRule getSpaceStrippingRule() {
        return stripperRules;
    }

    /*@NotNull*/
    protected FilterFactory makeStripper() {
        return new FilterFactory() {
            @Override
            public ProxyReceiver makeFilter(Receiver next) {
                return new Stripper(stripperRules, next);
            }
        };
    }
}

