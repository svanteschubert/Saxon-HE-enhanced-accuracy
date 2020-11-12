////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.SameNameTest;
import net.sf.saxon.trans.XmlProcessingIncident;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.tiny.WhitespaceTextImpl;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.ComplexType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * XSLT 2.0 deep-equal() function, where the collation is already known.
 * Supports deep comparison of two sequences (of nodes and/or atomic values)
 * optionally using a collation
 */

public class DeepEqual extends CollatingFunctionFixed {

    /**
     * Flag indicating that two elements should only be considered equal if they have the same
     * in-scope namespaces
     */
    public static final int INCLUDE_NAMESPACES = 1;

    /**
     * Flag indicating that two element or attribute nodes are considered equal only if their
     * names use the same namespace prefix
     */
    public static final int INCLUDE_PREFIXES = 1 << 1;

    /**
     * Flag indicating that comment children are taken into account when comparing element or document nodes
     */
    public static final int INCLUDE_COMMENTS = 1 << 2;

    /**
     * Flag indicating that processing instruction nodes are taken into account when comparing element or document nodes
     */
    public static final int INCLUDE_PROCESSING_INSTRUCTIONS = 1 << 3;

    /**
     * Flag indicating that whitespace text nodes are ignored when comparing element nodes
     */
    public static final int EXCLUDE_WHITESPACE_TEXT_NODES = 1 << 4;

    /**
     * Flag indicating that elements and attributes should always be compared according to their string
     * value, not their typed value
     */
    public static final int COMPARE_STRING_VALUES = 1 << 5;

    /**
     * Flag indicating that elements and attributes must have the same type annotation to be considered
     * deep-equal
     */
    public static final int COMPARE_ANNOTATIONS = 1 << 6;

    /**
     * Flag indicating that a warning message explaining the reason why the sequences were deemed non-equal
     * should be sent to the ErrorListener
     */
    public static final int WARNING_IF_FALSE = 1 << 7;

    /**
     * Flag indicating that adjacent text nodes in the top-level sequence are to be merged
     */

    public static final int JOIN_ADJACENT_TEXT_NODES = 1 << 8;

    /**
     * Flag indicating that the is-id and is-idref flags are to be compared
     */

    public static final int COMPARE_ID_FLAGS = 1 << 9;

    /**
     * Flag indicating that the variety of the type of a node is to be ignored (for example, a mixed content
     * node can compare equal to an element-only content node
     */

    public static final int EXCLUDE_VARIETY = 1 << 10;


    /**
     * Determine when two sequences are deep-equal
     *
     * @param op1      the first sequence
     * @param op2      the second sequence
     * @param comparer the comparer to be used
     * @param context  the XPathContext item
     * @param flags    bit-significant integer giving comparison options. Always zero for standard
     *                 F+O deep-equals comparison.
     * @return true if the sequences are deep-equal
     * @throws XPathException if either sequence contains a function item
     */

