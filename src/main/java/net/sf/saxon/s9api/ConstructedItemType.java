////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.type.TypeHierarchy;

import java.util.Objects;

/**
 * An item type constructed by the {@link ItemTypeFactory} (as distinct from one that is predefined)
 * <p>This class is not user-visible.</p>
 */

@CSharpInjectMembers(
        code={"public override bool Matches(Saxon.Api.XdmItem item) {return matches(item);}",
              "public override bool Subsumes(Saxon.Api.XdmItemType other) {return subsumes(other);}"
        }
)

class ConstructedItemType extends ItemType {

    private final Configuration config;

    /**
     * Protected constructor
     *
     * @param underlyingType the Saxon internal item type. Must not be null.
     * @param config      The Saxon Configuration Must not be null
     */

    protected ConstructedItemType(/*@Nullable*/ net.sf.saxon.type.ItemType underlyingType, Configuration config) {
        super(underlyingType);
        Objects.requireNonNull(config);
        Objects.requireNonNull(underlyingType);
        this.config = config;
    }

    /**
     * Get the conversion rules implemented by this type. The conversion rules reflect variations
     * between different versions of the W3C specifications, for example XSD 1.1 allows "+INF" as
     * a lexical representation of xs:double, while XSD 1.0 does not.
     *
     * @return the conversion rules
     */

    @Override
    public ConversionRules getConversionRules() {
        return config.getConversionRules();
    }

    /**
     * Determine whether this item type matches a given item.
     *
     * @param item the item to be tested against this item type
     * @return true if the item matches this item type, false if it does not match.
     * @throws net.sf.saxon.s9api.SaxonApiUncheckedException in the rare event that the item is a map or
     * array containing a lazily evaluated sequence, and evaluation of the sequence fails with a dynamic
     * error in the course of checking
     */

    @Override
    public boolean matches(XdmItem item) {
        TypeHierarchy th = config.getTypeHierarchy();
        return underlyingType.matches(item.getUnderlyingValue(), th);
    }

    /**
     * Determine whether this ItemType subsumes another ItemType. Specifically,
     * <code>A.subsumes(B) is true if every value that matches the ItemType B also matches
     * the ItemType A.
     *
     * @param other the other ItemType
     * @return true if this ItemType subsumes the other ItemType. This includes the case where A and B
     *         represent the same ItemType.
     * @since 9.1
     */

    @Override
    public boolean subsumes(ItemType other) {
        TypeHierarchy th = config.getTypeHierarchy();
        return th.isSubType(other.getUnderlyingItemType(), underlyingType);
    }

    /**
     * Method to get the underlying Saxon implementation object
     * <p>This gives access to Saxon methods that may change from one release to another.</p>
     *
     * @return the underlying Saxon implementation object
     */

    @Override
    public net.sf.saxon.type.ItemType getUnderlyingItemType() {
        return underlyingType;
    }

    /**
     * Get the underlying Configuration
     *
     * @return the Configuration used to create this ItemType.
     */

    protected Configuration getConfiguration() {
        return config;
    }


}

