// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicMatcher;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.str.StringTool;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingIncident;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.tiny.WhitespaceTextImpl;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.ComplexType;
import net.sf.saxon.type.ComplexVariety;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.gnode.NamedXNodeType;
import net.sf.saxon.value.*;

import javax.xml.transform.OutputKeys;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

/**
 * Implements the saxon:deep-equal() function, a variant of fn:deep-equal that provides
 * additional control over how the comparison is performed.
 * <p>The specification is as follows:</p>
 * <p>arg1      The first sequence to be compared</p>
 * <p>arg2      The second sequence to be compared</p>
 * <p>collation The collation to be used (null if the default collation is to be used)</p>
 * <p>flags     A string whose characters select options that cause the comparison to vary from the
 * standard fn:deep-equals() function. The flags are:</p>
 * <ul>
 * <li>N - take namespace nodes into account</li>
 * <li>J - join adjacent text nodes (e.g, nodes either side of a comment)
 * <li>A - compare type annotations</li>
 * <li>C - take comments into account</li>
 * <li>F - take namespace prefixes into account</li>
 * <li>I - take the is-id() and is-idref() properties into account</li>
 * <li>P - take processing instructions into account</li>
 * <li>S - compare string values, not typed values</li>
 * <li>w - don't take whitespace-only text nodes into account</li>
 * </ul>
 * <p>returns true if the sequences are deep equal, otherwise false</p>
 */

