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
import net.sf.saxon.expr.instruct.ForEachGroup;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Handler for xsl:for-each-group elements in stylesheet. This is a new instruction
 * defined in XSLT 2.0
 */

public final class XSLForEachGroup extends StyleElement {

    /*@Nullable*/ private Expression select = null;
    private Expression groupBy = null;
    private Expression groupAdjacent = null;
    private Pattern starting = null;
    private Pattern ending = null;
    private Expression collationName;
    private boolean composite = false;

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
        return child instanceof XSLSort;
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
        String groupByAtt = null;
        String groupAdjacentAtt = null;
        String startingAtt = null;
        String endingAtt = null;
        String collationAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            switch (f) {
                case "select":
                    selectAtt = value;
                    select = makeExpression(selectAtt, att);
                    break;
                case "group-by":
                    groupByAtt = value;
                    groupBy = makeExpression(groupByAtt, att);
                    break;
                case "group-adjacent":
                    groupAdjacentAtt = value;
                    groupAdjacent = makeExpression(groupAdjacentAtt, att);
                    break;
                case "group-starting-with":
                    startingAtt = value;
                    break;
                case "group-ending-with":
                    endingAtt = value;
                    break;
                case "collation":
                    collationAtt = Whitespace.trim(value);
                    collationName = makeAttributeValueTemplate(collationAtt, att);
                    break;
                case "bind-group":
                    compileError("The bind-group attribute has been dropped from the XSLT 3.0 specification", "XTSE0090");
                    break;
                case "bind-grouping-key":
                    compileError("The bind-grouping-key attribute has been dropped from the XSLT 3.0 specification", "XTSE0090");
                    break;
                case "composite":
                    composite = processBooleanAttribute("composite", value);
                    break;
                default:
                    checkUnknownAttribute(attName);
                    break;
            }
        }

        if (selectAtt == null) {
            reportAbsence("select");
            select = Literal.makeEmptySequence(); // for error recovery
        }

        int c = (groupByAtt == null ? 0 : 1) +
                (groupAdjacentAtt == null ? 0 : 1) +
                (startingAtt == null ? 0 : 1) +
                (endingAtt == null ? 0 : 1);
        if (c != 1) {
            compileError("Exactly one of the attributes group-by, group-adjacent, group-starting-with, " +
                    "and group-ending-with must be specified", "XTSE1080");
        }

        if (startingAtt != null) {
            starting = makePattern(startingAtt, "group-starting-with");
        }

        if (endingAtt != null) {
            ending = makePattern(endingAtt, "group-ending-with");
        }

        if (collationAtt != null) {
            if (groupBy == null && groupAdjacent == null) {
                compileError("A collation may be specified only if group-by or group-adjacent is specified", "XTSE1090");
            } else {
                if (collationName instanceof StringLiteral) {
                    String collation = ((StringLiteral) collationName).getStringValue();
                    URI collationURI;
                    try {
                        collationURI = new URI(collation);
                        if (!collationURI.isAbsolute()) {
                            URI base = new URI(getBaseURI());
                            collationURI = base.resolve(collationURI);
                            collationName = new StringLiteral(collationURI.toString());
                        }
                    } catch (URISyntaxException err) {
                        compileError("Collation name '" + collationName + "' is not a valid URI", "XTDE1110");
                        collationName = new StringLiteral(NamespaceConstant.CODEPOINT_COLLATION_URI);
                    }
                }
            }
        } else {
            String defaultCollation = getDefaultCollationName();
            if (defaultCollation != null) {
                collationName = new StringLiteral(defaultCollation);
            }
        }

        if (composite && (starting != null || ending != null)) {
            compileError("The composite attribute cannot be used with " +
                    (starting == null ? "grouping-ending-with" : "group-starting-with"), "XTSE1090");
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkSortComesFirst(false);
        TypeChecker tc = getConfiguration().getTypeChecker(false);
        select = typeCheck("select", select);

        ExpressionVisitor visitor = makeExpressionVisitor();
        if (groupBy != null) {
            groupBy = typeCheck("group-by", groupBy);
            try {
                RoleDiagnostic role =
                        new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:for-each-group/group-by", 0);
                groupBy = tc.staticTypeCheck(groupBy, SequenceType.ATOMIC_SEQUENCE, role, visitor);
            } catch (XPathException err) {
                compileError(err);
            }
        } else if (groupAdjacent != null) {
            groupAdjacent = typeCheck("group-adjacent", groupAdjacent);
            try {
                RoleDiagnostic role =
                        new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:for-each-group/group-adjacent", 0);
                role.setErrorCode("XTTE1100");
                groupAdjacent = tc.staticTypeCheck(groupAdjacent,
                        composite ? SequenceType.ATOMIC_SEQUENCE : SequenceType.SINGLE_ATOMIC,
                                                   role, visitor);
            } catch (XPathException err) {
                compileError(err);
            }
        }

        starting = typeCheck("starting", starting);
        ending = typeCheck("ending", ending);

        if ((starting != null || ending != null) && visitor.getStaticContext().getXPathVersion() < 30) {
            try {
                RoleDiagnostic role =
                        new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:for-each-group/select", 0);
                role.setErrorCode("XTTE1120");
                select = tc.staticTypeCheck(select, SequenceType.NODE_SEQUENCE, role, visitor);
            } catch (XPathException err) {
                String prefix = starting != null ?
                        "With group-starting-with attribute: " :
                        "With group-ending-with attribute: ";
                compileError(prefix + err.getMessage(), err.getErrorCodeQName());
            }
        }
        if (!hasChildNodes()) {
            compileWarning("An empty xsl:for-each-group instruction has no effect", SaxonErrorCode.SXWN9009);
        }

    }

    @Override
    public Expression compile(Compilation compilation, ComponentDeclaration decl) throws XPathException {

        StringCollator collator = null;
        if (collationName instanceof StringLiteral) {
            // if the collation name is constant, then we've already resolved it against the base URI
            final String uri = ((StringLiteral) collationName).getStringValue();
            collator = findCollation(uri, getBaseURI());
            if (collator == null) {
                compileError("The collation name '" + collationName + "' has not been defined", "XTDE1110");
            }
        }

        byte algorithm = 0;
        Expression key = null;
        if (groupBy != null) {
            algorithm = ForEachGroup.GROUP_BY;
            key = groupBy;
        } else if (groupAdjacent != null) {
            algorithm = ForEachGroup.GROUP_ADJACENT;
            key = groupAdjacent;
        } else if (starting != null) {
            algorithm = ForEachGroup.GROUP_STARTING;
            key = starting;
        } else if (ending != null) {
            algorithm = ForEachGroup.GROUP_ENDING;
            key = ending;
        }

        Expression action = compileSequenceConstructor(compilation, decl, true);
        if (action == null) {
            // body of for-each is empty: it's a no-op.
            return Literal.makeEmptySequence();
        }
        try {

            ForEachGroup instr = new ForEachGroup(select,
                    action.simplify(),
                    algorithm,
                    key,
                    collator,
                    collationName,
                makeSortKeys(compilation, decl));
            instr.setIsInFork(getParent().getFingerprint() == StandardNames.XSL_FORK);
            instr.setComposite(composite);
            return instr;
        } catch (XPathException e) {
            compileError(e);
            return null;
        }

    }

}

