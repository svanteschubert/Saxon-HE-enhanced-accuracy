////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;

import net.sf.saxon.transpile.CSharpReplaceBody;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;

import java.util.HashMap;
import java.util.Stack;


/**
 * This class sits in a receiver (push) pipeline and maintains the current path.
 */


public class PathMaintainer extends ProxyReceiver {

    private final Stack<AbsolutePath.PathElement> path = new Stack<>();
    private final Stack<HashMap<NodeName, Integer>> siblingCounters = new Stack<>();

    public PathMaintainer(/*@NotNull*/ Receiver next) {
        super(next);
        siblingCounters.push(new HashMap<>());
    }

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties)
            throws XPathException {
        // System.err.println("startElement " + nameCode);

        HashMap<NodeName, Integer> counters = siblingCounters.peek();
        int preceding = counters.getOrDefault(elemName, 0);
        int index = preceding + 1;
        counters.put(elemName, index);
        path.push(new AbsolutePath.PathElement(Type.ELEMENT, elemName, index));
        siblingCounters.push(new HashMap<>());

        nextReceiver.startElement(elemName, type, attributes, namespaces, location, properties);
    }

    /**
     * Handle an end-of-element event
     */

    @Override
    public void endElement() throws XPathException {
        nextReceiver.endElement();
        siblingCounters.pop();
        path.pop();
    }

    @CSharpReplaceBody(code="return new Saxon.Hej.om.AbsolutePath(new List<Saxon.Hej.om.AbsolutePath.PathElement>(path.ToArray()));")
    // Custom code needed for C# because a Stack is not a List.
    public AbsolutePath getAbsolutePath() {
        return new AbsolutePath(path);
    }


}

