////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.NumberInstruction;
import net.sf.saxon.expr.number.NumberFormatter;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.functions.Number_1;
import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.lib.Numberer;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * This expression performs the formatting part of the logic of the xsl:number instruction
 * It takes as input a sequence of integers, which may either be supplied directly as the
 * value attribute of xsl:number, or may be computed by counting nodes. The expression
 * returns a string.
 */

public class NumberSequenceFormatter extends Expression {

    private Operand valueOp;
    private Operand formatOp;
    private Operand groupSizeOp;
    private Operand groupSeparatorOp;
    private Operand letterValueOp;
    private Operand ordinalOp;
    private Operand startAtOp;
    private Operand langOp;

    private NumberFormatter formatter = null;
    private Numberer numberer = null;
    private boolean backwardsCompatible;

    /**
     * Construct a NumberSequenceFormatter
     *
     * @param value                  the expression supplied in the value attribute, or
     *                               the {@link NumberInstruction} which computes the place-marker
     * @param format                 the expression supplied in the format attribute
     * @param groupSize              the expression supplied in the group-size attribute
     * @param groupSeparator         the expression supplied in the grouping-separator attribute
     * @param letterValue            the expression supplied in the letter-value attribute
     * @param ordinal                the expression supplied in the ordinal attribute
     * @param startAt                the expression supplied in the start-at attribute
     * @param lang                   the expression supplied in the lang attribute
     * @param formatter              A NumberFormatter to be used
     * @param backwardsCompatible    true if running in 1.0 compatibility mode with a real value attribute
     */

    public NumberSequenceFormatter(Expression value,
                                   Expression format,
                                   Expression groupSize,
                                   Expression groupSeparator,
                                   Expression letterValue,
                                   Expression ordinal,
                                   Expression startAt,
                                   Expression lang,
                                   NumberFormatter formatter,
                                   boolean backwardsCompatible) {

        if (value != null) {
            valueOp = new Operand(this, value, OperandRole.SINGLE_ATOMIC);
        }
        if (format != null) {
            formatOp = new Operand(this, format, OperandRole.SINGLE_ATOMIC);
        }
        if (groupSize != null) {
            groupSizeOp = new Operand(this, groupSize, OperandRole.SINGLE_ATOMIC);
        }
        if (groupSeparator != null) {
            groupSeparatorOp = new Operand(this, groupSeparator, OperandRole.SINGLE_ATOMIC);
        }
        if (letterValue != null) {
            letterValueOp = new Operand(this, letterValue, OperandRole.SINGLE_ATOMIC);
        }
        if (ordinal != null) {
            ordinalOp = new Operand(this, ordinal, OperandRole.SINGLE_ATOMIC);
        }
        //if (startAt != null) {
            startAtOp = new Operand(this, startAt, OperandRole.SINGLE_ATOMIC);
        //}
        if (lang != null) {
            langOp = new Operand(this, lang, OperandRole.SINGLE_ATOMIC);
        }

        this.formatter = formatter;
        this.backwardsCompatible = backwardsCompatible;

        if (formatter == null && format instanceof StringLiteral) {
            this.formatter = new NumberFormatter();
            this.formatter.prepare(((StringLiteral)format).getStringValue());
        }
    }

    /**
     * Simplify an expression. This performs any static optimization (by rewriting the expression
     * as a different expression). The default implementation simplifies its operands.
     *
     * @return the simplified expression (or the original if unchanged, or if modified in-situ)
     * @throws XPathException if an error is discovered during expression
     *                                           rewriting
     */
    @Override
    public Expression simplify() throws XPathException {
        if (valueOp != null && !valueOp.getChildExpression().getItemType().isPlainType()) {
            valueOp.setChildExpression(Atomizer.makeAtomizer(valueOp.getChildExpression(), null));
        }
        preallocateNumberer(getConfiguration());
        return super.simplify();
    }

    public void preallocateNumberer(Configuration config) throws XPathException {
        if (langOp == null) {
            numberer = config.makeNumberer(null, null);
        } else {
            if (langOp.getChildExpression() instanceof StringLiteral) {
                String language = ((StringLiteral) langOp.getChildExpression()).getStringValue();
                if (!language.isEmpty()) {
                    ValidationFailure vf = StringConverter.StringToLanguage.INSTANCE.validate(language);
                    if (vf != null) {
                        langOp.setChildExpression(new StringLiteral(StringValue.EMPTY_STRING));
                        throw new XPathException("The lang attribute must be a valid language code", "XTDE0030");
                    }
                }
                numberer = config.makeNumberer(language, null);
            }   // else we allocate a numberer at run-time
        }
    }


