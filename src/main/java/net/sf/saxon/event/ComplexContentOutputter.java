////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.CharSequenceConsumer;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ExternalObject;

import javax.xml.transform.Result;
import java.util.*;

import static net.sf.saxon.event.RegularSequenceChecker.State.*;

/**
 * This class is used for generating complex content, that is, the content of an
 * element or document node. It enforces the rules on the order of events within
 * complex content (attributes and namespaces must come first), and it implements
 * part of the namespace fixup rules, in particular, it ensures that there is a
 * namespace node for the namespace used in the element name and in each attribute
 * name.
 * <p>
 * The same ComplexContentOutputter may be used for generating an entire XML
 * document; it is not necessary to create a new outputter for each element node.
 * <p>
 * From Saxon 9.9, the ComplexContentOutputter does not combine top-level events.
 * Unless nested within a startDocument/endDocument or startElement/endElement pair,
 * items such as atomic values, text nodes, attribute nodes, maps and arrays are
 * passed through unchanged to the output. It is typically the responsibility
 * of the Destination object to decide how to combine top-level events (whether
 * to build a single document, whether to insert item separators, etc).
 * <p>
 * From Saxon 10.0, the handling of namespaces changes. Unlike other receivers,
 * the {@code ComplexContentOutputter} can receive individual namespace events
 * as part of the complex content of an element node. The class is now fully
 * responsible for namespace fixup and for namespace inheritance. The mechanism
 * for namespace inheritance is changed; we are now maintaining all the in-scope
 * namespaces for an element rather than a set of deltas, so namespace inheritance
 * (rather than disinheritance) now requires concrete action.
 * </p>
 *
 */

public final class ComplexContentOutputter extends Outputter implements Receiver, Result {

    private Receiver nextReceiver;
    // the next receiver in the output pipeline

    private NodeName pendingStartTag = null;
    private int level = -1;
    // records the number of startDocument or startElement events
    // that have not yet been closed. Note that startDocument and startElement
    // events may be arbitrarily nested; startDocument and endDocument
    // are ignored unless they occur at the outermost level, except that they
    // still change the level number
    private boolean[] currentLevelIsDocument = new boolean[20];
    private final List<AttributeInfo> pendingAttributes = new ArrayList<>();
//    private NodeName[] pendingAttCode = new NodeName[20];
//    private SimpleType[] pendingAttType = new SimpleType[20];
//    private String[] pendingAttValue = new String[20];
//    private Location[] pendingAttLocation = new Location[20];
//    private int[] pendingAttProp = new int[20];
//    private int pendingAttListSize = 0;

    private NamespaceMap pendingNSMap;
    private final Stack<NamespaceMap> inheritedNamespaces = new Stack<>();
//    private int pendingNSListSize = 0;

    private SchemaType currentSimpleType = null;  // any other value means we are currently writing an
    // element of a particular simple type

    private int startElementProperties;
    private Location startElementLocationId = Loc.NONE;
    private HostLanguage hostLanguage = HostLanguage.XSLT;

    private RegularSequenceChecker.State state = Initial;
    private boolean previousAtomic = false;
    /**
     * Create a ComplexContentOutputter
     * @param next the next receiver in the pipeline
     */

    public ComplexContentOutputter(Receiver next) {
        PipelineConfiguration pipe = next.getPipelineConfiguration();
        setPipelineConfiguration(pipe);
        setReceiver(next);
        Objects.requireNonNull(pipe);
        setHostLanguage(pipe.getHostLanguage());
        inheritedNamespaces.push(NamespaceMap.emptyMap());
    }

    /**
     * Static factory method to create an push pipeline containing a ComplexContentOutputter
     * @param receiver the destination to which the constructed complex content will be written
     * @param options options for validating the output stream; may be null
     * @return the new ComplexContentOutputter at the head of the constructed pipeline
     */

    public static ComplexContentOutputter makeComplexContentReceiver(Receiver receiver, ParseOptions options) {
//        System.err.println("CHANGE OUTPUT DESTINATION new=" + receiver);

        String systemId = receiver.getSystemId();
        boolean validate = options != null && options.getSchemaValidationMode() != Validation.PRESERVE;

        // add a validator to the pipeline if required

        if (validate) {
            Configuration config = receiver.getPipelineConfiguration().getConfiguration();
            receiver = config.getDocumentValidator(receiver, systemId, options, null);
        }

        ComplexContentOutputter result = new ComplexContentOutputter(receiver);
        result.setSystemId(systemId);
        return result;
    }

