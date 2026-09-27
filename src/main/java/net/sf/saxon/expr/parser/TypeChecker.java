////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.type.coercion.CoercionPlan;
import net.sf.saxon.type.coercion.SequenceCoercer;
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
 *
 * <p>This class has been redesigned in 13.0. Coercion of static calls now reuses
 * much of the same code as coercion for dynamic calls. The design now allocates
 * a {@link CoercionPlan} based on the required type; the {@code CoercionPlan}
 * is polymorphic and contains the actual coercion logic.</p>
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

        // Rewritten in 13.0 to use common code with dynamic coercions, as needed for dynamic function calls.

        // Some expressions have custom rules for type checking. For example if the expression (a, b) is used
        // in a context where an atomic value is required, then a and b will be independently atomized and the
        // results concatenated.

        if (supplied.implementsStaticTypeCheck()) {
            return supplied.staticTypeCheck(req, false, roleSupplier, visitor);
        }

        // If the static type of the expression already satisfies the required type, then in general
        // no coercion is required. The exception is with functions, where function coercion is always done,
        // to ensure that the parameters of a function call are checked against the required types even
        // if the actual function accepts a supertype.

        ItemType reqItemType = req.getPrimaryType();
        int reqCardinality = req.getCardinality();
        TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();

        // Function coercion is necessary even if the supplied function conforms to the required type;
        // but it's not needed if the supplied expression is already a function coercer.
        boolean forceCoercion = (reqItemType instanceof SpecificFunctionType && !(supplied instanceof SequenceCoercer));

        if (!forceCoercion) {
            // Unless coercion is forced, if the supplied type is a subtype of the required type, no action is needed
            if (supplied instanceof Literal && ((Literal)supplied).isInstance(req, th)) {
                return supplied;
            }
            if (th.isSubType(supplied.getItemType(), reqItemType)) {
                // The item type is OK
                if (Cardinality.subsumes(reqCardinality, supplied.getCardinality())) {
                    // The cardinality is OK
                    return supplied;
                } else {
                    // return a CardinalityChecker
                    return CardinalityChecker.makeCardinalityChecker(supplied, reqCardinality, roleSupplier);
                }
            }
        }

        // Produce a static error if coercion cannot succeed or (in 4.0) is implausible

        if (reqItemType instanceof JavaExternalObjectType) {

            if (Sequence.class.isAssignableFrom(((JavaExternalObjectType) reqItemType).getJavaClass())) {
                // special case: allow an extension function to call an instance method on the implementation class of an XDM value.
                // We leave the conversion to be sorted out at run-time
                return supplied;
            } else if (supplied instanceof FunctionCall) {
                // adjust the required type of the Java extension function call
                // this does nothing unless supplied is an instanceof JavaExtensionFunctionCall
                if (((FunctionCall) supplied).adjustRequiredType((JavaExternalObjectType) reqItemType)) {
                    return supplied;
                }
            }
        }

        final int version = visitor.getStaticContext().getPackageData().getHostLanguageVersion();
        if (reqCardinality != StaticProperty.ALLOWS_ZERO && supplied.getCardinality() != StaticProperty.ALLOWS_ZERO
                && !isCoercible(supplied.getItemType(), reqItemType)) {
            boolean implausibility = false;
            String message = roleSupplier.get().composeErrorMessage(reqItemType, supplied, th);
            if (Cardinality.allowsZero(reqCardinality) && Cardinality.allowsZero(supplied.getCardinality())) {
                message += ". The only value that could succeed is therefore the empty sequence";
                implausibility = true;
            }
            if (implausibility &&
                    (version < 40
                             || visitor.getStaticContext().getConfiguration().getBooleanProperty(Feature.ALLOW_IMPLAUSIBLE_EXPRESSIONS))) {
                visitor.issueWarning(message, "XPTY0004", supplied.getLocation());
            } else {
                if (implausibility) {
                    message += ". Set Feature.ALLOW_IMPLAUSIBLE_EXPRESSIONS to suppress this 4.0 error";
                }

                String errorCode = roleSupplier.get().getErrorCode();
                ItemType suppliedItemType = supplied.getItemType();
                if (suppliedItemType instanceof FunctionItemType && !(suppliedItemType instanceof ArrayItemType) && reqItemType.isAtomicType()) {
                    errorCode = "FOTY0013";
                }
                throw new XPathException(message, errorCode)
                        .asTypeError()
                        .withLocation(supplied.getLocation());
            }
        }

        // Create a coercer expression wrapping the supplied expression

        Expression result = SequenceCoercer.makeSequenceCoercer(supplied, req, roleSupplier, version >= 40);

        // If the supplied expression was a literal, do the coercion right now

        if (supplied instanceof Literal) {
            SequenceIterator eval = result.iterate(new EarlyEvaluationContext(visitor.getConfiguration()));
            return Literal.makeLiteral(SequenceTool.toGroundedValue(eval), supplied);
        }

        return result;
    }

    private boolean isCoercible(ItemType supplied, ItemType required) {
        UType uSupplied = supplied.getUType();
        UType uRequired = required.getUType();
        if (uSupplied.overlaps(uRequired)) {
            return true;
        }
        if (uSupplied.overlaps(UType.JNODE)) {
            return true;
        }
//        if ((supplied instanceof ArrayItemType || supplied instanceof MapType) &&
//                required instanceof GNodeType) {
//            return true;
//        }
        if (uRequired.overlaps(UType.ANY_ATOMIC)) {
            return switch (supplied.getGenre()) {
                case XNODE -> true;
                case JNODE -> true;
                case ARRAY -> true;
                case ATOMIC -> possiblePromotions(uSupplied).overlaps(uRequired);
                case EXTERNAL -> true;
                case ANY -> true;
                default -> false;
            };
        }
        return false;
    }

    private UType possiblePromotions(UType input) {
        if (input.equals(UType.UNTYPED_ATOMIC)) {
            return UType.ANY_ATOMIC;
        } else if (input.equals(UType.STRING)) {
            return UType.ANY_URI;
        } else if (input.equals(UType.ANY_URI)) {
            return UType.STRING;
        } else if (input.overlaps(UType.NUMERIC)) {
            return UType.NUMERIC;
        } else if (input.overlaps(UType.BINARY)) {
            return UType.BINARY;
        } else {
            return input;
        }
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

    public Expression makeArithmeticExpression(Expression lhs, OperatorSymbol operator, Expression rhs) {
        return new ArithmeticExpression(lhs, operator, rhs);
    }

    public Expression makeGeneralComparison(Expression lhs, OperatorSymbol operator, Expression rhs) {
        return new GeneralComparison20(lhs, operator, rhs);
    }

    public Expression processValueOf(Expression select, Configuration config) {
        return select;
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
            if (!reqItemType.matches(item)) {
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
     * @param visitor  the expression visitor
     * @throws XPathException if the expression cannot deliver an effective boolean value
     */

    public static void ebvTypeCheck(Expression exp, ExpressionVisitor visitor) throws XPathException {
        if (Cardinality.allowsZero(exp.getCardinality())) {
            return;
        }
        UType t = exp.getItemType().getUType();
        if (!t.overlaps(EBV_ITEM_TYPES)) {
            String message = "Effective boolean value is defined only for sequences containing " +
                    "booleans, strings, numbers, URIs, or nodes. The supplied expression `" + exp.toShortString() +
                    " delivers items of type " + exp.getItemType();
            if (Cardinality.allowsZero(exp.getCardinality())) {
                // Make this a warning because it can succeed on some paths
                visitor.issueWarning(message + ". The expression will fail when evaluated, except in the case where it returns an empty sequence",
                                     "FORG0006", exp.getLocation());
            } else {
                throw new XPathException(message)
                        .withErrorCode("FORG0006")
                        .asTypeError()
                        .withLocation(exp.getLocation());
            }
        }
    }

    private final static UType EBV_ITEM_TYPES = UType.BOOLEAN
            .union(UType.STRING)
            .union(UType.ANY_URI)
            .union(UType.UNTYPED_ATOMIC)
            .union(UType.NUMERIC)
            .union(UType.XNODE)
            .union(UType.JNODE)
            .union(UType.EXTENSION);

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