    public static boolean deepEqual(SequenceIterator op1, SequenceIterator op2,
                                    AtomicComparer comparer, XPathContext context, int flags)
            throws XPathException {
        boolean result = true;
        String reason = null;
        ErrorReporter reporter = context.getErrorReporter();

        try {

            if ((flags & JOIN_ADJACENT_TEXT_NODES) != 0) {
                op1 = mergeAdjacentTextNodes(op1);
                op2 = mergeAdjacentTextNodes(op2);
            }
            int pos1 = 0;
            int pos2 = 0;
            while (true) {
                Item item1 = op1.next();
                Item item2 = op2.next();

                if (item1 == null && item2 == null) {
                    break;
                }

                pos1++;
                pos2++;

                if (item1 == null || item2 == null) {
                    result = false;
                    if (item1 == null) {
                        reason = "Second sequence is longer (first sequence length = " + pos2 + ")";
                    } else {
                        reason = "First sequence is longer (second sequence length = " + pos1 + ")";
                    }
                    if (item1 instanceof WhitespaceTextImpl || item2 instanceof WhitespaceTextImpl) {
                        reason += " (the first extra node is whitespace text)";
                    }
                    break;
                }

                if (item1 instanceof Function || item2 instanceof Function) {
                    if (!(item1 instanceof Function && item2 instanceof Function)) {
                        reason = "if one item is a function then both must be functions (position " + pos1 + ")";
                        return false;
                    }
                    // two maps or arrays can be deep-equal
                    boolean fe = ((Function) item1).deepEquals((Function) item2, context, comparer, flags);
                    if (!fe) {
                        result = false;
                        reason = "functions at position " + pos1 + " differ";
                        break;
                    }
                    continue;
                }

                if (item1 instanceof ObjectValue || item2 instanceof ObjectValue) {
                    if (!item1.equals(item2)) {
                        return false;
                    }
                    continue;
                }

                if (item1 instanceof NodeInfo) {
                    if (item2 instanceof NodeInfo) {
                        if (!deepEquals((NodeInfo) item1, (NodeInfo) item2, comparer, context, flags)) {
                            result = false;
                            reason = "nodes at position " + pos1 + " differ";
                            break;
                        }
                    } else {
                        result = false;
                        reason = "comparing a node to an atomic value at position " + pos1;
                        break;
                    }
                } else {
                    if (item2 instanceof NodeInfo) {
                        result = false;
                        reason = "comparing an atomic value to a node at position " + pos1;
                        break;
                    } else {
                        AtomicValue av1 = (AtomicValue) item1;
                        AtomicValue av2 = (AtomicValue) item2;
                        if (av1.isNaN() && av2.isNaN()) {
                            // treat as equal, no action
                        } else if (!comparer.comparesEqual(av1, av2)) {
                            result = false;
                            reason = "atomic values at position " + pos1 + " differ";
                            break;
                        }
                    }
                }
            } // end while

        } catch (ClassCastException err) {
            // this will happen if the sequences contain non-comparable values
            // comparison errors are masked
            //err.printStackTrace();
            result = false;
            reason = "sequences contain non-comparable values";
        }

        if (!result) {
            explain(reporter, reason, flags, null, null);
            //                config.getErrorReporter().warning(
            //                        new XPathException("deep-equal(): " + reason)
            //                );
        }

        return result;
    }

    /*
      * Determine whether two nodes are deep-equal
      */

