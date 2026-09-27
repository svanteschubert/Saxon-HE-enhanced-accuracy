// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type.gnode;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.GNode;
import net.sf.saxon.om.Genre;
import net.sf.saxon.om.Item;
import net.sf.saxon.pattern.nodetest.NodePredicate;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.pattern.qname.AnyQNameTest;
import net.sf.saxon.pattern.qname.QNameTest;
import net.sf.saxon.type.*;
import net.sf.saxon.type.coercion.CoercionPlan;
import net.sf.saxon.value.SequenceType;

import java.util.Optional;

/**
 * Abstract superclass for all gnode types. In 4.0, node predicates (as used in axis steps)
 * are separated from node types (as item types). Subclasses of {@code GNodeType} represent
 * item types whose instances are all nodes; they cannot be used directly as node predicates
 * in an axis step.
 */
public abstract class GNodeType implements ItemTypeWithSequenceTypeCache, ItemType, NodeTest {

    private SequenceType _one;
    private SequenceType _oneOrMore;
    private SequenceType _zeroOrOne;
    private SequenceType _zeroOrMore;

    /**
     * Determine the Genre (top-level classification) of this type
     *
     * @return the Genre to which this type belongs, for example node or atomic value
     */
    @Override
    public Genre getGenre() {
        return Genre.ANY;
    }

    /**
     * Determine whether this item type is an atomic type
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof
     */
    @Override
    public final boolean isAtomicType() {
        return false;
    }

    /**
     * Determine whether this item type is a plain type (that is, whether it can ONLY match
     * atomic values)
     *
     * @return true if this is ANY_ATOMIC_TYPE or a subtype thereof, or a
     * "plain" union type (that is, unions of atomic types that impose no further restrictions).
     * Return false if this is a union type whose member types are not all known.
     */
    @Override
    public final boolean isPlainType() {
        return false;
    }

    /**
     * Get the allowed content type. By default, this returns ANY_TYPE,
     * allowing any content.
     * @return the allowed content type
     */

    public SchemaType getContentType() {
        return AnyType.INSTANCE;
    }

    /**
     * Get the primitive item type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue and union types it is Type.ATOMIC_VALUE. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that integer, xs:dayTimeDuration, and xs:yearMonthDuration
     * are considered to be primitive types.
     *
     * @return the corresponding primitive type
     */
    @Override
    public ItemType getPrimitiveItemType() {
        return this;
    }

    /**
     * Get the primitive type corresponding to this item type. For item(),
     * this is Type.ITEM. For node(), it is Type.NODE. For specific node kinds,
     * it is the value representing the node kind, for example Type.ELEMENT.
     * For anyAtomicValue it is BuiltInAtomicType.ANY_ATOMIC. For numeric it is Type.NUMBER.
     * For other atomic types it is the primitive type as defined in XML Schema,
     * except that INTEGER is considered to be a primitive type.
     *
     * @return the integer fingerprint of the corresponding primitive type
     */
    @Override
    public int getPrimitiveType() {
        return Type.GNODE;
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public abstract boolean matches(Item item);


    /**
     * Test whether a given item conforms to this type (to implement {@link NodePredicate})
     *
     * @param node The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */

    @Override
    public final boolean test(GNode node) {
        return matches(node);
    }

    /**
     * Get the corresponding {@link UType}. A UType is a union of primitive item
     * types.
     *
     * @return the smallest UType that subsumes this item type
     */
    @Override
    public UType getUType() {
        return UType.GNODE;
    }

    /**
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     *
     * @param version the XPath language version (40 or 31)
     */
    @Override
    public CoercionPlan getCoercionPlan(int version) {
        return null;
    }

    /**
     * Get the item type of the atomic values that will be produced when an item
     * of this type is atomized
     *
     * @return the best available item type of the atomic values that will be produced when an item
     * of this type is atomized, or null if it is known that atomization will throw an error.
     */
    @Override
    public PlainType getAtomizedItemType() {
        return BuiltInAtomicType.ANY_ATOMIC;
    }

    /**
     * Ask whether values of this type are atomizable
     *
     * @param th the type hierarchy cache
     * @return true if some or all instances of this type can be successfully atomized; false
     * if no instances of this type can be atomized
     */
    @Override
    public boolean isAtomizable(TypeHierarchy th) {
        return true;
    }

    /**
     * Get extra diagnostic information about why a supplied item does not conform to this
     * item type, if available. If extra information is returned, it should be in the form of a complete
     * sentence, minus the closing full stop. No information should be returned for obvious cases.
     *
     * @param item the item that doesn't match this type
     * @param th   the type hierarchy cache
     * @return optionally, a message explaining why the item does not match the type
     */
    @Override
    public Optional<String> explainMismatch(Item item, TypeHierarchy th) {
        return Optional.empty();
    }

    /**
     * Extract a QNameTest (the strongest one possible) that must be satisfied by a node
     * if it is to satisfy this NodeTest
     *
     * @return the strongest possible QNameTest
     */
    @Override
    public QNameTest getQNameTest() {
        return AnyQNameTest.getInstance();
    }

    /**
     * Get an item type that all matching nodes must satisfy
     *
     * @return an item type
     */
    @Override
    public ItemType getItemType() {
        return this;
    }

    /**
     * Get a concise string representation of this node test for use in diagnostics
     *
     * @return a suitably abbreviated respresention of the node test
     */
    @Override
    public String toShortString() {
        return toString();
    }

    /**
     * Get a sequence type representing exactly one instance of this type
     *
     * @return a sequence type representing exactly one instance of this type
     */

    @Override
    public SequenceType one() {
        if (_one == null) {
            _one = new SequenceType(this, StaticProperty.EXACTLY_ONE);
        }
        return _one;
    }

    /**
     * Get a sequence type representing zero or one instances of this type
     *
     * @return a sequence type representing zero or one instances of this type
     */

    @Override
    public SequenceType zeroOrOne() {
        if (_zeroOrOne == null) {
            _zeroOrOne = new SequenceType(this, StaticProperty.ALLOWS_ZERO_OR_ONE);
        }
        return _zeroOrOne;
    }

    /**
     * Get a sequence type representing one or more instances of this type
     *
     * @return a sequence type representing one or more instances of this type
     */

    @Override
    public SequenceType oneOrMore() {
        if (_oneOrMore == null) {
            _oneOrMore = new SequenceType(this, StaticProperty.ALLOWS_ONE_OR_MORE);
        }
        return _oneOrMore;
    }

    /**
     * Get a sequence type representing one or more instances of this type
     *
     * @return a sequence type representing one or more instances of this type
     */

    @Override
    public SequenceType zeroOrMore() {
        if (_zeroOrMore == null) {
            _zeroOrMore = new SequenceType(this, StaticProperty.ALLOWS_ZERO_OR_MORE);
        }
        return _zeroOrMore;
    }

}