    @Override
    public void setPipelineConfiguration(/*@NotNull*/ PipelineConfiguration pipe) {
        if (pipelineConfiguration != pipe) {
            pipelineConfiguration = pipe;
            if (nextReceiver != null) {
                nextReceiver.setPipelineConfiguration(pipe);
            }
        }
    }

    @Override
    public void setSystemId(String systemId) {
        super.setSystemId(systemId);
        nextReceiver.setSystemId(systemId);
    }

    /**
     * Set the host language
     *
     * @param language the host language, for example {@link HostLanguage#XQUERY}
     */

    public void setHostLanguage(HostLanguage language) {
        hostLanguage = language;
    }


    /**
     * Set the receiver (to handle the next stage in the pipeline) directly
     *
     * @param receiver the receiver to handle the next stage in the pipeline
     */

    public void setReceiver(Receiver receiver) {
        this.nextReceiver = receiver;
    }

    /**
     * Get the next receiver in the processing pipeline
     *
     * @return the receiver which this ComplexContentOutputter writes to
     */

    public Receiver getReceiver() {
        return nextReceiver;
    }


    /**
     * Start the output process
     */

    @Override
    public void open() throws XPathException {
        nextReceiver.open();
        previousAtomic = false;
        state = Open;
    }

    /**
     * Start of a document node.
     * @param properties any special properties of the node
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        level++;
        if (level == 0) {
            nextReceiver.startDocument(properties);
        } else if (state == StartTag) {
            startContent();
        }
        previousAtomic = false;
        if (currentLevelIsDocument.length < level + 1) {
            currentLevelIsDocument = Arrays.copyOf(currentLevelIsDocument, level * 2);
        }
        currentLevelIsDocument[level] = true;
        state = Content;
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        if (level == 0) {
            nextReceiver.endDocument();
        }
        previousAtomic = false;
        level--;
        state = level < 0 ? Open : Content;
    }

    /**
     * Notify an unparsed entity URI.
     *
     * @param name     The name of the unparsed entity
     * @param systemID The system identifier of the unparsed entity
     * @param publicID The public identifier of the unparsed entity
     */
    @Override
    public void setUnparsedEntity(String name, String systemID, String publicID) throws XPathException {
        nextReceiver.setUnparsedEntity(name, systemID, publicID);
    }

    /**
     * Produce text content output. <BR>
     * Special characters are escaped using XML/HTML conventions if the output format
     * requires it.
     *
     * @param s The String to be output
     * @param locationId the location of the node in the source, or of the instruction that created it
     * @param properties any special properties of the node
     * @throws XPathException for any failure
     */

    @Override
    public void characters(CharSequence s, Location locationId, int properties) throws XPathException {
        if (level >= 0) {
            previousAtomic = false;
            if (s == null) {
                return;
            }
            int len = s.length();
            if (len == 0) {
                return;
            }
            if (state == StartTag) {
                startContent();
            }
        }
        nextReceiver.characters(s, locationId, properties);
    }

    /**
     * Output an element start tag. <br>
     * The actual output of the tag is deferred until all attributes have been output
     * using attribute().
     * @param elemName The element name
     * @param location the location of the element node (or the instruction that created it)
     * @param properties any special properties of the node
     */

    @Override
    public void startElement(NodeName elemName, SchemaType typeCode, Location location, int properties) throws XPathException {
        //System.err.println("Start element " + elemName);
        level++;
        if (state == StartTag) {
            startContent();
        }
        startElementProperties = properties;
        startElementLocationId = location.saveLocation();
        pendingAttributes.clear();
        pendingNSMap = NamespaceMap.emptyMap();
        pendingStartTag = elemName;
        currentSimpleType = typeCode;
        previousAtomic = false;
        if (currentLevelIsDocument.length < level + 1) {
            currentLevelIsDocument = Arrays.copyOf(currentLevelIsDocument, level * 2);
        }

        currentLevelIsDocument[level] = false;
        state = StartTag;
    }


    /**
     * Add a namespace node to the content being constructed.
     */