public class SaxonDeepEqual extends SystemFunction {

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
     * Evaluate this function call at run-time
     *
     * @param context   The XPath dynamic evaluation context
     * @param arguments The values of the arguments to the function call. Each argument value (which is in general
     *                  a sequence) is supplied in the form of a sequence.
     * @return the results of the function.
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs during evaluation of the function. The Saxon run-time
     *                                           code will add information about the error location.
     */

    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        String flags = arguments[3].head().getStringValue();
        if (flags.indexOf('!') >= 0) {
            // undocumented diagnostic option
            Logger err = context.getConfiguration().getLogger();
            Properties indent = new Properties();
            indent.setProperty(OutputKeys.METHOD, "xml");
            indent.setProperty(OutputKeys.INDENT, "yes");
            err.info("DeepEqual: first argument:");
            QueryResult.serialize(QueryResult.wrap(arguments[0].iterate(), context.getConfiguration()),
                                  err.asStreamResult(), indent);
            err.info("DeepEqual: second argument:");
            QueryResult.serialize(QueryResult.wrap(arguments[1].iterate(), context.getConfiguration()),
                                  err.asStreamResult(), indent);
        }
        Item collationValue = arguments[2].head();
        Configuration config = context.getConfiguration();
        String collation = collationValue == null ? config.getDefaultCollationName() : collationValue.getStringValue();
        StringCollator collator = config.getCollation(collation);
        if (collator == null) {
            throw new XPathException("Unknown collation " + collation, "FOCH0002");
        }
        AtomicMatcher comparer = GenericAtomicComparer.getTransitiveAtomicMatcher(collator, context.getImplicitTimezone());
        int flag = 0;
        if (flags.contains("N")) {
            flag |= INCLUDE_NAMESPACES;
        }
        if (flags.contains("J")) {
            flag |= JOIN_ADJACENT_TEXT_NODES;
        }
        if (flags.contains("C")) {
            flag |= INCLUDE_COMMENTS;
        }
        if (flags.contains("P")) {
            flag |= INCLUDE_PROCESSING_INSTRUCTIONS;
        }
        if (flags.contains("F")) {
            flag |= INCLUDE_PREFIXES;
        }
        if (flags.contains("S")) {
            flag |= COMPARE_STRING_VALUES;
        }
        if (flags.contains("A")) {
            flag |= COMPARE_ANNOTATIONS;
        }
        if (flags.contains("I")) {
            flag |= COMPARE_ID_FLAGS;
        }
        if (flags.contains("v")) {
            flag |= EXCLUDE_VARIETY;
        }
        if (flags.contains("w")) {
            flag |= EXCLUDE_WHITESPACE_TEXT_NODES;
        }
        if (flags.contains("?")) {
            flag |= WARNING_IF_FALSE;
        }
        boolean result = deepEqual(
                arguments[0].iterate(), arguments[1].iterate(), comparer, context, flag);
        return BooleanValue.get(result);

    }

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
                                    AtomicMatcher comparer, XPathContext context, int flags)
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

                if (item1 instanceof FunctionItem || item2 instanceof FunctionItem) {
                    if (!(item1 instanceof FunctionItem && item2 instanceof FunctionItem)) {
                        reason = "if one item is a function then both must be functions (position " + pos1 + ")";
                        return false;
                    }
                    // two maps or arrays can be deep-equal
                    boolean fe = ((FunctionItem) item1).deepEquals((FunctionItem) item2, context, comparer, flags);
                    if (!fe) {
                        result = false;
                        reason = "functions at position " + pos1 + " differ";
                        break;
                    }
                    continue;
                }

                if (item1 instanceof ObjectValue<?> || item2 instanceof ObjectValue<?>) {
                    if (!item1.equals(item2)) {
                        return false;
                    }
                    continue;
                }

                if (item1 instanceof NodeInfo) {
                    if (item2 instanceof NodeInfo) {
                        String message = deepEqualNodes((NodeInfo) item1, (NodeInfo) item2, comparer, context, flags);
                        if (message != null) {
                            result = false;
                            reason = "nodes at position " + pos1 + " differ: " + message;
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
                        } else if (!comparer.equal(av1, av2)) {
                            result = false;
                            reason = "atomic values at position " + pos1 + " differ";
                            break;
                        }
                    }
                }
            } // end while

        } catch (UncheckedXPathException uxe) {
            throw uxe.getXPathException();
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

    /**
     * Determine whether two items are deep-equal, with default options
     *
     * @param item1    the first item
     * @param item2    the second item
     * @param comparer the comparer to be used for comparing atomic values
     * @param context  the XPathContext item
     * @return true if the items are deep-equal
     * @throws XPathException if either sequence contains a function item
     */

    public static boolean deepEqual(Item item1, Item item2,
                                    AtomicMatcher comparer, XPathContext context)
            throws XPathException {

        if (item1.getGenre() != item2.getGenre()) {
            return false;
        }
        if (item1 instanceof FunctionItem) {
            return ((FunctionItem) item1).deepEquals((FunctionItem) item2, context, comparer, 0);
        }

        if (item1 instanceof ObjectValue<?>) {
            return item1.equals(item2);
        }

        if (item1 instanceof NodeInfo) {
            String message = deepEqualNodes((NodeInfo) item1, (NodeInfo) item2, comparer, context, 0);
            return message == null;

        } else {
            AtomicValue av1 = (AtomicValue) item1;
            AtomicValue av2 = (AtomicValue) item2;
            return (av1.isNaN() && av2.isNaN()) || comparer.equal(av1, av2);

        }
    }


    /*
     * Determine whether two nodes are deep-equal
     * @return null if they are deep equal, or an explanation of the reason if not
     */

    public static String deepEqualNodes(NodeInfo n1, NodeInfo n2,
                                        AtomicMatcher comparer, XPathContext context, int flags)
            throws XPathException {
        // shortcut: a node is always deep-equal to itself
        if (n1.equals(n2)) {
            return null;
        }

        ErrorReporter reporter = context.getErrorReporter();

        if (n1.getNodeKind() != n2.getNodeKind()) {
            String reason = "node kinds differ: comparing " + showKind(n1) + " to " + showKind(n2);
            explain(reporter, reason, flags, n1, n2);
            return reason;
        }

        switch (n1.getNodeKind()) {
            case Type.ELEMENT:
                if (!Navigator.haveSameName(n1, n2)) {
                    final String reason = "element names differ: " + NameOfNode.makeName(n1).getStructuredQName().getEQName() +
                            " != " + NameOfNode.makeName(n2).getStructuredQName().getEQName();
                    explain(reporter, reason, flags, n1, n2);
                    return reason;
                }
                if (((flags & INCLUDE_PREFIXES) != 0) && !n1.getPrefix().equals(n2.getPrefix())) {
                    final String reason = "element prefixes differ: " + n1.getPrefix() +
                            " != " + n2.getPrefix();
                    explain(reporter, reason, flags, n1, n2);
                    return reason;
                }
                SequenceIterator a1 = n1.iterateAttributeAxis(AnyGNode.TEST);
                SequenceIterator a2 = n2.iterateAttributeAxis(AnyGNode.TEST);
                if (!SequenceTool.sameLength(a1, a2)) {
                    final String reason = "elements have different number of attributes";
                    explain(reporter, reason, flags, n1, n2);
                    return reason;
                }
                NodeInfo att1;
                a1 = n1.iterateAttributeAxis(AnyGNode.TEST);
                while ((att1 = (NodeInfo)a1.next()) != null) {
                    StructuredQName required = att1.getQName();
                    SequenceIterator a2iter = n2.iterateAttributeAxis(new NamedXNodeType(Type.ATTRIBUTE, required, context.getConfiguration()));
                    NodeInfo att2 = (NodeInfo)a2iter.next();

                    if (att2 == null) {
                        final String reason = "one element has an attribute " +
                                NameOfNode.makeName(att1).getStructuredQName().getEQName() +
                                ", the other does not";
                        explain(reporter, reason, flags, n1, n2);
                        return reason;
                    }
                    String attReason = deepEqualNodes(att1, att2, comparer, context, flags);
                    if (attReason != null) {
                        final String reason = "elements have different values for the attribute " +
                                NameOfNode.makeName(att1).getStructuredQName().getEQName() + " - " + attReason;
                        explain(reporter, reason, flags, n1, n2);
                        return reason;
                    }
                }
                if ((flags & INCLUDE_NAMESPACES) != 0) {
                    NamespaceMap nm1 = n1.getAllNamespaces();
                    NamespaceMap nm2 = n2.getAllNamespaces();
                    if (!nm1.equals(nm2)) {
                        final String reason = "elements have different in-scope namespaces: " +
                                nm1 + " versus " + nm2;
                        explain(reporter, reason, flags, n1, n2);
                        return reason;
                    }
                }

                if ((flags & COMPARE_ANNOTATIONS) != 0) {
                    if (!n1.getSchemaType().equals(n2.getSchemaType())) {
                        final String reason = "elements have different type annotation";
                        explain(reporter, reason, flags, n1, n2);
                        return reason;
                    }
                }

                if ((flags & EXCLUDE_VARIETY) == 0) {
                    if (n1.getSchemaType().isComplexType() != n2.getSchemaType().isComplexType()) {
                        final String reason = "one element has complex type, the other simple";
                        explain(reporter, reason, flags, n1, n2);
                        return reason;
                    }

                    if (n1.getSchemaType().isComplexType()) {
                        ComplexVariety variety1 = ((ComplexType) n1.getSchemaType()).getVariety();
                        ComplexVariety variety2 = ((ComplexType) n2.getSchemaType()).getVariety();
                        if (variety1 != variety2) {
                            final String reason = "both elements have complex type, but a different variety";
                            explain(reporter, reason, flags, n1, n2);
                            return reason;
                        }
                    }
                }

                if ((flags & COMPARE_STRING_VALUES) == 0) {
                    final SchemaType type1 = n1.getSchemaType();
                    final SchemaType type2 = n2.getSchemaType();
                    final boolean isSimple1 = type1.isSimpleType() || ((ComplexType) type1).isSimpleContent();
                    final boolean isSimple2 = type2.isSimpleType() || ((ComplexType) type2).isSimpleContent();
                    if (isSimple1 != isSimple2) {
                        final String reason = "one element has a simple type, the other does not";
                        explain(reporter, reason, flags, n1, n2);
                        return reason;
                    }
                    if (isSimple1) {
                        assert isSimple2;
                        final AtomicIterator v1 = n1.atomize().iterate();
                        final AtomicIterator v2 = n2.atomize().iterate();
                        boolean typedValueComparison = deepEqual(v1, v2, comparer, context, flags);
                        return typedValueComparison ? null : "typed values of elements differ";
                    }
                }

                if ((flags & COMPARE_ID_FLAGS) != 0) {
                    if (n1.isId() != n2.isId()) {
                        final String reason = "one element is an ID, the other is not";
                        explain(reporter, reason, flags, n1, n2);
                        return reason;
                    }
                    if (n1.isIdref() != n2.isIdref()) {
                        final String reason = "one element is an IDREF, the other is not";
                        explain(reporter, reason, flags, n1, n2);
                        return reason;
                    }
                }
                CSharp.emitCode("goto case Saxon.Hej.type.Type.DOCUMENT;");
                // fall through
            case Type.DOCUMENT:
                SequenceIterator c1 = n1.iterateChildAxis(AnyGNode.TEST);
                SequenceIterator c2 = n2.iterateChildAxis(AnyGNode.TEST);
                while (true) {
                    NodeInfo d1 = (NodeInfo)c1.next();
                    while (d1 != null && isIgnorable(d1, flags)) {
                        d1 = (NodeInfo)c1.next();
                    }
                    NodeInfo d2 = (NodeInfo)c2.next();
                    while (d2 != null && isIgnorable(d2, flags)) {
                        d2 = (NodeInfo)c2.next();
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
                            return message;
                        }
                        return null;
                    }
                    String recursiveResult = deepEqualNodes(d1, d2, comparer, context, flags);
                    if (recursiveResult != null) {
                        return recursiveResult;
                    }
                }

            case Type.ATTRIBUTE:
                if (!Navigator.haveSameName(n1, n2)) {
                    final String reason = "attribute names differ: " +
                            NameOfNode.makeName(n1).getStructuredQName().getEQName() +
                            " != " + NameOfNode.makeName(n1).getStructuredQName().getEQName();
                    explain(reporter, reason, flags, n1, n2);
                    return reason;
                }
                if (((flags & INCLUDE_PREFIXES) != 0) && !n1.getPrefix().equals(n2.getPrefix())) {
                    final String reason = "attribute prefixes differ: " + n1.getPrefix() +
                            " != " + n2.getPrefix();
                    explain(reporter, reason, flags, n1, n2);
                    return reason;
                }
                if ((flags & COMPARE_ANNOTATIONS) != 0) {
                    if (!n1.getSchemaType().equals(n2.getSchemaType())) {
                        final String reason = "attributes have different type annotations";
                        explain(reporter, reason, flags, n1, n2);
                        return reason;
                    }
                }
                boolean ar;
                if ((flags & COMPARE_STRING_VALUES) == 0) {
                    ar = deepEqual(n1.atomize().iterate(), n2.atomize().iterate(), comparer, context, 0);
                } else {
                    ar = comparer.equal(new StringValue(n1.getUnicodeStringValue()), new StringValue(n2.getUnicodeStringValue()));
                }
                if (!ar) {
                    final String reason = "attribute values differ";
                    explain(reporter, reason, flags, n1, n2);
                    return reason;
                }
                if ((flags & COMPARE_ID_FLAGS) != 0) {
                    if (n1.isId() != n2.isId()) {
                        final String reason = "one attribute is an ID, the other is not";
                        explain(reporter, reason, flags, n1, n2);
                        return reason;
                    }
                    if (n1.isIdref() != n2.isIdref()) {
                        final String reason = "one attribute is an IDREF, the other is not";
                        explain(reporter, reason, flags, n1, n2);
                        return reason;
                    }
                }
                return null;


            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                if (!n1.getLocalPart().equals(n2.getLocalPart())) {
                    final String reason = Type.displayTypeName(n1) + " names differ";
                    explain(reporter, reason, flags, n1, n2);
                    return reason;
                }
                CSharp.emitCode("goto case Saxon.Hej.type.Type.TEXT;");
                // drop through
            case Type.TEXT:
            case Type.COMMENT:
                boolean vr = comparer.equal((AtomicValue) n1.atomize(), (AtomicValue) n2.atomize());
                if (!vr) {
                    if ((flags & WARNING_IF_FALSE) != 0) {
                        String v1 = n1.getStringValue();
                        String v2 = n2.getStringValue();
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
                                        StringTool.diagnosticDisplay((v1.length() > v2.length() ? v1 : v2).substring(min)) + "\")";
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
                        return message;
                    } else {
                        return "atomized values differ";
                    }
                }
                return null;

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
                    Whitespace.isAllWhite(node.getUnicodeStringValue());
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
                Whitespace.isAllWhite(item.getUnicodeStringValue())) {
            return "whitespace text() node";
        } else {
            return Type.displayTypeName(item);
        }
    }

    private static String showNamespaces(HashSet<NamespaceBinding> bindings) {
        StringBuilder sb = new StringBuilder(256);
        for (NamespaceBinding binding : bindings) {
            sb.append(binding.getPrefix());
            sb.append("=");
            sb.append(binding.getNamespaceUri());
            sb.append(" ");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private static SequenceIterator mergeAdjacentTextNodes(SequenceIterator in) throws XPathException {
        List<Item> items = new ArrayList<>(20);
        boolean prevIsText = false;
        UnicodeBuilder textBuffer = new UnicodeBuilder();
        while (true) {
            Item next = in.next();
            if (next == null) {
                break;
            }
            if (next instanceof NodeInfo && ((NodeInfo) next).getNodeKind() == Type.TEXT) {
                textBuffer.accept(next.getUnicodeStringValue());
                prevIsText = true;
            } else {
                if (prevIsText) {
                    Orphan textNode = new Orphan(null);
                    textNode.setNodeKind(Type.TEXT);
                    textNode.setStringValue(textBuffer.toUnicodeString());
                    items.add(textNode);
                    textBuffer.clear();
                }
                prevIsText = false;
                items.add(next);
            }
        }
        if (prevIsText) {
            Orphan textNode = new Orphan(null);
            textNode.setNodeKind(Type.TEXT);
            textNode.setStringValue(textBuffer.toUnicodeString());
            items.add(textNode);
        }
        return new ListIterator.Of<>(items);
    }

    @Override
    public String getStreamerName() {
        return "SaxonDeepEqual";
    }


}

// Copyright (c) 2009-2026 Saxonica Limited

