////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.ma.Parcel;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.ma.jnode.RootJNode;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.MapType;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.str.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.LocalUnionType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Implement the fn:path function
 */
public class PathFn extends SystemFunction implements IContextAccessorFunction {

    private Item boundContextItem;

    @Override
    public boolean dependsOnContext() {
        return getArity() == 0;
    }

    @Override
    public FunctionItem bindContext(XPathContext context) throws XPathException {
        boundContextItem = context.getContextItem();
        return this;
    }

    public static OptionsParameter getOptionDetails() {
//        ItemType inScopeNamespacesResult = new MapType(
//                LocalUnionType.of(BuiltInAtomicType.NCNAME, InScopeNamespacesFn.EMPTY_STRING_TYPE),
//                BuiltInAtomicType.ANY_URI.one());
        // TODO: this is a more liberal type than prescribed by the spec, because we don't yet do coercion on maps
        ItemType inScopeNamespacesResult = new MapType(
                BuiltInAtomicType.STRING,
                SequenceType.one(LocalUnionType.of(BuiltInAtomicType.STRING, BuiltInAtomicType.ANY_URI)));
        OptionsParameter pathOptions = new OptionsParameter(40);
        pathOptions.addAllowedOption("indexes", SequenceType.SINGLE_BOOLEAN, BooleanValue.TRUE);
        pathOptions.addAllowedOption("lexical", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        pathOptions.addAllowedOption("origin", SequenceType.OPTIONAL_GNODE, EmptySequence.INSTANCE);
        pathOptions.addAllowedOption("namespaces", SequenceType.optional(inScopeNamespacesResult), EmptySequence.INSTANCE);
        return pathOptions;
    }


    /**
     * Call the function.
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        GNode node;
        if (arguments.length == 0) {
            Item item = boundContextItem == null ? context.getContextItem() : boundContextItem;
            if (item instanceof Parcel) {
                GroundedValue val = ((Parcel)item).getValue();
                switch (val.getLength()) {
                    case 0:
                        return EmptySequence.INSTANCE;
                    case 1:
                        item = ((Parcel)item).getValue().head();
                        break;
                    default:
                        throw new XPathException("In call to fn:path(), the context value contains more than one item", "XPTY0004");
                }
            }
            if (item == null) {
                throw new XPathException("In call to fn:path(), the context item is absent", "XPDY0002");
            }
            if (!(item instanceof GNode)) {
                throw new XPathException("In call to fn:path(), the context item is not a node", "XPTY0004");
            }
            node = (GNode)item;
        } else {
            node = (GNode)arguments[0].head();
            if (node == null) {
                return EmptySequence.INSTANCE;
            }
        }
        boolean withIndexes = true;
        boolean lexical = false;
        GNode origin = null;
        Map<NamespaceUri, String> namespaces = Collections.emptyMap();
        if (arguments.length > 1) {
            MapItem options = (MapItem)arguments[1].head();
            Map<String, GroundedValue> checkedOptions = null;
            if (options != null) {
                checkedOptions = getDetails().optionDetails.processSuppliedOptions(options, context, 40);
                if (checkedOptions.containsKey("indexes")) {
                    withIndexes = checkedOptions.get("indexes").effectiveBooleanValue();
                }
                if (checkedOptions.containsKey("lexical")) {
                    lexical = checkedOptions.get("lexical").effectiveBooleanValue();
                }
                if (checkedOptions.containsKey("namespaces")) {
                    MapItem nsMap = (MapItem) checkedOptions.get("namespaces").head();
                    if (nsMap != null) {
                        namespaces = new HashMap<>();
                        for (KeyValuePair kvp : nsMap.keyValuePairs()) {
                            namespaces.put(NamespaceUri.of(kvp.value().getStringValue()), kvp.key().getStringValue());
                        }
                    }
                }
                if (checkedOptions.containsKey("origin")) {
                    origin = (GNode)checkedOptions.get("origin").head();
                }
            }

        }
        if (origin != null && (node instanceof NodeInfo) != (origin instanceof NodeInfo)) {
            // One is an XNode, the other is a JNode, so they can't be in the same tree
            throw new XPathException(
                    "In fn:path, the origin node must be in the same tree as the target node", "FOPA0001");
        }
        if (node instanceof NodeInfo) {
            return makePath((NodeInfo)node, withIndexes, lexical, namespaces, (NodeInfo)origin, context);
        } else {
            return makeJPath((JNode)node, (JNode)origin);
        }

    }

    /**
     * Get the path to an XNode
     * @param node the node whose path is required
     * @param withIndex true if index positions are to be included (in square brackets, after each element name)
     * @param lexical true if element and attribute names are to be output in lexical QName form
     * @param namespaces map from namespace URIs to prefixes. If a namespace is present in the map, the prefix
     *                   is used in preference to Q{uri}local notation
     * @param context the dynamic evaluation context
     * @return the path, as a StringValue.
     */
    public static StringValue makePath(NodeInfo node, boolean withIndex, boolean lexical,
                                       Map<NamespaceUri, String> namespaces, NodeInfo origin, XPathContext context)
            throws XPathException {
        if (node.getNodeKind() == Type.DOCUMENT && origin == null) {
            return StringValue.makeStringValue("/");
        }
        StringBuilder fsb = new StringBuilder(256);
        SequenceIterator iter = node.iterateAncestorOrSelfAxis(AnyGNode.TEST);
        NodeInfo n;
        boolean first = true;
        while ((n = (NodeInfo)iter.next()) != null) {
            if (origin != null && n.isSameNodeInfo(origin) && !first) {
                String path = fsb.toString();
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                return new StringValue(path);
            }
            first = false;
            if (n.getParent() == null) {
                if (origin != null) {
                    throw new XPathException(
                            "In fn:path, the origin node must be an ancestor of the target node", "FOPA0001");
                }
                if (n.getNodeKind() == Type.DOCUMENT) {
                    return new StringValue(fsb.toString());
                } else if (lexical) {
                    fsb.insert(0, "fn:root()");
                    return new StringValue(fsb.toString());
                } else {
                    fsb.insert(0, qualifier(NamespaceUri.FN, namespaces) + "root()");
                    return new StringValue(fsb.toString());
                }
            }
            StringBuilder fsb2 = new StringBuilder(256);
            switch (n.getNodeKind()) {
                case Type.DOCUMENT:
                    return new StringValue(fsb.toString());
                case Type.ELEMENT:
                    if (lexical) {
                        fsb2.append("/").append(n.getDisplayName());
                    } else {
                        fsb2.append("/")
                                .append(qualifier(n.getNamespaceUri(), namespaces))
                                .append(n.getLocalPart());
                    }
                    if (withIndex) {
                        fsb2.append("[").append(Navigator.getNumberSimple(n, context)).append("]");
                    }
                    fsb2.append(fsb);
                    fsb = fsb2;
                    break;
                case Type.ATTRIBUTE:
                    fsb2.append("/@");
                    if (lexical) {
                        fsb2.append(n.getDisplayName());
                    } else {
                        if (n.getNamespaceUri() != NamespaceUri.NULL) {
                            fsb2.append(qualifier(n.getNamespaceUri(), namespaces));
                        }
                        fsb2.append(n.getLocalPart());
                    }
                    fsb2.append(fsb);
                    fsb = fsb2;
                    break;
                case Type.TEXT:
                    fsb2.append("/text()");
                    if (withIndex) {
                        fsb2.append("[").append(Navigator.getNumberSimple(n, context)).append("]");
                    }
                    fsb2.append(fsb);
                    fsb = fsb2;
                    break;
                case Type.COMMENT:
                    fsb2.append("/comment()");
                    if (withIndex) {
                        fsb2.append("[").append(Navigator.getNumberSimple(n, context)).append("]");
                    }
                    fsb2.append(fsb);
                    fsb = fsb2;
                    break;
                case Type.PROCESSING_INSTRUCTION:
                    fsb2.append("/processing-instruction(").append(n.getLocalPart()).append(")");
                    if (withIndex) {
                        fsb2.append("[").append(Navigator.getNumberSimple(n, context)).append("]");
                    }
                    fsb2.append(fsb);
                    fsb = fsb2;
                    break;
                case Type.NAMESPACE:
                    fsb2.append("/namespace::");
                    if (n.getLocalPart().isEmpty()) {
                        fsb2.append("*[").append(qualifier(NamespaceUri.FN, namespaces)).append("local-name()=\"\"]");
                    } else {
                        fsb2.append(n.getLocalPart());
                    }
                    fsb2.append(fsb);
                    fsb = fsb2;
                    break;
                default:
                    throw new AssertionError();
            }
        }
        // should not reach here...
        fsb.insert(0, "Q{http://www.w3.org/2005/xpath-functions}root()");
        return new StringValue(fsb.toString());
    }

    private static String qualifier(NamespaceUri uri, Map<NamespaceUri, String> namespaces) {
        if (namespaces.containsKey(uri)) {
            String prefix = namespaces.get(uri);
            if (prefix.isEmpty()) {
                return prefix;
            } else {
                return prefix + ":";
            }
        } else {
            return "Q{" + uri.toString() + "}";
        }
    }

    private final static UnicodeChar SLASH = new UnicodeChar('/');
    private final static UnicodeChar QUOT = new UnicodeChar('"');

    /**
     * Get the path to an XNode
     *
     * @param node       the node whose path is required
     * @return the path, as a StringValue.
     */
    public static StringValue makeJPath(JNode node, JNode origin)
            throws XPathException {
        if (node instanceof RootJNode) {
            return StringValue.makeStringValue("/");
        }
        UnicodeString buffer = ZenoString.EMPTY;
        SequenceIterator iter = node.iterateAncestorOrSelfAxis(AnyGNode.TEST);
        JNode n;
        boolean first = true;
        boolean foundOrigin = origin == null;
        while ((n = (JNode) iter.next()) != null) {
            if (n.equals(origin)) {
                foundOrigin = true;
                if (!first) {
                    if (buffer.codePointAt(0) == '/') {
                        buffer = buffer.substring(1);
                    }
                    return new StringValue(buffer);
                }
            }
            first = false;
            if (n instanceof RootJNode) {
                if (!foundOrigin) {
                    throw new XPathException(
                            "In fn:path, the origin node must be an ancestor of the target node", "FOPA0001");
                }
                return new StringValue(buffer);
            }
            if (n.getParent().getContent() instanceof ArrayItem) {
                UnicodeString step = new Twine8("/*[").concat(n.getSelector().getUnicodeStringValue()).concat(new UnicodeChar(']'));
                buffer = step.concat(buffer);
            } else {
                AtomicValue selector = n.getSelector();
                UnicodeString selectorStr = selector.getUnicodeStringValue();
                if (selector instanceof StringValue && NameChecker.isValidNCName(selectorStr.codePoints())) {
                    buffer = SLASH.concat(selectorStr).concat(buffer);
                } else if (selector instanceof StringValue) {
                    buffer = SLASH.concat(new Twine8("get(\"")).concat(selectorStr).concat(new Twine8("\")").concat(buffer));
                } else if (selector instanceof NumericValue) {
                    buffer = SLASH.concat(new Twine8("get(")).concat(selectorStr).concat(new UnicodeChar(')').concat(buffer));
                } else if (selector instanceof QNameValue) {
                    buffer = SLASH.concat(StringView.of(((QNameValue) selector).getStructuredQName().getEQName()))
                                   .concat(buffer);
                } else if (selector instanceof BooleanValue) {
                    if (((BooleanValue) selector).getBooleanValue()) {
                        buffer = new Twine8("/get(true())").concat(buffer);
                    } else {
                        buffer = new Twine8("/get(false())").concat(buffer);
                    }
                } else {
                    buffer = new Twine8("/get(")
                            .concat(StringView.of(selector.getPrimitiveType().getDisplayName()))
                            .concat(new Twine8("(\""))
                            .concat(selectorStr)
                            .concat(new Twine8("\"))"))
                            .concat(buffer);
                }
            }
        }
        if (n.getParent() == null) {
            if (origin != null) {
                throw new XPathException(
                        "In fn:path, the origin node must be an ancestor of the target node", "FOPA0001");
            }
        }
        return new StringValue(buffer);

    }


}

// Copyright (c) 2012-2026 Saxonica Limited

