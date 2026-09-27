////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.SequenceInstr;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.ma.map.*;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.function.Supplier;

/**
 * Handler for xsl:map instructions in an XSLT 3.0 or 4.0 stylesheet.
 */

public class XSLMap extends StyleElement implements AutoDocumentInhibitor {

    private Expression select = null;
    private Expression onDuplicates = null;

    /**
     * Ask whether this node is an instruction.
     * @return true - it is an instruction
     */

    @Override
    public boolean isInstruction() {
        return true;
    }

    /**
     * Get the type of the items returned by this instruction
     *
     * @return the static item type
     */
    @Override
    public ItemType getItemType() {
        return MapType.ANY_MAP_TYPE;
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true: yes, it may contain a sequence constructor
     */

    @Override
    protected boolean mayContainSequenceConstructor() {
        return true;
    }

    @Override
    protected void prepareAttributes() {
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            if (f.equals("select")) {
                if (requireXslt40Attribute("select")) {
                    select = makeExpression(value, att);
                }
            } else if (f.equals("duplicates") /*|| f.equals("on-duplicates")*/) { // TODO: drop old name
                if (requireXslt40Attribute("duplicates")) {
                    onDuplicates = makeExpression(value, att);
                }
            } else {
                checkUnknownAttribute(attName);
            }
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        select = typeCheck("select", select);
        if (select != null) {
            for (NodeInfo kid : children()) {
                if (!(kid instanceof XSLFallback)) {
                    compileError("An xsl:map element with a select attribute must be empty", "XTSE3185");
                    return;
                }
            }
        }
    }

    @Override
    public Expression compile(Compilation exec, ComponentDeclaration decl) throws XPathException {
        if (select == null) {
            select = compileSequenceConstructor(exec, decl, false);
        }
        select = select.simplify();
        // Custom type-checking; the checking performed by map:merge() gives poor diagnostics
        TypeChecker tc = getConfiguration().getTypeChecker(false);
        Supplier<RoleDiagnostic> role =
                () -> new RoleDiagnostic(RoleDiagnostic.MISC, "xsl:map sequence constructor", 0, "XTTE3375");
        select = tc.staticTypeCheck(
                select,
                SequenceType.zeroOrMore(MapType.ANY_MAP_TYPE),
                role, makeExpressionVisitor());

        Expression optionsExp;

        if (onDuplicates != null) {
            optionsExp = MapFunctionSet.getInstance(31).makeFunction("entry", 2).makeFunctionCall(
                    new StringLiteral("duplicates"), onDuplicates);
        } else {
            GeneralMapBuilder optionsBuilder = AbstractFixedMap.getBuilder(
                    getCompilation().getCompilerInfo().getXsltVersion());
            optionsBuilder.put(new StringValue("duplicates"), MapItem.xslMapDuplicatesAction);
            optionsBuilder.put(new QNameValue("", NamespaceUri.SAXON, "allow-streaming"), BooleanValue.TRUE);
            optionsExp = Literal.makeLiteral(optionsBuilder.getCompletedMap());
        }

        Expression exp = MapFunctionSet.getInstance(40).makeFunction("merge", 2)
                .makeFunctionCall(select, optionsExp);
        exp.setRetainedStaticContext(makeRetainedStaticContext());
        if (getConfiguration().getBooleanProperty(Feature.STRICT_STREAMABILITY)) {
            exp = new SequenceInstr(exp);
        }
        return exp;
    }

}
