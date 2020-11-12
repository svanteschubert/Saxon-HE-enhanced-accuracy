////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;
import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is an implementation of the DOM Document class that wraps a Saxon DocumentInfo
 * representation of a document node.
 */

public class DocumentOverNodeInfo extends NodeOverNodeInfo implements Document {

    /**
     * Get the Document Type Declaration (see <code>DocumentType</code> )
     * associated with this document. For HTML documents as well as XML
     * documents without a document type declaration this returns
     * <code>null</code>. DOM method.
     *
     * @return null: The Saxon tree model does not include the document type
     *         information.
     */

    @Override
    public DocumentType getDoctype() {
        return null;
    }

    /**
     * Get a <code>DOMImplementation</code> object that handles this document.
     * A DOM application may use objects from multiple implementations.
     * DOM method.
     */

    @Override
    public DOMImplementation getImplementation() {
        return new DOMImplementationImpl();
    }

    /**
     * Creates an element of the type specified. DOM method: always fails,
     * because the Saxon tree is not updateable.
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    @Override
    public Element createElement(String tagName) throws DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Creates an empty <code>DocumentFragment</code> object.
     *
     * @return A new <code>DocumentFragment</code> .
     *         DOM method: returns null, because the Saxon tree is not updateable.
     */

    @Override
    public DocumentFragment createDocumentFragment() {
        return null;
    }

    /**
     * Create a <code>Text</code> node given the specified string.
     * DOM method: returns null, because the Saxon tree is not updateable.
     *
     * @param data The data for the node.
     * @return The new <code>Text</code> object.
     */

    @Override
    public Text createTextNode(String data) {
        return null;
    }

    /**
     * Create a <code>Comment</code> node given the specified string.
     * DOM method: returns null, because the Saxon tree is not updateable.
     *
     * @param data The data for the node.
     * @return The new <code>Comment</code> object.
     */
    @Override
    public Comment createComment(String data) {
        return null;
    }

    /**
     * Create a <code>CDATASection</code> node whose value  is the specified
     * string.
     * DOM method: always fails, because the Saxon tree is not updateable.
     *
     * @param data The data for the <code>CDATASection</code> contents.
     * @return The new <code>CDATASection</code> object.
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    @Override
    public CDATASection createCDATASection(String data) throws org.w3c.dom.DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Create a <code>ProcessingInstruction</code> node given the specified
     * name and data strings.
     * DOM method: returns null, because the Saxon tree is not updateable.
     *
     * @param target The target part of the processing instruction.
     * @param data   The data for the node.
     * @return The new <code>ProcessingInstruction</code> object.
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    @Override
    public ProcessingInstruction createProcessingInstruction(String target, String data)
            throws org.w3c.dom.DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Create an <code>Attr</code> of the given name.
     * DOM method: always fails, because the Saxon tree is not updateable.
     *
     * @param name The name of the attribute.
     * @return A new <code>Attr</code> object with the <code>nodeName</code>
     *         attribute set to <code>name</code> , and <code>localName</code> ,
     *         <code>prefix</code> , and <code>namespaceURI</code> set to
     *         <code>null</code> .
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    @Override
    public Attr createAttribute(String name) throws org.w3c.dom.DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Create an <code>EntityReference</code> object.
     * DOM method: returns null, because the Saxon tree is not updateable.
     *
     * @param name The name of the entity to reference.
     * @return The new <code>EntityReference</code> object.
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    @Override
    public EntityReference createEntityReference(String name) throws org.w3c.dom.DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * Return a <code>NodeList</code> of all the <code>Elements</code> with
     * a given tag name in the order in which they are encountered in a
     * preorder traversal of the <code>Document</code> tree.
     *
     * @param tagname The name of the tag to match on. The special value "*"
     *                matches all tags.
     * @return A new <code>NodeList</code> object containing all the matched
     *         <code>Elements</code> .
     */

    @Override
    public NodeList getElementsByTagName(String tagname) {
        return getElementsByTagName(node, tagname);
    }

