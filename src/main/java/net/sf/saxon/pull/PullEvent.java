////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pull;

import net.sf.saxon.transpile.CSharpSimpleEnum;

/**
 * Enumeration of event types that can occur in the {@link PullProvider} interface
 */

@CSharpSimpleEnum
public enum PullEvent {
    /**
     * START_OF_INPUT is the initial state when the PullProvider is instantiated.
     * This event is never notified by the next() method, but it is returned
     * from a call of current() prior to the first call on next().
     */

    START_OF_INPUT,

    /**
     * ATOMIC_VALUE is notified when the PullProvider is reading a sequence of items,
     * and one of the items is an atomic value rather than a node. This will always
     * be a top-level event (it will never be nested in Start/End Document or
     * Start/End Element).
     */

    ATOMIC_VALUE,

    /**
     * START_DOCUMENT is notified when a document node is encountered. This will
     * always be a top-level event (it will never be nested in Start/End Document or
     * Start/End Element). Note however that multiple document nodes can occur in
     * a sequence, and the start and end of each one will be notified.
     */

    START_DOCUMENT,

    /**
     * END_DOCUMENT is notified at the end of processing a document node, that is,
     * after all the descendants of the document node have been notified. The event
     * will always be preceded by the corresponding START_DOCUMENT event.
     */

    END_DOCUMENT,

    /**
     * START_ELEMENT is notified when an element node is encountered. This may either
     * be a top-level element (an element node that participates in the sequence being
     * read in its own right) or a nested element (reported because it is a descendant
     * of an element or document node that participates in the sequence.)
     * <p>Following the notification of START_ELEMENT, the client may obtain information
     * about the element node, such as its name and type annotation. The client may also
     * call getAttributes() to obtain information about the attributes of the element
     * node, and/or getNamespaceDeclarations() to get information about the namespace
     * declarations. The client may then do one of the following:</p>
     * <ul>
     * <li>Call skipToMatchingEnd() to move straight to the corresponding END_ELEMENT event (which
     * will then be the current event)</li>
     * <li>Call next(), repeatedly, to be notified of events relating to the children and
     * descendants of this element node</li>
     * <li>Call getStringValue() to obtain the string value of the element node, after which
     * the next event notified will be the corresponding END_ELEMENT event</li>
     * <li>Call getTypedValue() to obtain the typed value of the element node, after which
     * the next event notified will be the corresponding END_ELEMENT event</li>
     * </ul>
     */

    START_ELEMENT,

    /**
     * END_ELEMENT is notified at the end of an element node, that is, after all the children
     * and descendants of the element have either been processed or skipped. It may relate to
     * a top-level element, or to a nested element. For an empty element (one with no children)
     * the END_ELEMENT event will immediately follow the corresponding START_ELEMENT event.
     * No information (such as the element name) is available after an END_ELEMENT event: if the
     * client requires such information, it must remember it, typically on a Stack.
     */

    END_ELEMENT,

    /**
     * The ATTRIBUTE event is notified only for an attribute node that appears in its own right
     * as a top-level item in the sequence being read. ATTRIBUTE events are not notified for
     * the attributes of an element that has been notified: such attributes must be read using the
     * {@link PullProvider#getAttributes()} method.
     */

    ATTRIBUTE,

    /**
     * The NAMESPACE event is notified only for a namespace node that appears in its own right
     * as a top-level item in the sequence being read. NAMESPACE events are not notified for
     * the namespaces of an element that has been notified: such attributes must be read using the
     * {@link PullProvider#getNamespaceDeclarations()} method.
     */

    NAMESPACE,

    /**
     * A TEXT event is notified for a text node. This may either be a top-level text
     * node, or a text node nested within an element or document node. At the top level,
     * text nodes may be zero-length and may be consecutive in the sequence being read.
     * Nested within an element or document node, text nodes will never be zero-length,
     * and adjacent text nodes will have been coalesced into one. (This might not always
     * be true when reading third-party data models such as a DOM.) Whitespace-only
     * text nodes will be notified unless something has been done (e.g. xsl:strip-space)
     * to remove them.
     */

    TEXT,

    /**
     * A COMMENT event is notified for a comment node, which may be either a top-level
     * comment or one nested within an element or document node.
     */

    COMMENT,

    /**
     * A PROCESSING_INSTRUCTION event is notified for a processing instruction node,
     * which may be either a top-level comment or one nested within an element or document node.
     * As defined in the XPath data model, the "target" of a processing instruction is represented
     * as the node name (which only has a local part, no prefix or URI), and the "data" of the
     * processing instruction is represented as the string-value of the node.
     */

    PROCESSING_INSTRUCTION,

    /**
     * The END_OF_INPUT event is returned to indicate the end of the sequence being read.
     * After this event, the result of any further calls on the next() method is undefined.
     */

    END_OF_INPUT,

    /**
     * The WORK_IN_PROGRESS event should never be returned to the caller; it represents
     * an intermediate state during parsing that is not yet ready for delivery to the
     * application
     */

    WORK_IN_PROGRESS
}
