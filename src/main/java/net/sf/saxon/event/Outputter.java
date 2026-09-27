////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.NamespaceBindingSet;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UniStringConsumer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

/**
 * Outputter: This interface represents a recipient of XML tree-walking (push) events. It was
 * originally based on SAX2's ContentHandler, but adapted to handle additional events. Namespaces and
 * Attributes are handled by separate events following the startElement event. Schema types
 * can be defined for elements and attributes.
 *
 * <p>The Outputter interface is an important internal interface within Saxon, and provides a powerful
 * mechanism for integrating Saxon with other applications. It has been designed with extensibility
 * and stability in mind. However, it should be considered as an interface designed primarily for
 * internal use, and not as a completely stable part of the public Saxon API.</p>
 *
 * @since 10.0; derived from the original Outputter interface and SequenceReceiver class.
 * This interface is now used primarily for capturing the results of push-mode evaluation
 * of tree construction expressions in XSLT and XQuery.
 *
 * @since 12.10; the original Outputter abstract class was renamed to AbstractOutputter
 * and refactored into this (minimal) interface. The refactoring was part of an effort to work
 * around what appears to be a bug in .NET Core. Refactoring the Java code was simpler than
 * trying to adapt the transpiler to make an interface here.
 */

public interface Outputter extends Receiver {
   /**
     * Notify the start of an element. This version of <code>startElement()</code> must be followed
     * by calls on {@link #attribute(NodeName, SimpleType, String, Location, int)} and
     * {@link #namespace(String, NamespaceUri, int)} to supply the attributes and namespaces; these calls
     * may be terminated by a call on {@link #startContent()} but this is not mandatory.
     *
     * @param elemName   the name of the element.
     * @param typeCode   the type annotation of the element.
     * @param location   an object providing information about the module, line, and column where the node originated
     * @param properties bit-significant properties of the element node. If there are no relevant
     *                   properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */

    void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties)
            throws XPathException;

    /**
     * Notify a namespace binding. This method is called at some point after startElement().
     * The semantics are similar to the xsl:namespace instruction in XSLT, or the namespace
     * node constructor in XQuery.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param prefix           The namespace prefix; zero-length string for the default namespace
     * @param namespaceUri     The namespace URI. In some cases a zero-length string may be used to
     *                         indicate a namespace undeclaration.
     * @param properties       The REJECT_DUPLICATES property: if set, the
     *                         namespace declaration will be rejected if it conflicts with a previous declaration of the same
     *                         prefix. If the property is not set, the namespace declaration will be ignored if it conflicts
     *                         with a previous declaration. This reflects the fact that when copying a tree, namespaces for child
     *                         elements are emitted before the namespaces of their parent element. Unfortunately this conflicts
     *                         with the XSLT rule for complex content construction, where the recovery action in the event of
     *                         conflicts is to take the namespace that comes last. XSLT therefore doesn't recover from this error:
     * @throws XPathException if an error occurs
     * @since changed in 10.0 to report all the in-scope namespaces for an element, and to do so
     * in a single call.
     */

    void namespace(String prefix, NamespaceUri namespaceUri, int properties) throws XPathException;

    /**
     * Output a set of namespace bindings. This should have the same effect as outputting the
     * namespace bindings individually using {@link #namespace(String, NamespaceUri, int)}, but it
     * may be more efficient. It is used only when copying an element node together with
     * all its namespaces, so less checking is needed that the namespaces form a consistent
     * and complete set
     * @param bindings the set of namespace bindings
     * @param properties any special properties. The property {@link ReceiverOption#NAMESPACE_OK}
     *                   means that no checking is needed.
     * @throws XPathException if any failure occurs
     */

    void namespaces(NamespaceBindingSet bindings, int properties) throws XPathException;

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * @param attName    The name of the attribute
     * @param typeCode   The type annotation of the attribute
     * @param value      the string value of the attribute
     * @param location  provides information such as line number and system ID.
     * @param properties Bit significant value. The following bits are defined:
     *                   <dl>
     *                   <dt>DISABLE_ESCAPING</dt>    <dd>Disable escaping for this attribute</dd>
     *                   <dt>NO_SPECIAL_CHARACTERS</dt>      <dd>Attribute value contains no special characters</dd>
     *                   </dl>
     * @throws IllegalStateException  attempt to output an attribute when there is no open element
     *                                start tag
     * @throws XPathException         if an error occurs
     */

    void attribute(NodeName attName, SimpleType typeCode, String value, Location location, int properties)
            throws XPathException;

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial Outputter of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     *
     * @throws XPathException if an error occurs
     */

    void startContent() throws XPathException;

    /**
     * Get a string-value consumer object that allows an item of type xs:string
     * to be appended one fragment at a time. This potentially allows operations that
     * output large strings to avoid building the entire string in memory. The default
     * implementation, however, simply assembles the string in a buffer and releases
     * the entire string on completion.
     * @return an object that accepts xs:string values via a sequence of append() calls
     * @param asTextNode set to true if the concatenated string values are to be treated
     *                   as a text node item rather than a string
     */

    UniStringConsumer getStringReceiver(boolean asTextNode, Location loc);
}
