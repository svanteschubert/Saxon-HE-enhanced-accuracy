////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.expr.sort.SimpleTypeComparison;
import net.sf.saxon.functions.hof.FunctionLiteral;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.jnode.JNode;
import net.sf.saxon.ma.jnode.RootJNode;
import net.sf.saxon.ma.map.KeyValuePair;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.ItemTypePattern;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.EmptyUnicodeString;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.Properties;


/**
 * A Literal is an expression whose value is constant: it is a class that implements the {@link Expression}
 * interface as a wrapper around a {@link GroundedValue}. This may derive from an actual literal in an XPath expression
 * or query, or it may be the result of evaluating a constant subexpression such as true() or xs:date('2007-01-16')
 */

public class Literal extends Expression {

    private final GroundedValue value;

    /**
     * Create a literal as a wrapper around a Value
     *
     * @param value the value of this literal
     */

    protected Literal(GroundedValue value) {
        this.value = value.reduce();
    }

    /**
     * Get the value represented by this Literal
     *
     * @return the constant value
     */

    public GroundedValue getGroundedValue() {
        return value;
    }

    /**
     * TypeCheck an expression
     *
     * @return for a Value, this always returns the value unchanged
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        return this;
    }

    /**
     * Optimize an expression
     *
     * @return for a Value, this always returns the value unchanged
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        return this;
    }

    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     */

    @Override
    public int getNetCost() {
        return 0;
    }

