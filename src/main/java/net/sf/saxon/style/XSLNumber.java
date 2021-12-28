////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.ContextItemExpression;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.NumberSequenceFormatter;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.NumberInstruction;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.expr.number.NumberFormatter;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

/**
 * An xsl:number element in the stylesheet. <br>
 */

public class XSLNumber extends StyleElement {



    private int level;
    /*@Nullable*/ private Pattern count = null;
    private Pattern from = null;
    private Expression select = null;
    private Expression value = null;
    private Expression format = null;
    private Expression groupSize = null;
    private Expression groupSeparator = null;
    private Expression letterValue = null;
    private Expression lang = null;
    private Expression ordinal = null;
    private Expression startAt = null;
    private NumberFormatter formatter = null;

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    @Override
    public boolean isInstruction() {
        return true;
    }


    @Override
    public void prepareAttributes() {

        String selectAtt = null;
        String valueAtt = null;
        String countAtt = null;
        String fromAtt = null;
        String levelAtt = null;
        String formatAtt = null;
        AttributeInfo gsizeAtt = null;
        AttributeInfo gsepAtt = null;
        String langAtt = null;
        String letterValueAtt = null;
        String ordinalAtt = null;
        String startAtAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String attValue = att.getValue();
            switch (f) {
                case "select":
                    selectAtt = attValue;
                    select = makeExpression(selectAtt, att);
                    break;
                case "value":
                    valueAtt = attValue;
                    this.value = makeExpression(valueAtt, att);
                    break;
                case "count":
                    countAtt = attValue;
                    break;
                case "from":
                    fromAtt = attValue;
                    break;
                case "level":
                    levelAtt = Whitespace.trim(attValue);
                    break;
                case "format":
                    formatAtt = attValue;
                    format = makeAttributeValueTemplate(formatAtt, att);
                    break;
                case "lang":
                    langAtt = attValue;
                    lang = makeAttributeValueTemplate(langAtt, att);
                    break;
                case "letter-value":
                    letterValueAtt = Whitespace.trim(attValue);
                    letterValue = makeAttributeValueTemplate(letterValueAtt, att);
                    break;
                case "grouping-size":
                    gsizeAtt = att;
                    break;
                case "grouping-separator":
                    gsepAtt = att;
                    break;
                case "ordinal":
                    ordinalAtt = attValue;
                    ordinal = makeAttributeValueTemplate(ordinalAtt, att);
                    break;
                case "start-at":
                    startAtAtt = attValue;
                    startAt = makeAttributeValueTemplate(startAtAtt, att);
                    break;
                default:
                    checkUnknownAttribute(attName);
                    break;
            }
        }


        if (valueAtt != null) {
            if (selectAtt != null) {
                compileError("The select attribute and value attribute must not both be present", "XTSE0975");
            }
            if (countAtt != null) {
                compileError("The count attribute and value attribute must not both be present", "XTSE0975");
            }
            if (fromAtt != null) {
                compileError("The from attribute and value attribute must not both be present", "XTSE0975");
            }
            if (levelAtt != null) {
                compileError("The level attribute and value attribute must not both be present", "XTSE0975");
            }
        }

        if (countAtt != null) {
            count = makePattern(countAtt, "count");
        }

        if (fromAtt != null) {
            from = makePattern(fromAtt, "from");
        }

        if (levelAtt == null) {
            level = NumberInstruction.SINGLE;
        } else if (levelAtt.equals("single")) {
            level = NumberInstruction.SINGLE;
        } else if (levelAtt.equals("multiple")) {
            level = NumberInstruction.MULTI;
        } else if (levelAtt.equals("any")) {
            level = NumberInstruction.ANY;
        } else {
            invalidAttribute("level", "single|any|multiple");
        }

        if (level == NumberInstruction.SINGLE && from == null && count == null) {
            level = NumberInstruction.SIMPLE;
        }

        if (formatAtt != null) {
            if (format instanceof StringLiteral) {
                formatter = new NumberFormatter();
                formatter.prepare(((StringLiteral) format).getStringValue());
            }
            // else we'll need to allocate the formatter at run-time
        } else {
            formatter = new NumberFormatter();
            formatter.prepare("1");
        }

        if (gsepAtt != null && gsizeAtt != null) {
            // the spec says that if only one is specified, it is ignored
            groupSize = makeAttributeValueTemplate(gsizeAtt.getValue(), gsizeAtt);
            groupSeparator = makeAttributeValueTemplate(gsepAtt.getValue(), gsepAtt);
        }

        if (startAtAtt != null) {
            if (startAtAtt.indexOf('{') < 0 && !startAtAtt.matches("-?[0-9]+(\\s+-?[0-9]+)*")) {
                compileErrorInAttribute("Invalid format for start-at attribute", "XTSE0020", "start-at");
            }
        } else {
            startAt = new StringLiteral("1");
        }

    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkEmpty();

        select = typeCheck("select", select);
        value = typeCheck("value", value);
        format = typeCheck("format", format);
        groupSize = typeCheck("group-size", groupSize);
        groupSeparator = typeCheck("group-separator", groupSeparator);
        letterValue = typeCheck("letter-value", letterValue);
        ordinal = typeCheck("ordinal", ordinal);
        lang = typeCheck("lang", lang);
        from = typeCheck("from", from);
        count = typeCheck("count", count);
        startAt = typeCheck("start-at", startAt);

        String errorCode = "XTTE1000";
        if (value == null && select == null) {
            errorCode = "XTTE0990";
            ContextItemExpression implicitSelect = new ContextItemExpression();
            implicitSelect.setLocation(allocateLocation());
            implicitSelect.setErrorCodeForUndefinedContext(errorCode, false);
            select = implicitSelect;
        }

        if (select != null) {
            try {
                RoleDiagnostic role =
                        new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:number/select", 0);
                role.setErrorCode(errorCode);
                select = getConfiguration().getTypeChecker(false).staticTypeCheck(select,
                        SequenceType.SINGLE_NODE,
                                                                                  role, makeExpressionVisitor());
            } catch (XPathException err) {
                compileError(err);
            }
        }
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        boolean valueSpecified = value != null;
        if (value == null) {
            value = new NumberInstruction(
                    select,
                    level,
                    count,
                    from);
            value.setLocation(allocateLocation());
        }
        NumberSequenceFormatter numFormatter = new NumberSequenceFormatter(
                value,
                format,
                groupSize,
                groupSeparator,
                letterValue,
                ordinal,
                startAt,
                lang,
                formatter,
                xPath10ModeIsEnabled() && valueSpecified);
        numFormatter.setLocation(allocateLocation());
        ValueOf inst = new ValueOf(numFormatter, false, false);
        inst.setLocation(allocateLocation());
        inst.setIsNumberingInstruction();
        return inst;
    }

}
