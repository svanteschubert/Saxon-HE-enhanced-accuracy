////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

/**
 * An interface designed for applications to generate XML documents by issuing events. Functionally
 * similar to the SAX {@link org.xml.sax.ContentHandler} or the Stax {@link javax.xml.stream.XMLStreamWriter},
 * it is designed eliminate the usability problems and ambiguities in those specifications.
 *
 * <p>The {@code Push} interface can be used to create a single tree rooted at a document node.
 * It is possible to constrain the document node to be well-formed (in which case it must have
 * a single element node child, plus optionally comment and processing instruction children).
 * Some implementations may only accept well-formed documents.</p>
 *
 * <p>The document created using the {@code Push} interface is set to the {@link Destination}
 * defined when {@code Push} is created using the factory method {@link Processor#newPush(Destination)}.
 * The {@link Destination} will commonly be an {@link XdmDestination} or a {@link Serializer},
 * but it could also be, for example, an {@link XsltTransformer} or an {@link SchemaValidator}.</p>
 *
 * <p>Here is an example of application code written to construct a simple XML document:</p>
 *
 * <pre>{@code
 * Push.Document doc = processor.newPush(destination).document(true);
 * doc.setDefaultNamespace("http://www.example.org/ns");
 * Push.Element top = doc.element("root");
 * top.attribute("version", "1.5");
 * for (Employee emp : employees) {
 *     top.element("emp")
 *        .attribute("ssn", emp.getSSN())
 *        .text(emp.getName());
 * }
 * doc.close();
 * }</pre>
 */

public interface Push {

    /**
     * Start an XML document.
     *
     * @param wellFormed Set to true if the document is required to be well-formed;
     *                   set to false if there is no such requirement. A well-formed
     *                   document must have as its children exactly one element node
     *                   plus optionally, any number of comment and processing instruction
     *                   nodes (no text nodes are allowed); any attempt to construct a node
     *                   sequence that does not follow these rules will result in an exception.
     *                   If the document is not required to be well-formed, the children
     *                   of the document node may comprise any sequence of element, text,
     *                   comment, and processing instruction nodes.
     * @return a Doc object which may be used to add content to the document, or to
     * close the document when it has been fully written.
     * @throws SaxonApiException if the specified constraints are violated, or if the
     * implementation detects any problems
     */

    Document document(boolean wellFormed) throws SaxonApiException;

    /**
     * The {@code Container} interface represents a document node or element node
     * under construction; there are concrete subclasses representing document nodes
     * and element nodes respectively.
     */

    interface Container {

        /**
         * Set the default namespace for a section of the document. This applies to
         * all subsequent {@link #element(String)} or #element(QName)} calls, and
         * is inherited by inner elements unless overridden by another call that
         * sets a different value.
         *
         * <p>Setting the default namespace has the effect that within its scope,
         * on any call on {@link #element(String)}, the supplied string is taken
         * as being a local name in the current default namespace. A default namespace
         * declaration in the form <code>xmlns="uri"</code> will appear on any element
         * where it is not redundant.</p>
         *
         * <p>If the method is called repeatedly on the same {@code Container},
         * then the most recent call applies.</p>
         *
         * @param uri the namespace URI to be used as the default namespace for the
         *            subsequent elements. The value may be a zero-length string or null, indicating
         *            that the default within the scope of this call is for elements
         *            to be in no namespace.
         */

        void setDefaultNamespace(String uri);

        /**
         * Start an element node, with a specified prefix, namespace URI, and local name.
         *
         * <p>The level of validation applied to the supplied names is implementation-defined.</p>
         *
         * @param name The name of the element, as a non-null QName (which may contain
         *             prefix, namespace URI, and local name). An empty string
         *             as the prefix represents the default namespace; an empty
         *             string as the namespace URI represents no namespace. If the
         *             namespace is empty then the prefix must also be empty.
         *
         *             <p>A namespace declaration binding the prefix to the namespace URI
         *             will be generated unless it is redundant.</p>
         *
         *             <p>If the prefix is empty and the namespace is not the default
         *             namespace established using {@link #setDefaultNamespace(String)},
         *             then the prefix will be substituted with a system-allocated prefix.
         *             The prefix and namespace URI used on this method call do not affect
         *             the namespace context for any elements other than this one.</p>
         * @return a new {@code Tag} representing the new element node
         * @throws SaxonApiException if the specified constraints are violated,
         *                           or if the implementation detects any problems
         */

        Element element(QName name) throws SaxonApiException;

