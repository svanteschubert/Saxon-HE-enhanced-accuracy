////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.type.UType;

/**
 * A mapping function for use in conjunction with an {@link ItemMappingIterator} that checks that
 * all the items in a sequence are instances of a given item type
 *
 */
public class ItemTypeCheckingFunction implements ItemMappingFunction {

    private ItemType requiredItemType;
    private RoleDiagnostic role;
    private Location location;
    private Expression failingExpression;
    private Configuration config = null;

    /**
     * Create the type-checking function
     *
     * @param requiredItemType the item type that all items in the sequence must conform to
     * @param role             information for error messages
     * @param failingExpression  the location of the expression for error messages
     * @param config           the Saxon configuration
     */

    public ItemTypeCheckingFunction(ItemType requiredItemType, RoleDiagnostic role, Expression failingExpression, Configuration config) {
        this.requiredItemType = requiredItemType;
        this.role = role;
        this.failingExpression = failingExpression;
        this.location = failingExpression.getLocation();
        this.config = config;
    }

    public ItemTypeCheckingFunction(ItemType requiredItemType, RoleDiagnostic role, Location locator, Configuration config) {
        this.requiredItemType = requiredItemType;
        this.role = role;
        this.location = locator;
        this.config = config;
    }

    @Override
    public Item mapItem(Item item) throws XPathException {
        testConformance(item, config);
        return item;
    }

    private void testConformance(Item item, Configuration config) throws XPathException {
        final TypeHierarchy th = config.getTypeHierarchy();
        if (requiredItemType.matches(item, th)) {
            // OK, no action
        } else if (requiredItemType.getUType().subsumes(UType.STRING) && BuiltInAtomicType.ANY_URI.matches(item, th)) {
            // OK, no action
        } else {
            String message = role.composeErrorMessage(requiredItemType, item, th);

            String errorCode = role.getErrorCode();
            if ("XPDY0050".equals(errorCode)) {
                // error in "treat as" assertion
                XPathException te = new XPathException(message, errorCode);
                te.setFailingExpression(failingExpression);
                te.setLocator(location);
                te.setIsTypeError(false);
                throw te;
            } else {
                XPathException te = new XPathException(message, errorCode);
                te.setFailingExpression(failingExpression);
                te.setLocator(location);
                te.setIsTypeError(true);
                throw te;
            }
        }
    }

}

