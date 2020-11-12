////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.sxpath;

import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.value.QNameValue;

/**
 * This interface defines methods that must be provided when Saxon's free-standing XPath API is used.
 * The default implementation of this interface is {@link net.sf.saxon.sxpath.IndependentContext}, and
 * that implementation should be adequate for most purposes; but for extra customization, a user-written
 * implementation of this interface may be used instead.
 */
public interface XPathStaticContext extends StaticContext {

    /**
     * Set the default namespace for elements and types
     *
     * @param uri The namespace to be used to qualify unprefixed element names and type names appearing
     *            in the XPath expression.
     */

    void setDefaultElementNamespace(String uri);

    /**
     * Set an external namespace resolver. If this is set, then all resolution of namespace
     * prefixes is delegated to the external namespace resolver, and namespaces declared
     * individually on this IndependentContext object are ignored.
     *
     * @param resolver the external namespace resolver
     */

    void setNamespaceResolver(NamespaceResolver resolver);

    /**
     * Declare a variable. A variable must be declared before an expression referring
     * to it is compiled. The initial value of the variable will be the empty sequence.
     *
     * @param qname The name of the variable
     * @return a Variable object representing information about the variable that has been
     *         declared.
     */

    XPathVariable declareVariable(QNameValue qname);

    /**
     * Declare a variable. A variable must be declared before an expression referring
     * to it is compiled. The initial value of the variable will be the empty sequence.
     *
     * @param namespaceURI The namespace URI of the name of the variable. Supply "" to represent
     *                     names in no namespace (null is also accepted)
     * @param localName    The local part of the name of the variable (an NCName)
     * @return an XPathVariable object representing information about the variable that has been
     *         declared.
     */

    XPathVariable declareVariable(String namespaceURI, String localName);

    /**
     * Get a Stack Frame Map containing definitions of all the declared variables. This will return a newly
     * created object that the caller is free to modify by adding additional variables, without affecting
     * the static context itself.
     *
     * @return a SlotManager object holding details of the allocation of variables on the stack frame.
     */

    SlotManager getStackFrameMap();

    /**
     * Ask whether the context item is known to be parentless
     * @return true if it is known that the context item for evaluating the expression will have no parent
     */

    boolean isContextItemParentless();




}

