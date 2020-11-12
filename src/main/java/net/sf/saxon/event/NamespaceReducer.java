////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * <tt>NamespaceReducer</tt> is a {@link ProxyReceiver} responsible for removing duplicate namespace
 * declarations. It also ensures that an {@code xmlns=""} undeclaration is output when
 * necessary. Used on its own, the {@code NamespaceReducer} simply eliminates unwanted
 * namespace declarations. It can also be subclassed, in which case the subclass
 * can use the services of the {@code NamespaceReducer} to resolve QNames.
 * <p>The {@code NamespaceReducer} also validates namespace-sensitive content.</p>
 */

public class NamespaceReducer extends ProxyReceiver implements NamespaceResolver {
    // We keep track of namespaces to avoid outputting duplicate declarations. The namespaces
    // array holds a list of all namespaces currently declared (organised as pairs of entries,
    // prefix followed by URI). The countStack contains an entry for each element currently open; the
    // value on the stack is an integer giving the number of namespaces added to the main
    // namespace stack by that element.

    private NamespaceBinding[] namespaces = new NamespaceBinding[50];          // all namespace codes currently declared
    private int namespacesSize = 0;                  // all namespaces currently declared
    private int[] countStack = new int[50];
    private int depth = 0;

    // Creating an element does not automatically inherit the namespaces of the containing element.
    // When the DISINHERIT property is set on startElement(), this indicates that the namespaces
    // on that element are not to be automatically inherited by its children. So startElement()
    // stacks a boolean flag indicating whether the children are to disinherit the parent's namespaces.

    private boolean[] disinheritStack = new boolean[50];

    // When a child element does not inherit the namespaces of its parent, it acquires undeclarations
    // to indicate this fact. This array keeps track of the undeclarations that need to be added to the
    // current child element.

    private NamespaceBinding[] pendingUndeclarations = null;

    /**
     * Create a NamespaceReducer
     *
     * @param next the Receiver to which events will be passed after namespace reduction
     */

    public NamespaceReducer(Receiver next) {
        super(next);
    }

    /**
     * startElement. This call removes redundant namespace declarations, and
     * possibly adds an xmlns="" undeclaration.
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaceMap,
                             Location location, int properties)
            throws XPathException {
        nextReceiver.startElement(elemName, type, attributes, namespaceMap, location, properties);


        if (ReceiverOption.contains(properties, ReceiverOption.REFUSE_NAMESPACES)) {
            // Typically XQuery: the element does not inherit namespaces from its parent
            pendingUndeclarations = Arrays.copyOf(namespaces, namespacesSize);
        } else if (depth > 0 && disinheritStack[depth - 1]) {
            // If the parent element specified inherit=no, keep a list of namespaces that need to be
            // undeclared. Note (bug 20340) that namespaces are still inherited from grandparent elements
            List<NamespaceBinding> undeclarations = new ArrayList<>(namespacesSize);
            int k = namespacesSize;
            for (int d = depth-1; d>=0; d--) {
                if (!disinheritStack[d]) {
                    break;
                }
                for (int i=0; i<countStack[d]; i++) {
                    undeclarations.add(namespaces[--k]);
                }
            }
            pendingUndeclarations = undeclarations.toArray(NamespaceBinding.EMPTY_ARRAY);
        } else {
            pendingUndeclarations = null;
        }

        // Record the current height of the namespace list so it can be reset at endElement time

        countStack[depth] = 0;
        disinheritStack[depth] = ReceiverOption.contains(properties, ReceiverOption.DISINHERIT_NAMESPACES);
        if (++depth >= countStack.length) {
            countStack = Arrays.copyOf(countStack, depth*2);
            disinheritStack = Arrays.copyOf(disinheritStack, depth*2);
        }

    }

//    /**
//     * Output a namespace node (binding)
//     *
//     * @param namespaceBindings the prefix/uri pair to be output
//     * @param properties       the properties of the namespace binding
//     * @throws XPathException if any downstream error occurs
//     */
//
//    public void namespaces(NamespaceMap namespaceBindings, int properties) throws XPathException {
//        nextReceiver.namespaces(namespaceBindings, properties);
//    }

