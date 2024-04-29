////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;

import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

/**
 * Implement the fn:path function with one argument
 */
public class Path_1 extends ScalarSystemFunction {

    @Override
    public AtomicValue evaluate(Item arg, XPathContext context) throws XPathException {
        return makePath((NodeInfo)arg, context);
    }

    public static StringValue makePath(NodeInfo node, XPathContext context) {
        if (node.getNodeKind() == Type.DOCUMENT) {
            return StringValue.makeStringValue("/");
        }
        StringBuilder fsb = new StringBuilder(256);
        AxisIterator iter = node.iterateAxis(AxisInfo.ANCESTOR_OR_SELF);
        NodeInfo n;
        while ((n = iter.next()) != null) {
            if (n.getParent() == null) {
                if (n.getNodeKind() == Type.DOCUMENT) {
                    return new StringValue(fsb.toString());
                } else {
                    fsb.insert(0, "Q{http://www.w3.org/2005/xpath-functions}root()");
                    return new StringValue(fsb.toString());
                }
            }
            StringBuilder fsb2 = new StringBuilder(256);
            switch (n.getNodeKind()) {
                case Type.DOCUMENT:
                    return new StringValue(fsb.toString());
                case Type.ELEMENT:
                    fsb2.append("/Q{").append(n.getNamespaceUri()).append("}");
                    fsb2.append(n.getLocalPart());
                    fsb2.append("[").append(Navigator.getNumberSimple(n, context)).append("]");
                    fsb2.append(fsb);
                    fsb = fsb2;
                    break;
                case Type.ATTRIBUTE:
                    fsb2.append("/@");
                    String attURI = n.getNamespaceUri().toString();
                    if (!"".equals(attURI)) {
                        fsb2.append("Q{").append(attURI).append("}");
                    }
                    fsb2.append(n.getLocalPart());
                    fsb2.append(fsb);
                    fsb = fsb2;
                    break;
                case Type.TEXT:
                    fsb2.append("/text()[").append(Navigator.getNumberSimple(n, context) + "]").append(fsb);
                    fsb = fsb2;
                    break;
                case Type.COMMENT:
                    fsb2.append("/comment()[").append(Navigator.getNumberSimple(n, context)).append("]");
                    fsb2.append(fsb);
                    fsb = fsb2;
                    break;
                case Type.PROCESSING_INSTRUCTION:
                    fsb2.append("/processing-instruction(").append(n.getLocalPart()).append(")[");
                    fsb2.append(Navigator.getNumberSimple(n, context)).append("]");
                    fsb2.append(fsb);
                    fsb = fsb2;
                    break;
                case Type.NAMESPACE:
                    fsb2.append("/namespace::");
                    if (n.getLocalPart().isEmpty()) {
                        fsb2.append("*[Q{" + NamespaceConstant.FN + "}local-name()=\"\"]");
                    } else {
                        fsb.append(n.getLocalPart());
                    }
                    fsb2.append(fsb);
                    fsb = fsb2;
                    break;
                default:
                    throw new AssertionError();
            }
        }
        // should not reach here...
        fsb.insert(0, "Q{http://www.w3.org/2005/xpath-functions}root()");
        return new StringValue(fsb.toString());
    }

}

// Copyright (c) 2012-2023 Saxonica Limited

