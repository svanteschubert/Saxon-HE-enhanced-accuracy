////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.compat;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.AtomicSequenceConverter;
import net.sf.saxon.expr.Atomizer;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.FirstItemExpression;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.NumericType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.SequenceType;

/**
 * This class provides type checking capability with XPath 1.0 backwards compatibility enabled.
 */

public class TypeChecker10 extends TypeChecker {

    public TypeChecker10() {
    }

    @Override
    public Expression staticTypeCheck(Expression supplied,
                                      SequenceType req,
                                      RoleDiagnostic role,
                                      final ExpressionVisitor visitor) throws XPathException {

        if (supplied.implementsStaticTypeCheck()) {
            return supplied.staticTypeCheck(req, true, role, visitor);
        }

//        In a static function call, if XPath 1.0 compatibility mode is true and an argument of a static function is
//        not of the expected type, then the following conversions are applied sequentially to the argument value V:
//        (1) If the expected type calls for a single item or optional single item(examples:xs:
//        string, xs:string ?, xs:untypedAtomic, xs:untypedAtomic ?, node(), node() ?, item(), item() ?),then the value V
//        is effectively replaced by V[1].
//        (2) If the expected type is xs:string or xs:string?, then the value V is effectively replaced by fn:string(V).
//        (3) If the expected type is xs:double or xs:double?,then the value V is effectively replaced by fn:number(V).
//        We interpret this as including xs:numeric so that the intended effect is achieved with functions such as fn:floor().

        final Configuration config = visitor.getConfiguration();
        final TypeHierarchy th = config.getTypeHierarchy();

        // rule 1
        if (!Cardinality.allowsMany(req.getCardinality()) && Cardinality.allowsMany(supplied.getCardinality())) {
            Expression cexp = FirstItemExpression.makeFirstItemExpression(supplied);
            cexp.adoptChildExpression(supplied);
            supplied = cexp;
        }

        // rule 2
        ItemType reqItemType = req.getPrimaryType();
        if (req.getPrimaryType().equals(BuiltInAtomicType.STRING) &&
                !Cardinality.allowsMany(req.getCardinality()) &&
                !th.isSubType(supplied.getItemType(), BuiltInAtomicType.STRING)) {
            final RetainedStaticContext rsc = new RetainedStaticContext(config);
            Expression fn = SystemFunction.makeCall("string", rsc, supplied);
            try {
                return fn.typeCheck(visitor, config.getDefaultContextItemStaticInfo());
            } catch (XPathException err) {
                err.maybeSetLocation(supplied.getLocation());
                err.setIsStaticError(true);
                throw err;
            }
        }

        // rule 3
        if (reqItemType.equals(NumericType.getInstance()) || reqItemType.equals(BuiltInAtomicType.DOUBLE) &&
                !Cardinality.allowsMany(req.getCardinality()) &&
                !th.isSubType(supplied.getItemType(), BuiltInAtomicType.DOUBLE)) {
            final RetainedStaticContext rsc = new RetainedStaticContext(config);
            Expression fn = SystemFunction.makeCall("number", rsc, supplied);
            try {
                return fn.typeCheck(visitor, config.getDefaultContextItemStaticInfo());
            } catch (XPathException err) {
                err.maybeSetLocation(supplied.getLocation());
                err.setIsStaticError(true);
                throw err;
            }
        }
        return super.staticTypeCheck(supplied, req, role, visitor);

    }

    @Override
    public Expression makeArithmeticExpression(Expression lhs, int operator, Expression rhs) {
        return new ArithmeticExpression10(lhs, operator, rhs);
    }

    @Override
    public Expression makeGeneralComparison(Expression lhs, int operator, Expression rhs) {
        return new GeneralComparison10(lhs, operator, rhs);
    }

    @Override
    public Expression processValueOf(Expression select, Configuration config) {
        TypeHierarchy th = config.getTypeHierarchy();
        if (!select.getItemType().isPlainType()) {
            select = Atomizer.makeAtomizer(select, null);
        }
        if (Cardinality.allowsMany(select.getCardinality())) {
            select = FirstItemExpression.makeFirstItemExpression(select);
        }
        if (!th.isSubType(select.getItemType(), BuiltInAtomicType.STRING)) {
            select = new AtomicSequenceConverter(select, BuiltInAtomicType.STRING);
            ((AtomicSequenceConverter) select).allocateConverterStatically(config, false);
        }
        return select;
    }


}
