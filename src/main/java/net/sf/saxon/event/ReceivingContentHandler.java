////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.QuitParsingException;
import net.sf.saxon.trans.XmlProcessingException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.tiny.CompressedWhitespace;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Whitespace;
import org.xml.sax.*;
import org.xml.sax.ext.Attributes2;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.transform.Result;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * ReceivingContentHandler is a glue class that provides a standard SAX ContentHandler
 * interface to a Saxon Receiver. To achieve this it needs to map names supplied
 * as strings to numeric name codes, for which purpose it needs access to a name
 * pool. The class also performs the function of assembling adjacent text nodes.
 * <p>If the input stream contains the processing instructions assigned by JAXP to switch
 * disable-output-escaping on or off, these will be reflected in properties set in the corresponding
 * characters events. In this case adjacent text nodes will not be combined.</p>
 * <p>The {@code ReceivingContentHandler} is written on the assumption that it is receiving events
 * from a parser configured with {@code http://xml.org/sax/features/namespaces} set to true
 * and {@code http://xml.org/sax/features/namespace-prefixes} set to false.</p>
 * <p>When running as a {@code TransformerHandler}, we have no control over the feature settings
 * of the sender of the events, and if the events do not follow this pattern then the class may
 * fail in unpredictable ways.</p>
 *
 */

public class ReceivingContentHandler
        implements ContentHandler, LexicalHandler, DTDHandler
{
    private PipelineConfiguration pipe;
    private Receiver receiver;
    private boolean inDTD = false;    // true while processing the DTD
    private LocalLocator localLocator = new LocalLocator(Loc.NONE);
    private boolean lineNumbering;
    private Location lastTextNodeLocator;

    // buffer for accumulating character data, until the next markup event is received

    private char[] buffer = new char[512];
    private int charsUsed = 0;
    private CharSlice slice = new CharSlice(buffer, 0, 0);

    // stack for accumulating namespace information

    private Stack<NamespaceMap> namespaceStack = new Stack<>();
    private NamespaceMap currentNamespaceMap;

    // determine whether ignorable whitespace is ignored

    private boolean ignoreIgnorable = false;

    // determine whether DTD attribute types are retained

    private boolean retainDTDAttributeTypes = false;

    // determine whether DTD attribute value defaults should be suppressed

    //private boolean suppressDTDAttributeDefaults = false;

    // indicate that escaping is allowed to be disabled using the JAXP-defined processing instructions

    private boolean allowDisableOutputEscaping = false;

    // indicate that escaping is disabled

    private boolean escapingDisabled = false;

    // flag to indicate whether the last tag was a start tag or an end tag

    private boolean afterStartTag = true;

    /**
     * A local cache is used to avoid allocating namecodes for the same name more than once.
     * This reduces contention on the NamePool. This is a two-level hashmap: the first level
     * has the namespace URI as its key, and returns a HashMap which maps lexical QNames to integer
     * namecodes.
     */

    private final HashMap<String, HashMap<String, NodeName>> nameCache = new HashMap<>(10);
    private HashMap<String, NodeName> noNamespaceNameCache = new HashMap<>(10);

    // Action to be taken with defaulted attributes. 0=process normally, -1=suppress, +1=mark as defaulted
    private int defaultedAttributesAction = 0;

    // Stack holding depth of nesting of elements within external entities; created on first use
    private Stack<Integer> elementDepthWithinEntity;


    /**
     * Create a ReceivingContentHandler and initialise variables
     */

    public ReceivingContentHandler() {
        currentNamespaceMap = NamespaceMap.emptyMap();
        namespaceStack.push(currentNamespaceMap);
    }

    /**
     * Set the ReceivingContentHandler to its initial state, except for the local name cache,
     * which is retained
     */

    public void reset() {
        pipe = null;
        receiver = null;
        ignoreIgnorable = false;
        retainDTDAttributeTypes = false;
        charsUsed = 0;
        slice.setLength(0);
        namespaceStack = new Stack<>();
        currentNamespaceMap = NamespaceMap.emptyMap();
        namespaceStack.push(currentNamespaceMap);
        localLocator = new LocalLocator(Loc.NONE);
        allowDisableOutputEscaping = false;
        escapingDisabled = false;
        lineNumbering = false;
    }

    /**
     * Set the receiver to which events are passed. ReceivingContentHandler is essentially a translator
     * that takes SAX events as input and produces Saxon Receiver events as output; these Receiver events
     * are passed to the supplied Receiver
     *
     * @param receiver the Receiver of events
     */

    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
        //receiver = new TracingFilter(receiver);
    }

    /**
     * Get the receiver to which events are passed.
     *
     * @return the underlying Receiver
     */

    public Receiver getReceiver() {
        return receiver;
    }

    /**
     * Set the pipeline configuration
     *
     * @param pipe the pipeline configuration. This holds a reference to the Saxon configuration, as well as
     *             information that can vary from one pipeline to another
     */

    public void setPipelineConfiguration(PipelineConfiguration pipe) {
        this.pipe = pipe;
        Configuration config = pipe.getConfiguration();
        ignoreIgnorable = pipe.getParseOptions().getSpaceStrippingRule() != NoElementsSpaceStrippingRule.getInstance();
        retainDTDAttributeTypes = config.getBooleanProperty(Feature.RETAIN_DTD_ATTRIBUTE_TYPES);
        if (!pipe.getParseOptions().isExpandAttributeDefaults()) {
            defaultedAttributesAction = -1;
        } else if (config.getBooleanProperty(Feature.MARK_DEFAULTED_ATTRIBUTES)) {
            defaultedAttributesAction = +1;
        }
        allowDisableOutputEscaping = config.getConfigurationProperty(Feature.USE_PI_DISABLE_OUTPUT_ESCAPING);
        lineNumbering = pipe.getParseOptions().isLineNumbering();
    }

    /**
     * Get the pipeline configuration
     *
     * @return the pipeline configuration as supplied to
     *         {@link #setPipelineConfiguration(PipelineConfiguration)}
     */

    public PipelineConfiguration getPipelineConfiguration() {
        return pipe;
    }

    /**
     * Get the Configuration object
     *
     * @return the Saxon configuration
     */

    public Configuration getConfiguration() {
        return pipe.getConfiguration();
    }

    /**
     * Set whether "ignorable whitespace" should be ignored. This method is effective only
     * if called after setPipelineConfiguration, since the default value is taken from the
     * configuration.
     *
     * @param ignore true if ignorable whitespace (whitespace in element content that is notified
     *               via the {@link #ignorableWhitespace(char[], int, int)} method) should be ignored, false if
     *               it should be treated as ordinary text.
     */

    public void setIgnoreIgnorableWhitespace(boolean ignore) {
        ignoreIgnorable = ignore;
    }

    /**
     * Determine whether "ignorable whitespace" is ignored. This returns the value that was set
     * using {@link #setIgnoreIgnorableWhitespace} if that has been called; otherwise the value
     * from the configuration.
     *
     * @return true if ignorable whitespace is being ignored
     */

    public boolean isIgnoringIgnorableWhitespace() {
        return ignoreIgnorable;
    }

    /**
     * Receive notification of the beginning of a document.
     */

    @Override
    public void startDocument() throws SAXException {
//        System.err.println("ReceivingContentHandler#startDocument");
        try {
            charsUsed = 0;
            currentNamespaceMap = NamespaceMap.emptyMap();
            namespaceStack = new Stack<>();
            namespaceStack.push(currentNamespaceMap);
            receiver.setPipelineConfiguration(pipe);
            String systemId = localLocator.getSystemId();
            if (systemId != null) {
                receiver.setSystemId(localLocator.getSystemId());
            }
            receiver.open();
            receiver.startDocument(ReceiverOption.NONE);
        } catch (QuitParsingException quit) {
            getPipelineConfiguration().getErrorReporter().report(
                    new XmlProcessingException(quit).asWarning());
            throw new SAXException(quit);
        } catch (XPathException err) {
            throw new SAXException(err);
        }
    }

    /**
     * Receive notification of the end of a document
     */

    @Override
    public void endDocument() throws SAXException {
        // System.err.println("RCH: end document");
        try {
            flush(true);
            receiver.endDocument();
            receiver.close();
        } catch (ValidationException err) {
            err.setLocator(localLocator);
            throw new SAXException(err);
        } catch (QuitParsingException err) {
            // no action: not worth bothering at this stage of the game
        } catch (XPathException err) {
            err.maybeSetLocation(localLocator);
            throw new SAXException(err);
        }
    }

    /**
     * Supply a locator that can be called to give information about location in the source document
     * being parsed.
     */

    @Override
    public void setDocumentLocator(Locator locator) {
        localLocator = new LocalLocator(locator);
        if (!lineNumbering) {
            lastTextNodeLocator = localLocator;
        }
    }

    /**
     * Notify a namespace prefix to URI binding
     */

    @Override
    public void startPrefixMapping(String prefix, String uri) {
        //System.err.println("StartPrefixMapping " + prefix + "=" + uri);
        if (prefix.equals("xmlns")) {
            // the binding xmlns:xmlns="http://www.w3.org/2000/xmlns/"
            // should never be reported, but it's been known to happen
            return;
        }
        currentNamespaceMap = currentNamespaceMap.bind(prefix, uri);
    }

    /**
     * Notify that a namespace binding is going out of scope
     */

    @Override
    public void endPrefixMapping(String prefix) {
        //System.err.println("endPrefixMapping " + prefix);
    }

    /**
     * Receive notification of the beginning of an element.
     *
     * <p>The Parser will invoke this method at the beginning of every
     * element in the XML document; there will be a corresponding
     * {@link #endElement endElement} event for every startElement event
     * (even when the element is empty). All of the element's content will be
     * reported, in order, before the corresponding endElement
     * event.</p>
     *
     * <p>This event allows up to three name components for each
     * element:</p>
     *
     * <ol>
     * <li>the Namespace URI;</li>
     * <li>the local name; and</li>
     * <li>the qualified (prefixed) name.</li>
     * </ol>
     *
     * <p>Saxon expects all three of these to be provided.
     *
     * <p>The attribute list provided should contain only
     * attributes with explicit values (specified or defaulted):
     * #IMPLIED attributes should be omitted.  The attribute list
     * should not contain attributes used for Namespace declarations
     * (xmlns* attributes); if it does, Saxon will ignore them,
     * which may lead to unresolved namespace prefixes.</p>
     *
     * @param uri       the Namespace URI, or the empty string if the
     *                  element has no Namespace URI or if Namespace
     *                  processing is not being performed
     * @param localname the local name (without prefix), or the
     *                  empty string if Namespace processing is not being
     *                  performed
     * @param rawname   the qualified name (with prefix), or the
     *                  empty string if qualified names are not available
     * @param atts      the attributes attached to the element.  If
     *                  there are no attributes, it shall be an empty
     *                  Attributes object.  The value of this object after
     *                  startElement returns is undefined
     * @throws org.xml.sax.SAXException any SAX exception, possibly
     *                                  wrapping another exception
     * @see #endElement
     * @see org.xml.sax.Attributes
     * @see org.xml.sax.helpers.AttributesImpl
     */
    @Override
    public void startElement(String uri, String localname, String rawname, Attributes atts)
            throws SAXException {
        //System.err.println("ReceivingContentHandler#startElement " + localname + " (depth=" + namespaceStack.size() + ")");
        //for (int a=0; a<atts.getLength(); a++) {
        //     System.err.println("  Attribute " + atts.getURI(a) + "/" + atts.getLocalName(a) + "/" + atts.getQName(a));
        //}
        try {
            flush(true);

            int options = ReceiverOption.NAMESPACE_OK | ReceiverOption.ALL_NAMESPACES;
            NodeName elementName = getNodeName(uri, localname, rawname);
            AttributeMap attributes = makeAttributeMap(atts, localLocator);
            receiver.startElement(elementName, Untyped.getInstance(),
                                  attributes, currentNamespaceMap,
                                  localLocator, options);

            localLocator.levelInEntity++;
            namespaceStack.push(currentNamespaceMap);
            afterStartTag = true;

        } catch (XPathException err) {
            err.maybeSetLocation(localLocator);
            throw new SAXException(err);
        }
    }

    private AttributeMap makeAttributeMap(Attributes atts, Location location) throws SAXException {
        int length = atts.getLength();
        List<AttributeInfo> list = new ArrayList<>(atts.getLength());
        for (int a=0; a<length; a++) {
            int properties = ReceiverOption.NAMESPACE_OK;
            String value = atts.getValue(a);
            String qname = atts.getQName(a);
            if (qname.startsWith("xmlns") && (qname.length() == 5 || qname.charAt(5) == ':')) {
                // We normally configure the parser so that it doesn't notify namespaces as attributes.
                // But when running as a TransformerHandler, we have no control over the feature settings
                // of the sender of the events. So we filter them out, just in case. There might be cases
                // where we ought not just to ignore them, but to handle them as namespace events, but
                // we'll cross that bridge when we come to it.
                continue;
            }

            if (defaultedAttributesAction != 0
                    && atts instanceof Attributes2
                    && !((Attributes2) atts).isSpecified(qname)) {
                if (defaultedAttributesAction == -1) {
                    // suppress defaulted attributes
                    continue;
                } else {
                    // mark defaulted attributes
                    properties |= ReceiverOption.DEFAULTED_VALUE;
                }
            }

            NodeName attCode = getNodeName(atts.getURI(a), atts.getLocalName(a), atts.getQName(a));
            String type = atts.getType(a);
            SimpleType typeCode = BuiltInAtomicType.UNTYPED_ATOMIC;
            if (retainDTDAttributeTypes) {
                switch (type) {
                    case "CDATA":
                        // common case, no action
                        break;
                    case "ID":
                        typeCode = BuiltInAtomicType.ID;
                        break;
                    case "IDREF":
                        typeCode = BuiltInAtomicType.IDREF;
                        break;
                    case "IDREFS":
                        typeCode = BuiltInListType.IDREFS;
                        break;
                    case "NMTOKEN":
                        typeCode = BuiltInAtomicType.NMTOKEN;
                        break;
                    case "NMTOKENS":
                        typeCode = BuiltInListType.NMTOKENS;
                        break;
                    case "ENTITY":
                        typeCode = BuiltInAtomicType.ENTITY;
                        break;
                    case "ENTITIES":
                        typeCode = BuiltInListType.ENTITIES;
                        break;
                }
            } else {
                switch (type) {
                    case "ID":
                        properties |= ReceiverOption.IS_ID;
                        break;
                    case "IDREF":
                        properties |= ReceiverOption.IS_IDREF;
                        break;
                    case "IDREFS":
                        properties |= ReceiverOption.IS_IDREF;
                        break;
                }
            }
            list.add(new AttributeInfo(attCode, typeCode, value, location, properties));
        }
        return AttributeMap.fromList(list);
    }

    /**
     * Get the NodeName object associated with a name appearing in the document. Note that no
     * NamePool name code is allocated at this stage, but the returned NodeName object contains
     * room for one to be added later, to speed up name comparisons.
     *
     * @param uri       the namespace URI
     * @param localname the local part of the name
     * @param rawname   the lexical QName
     * @return the NamePool name code, newly allocated if necessary
     * @throws SAXException if the information supplied by the SAX parser is insufficient
     */

    private NodeName getNodeName(String uri, String localname, String rawname) throws SAXException {
        // System.err.println("URI=" + uri + " local=" + " raw=" + rawname);
        // The XML parser isn't required to report the rawname (qname), though all known parsers do.
        // If none is provided, we give up
        if (rawname.isEmpty()) {
            throw new SAXException("Saxon requires an XML parser that reports the QName of each element");
        }
        // It's also possible (especially when using a TransformerHandler) that the parser
        // has been configured to report the QName rather than the localname+URI
        if (localname.isEmpty()) {
            throw new SAXException("Parser configuration problem: namespace reporting is not enabled");
        }

        // Following code maintains a local cache to remember all the element names that have been
        // allocated, which reduces contention on the NamePool. It also avoids parsing the lexical QName
        // when the same name is used repeatedly. We also get a tiny improvement by avoiding the first hash
        // table lookup for names in the null namespace.

        HashMap<String, NodeName> map2 = uri.isEmpty() ? noNamespaceNameCache : nameCache.get(uri);
        if (map2 == null) {
            map2 = new HashMap<>(50);
            nameCache.put(uri, map2);
            if (uri.isEmpty()) {
                noNamespaceNameCache = map2;
            }
        }

        NodeName n = map2.get(rawname);
        // we use the rawname (qname) rather than the local name because we want to retain the prefix
        // Note that the NodeName objects generated do not contain a namecode or fingerprint; it will be generated
        // later if we are building a TinyTree, but not necessarily on other paths (e.g. an identity transformation).
        // The NodeName object is shared by all elements with the same name, so when the namecode is allocated to one
        // of them, it is there for all of them.
        if (n == null) {
            if (uri.isEmpty()) {
                NoNamespaceName qn = new NoNamespaceName(localname);
                map2.put(rawname, qn);
                return qn;
            } else {
                String prefix = NameChecker.getPrefix(rawname);
                FingerprintedQName qn = new FingerprintedQName(prefix, uri, localname);
                map2.put(rawname, qn);
                return qn;
            }
        } else {
            return n;
        }

    }

    /**
     * Report the end of an element (the close tag)
     */

    @Override
    public void endElement(String uri, String localname, String rawname) throws SAXException {
        //System.err.println("ReceivingContentHandler#End element " + rawname + " (depth=" + namespaceStack.size() + ")");
        try {
            // don't attempt whitespace compression if this end tag follows a start tag
            flush(!afterStartTag);
            localLocator.levelInEntity--;
            receiver.endElement();
        } catch (ValidationException err) {
            err.maybeSetLocation(localLocator);
            if (!err.hasBeenReported()) {
                pipe.getErrorReporter().report(new XmlProcessingException(err));
            }
            err.setHasBeenReported(true);
            throw new SAXException(err);
        } catch (XPathException err) {
            err.maybeSetLocation(localLocator);
            throw new SAXException(err);
        }
        afterStartTag = false;
        namespaceStack.pop();
        currentNamespaceMap = namespaceStack.peek();
    }

    /**
     * Report character data. Note that contiguous character data may be reported as a sequence of
     * calls on this method, with arbitrary boundaries
     */

    @Override
    public void characters(char[] ch, int start, int length) {
        // System.err.println("characters (" + length + ")");
        // need to concatenate chunks of text before we can decide whether a node is all-white

        while (charsUsed + length > buffer.length) {
            buffer = Arrays.copyOf(buffer, buffer.length*2);
            slice = new CharSlice(buffer, 0, 0);
        }
        System.arraycopy(ch, start, buffer, charsUsed, length);
        charsUsed += length;
        if (lineNumbering) {
            lastTextNodeLocator = localLocator.saveLocation();
        }
    }

    /**
     * Report character data classified as "Ignorable whitespace", that is, whitespace text nodes
     * appearing as children of elements with an element-only content model
     */

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
        if (!ignoreIgnorable) {
            characters(ch, start, length);
        }
    }

    /**
     * Notify the existence of a processing instruction
     */

    @Override
    public void processingInstruction(String name, String remainder) throws SAXException {
        try {
            flush(true);
            if (!inDTD) {
                if (name == null) {
                    // trick used by the old James Clark xp parser to notify a comment
                    comment(remainder.toCharArray(), 0, remainder.length());
                } else {
                    // some parsers allow through PI names containing colons
                    if (!NameChecker.isValidNCName(name)) {
                        throw new SAXException("Invalid processing instruction name (" + name + ')');
                    }
                    if (allowDisableOutputEscaping) {
                        if (name.equals(Result.PI_DISABLE_OUTPUT_ESCAPING)) {
                            //flush();
                            escapingDisabled = true;
                            return;
                        } else if (name.equals(Result.PI_ENABLE_OUTPUT_ESCAPING)) {
                            //flush();
                            escapingDisabled = false;
                            return;
                        }
                    }
                    CharSequence data;
                    if (remainder == null) {
                        // allowed by the spec but rarely seen: see Saxon bug 2491
                        data = "";
                    } else {
                        // not strictly necessary (the parser should have done this) but needed in practice
                        data = Whitespace.removeLeadingWhitespace(remainder);
                    }
                    receiver.processingInstruction(name, data, localLocator, ReceiverOption.NONE);
                }
            }
        } catch (XPathException err) {
            throw new SAXException(err);
        }
    }

    /**
     * Notify the existence of a comment. Note that in SAX this is part of LexicalHandler interface
     * rather than the ContentHandler interface.
     */

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        try {
            flush(true);
            if (!inDTD) {
                receiver.comment(new CharSlice(ch, start, length), localLocator, ReceiverOption.NONE);
            }
        } catch (XPathException err) {
            throw new SAXException(err);
        }
    }

    /**
     * Flush buffer for accumulated character data
     *
     * @param compress true if compression of whitespace should be attempted. This is an expensive
     *                 operation, so we avoid doing it when we hit an end tag that follows after a start tag, as
     *                 it's not likely to succeed in that situation.
     * @throws XPathException if flushing the character data fails
     */

    private void flush(boolean compress) throws XPathException {
        if (charsUsed > 0) {
            slice.setLength(charsUsed);
            CharSequence cs = compress ? CompressedWhitespace.compress(slice) : slice;
            receiver.characters(cs, lastTextNodeLocator,
                    escapingDisabled ? ReceiverOption.DISABLE_ESCAPING : ReceiverOption.WHOLE_TEXT_NODE);
            charsUsed = 0;
            escapingDisabled = false;
        }
    }

    /**
     * Notify a skipped entity. Saxon ignores this event
     */

    @Override
    public void skippedEntity(String name) {
    }

    // No-op methods to satisfy lexical handler interface

    /**
     * Register the start of the DTD. Saxon ignores the DTD; however, it needs to know when the DTD starts and
     * ends so that it can ignore comments in the DTD, which are reported like any other comment, but which
     * are skipped because they are not part of the XPath data model
     */

    @Override
    public void startDTD(String name, String publicId, String systemId) {
        inDTD = true;
    }

    /**
     * Register the end of the DTD. Comments in the DTD are skipped because they
     * are not part of the XPath data model
     */

    @Override
    public void endDTD() {
        inDTD = false;
    }

    @Override
    public void startEntity(String name) {
        if (elementDepthWithinEntity == null) {
            elementDepthWithinEntity = new Stack<>();
        }
        elementDepthWithinEntity.push(localLocator.levelInEntity);
        localLocator.levelInEntity = 0;
    }

    @Override
    public void endEntity(String name) {
        localLocator.levelInEntity = elementDepthWithinEntity.pop();
    }

    @Override
    public void startCDATA() {
    }

    @Override
    public void endCDATA() {
    }

    //////////////////////////////////////////////////////////////////////////////
    // Implement DTDHandler interface
    //////////////////////////////////////////////////////////////////////////////


    @Override
    public void notationDecl(String name,
                             String publicId,
                             String systemId) {
    }


    @Override
    public void unparsedEntityDecl(String name,
                                   String publicId,
                                   String systemId,
                                   String notationName) throws SAXException {
        // Some (non-conformant) SAX parsers report the systemId as written.
        // We need to turn it into an absolute URL.

        String uri = systemId;
        if (localLocator != null) {
            try {
                URI suppliedURI = new URI(systemId);
                if (!suppliedURI.isAbsolute()) {
                    String baseURI = localLocator.getSystemId();
                    if (baseURI != null) {   // See bug 21679
                        uri = ResolveURI.makeAbsolute(systemId, baseURI).toString();
                    }
                }
            } catch (URISyntaxException err) {
                // fallback - no action
            }
        }
        try {
            receiver.setUnparsedEntity(name, uri, publicId);
        } catch (XPathException err) {
            throw new SAXException(err);
        }
    }

    /**
     * An implementation of the Saxon {@link Location} interface that wraps the SAX Locator
     * information. Note that this object is mutable and changes continually as parsing proceeds;
     * it is therefore necessary to call its {@link #saveLocation()} method to obtain an
     * immutable location that still has meaning once parsing is finished.
     */

    public static class LocalLocator implements Location {

        private final Locator saxLocator;
        public int levelInEntity;

        LocalLocator(Locator saxLocator) {
            this.saxLocator = saxLocator;
            this.levelInEntity = 0;
        }

        /**
         * Return the system identifier for the current document event.
         *
         * @return A string containing the system identifier, or
         *         null if none is available.
         */

        @Override
        public String getSystemId() {
            return saxLocator.getSystemId();
        }

        /**
         * Return the public identifier for the current document event.
         *
         * @return A string containing the public identifier, or
         *         null if none is available.
         */

        @Override
        public String getPublicId() {
            return saxLocator.getPublicId();
        }

        /**
         * Return the line number where the current document event ends.
         *
         * @return The line number, or -1 if none is available.
         */

        @Override
        public int getLineNumber() {
            return saxLocator.getLineNumber();
        }

        /**
         * Return the character position where the current document event ends.
         *
         * @return The column number, or -1 if none is available.
         */

        @Override
        public int getColumnNumber() {
            return saxLocator.getColumnNumber();
        }

        /**
         * Get an immutable copy of this Location object. By default Location objects may be mutable, so they
         * should not be saved for later use. The result of this operation holds the same location information,
         * but in an immutable form.
         */

        @Override
        public Location saveLocation() {
            return new Loc(getSystemId(), getLineNumber(), getColumnNumber());
        }
    }

}

