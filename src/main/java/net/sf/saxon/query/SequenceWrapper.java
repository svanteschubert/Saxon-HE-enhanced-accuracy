////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.ObjectValue;

/**
 * This class can be used in a push pipeline: it accepts any sequence as input, and generates
 * a document in which the items of the sequence are wrapped by elements containing information about
 * the types of the items in the input sequence.
 */

public class SequenceWrapper extends SequenceReceiver {

    public static final String RESULT_NS = QueryResult.RESULT_NS;

    private ComplexContentOutputter out;
    private int depth = 0;

    private FingerprintedQName resultDocument;
    private FingerprintedQName resultElement;
    private FingerprintedQName resultAttribute;
    private FingerprintedQName resultText;
    private FingerprintedQName resultComment;
    private FingerprintedQName resultPI;
    private FingerprintedQName resultNamespace;
    private FingerprintedQName resultAtomicValue;
    private FingerprintedQName resultFunction;
    private FingerprintedQName resultArray;
    private FingerprintedQName resultMap;
    private FingerprintedQName resultExternalValue;
    private FingerprintedQName xsiType;

    private NamespaceMap namespaces;

    /**
     * Create a sequence wrapper. This creates an XML representation of the items sent to destination
     * in which the types of all items are made explicit
     *
     * @param destination the sequence being wrapped
     */

    public SequenceWrapper(Receiver destination) {
        super(destination.getPipelineConfiguration());
        out = new ComplexContentOutputter(destination);
        // out = new TracingFilter(out);
    }

    public ComplexContentOutputter getDestination() {
        return out;
    }

    private void startWrapper(NodeName name) throws XPathException {
        out.startElement(name, Untyped.getInstance(),
                         Loc.NONE,
                         ReceiverOption.NONE);
        out.namespace("", "", ReceiverOption.NONE);
        out.startContent();
    }

    private void endWrapper() throws XPathException {
        out.endElement();
    }

    @Override
    public void open() throws XPathException {

        //@SuppressWarnings({"FieldCanBeLocal"})
        FingerprintedQName resultSequence = new FingerprintedQName("result", RESULT_NS, "sequence");
        resultDocument = new FingerprintedQName("result", RESULT_NS, "document");
        resultElement = new FingerprintedQName("result", RESULT_NS, "element");
        resultAttribute = new FingerprintedQName("result", RESULT_NS, "attribute");
        resultText = new FingerprintedQName("result", RESULT_NS, "text");
        resultComment = new FingerprintedQName("result", RESULT_NS, "comment");
        resultPI = new FingerprintedQName("result", RESULT_NS, "processing-instruction");
        resultNamespace = new FingerprintedQName("result", RESULT_NS, "namespace");
        resultAtomicValue = new FingerprintedQName("result", RESULT_NS, "atomic-value");
        resultFunction = new FingerprintedQName("result", RESULT_NS, "function");
        resultArray = new FingerprintedQName("result", RESULT_NS, "array");
        resultMap = new FingerprintedQName("result", RESULT_NS, "map");
        resultExternalValue = new FingerprintedQName("result", RESULT_NS, "external-object");
        xsiType = new FingerprintedQName("xsi", NamespaceConstant.SCHEMA_INSTANCE, "type");

        out.open();
        out.startDocument(ReceiverOption.NONE);

        namespaces = NamespaceMap.emptyMap()
                .put("result", RESULT_NS)
                .put("xs", NamespaceConstant.SCHEMA)
                .put("xsi", NamespaceConstant.SCHEMA_INSTANCE);

        startWrapper(resultSequence);

    }

    /**
     * Start of a document node.
     * @param properties properties of the document node
     */

    @Override
    public void startDocument(int properties) throws XPathException {
        startWrapper(resultDocument);
        depth++;
    }

    /**
     * Notify the end of a document node
     */

    @Override
    public void endDocument() throws XPathException {
        endWrapper();
        depth--;
    }

    /**
     * Notify the start of an element
     */

    @Override
    public void startElement(NodeName elemName, SchemaType type,
                             AttributeMap attributes, NamespaceMap namespaces,
                             Location location, int properties) throws XPathException {
        if (depth++ == 0) {
            startWrapper(resultElement);
        }
        out.startElement(elemName, type, location, properties);
        out.namespace("", "", properties);
        for (AttributeInfo att : attributes) {
            out.attribute(att.getNodeName(), att.getType(), att.getValue(),
                          att.getLocation(), att.getProperties());
        }
        out.startContent();
    }

    /**
     * End of element
     */

    @Override
    public void endElement() throws XPathException {
        out.endElement();
        if (--depth == 0) {
            endWrapper();
        }
    }


    /**
     * Character data
     */

    @Override
    public void characters(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (depth == 0) {
            startWrapper(resultText);
            out.characters(chars, locationId, properties);
            endWrapper();
        } else {
            out.characters(chars, locationId, properties);
        }
    }

    /**
     * Output a comment
     */

    @Override
    public void comment(CharSequence chars, Location locationId, int properties) throws XPathException {
        if (depth == 0) {
            startWrapper(resultComment);
            out.comment(chars, locationId, properties);
            endWrapper();
        } else {
            out.comment(chars, locationId, properties);
        }
    }

    /**
     * Processing Instruction
     */

    @Override
    public void processingInstruction(String target, CharSequence data, Location locationId, int properties) throws XPathException {
        if (depth == 0) {
            startWrapper(resultPI);
            out.processingInstruction(target, data, locationId, properties);
            endWrapper();
        } else {
            out.processingInstruction(target, data, locationId, properties);
        }
    }

