////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.ma.map.RecordType;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

import java.util.function.Supplier;

import static net.sf.saxon.type.Affinity.DISJOINT;
import static net.sf.saxon.type.Affinity.SAME_TYPE;
import static net.sf.saxon.type.Affinity.SUBSUMED_BY;
import static net.sf.saxon.type.Affinity.SUBSUMES;

/**
 * This class provides Saxon's type checking capability. It contains a method,
 * staticTypeCheck, which is called at compile time to perform type checking of
 * an expression.
 */

public class TypeChecker {

    public TypeChecker() {
    }

    /**
     * Check an expression against a required type, modifying it if necessary.
     * <p>This method takes the supplied expression and checks to see whether it is
     * known statically to conform to the specified type. There are three possible
     * outcomes. If the static type of the expression is a subtype of the required
     * type, the method returns the expression unchanged. If the static type of
     * the expression is incompatible with the required type (for example, if the
     * supplied type is integer and the required type is string) the method throws
     * an exception (this results in a compile-time type error being reported). If
     * the static type is a supertype of the required type, then a new expression
     * is constructed that evaluates the original expression and checks the dynamic
     * type of the result; this new expression is returned as the result of the
     * method.</p>
     * <p>The rules applied are those for function calling in XPath, that is, the rules
     * that the argument of a function call must obey in relation to the signature of
     * the function. Some contexts require slightly different rules (for example,
     * operands of polymorphic operators such as "+"). In such cases this method cannot
     * be used.</p>
     * <p>Note that this method does <b>not</b> do recursive type-checking of the
     * sub-expressions.</p>
     *
     * @param supplied            The expression to be type-checked
     * @param req                 The required type for the context in which the expression is used
     * @param roleSupplier                Information about the role of the subexpression within the
     *                            containing expression, used to provide useful error messages
     * @param visitor             An expression visitor
     * @return The original expression if it is type-safe, or the expression
     * wrapped in a run-time type checking expression if not.
     * @throws XPathException if the supplied type is statically inconsistent with the
     *                        required type (that is, if they have no common subtype)
     */

