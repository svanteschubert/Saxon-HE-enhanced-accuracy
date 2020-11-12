package com.saxonica.xqj.pull;

import net.sf.saxon.event.NamespaceReducer;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pull.PullFilter;
import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * PullNamespaceReducer is a PullFilter responsible for removing duplicate namespace
 * declarations. It also performs namespace fixup: that is, it ensures that the
 * namespaces used in element and attribute names are all declared.
 * <p>This class is derived from, and contains much common code with, the NamespaceReducer
 * in the push pipeline. (In the push version, however, namespace fixup is not
 * performed by the NamespaceReducer, but by the ComplexContentOutputter).</p>
 *
 * @see NamespaceReducer
 */

public class PullNamespaceReducer extends PullFilter implements NamespaceResolver {

    // As well as keeping track of namespaces, this class keeps a stack of element names,
    // so that the current element name is available to the caller after an endElement event

    private NodeName[] namestack = new NodeName[50];              // stack of element name codes
    NodeName elementJustEnded = null;                          // namecode of the element that has just ended

    // We keep track of namespaces to avoid outputting duplicate declarations. The namespaces
    // vector holds a list of all namespaces currently declared (organised as integer namespace codes).
    // The countStack contains an entry for each element currently open; the
    // value on the countStack is an Integer giving the number of namespaces added to the main
    // namespace stack by that element.

    private NamespaceBinding[] allNamespaces = new NamespaceBinding[50];
    // all namespaces currently declared
    private int allNamespacesSize = 0;                  // all namespaces currently declared
    private int[] namespaceCountStack = new int[50];    // one entry per started element, holding the number
    // of namespaces declared at that level
    private int depth = 0;                              // current depth of element nesting
    private NamespaceBinding[] localNamespaces;         // namespaces declared on the current start element
    private int localNamespacesSize = 0;
    private NodeName nodeName;                               // the name of the current element

    private NamespaceBinding[] declaredNamespaces;
    private AttributeMap attributeMap;

    // Creating an element does not automatically inherit the namespaces of the containing element.
    // TODO: disinheriting namespaces is not yet supported by the pull pipeline
    //private boolean[] disinheritStack = new boolean[50];

    private NamespaceBinding[] pendingUndeclarations = null;  // never assigned

    /**
     * Create a namespace reducer for a pull pipeline
     *
     * @param base the next stage in the pipeline, from which events are read
     */

    public PullNamespaceReducer(PullProvider base) {
        super(base);
    }

    /**
     * next(): handle next event.
     * The START_ELEMENT event removes redundant namespace declarations, and
     * possibly adds an xmlns="" undeclaration.
     */

    @Override
    public Event next() throws XPathException {

        currentEvent = super.next();

        switch (currentEvent) {
            case START_ELEMENT:
                startElement();
                break;
            case END_ELEMENT:
                endElement();
                break;
            case PROCESSING_INSTRUCTION:
            case ATTRIBUTE:
            case NAMESPACE:
                nodeName = super.getNodeName();
                break;
            default:
                nodeName = null;
        }
        return currentEvent;
    }

    private void startElement() throws XPathException {
        // Record the current height of the namespace list so it can be reset at endElement time

        namespaceCountStack[depth] = 0;
        //disinheritStack[depth] = (properties & ReceiverOption.DISINHERIT_NAMESPACES) != 0;
        if (++depth >= namespaceCountStack.length) {
            namespaceCountStack = Arrays.copyOf(namespaceCountStack, depth*2);
            namestack = Arrays.copyOf(namestack, depth*2);
        }

        // Get the list of namespaces associated with this element

        localNamespaces = super.getNamespaceDeclarations();
        localNamespacesSize = 0;
        for (int i = 0; i < localNamespaces.length; i++) {
            if (localNamespaces[i] == null) {
                break;
            } else {
                if (isNeeded(localNamespaces[i])) {
                    addGlobalNamespace(localNamespaces[i]);
                    namespaceCountStack[depth - 1]++;
                    localNamespaces[localNamespacesSize++] = localNamespaces[i];
                }
            }
        }

        // Namespace fixup: ensure that the element namespace is output


        nodeName = checkProposedPrefix(super.getNodeName(), 0);
        namestack[depth - 1] = nodeName;

        // Namespace fixup: ensure that all namespaces used in attribute names are declared

        attributeMap = super.getAttributes();
        List<AttributeInfo> list = new ArrayList<>(attributeMap.size());
        for (AttributeInfo att : attributeMap) {
            NodeName nc = att.getNodeName();
            AttributeInfo att2 = att;
            if (!nc.getURI().isEmpty()) {
                // Only need to do checking for an attribute that's namespaced
                NodeName newnc = checkProposedPrefix(nc, list.size());
                if (nc != newnc) {
                    att2 = new AttributeInfo(newnc,
                                             att.getType(),
                                             att.getValue(),
                                             att.getLocation(),
                                             att.getProperties());
                }
            }
            list.add(att2);
        }
        attributeMap = AttributeMap.fromList(list);

        if (localNamespacesSize < localNamespaces.length) {
            localNamespaces[localNamespacesSize] = null;     // add a terminator
        }

        declaredNamespaces = localNamespaces;
        namespaceCountStack[depth - 1] = localNamespacesSize;
    }

