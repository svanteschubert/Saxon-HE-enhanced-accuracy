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
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;


/**
 * An AtomicSequenceConverter is an expression that performs a cast (or other supplied conversion)
 * on each member of a supplied sequence
 */

public class AtomicSequenceConverter extends UnaryExpression {

    public static ToStringMappingFunction TO_STRING_MAPPER = new ToStringMappingFunction();

    protected PlainType requiredItemType;
    protected Converter converter;
    private RoleDiagnostic roleDiagnostic; // may be null


    /**
     * Constructor
     *
     * @param sequence         this must be a sequence of atomic values. This is not checked; a ClassCastException
     *                         will occur if the precondition is not satisfied.
     * @param requiredItemType the item type to which all items in the sequence should be converted,
     */

    public AtomicSequenceConverter(Expression sequence, PlainType requiredItemType) {
        super(sequence);
        this.requiredItemType = requiredItemType;
    }

    public void allocateConverterStatically(Configuration config, boolean allowNull) {
        converter = allocateConverter(config, allowNull, getBaseExpression().getItemType());
    }

    public Converter allocateConverter(Configuration config, boolean allowNull) {
        return allocateConverter(config, allowNull, getBaseExpression().getItemType());
    }

    protected Converter getConverterDynamically(XPathContext context) {
        if (converter != null) {
            return converter;
        }
        return allocateConverter(context.getConfiguration(), false);
    }

    public Converter allocateConverter(Configuration config, boolean allowNull, ItemType sourceType) {
        final ConversionRules rules = config.getConversionRules();
        Converter converter = null;
        if (sourceType instanceof ErrorType) {
            converter = StringConverter.IdentityConverter.INSTANCE;
        } else if (!(sourceType instanceof AtomicType)) {
            converter = null;
        } else if (requiredItemType instanceof AtomicType) {
            converter = rules.getConverter((AtomicType) sourceType, (AtomicType) requiredItemType);
        } else if (((SimpleType) requiredItemType).isUnionType()) {
            converter = new StringConverter.StringToUnionConverter(requiredItemType, rules);
        }

        if (converter == null && !allowNull) {
            // source type not known statically; create a converter that decides at run-time
            converter = new Converter(rules) {
                /*@NotNull*/
                @Override
                public ConversionResult convert(/*@NotNull*/ AtomicValue input) {
                    Converter converter = rules.getConverter(input.getPrimitiveType(), (AtomicType) requiredItemType);
                    if (converter == null) {
                        return new ValidationFailure("Cannot convert value from " + input.getPrimitiveType() + " to " + requiredItemType);
                    } else {
                        return converter.convert(input);
                    }
                }
            };
        }
        return converter;
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.ATOMIC_SEQUENCE;
    }

    /**
     * Get the required item type (the target type of the conversion
     *
     * @return the required item type
     */

    public PlainType getRequiredItemType() {
        return requiredItemType;
    }

    /**
     * Get the converter used to convert the items in the sequence
     *
     * @return the converter. Note that a converter is always allocated during the typeCheck() phase,
     *         even if the source type is not known.
     */

    public Converter getConverter() {
        return converter;
    }

    public void setConverter(Converter converter) {
        this.converter = converter;
    }


    /**
     * Set a RoleDiagnostic, used to give more precise error information if the conversion
     * fails
     *
     * @param role the RoleDiagnostic, which gives more precise error information
     */

    public void setRoleDiagnostic(RoleDiagnostic role) {
        // TODO: pragmatic kludge in the run-up to 9.9. If the role says error=XPTY0004, ignore it, because
        // in XQuery the cast failure should be FORG0001. We only use the error code in XSLT paths where it
        // might be something like XTTE0780
        if (role != null && !"XPTY0004".equals(role.getErrorCode())) {
            this.roleDiagnostic = role;
        }
    }

    /**
     * Get the RoleDiagnostic, used to give more precise error information if the conversion
     * fails
     *
     * @return the RoleDiagnostic, or null if none has been set
     */

    public RoleDiagnostic getRoleDiagnostic() {
        return this.roleDiagnostic;
    }


    /**
     * Simplify an expression
     *
     */

    /*@NotNull*/
    @Override
    public Expression simplify() throws XPathException {
        Expression operand = getBaseExpression().simplify();
        setBaseExpression(operand);
        if (operand instanceof Literal && requiredItemType instanceof AtomicType) {
            if (Literal.isEmptySequence(operand)) {
                return operand;
            }
            Configuration config = getConfiguration();
            allocateConverterStatically(config, true);
            if (converter != null) {
                GroundedValue val = iterate(new EarlyEvaluationContext(config)).materialize();
                return Literal.makeLiteral(val, operand);
            }
        }
        return this;
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        typeCheckChildren(visitor, contextInfo);
        Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();
        Expression operand = getBaseExpression();
        if (th.isSubType(operand.getItemType(), requiredItemType)) {
            return operand;
        } else {
            if (converter == null) {
                allocateConverterStatically(config, true);
            }
            return this;
        }
    }