    public Expression staticTypeCheck(Expression supplied,
                                      SequenceType req,
                                      Supplier<RoleDiagnostic> roleSupplier,
                                      final ExpressionVisitor visitor)
            throws XPathException {

        // System.err.println("Static Type Check on expression (requiredType = " + req + "):"); supplied.display(10);

        if (supplied.implementsStaticTypeCheck()) {
            return supplied.staticTypeCheck(req, false, roleSupplier, visitor);
        }

        Expression exp = supplied;
        final StaticContext env = visitor.getStaticContext();
        final Configuration config = env.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();
        final ContextItemStaticInfo defaultContextInfo = config.getDefaultContextItemStaticInfo();
        final boolean allow40 = env.getXPathVersion() >= 40;

        final ItemType reqItemType = req.getPrimaryType();
        int reqCard = req.getCardinality();

        ItemType suppliedItemType = null;
        // item type of the supplied expression: null means not yet calculated
        int suppliedCard = -1;
        // cardinality of the supplied expression: -1 means not yet calculated

        boolean cardOK = reqCard == StaticProperty.ALLOWS_ZERO_OR_MORE;
        // Unless the required cardinality is zero-or-more (no constraints).
        // check the static cardinality of the supplied expression
        if (!cardOK) {
            suppliedCard = exp.getCardinality();
            cardOK = Cardinality.subsumes(reqCard, suppliedCard);
            // May later find that cardinality is not OK after all, if atomization takes place
        }

        boolean itemTypeOK = reqItemType instanceof AnyItemType;
        if (reqCard == StaticProperty.ALLOWS_ZERO) {
            // required type is empty sequence; we don't need an item check because a cardinality check suffices
            itemTypeOK = true;
        }
        // Unless the required item type and content type are ITEM (no constraints)
        // check the static item type against the supplied expression.
        // NOTE: we don't currently do any static inference regarding the content type
        if (!itemTypeOK) {
            suppliedItemType = exp.getItemType();
            if (reqItemType == null || suppliedItemType == null) {
                throw new NullPointerException();
            }
            Affinity affinity = th.relationship(reqItemType, suppliedItemType);
            itemTypeOK = affinity == Affinity.SAME_TYPE || affinity == Affinity.SUBSUMES;
        }


        if (reqItemType.isPlainType()) {

            if (!itemTypeOK) {

                // rule 1: Atomize
                if (!suppliedItemType.isPlainType() &&
                        !(suppliedCard == StaticProperty.EMPTY)) {
                    boolean atomizable = suppliedItemType.isAtomizable(th);
                    if (atomizable && (exp.getSpecialProperties() & StaticProperty.COMPUTED_FUNCTION) != 0) {
                        atomizable = false; // in this case we know the function isn't going to be an array
                    }
                    if (!atomizable) {
                        String shortItemType;
                        if (suppliedItemType instanceof RecordType) {
                            shortItemType = "a record type";
                        } else if (suppliedItemType instanceof MapType) {
                            shortItemType = "a map type";
                        } else if (suppliedItemType instanceof FunctionItemType) {
                            shortItemType = "a function type";
                        } else if (suppliedItemType instanceof NodeTest) {
                            shortItemType = "an element type with element-only content";
                        } else {
                            shortItemType = suppliedItemType.toString();
                        }
                        RoleDiagnostic role = roleSupplier.get();
                        throw new XPathException(
                                "An atomic value is required for the " + role.getMessage() +
                                        ", but the supplied type is " + shortItemType + ", which cannot be atomized")
                                .withErrorCode("FOTY0013")
                                .withLocation(supplied.getLocation())
                                .asTypeError()
                                .withFailingExpression(supplied);
                    }

                    if (exp.getRetainedStaticContext() == null) {
                        exp.setRetainedStaticContextLocally(env.makeRetainedStaticContext());
                    }
                    Expression cexp = Atomizer.makeAtomizer(exp, roleSupplier);
                    ExpressionTool.copyLocationInfo(exp, cexp);
                    exp = cexp;
                    cexp = exp.simplify();
                    ExpressionTool.copyLocationInfo(exp, cexp);
                    exp = cexp;
                    suppliedItemType = exp.getItemType();
                    suppliedCard = exp.getCardinality();
                    cardOK = Cardinality.subsumes(reqCard, suppliedCard);
                }
            }

            // rule 2: convert untypedAtomic to the required type

            // The specification says we do untypedAtomic conversion first, then promotion. However, if the
            // target type is one to which promotion applies, then we combine the two operations into one:
            // the conversion functions that handle type promotion (for example from float to double) also
            // handle conversion from untypedAtomic, so we only need to make one pass over the data.

            // rule 3: type promotion (combined with untypedAtomic conversion)

            if (reqItemType instanceof BuiltInAtomicType && ((BuiltInAtomicType) reqItemType).isPrimitiveType() && !itemTypeOK) {
                int rt = ((BuiltInAtomicType) reqItemType).getFingerprint();
                UType promotables = promotableTypes(rt, allow40);
                if (suppliedItemType.getUType().intersection(promotables).equals(UType.VOID)) {
                    // Promotion cannot succeed: raise a static type error
                    RoleDiagnostic role = roleSupplier.get();
                    throw new XPathException(
                            "An item of type " + suppliedItemType +
                                    " cannot be converted to " + reqItemType +
                                    " as required for the " + role.getMessage())
                            .withErrorCode(role.getErrorCode())
                            .withLocation(supplied.getLocation())
                            .withFailingExpression(supplied);
                }
                ConversionRules rules = config.getConversionRules();
                Expression promoted = null;
                Converter converter = makePromotingConverter(suppliedItemType, rt, rules, allow40);
                if (converter != null) {
                    promoted = makePromoter(exp, converter, (BuiltInAtomicType)reqItemType);
                }

                if (promoted != null) {
                    if (promoted instanceof AtomicSequenceConverter) {
                        ((AtomicSequenceConverter) promoted).setRoleDiagnostic(roleSupplier);
                    }
                    exp = promoted;
                    try {
                        exp = exp.simplify().typeCheck(visitor, defaultContextInfo);
                    } catch (XPathException err) {
                        throw err.maybeWithLocation(exp.getLocation())
                                .asStaticError()
                                .withFailingExpression(supplied);
                    }
                    suppliedItemType = reqItemType;
                    suppliedCard = -1;
                    itemTypeOK = true;

                }
            }

            if (!itemTypeOK) {

                // Revisit rule 2 (conversion from untyped atomic) for target types that have not been handled by a Promoter

                //   2b: all supplied values are untyped atomic. Convert if necessary, and we're finished.

                if (suppliedItemType.equals(BuiltInAtomicType.UNTYPED_ATOMIC)
                        && !(reqItemType.equals(BuiltInAtomicType.UNTYPED_ATOMIC) || reqItemType.equals(BuiltInAtomicType.ANY_ATOMIC))) {

                    if (((PlainType) reqItemType).isNamespaceSensitive()) {
                        // See spec bug 11964
                        RoleDiagnostic role = roleSupplier.get();
                        throw new XPathException(
                                "An untyped atomic value cannot be converted to a QName or NOTATION as required for the " +
                                        role.getMessage())
                                .withErrorCode("XPTY0117")
                                .withLocation(supplied.getLocation())
                                .withFailingExpression(supplied);
                    }
                    UntypedSequenceConverter cexp = UntypedSequenceConverter.makeUntypedSequenceConverter(config, exp, (PlainType) reqItemType);
                    cexp.setRoleDiagnostic(roleSupplier);
                    ExpressionTool.copyLocationInfo(exp, cexp);
                    try {
                        if (exp instanceof Literal) {
                            try {
                                exp = Literal.makeLiteral(
                                        SequenceTool.toGroundedValue(cexp.iterate(visitor.makeDynamicContext())), exp);
                                ExpressionTool.copyLocationInfo(cexp, exp);
                            } catch (UncheckedXPathException e) {
                                throw e.getXPathException();
                            }
                        } else {
                            exp = cexp;
                        }
                    } catch (XPathException err) {
                        throw err.maybeWithLocation(exp.getLocation())
                                .withFailingExpression(supplied)
                                .maybeWithErrorCode(roleSupplier.get().getErrorCode())
                                .asStaticError();
                    }
                    itemTypeOK = true;
                    suppliedItemType = reqItemType;
                }

                //   2c: some supplied values are untyped atomic. Convert these to the required type; but
                //   there may be other values in the sequence that won't convert and still need to be checked

                if (suppliedItemType.equals(BuiltInAtomicType.ANY_ATOMIC)
                        && !(reqItemType.equals(BuiltInAtomicType.UNTYPED_ATOMIC) || reqItemType.equals(BuiltInAtomicType.ANY_ATOMIC))
                        && !exp.hasSpecialProperty(StaticProperty.NOT_UNTYPED_ATOMIC)) {

                    Expression conversion;
                    if (((PlainType) reqItemType).isNamespaceSensitive()) {
                        conversion = UntypedSequenceConverter.makeUntypedSequenceRejector(config, exp, (PlainType) reqItemType);
                    } else {
                        UntypedSequenceConverter usc = UntypedSequenceConverter.makeUntypedSequenceConverter(config, exp, (PlainType) reqItemType);
                        usc.setRoleDiagnostic(roleSupplier);
                        conversion = usc;
                    }
                    ExpressionTool.copyLocationInfo(exp, conversion);
                    try {
                        if (exp instanceof Literal) {
                            try {
                                exp = Literal.makeLiteral(
                                        SequenceTool.toGroundedValue(conversion.iterate(visitor.makeDynamicContext())), exp);
                                ExpressionTool.copyLocationInfo(supplied, exp);
                            } catch (UncheckedXPathException e) {
                                throw e.getXPathException();
                            }
                        } else {
                            exp = conversion;
                        }
                        suppliedItemType = exp.getItemType();
                    } catch (XPathException err) {
                        throw err.maybeWithLocation(exp.getLocation()).asStaticError();
                    }
                }
            }

            // New 4.0 rule - relabelling (or "downcasting")

            if (!itemTypeOK && reqItemType.getBasicAlphaCode().length() > 2 && visitor.getStaticContext().getXPathVersion() >= 40) {
                // allow down-conversion ("relabelling")
                if (reqItemType.getUType().overlaps(suppliedItemType.getUType())) {
                    itemTypeOK = true;
                    Expression cexp = makeDownCaster(exp, (AtomicType) reqItemType, config);
                    if (cexp instanceof AtomicSequenceConverter) {
                        ((AtomicSequenceConverter) cexp).setRoleDiagnostic(roleSupplier);
                    }
                    ExpressionTool.copyLocationInfo(exp, cexp);
                    exp = cexp;
                    try {
                        exp = exp.simplify().typeCheck(visitor, defaultContextInfo);
                    } catch (XPathException err) {
                        throw err.maybeWithLocation(exp.getLocation())
                                .asStaticError()
                                .withFailingExpression(supplied);
                    }
                    suppliedItemType = reqItemType;
                }

            }
        // Function coercion

        } else if (!itemTypeOK && reqItemType instanceof FunctionItemType && !((FunctionItemType) reqItemType).isMapType()
                && !((FunctionItemType) reqItemType).isArrayType()) {
            Affinity r = th.relationship(suppliedItemType, th.getGenericFunctionItemType());
            if (r != DISJOINT) {
                if (!(suppliedItemType instanceof FunctionItemType)) {
                    exp = new ItemChecker(exp, th.getGenericFunctionItemType(), roleSupplier);
                    suppliedItemType = th.getGenericFunctionItemType();
                }
                exp = makeFunctionSequenceCoercer(exp, (FunctionItemType) reqItemType, roleSupplier, allow40);
                itemTypeOK = true;
            }

        // External object conversion

        } else if (!itemTypeOK && reqItemType instanceof JavaExternalObjectType &&
                /*Sequence.class.isAssignableFrom(((JavaExternalObjectType) reqItemType).getJavaClass()) &&  */
                reqCard == StaticProperty.EXACTLY_ONE) {

            if (Sequence.class.isAssignableFrom(((JavaExternalObjectType) reqItemType).getJavaClass())) {
                // special case: allow an extension function to call an instance method on the implementation type of an XDM value
                // we leave the conversion to be sorted out at run-time
                itemTypeOK = true;
            } else if (supplied instanceof FunctionCall) {
                // adjust the required type of the Java extension function call
                // this does nothing unless supplied is an instanceof JavaExtensionFunctionCall
                if (((FunctionCall) supplied).adjustRequiredType((JavaExternalObjectType) reqItemType)) {
                    itemTypeOK = true;
                    cardOK = true;
                }

            }

        }

        // If both the cardinality and item type are statically OK, return now.
        if (itemTypeOK && cardOK) {
            return exp;
        }

        // If we haven't evaluated the cardinality of the supplied expression, do it now
        if (suppliedCard == -1) {
            suppliedCard = exp.getCardinality();
            if (!cardOK) {
                cardOK = Cardinality.subsumes(reqCard, suppliedCard);
            }
        }

        // If an empty sequence was explicitly supplied, and empty sequence is allowed,
        // then the item type doesn't matter
        if (cardOK && suppliedCard == StaticProperty.EMPTY) {
            return exp;
        }

        // If the supplied value is () and () isn't allowed, fail now
        if (suppliedCard == StaticProperty.EMPTY && ((reqCard & StaticProperty.ALLOWS_ZERO) == 0)) {
            RoleDiagnostic role = roleSupplier.get();
            throw new XPathException("An empty sequence is not allowed as the " + role.getMessage())
                    .withErrorCode(role.getErrorCode())
                    .withLocation(supplied.getLocation())
                    .asTypeErrorIf(role.isTypeError())
                    .withFailingExpression(supplied);
        }

        // Try a static type check. We only throw it out if the call cannot possibly succeed, unless
        // pessimistic type checking is enabled

        Affinity relation = itemTypeOK ? SUBSUMED_BY : th.relationship(suppliedItemType, reqItemType);

        if (reqCard == StaticProperty.ALLOWS_ZERO) {
            //  No point doing any item checking if no items are allowed in the result
            relation = SAME_TYPE;
        }
        if (relation == DISJOINT) {
            // The item types may be disjoint, but if both the supplied and required types permit
            // an empty sequence, we can't raise a static error. Raise a warning instead.
            RoleDiagnostic role = roleSupplier.get();
            if (Cardinality.allowsZero(suppliedCard) &&
                    Cardinality.allowsZero(reqCard)) {
                if (suppliedCard != StaticProperty.EMPTY) {
                    String msg = role.composeErrorMessage(reqItemType, supplied, th);
                    msg += ". The expression can succeed only if the supplied value is an empty sequence.";
                    visitor.issueWarning(msg, SaxonErrorCode.SXWN9026, supplied.getLocation());
                }
            } else {
                String msg = role.composeErrorMessage(reqItemType, supplied, th);
                throw new XPathException(msg)
                        .withErrorCode(role.getErrorCode())
                        .withLocation(supplied.getLocation())
                        .asTypeErrorIf(role.isTypeError())
                        .withFailingExpression(supplied);
            }
        }

        // Unless the type is guaranteed to match, add a dynamic type check,
        // unless the value is already known in which case we might as well report
        // the error now.

        if (!(relation == SAME_TYPE || relation == SUBSUMED_BY)) {
            if (exp instanceof Literal) {
                // Try a more detailed check, since for maps, functions etc getItemType() can be imprecise
                if (req.matches(((Literal) exp).getGroundedValue(), th)) {
                    return exp;
                }
                RoleDiagnostic role = roleSupplier.get();
                String msg = role.composeErrorMessage(reqItemType, supplied, th);
                throw new XPathException(msg)
                        .withErrorCode(role.getErrorCode())
                        .withLocation(supplied.getLocation())
                        .asTypeErrorIf(role.isTypeError())
                        .withFailingExpression(supplied);
            } else {
                Expression cexp = new ItemChecker(exp, reqItemType, roleSupplier);
                ExpressionTool.copyLocationInfo(exp, cexp);
                exp = cexp;
            }
        }

        if (!cardOK) {
            if (exp instanceof Literal) {
                RoleDiagnostic role = roleSupplier.get();
                throw new XPathException("Required cardinality of " + role.getMessage() +
                                                                " is " + Cardinality.describe(reqCard) +
                                                                "; supplied value has cardinality " +
                                                                Cardinality.describe(suppliedCard))
                        .withErrorCode(role.getErrorCode())
                        .withLocation(supplied.getLocation())
                        .withFailingExpression(supplied)
                        .asTypeErrorIf(role.isTypeError());
            } else {
                Expression cexp = CardinalityChecker.makeCardinalityChecker(exp, reqCard, roleSupplier);
                ExpressionTool.copyLocationInfo(exp, cexp);
                exp = cexp;
            }
        }

        return exp;
    }

