////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;

/**
 * A data structure that represents the required type of the context item, together
 * with information about whether it is known to be present or absent or whether it
 * is not known statically whether it is present or absent.
 */


public class ContextItemStaticInfo {


    private ItemType itemType;
    private boolean contextMaybeUndefined;
    private Expression contextSettingExpression;
    private boolean parentless;

    /**
     * Create a ContextItemStaticInfo
     * @param itemType the item type of the context item. If the context item is absent, set this to
     * {@link net.sf.saxon.type.ErrorType#getInstance()}.
     * @param maybeUndefined set to true if it is possible (or certain) that the context item will be absent.
     */

    public ContextItemStaticInfo(ItemType itemType, boolean maybeUndefined) {
        this.itemType = itemType;
        this.contextMaybeUndefined = maybeUndefined;
    }

    public void setContextSettingExpression(Expression setter) {
        contextSettingExpression = setter;
    }

    public Expression getContextSettingExpression() {
        return contextSettingExpression;
    }

    /**
     * Get the static type of the context item. If the context item is known to be undefined, the
     * returned value is
     * @return the static context item type
     */

    public ItemType getItemType() {
        return itemType;
    }

    /**
     * Get the static type of the context item as a UType
     * @return the static context item type
     */

    public UType getContextItemUType() {
        return itemType.getUType();
    }

    /**
     * Ask whether it is possible that the context item is absent
     * @return true if the context item might be absent
     */

    public boolean isPossiblyAbsent() {
        return contextMaybeUndefined;
    }

    /**
     * Set streaming posture. The Saxon-HE version of this method has no effect.
     */

    public void setContextPostureStriding() {
    }

    /**
     * Set streaming posture. The Saxon-HE version of this method has no effect.
     */

    public void setContextPostureGrounded() {
    }

    public boolean isStrictStreamabilityRules() {
        return false;
    }

    public void setParentless(boolean parentless) {
        this.parentless = parentless;
    }

    public boolean isParentless() {
        return parentless;
    }

    /**
     * Default information when nothing else is known
     */

    public final static ContextItemStaticInfo DEFAULT =
            new ContextItemStaticInfo(AnyItemType.getInstance(), true);

    public final static ContextItemStaticInfo ABSENT =
            new ContextItemStaticInfo(ErrorType.getInstance(), true);

}
