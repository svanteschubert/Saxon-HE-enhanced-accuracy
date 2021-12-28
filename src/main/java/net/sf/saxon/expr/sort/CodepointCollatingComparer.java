////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

/**
 * An AtomicComparer used for comparing strings, untypedAtomic values, and URIs using the Unicode codepoint
 * collation.
 * A CodepointCollatingComparer is used when it is known in advance that both operands will be of these
 * types, and when the collation is the unicode codepoint collation.
 * This enables all conversions and promotions to be bypassed: the string values of both operands
 * are simply extracted and passed to the collator for comparison.
 * <p>The difference between using this class and using the underlying CodepointCollator directly is that
 * the compare() method in this class expects two instances of AtomicValue as its operands, whereas the
 * underlying class expects two instances of java.lang.String. This class makes use of the extra information
 * held in the wrapping StringValue object, specifically, the knowledge of whether the string contains
 * surrogate pairs.</p>
 *
 */

public class CodepointCollatingComparer implements AtomicComparer {

    private static CodepointCollator collator = CodepointCollator.getInstance();

    private static CodepointCollatingComparer THE_INSTANCE = new CodepointCollatingComparer();

    /**
     * Get the singular instance of this class
     *
     * @return the singleton instance
     */

    public static CodepointCollatingComparer getInstance() {
        return THE_INSTANCE;
    }

    private CodepointCollatingComparer() {
    }

    @Override
    public StringCollator getCollator() {
        return collator;
    }

    /**
     * Supply the dynamic context in case this is needed for the comparison
     *
     * @param context the dynamic evaluation context
     * @return either the original AtomicComparer, or a new AtomicComparer in which the context
     *         is known. The original AtomicComparer is not modified
     */

    @Override
    public AtomicComparer provideContext(XPathContext context) {
        return this;
    }

    /**
     * Compare two AtomicValue objects according to the rules for their data type. UntypedAtomic
     * values are compared as if they were strings; if different semantics are wanted, the conversion
     * must be done by the caller.
     *
     * @param a the first object to be compared. This must be either be an instance
     *          of AtomicValue, or null to represent an empty sequence. Empty collates before non-empty.
     * @param b the second object to be compared. This must be either be an instance
     *          of AtomicValue, or null to represent an empty sequence.
     * @return &lt;0 if a&lt;b, 0 if a=b, &gt;0 if a&gt;b
     * @throws ClassCastException if the objects are not comparable
     */

    @Override
    public int compareAtomicValues(/*@Nullable*/ AtomicValue a, AtomicValue b) {
        if (a == null) {
            return (b == null ? 0 : -1);
        } else if (b == null) {
            return +1;
        }
        StringValue as = (StringValue) a;
        StringValue bs = (StringValue) b;
        //if (as.containsSurrogatePairs() || bs.containsSurrogatePairs()) {
        return CodepointCollator.compareCS(as.getStringValueCS(), bs.getStringValueCS());
        //} else {
        // optimize to use UTF-16 binary comparison
        //    return as.getStringValue().compareTo(bs.getStringValue());
        //}
    }

    /**
     * Compare two AtomicValue objects for equality. The values must be instances of xs:string or a type
     * derived from xs:string. The method will also handle xs:untypedAtomic and xs:anyURI values.
     *
     * @param a the first object to be compared.
     * @param b the second object to be compared.
     * @return true if the values are equal, false if not
     * @throws ClassCastException if either value is not xs:string or a subtype
     */

    @Override
    public boolean comparesEqual(AtomicValue a, AtomicValue b) {
        return ((StringValue)a).codepointEquals((StringValue)b);
    }

    /**
     * Create a string representation of this AtomicComparer that can be saved in a compiled
     * package and used to reconstitute the AtomicComparer when the package is reloaded
     *
     * @return a string representation of the AtomicComparer
     */
    @Override
    public String save() {
        return "CCC";
    }
}