    /**
     * Get the outermost element of a document.
     *
     * @return the Element for the outermost element of the document. If the document is
     *         not well-formed, this returns the first element child of the root if there is one, otherwise
     *         null.
     */

    @Override
    public Element getDocumentElement() {
        NodeInfo root = node.getRoot();
        if (root == null) {
            return null;
        }
        AxisIterator children =
                root.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        return (Element) wrap(children.next());
    }

    protected static NodeList getElementsByTagName(NodeInfo node, String tagname) {
        AxisIterator allElements = node.iterateAxis(AxisInfo.DESCENDANT);
        List<Node> nodes = new ArrayList<>(100);
        while (true) {
            NodeInfo next = allElements.next();
            if (next == null) {
                break;
            }
            if (next.getNodeKind() == Type.ELEMENT) {
                if (tagname.equals("*") || tagname.equals(next.getDisplayName())) {
                    nodes.add(NodeOverNodeInfo.wrap(next));
                }
            }
        }
        return new DOMNodeList(nodes);
    }


    /**
     * Import a node from another document to this document.
     * DOM method: always fails, because the Saxon tree is not updateable.
     *
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     * @since DOM Level 2
     */

    @Override
    public Node importNode(Node importedNode, boolean deep) throws UnsupportedOperationException {
        disallowUpdate();
        return null;
    }

    /**
     * Create an element of the given qualified name and namespace URI.
     * HTML-only DOM implementations do not need to implement this method.
     * DOM method: always fails, because the Saxon tree is not updateable.
     *
     * @param namespaceURI  The  namespace URI of the element to create.
     * @param qualifiedName The  qualified name of the element type to
     *                      instantiate.
     * @return A new <code>Element</code> object
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    @Override
    public Element createElementNS(String namespaceURI, String qualifiedName) throws UnsupportedOperationException {
        disallowUpdate();
        return null;
    }

    /**
     * Create an attribute of the given qualified name and namespace URI.
     * HTML-only DOM implementations do not need to implement this method.
     * DOM method: returns null, because the Saxon tree is not updateable.
     *
     * @param namespaceURI  The  namespace URI of the attribute to create.
     * @param qualifiedName The  qualified name of the attribute to
     *                      instantiate.
     * @return A new <code>Attr</code> object.
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */

    @Override
    public Attr createAttributeNS(String namespaceURI, String qualifiedName) throws UnsupportedOperationException {
        disallowUpdate();
        return null;
    }

    /**
     * Return a <code>NodeList</code> of all the <code>Elements</code> with
     * a given  local name and namespace URI in the order in which they are
     * encountered in a preorder traversal of the <code>Document</code> tree.
     * DOM method.
     *
     * @param namespaceURI The  namespace URI of the elements to match on.
     *                     The special value "*" matches all namespaces. The value null matches
     *                     elements not in any namespace
     * @param localName    The  local name of the elements to match on. The
     *                     special value "*" matches all local names.
     * @return A new <code>NodeList</code> object containing all the matched
     *         <code>Elements</code> .
     * @since DOM Level 2
     */

    @Override
    public NodeList getElementsByTagNameNS(String namespaceURI, String localName) {
        return getElementsByTagNameNS(node, namespaceURI, localName);
    }

    public static NodeList getElementsByTagNameNS(NodeInfo node, String namespaceURI, String localName) {
        String ns = namespaceURI == null ? "" : namespaceURI;
        AxisIterator allElements = node.iterateAxis(AxisInfo.DESCENDANT);
        List<Node> nodes = new ArrayList<>(100);
        while (true) {
            NodeInfo next = allElements.next();
            if (next == null) {
                break;
            }
            if (next.getNodeKind() == Type.ELEMENT) {
                if ((ns.equals("*") || ns.equals(next.getURI())) &&
                        (localName.equals("*") || localName.equals(next.getLocalPart()))) {
                    nodes.add(NodeOverNodeInfo.wrap(next));
                }
            }
        }
        return new DOMNodeList(nodes);
    }