    /**
     * Determine whether a namespace declaration is needed
     *
     * @param nsBinding the namespace binding
     * @return true if the namespace is needed: that is, if it not the XML namespace, is not a duplicate,
     *         and is not a redundant xmlns="".
     */

    private boolean isNeeded(NamespaceBinding nsBinding) {
        if (nsBinding.isXmlNamespace()) {
            // Ignore the XML namespace
            return false;
        }

        // First cancel any pending undeclaration of this namespace prefix (there may be more than one)

        String prefix = nsBinding.getPrefix();
        if (pendingUndeclarations != null) {
            for (int p = 0; p < pendingUndeclarations.length; p++) {
                NamespaceBinding nb = pendingUndeclarations[p];
                if (nb != null && prefix.equals(nb.getPrefix())) {
                    pendingUndeclarations[p] = null;
                    //break;
                }
            }
        }

        for (int i = namespacesSize - 1; i >= 0; i--) {
            if (namespaces[i].equals(nsBinding)) {
                // it's a duplicate so we don't need it
                return false;
            }
            if (namespaces[i].getPrefix().equals(nsBinding.getPrefix())) {
                // same prefix, different URI.
                return true;
            }
        }

        // we need it unless it's a redundant xmlns=""
        return !nsBinding.isDefaultUndeclaration();
    }

    /**
     * Add a namespace declaration to the stack
     *
     * @param nsBinding the namespace code to be added
     */

    private void addToStack(NamespaceBinding nsBinding) {
        // expand the stack if necessary
        if (namespacesSize + 1 >= namespaces.length) {
            namespaces = Arrays.copyOf(namespaces, namespacesSize * 2);
        }
        namespaces[namespacesSize++] = nsBinding;
    }

    /**
     * Ask whether the namespace reducer is disinheriting namespaces at the current level
     * @return true if namespaces are being disinherited
     */

    public boolean isDisinheritingNamespaces() {
        return depth > 0 && disinheritStack[depth-1];
    }

//    /**
//     * startContent: Add any namespace undeclarations needed to stop
//     * namespaces being inherited from parent elements
//     */
//
//    public void startContent() throws XPathException {
//        pendingUndeclarations = null;
//        nextReceiver.startContent();
//    }

    /**
     * endElement: Discard the namespaces declared on this element.
     */


    @Override
    public void endElement() throws XPathException {
        if (depth-- == 0) {
            throw new IllegalStateException("Attempt to output end tag with no matching start tag");
        }

        namespacesSize -= countStack[depth];

        nextReceiver.endElement();

    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is ""
     * @return the uri for the namespace, or null if the prefix is not in scope
     */

    /*@Nullable*/
    @Override
    public String getURIForPrefix(String prefix, boolean useDefault) {
        if (prefix.isEmpty() && !useDefault) {
            return NamespaceConstant.NULL;
        } else if ("xml".equals(prefix)) {
            return NamespaceConstant.XML;
        } else {
            for (int i = namespacesSize - 1; i >= 0; i--) {
                if (namespaces[i].getPrefix().equals(prefix)) {
                    return namespaces[i].getURI();
                }
            }
        }
        return prefix.isEmpty() ? NamespaceConstant.NULL : null;
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    @Override
    public Iterator<String> iteratePrefixes() {
        List<String> prefixes = new ArrayList<>(namespacesSize);
        for (int i = namespacesSize - 1; i >= 0; i--) {
            String prefix = namespaces[i].getPrefix();
            if (!prefixes.contains(prefix)) {
                prefixes.add(prefix);
            }
        }
        prefixes.add("xml");
        return prefixes.iterator();
    }
}