    @Override
    public void namespace(String prefix, String namespaceUri, int properties)
            throws XPathException {
        Objects.requireNonNull(prefix);
        Objects.requireNonNull(namespaceUri);
        if (ReceiverOption.contains(properties, ReceiverOption.NAMESPACE_OK)) {
            pendingNSMap = pendingNSMap.put(prefix, namespaceUri);
        } else if (level >= 0) {
            if (state != StartTag) {
                throw NoOpenStartTagException.makeNoOpenStartTagException(
                        Type.NAMESPACE,
                        prefix,
                        hostLanguage,
                        currentLevelIsDocument[level],
                        startElementLocationId);
            }

            // It is an error to output a namespace node for the default namespace if the element
            // itself is in the null namespace, as the resulting element could not be serialized

            boolean elementIsInNullNamespace = pendingStartTag.hasURI("");
            if (prefix.isEmpty() && !namespaceUri.isEmpty()) {
                if (elementIsInNullNamespace) {
                    XPathException err = new XPathException("Cannot output a namespace node for the default namespace ("
                                                                    + namespaceUri + ") when the element is in no namespace");
                    err.setErrorCode(hostLanguage == HostLanguage.XSLT ? "XTDE0440" : "XQDY0102");
                    throw err;
                }
            }

            boolean rejectDuplicates = ReceiverOption.contains(properties, ReceiverOption.REJECT_DUPLICATES);
            if (rejectDuplicates) {

                // Handle declarations whose prefix is duplicated for this element.
                String uri = pendingNSMap.getURI(prefix);
                if (uri != null && !uri.equals(namespaceUri)) {
                    XPathException err = new XPathException(
                            "Cannot create two namespace nodes with the same prefix "
                                    + "mapped to different URIs (prefix=\"" + prefix + "\", URIs=(\"" +
                                    uri + "\", \"" + namespaceUri + "\"))");
                    err.setErrorCode(hostLanguage == HostLanguage.XSLT ? "XTDE0430" : "XQDY0102");
                    throw err;
                }
            }
            pendingNSMap = pendingNSMap.put(prefix, namespaceUri);

        } else {
            // push top-level namespace nodes down the pipeline
            Orphan orphan = new Orphan(getConfiguration());
            orphan.setNodeKind(Type.NAMESPACE);
            orphan.setNodeName(new NoNamespaceName(prefix));
            orphan.setStringValue(namespaceUri);
            nextReceiver.append(orphan, Loc.NONE, properties);
        }
        previousAtomic = false;

    }

    /**
     * Output a set of namespace bindings. This should have the same effect as outputting the
     * namespace bindings individually using {@link #namespace(String, String, int)}, but it
     * may be more efficient. It is used only when copying an element node together with
     * all its namespaces, so less checking is needed that the namespaces form a consistent
     * and complete set
     *
     * @param bindings   the set of namespace bindings
     * @param properties any special properties. The property {@link ReceiverOption#NAMESPACE_OK}
     *                   means that no checking is needed.
     * @throws XPathException if any failure occurs
     */

    @Override
    public void namespaces(NamespaceBindingSet bindings, int properties) throws XPathException {
        if (bindings instanceof NamespaceMap && pendingNSMap.isEmpty()
                && ReceiverOption.contains(properties, ReceiverOption.NAMESPACE_OK)) {
            pendingNSMap = (NamespaceMap)bindings;
        } else {
            super.namespaces(bindings, properties);
        }
    }

    /**
     * Output an attribute value. <p>
     * This is added to a list of pending attributes for the current start tag, overwriting
     * any previous attribute with the same name. But in XQuery, duplicate attributes are reported
     * as an error.<p>
     * This method must NOT be used to output namespace declarations.<p>
     * @param attName    The name of the attribute
     * @param value      The value of the attribute
     * @param locationId the location of the node in the source, or of the instruction that created it
     * @param properties Bit fields containing properties of the attribute to be written  @throws XPathException if there is no start tag to write to (created using writeStartTag),
     */

    @Override
    public void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        //System.err.println("Write attribute " + attName + "=" + value + " to Outputter " + this);
        if (level >= 0 && state != StartTag) {
            // The complexity here is in identifying the right error message and error code

            XPathException err = NoOpenStartTagException.makeNoOpenStartTagException(
                    Type.ATTRIBUTE,
                    attName.getDisplayName(),
                    hostLanguage,
                    currentLevelIsDocument[level],
                startElementLocationId);
            err.setLocator(locationId);
            throw err;
        }