    /**
     * Return the <code>Element</code> whose <code>ID</code> is given by
     * <code>elementId</code> . If no such element exists, returns
     * <code>null</code> . Behavior is not defined if more than one element
     * has this <code>ID</code> .  The DOM implementation must have
     * information that says which attributes are of type ID. Attributes with
     * the name "ID" are not of type ID unless so defined. Implementations
     * that do not know whether attributes are of type ID or not are expected
     * to return <code>null</code> .
     *
     * @param elementId The unique <code>id</code> value for an element.
     * @return The matching element, or null if there is none.
     * @since DOM Level 2
     */

    @Override
    public Element getElementById(String elementId) {
        // Defined on Document node; but we support it on any node.
        TreeInfo doc = node.getTreeInfo();
        if (doc == null) {
            return null;
        }
        return (Element) wrap(doc.selectID(elementId, false));
    }

    /**
     * An attribute specifying the encoding used for this document at the time
     * of the parsing. This is <code>null</code> when it is not known, such
     * as when the <code>Document</code> was created in memory.
     *
     * @since DOM Level 3
     */
    @Override
    public String getInputEncoding() {
        return null;
    }

    /**
     * An attribute specifying, as part of the
     * <a href='http://www.w3.org/TR/2004/REC-xml-20040204#NT-XMLDecl'>XML declaration</a>,
     * the encoding of this document. This is <code>null</code> when
     * unspecified or when it is not known, such as when the
     * <code>Document</code> was created in memory.
     *
     * @since DOM Level 3
     */
    @Override
    public String getXmlEncoding() {
        return null;
    }

    /**
     * An attribute specifying, as part of the
     * <a href='http://www.w3.org/TR/2004/REC-xml-20040204#NT-XMLDecl'>XML declaration</a>,
     * whether this document is standalone. This is <code>false</code> when
     * unspecified.
     * <p ><b>Note:</b>  No verification is done on the value when setting
     * this attribute. Applications should use
     * <code>Document.normalizeDocument()</code> with the "validate"
     * parameter to verify if the value matches the <a href='http://www.w3.org/TR/2004/REC-xml-20040204#sec-rmd'>validity
     * constraint for standalone document declaration</a> as defined in [<a href='http://www.w3.org/TR/2004/REC-xml-20040204'>XML 1.0</a>].
     *
     * @since DOM Level 3
     */
    @Override
    public boolean getXmlStandalone() {
        return false;
    }

    /**
     * An attribute specifying, as part of the <a href='http://www.w3.org/TR/2004/REC-xml-20040204#NT-XMLDecl'>XML declaration</a>, whether this document is standalone. This is <code>false</code> when
     * unspecified.
     * <p ><b>Note:</b>  No verification is done on the value when setting
     * this attribute. Applications should use
     * <code>Document.normalizeDocument()</code> with the "validate"
     * parameter to verify if the value matches the <a href='http://www.w3.org/TR/2004/REC-xml-20040204#sec-rmd'>validity
     * constraint for standalone document declaration</a> as defined in [<a href='http://www.w3.org/TR/2004/REC-xml-20040204'>XML 1.0</a>].
     *
     * @throws org.w3c.dom.DOMException NOT_SUPPORTED_ERR: Raised if this document does not support the
     *                                  "XML" feature.
     * @since DOM Level 3
     */
    @Override
    public void setXmlStandalone(boolean xmlStandalone) throws DOMException {
        disallowUpdate();
    }