    /**
     * Make an expression that performs type promotion on a supplied sequence
     *
     * @param suppliedItemType the inferred type of the supplied value
     * @param requiredType     the required type, the target of promotion
     * @param rules            the conversion rules
     * @param allow40          true if XPath 4.0 is enabled
     * @return the promoting converter, if available for the required type, or null.
     * Note that promoting converters not only implement
     * type promotion (for example from decimal to double) but also perform conversion of untypedAtomic values
     * to the target type.
     */
    public static Converter makePromotingConverter(ItemType suppliedItemType, int requiredType, ConversionRules rules, boolean allow40) {
        switch (requiredType) {
            case StandardNames.XS_DOUBLE:
                return new Converter.PromoterToDouble(rules);
            case StandardNames.XS_FLOAT:
                return new Converter.PromoterToFloat(rules);
            case StandardNames.XS_STRING:
                return new Converter.PromoterToString();
            case StandardNames.XS_ANY_URI:
                if (allow40) {
                    return new Converter.PromoterToAnyURI();
                }
                break;
            case StandardNames.XS_HEX_BINARY:
                if (allow40) {
                    return new Converter.PromoterToHexBinary();
                }
                break;
            case StandardNames.XS_BASE64_BINARY:
                if (allow40) {
                    return new Converter.PromoterToBase64Binary();
                }
                break;

        }
        return null;
    }

