////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.SimpleNodeConstructor;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.Whitespace;

/**
 * Common superclass for XSLT elements whose content template produces a text
 * value: xsl:text, xsl:value-of, xsl:attribute, xsl:comment, xsl:namespace, and xsl:processing-instruction
 */

public abstract class XSLLeafNodeConstructor extends StyleElement {

    //protected String stringValue = null;
    /*@Nullable*/ protected Expression select = null;

    /**
     * Method for use by subclasses (processing-instruction and namespace) that take
     * a name and a select attribute
     *
     * @return the expression defining the name attribute
     */

    protected Expression prepareAttributesNameAndSelect()  {

        Expression name = null;
        String nameAtt = null;
        String selectAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("name")) {
                nameAtt = Whitespace.trim(value);
                name = makeAttributeValueTemplate(nameAtt, att);
            } else if (f.equals("select")) {
                selectAtt = value;
                select = makeExpression(selectAtt, att);
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (nameAtt == null) {
            reportAbsence("name");
        }

        return name;
    }

    /**
     * Determine whether this node is an instruction.
     *
     * @return true - it is an instruction
     */

    @Override
    public boolean isInstruction() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain a template-body
     *
     * @return true: yes, it may contain a template-body
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        if (select != null && hasChildNodes()) {
            String errorCode = getErrorCodeForSelectPlusContent();
            compileError("An " + getDisplayName() + " element with a select attribute must be empty", errorCode);
        }
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        NodeInfo first = kids.next();
        if (select == null) {
            if (first == null) {
                // there are no child nodes and no select attribute
                //stringValue = "";
                select = new StringLiteral(StringValue.EMPTY_STRING);
                select.setRetainedStaticContext(makeRetainedStaticContext());
            } else {
                if (kids.next() == null && !isExpandingText()) {
                    // there is exactly one child node
                    if (first.getNodeKind() == Type.TEXT) {
                        // it is a text node: optimize for this case
                        select = new StringLiteral(first.getStringValue());
                        select.setRetainedStaticContext(makeRetainedStaticContext());
                    }
                }
            }
        }
    }

    /**
     * Get the error code to be returned when the element has a select attribute but is not empty.
     *
     * @return the error code defined for this condition, for this particular instruction
     */

    protected abstract String getErrorCodeForSelectPlusContent();

    protected void compileContent(Compilation exec, ComponentDeclaration decl, SimpleNodeConstructor inst, Expression separator) throws XPathException {
        if (separator == null) {
            separator = new StringLiteral(StringValue.SINGLE_SPACE);
        }
        try {
            if (select == null) {
                select = compileSequenceConstructor(exec, decl, true);
            }
            select = makeSimpleContentConstructor(select, separator, getStaticContext());
            inst.setSelect(select);

        } catch (XPathException err) {
            compileError(err);
        }
    }

    /**
     * Construct an expression that implements the rules of "constructing simple content":
     * given an expression to select the base sequence, and an expression to compute the separator,
     * build an (unoptimized) expression to produce the value of the node as a string.
     *
     * @param select    the expression that selects the base sequence
     * @param separator the expression that computes the separator
     * @param env    the static context
     * @return an expression that returns a string containing the string value of the constructed node
     */

    public static Expression makeSimpleContentConstructor(Expression select, Expression separator, StaticContext env) {
        RetainedStaticContext rsc = select.getLocalRetainedStaticContext();
        if (rsc == null) {
            rsc = env.makeRetainedStaticContext();
        }
        // Merge adjacent text nodes
        select = AdjacentTextNodeMerger.makeAdjacentTextNodeMerger(select);
        // Atomize the result
        select = Atomizer.makeAtomizer(select, null);
        // Convert each atomic value to a string
        select = new AtomicSequenceConverter(select, BuiltInAtomicType.STRING);
        select.setRetainedStaticContext(rsc);
        ((AtomicSequenceConverter) select).allocateConverterStatically(env.getConfiguration(), false);
        // Join the resulting strings with a separator
        if (select.getCardinality() != StaticProperty.EXACTLY_ONE) {
            select = SystemFunction.makeCall("string-join", rsc, select, separator);
        }
        // All that's left for the instruction to do is to construct the right kind of node
        return select;
    }


}

