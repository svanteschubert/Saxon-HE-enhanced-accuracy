////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.Optionality;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Information about the requirements placed by a query or stylesheet on the global
 * context item (or value): whether it is mandatory or optional, what its type must be, and
 * whether it has a default value.
 *
 * <p>In XSLT, if more than one module specifies a global context item type, they must be the same.
 * In XQuery, several modules can specify different required types, and the actual context item
 * must satisfy them all.</p>
 *
 * <p>In XQuery 4.0, the global context value can be any sequence, not just a singleton item.</p>
 */
public class GlobalContextRequirement {

    private Optionality optionality = Optionality.OPTIONAL;
    private boolean external;   // XQuery only
    private final List<SequenceType> requiredTypes = new ArrayList<>();
    private Expression defaultValue = null;  // Used in XQuery only

    /**
     * Get the required item type of the context item. If several required item types have been registered
     * (which only happens in XQuery with multiple modules) then this returns the first.
     * @return The first registered required item type
     */

    public ItemType getRequiredItemType() {
        if (requiredTypes.isEmpty()) {
            return AnyItemType.getInstance();
        } else {
            return requiredTypes.get(0).getPrimaryType();
        }
    }

    /**
     * Get the required item type of the context item. If several required item types have been registered
     * (which only happens in XQuery with multiple modules) then this returns the first.
     *
     * @return The first registered required item type
     */

    public SequenceType getRequiredSequenceType() {
        if (requiredTypes.isEmpty()) {
            return SequenceType.SINGLE_ITEM;
        } else {
            return requiredTypes.get(0);
        }
    }

    /**
     * Get all the required sequence types. In XSLT there can only be one, but in XQuery there can be several,
     * one for each module (the actual context item must satisfy them all)
     * @return the list of required item types
     */

    public List<SequenceType> getRequiredTypes() {
        return requiredTypes;
    }

    /**
     * Specify the required type of the context item / context value
     * @param requiredType the required item type
     */

    public void addRequiredSequenceType(SequenceType requiredType, boolean isMainModule) {
        if (isMainModule) {
            requiredTypes.add(0, requiredType);
        } else {
            requiredTypes.add(requiredType);
        }
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
     * Export the global context item declaration to an SEF export file.
     * Note this only needs to handle the situations that arise in XSLT.
     * @param out the export destination
     * @throws XPathException if things go wrong
     */

    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("glob");
        String use = switch (optionality) {
            case PROHIBITED -> "pro";
            case REQUIRED -> "req";
            case OPTIONAL -> "opt";
        };
        out.emitAttribute("use", use);
        if (!getRequiredItemType().equals(AnyItemType.getInstance())) {
            out.emitAttribute("type", getRequiredItemType().toExportString());
        }
        out.endElement();
    }

    /**
     * Say whether the context item/value is required, optional, or disallowed
     * @param status the relevant status
     */
    public void setContextValueOptionality(Optionality status) {
        this.optionality = status;
    }

    /**
     * Ask whether the context item/value is required, optional, or disallowed
     * @return the relevant status
     */
    public Optionality getContextValueOptionality() {
        return optionality;
    }



    public Optionality getOptionality() {
        return optionality;
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

    /**
     * Make a ContextItemStaticInfo object describing the global context item
     * @param config the Configuration
     * @return a suitable ContextItemStaticInfo
     */
    public ContextItemStaticInfo makeGlobalContextInfo(Configuration config) {
        ItemType type = optionality == Optionality.PROHIBITED ? ErrorType.getInstance() : getRequiredItemType();
        return config.makeContextItemStaticInfo(type, getContextValueOptionality());
    }
}

// Copyright (c) 2018-2026 Saxonica Limited