    /**
     * Output an item (atomic value or node) to the sequence
     */

    @Override
    public void append(/*@NotNull*/ Item item, Location locationId, int copyNamespaces) throws XPathException {
        if (item instanceof AtomicValue) {
            final NamePool pool = getNamePool();
            out.startElement(resultAtomicValue, Untyped.getInstance(), Loc.NONE, ReceiverOption.NONE);
            out.namespace("", "", ReceiverOption.NONE);
            AtomicType type = ((AtomicValue) item).getItemType();
            StructuredQName name = type.getStructuredQName();
            String prefix = name.getPrefix();
            String localName = name.getLocalPart();
            String uri = name.getURI();
            if (prefix.isEmpty()) {
                prefix = pool.suggestPrefixForURI(uri);
                if (prefix == null) {
                    prefix = "p" + uri.hashCode();
                }
            }
            String displayName = prefix + ':' + localName;
            out.namespace("", "", ReceiverOption.NONE);
            out.namespace(prefix, uri, ReceiverOption.NONE);
            out.attribute(xsiType, BuiltInAtomicType.UNTYPED_ATOMIC, displayName, locationId, ReceiverOption.NONE);
            out.startContent();
            out.characters(item.getStringValue(), locationId, ReceiverOption.NONE);
            out.endElement();
        } else if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo)item;
            int kind = node.getNodeKind();
            if (kind == Type.ATTRIBUTE) {
                attribute(NameOfNode.makeName(node), (SimpleType)node.getSchemaType(), node.getStringValueCS(), Loc.NONE, 0);
            } else if (kind == Type.NAMESPACE) {
                namespace(new NamespaceBinding(node.getLocalPart(), node.getStringValue()), 0);
            } else {
                ((NodeInfo) item).copy(this, CopyOptions.ALL_NAMESPACES | CopyOptions.TYPE_ANNOTATIONS, locationId);
            }
        } else if (item instanceof Function) {
            if (item instanceof MapItem) {
                out.startElement(resultMap, Untyped.getInstance(), Loc.NONE, ReceiverOption.NONE);
                out.startContent();
                out.characters(item.toShortString(), locationId, ReceiverOption.NONE);
                out.endElement();
            } else if (item instanceof ArrayItem) {
                out.startElement(resultArray, Untyped.getInstance(), Loc.NONE, ReceiverOption.NONE);
                out.startContent();
                out.characters(item.toShortString(), locationId, ReceiverOption.NONE);
                out.endElement();
            } else {
                out.startElement(resultFunction, Untyped.getInstance(), Loc.NONE, ReceiverOption.NONE);
                out.startContent();
                out.characters(((Function)item).getDescription(), locationId, ReceiverOption.NONE);
                out.endElement();
            }
        } else if (item instanceof ObjectValue) {
            Object obj = ((ObjectValue)item).getObject();
            out.startElement(resultExternalValue, Untyped.getInstance(), Loc.NONE, ReceiverOption.NONE);
            out.attribute(new NoNamespaceName("class"), BuiltInAtomicType.UNTYPED_ATOMIC,
                          obj.getClass().getName(), Loc.NONE, ReceiverOption.NONE);
            out.startContent();
            out.characters(obj.toString(), locationId, ReceiverOption.NONE);
            out.endElement();
        }
    }

    /**
     * Notify the end of the event stream
     */

    @Override
    public void close() throws XPathException {
        endWrapper();   // close the result:sequence element
        out.endDocument();
        out.close();
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
        return true;
    }

    /**
     * Notify an attribute. Attributes are notified after the startElement event, and before any
     * children. Namespaces and attributes may be intermingled.
     *
     * @param attName    The name of the attribute
     * @param typeCode   The type of the attribute
     * @param locationId location of the attribute
     * @param properties Bit significant value. The following bits are defined:
     *                   <dl>
     *                   <dd>DISABLE_ESCAPING</dd>    <dt>Disable escaping for this attribute</dt>
     *                   <dd>NO_SPECIAL_CHARACTERS</dd>      <dt>Attribute value contains no special characters</dt>
     *                   </dl>
     * @throws IllegalStateException: attempt to output an attribute when there is no open element
     *                                start tag
     */

    private void attribute(NodeName attName, SimpleType typeCode, CharSequence value, Location locationId, int properties) throws XPathException {
        AttributeMap atts = SingletonAttributeMap.of(new AttributeInfo(
            attName, typeCode, value.toString(), locationId, properties));
        NamespaceMap ns = NamespaceMap.emptyMap();
        if (!attName.hasURI("")) {
            ns = ns.put(attName.getPrefix(), attName.getURI());
        }
        out.startElement(resultAttribute, Untyped.getInstance(), atts, ns, Loc.NONE, 0);
        out.startContent();
        out.endElement();
    }

    /**
     * Notify a namespace. Namespaces are notified <b>after</b> the startElement event, and before
     * any children for the element. The namespaces that are reported are only required
     * to include those that are different from the parent element; however, duplicates may be reported.
     * A namespace must not conflict with any namespaces already used for element or attribute names.
     *
     * @param namespaceBindings the namespace binding or bindings being notified
     * @throws IllegalStateException: attempt to output a namespace when there is no open element
     *                                start tag
     */

    private void namespace(NamespaceBindingSet namespaceBindings, int properties) throws XPathException {
        NamespaceMap ns = NamespaceMap.emptyMap();
        ns = ns.addAll(namespaceBindings);
        out.startElement(resultNamespace, Untyped.getInstance(), EmptyAttributeMap.getInstance(), ns, Loc.NONE, 0);
        out.startContent();
        out.endElement();
    }

}

