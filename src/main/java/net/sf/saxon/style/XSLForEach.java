////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.ForEach;
import net.sf.saxon.expr.sort.SortExpression;
import net.sf.saxon.expr.sort.SortKeyDefinitionList;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.Whitespace;


/**
 * Handler for xsl:for-each elements in stylesheet. <br>
 */

public class XSLForEach extends StyleElement {

    /*@Nullable*/ private Expression select = null;
    private boolean containsTailCall = false;
    private Expression threads = null;
    private Expression separator = null;

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
     * Specify that xsl:sort is a permitted child
     */

    @Override
    protected boolean isPermittedChild(StyleElement child) {
        return (child instanceof XSLSort);
    }

    @Override
    protected boolean markTailCalls() {
        assert select != null;
        if (Cardinality.allowsMany(select.getCardinality())) {
            return false;
        } else {
            StyleElement last = getLastChildInstruction();
            containsTailCall = last != null && last.markTailCalls();
            return containsTailCall;
        }
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
    public void prepareAttributes() {

        String selectAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("select")) {
                selectAtt = value;
                select = makeExpression(selectAtt, att);
            } else if (f.equals("separator")) {
                requireSyntaxExtensions("separator");
                separator = makeAttributeValueTemplate(value, att);
            } else if (attName.getLocalPart().equals("threads") && attName.hasURI(NamespaceConstant.SAXON)) {
                String threadsAtt = Whitespace.trim(value);
                threads = makeAttributeValueTemplate(threadsAtt, att);
                if (getCompilation().getCompilerInfo().isCompileWithTracing()) {
                    compileWarning("saxon:threads - no multithreading takes place when compiling with trace enabled",
                            SaxonErrorCode.SXWN9012);
                    threads = new StringLiteral("0");
                } else if (!"EE".equals(getConfiguration().getEditionCode())) {
                    compileWarning("saxon:threads - ignored when not running Saxon-EE",
                            SaxonErrorCode.SXWN9013);
                    threads = new StringLiteral("0");
                }
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (selectAtt == null) {
            reportAbsence("select");
            select = Literal.makeEmptySequence();
        }

    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkSortComesFirst(false);
        select = typeCheck("select", select);
        if (separator != null) {
            separator = typeCheck("separator", separator);
        }
        if (threads != null) {
            threads = typeCheck("threads", threads);
        }
        if (!hasChildNodes()) {
            compileWarning("An empty xsl:for-each instruction has no effect", SaxonErrorCode.SXWN9009);
        }
    }

    @Override
    public Expression compile(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        SortKeyDefinitionList sortKeys = makeSortKeys(compilation, decl);
        Expression sortedSequence = select;
        if (sortKeys != null) {
            sortedSequence = new SortExpression(select, sortKeys);
        }

        Expression block = compileSequenceConstructor(compilation, decl, true);
        if (block == null) {
            // body of for-each is empty: it's a no-op.
            return Literal.makeEmptySequence();
        }
        try {
            ForEach result = new ForEach(sortedSequence, block.simplify(), containsTailCall, threads);
            result.setInstruction(true);
            result.setLocation(allocateLocation());
            if (separator != null) {
                result.setSeparatorExpression(separator);
            }
            return result;
        } catch (XPathException err) {
            compileError(err);
            return null;
        }
    }


}