        // if this is a duplicate attribute, overwrite the original in XSLT; throw an error in XQuery.
        // No check needed if the NOT_A_DUPLICATE property is set (typically, during a deep copy operation)

        AttributeInfo attInfo = new AttributeInfo(attName, typeCode, value.toString(), locationId, properties);
        if (level >= 0 && !ReceiverOption.contains(properties, ReceiverOption.NOT_A_DUPLICATE)) {
            for (int a = 0; a < pendingAttributes.size(); a++) {
                if (pendingAttributes.get(a).getNodeName().equals(attName)) {
                    if (hostLanguage == HostLanguage.XSLT) {
                        pendingAttributes.set(a, attInfo);
                        return;
                    } else {
                        XPathException err = new XPathException("Cannot create an element having two attributes with the same name: " +
                                Err.wrap(attName.getDisplayName(), Err.ATTRIBUTE));
                        err.setErrorCode("XQDY0025");
                        throw err;
                    }
                }
            }
        }

        // for top-level attributes (attributes whose parent element is not being copied),
        // check that the type annotation is not namespace-sensitive (because the namespace context might
        // be different, and we don't do namespace fixup for prefixes in content: see bug 4151

        if (level == 0 && !typeCode.equals(BuiltInAtomicType.UNTYPED_ATOMIC) /**/ && currentLevelIsDocument[0] /**/) {
            // commenting-out in line above done MHK 22 Jul 2011 to pass test Constr-cont-nsmode-8
            // reverted 2011-07-27 to pass tests in qischema family
            if (typeCode.isNamespaceSensitive()) {
                XPathException err = new XPathException("Cannot copy attributes whose type is namespace-sensitive (QName or NOTATION): " +
                        Err.wrap(attName.getDisplayName(), Err.ATTRIBUTE));
                err.setErrorCode(hostLanguage == HostLanguage.XSLT ? "XTTE0950" : "XQTY0086");
                throw err;
            }
        }

        // push top-level attribute nodes down the pipeline
        if (level < 0) {
            Orphan orphan = new Orphan(getConfiguration());
            orphan.setNodeKind(Type.ATTRIBUTE);
            orphan.setNodeName(attName);
            orphan.setTypeAnnotation(typeCode);
            orphan.setStringValue(value);
            nextReceiver.append(orphan, locationId, properties);
        }

        // otherwise, add this one to the list