    private void addLocalNamespace(NamespaceBinding nc) {
        if (localNamespacesSize < localNamespaces.length) {
            localNamespaces[localNamespacesSize++] = nc;
        } else {
            if (localNamespacesSize == 0) {
                localNamespaces = new NamespaceBinding[10];
            } else {
                localNamespaces = Arrays.copyOf(localNamespaces, localNamespacesSize*2);
                localNamespaces[localNamespacesSize++] = nc;
            }
        }
        addGlobalNamespace(nc);
    }

    /**
     * Determine whether a namespace declaration is needed
     *
     * @param nscode the namespace code of the declaration (prefix plus uri)
     * @return true if the namespace declaration is needed
     */

    private boolean isNeeded(NamespaceBinding nscode) {
        if (nscode.isXmlNamespace()) {
            // Ignore the XML namespace
            return false;
        }

        // First cancel any pending undeclaration of this namespace prefix (there may be more than one)

        if (pendingUndeclarations != null) {
            for (int p = 0; p < pendingUndeclarations.length; p++) {
                if (nscode.getPrefix().equals(pendingUndeclarations[p].getPrefix())) {
                    pendingUndeclarations[p] = null;
                    //break;
                }
            }
        }

        for (int i = allNamespacesSize - 1; i >= 0; i--) {
            if (allNamespaces[i] == nscode) {
                // it's a duplicate so we don't need it
                return false;
            }
            if (allNamespaces[i].getPrefix().equals(nscode.getPrefix())) {
                // same prefix, different URI, so we do need it
                return true;
            }
        }

        // we need it unless it's a redundant xmlns=""
        return !nscode.isDefaultUndeclaration();

    }

    /**
     * Check that the prefix for an element or attribute is acceptable, allocating a substitute
     * prefix if not. The prefix is acceptable unless a namespace declaration has been
     * written that assignes this prefix to a different namespace URI. This method
     * also checks that the element or attribute namespace has been declared, and declares it
     * if not.
     *
     * @param nameCode the integer name code of the element or attribute
     * @param seq      sequence number used to generate a unique prefix code
     * @return either the original nameCode, or a new nameCode in which the prefix has been changed
     */

    private NodeName checkProposedPrefix(NodeName nameCode, int seq) {
        String oldPrefix = nameCode.getPrefix();
        String uri = nameCode.getURI();
        NamespaceBinding nsBinding = new NamespaceBinding(oldPrefix, uri);

        for (int i = allNamespacesSize - 1; i >= 0; i--) {
            if (oldPrefix.equals(allNamespaces[i].getPrefix())) {
                // same prefix
                if (uri.equals(allNamespaces[i].getURI())) {
                    // same URI
                    return nameCode;    // all is well
                } else {
                    // same prefix is bound to a different URI. Action depends on whether the declaration
                    // is local to this element or at an outer level
                    if (i + localNamespacesSize >= allNamespacesSize) {
                        // the prefix is already defined locally, so allocate a new one
                        String newPrefix = getSubstitutePrefix(oldPrefix, seq);
                        NodeName newNameCode = new FingerprintedQName(newPrefix, uri, nameCode.getLocalPart());
                        NamespaceBinding newNSCode = new NamespaceBinding(newPrefix, uri);
                        addLocalNamespace(newNSCode);
                        return newNameCode;
                    } else {
                        // the prefix has been used on an outer level, but we can reuse it here
                        addLocalNamespace(nsBinding);
                        return nameCode;
                    }
                }
            }
        }
        // there is no declaration of this prefix: declare it now
        if (!nsBinding.isDefaultUndeclaration()) {
            addLocalNamespace(nsBinding);
        }
        return nameCode;
    }

    /**
     * It is possible for a single output element to use the same prefix to refer to different
     * namespaces. In this case we have to generate an alternative prefix for uniqueness. The
     * one we generate is based on the sequential position of the element/attribute: this is
     * designed to ensure both uniqueness (with a high probability) and repeatability
     *
     * @param prefix the original prefix
     * @param seq    sequence number to help in making the generated prefix unique
     * @return the invented prefix
     */