    /**
     * An attribute specifying, as part of the <a href='http://www.w3.org/TR/2004/REC-xml-20040204#NT-XMLDecl'>XML declaration</a>, the version number of this document. If there is no declaration and if
     * this document supports the "XML" feature, the value is
     * <code>"1.0"</code>. If this document does not support the "XML"
     * feature, the value is always <code>null</code>. Changing this
     * attribute will affect methods that check for invalid characters in
     * XML names. Application should invoke
     * <code>Document.normalizeDocument()</code> in order to check for
     * invalid characters in the <code>Node</code>s that are already part of
     * this <code>Document</code>.
     * <br> DOM applications may use the
     * <code>DOMImplementation.hasFeature(feature, version)</code> method
     * with parameter values "XMLVersion" and "1.0" (respectively) to
     * determine if an implementation supports [<a href='http://www.w3.org/TR/2004/REC-xml-20040204'>XML 1.0</a>]. DOM
     * applications may use the same method with parameter values
     * "XMLVersion" and "1.1" (respectively) to determine if an
     * implementation supports [<a href='http://www.w3.org/TR/2004/REC-xml11-20040204/'>XML 1.1</a>]. In both
     * cases, in order to support XML, an implementation must also support
     * the "XML" feature defined in this specification. <code>Document</code>
     * objects supporting a version of the "XMLVersion" feature must not
     * raise a <code>NOT_SUPPORTED_ERR</code> exception for the same version
     * number when using <code>Document.xmlVersion</code>.
     *
     * @since DOM Level 3
     */
    @Override
    public String getXmlVersion() {
        return "1.0";
    }

    /**
     * An attribute specifying, as part of the <a href='http://www.w3.org/TR/2004/REC-xml-20040204#NT-XMLDecl'>XML declaration</a>, the version number of this document. If there is no declaration and if
     * this document supports the "XML" feature, the value is
     * <code>"1.0"</code>. If this document does not support the "XML"
     * feature, the value is always <code>null</code>. Changing this
     * attribute will affect methods that check for invalid characters in
     * XML names. Application should invoke
     * <code>Document.normalizeDocument()</code> in order to check for
     * invalid characters in the <code>Node</code>s that are already part of
     * this <code>Document</code>.
     * <br> DOM applications may use the
     * <code>DOMImplementation.hasFeature(feature, version)</code> method
     * with parameter values "XMLVersion" and "1.0" (respectively) to
     * determine if an implementation supports [<a href='http://www.w3.org/TR/2004/REC-xml-20040204'>XML 1.0</a>]. DOM
     * applications may use the same method with parameter values
     * "XMLVersion" and "1.1" (respectively) to determine if an
     * implementation supports [<a href='http://www.w3.org/TR/2004/REC-xml11-20040204/'>XML 1.1</a>]. In both
     * cases, in order to support XML, an implementation must also support
     * the "XML" feature defined in this specification. <code>Document</code>
     * objects supporting a version of the "XMLVersion" feature must not
     * raise a <code>NOT_SUPPORTED_ERR</code> exception for the same version
     * number when using <code>Document.xmlVersion</code>.
     *
     * @throws org.w3c.dom.DOMException NOT_SUPPORTED_ERR: Raised if the version is set to a value that is
     *                                  not supported by this <code>Document</code> or if this document
     *                                  does not support the "XML" feature.
     * @since DOM Level 3
     */
    @Override
    public void setXmlVersion(String xmlVersion) throws DOMException {
        disallowUpdate();
    }

    /**
     * An attribute specifying whether error checking is enforced or not. When
     * set to <code>false</code>, the implementation is free to not test
     * every possible error case normally defined on DOM operations, and not
     * raise any <code>DOMException</code> on DOM operations or report
     * errors while using <code>Document.normalizeDocument()</code>. In case
     * of error, the behavior is undefined. This attribute is
     * <code>true</code> by default.
     *
     * @since DOM Level 3
     */
    @Override
    public boolean getStrictErrorChecking() {
        return false;
    }

    /**
     * An attribute specifying whether error checking is enforced or not. When
     * set to <code>false</code>, the implementation is free to not test
     * every possible error case normally defined on DOM operations, and not
     * raise any <code>DOMException</code> on DOM operations or report
     * errors while using <code>Document.normalizeDocument()</code>. In case
     * of error, the behavior is undefined. This attribute is
     * <code>true</code> by default.
     *
     * @since DOM Level 3
     */
    @Override
    public void setStrictErrorChecking(boolean strictErrorChecking) {
        //no-op
    }