        /**
         * Start an element node, with a specified local name. The element will be in the default
         * namespace if one has been established; otherwise it will be in no namespace.
         *
         * <p>The level of validation applied to the supplied name is implementation-defined.</p>
         *
         * @param name The local name of the element, as a non-null string. If a default
         *             namespace has been established by a call on {@link #setDefaultNamespace(String)}
         *             then the element will be in that namespace; otherwise it will be in no namespace.
         * @return a new {@code Tag} representing the new element node
         * @throws SaxonApiException if the specified constraints are violated, or if the implementation
         *                           detects any problems
         */

        Element element(String name) throws SaxonApiException;

        /**
         * Add text content to the current element node (or, in the case of a non-well-formed document,
         * as a child of the document node).
         *
         * <p>Multiple consecutive calls on {@code text()} generate a single text node with concatenated
         * content: that is, {@code text("one).text("two")} is equivalent to {@code text("onetwo")}.</p>
         *
         * @param value the content of the text node. Supplying a zero-length string or null is permitted,
         *              but has no effect.
         * @return the Container to which the method is applied. This is to allow chained method calls, of the form
         * {@code tag.element("a").text("content").close()}
         * @throws SaxonApiException if the specified constraints are violated, or if the implementation
         *                           detects any problems
         */

        Container text(CharSequence value) throws SaxonApiException;

        /**
         * Add a comment node to the current element or document node.
         *
         * <p>The method call is allowed in states {@code START_TAG}, {@code CONTENT}, and
         * {@code NON_TEXT_CONTENT}, and it sets the state to {@code CONTENT}.</p>
         *
         * @param value the content of the comment node. The value should not contain the string "--";
         *              it is implementation-defined whether this causes an exception, or whether some
         *              recovery action is taken such as replacing the string by "- -". If the value
         *              is null, no comment node is written.
         * @return the Container to which the method is applied. This is to allow chained method calls, of the form
         * {@code tag.element("a").comment("optional").close()}
         * @throws SaxonApiException if the specified constraints are violated, or if the implementation
         *                           detects any problems
         */

        Container comment(CharSequence value) throws SaxonApiException;

        /**
         * Add a processing instruction node to the current element or document node.
         *
         * <p>The method call is allowed in states {@code START_TAG}, {@code CONTENT}, and
         * {@code NON_TEXT_CONTENT}, and it sets the state to {@code CONTENT}.</p>
         *
         * @param name  the name ("target") of the processing instruction. The level of validation applied
         *              to the supplied name is implementation-defined. Must not be null.
         * @param value the content ("data") of the processing instruction node.
         *              The value should not contain the string {@code "?>"};
         *              it is implementation-defined whether this causes an exception, or whether some
         *              recovery action is taken such as replacing the string by {@code "? >"}. If the value
         *              is null, no processing instruction node is written.
         * @return the Container to which the method is applied. This is to allow chained method calls, of the form
         * {@code tag.element("a").processing-instruction("target", "data").close()}
         * @throws SaxonApiException if the specified constraints are violated, or if the implementation
         *                           detects any problems
         */

        Container processingInstruction(String name, CharSequence value) throws SaxonApiException;

        /**
         * Close the current document or element container.
         *
         * <p>Closing a container more than once has no effect.</p>
         *
         * <p>Adding any content to a node after it has been closed causes an exception.</p>
         *
         * <p>Closing a node implicitly closes any unclosed children of the node.</p>
         *
         * @throws SaxonApiException if a downstream recipient of the data reports a failure
         */

        void close() throws SaxonApiException;

    }

    /**
     * A {@link net.sf.saxon.s9api.Push.Container} representing a document node.
     *
     * <p>If the document is constrained to be well-formed then the permitted sequence of events is
     * {@code (COMMENT | PI)* ELEMENT (COMMENT | PI)* CLOSE}.</p>
     *
     * <p>If the document is NOT constrained to be well-formed then the permitted sequence of events is
     * {@code (COMMENT | PI | TEXT | ELEMENT)* CLOSE}.</p>
     */

    interface Document extends Container {

        @Override
        Document text(CharSequence value) throws SaxonApiException;

        @Override
        Document comment(CharSequence value) throws SaxonApiException;

        @Override
        Document processingInstruction(String name, CharSequence value) throws SaxonApiException;
    }

    /**
     * A {@link net.sf.saxon.s9api.Push.Container} representing an element node.
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

    interface Element extends Container {
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
         * @return the Tag to which the method is applied. This is to allow chained method calls, of the form
         * {@code tag.element("a").attribute("x", "1").attribute("y", "2")}
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
}

