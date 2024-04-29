////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PushEvaluator;
import net.sf.saxon.expr.elab.SimpleNodePushElaborator;
import net.sf.saxon.expr.elab.UnicodeStringEvaluator;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.StringConstants;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;


/**
 * An instruction representing an xsl:comment element in the stylesheet.
 */

public final class Comment extends SimpleNodeConstructor {

    /**
     * Construct the instruction
     */

    public Comment() {
    }

    /**
     * Get the instruction name, for diagnostics and tracing
     * return the string "xsl:comment"
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_COMMENT;
    }

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return NodeKindTest.COMMENT;
    }

    @Override
    public int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }


    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables to be re-bound
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        Comment exp = new Comment();
        ExpressionTool.copyLocationInfo(this, exp);
        exp.setSelect(getSelect().copy(rebindings));
        return exp;
    }

    @Override
    public void localTypeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        // Do early checking of content if known statically

        if (getSelect() instanceof Literal) {
            UnicodeString s = ((Literal) getSelect()).getGroundedValue().getUnicodeStringValue();
            UnicodeString s2 = checkContent(s, visitor.getStaticContext().makeEarlyEvaluationContext());
            if (!s2.equals(s)) {
                setSelect(new StringLiteral(s2));
            }
        }
    }


    /**
     * Process the value of the node, to create the new node.
     *
     * @param value   the string value of the new node
     * @param output the destination for the result
     * @param context the dynamic evaluation context
     * @throws XPathException if a dynamic error occurs
     */

    @Override
    public void processValue(UnicodeString value, Outputter output, XPathContext context) throws XPathException {
        UnicodeString comment = checkContent(value, context);
        output.comment(comment, getLocation(), ReceiverOption.NONE);
    }


    /**
     * Check the content of the node, and adjust it if necessary
     *
     * @param comment the supplied content
     * @param context the dynamic context
     * @return the original content, unless adjustments are needed
     * @throws XPathException if the content is invalid
     */

    @Override
    public UnicodeString checkContent(UnicodeString comment, XPathContext context) throws XPathException {
        if (isXSLT()) {
            return checkContentXSLT(comment);
        } else {
            try {
                return checkContentXQuery(comment);
            } catch (XPathException err) {
                throw err.withXPathContext(context)
                        .withLocation(getLocation());
            }
        }
    }

    /**
     * Check the content of the comment according to the XSLT rules (which fix it if it is wrong)
     *
     * @param comment the proposed text of the comment
     * @return the adjusted text of the comment
     */

    public static UnicodeString checkContentXSLT(UnicodeString comment) {
        String message = invalidity(comment);
        if (message != null) {
            long hh;
            while ((hh = comment.indexOf(TWO_HYPHENS, 0)) >= 0) {
                comment = comment.substring(0, hh + 1).concat(StringConstants.SINGLE_SPACE).concat(comment.substring(hh + 1));
            }
            if (comment.codePointAt(comment.length()-1) == '-') {
                comment = comment.concat(StringConstants.SINGLE_SPACE);
            }
        }
        return comment;
    }

    /**
     * Check the content of the comment according to the XQuery rules (which throw an error if it is wrong)
     *
     * @param comment the proposed text of the comment
     * @return the adjusted text of the comment (always the same as the original if there is no error)
     * @throws net.sf.saxon.trans.XPathException
     *          if the content is invalid
     */

    public static UnicodeString checkContentXQuery(UnicodeString comment) throws XPathException {
        String message = invalidity(comment);
        if (message != null) {
            throw new XPathException(message, "XQDY0072");
        }
        return comment;
    }

    private static String invalidity(UnicodeString comment) {
        if (comment.indexOf(TWO_HYPHENS, 0) >= 0) {
            return "Invalid characters (--) in comment";
        }
        if (comment.length() > 0 && comment.codePointAt(comment.length() - 1) == '-') {
            return "Comment cannot end in '-'";
        }
        return null;
    }

    private final static UnicodeString TWO_HYPHENS = new Twine8(StringConstants.TWO_HYPHENS);


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("comment", this);
        String flags = "";
        if (isLocal()) {
            flags += "l";
        }
        if (!flags.isEmpty()) {
            out.emitAttribute("flags", flags);
        }
        getSelect().export(out);
        out.endElement();
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new CommentElaborator();
    }


    private static class CommentElaborator extends SimpleNodePushElaborator {
        @Override
        public PushEvaluator elaborateForPush() {
            Comment expr = (Comment)getExpression();
            Location loc = expr.getLocation();
            UnicodeStringEvaluator contentEval = expr.getSelect().makeElaborator().elaborateForUnicodeString(true);
            if (expr.isXSLT()) {
                return (out, context) -> {
                    UnicodeString content = contentEval.eval(context);
                    content = Comment.checkContentXSLT(content);
                    out.comment(content, loc, ReceiverOption.NONE);
                    return null;
                };
            } else {
                return (out, context) -> {
                    UnicodeString content = contentEval.eval(context);
                    Comment.checkContentXQuery(content);
                    out.comment(content, loc, ReceiverOption.NONE);
                    return null;
                };
            }
        }
    }

}
