////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.DefaultedArgumentExpression;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.ma.map.SingleEntryMap;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodePredicateLambda;
import net.sf.saxon.pattern.PortableNamedXNodeType;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.str.StringTool;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XmlProcessingIncident;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.tiny.WhitespaceTextImpl;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntSet;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * XSLT 2.0 deep-equal() function, where the collation is already known.
 * Supports deep comparison of two sequences (of nodes and/or atomic values)
 * optionally using a collation
 */

public class DeepEqual extends CollatingFunctionFixed {

    private final int version;

    public static Supplier<SystemFunction> makeDeepEqual(int version) {
        return () -> new DeepEqual(version);
    }

    public DeepEqual() {
        this(31);
    }

    private DeepEqual(int version) {
        this.version = version;
    }

    public static OptionsParameter OPTION_DETAILS;

    static {
        SequenceType itemsEqualFn = SequenceType.optional(
                new SpecificFunctionType(SequenceType.SINGLE_ITEM, SequenceType.SINGLE_ITEM,
                                         SequenceType.OPTIONAL_BOOLEAN));
        OptionsParameter o = new OptionsParameter(40);
        o.addAllowedOption("base-uri", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        o.addAllowedOption("collation", SequenceType.SINGLE_STRING);
        o.addAllowedOption("comments", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        o.addAllowedOption("debug", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        //o.addAllowedOption("false-on-error", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        o.addAllowedOption("id-property", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        o.addAllowedOption("idrefs-property", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        o.addAllowedOption("in-scope-namespaces", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        o.addAllowedOption("items-equal", itemsEqualFn, BooleanValue.FALSE);
        o.addAllowedOption("map-order", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        o.addAllowedOption("namespace-prefixes", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        o.addAllowedOption("nilled-property", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        //o.addAllowedOption("normalize-space", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        o.addAllowedOption("ordered", SequenceType.SINGLE_BOOLEAN, BooleanValue.TRUE);
        //o.addAllowedOption("preserve-space", SequenceType.SINGLE_BOOLEAN, BooleanValue.TRUE);
        o.addAllowedOption("processing-instructions", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        //o.addAllowedOption("text-boundaries", SequenceType.SINGLE_BOOLEAN, BooleanValue.TRUE);
        o.addAllowedOption("timezones", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        o.addAllowedOption("type-annotations", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        o.addAllowedOption("type-variety", SequenceType.SINGLE_BOOLEAN, BooleanValue.TRUE);
        o.addAllowedOption("typed-values", SequenceType.SINGLE_BOOLEAN, BooleanValue.TRUE);

        o.addAllowedOption("normalization-form", SequenceType.OPTIONAL_STRING, EmptySequence.INSTANCE);
        o.setAllowedValues("normalization-form", "FOJS0005", "NFC", "NFD", "NFKC", "NFKD");

        o.addAllowedOption("unordered-elements", BuiltInAtomicType.QNAME.zeroOrMore(), EmptySequence.INSTANCE);

        o.addAllowedOption("whitespace", SequenceType.OPTIONAL_STRING, new StringValue("preserve"));
        o.setAllowedValues("whitespace", "FOJS0005", "preserve", "strip", "normalize");

        OPTION_DETAILS = o;
    }

    @Override
    public Expression makeFunctionCall(Expression... arguments) {
        Expression[] newArgs = new Expression[3];
        newArgs[0] = arguments[0];
        newArgs[1] = arguments[1];

        if (arguments.length < 3 || arguments[2] instanceof DefaultedArgumentExpression) {
            newArgs[2] = new StringLiteral(getRetainedStaticContext().getDefaultCollationName());
        } else {
            newArgs[2] = arguments[2];
        }
        setArity(3);
        return super.makeFunctionCall(newArgs);
    }

    public static class DeepEqualOptions {
        public boolean baseUriSignificant = false;
        public boolean commentsSignificant = false;
        public boolean debug = false;
        public boolean idSignificant = false;
        public boolean idrefSignificant = false;
        public boolean inScopeNamespacesSignificant = false;
        public boolean mapOrderSignificant = false;
        public boolean namespacePrefixesSignificant = false;
        public String normalizationForm = null;
        public boolean nilledSignificant = false;
        public boolean normalizeSpace = false;
        public boolean stripSpace = false;
        public boolean processingInstructionsSignificant = false;
        public boolean textBoundariesSignificant = true;
        public boolean timezonesSignificant = false;
        public boolean typeAnnotationsSignificant = false;
        public boolean typeVarietySignificant = true;
        public boolean typedValuesSignificant = true;
        public Set<StructuredQName> unorderedElements = Collections.emptySet();
        public boolean orderedTopLevel = true;
        public String collationName;
        public StringCollator stringCollator;
        public BiFunction<AtomicValue, AtomicValue, Boolean> comparer;
        public FunctionItem itemsEqualFn;
        public int version;


        private static final String[] booleanOptions = new String[]{
                "base-uri", "comments", "debug", "id-property", "idrefs-property",
                "in-scope-namespaces", "map-order",
                "namespace-prefixes", "nilled-property", "normalize-space",
                "ordered", "processing-instructions", "text-boundaries", "timezones",
                "type-annotations", "type-variety", "typed-values"};


        public DeepEqualOptions(MapItem map, XPathContext context, int version) throws XPathException {
            Map<String, GroundedValue> values = OPTION_DETAILS.processSuppliedOptions(map, context, 40);
            for (String option : DeepEqualOptions.booleanOptions) {
                setBooleanOption(values, option);
            }
            this.version = version;
            GroundedValue normForm = map.get(new StringValue("normalization-form"));
            if (normForm != null) {
                normalizationForm = normForm.getStringValue();
            }
            GroundedValue whitespace = map.get(new StringValue("whitespace"));
            if (whitespace != null) {
                stripSpace = whitespace.getStringValue().equals("strip");
                normalizeSpace = whitespace.getStringValue().equals("normalize");
            }
            GroundedValue listedElements = map.get(new StringValue("unordered-elements"));
            if (listedElements != null) {
                unorderedElements = new HashSet<>();
                for (Item item : listedElements.asIterable()) {
                    if (item instanceof QNameValue) {
                        unorderedElements.add(((QNameValue)item).getStructuredQName());
                    }
                }
            }
            GroundedValue collationItem = values.get("collation");
            if (collationItem != null) {
                this.collationName = collationItem.getStringValue();
            } else {
                this.collationName = CodepointCollator.getInstance().getCollationURI();
            }
            stringCollator = context.getConfiguration().getCollation(collationName);
            if (stringCollator == null) {
                throw new XPathException("Unknown collation " + collationName, "FOCH0002");
            }
            if (version < 40) {
                AtomicComparer ac = GenericAtomicComparer.makeAtomicComparer(
                        BuiltInAtomicType.ANY_ATOMIC, BuiltInAtomicType.ANY_ATOMIC,
                        stringCollator, version, context);
                comparer = (a, b)  -> ac.comparesEqual(a, b);
            } else {
                comparer = (a, b) -> {
                    int implicitTimezone = context.getImplicitTimezone();
                    int implicitTimezone1 = context.getImplicitTimezone();
                    return a.getXPathMatchKey(stringCollator, implicitTimezone1, 40)
                            .equals(b.getXPathMatchKey(stringCollator, implicitTimezone, 40));
                };
            }
            if (normalizeSpace || normalizationForm != null) {
                final BiFunction<AtomicValue, AtomicValue, Boolean> baseComparer = comparer;
                comparer = (v0, v1) -> {
                    if (v0 instanceof StringValue && v1 instanceof StringValue) {
                        UnicodeString u0 = v0.getUnicodeStringValue();
                        UnicodeString u1 = v1.getUnicodeStringValue();
                        if (normalizeSpace) {
                            u0 = Whitespace.collapseWhitespace(u0);
                            u1 = Whitespace.collapseWhitespace(u1);
                        }
                        if (normalizationForm != null) {
                            try {
                                u0 = StringView.of(NormalizeUnicode.normalize(u0.toString(), normalizationForm));
                            } catch (XPathException e) {
                                throw new IllegalArgumentException(e);
                            }
                            try {
                                u1 = StringView.of(NormalizeUnicode.normalize(u1.toString(), normalizationForm));
                            } catch (XPathException e) {
                                throw new IllegalArgumentException();
                            }
                        }
                        return stringCollator.comparesEqual(u0, u1);
                    } else {
                        return baseComparer.apply(v0, v1);
                    }
                };
            }
            GroundedValue itemsEqual = map.get(new StringValue("items-equal"));
            if (itemsEqual != null) {
                itemsEqualFn = (FunctionItem)itemsEqual.head();
            }
            // In 3.1, comments and processing instructions are ignored, but boundaries between
            // text nodes are not. So <a>x<?pi?>y</a> is not deep-equal to <a>xy</a>, not because
            // of the presence of the processing instruction, but because the number of text nodes
            // is different. This crazy rule disappears in 4.0.
            textBoundariesSignificant = version < 40;
        }

        private void setBooleanOption(Map<String, GroundedValue> map, String optionName) throws XPathException {
            Sequence value = map.get(optionName);
            if (value != null) {
                boolean booleanValue = ExpressionTool.effectiveBooleanValue(value.iterate());
                switch (optionName) {
                    case "base-uri":
                        baseUriSignificant = booleanValue;
                        return;
                    case "comments":
                        commentsSignificant = booleanValue;
                        return;
                    case "debug":
                        debug = booleanValue;
                        return;
                    case "id-property":
                        idSignificant = booleanValue;
                        return;
                    case "idrefs-property":
                        idrefSignificant = booleanValue;
                        return;
                    case "in-scope-namespaces":
                        inScopeNamespacesSignificant = booleanValue;
                        return;
                    case "map-order":
                        mapOrderSignificant = booleanValue;
                        return;
                    case "namespace-prefixes":
                        namespacePrefixesSignificant = booleanValue;
                        return;
                    case "nilled-property":
                        nilledSignificant = booleanValue;
                        return;
                    case "normalize-space":
                        normalizeSpace = booleanValue;
                        return;
                    case "ordered":
                        orderedTopLevel = booleanValue;
                        return;
                    case "processing-instructions":
                        processingInstructionsSignificant = booleanValue;
                        return;
//                    case "text-boundaries":
//                        textBoundariesSignificant = booleanValue;
//                        return;
                    case "timezones":
                        timezonesSignificant = booleanValue;
                        return;
                    case "type-annotations":
                        typeAnnotationsSignificant = booleanValue;
                        return;
                    case "type-variety":
                        typeVarietySignificant = booleanValue;
                        return;
                    case "typed-values":
                        typedValuesSignificant = booleanValue;
                        return;
                    default:
                        throw new IllegalArgumentException();
                }
            }
        }
    }

    /**
     * Compute a hash code for a given item (used when doing unordered comparisons)
     * @param item the given item
     * @param options the comparison options
     * @return the hash code
     */

    private static long hash(Item item, DeepEqualOptions options) {
        if (options.itemsEqualFn != null) {
            // We can't do any useful hashing for this case, so all items get the same hash code
            return 1L;
        }
        switch (item.getGenre()) {
            case ATOMIC:
                return 0x01L << 56 | (long)((AtomicValue)item).asMapKey(options.version).hashCode();
            case XNODE:
                return 0x02L << 56 | (long)nodeHashCode(((NodeInfo)item), options);
            case MAP:
                return 0x03L << 56 | (long)((MapItem)item).size();   // TODO: improve this
            case ARRAY:
                return 0x04L << 56 | (long)((ArrayItem) item).arrayLength();   // TODO: improve this
            case FUNCTION:
                return 0x05L << 56 | (long)System.identityHashCode(item);
            case EXTERNAL:
            default:
                return 0x06L << 56 | (long)((ObjectValue<?>)item).getObject().hashCode();
        }
    }

    private static Map<Long, List<Item>> makeHashMap(SequenceIterator iter, DeepEqualOptions options) {
        Map<Long, List<Item>> result = new HashMap<>();
        Item item;
        //int count = 0;
        while ((item = iter.next()) != null) {
            //count++;
            long itemHash = hash(item, options);
            List<Item> bucket = result.computeIfAbsent(itemHash, k -> new ArrayList<Item>());
            bucket.add(item);
        }
        //System.err.println("*** Hash: " + count + " items in " + result.size() + " buckets");
        return result;
    }

    private static boolean cartesianCompare(List<Item> list0, List<Item> list1, XPathContext context, DeepEqualOptions options)
    throws XPathException {
        if (list0.size() != list1.size()) {
            return false;
        }
        IntHashSet matched = new IntHashSet();
        for (Item i0 : list0) {
            int matchPos = -1;
            for (int j=0; j<list1.size(); j++) {
                if (!matched.contains(j)) {
                    SequenceIterator iter0 = SingletonIterator.makeIterator(i0);
                    SequenceIterator iter1 = SingletonIterator.makeIterator(list1.get(j));
                    if (deepEqual(iter0, iter1, context, options)) {
                        matchPos = j;
                        matched.add(j);
                        break;
                    }
                }
            }
            if (matchPos == -1) {
                return false;
            }
        }
        return true;
    }


    /**
     * Determine when two sequences are deep-equal
     *
     * @param op1     the first sequence
     * @param op2     the second sequence
     * @param context the XPathContext item
     * @param options comparison options.
     * @return true if the sequences are deep-equal
     * @throws XPathException if either sequence contains a function item
     */

    public static boolean deepEqual(SequenceIterator op1, SequenceIterator op2,
                                    XPathContext context, DeepEqualOptions options)
            throws XPathException {

        if (!options.orderedTopLevel) {
            // Group the items into hash buckets, then do N*N comparison within each hash bucket.
            // The hashing is chosen (a) to be reasonably fast, and (b) to ignore anything that varies
            // depending on the comparison options
            Map<Long, List<Item>> hashMap1 = makeHashMap(op1, options);
            Map<Long, List<Item>> hashMap2 = makeHashMap(op2, options);
            if (hashMap1.size() != hashMap2.size()) {
                return false;
            }
            options.orderedTopLevel = true;
            for (Map.Entry<Long, List<Item>> bucket1 : hashMap1.entrySet()) {
                List<Item> list2 = hashMap2.get(bucket1.getKey());
                if (list2 == null) {
                    return false;
                }
                if (!cartesianCompare(bucket1.getValue(), list2, context, options)) {
                    return false;
                }
            }
            return true;
        }

        boolean result = true;
        String reason = null;
        ErrorReporter reporter = context.getErrorReporter();

        try {
            op1 = mergeAdjacentTextNodes(op1);
            op2 = mergeAdjacentTextNodes(op2);
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

                if (options.itemsEqualFn != null && !(item1 instanceof NodeInfo && item2 instanceof NodeInfo)) {
                    // Skip the callback if both items are nodes, as it will be done later
                    Sequence comparison = options.itemsEqualFn.call(context, new Sequence[]{item1, item2});
                    BooleanValue bv = (BooleanValue)comparison.head();
                    if (bv != null) {
                        if (!bv.getBooleanValue()) {
                            return false;
                        } else {
                            continue;
                        }
                    }
                }

                if (item1 instanceof FunctionItem || item2 instanceof FunctionItem) {
                    if (!(item1 instanceof FunctionItem && item2 instanceof FunctionItem)) {
                        reason = "if one item is a function then both must be functions (position " + pos1 + ")";
                        result = false;
                        break;
                    }
                    // two maps or arrays can be deep-equal
                    boolean fe = ((FunctionItem) item1).deepEqual40((FunctionItem) item2, context, options);
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

                if (item1 instanceof NodeInfo && item2 instanceof NodeInfo) {
                    String message = deepEqual((NodeInfo) item1, (NodeInfo) item2, context, options);
                    if (message != null) {
                        result = false;
                        reason = "nodes at position " + pos1 + " differ: " + message;
                        break;
                    }
                    continue;
                }

                if (item1 instanceof JNode && item2 instanceof JNode) {
                    boolean OK = deepEqual(((JNode) item1).getContent().iterate(),
                                               ((JNode) item2).getContent().iterate(),
                                               context, options);
                    if (!OK) {
                        result = false;
                        reason = "JNodes at position " + pos1 + " have different content";
                        break;
                    }
                    continue;

                } else {
                    if (item2 instanceof NodeInfo) {
                        result = false;
                        reason = "comparing an atomic value to a node at position " + pos1;
                        break;
                    } else {
                        // Compare two atomic values
                        AtomicValue av1 = (AtomicValue) item1;
                        AtomicValue av2 = (AtomicValue) item2;
                        if (av1.isNaN() && av2.isNaN()) {
                            // treat as equal, no action
                        } else if (!options.comparer.apply(av1, av2)) {
                            result = false;
                            reason = "atomic values at position " + pos1 + " differ";
                            break;
                        }
                        if (options.typeAnnotationsSignificant && !av1.getItemType().equals(av2.getItemType())) {
                            result = false;
                            reason = "atomic values at position " + pos1 + " have different type annotations";
                            break;
                        }
                        if (options.namespacePrefixesSignificant
                                && av1 instanceof QualifiedNameValue
                                && av2 instanceof QualifiedNameValue
                                && !((QualifiedNameValue)av1).getPrefix().equals(((QualifiedNameValue) av2).getPrefix())) {
                            result = false;
                            reason = "QName values at position " + pos1 + " have different namespace prefixes";
                            break;
                        }
                        if (options.timezonesSignificant
                                && av1 instanceof CalendarValue
                                && av2 instanceof CalendarValue
                                && ((CalendarValue) av1).getTimezoneInMinutes() != ((CalendarValue) av2).getTimezoneInMinutes()) {
                            result = false;
                            reason = "Values at position " + pos1 + " have different timezone";
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
            explain(reporter, reason, options, null, null);
        }

        return result;
    }

    /*
     * Determine whether two nodes are deep-equal.
     */

    public static String deepEqual(NodeInfo n1, NodeInfo n2, XPathContext context, DeepEqualOptions options)
            throws XPathException {

        ErrorReporter reporter = context.getErrorReporter();

        if (options.itemsEqualFn != null) {
            Sequence comparison = options.itemsEqualFn.call(context, new Sequence[]{n1, n2});
            BooleanValue bv = (BooleanValue) comparison.head();
            if (bv != null) {
                final String reason = "Two nodes are deemed not equal by the items-equal callback function";
                if (!bv.getBooleanValue()) {
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                } else {
                    return null;
                }
            }
        }

        // shortcut: a node is always deep-equal to itself
        if (n1.equals(n2)) {
            return null;
        }


        if (n1.getNodeKind() != n2.getNodeKind()) {
            String reason = "node kinds differ: comparing " + showKind(n1) + " to " + showKind(n2);
            explain(reporter, reason, options, n1, n2);
            return reason;
        }

        if (options.baseUriSignificant && !Objects.equals(n1.getBaseURI(), n2.getBaseURI())) {
            String reason = "base URIs differ: comparing " + n1.getBaseURI() + " to " + n2.getBaseURI();
            explain(reporter, reason, options, n1, n2);
            return reason;
        }

        if (options.typeAnnotationsSignificant && !n1.getSchemaType().equals(n2.getSchemaType())) {
            String reason = "nodes have different type annotations";
            explain(reporter, reason, options, n1, n2);
            return reason;
        }

        switch (n1.getNodeKind()) {
            case Type.ELEMENT:
                if (!Navigator.haveSameName(n1, n2)) {
                    final String reason = "element names differ: " + NameOfNode.makeName(n1).getStructuredQName().getEQName() +
                            " != " + NameOfNode.makeName(n2).getStructuredQName().getEQName();
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                if (options.namespacePrefixesSignificant && !n1.getPrefix().equals(n2.getPrefix())) {
                    final String reason = "element prefixes differ: " + n1.getPrefix() +
                            " != " + n2.getPrefix();
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                if (options.inScopeNamespacesSignificant && !n1.getAllNamespaces().equals(n2.getAllNamespaces())) {
                    final String reason = "in-scope namespaces differ: " + n1.getAllNamespaces() +
                            " versus " + n2.getAllNamespaces();
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                SequenceIterator a1 = n1.iterateAttributeAxis(AnyGNode.TEST);
                SequenceIterator a2 = n2.iterateAttributeAxis(AnyGNode.TEST);
                if (!SequenceTool.sameLength(a1, a2)) {
                    final String reason = "elements have different number of attributes";
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                NodeInfo att1;
                a1 = n1.iterateAttributeAxis(AnyGNode.TEST);
                while ((att1 = ((NodeInfo)a1.next())) != null) {
                    SequenceIterator a2iter = n2.iterateAttributeAxis(
                            new PortableNamedXNodeType(Type.ATTRIBUTE, att1.getQName()));
                    NodeInfo att2 = (NodeInfo)a2iter.next();

                    if (att2 == null) {
                        final String reason = "one element has an attribute " +
                                NameOfNode.makeName(att1).getStructuredQName().getEQName() +
                                ", the other does not";
                        explain(reporter, reason, options, n1, n2);
                        return reason;
                    }

                    String attReason = deepEqual(att1, att2, context, options);
                    if (attReason != null) {
                        final String reason = "elements have different values for the attribute " +
                                NameOfNode.makeName(att1).getStructuredQName().getEQName() + " - " + attReason;
                        explain(reporter, reason, options, n1, n2);
                        return reason;
                    }
                }
                if (options.inScopeNamespacesSignificant) {
                    NamespaceMap nm1 = n1.getAllNamespaces();
                    NamespaceMap nm2 = n2.getAllNamespaces();
                    if (!nm1.equals(nm2)) {
                        final String reason = "elements have different in-scope namespaces: " +
                                nm1 + " versus " + nm2;
                        explain(reporter, reason, options, n1, n2);
                        return reason;
                    }
                }

                if (options.typeAnnotationsSignificant) {
                    if (!n1.getSchemaType().equals(n2.getSchemaType())) {
                        final String reason = "elements have different type annotation";
                        explain(reporter, reason, options, n1, n2);
                        return reason;
                    }
                }

                if (options.typeVarietySignificant) {
                    if (n1.getSchemaType().isComplexType() != n2.getSchemaType().isComplexType()) {
                        final String reason = "one element has complex type, the other simple";
                        explain(reporter, reason, options, n1, n2);
                        return reason;
                    }

                    if (n1.getSchemaType().isComplexType()) {
                        ComplexVariety variety1 = ((ComplexType) n1.getSchemaType()).getVariety();
                        ComplexVariety variety2 = ((ComplexType) n2.getSchemaType()).getVariety();
                        if (variety1 != variety2) {
                            final String reason = "both elements have complex type, but a different variety";
                            explain(reporter, reason, options, n1, n2);
                            return reason;
                        }
                    }
                }

                if (options.typedValuesSignificant) {
                    final SchemaType type1 = n1.getSchemaType();
                    final SchemaType type2 = n2.getSchemaType();
                    final boolean isSimple1 = type1.isSimpleType() || ((ComplexType) type1).isSimpleContent();
                    final boolean isSimple2 = type2.isSimpleType() || ((ComplexType) type2).isSimpleContent();
                    if (options.typeVarietySignificant && isSimple1 != isSimple2) {
                        final String reason = "one element has a simple type, the other does not";
                        explain(reporter, reason, options, n1, n2);
                        return reason;
                    }
                    if (isSimple1 && isSimple2) {
                        final AtomicIterator v1 = n1.atomize().iterate();
                        final AtomicIterator v2 = n2.atomize().iterate();
                        boolean typedValueComparison = deepEqual(v1, v2, context, options);
                        return typedValueComparison ? null : "typed values of elements differ";
                    }
                }

                if (options.idSignificant && n1.isId() != n2.isId()) {
                    final String reason = "one element is an ID, the other is not";
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                if (options.idrefSignificant && n1.isIdref() != n2.isIdref()) {
                    final String reason = "one element is an IDREF, the other is not";
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                if (options.nilledSignificant && n1.isNilled() != n2.isNilled()) {
                    final String reason = "one element is nilled, the other is not";
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }

                if (options.unorderedElements.contains(NameOfNode.makeName(n1).getStructuredQName())) {
                    return hasSameChildrenUnordered(n1, n2, options, context);
                }

                CSharp.emitCode("goto case Saxon.Hej.type.Type.DOCUMENT;");
                // fall through
            case Type.DOCUMENT:
                SequenceIterator c1 = n1.iterateChildAxis(NodePredicateLambda.of(node -> !isIgnorable((NodeInfo)node, options)));
                SequenceIterator c2 = n2.iterateChildAxis(NodePredicateLambda.of(node -> !isIgnorable((NodeInfo)node, options)));

                if (!options.textBoundariesSignificant) {
                    c1 = mergeAdjacentTextNodes(c1);
                    c2 = mergeAdjacentTextNodes(c2);
                }

                while (true) {
                    NodeInfo d1 = (NodeInfo)c1.next();
                    NodeInfo d2 = (NodeInfo)c2.next();
                    if (d1 == null || d2 == null) {
                        boolean r = d1 == d2;
                        if (!r) {
                            String message = "the first operand contains a node with " +
                                    (d1 == null ? "fewer" : "more") +
                                    " children than the second";
                            if (d1 instanceof WhitespaceTextImpl || d2 instanceof WhitespaceTextImpl) {
                                message += " (the first extra child is whitespace text)";
                            }
                            explain(reporter, message, options, n1, n2);
                            return message;
                        }
                        return null;
                    }
                    String recursiveResult = deepEqual(d1, d2, context, options);
                    if (recursiveResult != null) {
                        return recursiveResult;
                    }
                }

            case Type.ATTRIBUTE:
                if (!Navigator.haveSameName(n1, n2)) {
                    final String reason = "attribute names differ: " +
                            NameOfNode.makeName(n1).getStructuredQName().getEQName() +
                            " != " + NameOfNode.makeName(n1).getStructuredQName().getEQName();
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                if (options.namespacePrefixesSignificant && !n1.getPrefix().equals(n2.getPrefix())) {
                    final String reason = "attribute prefixes differ: " + n1.getPrefix() +
                            " != " + n2.getPrefix();
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                if (options.typeAnnotationsSignificant) {
                    if (!n1.getSchemaType().equals(n2.getSchemaType())) {
                        final String reason = "attributes have different type annotations";
                        explain(reporter, reason, options, n1, n2);
                        return reason;
                    }
                }
                boolean ar;
                if (options.typedValuesSignificant) {
                    ar = deepEqual(n1.atomize().iterate(), n2.atomize().iterate(), context, options);
                } else {
                    ar = options.comparer.apply(new StringValue(n1.getUnicodeStringValue()), new StringValue(n2.getUnicodeStringValue()));
                }
                if (!ar) {
                    final String reason = "attribute values differ";
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                if (options.idSignificant && n1.isId() != n2.isId()) {
                    final String reason = "one attribute is an ID, the other is not";
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                if (options.idrefSignificant && n1.isIdref() != n2.isIdref()) {
                    final String reason = "one attribute is an IDREF, the other is not";
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                return null;


            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                if (!n1.getLocalPart().equals(n2.getLocalPart())) {
                    final String reason = Type.displayTypeName(n1) + " names differ";
                    explain(reporter, reason, options, n1, n2);
                    return reason;
                }
                CSharp.emitCode("goto case Saxon.Hej.type.Type.TEXT;");
                // drop through
            case Type.TEXT:
            case Type.COMMENT:
                boolean vr = compareStrings(n1.getStringValue(), n2.getStringValue(), options, context);
                        //options.comparer.comparesEqual((AtomicValue) n1.atomize(), (AtomicValue) n2.atomize());
                if (!vr) {
                    if (options.debug) {
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
                                message, options, n1, n2);
                        return message;
                    } else {
                        return "atomized values differ";
                    }
                }
                return null;

            default:
                throw new IllegalArgumentException("Unknown node kind");
        }
    }

    /**
     * Determine whether the children of a supplied node are deep-equal to some permutation of
     * the children of another.
     * @param e0 the first node
     * @param e1 the second node
     * @param options the comparison options
     * @param context the dynamic context
     * @return null if one sequence is deep-equal to some permutation of the other; otherwise, an explanation
     * of the difference
     */
    private static String hasSameChildrenUnordered(NodeInfo e0, NodeInfo e1,
                                         DeepEqualOptions options, XPathContext context) throws XPathException {
        List<NodeInfo> children0 = new ArrayList<>();
        List<NodeInfo> children1 = new ArrayList<>();

        SequenceIterator children0iter = e0.iterateChildAxis(null);
        for (NodeInfo c0; (c0 = (NodeInfo) children0iter.next()) != null; ) {
            if (!isIgnorable(c0, options)) {
                children0.add(c0);
            }
        }

        SequenceIterator children1iter = e1.iterateChildAxis(null);
        for (NodeInfo c1; (c1 = (NodeInfo) children1iter.next()) != null; ) {
            if (!isIgnorable(c1, options)) {
                children1.add(c1);
            }
        }

        if (children0.size() != children1.size()) {
            return "Number of children differs: " + children0.size() + " vs. " + children1.size();
        }

        List<Integer> hashcodes1 = new ArrayList<>(children1.size());
        IntSet hashSet = new IntHashSet();
        for (NodeInfo nodeInfo : children1) {
            final int hash = nodeHashCode(nodeInfo, options);
            hashSet.add(hash);
            hashcodes1.add(hash);
        }

        for (NodeInfo c0 : children0) {
            final int hash = nodeHashCode(c0, options);
            if (!hashSet.contains(hash)) {
                return "Node found among first node's children with no counterpart among the second node's children";
            }
            int found = -1;
            for (int j = 0; j < hashcodes1.size(); j++) {
                if (hash == hashcodes1.get(j) && deepEqual(c0, children1.get(j), context, options) == null) {
                    found = j;
                    break;
                }
            }
            if (found >= 0) {
                children1.remove(found);
                hashcodes1.remove(found);
            } else {
                return "Node found among first node's children with no counterpart among the second node's children";
            }
        }
        return null;
    }

    private static int nodeHashCode(NodeInfo node, DeepEqualOptions options) {
        // If there's a user-defined items-equal callback, hashing isn't possible
        if (options.itemsEqualFn != null) {
            return 1;
        }
        // Keep it simple for now - independent of the options
        return node.getNodeKind() << 24
                ^ (node.hasFingerprint() ? node.getFingerprint() : node.getConfiguration().getNamePool().allocateFingerprint(node.getNamespaceUri(), node.getLocalPart()))
                ^ (node.attributes().size() << 10)
                ^ Whitespace.normalize(node.getUnicodeStringValue()).hashCode();

    }

    private static long hashCodeOfSequence(GroundedValue value, Function<Item, Long> hash) {
        long h = 0;
        for (Item it : value.asIterable()) {
            h ^= hash.apply(it);
        }
        return h;
    }

    private static long hashCodeOfNode(NodeInfo node, DeepEqualOptions options, XPathContext context) {
        int kind = node.getNodeKind();
        long h = 0x7876ABCD2345DCBAL;
        h ^= ((long) node.getFingerprint() << 25);
        if (options.namespacePrefixesSignificant) {
            h ^= ((long) node.getPrefix().hashCode() << 13);
        }
        if (kind == Type.TEXT && !Whitespace.isAllWhite(node.getUnicodeStringValue())) {
            String s = node.getStringValue();
            if (options.normalizeSpace) {
                s = Whitespace.collapseWhitespace(s);
            }
            if (options.normalizationForm != null) {
                try {
                    s = NormalizeUnicode.normalize(s, options.normalizationForm);
                } catch (XPathException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            h ^= (long)s.hashCode() << 5;
        }
        return h;
    }

    private static boolean compareStrings(String s1, String s2, DeepEqualOptions options, XPathContext context) {
        if (options.normalizeSpace) {
            s1 = Whitespace.collapseWhitespace(s1);
            s2 = Whitespace.collapseWhitespace(s2);
        }
        if (options.normalizationForm != null) {
            try {
                s1 = NormalizeUnicode.normalize(s1, options.normalizationForm);
                s2 = NormalizeUnicode.normalize(s2, options.normalizationForm);
            } catch (XPathException e) {
                return false;
            }
        }
        return options.stringCollator.comparesEqual(StringView.of(s1), StringView.of(s2));
    }

    private static boolean isIgnorable(NodeInfo node, DeepEqualOptions options) {
        final int kind = node.getNodeKind();
        if (kind == Type.COMMENT) {
            return !options.commentsSignificant;
        } else if (kind == Type.PROCESSING_INSTRUCTION) {
            return !options.processingInstructionsSignificant;
        } else if (kind == Type.TEXT) {
            return (options.stripSpace || options.normalizeSpace) &&
                    Whitespace.isAllWhite(node.getUnicodeStringValue()) &&
                    !isWithinXmlSpacePreserve(node);
        }
        return false;
    }

    private static boolean isWithinXmlSpacePreserve(NodeInfo node) {
        while (node != null) {
            String att = node.getAttributeValue(NamespaceUri.XML, "space");
            if (att != null) {
                return Whitespace.normalize(att).equals("preserve");
            }
            node = (NodeInfo)node.getParent();
        }
        return false;
    }

    private static void explain(ErrorReporter reporter, String message, DeepEqualOptions options, NodeInfo n1, NodeInfo n2) {
        if (options.debug) {
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

//    private static String showNamespaces(HashSet<NamespaceBinding> bindings) {
//        StringBuilder sb = new StringBuilder(256);
//        for (NamespaceBinding binding : bindings) {
//            sb.append(binding.getPrefix());
//            sb.append("=");
//            sb.append(binding.getNamespaceUri());
//            sb.append(" ");
//        }
//        sb.setLength(sb.length() - 1);
//        return sb.toString();
//    }

    private static SequenceIterator mergeAdjacentTextNodes(SequenceIterator in) {
        List<Item> items = new ArrayList<>(20);
        boolean prevIsText = false;
        UnicodeBuilder textBuffer = new UnicodeBuilder();
        Configuration config = null;
        while (true) {
            Item next = in.next();
            if (next == null) {
                break;
            }
            if (next instanceof NodeInfo && ((NodeInfo) next).getNodeKind() == Type.TEXT) {
                if (config == null) {
                    config = ((NodeInfo) next).getConfiguration();
                }
                textBuffer.accept(next.getUnicodeStringValue());
                prevIsText = true;
            } else {
                if (prevIsText) {
                    Orphan textNode = new Orphan(config);
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
            Orphan textNode = new Orphan(config);
            textNode.setNodeKind(Type.TEXT);
            textNode.setStringValue(textBuffer.toUnicodeString());
            items.add(textNode);
        }
        return new ListIterator.Of<>(items);
    }

    /**
     * Execute a call to the function
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     * of the callee to ensure that the type of result conforms to the expected result type.
     * @throws XPathException (should not happen)
     */

    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        Item optionsArg = arguments.length >= 3 ? arguments[2].head() : null;
        MapItem options;
        if (optionsArg == null) {
            options = new SingleEntryMap(new StringValue("collation"),
                                         new StringValue(getRetainedStaticContext().getDefaultCollationName()),
                                         40);
        } else if (optionsArg instanceof StringValue) {
            options = new SingleEntryMap(new StringValue("collation"),
                                         optionsArg,
                                         40);
        } else {
            options = (MapItem) optionsArg;
        }
        //GenericAtomicComparer comparer = new GenericAtomicComparer(getStringCollator(), context);
        DeepEqualOptions eqOptions = new DeepEqualOptions(options, context, version);
        boolean b = deepEqual(arguments[0].iterate(), arguments[1].iterate(), context, eqOptions);
        return BooleanValue.get(b);
    }

    @Override
    public String getStreamerName() {
        return "DeepEqual";
    }

//    private static class NormalizingComparer implements AtomicComparer {
//
//        private AtomicComparer baseComparer;
//        private DeepEqualOptions options;
//
//        public NormalizingComparer(AtomicComparer baseComparer, DeepEqualOptions options) {
//            this.baseComparer = baseComparer;
//            this.options = options;
//        }
//
//        @Override
//        public StringCollator getCollator() {
//            return baseComparer.getCollator();
//        }
//
//        @Override
//        public AtomicComparer provideContext(XPathContext context) {
//            baseComparer = baseComparer.provideContext(context);
//            return this; // TODO: thread safety?
//        }
//
//        @Override
//        public int compareAtomicValues(AtomicValue v0, AtomicValue v1) throws NoDynamicContextException {
//            return baseComparer.compareAtomicValues(v0, v1);
//        }
//
//        @Override
//        public boolean comparesEqual(AtomicValue v0, AtomicValue v1) throws NoDynamicContextException {
//            if (v0 instanceof StringValue && v1 instanceof StringValue) {
//                UnicodeString u0 = v0.getUnicodeStringValue();
//                UnicodeString u1 = v1.getUnicodeStringValue();
//                if (options.normalizeSpace) {
//                    u0 = Whitespace.collapseWhitespace(u0);
//                    u1 = Whitespace.collapseWhitespace(u1);
//                }
//                if (options.normalizationForm != null) {
//                    try {
//                        u0 = StringView.of(NormalizeUnicode.normalize(u0.toString(), options.normalizationForm));
//                    } catch (XPathException e) {
//                        throw new IllegalArgumentException(e);
//                    }
//                    try {
//                        u1 = StringView.of(NormalizeUnicode.normalize(u1.toString(), options.normalizationForm));
//                    } catch (XPathException e) {
//                        throw new IllegalArgumentException();
//                    }
//                }
//                return getCollator().comparesEqual(u0, u1);
//            } else {
//                return baseComparer.comparesEqual(v0, v1);
//            }
//        }
//
//        @Override
//        public String save() {
//            return null;
//        }
//    }

}