    public Expression makeArithmeticExpression(Expression lhs, int operator, Expression rhs) {
        return new ArithmeticExpression(lhs, operator, rhs);
    }

    public Expression makeGeneralComparison(Expression lhs, int operator, Expression rhs) {
        return new GeneralComparison20(lhs, operator, rhs);
    }

    public Expression processValueOf(Expression select, Configuration config) {
        return select;
    }

    private static Expression makeFunctionSequenceCoercer(
            Expression exp,
            FunctionItemType reqItemType,
            Supplier<RoleDiagnostic> role,
            boolean allow40) throws XPathException {
        // Apply function coercion as defined in XPath 3.0 or 4.0
        return reqItemType.makeFunctionSequenceCoercer(exp, role, allow40);
    }

    private Expression makeDownCaster(Expression exp, AtomicType reqItemType, Configuration config) {
        return AtomicSequenceConverter.makeDownCaster(exp, reqItemType, config);
    }

    /**
     * Check an expression against a required type, modifying it if necessary. This
     * is a variant of the method {@link #staticTypeCheck} used for expressions that
     * declare variables in XQuery. In these contexts, conversions such as numeric
     * type promotion and atomization are not allowed.
     *
     * @param supplied The expression to be type-checked
     * @param req      The required type for the context in which the expression is used
     * @param roleSupplier     Information about the role of the subexpression within the
     *                 containing expression, used to provide useful error messages
     * @param env      The static context containing the types being checked. At present
     *                 this is used only to locate a NamePool
     * @return The original expression if it is type-safe, or the expression
     * wrapped in a run-time type checking expression if not.
     * @throws XPathException if the supplied type is statically inconsistent with the
     *                        required type (that is, if they have no common subtype)
     */