    @Override
    public Iterable<Operand> operands() {
         return operandSparseList(valueOp, formatOp, groupSizeOp,
                                     groupSeparatorOp, letterValueOp, ordinalOp, startAtOp, langOp);
    }

    private boolean isFixed(Operand op) {
        return op==null || op.getChildExpression() instanceof Literal;
    }

    private boolean hasFixedOperands() {
        for (Operand o : operands()) {
            if (!isFixed(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        optimizeChildren(visitor, contextInfo);
        if (hasFixedOperands()) {
            StringValue val = evaluateItem(visitor.makeDynamicContext());
            StringLiteral literal = new StringLiteral(val);
            ExpressionTool.copyLocationInfo(this, literal);
            return literal;
        } else {
            return this;
        }
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
        NumberSequenceFormatter exp = new NumberSequenceFormatter(
                copy(valueOp, rebindings), copy(formatOp, rebindings),
                copy(groupSizeOp, rebindings), copy(groupSeparatorOp, rebindings), copy(letterValueOp, rebindings),
                copy(ordinalOp, rebindings), copy(startAtOp, rebindings),
                copy(langOp, rebindings), formatter, backwardsCompatible);
        ExpressionTool.copyLocationInfo(this, exp);
        return exp;
    }

    private Expression copy(Operand op, RebindingMap rebindings) {
        return op == null ? null : op.getChildExpression().copy(rebindings);
    }

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return BuiltInAtomicType.STRING;
    }

    @Override
    public int computeCardinality() {
        return StaticProperty.EXACTLY_ONE;
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
        return EVALUATE_METHOD;
    }

    @Override
    public StringValue evaluateItem(XPathContext context) throws XPathException {
        long value = -1;
        List<Object> vec = new ArrayList<>(4);    // a list whose items may be of type either Long or
                                                  // BigInteger or the string to be output (e.g. "NaN")
        final ConversionRules rules = context.getConfiguration().getConversionRules();
        String startAv = startAtOp.getChildExpression().evaluateAsString(context).toString();
        List<Integer> startValues = parseStartAtValue(startAv);


        SequenceIterator iter = valueOp.getChildExpression().iterate(context);
        AtomicValue val;
        int pos = 0;
        while ((val = (AtomicValue)iter.next()) != null) {
            if (backwardsCompatible && !vec.isEmpty()) {
                break;
            }
            int startValue = startValues.size() > pos ? startValues.get(pos) : startValues.get(startValues.size()-1);
            pos++;
            try {
                NumericValue num;
                if (val instanceof NumericValue) {
                    num = (NumericValue) val;
                } else {
                    num = Number_1.convert(val, context.getConfiguration());
                }
                if (num.isNaN()) {
                    throw new XPathException("NaN");  // thrown to be caught
                }
                num = num.round(0);
                if (num.compareTo(Int64Value.MAX_LONG) > 0) {
                    BigInteger bi = ((BigIntegerValue) Converter.convert(num, BuiltInAtomicType.INTEGER, rules).asAtomic()).asBigInteger();
                    if (startValue != 1) {
                        bi = bi.add(BigInteger.valueOf(startValue - 1));
                    }
                    vec.add(bi);
                } else {
                    if (num.compareTo(Int64Value.ZERO) < 0) {
                        throw new XPathException("The numbers to be formatted must not be negative");
                        // thrown to be caught
                    }
                    long i = ((NumericValue) Converter.convert(num, BuiltInAtomicType.INTEGER, rules).asAtomic()).longValue();
                    i += startValue - 1;
                    vec.add(i);
                }
            } catch (XPathException err) {
                if (backwardsCompatible) {
                    vec.add("NaN");
                } else {
                    vec.add(val.getStringValue());
                    XPathException e = new XPathException("Cannot convert supplied value to an integer. " + err.getMessage());
                    e.setErrorCode("XTDE0980");
                    e.setLocation(getLocation());
                    e.setXPathContext(context);
                    throw e;
                }
            }
        }
        if (backwardsCompatible && vec.isEmpty()) {
            vec.add("NaN");
        }

        int gpsize = 0;
        String gpseparator = "";
        String letterVal;
        String ordinalVal = null;

        if (groupSizeOp != null) {
            String g = groupSizeOp.getChildExpression().evaluateAsString(context).toString();
            try {
                gpsize = Integer.parseInt(g);
            } catch (NumberFormatException err) {
                XPathException e = new XPathException("grouping-size must be numeric");
                e.setXPathContext(context);
                e.setErrorCode("XTDE0030");
                e.setLocation(getLocation());
                throw e;
            }
        }

        if (groupSeparatorOp != null) {
            gpseparator = groupSeparatorOp.getChildExpression().evaluateAsString(context).toString();
        }

        if (ordinalOp != null) {
            ordinalVal = ordinalOp.getChildExpression().evaluateAsString(context).toString();
        }

        // Use the numberer decided at compile time if possible; otherwise try to get it from
        // a table of numberers indexed by language; if not there, load the relevant class and
        // add it to the table.
        Numberer numb = numberer;
        if (numb == null) {
            if (langOp == null) {
                numb = context.getConfiguration().makeNumberer(null, null);
            } else {
                String language = langOp.getChildExpression().evaluateAsString(context).toString();
                ValidationFailure vf = StringConverter.StringToLanguage.INSTANCE.validate(language);
                if (vf != null) {
                    throw new XPathException("The lang attribute of xsl:number must be a valid language code", "XTDE0030");
                }
                numb = context.getConfiguration().makeNumberer(language, null);
            }
        }

        if (letterValueOp == null) {
            letterVal = "";
        } else {
            letterVal = letterValueOp.getChildExpression().evaluateAsString(context).toString();
            if (!("alphabetic".equals(letterVal) || "traditional".equals(letterVal))) {
                XPathException e = new XPathException("letter-value must be \"traditional\" or \"alphabetic\"");
                e.setXPathContext(context);
                e.setErrorCode("XTDE0030");
                e.setLocation(getLocation());
                throw e;
            }
        }

        NumberFormatter nf;
        if (formatter == null) {              // format not known until run-time
            nf = new NumberFormatter();
            nf.prepare(formatOp.getChildExpression().evaluateAsString(context).toString());
        } else {
            nf = formatter;
        }

        CharSequence s = nf.format(vec, gpsize, gpseparator, letterVal, ordinalVal, numb);
        return new StringValue(s);
    }

    public List<Integer> parseStartAtValue(String value) throws XPathException {
        List<Integer> list = new ArrayList<>();
        String[] tokens = value.split("\\s+");
        for (String tok : tokens) {
            try {
                int n = Integer.parseInt(tok);
                list.add(n);
            } catch (NumberFormatException err) {
                XPathException e = new XPathException("Invalid start-at value: non-integer component {" + tok + "}");
                e.setErrorCode("XTDE0030");
                e.setLocation(getLocation());
                throw e;
            }
        }
        if (list.isEmpty()) {
            XPathException e = new XPathException("Invalid start-at value: no numeric components found");
            e.setErrorCode("XTDE0030");
            e.setLocation(getLocation());
            throw e;
        }
        return list;
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("numSeqFmt", this);
        String flags = "";
        if (backwardsCompatible) {
            flags += "1";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        if (valueOp != null) {
            out.setChildRole("value");
            valueOp.getChildExpression().export(out);
        }
        if (formatOp != null) {
            out.setChildRole("format");
            formatOp.getChildExpression().export(out);
        }
        if (startAtOp != null) {
            out.setChildRole("startAt");
            startAtOp.getChildExpression().export(out);
        }
        if (langOp != null) {
            out.setChildRole("lang");
            langOp.getChildExpression().export(out);
        }
        if (ordinalOp != null) {
            out.setChildRole("ordinal");
            ordinalOp.getChildExpression().export(out);
        }
        if (groupSeparatorOp != null) {
            out.setChildRole("gpSep");
            groupSeparatorOp.getChildExpression().export(out);
        }
        if (groupSizeOp != null) {
            out.setChildRole("gpSize");
            groupSizeOp.getChildExpression().export(out);
        }
        out.endElement();
    }
}

