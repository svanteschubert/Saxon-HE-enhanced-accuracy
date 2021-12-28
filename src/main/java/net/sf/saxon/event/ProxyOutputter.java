////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.AttributeMap;
import net.sf.saxon.om.NamespaceMap;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;

public class ProxyOutputter extends Outputter {

    private Outputter next;

    public ProxyOutputter(Outputter next) {
        this.next = next;
        setPipelineConfiguration(next.getPipelineConfiguration());
        setSystemId(next.getSystemId());
    }

    public Outputter getNextOutputter() {
        return next;
    }

    /**
     * Notify the start of the event stream
     *
     * @throws XPathException if an error occurs
     */
    @Override
    public void open() throws XPathException {
        next.open();
    }

    /**
     * Notify the start of a document node
     *
     * @param properties bit-significant integer indicating properties of the document node.
     *                   The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */
    @Override
    public void startDocument(int properties) throws XPathException {
        next.startDocument(properties);
    }

    /**
     * Notify the end of a document node
     *
     * @throws XPathException if an error occurs
     */
    @Override
    public void endDocument() throws XPathException {
        next.endDocument();
    }

    /**
     * Notify an unparsed entity URI.
     *
     * @param name     The name of the unparsed entity
     * @param systemID The system identifier of the unparsed entity
     * @param publicID The identifier of the unparsed entity
     * @throws XPathException if an error occurs
     */
    @Override
    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
        next.setUnparsedEntity(name, systemID, publicID);
    }

    /**
     * Notify the start of an element
     *
     * @param elemName   the name of the element.
     * @param typeCode   the type annotation of the element.
     * @param location   an object providing information about the module, line, and column where the node originated
     * @param properties bit-significant properties of the element node. If there are no relevant
     *                   properties, zero is supplied. The definitions of the bits are in class {@link ReceiverOption}
     * @throws XPathException if an error occurs
     */
    @Override
    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {
        next.startElement(elemName, typeCode, location, properties);
    }

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
        next.startElement(elemName, type, attributes, namespaces, location, properties);
    }

    /**
     * Notify a namespace binding. This method is called at some point after startElement().
     * The semantics are similar to the xsl:namespace instruction in XSLT, or the namespace
     * node constructor in XQuery.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param prefix       The namespace prefix; zero-length string for the default namespace
     * @param namespaceUri The namespace URI. In some cases a zero-length string may be used to
     *                     indicate a namespace undeclaration.
     * @param properties   The REJECT_DUPLICATES property: if set, the
     *                     namespace declaration will be rejected if it conflicts with a previous declaration of the same
     *                     prefix. If the property is not set, the namespace declaration will be ignored if it conflicts
     *                     with a previous declaration. This reflects the fact that when copying a tree, namespaces for child
     *                     elements are emitted before the namespaces of their parent element. Unfortunately this conflicts
     *                     with the XSLT rule for complex content construction, where the recovery action in the event of
     *                     conflicts is to take the namespace that comes last. XSLT therefore doesn't recover from this error:
     * @throws XPathException if an error occurs
     * @since changed in 10.0 to report all the in-scope namespaces for an element, and to do so
     * in a single call.
     */
    @Override
    public void namespace(String prefix, String namespaceUri, int properties) throws XPathException {
        next.namespace(prefix, namespaceUri, properties);
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * @param attName    The name of the attribute
     * @param typeCode   The type annotation of the attribute
     * @param value      the string value of the attribute
     * @param location   provides information such as line number and system ID.
     * @param properties Bit significant value. The following bits are defined:
     *                   <dl>
     *                   <dt>DISABLE_ESCAPING</dt>    <dd>Disable escaping for this attribute</dd>
     *                   <dt>NO_SPECIAL_CHARACTERS</dt>      <dd>Attribute value contains no special characters</dd>
     *                   </dl>
     * @throws IllegalStateException  attempt to output an attribute when there is no open element
     *                                start tag
     * @throws XPathException         if an error occurs
     */
    @Override
    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location location, int properties) throws XPathException {
        next.attribute(attName, typeCode, value, location, properties);
    }

    /**
     * Notify the start of the content, that is, the completion of all attributes and namespaces.
     * Note that the initial Outputter of output from XSLT instructions will not receive this event,
     * it has to detect it itself. Note that this event is reported for every element even if it has
     * no attributes, no namespaces, and no content.
     *
     * @throws XPathException if an error occurs
     */
    @Override
    public void startContent() throws XPathException {
        next.startContent();
    }

    /**
     * Notify the end of an element. The Outputter must maintain a stack if it needs to know which
     * element is ending.
     *
     * @throws XPathException if an error occurs
     */
    @Override
    public void endElement() throws XPathException {
        next.endElement();
    }

    /**
     * Notify character data. Note that some receivers may require the character data to be
     * sent in a single event, but in general this is not a requirement.
     *
     * @param chars      The characters
     * @param location   provides information such as line number and system ID.
     * @param properties Bit significant value. The following bits are defined:
     *                   <dl>
     *                   <dt>DISABLE_ESCAPING</dt>           <dd>Disable escaping for this text node</dd>
     *                   <dt>USE_CDATA</dt>                  <dd>Output as a CDATA section</dd>
     *                   </dl>
     * @throws XPathException if an error occurs
     */
    @Override
    public void characters(CharSequence chars, Location location, int properties) throws XPathException {
        next.characters(chars, location, properties);
    }

    /**
     * Output a processing instruction
     *
     * @param name       The PI name. This must be a legal name (it will not be checked).
     * @param data       The data portion of the processing instruction
     * @param location   provides information such as line number and system ID.
     * @param properties Additional information about the PI.
     * @throws IllegalArgumentException  the content is invalid for an XML processing instruction
     * @throws XPathException            if an error occurs
     */
    @Override
    public void processingInstruction(String name, CharSequence data, Location location, int properties) throws XPathException {
        next.processingInstruction(name, data, location, properties);
    }

    /**
     * Notify a comment. Comments are only notified if they are outside the DTD.
     *
     * @param content    The content of the comment
     * @param location   provides information such as line number and system ID.
     * @param properties Additional information about the comment.
     * @throws IllegalArgumentException  the content is invalid for an XML comment
     * @throws XPathException            if an error occurs
     */
    @Override
    public void comment(CharSequence content, Location location, int properties) throws XPathException {
        next.comment(content, location, properties);
    }

    /**
     * Append an arbitrary item (node, atomic value, or function) to the output. The default
     * implementation throws {@code UnsupportedOperationException}.
     *
     * @param item       the item to be appended
     * @param locationId the location of the calling instruction, for diagnostics
     * @param properties if the item is an element node, this indicates whether its namespaces
     *                   need to be copied. Values are {@link ReceiverOption#ALL_NAMESPACES}; the default (0) means
     */
    @Override
    public void append(Item item, Location locationId, int properties) throws XPathException {
        next.append(item, locationId, properties);
    }

    /**
     * Append an arbitrary item (node, atomic value, or function) to the output.
     * By default, if the item is an element node, it is copied with all namespaces.
     * The default implementation calls {@link #append(Item, Location, int)}, whose
     * default implementation throws {@code UnsupportedOperationException}
     *
     * @param item the item to be appended
     * @throws XPathException if the operation fails
     */
    @Override
    public void append(Item item) throws XPathException {
        next.append(item);
    }

    /**
     * Notify the end of the event stream
     *
     * @throws XPathException if an error occurs
     */
    @Override
    public void close() throws XPathException {
        next.close();
    }

    /**
     * Ask whether this Outputter (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events. The default implementation of this method
     * returns false.
     *
     * @return true if the Outputter makes any use of this information. If false, the caller
     * may supply untyped nodes instead of supplying the type annotation (or conversely, it may
     * avoid stripping unwanted type annotations)
     */
    @Override
    public boolean usesTypeAnnotations() {
        return next.usesTypeAnnotations();
    }

}