    /**
     * The location of the document or <code>null</code> if undefined or if
     * the <code>Document</code> was created using
     * <code>DOMImplementation.createDocument</code>. No lexical checking is
     * performed when setting this attribute; this could result in a
     * <code>null</code> value returned when using <code>Node.baseURI</code>
     * .
     * <br> Beware that when the <code>Document</code> supports the feature
     * "HTML" [<a href='http://www.w3.org/TR/2003/REC-DOM-Level-2-HTML-20030109'>DOM Level 2 HTML</a>]
     * , the href attribute of the HTML BASE element takes precedence over
     * this attribute when computing <code>Node.baseURI</code>.
     *
     * @since DOM Level 3
     */
    @Override
    public String getDocumentURI() {
        return node.getSystemId();
    }

    /**
     * The location of the document or <code>null</code> if undefined or if
     * the <code>Document</code> was created using
     * <code>DOMImplementation.createDocument</code>. No lexical checking is
     * performed when setting this attribute; this could result in a
     * <code>null</code> value returned when using <code>Node.baseURI</code>
     * .
     * <br> Beware that when the <code>Document</code> supports the feature
     * "HTML" [<a href='http://www.w3.org/TR/2003/REC-DOM-Level-2-HTML-20030109'>DOM Level 2 HTML</a>]
     * , the href attribute of the HTML BASE element takes precedence over
     * this attribute when computing <code>Node.baseURI</code>.
     *
     * @since DOM Level 3
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */
    @Override
    public void setDocumentURI(String documentURI) throws DOMException {
        disallowUpdate();
    }

    /**
     * Attempts to adopt a node from another document to this document. If
     * supported, it changes the <code>ownerDocument</code> of the source
     * node, its children, as well as the attached attribute nodes if there
     * are any. If the source node has a parent it is first removed from the
     * child list of its parent. This effectively allows moving a subtree
     * from one document to another (unlike <code>importNode()</code> which
     * create a copy of the source node instead of moving it). When it
     * fails, applications should use <code>Document.importNode()</code>
     * instead. Note that if the adopted node is already part of this
     * document (i.e. the source and target document are the same), this
     * method still has the effect of removing the source node from the
     * child list of its parent, if any. The following list describes the
     * specifics for each type of node.
     * <dl>
     * <dt>ATTRIBUTE_NODE</dt>
     * <dd>The
     * <code>ownerElement</code> attribute is set to <code>null</code> and
     * the <code>specified</code> flag is set to <code>true</code> on the
     * adopted <code>Attr</code>. The descendants of the source
     * <code>Attr</code> are recursively adopted.</dd>
     * <dt>DOCUMENT_FRAGMENT_NODE</dt>
     * <dd>The
     * descendants of the source node are recursively adopted.</dd>
     * <dt>DOCUMENT_NODE</dt>
     * <dd>
     * <code>Document</code> nodes cannot be adopted.</dd>
     * <dt>DOCUMENT_TYPE_NODE</dt>
     * <dd>
     * <code>DocumentType</code> nodes cannot be adopted.</dd>
     * <dt>ELEMENT_NODE</dt>
     * <dd><em>Specified</em> attribute nodes of the source element are adopted. Default attributes
     * are discarded, though if the document being adopted into defines
     * default attributes for this element name, those are assigned. The
     * descendants of the source element are recursively adopted.</dd>
     * <dt>ENTITY_NODE</dt>
     * <dd>
     * <code>Entity</code> nodes cannot be adopted.</dd>
     * <dt>ENTITY_REFERENCE_NODE</dt>
     * <dd>Only
     * the <code>EntityReference</code> node itself is adopted, the
     * descendants are discarded, since the source and destination documents
     * might have defined the entity differently. If the document being
     * imported into provides a definition for this entity name, its value
     * is assigned.</dd>
     * <dt>NOTATION_NODE</dt>
     * <dd><code>Notation</code> nodes cannot be
     * adopted.</dd>
     * <dt>PROCESSING_INSTRUCTION_NODE, TEXT_NODE, CDATA_SECTION_NODE,
     * COMMENT_NODE</dt>
     * <dd>These nodes can all be adopted. No specifics.</dd>
     * </dl>
     * <p><b>Note:</b>  Since it does not create new nodes unlike the
     * <code>Document.importNode()</code> method, this method does not raise
     * an <code>INVALID_CHARACTER_ERR</code> exception, and applications
     * should use the <code>Document.normalizeDocument()</code> method to
     * check if an imported name is not an XML name according to the XML
     * version in use.</p>
     *
     * @param source The node to move into this document.
     * @return The adopted node, or <code>null</code> if this operation
     *         fails, such as when the source node comes from a different
     *         implementation.
     * @throws org.w3c.dom.DOMException NOT_SUPPORTED_ERR: Raised if the source node is of type
     *                                  <code>DOCUMENT</code>, <code>DOCUMENT_TYPE</code>.
     *                                  <br>NO_MODIFICATION_ALLOWED_ERR: Raised when the source node is
     *                                  readonly.
     * @since DOM Level 3
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */
    @Override
    public Node adoptNode(Node source) throws DOMException {
        disallowUpdate();
        return null;
    }

