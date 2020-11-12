////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;

import java.util.ArrayList;
import java.util.List;

/**
 * Information about the requirements placed by a query or stylesheet on the global
 * context item: whether it is mandatory or optional, what its type must be, and
 * whether it has a default value.
 *
 * In XSLT, if more than one module specifies a global context item type, they must be the same.
 * In XQuery, several modules can specify different required types, and the actual context item
 * must satisfy them all.
 */
public class GlobalContextRequirement {

    private boolean mayBeOmitted = true;
    private boolean absentFocus;
    private boolean external;   // XQuery only
    private List<ItemType> requiredItemTypes = new ArrayList<>();
    private Expression defaultValue = null;  // Used in XQuery only

    /**
     * Get the required item type of the context item. If several required item types have been registered
     * (which only happens in XQuery with multiple modules) then this returns the first.
     * @return The first registered required item type
     */

    public ItemType getRequiredItemType() {
        if (requiredItemTypes.isEmpty()) {
            return AnyItemType.getInstance();
        } else {
            return requiredItemTypes.get(0);
        }
    }

    /**
     * Get all the required item types. In XSLT there can only be one, but in XQuery there can be several,
     * one for each module (the actual context item must satisfy them all)
     * @return the list of required item types
     */

    public List<ItemType> getRequiredItemTypes() {
        return requiredItemTypes;
    }

    /**
     * Specify the required item type of the context item
     * @param requiredItemType the required item type
     */

    public void addRequiredItemType(ItemType requiredItemType) {
        requiredItemTypes.add(requiredItemType);
    }

    /**
     * Get the expression that supplies the default value of the context item, if any. This
     * is used only in XQuery.
     * @return the expression used to compute the value of the context item, in the absence
     * of an externally-supplied value
     */

    public Expression getDefaultValue() {
        return defaultValue;
    }

    /**
     * Set the expression used to compute the default value of the global context item
     * @param defaultValue the expression used to compute the default value.
     */

    public void setDefaultValue(Expression defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Export the global context item declaration to an SEF export file
     * @param out the export destination
     * @throws XPathException if things go wrong
     */

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("glob");
        String use;
        if (isMayBeOmitted()) {
            if (isAbsentFocus()) {
                use = "pro";
            } else {
                use = "opt";
            }
        } else {
            use = "req";
        }
        out.emitAttribute("use", use);
        if (!getRequiredItemType().equals(AnyItemType.getInstance())) {
            out.emitAttribute("type", getRequiredItemType().toExportString());
        }
        out.endElement();
    }

    /**
     * Say whether the global context item for a query or stylesheet will be absent. This is relevant
     * only for XSLT.
     * @param absent true if there will be no context item (externally or internally supplied).
     */

    public void setAbsentFocus(boolean absent) {
        this.absentFocus = absent;
    }

    /**
     * Ask whether the global context item for a query or stylesheet will be absent. This is relevant
     * only for XSLT.
     *
     * @return true if there will be no context item (externally or internally supplied).
     */

    public boolean isAbsentFocus() {
        return absentFocus;
    }

    /**
     * Say whether it is OK for the application to supply no context item
     * @param mayOmit true if it is OK for the application to supply to context item
     */

    public void setMayBeOmitted(boolean mayOmit) {
        this.mayBeOmitted = mayOmit;
    }

    /**
     * Ask whether it is OK for the application to supply no context item
     * @return true if it is OK for the application to supply to context item
     */

    public boolean isMayBeOmitted() {
        return mayBeOmitted;
    }

    /**
     * Say whether (in XQuery) the global context item is declared as external
     * @param external true if the global context item is declared as external
     */

    public void setExternal(boolean external) {
        this.external = external;
    }

    /**
     * Ask whether (in XQuery) the global context item is declared as external
     * @return true if the global context item is declared as external
     */

    public boolean isExternal() {
        return external;
    }
}

// Copyright (c) 2018-2020 Saxonica Limited
