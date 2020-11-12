////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.CharSequenceConsumer;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.value.StringValue;

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
 */

public abstract class Outputter implements Receiver {

    protected PipelineConfiguration pipelineConfiguration;
    protected String systemId = null;

    /**
     * Set the pipeline configuration
     *
     * @param pipe the pipeline configuration
     */

    @Override
    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        this.pipelineConfiguration = pipe;
    }

    /**
     * Get the pipeline configuration
     *
     * @return the pipeline configuration
     */

    /*@NotNull*/
    @Override
    public PipelineConfiguration getPipelineConfiguration() {
        return pipelineConfiguration;
    }

    /**
     * Get the Saxon Configuration
     * @return the configuration
     */

    public final Configuration getConfiguration() {
        return pipelineConfiguration.getConfiguration();
    }

    /**
     * Set the System ID of the tree represented by this event stream
     *
     * @param systemId the system ID (which is used as the base URI of the nodes
     *                 if there is no xml:base attribute)
     */

    @Override
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Get the system ID
     *
     * @return the system ID that was supplied using the setSystemId() method
     */

    /*@Nullable*/
    @Override
    public String getSystemId() {
        return systemId;
    }


    /**
     * Notify the start of the event stream
     *
     * @throws XPathException if an error occurs
     */

    @Override
    public void open() throws XPathException {}

    /**
     * Notify the start of a document node
     *
     * @param properties bit-significant integer indicating properties of the document node.
     *                   The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */

    @Override
    abstract public void startDocument(int properties) throws XPathException;

    /**
     * Notify the end of a document node
     *
     * @throws XPathException if an error occurs
     */

    @Override
    abstract public void endDocument() throws XPathException;

    /**
     * Notify an unparsed entity URI.
     *
     * @param name     The name of the unparsed entity
     * @param systemID The system identifier of the unparsed entity
     * @param publicID The identifier of the unparsed entity
     * @throws XPathException if an error occurs
     */

    @Override
    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {}

    /**
     * Notify the start of an element. This version of <code>startElement()</code> must be followed
     * by calls on {@link #attribute(NodeName, SimpleType, CharSequence, Location, int)} and
     * {@link #namespace(String, String, int)} to supply the attributes and namespaces; these calls
     * may be terminated by a call on {@link #startContent()} but this is not mandatory.
     *
     * @param elemName   the name of the element.
     * @param typeCode   the type annotation of the element.
     * @param location   an object providing information about the module, line, and column where the node originated
     * @param properties bit-significant properties of the element node. If there are no relevant
     *                   properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */

    abstract public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties)
            throws XPathException;

    /**
     * Notify the start of an element, supplying all attributes and namespaces
     *
     * @param elemName   the name of the element.
     * @param type       the type annotation of the element.
     * @param attributes the attributes of this element
     * @param namespaces the in-scope namespaces of this element: generally this is all the in-scope
     *                   namespaces, without relying on inheriting namespaces from parent elements
     * @param location   an object providing information about the module, line, and column where the node originated
     * @param properties bit-significant properties of the element node. If there are no relevant
     *                   properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */
    @Override
    public void startElement(NodeName elemName, SchemaType type, AttributeMap attributes, NamespaceMap namespaces, Location location, int properties) throws XPathException {
        // This is a default implementation which is not particularly efficient. An Outputter that feeds
        // directly into a Receiver should try to avoid decomposing the attributes and namespaces.
        startElement(elemName, type, location, properties);
        for (NamespaceBinding ns : namespaces) {
            namespace(ns.getPrefix(), ns.getURI(), properties);
        }
        for (AttributeInfo att : attributes) {
            attribute(att.getNodeName(), att.getType(), att.getValue(), att.getLocation(), att.getProperties());
        }
        startContent();
    }

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

    abstract public void namespace(String prefix, String namespaceUri, int properties) throws XPathException;

    /**
     * Output a set of namespace bindings. This should have the same effect as outputting the
     * namespace bindings individually using {@link #namespace(String, String, int)}, but it
     * may be more efficient. It is used only when copying an element node together with
     * all its namespaces, so less checking is needed that the namespaces form a consistent
     * and complete set
     * @param bindings the set of namespace bindings
     * @param properties any special properties. The property {@link ReceiverOption#NAMESPACE_OK}
     *                   means that no checking is needed.
     * @throws XPathException if any failure occurs
     */

    public void namespaces(NamespaceBindingSet bindings, int properties) throws XPathException {
        // Optimized in ComplexContentOutputter subclass
        for (NamespaceBinding nb: bindings) {
            namespace(nb.getPrefix(), nb.getURI(), properties);
        }
    }

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

    abstract public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location location, int properties)
            throws XPathException;

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial Outputter of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     *
     * @throws XPathException if an error occurs
     */

    public void startContent() throws XPathException {}

    /**
     * Notify the end of an element. The Outputter must maintain a stack if it needs to know which
     * element is ending.
     *
     * @throws XPathException if an error occurs
     */

    @Override
    abstract public void endElement() throws XPathException;

    /**
     * Notify character data. Note that some receivers may require the character data to be
     * sent in a single event, but in general this is not a requirement.
     *
     * @param chars      The characters
     * @param location  provides information such as line number and system ID.
     * @param properties Bit significant value. The following bits are defined:
     *                   <dl>
     *                   <dt>DISABLE_ESCAPING</dt>           <dd>Disable escaping for this text node</dd>
     *                   <dt>USE_CDATA</dt>                  <dd>Output as a CDATA section</dd>
     *                   </dl>
     * @throws XPathException if an error occurs
     */

    @Override
    abstract public void characters(CharSequence chars, Location location, int properties)
            throws XPathException;

    /**
     * Output a processing instruction
     *
     * @param name       The PI name. This must be a legal name (it will not be checked).
     * @param data       The data portion of the processing instruction
     * @param location   provides information such as line number and system ID.
     * @param properties Additional information about the PI.
     * @throws IllegalArgumentException the content is invalid for an XML processing instruction
     * @throws XPathException            if an error occurs
     */

    @Override
    abstract public void processingInstruction(String name, CharSequence data, Location location, int properties)
            throws XPathException;

    /**
     * Notify a comment. Comments are only notified if they are outside the DTD.
     *
     * @param content    The content of the comment
     * @param location  provides information such as line number and system ID.
     * @param properties Additional information about the comment.
     * @throws IllegalArgumentException  the content is invalid for an XML comment
     * @throws XPathException            if an error occurs
     */

    @Override
    abstract public void comment(CharSequence content, Location location, int properties) throws XPathException;

    /**
     * Append an arbitrary item (node, atomic value, or function) to the output. The default
     * implementation throws {@code UnsupportedOperationException}.
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param properties if the item is an element node, this indicates whether its namespaces
*                       need to be copied. Values are {@link ReceiverOption#ALL_NAMESPACES}; the default (0) means
     */

     @Override
     public void append(Item item, Location locationId, int properties) throws XPathException {
         throw new UnsupportedOperationException();
     }

    /**
     * Append an arbitrary item (node, atomic value, or function) to the output.
     * By default, if the item is an element node, it is copied with all namespaces.
     * The default implementation calls {@link #append(Item, Location, int)}, whose
     * default implementation throws {@code UnsupportedOperationException}
     *
     * @param item the item to be appended
     * @throws net.sf.saxon.trans.XPathException if the operation fails
     */

    @Override
    public void append(Item item) throws XPathException {
        append(item, Loc.NONE, ReceiverOption.ALL_NAMESPACES);
    }

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

    public CharSequenceConsumer getStringReceiver(boolean asTextNode) {
        return new CharSequenceConsumer() {

            FastStringBuffer buffer = new FastStringBuffer(256);
            @Override
            public CharSequenceConsumer cat(CharSequence chars) {
                return buffer.cat(chars);
            }

            @Override
            public CharSequenceConsumer cat(char c) {
                return buffer.cat(c);
            }

            @Override
            public void close() throws XPathException {
                if (asTextNode) {
                    Outputter.this.characters(buffer, Loc.NONE, ReceiverOption.NONE);
                } else {
                    Outputter.this.append(new StringValue(buffer.condense()));
                }
            }
        };
    }

    /**
     * Notify the end of the event stream
     *
     * @throws XPathException if an error occurs
     */

    @Override
    public void close() throws XPathException {}

    /**
     * Ask whether this Outputter (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events. The default implementation of this method
     * returns false.
     *
     * @return true if the Outputter makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation (or conversely, it may
     *         avoid stripping unwanted type annotations)
     */

    @Override
    public boolean usesTypeAnnotations() {
        return false;
    }


}

