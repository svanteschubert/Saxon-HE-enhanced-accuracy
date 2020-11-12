////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import java.util.Properties;
import java.util.Stack;

/**
 * <p><tt>NamespaceDifferencer</tt> is a {@link ProxyReceiver} responsible for removing duplicate namespace
 * declarations. It also ensures that namespace undeclarations are emitted when necessary.</p>
 *
 * <p>The NamespaceDifferencer assumes that in the input event stream, all in-scope namespaces for every element
 * are accounted for in the call on namespace(). In the output event stream, the namespace() call represents
 * namespace declarations rather than in-scope namespaces. So (a) redundant namespaces are removed,
 * and (b) namespace undeclarations are added where necessary. A namespace undeclaration for the default
 * namespace is always added if the parent element has a default namespace and the child element does not;
 * namespace undeclarations for other namespaces are emitted only when the serialization option undeclare-namespaces
 * is set.</p>
 *
 * <p>The {@code NamespaceDifferencer} is part of the serialization pipeline, responsible for translating result trees
 * to serialized XML. As such, it is not concerned with operations such as namespace fixup and namespace
 * inheritance that are part of the result tree construction process.</p>
 *
 * <p>The {@code NamespaceDifferencer} is also needed when writing output to tree models such as DOM and JDOM
 * that require local namespace declarations to be provided for each element node.</p>
 */

public class NamespaceDifferencer extends ProxyReceiver {

    private boolean undeclareNamespaces = false;
    private Stack<NamespaceMap> namespaceStack = new Stack<>();
    private NodeName currentElement;

    /**
     * Create a NamespaceDeclarer
     *
     * @param next the Receiver to which events will be passed after namespace reduction
     */

    public NamespaceDifferencer(Receiver next, Properties details) {
        super(next);
        undeclareNamespaces = "yes".equals(details.getProperty(SaxonOutputKeys.UNDECLARE_PREFIXES));
        namespaceStack.push(NamespaceMap.emptyMap());
    }

    /**
     * startElement. This call removes redundant namespace declarations, and
     * possibly adds an xmlns="" undeclaration.
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties)
            throws XPathException {
        currentElement = elemName;
        NamespaceMap parentMap = namespaceStack.peek();
        namespaceStack.push(namespaces);
        NamespaceMap delta = getDifferences(namespaces, parentMap, currentElement.hasURI(""));
        nextReceiver.startElement(elemName, type, attributes, delta, location, properties);

    }

    @Override
    public void endElement() throws XPathException {
        namespaceStack.pop();
        super.endElement();
    }

    private NamespaceMap getDifferences(NamespaceMap thisMap, NamespaceMap parentMap, boolean elementInDefaultNamespace) throws XPathException {
        if (thisMap != parentMap) {
            NamespaceMap delta = NamespaceDeltaMap.emptyMap();
            for (NamespaceBinding nb : thisMap) {
                String parentUri = parentMap.getURI(nb.getPrefix());
                if (parentUri == null) {
                    delta = delta.put(nb.getPrefix(), nb.getURI());
                } else if (!parentUri.equals(nb.getURI())) {
                    delta = delta.put(nb.getPrefix(), nb.getURI());
                }
            }
            if (undeclareNamespaces) {
                for (NamespaceBinding nb : parentMap) {
                    if (thisMap.getURI(nb.getPrefix()) == null) {
                        delta = delta.put(nb.getPrefix(), "");
                    }
                }
            } else {
                // undeclare the default namespace if the child element is in the default namespace
                if (!parentMap.getDefaultNamespace().isEmpty() &&
                        thisMap.getDefaultNamespace().isEmpty()) {
                    delta = delta.put("", "");
                }
            }
            return delta;
        }
        return NamespaceMap.emptyMap();
    }

}