    /**
     * Perform optimisation of an expression and its subexpressions.
     */
    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        Expression e = super.optimize(visitor, contextInfo);
        if (e != this) {
            return e;
        }
        if (getBaseExpression() instanceof UntypedSequenceConverter) {
            UntypedSequenceConverter asc = (UntypedSequenceConverter) getBaseExpression();
            ItemType ascType = asc.getItemType();
            if (ascType == requiredItemType) {
                return getBaseExpression();
            } else if ((requiredItemType == BuiltInAtomicType.STRING || requiredItemType == BuiltInAtomicType.UNTYPED_ATOMIC) &&
                    (ascType == BuiltInAtomicType.STRING || ascType == BuiltInAtomicType.UNTYPED_ATOMIC)) {
                UntypedSequenceConverter old = (UntypedSequenceConverter) getBaseExpression();
                UntypedSequenceConverter asc2 = new UntypedSequenceConverter(
                        old.getBaseExpression(),
                        requiredItemType);
                return asc2.typeCheck(visitor, contextInfo)
                        .optimize(visitor, contextInfo);
            }
        } else if (getBaseExpression() instanceof AtomicSequenceConverter) {
            AtomicSequenceConverter asc = (AtomicSequenceConverter) getBaseExpression();
            ItemType ascType = asc.getItemType();
            if (ascType == requiredItemType) {
                return getBaseExpression();
            } else if ((requiredItemType == BuiltInAtomicType.STRING || requiredItemType == BuiltInAtomicType.UNTYPED_ATOMIC) &&
                    (ascType == BuiltInAtomicType.STRING || ascType == BuiltInAtomicType.UNTYPED_ATOMIC)) {
                AtomicSequenceConverter old = (AtomicSequenceConverter) getBaseExpression();
                AtomicSequenceConverter asc2 = new AtomicSequenceConverter(
                        old.getBaseExpression(),
                        requiredItemType
                );
                return asc2.typeCheck(visitor, contextInfo)
                        .optimize(visitor, contextInfo);
            }
        }
        return this;

    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */
    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD;
    }

    /**
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NO_NODES_NEWLY_CREATED}.
     */

    @Override
    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties() | StaticProperty.NO_NODES_NEWLY_CREATED;
        if (requiredItemType == BuiltInAtomicType.UNTYPED_ATOMIC) {
            p &= ~StaticProperty.NOT_UNTYPED_ATOMIC;
        } else {
            p |= StaticProperty.NOT_UNTYPED_ATOMIC;
        }
        return p;
    }


    @Override
    public String getStreamerName() {
        return "AtomicSequenceConverter";
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        AtomicSequenceConverter atomicConverter = new AtomicSequenceConverter(getBaseExpression().copy(rebindings), requiredItemType);
        ExpressionTool.copyLocationInfo(this, atomicConverter);
        atomicConverter.setConverter(converter);
        atomicConverter.setRoleDiagnostic(getRoleDiagnostic());
        return atomicConverter;
    }

    /**
     * Iterate over the sequence of values
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(final XPathContext context) throws XPathException {
        SequenceIterator base = getBaseExpression().iterate(context);
        Converter conv = getConverterDynamically(context);
        if (conv == Converter.ToStringConverter.INSTANCE) {
            return new ItemMappingIterator(base, TO_STRING_MAPPER, true);
        } else {
            AtomicSequenceMappingFunction mapper = new AtomicSequenceMappingFunction();
            mapper.setConverter(conv);
            if (roleDiagnostic != null) {
                mapper.setErrorCode(roleDiagnostic.getErrorCode());
            }
            return new ItemMappingIterator(base, mapper, true);
        }
    }

    /**
     * Mapping function wrapped around a converter
     */

    public static class AtomicSequenceMappingFunction implements ItemMappingFunction {
        private Converter converter;
        private String errorCode;

        public void setConverter(Converter converter) {
            this.converter = converter;
        }

        public void setErrorCode(String code) {
            this.errorCode = code;
        }

        @Override
        public AtomicValue mapItem(Item item) throws XPathException {
            ConversionResult result = converter.convert((AtomicValue)item);
            if (errorCode != null && result instanceof ValidationFailure) {
                ((ValidationFailure)result).setErrorCode(errorCode);
            }
            return result.asAtomic();
        }
    }

    /**
     * Mapping function that converts every item in a sequence to a string
     */

    public static class ToStringMappingFunction
            implements ItemMappingFunction {
        @Override
        public StringValue mapItem(Item item) {
            return StringValue.makeStringValue(item.getStringValueCS());
        }
    }


    /**
     * Evaluate as an Item. This should only be called if the AtomicSequenceConverter has cardinality zero-or-one
     */

    @Override
    public AtomicValue evaluateItem(XPathContext context) throws XPathException {
        Converter conv = getConverterDynamically(context);
        AtomicValue item = (AtomicValue) getBaseExpression().evaluateItem(context);
        if (item == null) {
            return null;
        }
        ConversionResult result = conv.convert(item);
        if (roleDiagnostic != null && result instanceof ValidationFailure) {
            // TODO: use more of the information in the roleDiagnostic to form the error message
            ((ValidationFailure)result).setErrorCode(roleDiagnostic.getErrorCode());
        }
        return result.asAtomic();
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
        return requiredItemType;
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
        return super.equals(other) &&
                requiredItemType.equals(((AtomicSequenceConverter) other).requiredItemType);
    }

    /**
     * get HashCode for comparing two expressions. Note that this hashcode gives the same
     * result for (A op B) and for (B op A), whether or not the operator is commutative.
     */

    @Override
    public int computeHashCode() {
        return super.computeHashCode() ^ requiredItemType.hashCode();
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
        return "convert";
    }

    @Override
    protected String displayOperator(Configuration config) {
        return "convert";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("convert", this);
        destination.emitAttribute("from", AlphaCode.fromItemType(getBaseExpression().getItemType()));
        destination.emitAttribute("to", AlphaCode.fromItemType(requiredItemType));
        if (converter instanceof Converter.PromoterToDouble || converter instanceof Converter.PromoterToFloat) {
            destination.emitAttribute("flags", "p");
        }
        if (getRoleDiagnostic() != null) {
            destination.emitAttribute("diag", getRoleDiagnostic().save());
        }
        getBaseExpression().export(destination);
        destination.endElement();
    }

}