    public static Expression strictTypeCheck(Expression supplied,
                                             SequenceType req,
                                             Supplier<RoleDiagnostic> roleSupplier,
                                             StaticContext env)
            throws XPathException {

        // System.err.println("Strict Type Check on expression (requiredType = " + req + "):"); supplied.display(10);

        Expression exp = supplied;
        final TypeHierarchy th = env.getConfiguration().getTypeHierarchy();

        ItemType reqItemType = req.getPrimaryType();
        int reqCard = req.getCardinality();

        ItemType suppliedItemType = null;
        // item type of the supplied expression: null means not yet calculated
        int suppliedCard = -1;
        // cardinality of the supplied expression: -1 means not yet calculated

        boolean cardOK = reqCard == StaticProperty.ALLOWS_ZERO_OR_MORE;
        // Unless the required cardinality is zero-or-more (no constraints).
        // check the static cardinality of the supplied expression
        if (!cardOK) {
            suppliedCard = exp.getCardinality();
            cardOK = Cardinality.subsumes(reqCard, suppliedCard);
        }

        boolean itemTypeOK = req.getPrimaryType() instanceof AnyItemType;
        // Unless the required item type and content type are ITEM (no constraints)
        // check the static item type against the supplied expression.
        // NOTE: we don't currently do any static inference regarding the content type
        if (!itemTypeOK) {
            suppliedItemType = exp.getItemType();
            Affinity affinity = th.relationship(reqItemType, suppliedItemType);
            itemTypeOK = affinity == SAME_TYPE || affinity == SUBSUMES;
        }

        // If both the cardinality and item type are statically OK, return now.
        if (itemTypeOK && cardOK) {
            return exp;
        }

        // If we haven't evaluated the cardinality of the supplied expression, do it now
        if (suppliedCard == -1) {
            if (suppliedItemType instanceof ErrorType) {
                suppliedCard = StaticProperty.EMPTY;
            } else {
                suppliedCard = exp.getCardinality();
            }
            if (!cardOK) {
                cardOK = Cardinality.subsumes(reqCard, suppliedCard);
            }
        }

        // If an empty sequence was explicitly supplied, and empty sequence is allowed,
        // then the item type doesn't matter
        if (cardOK && suppliedCard == StaticProperty.EMPTY) {
            return exp;
        }

        // If we haven't evaluated the item type of the supplied expression, do it now
        if (suppliedItemType == null) {
            suppliedItemType = exp.getItemType();
        }

        if (suppliedCard == StaticProperty.EMPTY && ((reqCard & StaticProperty.ALLOWS_ZERO) == 0)) {
            RoleDiagnostic role = roleSupplier.get();
            XPathException err = new XPathException(
                    "An empty sequence is not allowed as the " + role.getMessage(), role.getErrorCode(), supplied.getLocation());
            err.setIsTypeError(role.isTypeError());
            throw err;
        }

        // Try a static type check. We only throw it out if the call cannot possibly succeed.

        Affinity relation = th.relationship(suppliedItemType, reqItemType);
        if (relation == DISJOINT) {
            // The item types may be disjoint, but if both the supplied and required types permit
            // an empty sequence, we can't raise a static error. Raise a warning instead.
            if (Cardinality.allowsZero(suppliedCard) &&
                    Cardinality.allowsZero(reqCard)) {
                if (suppliedCard != StaticProperty.EMPTY) {
                    RoleDiagnostic role = roleSupplier.get();
                    String msg = "Required item type of " + role.getMessage() +
                            " is " + reqItemType +
                            "; supplied value (" + supplied.toShortString() + ") has item type " +
                            suppliedItemType +
                            ". The expression can succeed only if the supplied value is an empty sequence.";
                    env.issueWarning(msg, SaxonErrorCode.SXWN9026, supplied.getLocation());
                }
            } else {
                RoleDiagnostic role = roleSupplier.get();
                String msg = role.composeErrorMessage(reqItemType, supplied, th);
                XPathException err = new XPathException(msg, role.getErrorCode(), supplied.getLocation());
                err.setIsTypeError(role.isTypeError());
                throw err;
            }
        }

        // Unless the type is guaranteed to match, add a dynamic type check,
        // unless the value is already known in which case we might as well report
        // the error now.

        if (!(relation == SAME_TYPE || relation == SUBSUMED_BY)) {
            Expression cexp = new ItemChecker(exp, reqItemType, roleSupplier);
            cexp.adoptChildExpression(exp);
            exp = cexp;
        }

        if (!cardOK) {
            if (exp instanceof Literal) {
                RoleDiagnostic role = roleSupplier.get();
                XPathException err = new XPathException("Required cardinality of " + role.getMessage() +
                                                                " is " + Cardinality.describe(reqCard) +
                                                                "; supplied value has cardinality " +
                                                                Cardinality.describe(suppliedCard), role.getErrorCode(), supplied.getLocation());
                err.setIsTypeError(role.isTypeError());
                throw err;
            } else {
                Expression cexp = CardinalityChecker.makeCardinalityChecker(exp, reqCard, roleSupplier);
                cexp.adoptChildExpression(exp);
                exp = cexp;
            }
        }

        return exp;
    }

