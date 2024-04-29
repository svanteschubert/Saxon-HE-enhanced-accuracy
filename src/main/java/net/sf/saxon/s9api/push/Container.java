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
 * The {@code Container} interface represents a document node or element node
 * under construction; there are concrete subclasses representing document nodes
 * and element nodes respectively.
 */

public interface Container {

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

