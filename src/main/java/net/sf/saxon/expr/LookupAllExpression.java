////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.elab.Elaborator;
import net.sf.saxon.expr.elab.PullElaborator;
import net.sf.saxon.expr.elab.PullEvaluator;
import net.sf.saxon.expr.instruct.Block;
import net.sf.saxon.expr.parser.ContextItemStaticInfo;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.ArrayItemType;
import net.sf.saxon.ma.arrays.SquareArrayConstructor;
import net.sf.saxon.ma.map.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpSuppressWarnings;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * The {@code LookupAllExpression} handles lookup expressions of the form {@code expr?*} (which
 * returns all entries in a map or array) and {@code expr?type(T)} (which returns all entries
 * whose value matches a given sequence type).
 */

public class LookupAllExpression extends UnaryExpression {


    /**
     * Constructor
     *
     * @param lhs The left hand operand (which must always select a sequence of maps or arrays).
     */

    public LookupAllExpression(Expression lhs) {
        super(lhs);
    }

    @Override
    protected OperandRole getOperandRole() {
        return OperandRole.INSPECT;
    }


    /**
     * Determine the data type of the items returned by this expression
     *
     * @return the type of the step
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        return AnyItemType.getInstance();
    }


    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType The type of the context item (not used)
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        return getItemType().getUType();
    }

    /**
     * Type-check the expression
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        Configuration config = visitor.getConfiguration();
        TypeHierarchy th = config.getTypeHierarchy();

        // Check the first operand
        getOperand().typeCheck(visitor, contextInfo);

        ItemType containerType = getBaseExpression().getItemType();
        boolean isArrayLookup = containerType instanceof ArrayItemType;
        boolean isMapLookup = containerType instanceof MapType || containerType instanceof RecordType;

        if (!isArrayLookup && !isMapLookup) {
            if (th.relationship(containerType, MapType.ANY_MAP_TYPE) == Affinity.DISJOINT) {
                if (th.relationship(containerType, ArrayItemType.INSTANCE) == Affinity.DISJOINT) {
                    if (Cardinality.allowsZero(getBaseExpression().getCardinality())) {
                        visitor.issueWarning("The left-hand operand of '?' must be a map or an array; the expression can succeed only if the operand is an empty sequence "
                                                     + containerType, SaxonErrorCode.SXWN9026, getLocation());
                    } else {
                        throw new XPathException("The left-hand operand of '?' must be a map or an array; "
                                                         + "the supplied expression is of type " + containerType, "XPTY0004")
                                .withLocation(getLocation())
                                .asTypeError()
                                .withFailingExpression(this);
                    }
                }
            }
        }

        if (getBaseExpression() instanceof Literal) {
            try {
                return new Literal(SequenceTool.toGroundedValue(iterate(visitor.makeDynamicContext())));
            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }
        }

        return this;
    }

    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        getOperand().optimize(visitor, contextItemType);

        if (getBaseExpression() instanceof Literal) {
            try {
                return new Literal(SequenceTool.toGroundedValue(iterate(visitor.makeDynamicContext())));
            } catch (UncheckedXPathException e) {
                throw e.getXPathException();
            }
        }
        
        // See W3C bug 30228. In the interests of keeping certain tests streamable, we do a rewrite of [A,B,C]?*
        // to (A, B, C).
        if (getBaseExpression() instanceof SquareArrayConstructor) {
            List<Expression> children = new ArrayList<>();
            for (Operand o : getBaseExpression().operands()) {
                children.add(o.getChildExpression().copy(new RebindingMap()));
            }
            Expression[] childExpressions = children.toArray(new Expression[0]);
            Block block = new Block(childExpressions);
            ExpressionTool.copyLocationInfo(this, block);
            return block;
        }
        return this;
    }


    /**
     * Return the estimated cost of evaluating an expression. This is a very crude measure based
     * on the syntactic form of the expression (we have no knowledge of data values). We take
     * the cost of evaluating a simple scalar comparison or arithmetic expression as 1 (one),
     * and we assume that a sequence has length 5. The resulting estimates may be used, for
     * example, to reorder the predicates in a filter expression so cheaper predicates are
     * evaluated first.
     * @return the cost estimate
     */
    @Override
    public double getCost() {
        return getBaseExpression().getCost() + 1;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided directly. The other methods will always be available
     * indirectly, using an implementation that relies on one of the other methods.
     *
     * @return the implementation method, for example {@link #ITERATE_METHOD} or {@link #EVALUATE_METHOD} or
     * {@link #PROCESS_METHOD}
     */
    @Override
    public int getImplementationMethod() {
        return ITERATE_METHOD;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rebindings variables that need to be re-bound.
     */

    /*@NotNull*/
    @Override
    public LookupAllExpression copy(RebindingMap rebindings) {
        return new LookupAllExpression(getBaseExpression().copy(rebindings));
    }

    /**
     * Determine the static cardinality of the expression
     */

    @Override
    protected int computeCardinality() {
        return StaticProperty.ALLOWS_ZERO_OR_MORE;
    }

    /**
     * Is this expression the same as another expression?
     */

    public boolean equals(Object other) {
        if (!(other instanceof LookupAllExpression)) {
            return false;
        }
        LookupAllExpression p = (LookupAllExpression) other;
        return getBaseExpression().isEqual(p.getBaseExpression());
    }

    /**
     * get HashCode for comparing two expressions
     */

    @Override
    protected int computeHashCode() {
        return "LookupAll".hashCode() ^ getBaseExpression().hashCode();
    }

    /**
     * Iterate the lookup-expression in a given context
     *
     * @param context the evaluation context
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(final XPathContext context) throws XPathException {
        return makeElaborator().elaborateForPull().iterate(context);
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("lookupAll", this);
        getBaseExpression().export(destination);
        destination.endElement();
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     *
     * @return a representation of the expression as a string
     */

    public String toString() {
        return ExpressionTool.parenthesize(getBaseExpression()) + "?*";
    }

    @Override
    public String toShortString() {
        return getBaseExpression().toShortString() + "?*";
    }

    @Override
    public Elaborator getElaborator() {
        return new LookupAllElaborator();
    }

    public static class LookupAllElaborator extends PullElaborator {

        public PullEvaluator elaborateForPull() {
            LookupAllExpression expr = (LookupAllExpression) getExpression();
            PullEvaluator baseEval = expr.getBaseExpression().makeElaborator().elaborateForPull();
            SequenceType req = SequenceType.ANY_SEQUENCE;
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            return context -> {
                SequenceIterator lhs = baseEval.iterate(context);
                SequenceIterator entries = new MappingIterator(lhs, item -> {
                    if (item instanceof ArrayItem) {
                        return new ArrayEntriesIterator((ArrayItem)item);
                    } else if (item instanceof MapItem) {
                        return new MapEntriesIterator((MapItem)item);
                    } else {
                        XPathException err = new XPathException("Left-hand operand of lookup operator `?` must be a map or array", "XPTY0004").asTypeError();
                        throw new UncheckedXPathException(err);
                    }
                });

                return new MappingIterator(entries, item ->
                        ((ShapedMap) item).getU(Shape.VALUE).iterate());

            };
        }
    }

    /**
     * Iterator over a sequence of entries in an array, delivering them in the form
     * required by $array?entry::*
     */
    public static class ArrayEntriesIterator implements SequenceIterator {

        final ArrayItem container;
        final Iterator<GroundedValue> baseIterator;
        int index = 0;

        public ArrayEntriesIterator(ArrayItem arrayItem) {
            baseIterator = arrayItem.members().iterator();
            container = arrayItem;
        }

        @Override
        @CSharpSuppressWarnings("UnsafeIteratorConversion")
        public Item next() {
            if (baseIterator.hasNext()) {
                GroundedValue value = baseIterator.next();
                Int64Value key = Int64Value.makeIntegerValue(++index);
                return LookupExpression.makeEntry(key, value, container);
            } else {
                return null;
            }
        }
    }

    /**
     * Iterator over a sequence of entries in a map, delivering them in the form
     * required by $map?entry::*
     */
    public static class MapEntriesIterator implements SequenceIterator {

        final MapItem container;
        final Iterator<KeyValuePair> baseIterator;

        public MapEntriesIterator(MapItem mapItem) {
            baseIterator = mapItem.keyValuePairs().iterator();
            container = mapItem;
        }

        @Override
        @CSharpSuppressWarnings("UnsafeIteratorConversion")
        public Item next() {
            if (baseIterator.hasNext()) {
                KeyValuePair kvp = baseIterator.next();
                return LookupExpression.makeEntry(kvp.key(), kvp.value(), container);
            } else {
                return null;
            }
        }
    }
}

