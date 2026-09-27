////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.OperandRole;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PushElaborator;
import net.sf.saxon.expr.elab.PushEvaluator;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.str.StringView;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.type.Type;

/**
 * A saxon:doctype element in the stylesheet.
 */

public class Doctype extends Instruction {

    private final Operand contentOp;

    public Doctype(Expression content) {
        contentOp = new Operand(this, content, OperandRole.SINGLE_ATOMIC);
    }

    public Expression getContent() {
        return contentOp.getChildExpression();
    }

    public void setContent(Expression content) {
        contentOp.setChildExpression(content);
    }

    @Override
    public Iterable<Operand> operands() {
        return contentOp;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings the rebinding map
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        return new Doctype(getContent().copy(rebindings));
    }


    /**
     * Determine whether this instruction creates new nodes.
     * This implementation returns true.
     */

    @Override
    public final boolean mayCreateNewNodes() {
        return true;
    }

    /**
     * Get the name of this instruction for diagnostic and tracing purposes
     */

    @Override
    public int getInstructionNameCode() {
        return StandardNames.SAXON_DOCTYPE;
    }

    private void write(Outputter out, String s) throws XPathException {
        out.characters(StringView.of(s), getLocation(), ReceiverOption.DISABLE_ESCAPING);
    }


    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("saxonDoctype", this);
        getContent().export(out);
        out.endElement();
    }

    public Elaborator getElaborator() {
        return new DoctypeElaborator();
    }

    private static class DoctypeElaborator extends PushElaborator {

        private void write(Outputter out, String s) throws XPathException {
            out.characters(StringView.of(s), getExpression().getLocation(), ReceiverOption.DISABLE_ESCAPING);
        }
        @Override
        public PushEvaluator elaborateForPush() {
            Doctype expr = (Doctype) getExpression();
            PushEvaluator contentPush = expr.getContent().makeElaborator().elaborateForPush();
            return (output, context) -> {
                Controller controller = context.getController();

                PipelineConfiguration pipe = controller.makePipelineConfiguration();
                pipe.setXPathContext(context);
                pipe.setHostLanguage(expr.getPackageData().getHostLanguage(), expr.getPackageData().getHostLanguageVersion());
                TinyBuilder builder = new TinyBuilder(pipe);
                builder.setStatistics(pipe.getConfiguration().getTreeStatistics().RESULT_TREE_STATISTICS);
                builder.open();
                builder.startDocument(ReceiverOption.NONE);
                TailCall tc = contentPush.processLeavingTail(
                        ComplexContentOutputter.makeComplexContentReceiver(builder, null), context);
                Expression.dispatchTailCall(tc);
                builder.endDocument();
                builder.close();
                NodeInfo dtdRoot = builder.getCurrentRoot();

                SequenceIterator children = dtdRoot.iterateChildAxis(AnyGNode.TEST);
                NodeInfo docType = (NodeInfo) children.next();
                if (docType == null || !"doctype".equals(docType.getLocalPart())) {
                    throw new XPathException("saxon:doctype instruction must contain dtd:doctype")
                            .withXPathContext(context);
                }
                String name = docType.getAttributeValue(NamespaceUri.NULL, "name");
                String system = docType.getAttributeValue(NamespaceUri.NULL, "system");
                String publicid = docType.getAttributeValue(NamespaceUri.NULL, "public");

                if (name == null) {
                    throw new XPathException("dtd:doctype must have a name attribute")
                            .withXPathContext(context);
                }

                write(output, "<!DOCTYPE " + name + ' ');
                if (system != null) {
                    if (publicid != null) {
                        write(output, "PUBLIC \"" + publicid + "\" \"" + system + '\"');
                    } else {
                        write(output, "SYSTEM \"" + system + '\"');
                    }
                }

                boolean openSquare = false;
                children = docType.iterateChildAxis(AnyGNode.TEST);

                NodeInfo child = (NodeInfo) children.next();
                if (child != null) {
                    write(output, " [");
                    openSquare = true;
                }

                while (child != null) {
                    String localname = child.getLocalPart();

                    if ("element".equals(localname)) {
                        String elname = child.getAttributeValue(NamespaceUri.NULL, "name");
                        String content = child.getAttributeValue(NamespaceUri.NULL, "content");
                        if (elname == null) {
                            throw new XPathException("dtd:element must have a name attribute")
                                    .withXPathContext(context);
                        }
                        if (content == null) {
                            throw new XPathException("dtd:element must have a content attribute")
                                    .withXPathContext(context);
                        }
                        write(output, "\n  <!ELEMENT " + elname + ' ' + content + '>');

                    } else if (localname.equals("attlist")) {
                        String elname = child.getAttributeValue(NamespaceUri.NULL, "element");
                        if (elname == null) {
                            throw new XPathException("dtd:attlist must have an attribute named 'element'")
                                    .withXPathContext(context);
                        }
                        write(output, "\n  <!ATTLIST " + elname + ' ');

                        SequenceIterator attributes = child.iterateChildAxis(AnyGNode.TEST);
                        while (true) {
                            NodeInfo attDef = (NodeInfo) attributes.next();
                            if (attDef == null) {
                                break;
                            }

                            if ("attribute".equals(attDef.getLocalPart())) {

                                String atname = attDef.getAttributeValue(NamespaceUri.NULL, "name");
                                String type = attDef.getAttributeValue(NamespaceUri.NULL, "type");
                                String value = attDef.getAttributeValue(NamespaceUri.NULL, "value");
                                if (atname == null) {
                                    throw new XPathException("dtd:attribute must have a name attribute")
                                            .withXPathContext(context);
                                }
                                if (type == null) {
                                    throw new XPathException("dtd:attribute must have a type attribute")
                                            .withXPathContext(context);
                                }
                                if (value == null) {
                                    throw new XPathException("dtd:attribute must have a value attribute")
                                            .withXPathContext(context);
                                }
                                write(output, "\n    " + atname + ' ' + type + ' ' + value);
                            } else {
                                throw new XPathException("Unrecognized element within dtd:attlist")
                                        .withXPathContext(context);
                            }
                        }
                        write(output, ">");

                    } else if (localname.equals("entity")) {

                        String entname = child.getAttributeValue(NamespaceUri.NULL, "name");
                        String parameter = child.getAttributeValue(NamespaceUri.NULL, "parameter");
                        String esystem = child.getAttributeValue(NamespaceUri.NULL, "system");
                        String epublicid = child.getAttributeValue(NamespaceUri.NULL, "public");
                        String notation = child.getAttributeValue(NamespaceUri.NULL, "notation");

                        if (entname == null) {
                            throw new XPathException("dtd:entity must have a name attribute")
                                .withXPathContext(context);
                        }

                        // we could do a lot more checking now...

                        write(output, "\n  <!ENTITY ");
                        if ("yes".equals(parameter)) {
                            write(output, "% ");
                        }
                        write(output, entname + ' ');
                        if (esystem != null) {
                            if (epublicid != null) {
                                write(output, "PUBLIC \"" + epublicid + "\" \"" + esystem + "\" ");
                            } else {
                                write(output, "SYSTEM \"" + esystem + "\" ");
                            }
                        }
                        if (notation != null) {
                            write(output, "NDATA " + notation + ' ');
                        }

                        SequenceIterator grandChildren = child.iterateChildAxis(null);
                        for (NodeInfo content; (content = (NodeInfo) grandChildren.next()) != null; ) {
                            content.copy(output, 0, expr.getLocation());
                        }

                        write(output, ">");

                    } else if (localname.equals("notation")) {
                        String notname = child.getAttributeValue(NamespaceUri.NULL, "name");
                        String nsystem = child.getAttributeValue(NamespaceUri.NULL, "system");
                        String npublicid = child.getAttributeValue(NamespaceUri.NULL, "public");
                        if (notname == null) {
                            throw new XPathException("dtd:notation must have a name attribute")
                                    .withXPathContext(context);
                        }
                        if ((nsystem == null) && (npublicid == null)) {
                            throw new XPathException("dtd:notation must have a system attribute or a public attribute")
                                    .withXPathContext(context);
                        }
                        write(output, "\n  <!NOTATION " + notname);
                        if (npublicid != null) {
                            write(output, " PUBLIC \"" + npublicid + "\" ");
                            if (nsystem != null) {
                                write(output, '\"' + nsystem + "\" ");
                            }
                        } else {
                            write(output, " SYSTEM \"" + nsystem + "\" ");
                        }
                        write(output, ">");
                    } else if (child.getNodeKind() == Type.TEXT) {
                        write(output, child.getStringValue());
                    } else {
                        throw new XPathException("Unrecognized element " + localname + " in DTD output")
                                .withXPathContext(context);
                    }
                    child = (NodeInfo) children.next();
                }

                if (openSquare) {
                    write(output, "\n]");
                }
                write(output, ">\n");

                return null;

            };
        }
    }

}