    public static boolean deepEquals(NodeInfo n1, NodeInfo n2,
                                      AtomicComparer comparer, XPathContext context, int flags)
            throws XPathException {
        // shortcut: a node is always deep-equal to itself
        if (n1.equals(n2)) {
            return true;
        }

        ErrorReporter reporter = context.getErrorReporter();

        if (n1.getNodeKind() != n2.getNodeKind()) {
            explain(reporter, "node kinds differ: comparing " + showKind(n1) + " to " + showKind(n2), flags, n1, n2);
            return false;
        }

        switch (n1.getNodeKind()) {
            case Type.ELEMENT:
                if (!Navigator.haveSameName(n1, n2)) {
                    explain(reporter, "element names differ: " + NameOfNode.makeName(n1).getStructuredQName().getEQName() +
                            " != " + NameOfNode.makeName(n2).getStructuredQName().getEQName(), flags, n1, n2);
                    return false;
                }
                if (((flags & INCLUDE_PREFIXES) != 0) && !n1.getPrefix().equals(n2.getPrefix())) {
                    explain(reporter, "element prefixes differ: " + n1.getPrefix() +
                            " != " + n2.getPrefix(), flags, n1, n2);
                    return false;
                }
                AxisIterator a1 = n1.iterateAxis(AxisInfo.ATTRIBUTE);
                AxisIterator a2 = n2.iterateAxis(AxisInfo.ATTRIBUTE);
                if (!SequenceTool.sameLength(a1, a2)) {
                    explain(reporter, "elements have different number of attributes", flags, n1, n2);
                    return false;
                }
                NodeInfo att1;
                a1 = n1.iterateAxis(AxisInfo.ATTRIBUTE);
                while ((att1 = a1.next()) != null) {
                    AxisIterator a2iter = n2.iterateAxis(AxisInfo.ATTRIBUTE,
                            new SameNameTest(att1));
                    NodeInfo att2 = a2iter.next();

                    if (att2 == null) {
                        explain(reporter, "one element has an attribute " +
                            NameOfNode.makeName(att1).getStructuredQName().getEQName() +
                                ", the other does not", flags, n1, n2);
                        return false;
                    }
                    if (!deepEquals(att1, att2, comparer, context, flags)) {
                        deepEquals(att1, att2, comparer, context, flags);
                        explain(reporter, "elements have different values for the attribute " +
                            NameOfNode.makeName(att1).getStructuredQName().getEQName(), flags, n1, n2);
                        return false;
                    }
                }
                if ((flags & INCLUDE_NAMESPACES) != 0) {                // TODO: SIMPLIFY THIS!!!
                    HashSet<NamespaceBinding> ns1 = new HashSet<>(10);
                    HashSet<NamespaceBinding> ns2 = new HashSet<>(10);
                    AxisIterator it1 = n1.iterateAxis(AxisInfo.NAMESPACE);
                    NodeInfo nn1;
                    while ((nn1 = it1.next()) != null) {
                        NamespaceBinding nscode1 = new NamespaceBinding(nn1.getLocalPart(), nn1.getStringValue());
                        ns1.add(nscode1);
                    }
                    AxisIterator it2 = n2.iterateAxis(AxisInfo.NAMESPACE);
                    NodeInfo nn2;
                    while ((nn2 = it2.next()) != null) {
                        NamespaceBinding nscode2 = new NamespaceBinding(nn2.getLocalPart(), nn2.getStringValue());
                        ns2.add(nscode2);
                    }
                    if (!ns1.equals(ns2)) {
                        explain(reporter, "elements have different in-scope namespaces: " +
                            showNamespaces(ns1) + " versus " + showNamespaces(ns2), flags, n1, n2);
                        return false;
                    }
                }

                if ((flags & COMPARE_ANNOTATIONS) != 0) {
                    if (!n1.getSchemaType().equals(n2.getSchemaType())) {
                        explain(reporter, "elements have different type annotation", flags, n1, n2);
                        return false;
                    }
                }

                if ((flags & EXCLUDE_VARIETY) == 0) {
                    if (n1.getSchemaType().isComplexType() != n2.getSchemaType().isComplexType()) {
                        explain(reporter, "one element has complex type, the other simple", flags, n1, n2);
                        return false;
                    }

                    if (n1.getSchemaType().isComplexType()) {
                        int variety1 = ((ComplexType) n1.getSchemaType()).getVariety();
                        int variety2 = ((ComplexType) n2.getSchemaType()).getVariety();
                        if (variety1 != variety2) {
                            explain(reporter, "both elements have complex type, but a different variety", flags, n1, n2);
                            return false;
                        }
                    }
                }

                if ((flags & COMPARE_STRING_VALUES) == 0) {
                    final SchemaType type1 = n1.getSchemaType();
                    final SchemaType type2 = n2.getSchemaType();
                    final boolean isSimple1 = type1.isSimpleType() || ((ComplexType) type1).isSimpleContent();
                    final boolean isSimple2 = type2.isSimpleType() || ((ComplexType) type2).isSimpleContent();
                    if (isSimple1 != isSimple2) {
                        explain(reporter, "one element has a simple type, the other does not", flags, n1, n2);
                        return false;
                    }
                    if (isSimple1) {
                        assert isSimple2;
                        final AtomicIterator v1 = n1.atomize().iterate();
                        final AtomicIterator v2 = n2.atomize().iterate();
                        return deepEqual(v1, v2, comparer, context, flags);
                    }
                }

                if ((flags & COMPARE_ID_FLAGS) != 0) {
                    if (n1.isId() != n2.isId()) {
                        explain(reporter, "one element is an ID, the other is not", flags, n1, n2);
                        return false;
                    }
                    if (n1.isIdref() != n2.isIdref()) {
                        explain(reporter, "one element is an IDREF, the other is not", flags, n1, n2);
                        return false;
                    }
                }
                // fall through
            case Type.DOCUMENT:
                AxisIterator c1 = n1.iterateAxis(AxisInfo.CHILD);
                AxisIterator c2 = n2.iterateAxis(AxisInfo.CHILD);
                while (true) {
                    NodeInfo d1 = c1.next();
                    while (d1 != null && isIgnorable(d1, flags)) {
                        d1 = c1.next();
                    }
                    NodeInfo d2 = c2.next();
                    while (d2 != null && isIgnorable(d2, flags)) {
                        d2 = c2.next();
                    }
                    if (d1 == null || d2 == null) {
                        boolean r = d1 == d2;
                        if (!r) {
                            String message = "the first operand contains a node with " +
                                (d1 == null ? "fewer" : "more") +
                                " children than the second";
                            if (d1 instanceof WhitespaceTextImpl || d2 instanceof WhitespaceTextImpl) {
                                message += " (the first extra child is whitespace text)";
                            }
                            explain(reporter, message, flags, n1, n2);
                        }
                        return r;
                    }
                    if (!deepEquals(d1, d2, comparer, context, flags)) {
                        return false;
                    }
                }

            case Type.ATTRIBUTE:
                if (!Navigator.haveSameName(n1, n2)) {
                    explain(reporter, "attribute names differ: " +
                        NameOfNode.makeName(n1).getStructuredQName().getEQName() +
                            " != " + NameOfNode.makeName(n1).getStructuredQName().getEQName(), flags, n1, n2);
                    return false;
                }
                if (((flags & INCLUDE_PREFIXES) != 0) && !n1.getPrefix().equals(n2.getPrefix())) {
                    explain(reporter, "attribute prefixes differ: " + n1.getPrefix() +
                            " != " + n2.getPrefix(), flags, n1, n2);
                    return false;
                }
                if ((flags & COMPARE_ANNOTATIONS) != 0) {
                    if (!n1.getSchemaType().equals(n2.getSchemaType())) {
                        explain(reporter, "attributes have different type annotations", flags, n1, n2);
                        return false;
                    }
                }
                boolean ar;
                if ((flags & COMPARE_STRING_VALUES) == 0) {
                    ar = deepEqual(n1.atomize().iterate(), n2.atomize().iterate(), comparer, context, 0);
                } else {
                    ar = comparer.comparesEqual(
                            new StringValue(n1.getStringValueCS()),
                            new StringValue(n2.getStringValueCS()));
                }
                if (!ar) {
                    explain(reporter, "attribute values differ", flags, n1, n2);
                    return false;
                }
                if ((flags & COMPARE_ID_FLAGS) != 0) {
                    if (n1.isId() != n2.isId()) {
                        explain(reporter, "one attribute is an ID, the other is not", flags, n1, n2);
                        return false;
                    }
                    if (n1.isIdref() != n2.isIdref()) {
                        explain(reporter, "one attribute is an IDREF, the other is not", flags, n1, n2);
                        return false;
                    }
                }
                return true;


            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                if (!n1.getLocalPart().equals(n2.getLocalPart())) {
                    explain(reporter, Type.displayTypeName(n1) + " names differ", flags, n1, n2);
                    return false;
                }
                // drop through
            case Type.TEXT:
            case Type.COMMENT:
                boolean vr = comparer.comparesEqual((AtomicValue) n1.atomize(), (AtomicValue) n2.atomize());
                if (!vr && ((flags & WARNING_IF_FALSE) != 0)) {
                    String v1 = n1.atomize().getStringValue();
                    String v2 = n2.atomize().getStringValue();
                    String message = "";
                    if (v1.length() != v2.length()) {
                        message = "lengths (" + v1.length() + "," + v2.length() + ")";
                    }
                    if (v1.length() < 10 && v2.length() < 10) {
                        message = " (\"" + v1 + "\" vs \"" + v2 + "\")";
                    } else {
                        int min = Math.min(v1.length(), v2.length());

                        if (v1.substring(0, min).equals(v2.substring(0, min))) {
                            message += " different at char " + min + "(\"" +
                                StringValue.diagnosticDisplay((v1.length() > v2.length() ? v1 : v2).substring(min)) + "\")";
                        } else if (v1.charAt(0) != v2.charAt(0)) {
                            message += " different at start " + "(\"" +
                                v1.substring(0, Math.min(v1.length(), 10)) + "\", \"" +
                                v2.substring(0, Math.min(v2.length(), 10)) + "\")";
                        } else {
                            for (int i = 1; i < min; i++) {
                                if (!v1.substring(0, i).equals(v2.substring(0, i))) {
                                    message += " different at char " + (i - 1) + "(\"" +
                                        v1.substring(i - 1, Math.min(v1.length(), i + 10)) + "\", \"" +
                                        v2.substring(i - 1, Math.min(v2.length(), i + 10)) + "\")";
                                    break;
                                }
                            }
                        }
                    }
                    explain(reporter, Type.displayTypeName(n1) + " values differ (" +
                            Navigator.getPath(n1) + ", " + Navigator.getPath(n2) + "): " +
                            message, flags, n1, n2);
                }
                return vr;

            default:
                throw new IllegalArgumentException("Unknown node type");
        }
    }