    /**
     * Test whether a given value conforms to a given type
     *
     * @param val          the value
     * @param requiredType the required type
     * @param context      XPath dynamic context
     * @return an XPathException describing the error condition if the value doesn't conform;
     * or null if it does.
     * @throws XPathException if a failure occurs reading the value
     */

    /*@Nullable*/
    public static XPathException testConformance(
            Sequence val, SequenceType requiredType, XPathContext context) throws XPathException {
        ItemType reqItemType = requiredType.getPrimaryType();
        SequenceIterator iter = val.iterate();
        int count = 0;
        for (Item item; (item = iter.next()) != null; ) {
            count++;
            if (!reqItemType.matches(item, context.getConfiguration().getTypeHierarchy())) {
                return new XPathException("Required type is " + reqItemType +
                                                                "; supplied value has type " + UType.getUType(val.materialize()))
                        .asTypeError().withErrorCode("XPTY0004");
            }
        }

        int reqCardinality = requiredType.getCardinality();
        if (count == 0 && !Cardinality.allowsZero(reqCardinality)) {
            return new XPathException(
                    "Required type does not allow empty sequence, but supplied value is empty")
                    .asTypeError().withErrorCode("XPTY0004");
        }
        if (count > 1 && !Cardinality.allowsMany(reqCardinality)) {
            return new XPathException(
                    "Required type requires a singleton sequence; supplied value contains " + count + " items")
                    .asTypeError().withErrorCode("XPTY0004");
        }
        if (count > 0 && reqCardinality == StaticProperty.EMPTY) {
            return new XPathException(
                    "Required type requires an empty sequence, but supplied value is non-empty")
                    .asTypeError()
                    .withErrorCode("XPTY0004");
        }
        return null;
    }

