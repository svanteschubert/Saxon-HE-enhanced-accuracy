////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api.push;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;

/**
 * A {@link Container} representing an element node.
 *
 * <p>The permitted sequence of events for an element node is
 * {@code (ATTRIBUTE | NAMESPACE)* (COMMENT | PI | TEXT | ELEMENT)* CLOSE?}.</p>
 *
 * <p>The methods for events other than child elements return the element container to
 * while they are applied, so methods can be chained: for example
 * {@code element("foo").attribute("bar", "1").text("baz").close()} generates the XML
 * content {@code &lt;foo bar="1"&gt;baz&lt;/foo&gt;}</p>
 *
 * <p>Closing an element is optional; it is automatically closed when another event is applied
 * to its parent container, or when the parent container is closed.</p>
 */

public interface Element extends Container {
    /**
     * Add an attribute to the current element, supplying its name as a QName.
     *
     * <p>The level of validation applied to the supplied names is implementation-defined.</p>
     *
     * <p>This method call is allowed in state {@code START_TAG}, and it leaves the state unchanged.</p>
     *
     * @param name  The name of the attribute, as a QName (which may contain
     *              prefix, namespace URI, and local name). The prefix and namespace URI
     *              must either both be empty, or both be non-empty.
     *
     *              <p>A namespace declaration binding the prefix to the namespace URI
     *              will be generated unless it is redundant. An exception is thrown
     *              if the binding is incompatible with other bindings already established
     *              for the same prefix.</p>
     * @param value The value of the attribute. If the value is null, then no attribute is written.
     * @return the Element to which the method is applied. This is to allow chained method calls, of the form
     * {@code element("a").attribute("x", "1").attribute("y", "2")}
     * @throws SaxonApiException if the specified constraints are violated, or if the implementation
     *                           detects any problems
     */

    Element attribute(QName name, String value) throws SaxonApiException;

    /**
     * Add an attribute to the current element, supplying its name as a string.
     *
     * <p>The level of validation applied to the supplied name is implementation-defined.</p>
     *
     * <p>This method call is allowed in state {@code START_TAG}, and it leaves the state unchanged.</p>
     *
     * @param name  The name of the attribute, as a string. The attribute will be in no namespace.
     * @param value The value of the attribute. If the value is null, then no attribute is written.
     * @return the Tag to which the method is applied. This is to allow chained method calls, of the form
     * {@code tag.element("a").attribute("x", "1").attribute("y", "2")}
     * @throws SaxonApiException if the specified constraints are violated, or if the implementation
     *                           detects any problems
     */

    Element attribute(String name, String value) throws SaxonApiException;

    /**
     * Add an namespace binding to the current element.
     *
     * <p>It is never necessary to use this call to establish bindings for prefixes used in
     * element or attribute names; it is needed only when there is a requirement to declare
     * a namespace for use in other contexts, for example in the value of an {@code xsi:type}
     * attribute.</p>
     *
     * <p>This method is not used to declare a default namespace; that is done using
     * {@link #setDefaultNamespace(String)}.</p>
     *
     * <p>The level of validation applied to the supplied names is implementation-defined.</p>
     *
     * <p>This method call is allowed in state {@code START_TAG}, and it leaves the state unchanged.</p>
     *
     * <p>An exception is thrown if the binding is incompatible with other bindings already established
     * on the current element for the same prefix, including any binding established using
     * {@link #setDefaultNamespace(String)}</p>
     *
     * @param prefix The namespace prefix. This must not be a zero-length string.
     * @param uri    The namespace URI. This must not be a zero-length string.
     * @return the Tag to which the method is applied. This is to allow chained method calls, of the form
     * {@code tag.element("a").namespace("xs", XS_SCHEMA).attribute("type", "xs:string")}
     * @throws SaxonApiException if the specified constraints are violated, or if the implementation
     *                           detects any problems
     */

    Element namespace(String prefix, String uri) throws SaxonApiException;

    @Override
    Element text(CharSequence value) throws SaxonApiException;

    @Override
    Element comment(CharSequence value) throws SaxonApiException;

    @Override
    Element processingInstruction(String name, CharSequence value) throws SaxonApiException;
}