    private static boolean isIgnorable(NodeInfo node, int flags) {
        final int kind = node.getNodeKind();
        if (kind == Type.COMMENT) {
            return (flags & INCLUDE_COMMENTS) == 0;
        } else if (kind == Type.PROCESSING_INSTRUCTION) {
            return (flags & INCLUDE_PROCESSING_INSTRUCTIONS) == 0;
        } else if (kind == Type.TEXT) {
            return ((flags & EXCLUDE_WHITESPACE_TEXT_NODES) != 0) &&
                    Whitespace.isWhite(node.getStringValueCS());
        }
        return false;
    }

    private static void explain(ErrorReporter reporter, String message, int flags, NodeInfo n1, NodeInfo n2) {
        if ((flags & WARNING_IF_FALSE) != 0) {
            reporter.report(new XmlProcessingIncident("deep-equal() " +
                    (n1 != null && n2 != null ?
                            "comparing " + Navigator.getPath(n1) + " to " + Navigator.getPath(n2) + ": " :
                            ": ") +
                    message).asWarning());
        }
    }

    private static String showKind(Item item) {
        if (item instanceof NodeInfo && ((NodeInfo) item).getNodeKind() == Type.TEXT &&
            Whitespace.isWhite(item.getStringValueCS())) {
            return "whitespace text() node";
        } else {
            return Type.displayTypeName(item);
        }
    }

