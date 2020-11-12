////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.parser;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.ma.map.TupleType;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;
import static net.sf.saxon.type.Affinity.*;

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
     * @param role                Information about the role of the subexpression within the
     *                            containing expression, used to provide useful error messages
     * @param visitor             An expression visitor
     * @return The original expression if it is type-safe, or the expression
     * wrapped in a run-time type checking expression if not.
     * @throws XPathException if the supplied type is statically inconsistent with the
     *                        required type (that is, if they have no common subtype)
     */

    public Expression staticTypeCheck(Expression supplied,
                                      SequenceType req,
                                      RoleDiagnostic role,
                                      final ExpressionVisitor visitor)
            throws XPathException {

        // System.err.println("Static Type Check on expression (requiredType = " + req + "):"); supplied.display(10);

        if (supplied.implementsStaticTypeCheck()) {
            return supplied.staticTypeCheck(req, false, role, visitor);
        }

        Expression exp = supplied;
        final StaticContext env = visitor.getStaticContext();
        final Configuration config = env.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();
        final ContextItemStaticInfo defaultContextInfo = config.getDefaultContextItemStaticInfo();

        final ItemType reqItemType = req.getPrimaryType();
        int reqCard = req.getCardinality();
        boolean allowsMany = Cardinality.allowsMany(reqCard);

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
            Affinity relation = th.relationship(reqItemType, suppliedItemType);
            itemTypeOK = relation == Affinity.SAME_TYPE || relation == Affinity.SUBSUMES;
        }


        if (!itemTypeOK) {
            // Now apply the conversions needed in 2.0 mode

            if (reqItemType.isPlainType()) {

                // rule 1: Atomize
                if (!suppliedItemType.isPlainType() &&
                        !(suppliedCard == StaticProperty.EMPTY)) {
                    if (!suppliedItemType.isAtomizable(th)) {
                        String shortItemType;
                        if (suppliedItemType instanceof TupleType) {
                            shortItemType = "a tuple type";
                        } else if (suppliedItemType instanceof MapType) {
                            shortItemType = "a map type";
                        } else if (suppliedItemType instanceof FunctionItemType) {
                            shortItemType = "a function type";
                        } else if (suppliedItemType instanceof NodeTest) {
                            shortItemType = "an element type with element-only content";
                        } else {
                            shortItemType = suppliedItemType.toString();
                        }
                        XPathException err = new XPathException(
                                "An atomic value is required for the " + role.getMessage() +
                                        ", but the supplied type is " + shortItemType + ", which cannot be atomized", "FOTY0013", supplied.getLocation());
                        err.setIsTypeError(true);
                        err.setFailingExpression(supplied);
                        throw err;
                    }

                    if (exp.getRetainedStaticContext() == null) {
                        exp.setRetainedStaticContextLocally(env.makeRetainedStaticContext());
                    }
                    Expression cexp = Atomizer.makeAtomizer(exp, role);
                    ExpressionTool.copyLocationInfo(exp, cexp);
                    exp = cexp;
                    cexp = exp.simplify();
                    ExpressionTool.copyLocationInfo(exp, cexp);
                    exp = cexp;
                    suppliedItemType = exp.getItemType();
                    suppliedCard = exp.getCardinality();
                    cardOK = Cardinality.subsumes(reqCard, suppliedCard);
                }

                // rule 2: convert untypedAtomic to the required type

                //   2a: all supplied values are untyped atomic. Convert if necessary, and we're finished.

                if (suppliedItemType.equals(BuiltInAtomicType.UNTYPED_ATOMIC)
                        && !(reqItemType.equals(BuiltInAtomicType.UNTYPED_ATOMIC) || reqItemType.equals(BuiltInAtomicType.ANY_ATOMIC))) {

                    if (((PlainType) reqItemType).isNamespaceSensitive()) {
                        // See spec bug 11964
                        XPathException err = new XPathException("An untyped atomic value cannot be converted to a QName or NOTATION as required for the " + role.getMessage(), "XPTY0117", supplied.getLocation());
                        err.setIsTypeError(true);
                        throw err;
                    }
                    UntypedSequenceConverter cexp = UntypedSequenceConverter.makeUntypedSequenceConverter(config, exp, (PlainType) reqItemType);
                    cexp.setRoleDiagnostic(role);
                    ExpressionTool.copyLocationInfo(exp, cexp);
                    try {
                        if (exp instanceof Literal) {
                            exp = Literal.makeLiteral(
                                    cexp.iterate(visitor.makeDynamicContext()).materialize(), exp);
                            ExpressionTool.copyLocationInfo(cexp, exp);
                        } else {
                            exp = cexp;
                        }
                    } catch (XPathException err) {
                        err.maybeSetLocation(exp.getLocation());
                        err.setFailingExpression(supplied);
                        err.setErrorCode(role.getErrorCode());
                        err.setIsStaticError(true);
                        throw err;
                    }
                    itemTypeOK = true;
                    suppliedItemType = reqItemType;
                }

                //   2b: some supplied values are untyped atomic. Convert these to the required type; but
                //   there may be other values in the sequence that won't convert and still need to be checked

                if (suppliedItemType.equals(BuiltInAtomicType.ANY_ATOMIC)
                        && !(reqItemType.equals(BuiltInAtomicType.UNTYPED_ATOMIC) || reqItemType.equals(BuiltInAtomicType.ANY_ATOMIC))
                        && !exp.hasSpecialProperty(StaticProperty.NOT_UNTYPED_ATOMIC)) {

                    Expression conversion;
                    if (((PlainType) reqItemType).isNamespaceSensitive()) {
                        conversion = UntypedSequenceConverter.makeUntypedSequenceRejector(config, exp, (PlainType) reqItemType);
                    } else {
                        UntypedSequenceConverter usc = UntypedSequenceConverter.makeUntypedSequenceConverter(config, exp, (PlainType) reqItemType);
                        usc.setRoleDiagnostic(role);
                        conversion = usc;
                    }
                    ExpressionTool.copyLocationInfo(exp, conversion);
                    try {
                        if (exp instanceof Literal) {
                            exp = Literal.makeLiteral(
                                    conversion.iterate(visitor.makeDynamicContext()).materialize(), exp);
                            ExpressionTool.copyLocationInfo(supplied, exp);
                        } else {
                            exp = conversion;
                        }
                        suppliedItemType = exp.getItemType();
                    } catch (XPathException err) {
                        err.maybeSetLocation(exp.getLocation());
                        err.setIsStaticError(true);
                        throw err;
                    }
                }

                // Rule 3a: numeric promotion decimal -> float -> double

                if (reqItemType instanceof AtomicType) {
                    int rt = ((AtomicType) reqItemType).getFingerprint();
                    if (rt == StandardNames.XS_DOUBLE &&
                            th.relationship(suppliedItemType, NumericType.getInstance()) != DISJOINT) {
                        Expression cexp = makePromoterToDouble(exp);
                        if (cexp instanceof AtomicSequenceConverter) {
                            ((AtomicSequenceConverter) cexp).setRoleDiagnostic(role);
                        }
                        ExpressionTool.copyLocationInfo(exp, cexp);
                        exp = cexp;
                        try {
                            exp = exp.simplify().typeCheck(visitor, defaultContextInfo);
                        } catch (XPathException err) {
                            err.maybeSetLocation(exp.getLocation());
                            err.setIsStaticError(true);
                            throw err;
                        }
                        suppliedItemType = BuiltInAtomicType.DOUBLE;
                        suppliedCard = -1;

                    } else if (rt == StandardNames.XS_FLOAT &&
                            th.relationship(suppliedItemType, NumericType.getInstance()) != DISJOINT &&
                            !th.isSubType(suppliedItemType, BuiltInAtomicType.DOUBLE)) {
                        Expression cexp = makePromoterToFloat(exp);
                        if (cexp instanceof AtomicSequenceConverter) {
                            ((AtomicSequenceConverter)cexp).setRoleDiagnostic(role);
                        }
                        ExpressionTool.copyLocationInfo(exp, cexp);
                        exp = cexp;
                        try {
                            exp = exp.simplify().typeCheck(visitor, defaultContextInfo);
                        } catch (XPathException err) {
                            err.maybeSetLocation(exp.getLocation());
                            err.setFailingExpression(supplied);
                            err.setIsStaticError(true);
                            throw err;
                        }
                        suppliedItemType = BuiltInAtomicType.FLOAT;
                        suppliedCard = -1;

                    }

                    // Rule 3b: promotion from anyURI -> string

                    if (rt == StandardNames.XS_STRING && th.isSubType(suppliedItemType, BuiltInAtomicType.ANY_URI)) {
                        itemTypeOK = true;
                        Expression cexp = makePromoterToString(exp);
                        if (cexp instanceof AtomicSequenceConverter) {
                            ((AtomicSequenceConverter) cexp).setRoleDiagnostic(role);
                        }
                        ExpressionTool.copyLocationInfo(exp, cexp);
                        exp = cexp;
                        try {
                            exp = exp.simplify().typeCheck(visitor, defaultContextInfo);
                        } catch (XPathException err) {
                            err.maybeSetLocation(exp.getLocation());
                            err.setFailingExpression(supplied);
                            err.setIsStaticError(true);
                            throw err;
                        }
                        suppliedItemType = BuiltInAtomicType.STRING;
                        suppliedCard = -1;
                    }
                }

            } else if (reqItemType instanceof FunctionItemType && !((FunctionItemType) reqItemType).isMapType()
                    && !((FunctionItemType) reqItemType).isArrayType()) {
                Affinity r = th.relationship(suppliedItemType, th.getGenericFunctionItemType());
                if (r != DISJOINT) {
                    if (!(suppliedItemType instanceof FunctionItemType)) {
                        exp = new ItemChecker(exp, th.getGenericFunctionItemType(), role);
                        suppliedItemType = th.getGenericFunctionItemType();
                    }
                    exp = makeFunctionSequenceCoercer(exp, (FunctionItemType) reqItemType, visitor, role);
                    itemTypeOK = true;
                }

            } else if (reqItemType instanceof JavaExternalObjectType &&
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
            XPathException err = new XPathException("An empty sequence is not allowed as the " + role.getMessage(), role.getErrorCode(), supplied.getLocation());
            err.setIsTypeError(role.isTypeError());
            err.setFailingExpression(supplied);
            throw err;
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
            if (Cardinality.allowsZero(suppliedCard) &&
                    Cardinality.allowsZero(reqCard)) {
                if (suppliedCard != StaticProperty.EMPTY) {
                    String msg = role.composeErrorMessage(reqItemType, supplied, th);
                    msg += ". The expression can succeed only if the supplied value is an empty sequence.";
                    visitor.issueWarning(msg, supplied.getLocation());
                }
            } else {
                String msg = role.composeErrorMessage(reqItemType, supplied, th);
                XPathException err = new XPathException(msg, role.getErrorCode(), supplied.getLocation());
                err.setIsTypeError(role.isTypeError());
                err.setFailingExpression(supplied);
                throw err;
            }
        }

        // Unless the type is guaranteed to match, add a dynamic type check,
        // unless the value is already known in which case we might as well report
        // the error now.

        if (!(relation == SAME_TYPE || relation == SUBSUMED_BY)) {
            if (exp instanceof Literal) {
                // Try a more detailed check, since for maps, functions etc getItemType() can be imprecise
                if (req.matches(((Literal) exp).getValue(), th)) {
                    return exp;
                }
                String msg = role.composeErrorMessage(reqItemType, supplied, th);
                XPathException err = new XPathException(msg, role.getErrorCode(), supplied.getLocation());
                err.setIsTypeError(role.isTypeError());
                throw err;
            } else {
                Expression cexp = new ItemChecker(exp, reqItemType, role);
                ExpressionTool.copyLocationInfo(exp, cexp);
                exp = cexp;
            }
        }

        if (!cardOK) {
            if (exp instanceof Literal) {
                XPathException err = new XPathException("Required cardinality of " + role.getMessage() +
                                                                " is " + Cardinality.toString(reqCard) +
                                                                "; supplied value has cardinality " +
                                                                Cardinality.toString(suppliedCard), role.getErrorCode(), supplied.getLocation());
                err.setIsTypeError(role.isTypeError());
                throw err;
            } else {
                Expression cexp = CardinalityChecker.makeCardinalityChecker(exp, reqCard, role);
                ExpressionTool.copyLocationInfo(exp, cexp);
                exp = cexp;
            }
        }

        return exp;
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
            Expression exp, FunctionItemType reqItemType, ExpressionVisitor visitor, RoleDiagnostic role) throws XPathException {
        // Apply function coercion as defined in XPath 3.0.

        return reqItemType.makeFunctionSequenceCoercer(exp, role);
    }

    /**
     * Check an expression against a required type, modifying it if necessary. This
     * is a variant of the method {@link #staticTypeCheck} used for expressions that
     * declare variables in XQuery. In these contexts, conversions such as numeric
     * type promotion and atomization are not allowed.
     *
     * @param supplied The expression to be type-checked
     * @param req      The required type for the context in which the expression is used
     * @param role     Information about the role of the subexpression within the
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
                                             RoleDiagnostic role,
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
            Affinity relation = th.relationship(reqItemType, suppliedItemType);
            itemTypeOK = relation == SAME_TYPE || relation == SUBSUMES;
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
            XPathException err = new XPathException("An empty sequence is not allowed as the " + role.getMessage(), role.getErrorCode(), supplied.getLocation());
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
                    String msg = "Required item type of " + role.getMessage() +
                            " is " + reqItemType +
                            "; supplied value (" + supplied.toShortString() + ") has item type " +
                            suppliedItemType +
                            ". The expression can succeed only if the supplied value is an empty sequence.";
                    env.issueWarning(msg, supplied.getLocation());
                }
            } else {
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
            Expression cexp = new ItemChecker(exp, reqItemType, role);
            cexp.adoptChildExpression(exp);
            exp = cexp;
        }

        if (!cardOK) {
            if (exp instanceof Literal) {
                XPathException err = new XPathException("Required cardinality of " + role.getMessage() +
                                                                " is " + Cardinality.toString(reqCard) +
                                                                "; supplied value has cardinality " +
                                                                Cardinality.toString(suppliedCard), role.getErrorCode(), supplied.getLocation());
                err.setIsTypeError(role.isTypeError());
                throw err;
            } else {
                Expression cexp = CardinalityChecker.makeCardinalityChecker(exp, reqCard, role);
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
            Sequence val, SequenceType requiredType, XPathContext context)
            throws XPathException {
        ItemType reqItemType = requiredType.getPrimaryType();
        SequenceIterator iter = val.iterate();
        int count = 0;
        Item item;
        while ((item = iter.next()) != null) {
            count++;
            if (!reqItemType.matches(item, context.getConfiguration().getTypeHierarchy())) {
                XPathException err = new XPathException("Required type is " + reqItemType +
                                                                "; supplied value has type " + UType.getUType(val.materialize()));
                err.setIsTypeError(true);
                err.setErrorCode("XPTY0004");
                return err;
            }
        }

        int reqCardinality = requiredType.getCardinality();
        if (count == 0 && !Cardinality.allowsZero(reqCardinality)) {
            XPathException err = new XPathException(
                    "Required type does not allow empty sequence, but supplied value is empty");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            return err;
        }
        if (count > 1 && !Cardinality.allowsMany(reqCardinality)) {
            XPathException err = new XPathException(
                    "Required type requires a singleton sequence; supplied value contains " + count + " items");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            return err;
        }
        if (count > 0 && reqCardinality == StaticProperty.EMPTY) {
            XPathException err = new XPathException(
                    "Required type requires an empty sequence, but supplied value is non-empty");
            err.setIsTypeError(true);
            err.setErrorCode("XPTY0004");
            return err;
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
            XPathException err = new XPathException(
                    "Effective boolean value is defined only for sequences containing " +
                            "booleans, strings, numbers, URIs, or nodes");
            err.setErrorCode("FORG0006");
            err.setIsTypeError(true);
            return err;
        }
        return null;
    }

    private static Expression makePromoterToDouble(Expression exp) {
        return makePromoter(exp, new Converter.PromoterToDouble(), BuiltInAtomicType.DOUBLE);
    }

    private static Expression makePromoterToFloat(Expression exp) {
        return makePromoter(exp, new Converter.PromoterToFloat(), BuiltInAtomicType.FLOAT);
    }

    private static Expression makePromoterToString(Expression exp) {
        return makePromoter(exp, new Converter.ToStringConverter(), BuiltInAtomicType.STRING);
    }

    private static Expression makePromoter(Expression exp, Converter converter, BuiltInAtomicType type) {
        ConversionRules rules = exp.getConfiguration().getConversionRules();
        converter.setConversionRules(rules);
        if (exp instanceof Literal && ((Literal) exp).getValue() instanceof AtomicValue) {
            ConversionResult result = converter.convert((AtomicValue) ((Literal) exp).getValue());
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

}
