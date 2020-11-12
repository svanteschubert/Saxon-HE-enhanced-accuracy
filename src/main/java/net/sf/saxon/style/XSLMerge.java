////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.expr.parser.RoleDiagnostic;
import net.sf.saxon.expr.sort.MergeInstr;
import net.sf.saxon.expr.sort.MergeInstr.MergeSource;
import net.sf.saxon.expr.sort.SortExpression;
import net.sf.saxon.expr.sort.SortKeyDefinition;
import net.sf.saxon.expr.sort.SortKeyDefinitionList;
import net.sf.saxon.om.AttributeInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.Whitespace;

import java.util.HashSet;
import java.util.Set;


/**
 * Handler for xsl:merge elements in stylesheet. <br>
 */
public class XSLMerge extends StyleElement {

    private int numberOfMergeSources = 0;

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
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true: yes, it may contain a sequence constructor
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return false;
    }

    @Override
    protected void prepareAttributes() {
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            checkUnknownAttribute(attName);
        }
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        int childMask = 0;
        Set<String> mergeSourceNames = new HashSet<>();
        for (NodeInfo child : children()) {
            if (child instanceof XSLMergeSource) {
                String name = ((XSLMergeSource)child).getSourceName();
                if (mergeSourceNames.contains(name)) {
                    compileError("Duplicate xsl:merge-source/@name", "XTSE3190");
                }
                mergeSourceNames.add(name);
                childMask = childMask | 1;
                numberOfMergeSources++;
            } else if (child instanceof XSLMergeAction) {
                if ((childMask & 2) == 2) {
                    compileError("xsl:merge must have only one xsl:merge-action child element", "XTSE0010");
                }
                childMask = childMask | 2;
            } else if (child.getNodeKind() == Type.TEXT) {
                // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    compileError("No character data is allowed within xsl:merge", "XXXX");
                }
            } else if (child instanceof XSLFallback) {
                if ((childMask & 2) == 0) {
                    compileError("xsl:fallback child of xsl:merge can appear only after xsl:merge-action", "XTSE0010");
                }
            } else {
                compileError("Child element " + Err.wrap(child.getDisplayName(), Err.ELEMENT) +
                        " is not allowed as a child of xsl:merge", "XTSE0010");
            }
        }
        if (childMask == 1) {
            compileError("xsl:merge element requires an xsl:merge-action", "XTSE0010");
        } else if (childMask == 2) {
            compileError("xsl:merge element requires at least one xsl:merge-source child element", "XTSE0010");
        }
    }

    /**
     * Check that the xsl:merge-source children have consistent merge key definitions, provided
     * that all the merge key definitions are statically determined. If any attribute value templates
     * are used in the merge key definitions, the validation is postponed until run-time
     *
     * @param sources the xsl:merge-source children
     */


    private void checkCompatibleMergeKeys(MergeSource[] sources) {
        for (int i = 0; i < sources[0].mergeKeyDefinitions.size(); i++) {
            if (!sources[0].mergeKeyDefinitions.getSortKeyDefinition(i).isFixed()) {
                break;
            }
            for (int z = 1; z < sources.length; z++) {
                if (!sources[z].mergeKeyDefinitions.getSortKeyDefinition(i).isFixed()) {
                    break;
                }
                // Both definitions are fixed: compare them now
                if (!compareSortKeyDefinitions(sources[z].mergeKeyDefinitions.getSortKeyDefinition(i),
                        sources[0].mergeKeyDefinitions.getSortKeyDefinition(i))) {
                    compileError("The " + RoleDiagnostic.ordinal(i + 1) + " merge key definition of the "
                            + RoleDiagnostic.ordinal(z + 1) + " merge source is incompatible with the " +
                            RoleDiagnostic.ordinal(i + 1) + " merge key definition of the first merge source",
                            "XTDE2210");
                }
            }
        }
    }

    /**
     * Compare two sort key definitions whose defining attributes are known at compile time.
     * The comparison compares all subexpressions other than the actual sort key
     *
     * @param sd1 the first sort key definition
     * @param sd2 the second sort key definition
     * @return true if the two sort key definitions are compatible according to the definition
     * used by xsl:merge (the sort keys can be different, but the other properties of the sort
     * must be the same)
     */

    private boolean compareSortKeyDefinitions(SortKeyDefinition sd1, SortKeyDefinition sd2) {

        return sameFixedExpression(sd1.getLanguage(), sd2.getLanguage()) &&
                sameFixedExpression(sd1.getOrder(), sd2.getOrder()) &&
                sameFixedExpression(sd1.getCollationNameExpression(), sd2.getCollationNameExpression()) &&
                sameFixedExpression(sd1.getCaseOrder(), sd2.getCaseOrder()) &&
                sameFixedExpression(sd1.getDataTypeExpression(), sd2.getDataTypeExpression());
    }

    private boolean sameFixedExpression(Expression e1, Expression e2) {
        return (e1==null && e2==null) || (e1!=null && e1.equals(e2));
    }

    @Override
    public Expression compile(Compilation compilation, ComponentDeclaration decl)
            throws XPathException {

        MergeInstr merge = new MergeInstr();
        int entries = numberOfMergeSources;
        MergeSource[] sources = new MergeSource[entries];
        Expression action = Literal.makeEmptySequence();
        int w = 0;
        int sortKeyDefLen = 0;
        for (NodeInfo node : children()) {
            if (node instanceof XSLMergeSource) {
                XSLMergeSource source = (XSLMergeSource) node;
                SortKeyDefinitionList sortKeyDefs = source.makeSortKeys(compilation, decl);
                if (sortKeyDefLen == 0) {
                    sortKeyDefLen = sortKeyDefs.size();
                } else if (sortKeyDefLen != sortKeyDefs.size()) {
                    compileError("Each xsl:merge-source must have the same number of xsl:merge-key children", "XTSE2200");
                }
                Expression select = source.getSelect();
                if (source.isSortBeforeMerge()) {
                    select = new SortExpression(select, sortKeyDefs.copy(new RebindingMap()));
                }
                MergeSource ms = source.makeMergeSource(merge, select);
                ms.mergeKeyDefinitions = sortKeyDefs;
                //ms.setRowSelect(select);
                sources[w++] = ms;
            } else if (node instanceof XSLMergeAction) {
                action = ((XSLMergeAction) node).compileSequenceConstructor(compilation, decl, true);
                if (action == null) {
                    action = Literal.makeEmptySequence();
                }
                try {
                    action = action.simplify();
                } catch (XPathException e) {
                    compileError(e);
                }

            } else {
                // fallback
            }
        }
        checkCompatibleMergeKeys(sources);
        merge.init(sources, action);
        return merge;
    }


}