    private static String showNamespaces(HashSet<NamespaceBinding> bindings) {
        FastStringBuffer sb = new FastStringBuffer(256);
        for (NamespaceBinding binding : bindings) {
            sb.append(binding.getPrefix());
            sb.append("=");
            sb.append(binding.getURI());
            sb.append(" ");
        }
        sb.setLength(sb.length()-1);
        return sb.toString();
    }

    private static SequenceIterator mergeAdjacentTextNodes(SequenceIterator in) throws XPathException {
        List<Item> items = new ArrayList<>(20);
        boolean prevIsText = false;
        FastStringBuffer textBuffer = new FastStringBuffer(FastStringBuffer.C64);
        while (true) {
            Item next = in.next();
            if (next == null) {
                break;
            }
            if (next instanceof NodeInfo && ((NodeInfo) next).getNodeKind() == Type.TEXT) {
                textBuffer.cat(next.getStringValueCS());
                prevIsText = true;
            } else {
                if (prevIsText) {
                    Orphan textNode = new Orphan(null);
                    textNode.setNodeKind(Type.TEXT);
                    textNode.setStringValue(textBuffer.toString()); // must copy the buffer before reusing it
                    items.add(textNode);
                    textBuffer.setLength(0);
                }
                prevIsText = false;
                items.add(next);
            }
        }
        if (prevIsText) {
            Orphan textNode = new Orphan(null);
            textNode.setNodeKind(Type.TEXT);
            textNode.setStringValue(textBuffer.toString()); // must copy the buffer before reusing it
            items.add(textNode);
        }
        SequenceExtent extent = new SequenceExtent(items);
        return extent.iterate();
    }

    /**
     * Execute a dynamic call to the function
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     *         of the callee to ensure that the type of result conforms to the expected result type.
     * @throws XPathException (should not happen)
     */

    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        GenericAtomicComparer comparer = new GenericAtomicComparer(getStringCollator(), context);
        boolean b = deepEqual(arguments[0].iterate(), arguments[1].iterate(), comparer, context, 0);
        return BooleanValue.get(b);
    }

    @Override
    public String getStreamerName() {
        return "DeepEqual";
    }

}