    /**
     * Determine the data type of the items in the expression, if possible
     *
     * @return for the default implementation: AnyItemType (not known)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        // Avoid getting the configuration if we can: it's a common source of NPE's
        if (value instanceof AtomicValue) {
            return ((AtomicValue) value).getItemType();
        } else if (value.getLength() == 0) {
            return ErrorType.getInstance();
        } else {
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            return SequenceTool.getItemType(value, th);
        }
    }

    /**
     * Test whether the value is an instance of a supplied type
     * @param req the required type
     * @param th the type hierarchy cache
     * @return true if the value is (strictly) an instance of the type, in the sense of the "instance of" operator
     */
    public boolean isInstance(SequenceType req, TypeHierarchy th) {
        int requiredCardinality = req.getCardinality();
        int count = value.getLength();
        if (!Cardinality.allows(requiredCardinality, count)) {
            return false;
        }
        ItemType requiredType = req.getPrimaryType();
        if (requiredType == AnyItemType.getInstance()) {
            return true;
        }
        if (value instanceof IntegerRange) {
            return th.isSubType(BuiltInAtomicType.INTEGER, requiredType);
        }
        if (value instanceof Item it) {
            return requiredType.matches(it);
        }
        for (Item item : value.asIterable()) {
            if (!requiredType.matches(item)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @param contextItemType the static context item type
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        if (value.getLength() == 0) {
            return UType.VOID;
        } else if (value instanceof AtomicValue) {
            return ((AtomicValue) value).getUType();
        } else if (value instanceof FunctionItem) {
            return UType.FUNCTION;
        } else {
            return super.getStaticUType(contextItemType);
        }
    }


    /**
     * Determine the cardinality
     */

    @Override
    protected int computeCardinality() {
        if (value.getLength() == 0) {
            return StaticProperty.EMPTY;
        } else if (value instanceof Item) {
            return StaticProperty.EXACTLY_ONE;
        }
        SequenceIterator iter = value.iterate();
        Item next = iter.next();
        if (next == null) {
            return StaticProperty.EMPTY;
        } else {
            if (iter.next() != null) {
                return StaticProperty.ALLOWS_MANY;
            } else {
                return StaticProperty.EXACTLY_ONE;
            }
        }
    }

    /**
     * Compute the static properties of this expression (other than its type). For a
     * Value, the only special property is {@link StaticProperty#NO_NODES_NEWLY_CREATED}.
     *
     * @return the value {@link StaticProperty#NO_NODES_NEWLY_CREATED}
     */


    @Override
    protected int computeSpecialProperties() {
        if (value.getLength() == 0) {
            // An empty sequence has all special properties except "has side effects".
            return StaticProperty.SPECIAL_PROPERTY_MASK & ~StaticProperty.HAS_SIDE_EFFECTS;
        }
        return StaticProperty.NO_NODES_NEWLY_CREATED;
    }

    /**
     * Ask whether the expression supports lazy evaluation.
     *
     * @return false either if the expression cannot be evaluated lazily
     * because it has dependencies that cannot be saved in the context, or
     * because lazy evaluation is pointless (for example, for literals
     * and variable references).
     */
    @Override
    public boolean supportsLazyEvaluation() {
        return false;
    }

    /**
     * Determine whether this is a vacuous expression as defined in the XQuery update specification
     *
     * @return true if this expression is vacuous
     */

    @Override
    public boolean isVacuousExpression() {
        return value.getLength() == 0;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @param rebindings variables that need to be re-bound
     * @return a copy of the original literal. Note that the
     * underlying value is not copied; the code relies on the caller
     * treating the underlying value as immutable.
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        Literal l2 = new Literal(value);
        ExpressionTool.copyLocationInfo(this, l2);
        return l2;
    }

    /**
     * Convert this expression to an equivalent XSLT pattern
     *
     * @param config      the Saxon configuration
     * @param firstInPath
     * @return the equivalent pattern
     * @throws net.sf.saxon.trans.XPathException if conversion is not possible
     */
    @Override
    public Pattern toPattern(Configuration config, boolean firstInPath) throws XPathException {
        if (isEmptySequence(this)) {
            return new ItemTypePattern(ErrorType.getInstance());
        } else {
            return super.toPattern(config, firstInPath);
        }
    }

    /**
     * Determine which aspects of the context the expression depends on. The result is
     * a bitwise-or'ed value composed from constants such as StaticProperty.VARIABLES and
     * StaticProperty.CURRENT_NODE
     *
     * @return for a Value, this always returns zero.
     */

    @Override
    public final int getDependencies() {
        return 0;
    }


    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     * of the expression
     * @throws net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *                                           expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        return value.iterate();
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @return a SequenceIterator that can be used to iterate over the result
     * of the expression
     * @throws net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *                                           expression
     */

    /*@NotNull*/
    public SequenceIterator iterate() throws XPathException {
        return value.iterate();
    }

    /**
     * Evaluate as a singleton item (or empty sequence). Note: this implementation returns
     * the first item in the sequence. The method should not be used unless appropriate type-checking
     * has been done to ensure that the value will be a singleton.
     */

    @Override
    public Item evaluateItem(XPathContext context) throws XPathException {
        return value.head();
    }


    /**
     * Process the value as an instruction, without returning any tail calls
     *
     * @param output the destination for the result
     * @param context The dynamic context, giving access to the current node,
     */

    @Override
    public void process(Outputter output, XPathContext context) throws XPathException {
        if (value instanceof Item) {
            output.append((Item) value, getLocation(), ReceiverOption.ALL_NAMESPACES);
        } else {
            SequenceTool.supply(value.iterate(), (ItemConsumer<? super Item>) it -> output.append(it, getLocation(), ReceiverOption.ALL_NAMESPACES));
        }
    }

    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD | PROCESS_METHOD | EVALUATE_METHOD;
    }

    /*
      * Evaluate an expression as a String. This function must only be called in contexts
      * where it is known that the expression will return a single string (or where an empty sequence
      * is to be treated as a zero-length string). Implementations should not attempt to convert
      * the result to a string, other than converting () to "". This method is used mainly to
      * evaluate expressions produced by compiling an attribute value template.
      *
      * @exception net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
      *     expression
      * @exception ClassCastException if the result type of the
      *     expression is not xs:string?
      * @param context The context in which the expression is to be evaluated
      * @return the value of the expression, evaluated in the current context.
      *     The expression must return a string or (); if the value of the
      *     expression is (), this method returns "".
      */

    @Override
    public UnicodeString evaluateAsString(XPathContext context) throws XPathException {
        AtomicValue value = (AtomicValue) evaluateItem(context);
        if (value == null) {
            return EmptyUnicodeString.getInstance();
        }
        return value.getUnicodeStringValue();
    }


    /**
     * Get the effective boolean value of the expression. This returns false if the value
     * is the empty sequence, a zero-length string, a number equal to zero, or the boolean
     * false. Otherwise it returns true.
     *
     * @param context The context in which the expression is to be evaluated
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException if any dynamic error occurs evaluating the
     *                                           expression
     */

    @Override
    public boolean effectiveBooleanValue(XPathContext context) throws XPathException {
        return value.effectiveBooleanValue();
    }


    /**
     * Determine whether two literals are equal, when considered as expressions.
     *
     * @param obj the other expression
     * @return true if the two literals are equal. The test here requires (a) identity in the
     * sense defined by XML Schema (same value in the same value space), and (b) identical type
     * annotations. For example the literal xs:int(3) is not equal (as an expression) to xs:short(3),
     * because the two expressions are not interchangeable.
     */

    public boolean equals(Object obj) {
        if (!(obj instanceof Literal)) {
            return false;
        }
        GroundedValue v0 = value;
        GroundedValue v1 = ((Literal) obj).value;
        SequenceIterator i0 = v0.iterate();
        SequenceIterator i1 = v1.iterate();
        while (true) {
            Item m0 = i0.next();
            Item m1 = i1.next();
            if (m0 == null && m1 == null) {
                return true;
            }
            if (m0 == null || m1 == null) {
                return false;
            }
            if (m0 == m1) {
                continue;
            }
            boolean n0 = m0 instanceof NodeInfo;
            boolean n1 = m1 instanceof NodeInfo;
            if (n0 != n1) {
                return false;
            }
            if (n0) {
                if (m0.equals(m1)) {
                    continue;
                } else {
                    return false;
                }
            }
            boolean a0 = m0 instanceof AtomicValue;
            boolean a1 = m1 instanceof AtomicValue;
            if (a0 != a1) {
                return false;
            }
            if (a0) {
                if (((AtomicValue) m0).isIdentical((AtomicValue) m1) &&
                        ((AtomicValue) m0).getItemType() == ((AtomicValue) m1).getItemType()) {
                    continue;
                } else {
                    return false;
                }
            }
            // don't attempt to compare functions, maps, and arrays
            return false;
        }
    }

    /**
     * Return a hash code to support the equals() function
     */

    @Override
    protected int computeHashCode() {
        if (value instanceof AtomicSequence) {
            return SimpleTypeComparison.getInstance().hash((AtomicSequence) value);
            // TODO: why this comparator - what are we using this hash code for?
        } else {
            return super.computeHashCode();
        }
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        return value.toString();
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        exportValue(value, out);
    }

    public static void exportValue(Sequence value, ExpressionPresenter out) throws XPathException {
        if (value.head() == null) {
            out.startElement("empty");
            out.endElement();
        } else if (value instanceof AtomicValue) {
            exportAtomicValue((AtomicValue) value, out);
        } else if (value instanceof IntegerRange) {
            out.startElement("range");
            out.emitAttribute("from", "" + ((IntegerRange) value).getStart());
            out.emitAttribute("to", "" + ((IntegerRange) value).getEnd());
            out.endElement();
        } else if (value instanceof NodeInfo) {
            out.startElement("node");
            final int nodeKind = ((NodeInfo) value).getNodeKind();
            out.emitAttribute("kind", nodeKind + "");
            if (out.getOptions().explaining) {
                String name = ((NodeInfo) value).getDisplayName();
                if (!name.isEmpty()) {
                    out.emitAttribute("name", name);
                }
            } else {
                switch (nodeKind) {
                    case Type.DOCUMENT:
                    case Type.ELEMENT:
                        StringWriter sw = new StringWriter();
                        Properties props = new Properties();
                        props.setProperty("method", "xml");
                        props.setProperty("indent", "no");
                        props.setProperty("omit-xml-declaration", "yes");
                        QueryResult.serialize(((NodeInfo) value), new StreamResult(sw), props);
                        out.emitAttribute("content", sw.toString());
                        out.emitAttribute("baseUri", ((NodeInfo) value).getBaseURI());
                        break;
                    case Type.TEXT:
                    case Type.COMMENT:
                        out.emitAttribute("content", ((NodeInfo) value).getStringValue());
                        break;
                    case Type.ATTRIBUTE:
                    case Type.NAMESPACE:
                    case Type.PROCESSING_INSTRUCTION:
                        final StructuredQName name = NameOfNode.makeName(((NodeInfo) value)).getStructuredQName();
                        if (!name.getLocalPart().isEmpty()) {
                            out.emitAttribute("localName", name.getLocalPart());
                        }
                        if (!name.getPrefix().isEmpty()) {
                            out.emitAttribute("prefix", name.getPrefix());
                        }
                        if (!name.hasURI(NamespaceUri.NULL)) {
                            out.emitAttribute("ns", name.getNamespaceUri().toString());
                        }
                        out.emitAttribute("content", ((NodeInfo) value).getStringValue());
                        break;
                    default:
                        assert false;
                        break;

                }
            }
            out.endElement();
        } else if (value instanceof MapItem) {
            out.startElement("map");
            out.emitAttribute("size", "" + ((MapItem) value).size());
            for (KeyValuePair kvp : ((MapItem) value).keyValuePairs()) {
                exportAtomicValue(kvp.key(), out);
                exportValue(kvp.value(), out);
            }
            out.endElement();
        } else if (value instanceof JNode) {
            if (value instanceof RootJNode root) {
                out.startElement("jnodeRoot");
                exportValue(root.getContent(), out);
                out.endElement();
            } else {
                throw new XPathException(
                        "Cannot export a stylesheet containing a non-root JNode literal",
                        SaxonErrorCode.SXST0070);
            }
        } else if (value instanceof FunctionItem) {
            ((FunctionItem) value).export(out);
        } else if (value instanceof AnyExternalObject) {
            if (out.getOptions().explaining) {
                out.startElement("externalObject");
                out.emitAttribute("class", ((AnyExternalObject)value).getWrappedObject().getClass().getName());
                out.endElement();
            } else {
                throw new XPathException(
                        "Cannot export a stylesheet containing literal values bound to external Java objects",
                        SaxonErrorCode.SXST0070);
            }
        } else {
            out.startElement("literal");
            if (value instanceof GroundedValue) {
                out.emitAttribute("count", ((GroundedValue) value).getLength() + "");
            }
            SequenceTool.supply(value.iterate(), (ItemConsumer<? super Item>) it -> exportValue(it, out));
            out.endElement();

        }
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "literal";
    }

    public static void exportAtomicValue(AtomicValue value, ExpressionPresenter out) throws XPathException {
        if ("JS".equals(out.getOptions().target)) {
            value.checkValidInJavascript();
        }
        AtomicType type = value.getItemType();
        String val = value.getStringValue();
        if (type.equals(BuiltInAtomicType.STRING)) {
            out.startElement("str");
            out.emitAttribute("val", val);
            out.endElement();
        } else if (type.equals(BuiltInAtomicType.INTEGER)) {
            out.startElement("int");
            out.emitAttribute("val", val);
            out.endElement();
        } else if (type.equals(BuiltInAtomicType.DECIMAL)) {
            out.startElement("dec");
            out.emitAttribute("val", val);
            out.endElement();
        } else if (type.equals(BuiltInAtomicType.DOUBLE)) {
            out.startElement("dbl");
            out.emitAttribute("val", val);
            out.endElement();
        } else if (type.equals(BuiltInAtomicType.BOOLEAN)) {
            out.startElement(((BooleanValue) value).effectiveBooleanValue() ? "true" : "false");
            out.endElement();
        } else if (value instanceof QualifiedNameValue) {
            out.startElement("qName");
            out.emitAttribute("pre", ((QualifiedNameValue) value).getPrefix());
            out.emitAttribute("uri", ((QualifiedNameValue) value).getNamespaceURI().toString());
            out.emitAttribute("loc", ((QualifiedNameValue) value).getLocalName());
            if (!type.equals(BuiltInAtomicType.QNAME)) {
                out.emitAttribute("type", type.getEQName());
            }
            out.endElement();
        } else {
            out.startElement("atomic");
            out.emitAttribute("val", val);
            out.emitAttribute("type", AlphaCode.fromItemType(type));
            out.endElement();
        }
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */
    @Override
    public String toShortString() {
        if (value.getLength() == 0) {
            return "()";
        } else if (value.getLength() == 1) {
            return value.toShortString();
        } else if (value.getLength() == 2) {
            return "(" + value.head().toShortString() + ", " + value.itemAt(1).toShortString() + ")";
        } else {
            return "(" + value.head().toShortString()
                    + ", " + value.itemAt(1).toShortString()
                    + ", ...{" + value.getLength() + "})";
        }
    }

    /**
     * Test whether the literal wraps an atomic value. (Note, if this method returns false,
     * this still leaves the possibility that the literal wraps a sequence that happens to contain
     * a single atomic value).
     *
     * @param exp an expression
     * @return true if the expression is a literal and the literal wraps an AtomicValue
     */

    public static boolean isAtomic(Expression exp) {
        return exp instanceof Literal && ((Literal) exp).getGroundedValue() instanceof AtomicValue;
    }

    /**
     * Test whether the literal explicitly wraps an empty sequence. (Note, if this method returns false,
     * this still leaves the possibility that the literal wraps a sequence that happens to be empty).
     *
     * @param exp an expression
     * @return true if the expression is a literal and the value of the literal is an empty sequence
     */

    public static boolean isEmptySequence(Expression exp) {
        return exp instanceof Literal && ((Literal) exp).getGroundedValue().getLength() == 0;
    }

    /**
     * Test if a literal represents the boolean value true
     *
     * @param exp   an expression
     * @param value true or false
     * @return true if the expression is a literal and the literal represents the boolean value given in the
     * second argument
     */

    public static boolean isConstantBoolean(Expression exp, boolean value) {
        if (exp instanceof Literal) {
            GroundedValue b = ((Literal) exp).getGroundedValue();
            return b instanceof BooleanValue && ((BooleanValue) b).getBooleanValue() == value;
        }
        return false;
    }

    /**
     * Test if a literal has the effective boolean value true (or false)
     *
     * @param exp   an expression
     * @param value true or false
     * @return true if the expression is a literal and the literal represents the boolean value given in the
     * second argument
     */

    public static boolean hasEffectiveBooleanValue(Expression exp, boolean value) {
        if (exp instanceof Literal) {
            try {
                return value == ((Literal) exp).getGroundedValue().effectiveBooleanValue();
            } catch (XPathException err) {
                return false;
            }
        }
        return false;
    }

    /**
     * Test if a literal represents the integer value 1
     *
     * @param exp an expression
     * @return true if the expression is a literal and the literal represents the integer value 1
     */

    public static boolean isConstantOne(Expression exp) {
        if (exp instanceof Literal) {
            GroundedValue v = ((Literal) exp).getGroundedValue();
            return v instanceof Int64Value && ((Int64Value) v).longValue() == 1;
        }
        return false;
    }

    /**
     * Test if a literal represents the integer value 1
     *
     * @param exp an expression
     * @return true if the expression is a literal and the literal represents the integer value 1
     */

    public static boolean isConstantZero(Expression exp) {
        if (exp instanceof Literal) {
            GroundedValue v = ((Literal) exp).getGroundedValue();
            return v instanceof Int64Value && ((Int64Value) v).longValue() == 0;
        }
        return false;
    }

    /**
     * Determine whether the expression can be evaluated without reference to the part of the context
     * document outside the subtree rooted at the context node.
     *
     * @return true if the expression has no dependencies on the context node, or if the only dependencies
     * on the context node are downward selections using the self, child, descendant, attribute, and namespace
     * axes.
     */

    @Override
    public boolean isSubtreeExpression() {
        return true;
    }

    /**
     * Factory method to make an empty-sequence literal
     *
     * @return a literal whose value is the empty sequence
     */

    public static Literal makeEmptySequence() {
        return new Literal(EmptySequence.INSTANCE);
    }


    /**
     * Factory method to create a literal as a wrapper around a Value (factory method)
     *
     * @param value the value of this literal
     * @return the Literal
     */

    public static Literal makeLiteral(GroundedValue value) {
        value = value.reduce();
        if (value instanceof StringValue sv) {
            return new StringLiteral(sv);
        } else if (value instanceof FunctionItem && !(value instanceof MapItem || value instanceof ArrayItem)) {
            return new FunctionLiteral((FunctionItem) value);
        } else {
            return new Literal(value);
        }
    }


    /**
     * Make a literal, taking the retained static context and location information from another
     * expression which is being simplified/optimized
     *
     * @param value  the literal value
     * @param origin the expression whose context and location information is to be retained
     * @return the Literal
     */

    public static Literal makeLiteral(GroundedValue value, Expression origin) {
        Literal lit = makeLiteral(value);
        //lit.setRetainedStaticContextLocally(origin.getLocalRetainedStaticContext());
        ExpressionTool.copyLocationInfo(origin, lit);
        return lit;
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "Literal";
    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new LiteralElaborator();
    }

    /**
     * Elaborator for a literal.
     *
     * <p>Evaluators are supplied for evaluation in pull, push, and singleton mode, as well as boolean
     * evaluation</p>
     */

    public static class LiteralElaborator extends PullElaborator {

        @Override
        public SequenceEvaluator eagerly() {
            GroundedValue value = ((Literal) getExpression()).getGroundedValue();
            return new LiteralEvaluator(value);
        }

        @Override
        public SequenceEvaluator lazily(boolean repeatable, boolean lazyEvaluationRequired) {
            return eagerly();
        }


        @Override
        public PullEvaluator elaborateForPull() {
            GroundedValue value = ((Literal)getExpression()).getGroundedValue();
            return context -> value.iterate();
        }

        @Override
        public PushEvaluator elaborateForPush() {
            Literal expr = (Literal)getExpression();
            GroundedValue value = ((Literal) getExpression()).getGroundedValue();
            Location loc = expr.getLocation();
            if (value instanceof Item it) {
                return (out, context) -> {
                    out.append(it, loc, ReceiverOption.ALL_NAMESPACES);
                    return null;
                };
            } else {
                return (out, context) -> {
                    for (Item item : value.asIterable()) {
                         out.append(item, loc, ReceiverOption.ALL_NAMESPACES);
                    }
                    return null;
                };
            }
        }

        @Override
        public ItemEvaluator elaborateForItem() {
            GroundedValue value = ((Literal) getExpression()).getGroundedValue();
            assert value.getLength() < 2;
            Item item = value.head();
            return context -> item;
        }

        @Override
        public BooleanEvaluator elaborateForBoolean() {
            GroundedValue value = ((Literal) getExpression()).getGroundedValue();
            try {
                boolean ebv = value.effectiveBooleanValue();
                return context -> ebv;
            } catch (XPathException e) {
                return context -> {
                    throw e;
                };
            }
        }

        @Override
        public UnicodeStringEvaluator elaborateForUnicodeString(boolean zeroLengthWhenAbsent) {
            GroundedValue value = ((Literal) getExpression()).getGroundedValue();
            try {
                UnicodeString str = value.getUnicodeStringValue();
                return context -> str;
            } catch (XPathException e) {
                return context -> {
                    throw e;
                };
            }
        }

        @Override
        public StringEvaluator elaborateForString(boolean zeroLengthWhenAbsent) {
            GroundedValue value = ((Literal) getExpression()).getGroundedValue();
            try {
                String str = value.getStringValue();
                return context -> str;
            } catch (XPathException e) {
                return context -> {
                    throw e;
                };
            }
        }

        @Override
        public UpdateEvaluator elaborateForUpdate() {
            GroundedValue value = ((Literal) getExpression()).getGroundedValue();
            if (value.getLength() == 0) {
                return (context, pul) -> {};
            } else {
                return super.elaborateForUpdate();
            }
        }
    }
}