    /**
     * The configuration used when <code>Document.normalizeDocument()</code>
     * is invoked.
     *
     * @since DOM Level 3
     */
    @Override
    public DOMConfiguration getDomConfig() {
        return null;
    }

    /**
     * This method acts as if the document was going through a save and load
     * cycle, putting the document in a "normal" form. As a consequence,
     * this method updates the replacement tree of
     * <code>EntityReference</code> nodes and normalizes <code>Text</code>
     * nodes, as defined in the method <code>Node.normalize()</code>.
     * <br> Otherwise, the actual result depends on the features being set on
     * the <code>Document.domConfig</code> object and governing what
     * operations actually take place. Noticeably this method could also
     * make the document namespace well-formed according to the algorithm
     * described in , check the character normalization, remove the
     * <code>CDATASection</code> nodes, etc. See
     * <code>DOMConfiguration</code> for details.
     * <pre>// Keep in the document
     * the information defined // in the XML Information Set (Java example)
     * DOMConfiguration docConfig = myDocument.getDomConfig();
     * docConfig.setParameter("infoset", Boolean.TRUE);
     * myDocument.normalizeDocument();</pre>
     * <p>Mutation events, when supported, are generated to reflect the
     * changes occurring on the document.</p>
     * <p>If errors occur during the invocation of this method, such as an
     * attempt to update a read-only node or a <code>Node.nodeName</code>
     * contains an invalid character according to the XML version in use,
     * errors or warnings (<code>DOMError.SEVERITY_ERROR</code> or
     * <code>DOMError.SEVERITY_WARNING</code>) will be reported using the
     * <code>DOMErrorHandler</code> object associated with the "error-handler
     * " parameter. Note this method might also report fatal errors (
     * <code>DOMError.SEVERITY_FATAL_ERROR</code>) if an implementation
     * cannot recover from an error.</p>
     *
     * @since DOM Level 3
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */
    @Override
    public void normalizeDocument() throws DOMException {
        disallowUpdate();
    }

    /**
     * Rename an existing node of type <code>ELEMENT_NODE</code> or
     * <code>ATTRIBUTE_NODE</code>. Not supported in this implementation
     *
     * @param n             The node to rename.
     * @param namespaceURI  The new namespace URI.
     * @param qualifiedName The new qualified name.
     * @return The renamed node. This is either the specified node or the new
     *         node that was created to replace the specified node.
     * @throws org.w3c.dom.DOMException always, to indicate that update is not supported in this DOM implementation
     */
    /*@Nullable*/
    @Override
    public Node renameNode(Node n, String namespaceURI, String qualifiedName) throws DOMException {
        disallowUpdate();
        return null;
    }


}