    /**
     * Test whether a given expression is capable of returning a value that has an effective boolean
     * value.
     *
     * @param exp the given expression
     * @param th  the type hierarchy cache
     * @return null if the expression is OK (optimistically), an exception object if not
     */

    public static XPathException ebvError(Expression exp, TypeHierarchy th) {
        if (Cardinality.allowsZero(exp.getCardinality())) {
            return null;
        }
        ItemType t = exp.getItemType();
        if (th.relationship(t, Type.NODE_TYPE) == DISJOINT &&
                th.relationship(t, BuiltInAtomicType.BOOLEAN) == DISJOINT &&
                th.relationship(t, BuiltInAtomicType.STRING) == DISJOINT &&
                th.relationship(t, BuiltInAtomicType.ANY_URI) == DISJOINT &&
                th.relationship(t, BuiltInAtomicType.UNTYPED_ATOMIC) == DISJOINT &&
                th.relationship(t, NumericType.getInstance()) == DISJOINT &&
                !(t instanceof JavaExternalObjectType)) {
            return new XPathException(
                    "Effective boolean value is defined only for sequences containing " +
                            "booleans, strings, numbers, URIs, or nodes")
                    .withErrorCode("FORG0006").asTypeError();
        }
        return null;
    }

    private static Expression makePromoter(Expression exp, Converter converter, BuiltInAtomicType type) {
        ConversionRules rules = exp.getConfiguration().getConversionRules();
        converter.setConversionRules(rules);
        if (exp instanceof Literal && ((Literal) exp).getGroundedValue() instanceof AtomicValue) {
            ConversionResult result = converter.convert((AtomicValue) ((Literal) exp).getGroundedValue());
            if (result instanceof AtomicValue) {
                Literal converted = Literal.makeLiteral((AtomicValue) result, exp);
                ExpressionTool.copyLocationInfo(exp, converted);
                return converted;
            }
        }
        AtomicSequenceConverter asc = new AtomicSequenceConverter(exp, type);
        asc.setConverter(converter);
        ExpressionTool.copyLocationInfo(exp, asc);
        return asc;
    }

