////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.UntypedAtomicValue;

/**
 * An UntypedSequenceConverter is an expression that performs a cast on each member of
 * a supplied sequence that is an untypedAtomic value, while leaving other items unchanged
 */

public final class UntypedSequenceConverter extends AtomicSequenceConverter {

    /**
     * Constructor
     *
     * @param sequence         this must be a sequence of atomic values. This is not checked; a ClassCastException
     *                         will occur if the precondition is not satisfied.
     * @param requiredItemType the item type to which all items in the sequence should be converted,
     *                         using the rules for "cast as".
     */

    public UntypedSequenceConverter(Expression sequence, PlainType requiredItemType) {
        super(sequence, requiredItemType);
    }

    /**
     * Create an AtomicSequenceConverter that converts all untypedAtomic values in the input sequence to
     * a specified target type, while leaving items other than untypedAtomic unchanged
     *
     * @param config           the Saxon configuration
     * @param operand          the expression that delivers the input sequence
     * @param requiredItemType the type to which untypedAtomic values should be cast, which must either be an
     *                         atomic type or a "plain" union type
     * @return an AtomicSequenceConverter that performs the required conversion
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs, for example if the target type is namespace-sensitive
     */


    public static UntypedSequenceConverter makeUntypedSequenceConverter(Configuration config, Expression operand, PlainType requiredItemType)
            throws XPathException {
        UntypedSequenceConverter atomicSeqConverter =
                new UntypedSequenceConverter(operand, requiredItemType);
        final ConversionRules rules = config.getConversionRules();
        final Converter untypedConverter;
        if (requiredItemType.isNamespaceSensitive()) {
            throw new XPathException("Cannot convert untyped atomic values to a namespace-sensitive type", "XPTY0117");
        }
        if (requiredItemType.isAtomicType()) {
            untypedConverter = rules.getConverter(BuiltInAtomicType.UNTYPED_ATOMIC, (AtomicType) requiredItemType);
        } else if (requiredItemType == NumericType.getInstance()) {
            // converting untyped to numeric is common, and is effectively the same as converting to double
            untypedConverter = rules.getConverter(BuiltInAtomicType.UNTYPED_ATOMIC, BuiltInAtomicType.DOUBLE);
            atomicSeqConverter.requiredItemType = BuiltInAtomicType.DOUBLE;
        } else {
            untypedConverter = new StringConverter.StringToUnionConverter(requiredItemType, rules);
        }
        // source type not known statically; create a converter that decides at run-time
        Converter converter = new UntypedConverter(rules, untypedConverter);
        atomicSeqConverter.setConverter(converter);
        return atomicSeqConverter;
    }

    /**
     * A Converter that converts untyped atomic values to the required type, while
     * leaving other values unchanged
     */

    public static class UntypedConverter extends Converter {
        Converter untypedConverter = null;

        /**
         * Create an UntypedConverter
         *
         * @param rules     the conversion rules
         * @param converter the converter to be used in the case where the supplied
         *                  value is untypedAtomic
         */

        public UntypedConverter(ConversionRules rules, Converter converter) {
            super(rules);
            untypedConverter = converter;
            //untypedConverter.setConversionRules(rules);

        }

        /*@NotNull*/
        @Override
        public ConversionResult convert(/*@NotNull*/ AtomicValue input) {
            if (input instanceof UntypedAtomicValue) {
                return untypedConverter.convert(input);
            } else {
                return input;
            }
        }
    }

    public static UntypedSequenceConverter makeUntypedSequenceRejector(Configuration config, final Expression operand, final PlainType requiredItemType) {
        UntypedSequenceConverter atomicSeqConverter = new UntypedSequenceConverter(operand, requiredItemType);
        final ConversionRules rules = config.getConversionRules();
        final Converter untypedConverter = new Converter() {
            // called when an untyped atomic value is encountered
            @Override
            public ConversionResult convert(/*@NotNull*/ AtomicValue input) {
                ValidationFailure vf = new ValidationFailure(
                        "Implicit conversion of untypedAtomic value to " + requiredItemType.toString() + " is not allowed");
                vf.setErrorCode("XPTY0117");
                vf.setLocator(operand.getLocation());
                return vf;
            }
        };

        // source type not known statically; create a converter that decides at run-time
        Converter converter = new UntypedConverter(rules, untypedConverter);
        atomicSeqConverter.setConverter(converter);
        return atomicSeqConverter;
    }

    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e2 = super.typeCheck(visitor, contextInfo);
        if (e2 != this) {
            return e2;
        }
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
        Expression base = getBaseExpression();
        if (th.relationship(base.getItemType(), BuiltInAtomicType.UNTYPED_ATOMIC) == Affinity.DISJOINT ||
                base.hasSpecialProperty(StaticProperty.NOT_UNTYPED_ATOMIC)) {
            // operand cannot return untyped atomic values, so there's nothing to convert
            return getBaseExpression();
        }
        return this;
    }

    /**
     * Determine the special properties of this expression
     *
     * @return {@link net.sf.saxon.expr.StaticProperty#NO_NODES_NEWLY_CREATED}.
     */

    @Override
    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        return p | StaticProperty.NO_NODES_NEWLY_CREATED | StaticProperty.NOT_UNTYPED_ATOMIC;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        UntypedSequenceConverter atomicConverter = new UntypedSequenceConverter(getBaseExpression().copy(rebindings), getRequiredItemType());
        ExpressionTool.copyLocationInfo(this, atomicConverter);
        atomicConverter.setConverter(converter);
        atomicConverter.setRoleDiagnostic(getRoleDiagnostic());
        return atomicConverter;
    }


    /**
     * Determine the data type of the items returned by the expression, if possible
     *
     * @return a value such as Type.STRING, Type.BOOLEAN, Type.NUMBER, Type.NODE,
     *         or Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        if (getBaseExpression().getItemType() == BuiltInAtomicType.UNTYPED_ATOMIC) {
            return getRequiredItemType();
        } else {
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            return Type.getCommonSuperType(getRequiredItemType(), getBaseExpression().getItemType(), th);
        }
    }

    /**
     * Determine the static cardinality of the expression
     */

    @Override
    public int computeCardinality() {
        return getBaseExpression().getCardinality();
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        return other instanceof UntypedSequenceConverter &&
                getBaseExpression().isEqual(((UntypedSequenceConverter)other).getBaseExpression());
    }

    /**
     * get HashCode for comparing two expressions.
     */

    @Override
    public int computeHashCode() {
        return super.computeHashCode();
    }

    @Override
    protected String displayOperator(Configuration config) {
        return "convertUntyped";
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     *         The name will always be in the form of a lexical XML QName, and should match the name used
     *         in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "convertUntyped";
    }

    @Override
    public String toShortString() {
        return getBaseExpression().toShortString();
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("cvUntyped", this);
        destination.emitAttribute("to", AlphaCode.fromItemType(getRequiredItemType()));
        if (getRoleDiagnostic() != null) {
            destination.emitAttribute("diag", getRoleDiagnostic().save());
        }
        getBaseExpression().export(destination);
        destination.endElement();
    }

}

