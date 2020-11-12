////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Controller;
import net.sf.saxon.event.*;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Operand;
import net.sf.saxon.expr.OperandRole;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.type.Type;

/**
 * A saxon:doctype element in the stylesheet.
 */

public class Doctype extends Instruction {

    private Operand contentOp;

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
     * @param rebindings
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        throw new UnsupportedOperationException("Doctype.copy()");
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

    @Override
    public TailCall processLeavingTail(Outputter out, XPathContext context) throws XPathException {
        Controller controller = context.getController();

        PipelineConfiguration pipe = controller.makePipelineConfiguration();
        pipe.setXPathContext(context);
        pipe.setHostLanguage(getPackageData().getHostLanguage());
        TinyBuilder builder = new TinyBuilder(pipe);
        builder.setStatistics(pipe.getConfiguration().getTreeStatistics().RESULT_TREE_STATISTICS);
        builder.open();
        builder.startDocument(ReceiverOption.NONE);
        getContent().process(ComplexContentOutputter.makeComplexContentReceiver(builder, null), context);
        builder.endDocument();
        builder.close();
        NodeInfo dtdRoot = builder.getCurrentRoot();

        SequenceIterator children = dtdRoot.iterateAxis(AxisInfo.CHILD);
        NodeInfo docType = (NodeInfo) children.next();
        if (docType == null || !"doctype".equals(docType.getLocalPart())) {
            XPathException e = new XPathException("saxon:doctype instruction must contain dtd:doctype");
            e.setXPathContext(context);
            throw e;
        }
        String name = docType.getAttributeValue("", "name");
        String system = docType.getAttributeValue("", "system");
        String publicid = docType.getAttributeValue("", "public");

        if (name == null) {
            XPathException e = new XPathException("dtd:doctype must have a name attribute");
            e.setXPathContext(context);
            throw e;
        }

        write(out, "<!DOCTYPE " + name + ' ');
        if (system != null) {
            if (publicid != null) {
                write(out, "PUBLIC \"" + publicid + "\" \"" + system + '\"');
            } else {
                write(out, "SYSTEM \"" + system + '\"');
            }
        }

        boolean openSquare = false;
        children = docType.iterateAxis(AxisInfo.CHILD);

        NodeInfo child = (NodeInfo) children.next();
        if (child != null) {
            write(out, " [");
            openSquare = true;
        }

        while (child != null) {
            String localname = child.getLocalPart();

            if ("element".equals(localname)) {
                String elname = child.getAttributeValue("", "name");
                String content = child.getAttributeValue("", "content");
                if (elname == null) {
                    XPathException e = new XPathException("dtd:element must have a name attribute");
                    e.setXPathContext(context);
                    throw e;
                }
                if (content == null) {
                    XPathException e = new XPathException("dtd:element must have a content attribute");
                    e.setXPathContext(context);
                    throw e;
                }
                write(out, "\n  <!ELEMENT " + elname + ' ' + content + '>');

            } else if (localname.equals("attlist")) {
                String elname = child.getAttributeValue("", "element");
                if (elname == null) {
                    XPathException e = new XPathException("dtd:attlist must have an attribute named 'element'");
                    e.setXPathContext(context);
                    throw e;
                }
                write(out, "\n  <!ATTLIST " + elname + ' ');

                SequenceIterator attributes = child.iterateAxis(AxisInfo.CHILD);
                while (true) {
                    NodeInfo attDef = (NodeInfo) attributes.next();
                    if (attDef == null) {
                        break;
                    }

                    if ("attribute".equals(attDef.getLocalPart())) {

                        String atname = attDef.getAttributeValue("", "name");
                        String type = attDef.getAttributeValue("", "type");
                        String value = attDef.getAttributeValue("", "value");
                        if (atname == null) {
                            XPathException e = new XPathException("dtd:attribute must have a name attribute");
                            e.setXPathContext(context);
                            throw e;
                        }
                        if (type == null) {
                            XPathException e = new XPathException("dtd:attribute must have a type attribute");
                            e.setXPathContext(context);
                            throw e;
                        }
                        if (value == null) {
                            XPathException e = new XPathException("dtd:attribute must have a value attribute");
                            e.setXPathContext(context);
                            throw e;
                        }
                        write(out, "\n    " + atname + ' ' + type + ' ' + value);
                    } else {
                        XPathException e = new XPathException("Unrecognized element within dtd:attlist");
                        e.setXPathContext(context);
                        throw e;
                    }
                }
                write(out, ">");

            } else if (localname.equals("entity")) {

                String entname = child.getAttributeValue("", "name");
                String parameter = child.getAttributeValue("", "parameter");
                String esystem = child.getAttributeValue("", "system");
                String epublicid = child.getAttributeValue("", "public");
                String notation = child.getAttributeValue("", "notation");

                if (entname == null) {
                    XPathException e = new XPathException("dtd:entity must have a name attribute");
                    e.setXPathContext(context);
                    throw e;
                }

                // we could do a lot more checking now...

                write(out, "\n  <!ENTITY ");
                if ("yes".equals(parameter)) {
                    write(out, "% ");
                }
                write(out, entname + ' ');
                if (esystem != null) {
                    if (epublicid != null) {
                        write(out, "PUBLIC \"" + epublicid + "\" \"" + esystem + "\" ");
                    } else {
                        write(out, "SYSTEM \"" + esystem + "\" ");
                    }
                }
                if (notation != null) {
                    write(out, "NDATA " + notation + ' ');
                }

                for (NodeInfo content : child.children()) {
                    content.copy(out, 0, getLocation());
                }

                write(out, ">");

            } else if (localname.equals("notation")) {
                String notname = child.getAttributeValue("", "name");
                String nsystem = child.getAttributeValue("", "system");
                String npublicid = child.getAttributeValue("", "public");
                if (notname == null) {
                    XPathException e = new XPathException("dtd:notation must have a name attribute");
                    e.setXPathContext(context);
                    throw e;
                }
                if ((nsystem == null) && (npublicid == null)) {
                    XPathException e = new XPathException("dtd:notation must have a system attribute or a public attribute");
                    e.setXPathContext(context);
                    throw e;
                }
                write(out, "\n  <!NOTATION " + notname);
                if (npublicid != null) {
                    write(out, " PUBLIC \"" + npublicid + "\" ");
                    if (nsystem != null) {
                        write(out, '\"' + nsystem + "\" ");
                    }
                } else {
                    write(out, " SYSTEM \"" + nsystem + "\" ");
                }
                write(out, ">");
            } else if (child.getNodeKind() == Type.TEXT) {
                write(out, child.getStringValue());
            } else {
                XPathException e = new XPathException("Unrecognized element " + localname + " in DTD output");
                e.setXPathContext(context);
                throw e;
            }
            child = (NodeInfo) children.next();
        }

        if (openSquare) {
            write(out, "\n]");
        }
        write(out, ">\n");

        return null;

    }

    private void write(Outputter out, String s) throws XPathException {
        out.characters(s, getLocation(), ReceiverOption.DISABLE_ESCAPING);
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

}

