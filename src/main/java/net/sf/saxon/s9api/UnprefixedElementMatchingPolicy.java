////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.transpile.CSharpSimpleEnum;

/**
 * An enumeration defining possible strategies for resolving unprefixed element names appearing
 * as name tests in the steps of a path expression or XSLT match pattern
 */

@CSharpSimpleEnum
public enum UnprefixedElementMatchingPolicy {

    /**
     * The standard W3C policy, whereby element names are implicitly qualified by the
     * default namespace for elements and types, as defined in the XPath static context.
     * In XSLT this can be set using the <code>[xsl:]xpath-default-namespace</code>
     * attribute, or programmatically using {@link XsltCompiler#setDefaultElementNamespace(String)}.
     * In 4.0 it can also be set by using <code>declare default element namespace 'uuuu';</code>
     * within the expression itself.
     */
    DEFAULT_NAMESPACE,

    /**
     * Under this policy, unprefixed element names match on the local part only; an element
     * with this local name is matched regardless of its namespace (that is, it can have any
     * namespace, or none). This can be set from the command line using -ns:##any, or from
     * the XSLT, XQuery, or XPath compiler API, or within XPath and XQuery using the declaration
     * <code>declare default element namespace "##any"</code>.
     */
    ANY_NAMESPACE,

    /**
     * Under this policy, unprefixed element names match provided that (a) the local part of
     * the name matches, and (b) the namespace part of the name is either equal to the default
     * namespace for elements and types, or is absent.
     *
     * <p>This policy is provided primarily for use with HTML, where it can be unpredictable
     * whether HTML elements are in the XHTML namespace or in no namespace. It is also useful
     * with other vocabularies where instances are sometimes in a namespace and sometimes not.
     * The policy approximates to the special rules defined in the HTML5 specification, which states
     * that unprefixed names are treated as matching names in the XHTML namespace if the context
     * item for a step is "a node in an HTML DOM", and as matching no-namespace names otherwise;
     * since in the XDM data model it is not possible to make a distinction between different kinds
     * of DOM, this policy allows use of unprefixed names both when matching elements in the XHTML
     * namespace and when matching no-namespace elements</p>
     */
    DEFAULT_NAMESPACE_OR_NONE,

    /**
     * Default policy when none has been explicitly defined
     */

    UNSPECIFIED
}