    private UType promotableTypes(int targetType, boolean allow40) {
        if (allow40) {
            switch (targetType) {
                case StandardNames.XS_DOUBLE:
                    return UType.UNTYPED_ATOMIC.union(UType.DECIMAL).union(UType.FLOAT).union(UType.DOUBLE);
                case StandardNames.XS_FLOAT:
                    return UType.UNTYPED_ATOMIC.union(UType.DECIMAL).union(UType.FLOAT);
                case StandardNames.XS_ANY_URI:
                case StandardNames.XS_STRING:
                    return UType.UNTYPED_ATOMIC.union(UType.ANY_URI).union(UType.STRING);
                case StandardNames.XS_HEX_BINARY:
                case StandardNames.XS_BASE64_BINARY:
                    return UType.UNTYPED_ATOMIC.union(UType.HEX_BINARY).union(UType.BASE64_BINARY);
                default:
                    return UType.UNTYPED_ATOMIC.union(UType.fromTypeCode(targetType));
            }
        } else {
            switch (targetType) {
                case StandardNames.XS_DOUBLE:
                    return UType.UNTYPED_ATOMIC.union(UType.DECIMAL).union(UType.FLOAT).union(UType.DOUBLE);
                case StandardNames.XS_FLOAT:
                    return UType.UNTYPED_ATOMIC.union(UType.DECIMAL).union(UType.FLOAT);
                case StandardNames.XS_STRING:
                    return UType.UNTYPED_ATOMIC.union(UType.STRING).union(UType.ANY_URI);
                default:
                    return UType.UNTYPED_ATOMIC.union(UType.fromTypeCode(targetType));
            }
        }
    }

}
