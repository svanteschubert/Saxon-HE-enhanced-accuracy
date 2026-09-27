////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.SequenceType;

/**
 * A data structure that represents the required type of the context item, together
 * with information about whether it is known to be present or absent or whether it
 * is not known statically whether it is present or absent.
 */


public class ContextItemStaticInfo {

    protected final SequenceType sequenceType;
    protected final Optionality status;
    protected Expression contextSettingExpression;
    protected boolean parentless;

    public ContextItemStaticInfo(ItemType itemType) {
        this(itemType, Optionality.REQUIRED);
    }

    /**
     * Create a ContextItemStaticInfo
     * @param itemType the item type of the context item. If the context item is absent, set this to
     * {@link net.sf.saxon.type.ErrorType#getInstance()}.
     * @param status indicates whether the context value is required, optional, or disallowed.
     */

    public ContextItemStaticInfo(ItemType itemType, Optionality status) {
        this.sequenceType = SequenceType.makeSequenceType(itemType, StaticProperty.EXACTLY_ONE);
        this.status = status;
    }

    public ContextItemStaticInfo(SequenceType sequenceType, Optionality status) {
        this.sequenceType = sequenceType;
        this.status = status;
    }

    protected ContextItemStaticInfo(SequenceType sequenceType, Optionality status,
                                  boolean parentless) {
        this(sequenceType, status);
        this.parentless = parentless;
    }

    public ContextItemStaticInfo withStatusUnknown() {
        return new ContextItemStaticInfo(sequenceType, Optionality.OPTIONAL, parentless);
    }

    public ContextItemStaticInfo withContextSetter(Expression setter) {
        setContextSettingExpression(setter);
        return this;
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
        return sequenceType.getPrimaryType();
    }

    /**
     * Get the static type of the context item as a UType
     * @return the static context item type
     */

    public UType getContextItemUType() {
        return getItemType().getUType();
    }

    /**
     * Get the cardinality of the context value. XPath 4.0 allows the context value to be any sequence
     * @return the cardinality of the context value
     */
    public int getCardinality() {
        return sequenceType.getCardinality();
    }

    public Optionality getOptionality() {
        return status;
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

    public final static ContextItemStaticInfo DEFAULT;

    static {
        DEFAULT = new ContextItemStaticInfo(AnyItemType.getInstance(), Optionality.OPTIONAL);
    }

    public final static ContextItemStaticInfo ABSENT =
            new ContextItemStaticInfo(ErrorType.getInstance(), Optionality.PROHIBITED);

}