    private String getSubstitutePrefix(String prefix, int seq) {
        return prefix + '_' + seq;
    }

    /**
     * Add a namespace declaration to the stack
     *
     * @param nscode the namespace code representing the namespace declaration
     */

    private void addGlobalNamespace(NamespaceBinding nscode) {
        // expand the stack if necessary
        if (allNamespacesSize + 1 >= allNamespaces.length) {
            allNamespaces = Arrays.copyOf(allNamespaces, allNamespacesSize*2);
        }
        allNamespaces[allNamespacesSize++] = nscode;
    }

    /**
     * Get the attributes associated with the current element. This method must
     * be called only after a START_ELEMENT event has been notified. The contents
     * of the returned AttributeCollection are guaranteed to remain unchanged
     * until the next START_ELEMENT event, but may be modified thereafter. The object
     * should not be modified by the client.
     * <p>Attributes may be read before or after reading the namespaces of an element,
     * but must not be read after the first child node has been read, or after calling
     * one of the methods skipToEnd(), getStringValue(), or getTypedValue().</p>
     *
     * @return an AttributeCollection representing the attributes of the element
     *         that has just been notified.
     */

    @Override
    public AttributeMap getAttributes() {
        return attributeMap;
    }

    /**
     * Get the namespace declarations associated with the current element. This method must
     * be called only after a START_ELEMENT event has been notified. In the case of a top-level
     * START_ELEMENT event (that is, an element that either has no parent node, or whose parent
     * is not included in the sequence being read), the NamespaceDeclarations object returned
     * will contain a namespace declaration for each namespace that is in-scope for this element
     * node. In the case of a non-top-level element, the NamespaceDeclarations will contain
     * a set of namespace declarations and undeclarations, representing the differences between
     * this element and its parent.
     * <p>This class extends the semantics of the PullProvider interface by allowing this method
     * to be called also after an END_ELEMENT event. This is to support PullToStax, which requires
     * this functionality. In this situation it returns the namespaces declared on the startElement
     * associated with the element that has just ended.</p>
     * <p>It is permissible for this method to return namespace declarations that are redundant.</p>
     * <p>The NamespaceDeclarations object is guaranteed to remain unchanged until the next START_ELEMENT
     * event, but may then be overwritten. The object should not be modified by the client.</p>
     * <p>Namespaces may be read before or after reading the attributes of an element,
     * but must not be read after the first child node has been read, or after calling
     * one of the methods skipToEnd(), getStringValue(), or getTypedValue().</p>
     */

    @Override
    public NamespaceBinding[] getNamespaceDeclarations() {
        if (currentEvent == Event.END_ELEMENT) {
            // this case is sufficiently rare that we don't worry about its efficiency.
            // The namespaces that are needed are still on the namespace stack, even though the
            // top-of-stack pointer has already been decremented.
            int nscount = namespaceCountStack[depth];
            NamespaceBinding[] namespaces = new NamespaceBinding[nscount];
            System.arraycopy(allNamespaces, allNamespacesSize, namespaces, 0, nscount);
            return namespaces;
        } else {
            return declaredNamespaces;
        }
    }

    /**
     * endElement: Discard the namespaces declared on this element. Note, however, that for the
     * benefit of PullToStax, the namespaces that go out of scope on this endElement are available
     * so long as the endElement is the current event
     */


    public void endElement() throws XPathException {
        if (depth-- == 0) {
            throw new IllegalStateException("Attempt to output end tag with no matching start tag");
        }
        elementJustEnded = namestack[depth];
        allNamespacesSize -= namespaceCountStack[depth];
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
            return "";
        } else if ("xml".equals(prefix)) {
            return NamespaceConstant.XML;
        } else {
            for (int i = allNamespacesSize - 1; i >= 0; i--) {
                if (allNamespaces[i].getPrefix().equals(prefix)) {
                    return allNamespaces[i].getURI();
                }
            }
            return prefix.isEmpty() ? NamespaceConstant.NULL : null;
        }
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    @Override
    public Iterator<String> iteratePrefixes() {
        List<String> prefixes = new ArrayList<>(allNamespacesSize);
        for (int i = allNamespacesSize - 1; i >= 0; i--) {
            String prefix = allNamespaces[i].getPrefix();
            if (!prefixes.contains(prefix)) {
                prefixes.add(prefix);
            }
        }
        prefixes.add("xml");
        return prefixes.iterator();
    }


}

// Copyright (c) 2009-2020 Saxonica Limited