        pendingAttributes.add(attInfo);
        previousAtomic = false;
    }

    /**
     * Notify the start of an element. This version of the method supplies all attributes and
     * namespaces and implicitly invokes {@link #startContent()}, which means it cannot be followed
     * by further calls on {@link #attribute(NodeName, SimpleType, CharSequence, Location, int)} or
     * {@link #namespace} to define further attributes and namespaces.
     *
     * <p>This version of the method does not perform namespace fixup for prefixes used in the element
     * name or attribute names; it is assumed that these prefixes are declared within the namespace map,
     * and that there are no conflicts. The method does, however, perform namespace inheritance: that is,
     * unless {@code properties} includes {@link ReceiverOption#DISINHERIT_NAMESPACES}, namespaces
     * declared on the parent element and not overridden are implicitly added to the namespace map.</p>
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
        //System.err.println("Start element " + elemName + " with " + attributes.getLength() + " attributes");
        if (state == StartTag) {
            startContent();
        }

        level++;
        startElementLocationId = location.saveLocation();
        if (currentLevelIsDocument.length < level + 1) {
            currentLevelIsDocument = Arrays.copyOf(currentLevelIsDocument, level * 2);
        }
        currentLevelIsDocument[level] = false;

        if (elemName.hasURI("") && !namespaces.getDefaultNamespace().isEmpty()) {
            namespaces = namespaces.remove("");
        }

        boolean inherit = !ReceiverOption.contains(properties, ReceiverOption.DISINHERIT_NAMESPACES);
        NamespaceMap ns2;
        if (inherit) {
            NamespaceMap inherited = inheritedNamespaces.peek();
            if (!inherited.getDefaultNamespace().isEmpty() && elemName.getURI().isEmpty()) {
                inherited = inherited.remove("");
            }
            ns2 = inherited.putAll(namespaces);
            if (ReceiverOption.contains(properties, ReceiverOption.BEQUEATH_INHERITED_NAMESPACES_ONLY)) {
                inheritedNamespaces.push(inherited);
            } else {
                inheritedNamespaces.push(ns2);
            }
        } else {
            ns2 = namespaces;
            inheritedNamespaces.push(NamespaceMap.emptyMap());
        }

        boolean refuseInheritedNamespaces = ReceiverOption.contains(properties, ReceiverOption.REFUSE_NAMESPACES);
        NamespaceMap ns3 = refuseInheritedNamespaces ? namespaces : ns2;

        nextReceiver.startElement(elemName, type, attributes, ns3, location, properties);
        state = Content;
    }

    /**
     * Check that the prefix for an element or attribute is acceptable, allocating a substitute
     * prefix if not. The prefix is acceptable unless a namespace declaration has been
     * written that assignes this prefix to a different namespace URI. This method
     * also checks that the element or attribute namespace has been declared, and declares it
     * if not.
     *
     * @param nodeName the proposed name, including proposed prefix
     * @param seq      sequence number, used for generating a substitute prefix when necessary.
     *                 The value 0 is used for element names; values greater than 0 are used
     *                 for attribute names.
     * @return a nameCode to use in place of the proposed nameCode (or the original nameCode
     *         if no change is needed)
     */

    private NodeName checkProposedPrefix(NodeName nodeName, int seq) {
        String nodePrefix = nodeName.getPrefix();
        String nodeURI = nodeName.getURI();
        if (nodeURI.isEmpty()) {
            return nodeName;
        } else {
            String uri = pendingNSMap.getURI(nodePrefix);
            if (uri == null) {
                pendingNSMap = pendingNSMap.put(nodePrefix, nodeURI);
                return nodeName;
            } else if (nodeURI.equals(uri)) {
                return nodeName;    // all is well
            } else {
                String newPrefix = getSubstitutePrefix(nodePrefix, nodeURI, seq);
                NodeName newName = new FingerprintedQName(newPrefix, nodeURI, nodeName.getLocalPart());
                pendingNSMap = pendingNSMap.put(newPrefix, nodeURI);
                return newName;
            }
        }
    }

    /**
     * It is possible for a single output element to use the same prefix to refer to different
     * namespaces. In this case we have to generate an alternative prefix for uniqueness. The
     * one we generate is based on the sequential position of the element/attribute: this is
     * designed to ensure both uniqueness (with a high probability) and repeatability
     *
     * @param prefix the proposed prefix
     * @param uri the namespace URI
     * @param seq    sequence number for use in the substitute prefix
     * @return a prefix to use in place of the one originally proposed
     */

    private String getSubstitutePrefix(String prefix, String uri, int seq) {
        if (uri.equals(NamespaceConstant.XML)) {
            return "xml";
        }
        return prefix + '_' + seq;
    }

    /**
     * Output an element end tag.
     */

    @Override
    public void endElement() throws XPathException {
        //System.err.println("Write end tag ");
        if (state == StartTag) {
            startContent();
        } else {
            //pendingStartTagDepth = -2;
            pendingStartTag = null;
        }

        // write the end tag

        nextReceiver.endElement();
        level--;
        previousAtomic = false;
        state = level < 0 ? Open : Content;
        inheritedNamespaces.pop();
    }

    /**
     * Write a comment
     */

    @Override
    public void comment(CharSequence comment, Location locationId, int properties) throws XPathException {
        if (level >= 0) {
            if (state == StartTag) {
                startContent();
            }
            previousAtomic = false;
        }
        nextReceiver.comment(comment, locationId, properties);
    }

    /**
     * Write a processing instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (level >= 0) {
            if (state == StartTag) {
                startContent();
            }
            previousAtomic = false;
        }
        nextReceiver.processingInstruction(target, data, locationId, properties);
    }

    /**
     * Append an arbitrary item (node or atomic value) to the output
     * @param item           the item to be appended
     * @param locationId     the location of the calling instruction, for diagnostics
     * @param copyNamespaces if the item is an element node, this indicates whether its namespaces
*                       need to be copied. Values are {@link ReceiverOption#ALL_NAMESPACES}}; the default (0) means
     */

    @Override
    public void append(Item item, Location locationId, int copyNamespaces) throws XPathException {
        // Decompose the item into a sequence of node events if we're within a start/end element/document
        // pair. Otherwise, send the item down the pipeline unchanged: it's the job of the Destination
        // to deal with it (inserting item separators if appropriate)
        if (level >= 0) {
            decompose(item, locationId, copyNamespaces);
        } else {
            nextReceiver.append(item, locationId, copyNamespaces);
        }
    }

    /**
     * Get a string-value consumer object that allows an item of type xs:string
     * to be appended one fragment at a time. This potentially allows operations that
     * output large strings to avoid building the entire string in memory. This version
     * of the method, if called at an inner level, outputs the string as a sequence
     * of characters() events, with logic to include space separation where appropriate
     *
     * @param asTextNode set to true if the concatenated string values are to be treated as a text node
     * @param loc the location of the instruction that generates the content
     * @return an object that accepts xs:string values via a sequence of append() calls
     */

    @Override
    public CharSequenceConsumer getStringReceiver(boolean asTextNode, Location loc) {
        if (level >= 0) {
            return new CharSequenceConsumer() {

                @Override
                public void open() throws XPathException {
                    if (previousAtomic && !asTextNode) {
                        ComplexContentOutputter.this.characters(" ", loc, ReceiverOption.NONE);
                    }
                }

                @Override
                public CharSequenceConsumer cat(CharSequence chars) throws XPathException {
                    ComplexContentOutputter.this.characters(chars, loc, ReceiverOption.NONE);
                    return this;
                }

                @Override
                public void close() {
                    previousAtomic = !asTextNode;
                }
            };
        } else {
            return super.getStringReceiver(asTextNode, loc);
        }

    }

    /**
     * Close the output
     */

    @Override
    public void close() throws XPathException {
        // System.err.println("Close " + this + " using emitter " + emitter.getClass());
        nextReceiver.close();
        previousAtomic = false;
        state = Final;
    }

    /**
     * Flush out a pending start tag
     */

    @Override
    public void startContent() throws XPathException {

        if (state != StartTag) {
            // this can happen if the method is called from outside,
            // e.g. from a SequenceOutputter earlier in the pipeline
            return;
        }

        NodeName elcode = checkProposedPrefix(pendingStartTag, 0);
        int props = startElementProperties | ReceiverOption.NAMESPACE_OK;

        for (int a = 0; a < pendingAttributes.size(); a++) {
            NodeName oldName = pendingAttributes.get(a).getNodeName();
            if (!oldName.hasURI("")) {    // non-null prefix
                NodeName newName = checkProposedPrefix(oldName, a + 1);
                if (newName != oldName) {
                    AttributeInfo newInfo = pendingAttributes.get(a).withNodeName(newName);
                    pendingAttributes.set(a, newInfo);
                }
            }
        }

        NamespaceMap inherited = inheritedNamespaces.isEmpty() ? NamespaceMap.emptyMap() : inheritedNamespaces.peek();
        if (!ReceiverOption.contains(startElementProperties, ReceiverOption.REFUSE_NAMESPACES)) {
            pendingNSMap = inherited.putAll(pendingNSMap);
        }

        if (pendingStartTag.hasURI("") && !pendingNSMap.getDefaultNamespace().isEmpty()) {
            pendingNSMap = pendingNSMap.remove("");
        }

        AttributeMap attributes = AttributeMap.fromList(pendingAttributes);

        nextReceiver.startElement(elcode, currentSimpleType, attributes, pendingNSMap, startElementLocationId, props);

        boolean inherit = !ReceiverOption.contains(startElementProperties, ReceiverOption.DISINHERIT_NAMESPACES);
        inheritedNamespaces.push(inherit ? pendingNSMap : inherited);

        pendingAttributes.clear();
        pendingNSMap = NamespaceMap.emptyMap();
        previousAtomic = false;
        state = Content;
    }

    /**
     * Ask whether this Receiver (or the downstream pipeline) makes any use of the type annotations
     * supplied on element and attribute events
     *
     * @return true if the Receiver makes any use of this information. If false, the caller
     *         may supply untyped nodes instead of supplying the type annotation
     */

    @Override
    public boolean usesTypeAnnotations() {
        return nextReceiver.usesTypeAnnotations();
    }

    /**
     * Helper method for subclasses to invoke if required: flatten an array. The effect
     * is that each item in each member of the array is appended to this {@code ComplexContentOutputter}
     * by calling its {@link #append(Item) method}
     *
     * @param array          the array to be flattened
     * @param locationId     the location of the instruction triggering this operation
     * @param copyNamespaces options for copying namespace nodes
     * @throws XPathException if anything goes wrong
     */

    protected void flatten(ArrayItem array, Location locationId, int copyNamespaces) throws XPathException {
        for (Sequence member : array.members()) {
            member.iterate().forEachOrFail(it -> append(it, locationId, copyNamespaces));
        }
    }

    /**
     * Helper method for subclasses to invoke if required: decompose an item into a sequence
     * of node events. Note that when this is used, methods such as characters(), comment(),
     * startElement(), and processingInstruction() are responsible for setting previousAtomic to false.
     * @param item the item to be decomposed into a sequence of events
     * @param locationId the location of the requesting instruction
     * @param copyNamespaces options for copying namespace nodes
     */

    protected void decompose(Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (item != null) {
            if (item instanceof AtomicValue || item instanceof ExternalObject) {
                if (previousAtomic) {
                    characters(" ", locationId, ReceiverOption.NONE);
                }
                characters(item.getStringValueCS(), locationId, ReceiverOption.NONE);
                previousAtomic = true;
            } else if (item instanceof ArrayItem) {
                flatten((ArrayItem) item, locationId, copyNamespaces);
            } else if (item instanceof Function) {
                String thing = item instanceof MapItem ? "map" : "function item";
                String errorCode = getErrorCodeForDecomposingFunctionItems();
                if (errorCode.startsWith("SENR")) {
                    throw new XPathException("Cannot serialize a " + thing + " using this output method", errorCode, locationId);
                } else {
                    throw new XPathException("Cannot add a " + thing + " to an XDM node tree", errorCode, locationId);
                }
            } else {
                NodeInfo node = (NodeInfo) item;
                switch (node.getNodeKind()) {

                    case Type.TEXT:
                        int options = ReceiverOption.NONE;
                        if (node instanceof Orphan && ((Orphan) node).isDisableOutputEscaping()) {
                            options = ReceiverOption.DISABLE_ESCAPING;
                        }
                        characters(item.getStringValueCS(), locationId, options);
                        break;

                    case Type.ATTRIBUTE:
                        if (((SimpleType) node.getSchemaType()).isNamespaceSensitive()) {
                            XPathException err = new XPathException("Cannot copy attributes whose type is namespace-sensitive (QName or NOTATION): " +
                                                                            Err.wrap(node.getDisplayName(), Err.ATTRIBUTE));
                            err.setErrorCode(getPipelineConfiguration().isXSLT() ? "XTTE0950" : "XQTY0086");
                            throw err;
                        }
                        attribute(NameOfNode.makeName(node), (SimpleType) node.getSchemaType(), node.getStringValue(), locationId, ReceiverOption.NONE);
                        break;

                    case Type.NAMESPACE:
                        namespace(node.getLocalPart(), node.getStringValue(), ReceiverOption.NONE);
                        break;

                    case Type.DOCUMENT:
                        startDocument(ReceiverOption.NONE); // needed to ensure that illegal namespaces or attributes in the content are caught
                        for (NodeInfo child : node.children()) {
                            append(child, locationId, copyNamespaces);
                        }
                        endDocument();
                        break;

                    default:
                        int copyOptions = CopyOptions.TYPE_ANNOTATIONS;
                        if (ReceiverOption.contains(copyNamespaces, ReceiverOption.ALL_NAMESPACES)) {
                            copyOptions |= CopyOptions.ALL_NAMESPACES;
                        }
                        ((NodeInfo) item).copy(this, copyOptions, locationId);
                        break;
                }
                previousAtomic = false;


            }
        }
    }

    protected String getErrorCodeForDecomposingFunctionItems() {
        return getPipelineConfiguration().isXSLT() ? "XTDE0450" : "XQTY0105";
    }

}

