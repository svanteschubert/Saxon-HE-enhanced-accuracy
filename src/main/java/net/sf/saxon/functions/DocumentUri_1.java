////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.AtomicValue;

/**
 * This class supports the document-uri() function
 */

public class DocumentUri_1 extends ScalarSystemFunction  {



    @Override
    public AtomicValue evaluate(Item item, XPathContext context) throws XPathException {
        return getDocumentURI((NodeInfo)item, context);
    }


    public static AnyURIValue getDocumentURI(NodeInfo node, XPathContext c) {
        if (node.getNodeKind() == Type.DOCUMENT) {
            Object o = node.getTreeInfo().getUserData("saxon:document-uri");
            if (o instanceof String) {
                return o.toString().isEmpty() ? null : new AnyURIValue(o.toString());
            }
            final Controller controller = c.getController();
            assert controller != null;
            DocumentPool pool = controller.getDocumentPool();
            String docURI = pool.getDocumentURI(node);
            if (docURI == null) {
                docURI = node.getSystemId();
            }
            if (docURI == null) {
                return null;
            } else if ("".equals(docURI)) {
                return null;
            } else {
                return new AnyURIValue(docURI);
            }
        } else {
            return null;
        }
    }

}

