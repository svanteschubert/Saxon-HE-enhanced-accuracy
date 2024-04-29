////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.oper.OperandArray;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.UniStringConsumer;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpModifiers;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.Arrays;

/**
 * Implementation of the fn:concat() function as defined in XPath 2.0, 3.0, and 3.1
 */


public class Concat31 extends SystemFunction implements PushableFunction {

    @Override
    protected Sequence resultIfEmpty(int arg) {
        return null;
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */
    @Override
    public FunctionItemType getFunctionItemType() {
        SequenceType[] argTypes = new SequenceType[getArity()];
        Arrays.fill(argTypes, SequenceType.OPTIONAL_ATOMIC);
        return new SpecificFunctionType(argTypes, SequenceType.SINGLE_STRING);
    }

    /**
     * Allow the function to create an optimized call based on the values of the actual arguments
     *
     * @param visitor     the expression visitor
     * @param contextInfo information about the context item
     * @param arguments   the supplied arguments to the function call. Note: modifying the contents
     *                    of this array should not be attempted, it is likely to have no effect.
     * @return either a function call on this function, or an expression that delivers
     * the same result, or null indicating that no optimization has taken place
     * @throws XPathException if an error is detected
     */
    @Override
    public Expression makeOptimizedFunctionCall(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo, final Expression... arguments) throws XPathException {
        if (OperandArray.every(arguments,
                arg -> arg.getCardinality() == StaticProperty.EXACTLY_ONE && arg.getItemType() == BuiltInAtomicType.BOOLEAN)) {
            // Warning if all the arguments are booleans: probably a misuse of the '||' operator
            visitor.getStaticContext().issueWarning(
                    "Did you intend to apply string concatenation to boolean operands? "
                            + "Perhaps you intended 'or' rather than '||'. "
                            + "To suppress this warning, use string() on the arguments.", SaxonErrorCode.SXWN9035, arguments[0].getLocation());
        }
        return new SystemFunctionCall.Optimized(this, arguments) {
            @Override
            @CSharpModifiers(code = {"public", "override"})
            public UnicodeString evaluateAsString(XPathContext context) throws XPathException {
                UnicodeBuilder buffer = new UnicodeBuilder();
                for (Operand o: operands()) {
                    Item item = o.getChildExpression().evaluateItem(context);
                    if (item != null) {
                        buffer.accept(item.getUnicodeStringValue());
                    }
                }
                return buffer.toUnicodeString();
            }

            @Override
            @CSharpModifiers(code = {"public", "override"})
            public Item evaluateItem(XPathContext context) throws XPathException {
                return new StringValue(evaluateAsString(context));
            }
        };

    }

    @Override
    public StringValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        UnicodeBuilder builder = new UnicodeBuilder();
        for (Sequence arg : arguments) {
            Item head = arg.head();
            if (head != null) {
                builder.accept(head.getUnicodeStringValue());
            }
        }
        return new StringValue(builder.toUnicodeString());
    }

    @Override
    public void process(Outputter destination, XPathContext context, Sequence[] arguments) throws XPathException {
        UniStringConsumer output = destination.getStringReceiver(false, Loc.NONE);
        output.open();
        for (Sequence arg : arguments) {
            Item head = arg.head();
            if (head != null) {
                output.accept(head.getUnicodeStringValue());
            }
        }
        output.close();
    }

    /**
     * Get the required type of the nth argument
     */

    @Override
    public SequenceType getRequiredType(int arg) {
        return getDetails().paramTypes[0];
        // concat() is a special case
    }


}

